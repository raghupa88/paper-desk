package com.paperdesk.trading;

import com.paperdesk.domain.Account;
import com.paperdesk.domain.Enums.CallPut;
import com.paperdesk.domain.Enums.SettlementKind;
import com.paperdesk.domain.EquitySnapshot;
import com.paperdesk.domain.Instrument;
import com.paperdesk.domain.Position;
import com.paperdesk.domain.Settlement;
import com.paperdesk.gamification.GamificationService;
import com.paperdesk.gamification.MissionEvaluationService;
import com.paperdesk.repo.AccountRepo;
import com.paperdesk.repo.EquitySnapshotRepo;
import com.paperdesk.repo.InstrumentRepo;
import com.paperdesk.repo.PositionRepo;
import com.paperdesk.repo.SettlementRepo;
import com.paperdesk.sim.MarketDataService;
import com.paperdesk.sim.ScenarioParams;
import com.paperdesk.sim.SessionRuntime;
import com.paperdesk.sim.SimEngine;
import com.paperdesk.sim.SimEvents;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * Everything that happens when a sim day completes: futures daily
 * mark-to-market and margin calls, option expiry (auto-exercise/assignment or
 * expire worthless), forward maturity settlement, swap fixings, option-chain
 * rolling and end-of-day equity snapshots.
 */
@Service
public class LifecycleService {

    private final SimEngine engine;
    private final MarketDataService market;
    private final InstrumentRepo instrumentRepo;
    private final PositionRepo positionRepo;
    private final AccountRepo accountRepo;
    private final SettlementRepo settlementRepo;
    private final EquitySnapshotRepo snapshotRepo;
    private final PortfolioService portfolio;
    private final InstrumentFactory factory;
    private final OrderService orderService;
    private final GamificationService gamification;
    private final MissionEvaluationService missions;

    public LifecycleService(SimEngine engine, MarketDataService market, InstrumentRepo instrumentRepo,
                            PositionRepo positionRepo, AccountRepo accountRepo, SettlementRepo settlementRepo,
                            EquitySnapshotRepo snapshotRepo, PortfolioService portfolio,
                            InstrumentFactory factory, OrderService orderService,
                            GamificationService gamification, MissionEvaluationService missions) {
        this.engine = engine;
        this.market = market;
        this.instrumentRepo = instrumentRepo;
        this.positionRepo = positionRepo;
        this.accountRepo = accountRepo;
        this.settlementRepo = settlementRepo;
        this.snapshotRepo = snapshotRepo;
        this.portfolio = portfolio;
        this.factory = factory;
        this.orderService = orderService;
        this.gamification = gamification;
        this.missions = missions;
    }

    @EventListener
    @Transactional
    public void onDayRolled(SimEvents.DayRolled evt) {
        long sessionId = evt.sessionId();
        settleFutures(sessionId, evt);
        settleExpiries(sessionId, evt);
        applySwapFixings(sessionId, evt);
        rollChains(sessionId, evt);
        snapshotAccounts(sessionId, evt.closedDay());
    }

    // ---- futures daily settlement + margin calls ----

    private void settleFutures(long sessionId, SimEvents.DayRolled evt) {
        for (Position pos : positionRepo.findOpenBySession(sessionId)) {
            Instrument instr = market.instrument(pos.instrumentId);
            if (instr.instrumentType != com.paperdesk.domain.Enums.InstrumentType.FUTURE) continue;
            if (instr.expiryDate != null && !instr.expiryDate.isAfter(evt.closedDay())) continue; // final settle below
            double settle = market.mid(instr);
            double basis = pos.lastSettlePrice != null ? pos.lastSettlePrice : pos.avgPrice;
            double variation = (settle - basis) * pos.qty * instr.contractSize;
            Account account = accountRepo.findById(pos.accountId).orElseThrow();
            account.cashBalance += variation;
            pos.lastSettlePrice = settle;
            positionRepo.save(pos);
            accountRepo.save(account);
            record(account.id, instr.id, evt.closedDay(), SettlementKind.FUTURES_MTM, variation,
                    "Daily settle " + instr.symbol + " @ " + round4(settle));
        }
        // margin call check after all variation is posted
        for (Account account : accountRepo.findBySessionId(sessionId)) {
            if (account.cashBalance >= 0) continue;
            record(account.id, null, evt.closedDay(), SettlementKind.MARGIN_CALL, 0,
                    "Cash " + Math.round(account.cashBalance) + " below zero after daily settlement — liquidating futures");
            gamification.onMarginCall(account, evt.closedDay());
            liquidateFutures(account, evt.closedDay());
            orderService.notifyAccount(account.id, "MARGIN_CALL", evt.closedDay().toString());
        }
    }

    private void liquidateFutures(Account account, LocalDate day) {
        for (Position pos : positionRepo.findByAccountIdAndQtyNot(account.id, 0)) {
            Instrument instr = market.instrument(pos.instrumentId);
            if (instr.instrumentType != com.paperdesk.domain.Enums.InstrumentType.FUTURE) continue;
            double released = Math.abs(pos.qty) * instr.initialMargin;
            account.cashBalance += released;
            account.marginHeld -= released;
            record(account.id, instr.id, day, SettlementKind.LIQUIDATION, released,
                    "Forced close " + fmtQty(pos.qty) + " " + instr.symbol + ", margin released");
            pos.qty = 0;
            pos.avgPrice = 0;
            pos.lastSettlePrice = null;
            positionRepo.save(pos);
        }
        accountRepo.save(account);
    }

    // ---- expiry processing ----

    private void settleExpiries(long sessionId, SimEvents.DayRolled evt) {
        List<Instrument> expiring = instrumentRepo
                .findBySessionIdAndExpiryDateLessThanEqualAndActiveTrue(sessionId, evt.closedDay());
        for (Instrument instr : expiring) {
            switch (instr.instrumentType) {
                case OPTION, FX_OPTION -> expireOption(instr, evt);
                case FUTURE -> finalSettleFuture(instr, evt);
                case FORWARD -> settleForward(instr, evt);
                case SWAP -> matureSwap(instr, evt);
                default -> {}
            }
            instr.active = false;
            instrumentRepo.save(instr);
            market.evictInstrument(instr.id);
        }
    }

    private void expireOption(Instrument instr, SimEvents.DayRolled evt) {
        Instrument under = market.instrument(instr.underlyingId);
        double s = evt.closes().getOrDefault(under.symbol, 0.0);
        double intrinsic = instr.callPut == CallPut.CALL ? Math.max(s - instr.strike, 0)
                                                         : Math.max(instr.strike - s, 0);
        forEachOpenPosition(instr, (pos, account) -> {
            double cashFlow = intrinsic * pos.qty * instr.contractSize;
            SettlementKind kind = intrinsic <= 0 ? SettlementKind.OPTION_EXPIRY
                    : pos.qty > 0 ? SettlementKind.OPTION_EXERCISE : SettlementKind.OPTION_ASSIGNMENT;
            account.cashBalance += cashFlow;
            pos.realizedPnl += (intrinsic - pos.avgPrice) * pos.qty * instr.contractSize;
            gamification.onOptionSettled(account, intrinsic > 0, pos.qty, evt.closedDay());
            record(account.id, instr.id, evt.closedDay(), kind, cashFlow,
                    (intrinsic <= 0 ? "Expired worthless: " : "Cash-settled intrinsic "
                            + round4(intrinsic) + " x " + fmtQty(pos.qty) + " x " + instr.contractSize + ": ")
                            + instr.symbol + " (underlying closed " + round4(s) + ")");
        });
    }

    private void finalSettleFuture(Instrument instr, SimEvents.DayRolled evt) {
        Instrument under = market.instrument(instr.underlyingId);
        double settle = evt.closes().getOrDefault(under.symbol, 0.0); // at expiry F = S
        forEachOpenPosition(instr, (pos, account) -> {
            double basis = pos.lastSettlePrice != null ? pos.lastSettlePrice : pos.avgPrice;
            double variation = (settle - basis) * pos.qty * instr.contractSize;
            double released = Math.abs(pos.qty) * instr.initialMargin;
            account.cashBalance += variation + released;
            account.marginHeld -= released;
            pos.realizedPnl += variation;
            record(account.id, instr.id, evt.closedDay(), SettlementKind.FUTURES_MTM, variation + released,
                    "Final settlement " + instr.symbol + " @ " + round4(settle) + ", margin released " + Math.round(released));
        });
    }

    private void settleForward(Instrument instr, SimEvents.DayRolled evt) {
        Instrument under = market.instrument(instr.underlyingId);
        double s = evt.closes().getOrDefault(under.symbol, 0.0);
        forEachOpenPosition(instr, (pos, account) -> {
            double cashFlow = (s - pos.avgPrice) * pos.qty * instr.contractSize;
            account.cashBalance += cashFlow;
            pos.realizedPnl += cashFlow;
            record(account.id, instr.id, evt.closedDay(), SettlementKind.FORWARD_SETTLE, cashFlow,
                    "Forward matured: spot " + round4(s) + " vs agreed " + round4(pos.avgPrice));
        });
    }

    private void matureSwap(Instrument instr, SimEvents.DayRolled evt) {
        forEachOpenPosition(instr, (pos, account) -> {
            double cashFlow = com.paperdesk.pricing.Swaps.fixingCashFlow(
                    instr.notional, instr.fixedRate, evt.floatingRate(), instr.payFreqMonths) * pos.qty;
            account.cashBalance += cashFlow;
            pos.realizedPnl += cashFlow;
            record(account.id, instr.id, evt.closedDay(), SettlementKind.SWAP_MATURITY, cashFlow,
                    "Swap matured with final fixing at " + pct(evt.floatingRate()));
        });
    }

    /** Applies (pos, account) to every open position on the instrument, closing it afterwards. */
    private void forEachOpenPosition(Instrument instr, java.util.function.BiConsumer<Position, Account> action) {
        for (Position pos : positionRepo.findOpenBySession(instr.sessionId)) {
            if (!pos.instrumentId.equals(instr.id) || pos.qty == 0) continue;
            Account account = accountRepo.findById(pos.accountId).orElseThrow();
            action.accept(pos, account);
            pos.qty = 0;
            pos.avgPrice = 0;
            pos.lastSettlePrice = null;
            positionRepo.save(pos);
            accountRepo.save(account);
            orderService.notifyAccount(account.id, "SETTLEMENT", instr.symbol);
        }
    }

    // ---- swap periodic fixings ----

    private void applySwapFixings(long sessionId, SimEvents.DayRolled evt) {
        for (Position pos : positionRepo.findOpenBySession(sessionId)) {
            Instrument instr = market.instrument(pos.instrumentId);
            if (instr.instrumentType != com.paperdesk.domain.Enums.InstrumentType.SWAP) continue;
            if (instr.expiryDate != null && !instr.expiryDate.isAfter(evt.closedDay())) continue;
            LocalDate opened = LocalDate.ofInstant(pos.openedSimTime, ZoneOffset.UTC);
            long daysHeld = ChronoUnit.DAYS.between(opened, evt.closedDay());
            long freqDays = instr.payFreqMonths * 30L;
            if (daysHeld <= 0 || daysHeld % freqDays != 0) continue;
            double cashFlow = com.paperdesk.pricing.Swaps.fixingCashFlow(
                    instr.notional, instr.fixedRate, evt.floatingRate(), instr.payFreqMonths) * pos.qty;
            Account account = accountRepo.findById(pos.accountId).orElseThrow();
            account.cashBalance += cashFlow;
            pos.realizedPnl += cashFlow;
            positionRepo.save(pos);
            accountRepo.save(account);
            record(account.id, instr.id, evt.closedDay(), SettlementKind.SWAP_FIXING, cashFlow,
                    "Fixing: floating " + pct(evt.floatingRate()) + " vs fixed " + pct(instr.fixedRate)
                            + " on " + Math.round(instr.notional));
            orderService.notifyAccount(account.id, "SETTLEMENT", instr.symbol);
        }
    }

    private void rollChains(long sessionId, SimEvents.DayRolled evt) {
        SessionRuntime rt = engine.runtime(sessionId);
        ScenarioParams params = rt.params;
        factory.ensureOptionChains(sessionId, params, evt.closedDay().plusDays(1), evt.closes());
    }

    private void snapshotAccounts(long sessionId, LocalDate day) {
        SessionRuntime rt = engine.runtime(sessionId);
        for (Account account : accountRepo.findBySessionId(sessionId)) {
            EquitySnapshot snap = new EquitySnapshot();
            snap.accountId = account.id;
            snap.simDate = day;
            snap.equity = portfolio.equity(account);
            snapshotRepo.save(snap);
            gamification.onDayClosed(account, day, snap.equity, rt);
            missions.evaluateAndAward(account, day);
            accountRepo.save(account); // persist any XP the day-close/mission awards added
        }
    }

    private void record(long accountId, Long instrumentId, LocalDate day, SettlementKind kind,
                        double cashFlow, String detail) {
        Settlement s = new Settlement();
        s.accountId = accountId;
        s.instrumentId = instrumentId;
        s.simDate = day;
        s.kind = kind;
        s.cashFlow = cashFlow;
        s.detail = detail;
        settlementRepo.save(s);
    }

    private static double round4(double x) { return Math.round(x * 10000) / 10000.0; }
    private static String fmtQty(double q) { return q == Math.floor(q) ? String.valueOf((long) q) : String.valueOf(q); }
    private static String pct(double r) { return Math.round(r * 10000) / 100.0 + "%"; }
}
