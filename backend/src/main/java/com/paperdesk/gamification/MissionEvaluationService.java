package com.paperdesk.gamification;

import com.paperdesk.domain.Account;
import com.paperdesk.domain.Enums.CallPut;
import com.paperdesk.domain.Enums.InstrumentType;
import com.paperdesk.domain.Enums.OrderStatus;
import com.paperdesk.domain.Enums.SettlementKind;
import com.paperdesk.domain.Enums.ViewContext;
import com.paperdesk.domain.Instrument;
import com.paperdesk.domain.MissionCompletion;
import com.paperdesk.domain.Position;
import com.paperdesk.domain.Settlement;
import com.paperdesk.domain.TradeOrder;
import com.paperdesk.repo.AccountRepo;
import com.paperdesk.repo.MissionCompletionRepo;
import com.paperdesk.repo.OrderRepo;
import com.paperdesk.repo.PositionRepo;
import com.paperdesk.repo.SettlementRepo;
import com.paperdesk.sim.MarketDataService;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Computes step completion for every mission from the account's current
 * orders/positions/settlements (read-derived, not incrementally tracked), and
 * persists + awards XP the first time a mission's steps are all satisfied.
 * Called both for the read-only Progress tab and, fire-and-forget, after
 * fills and day-rolls so completion is caught and awarded promptly.
 */
@Service
public class MissionEvaluationService {

    public record StepResult(String description, boolean done) {}
    public record MissionResult(Mission mission, boolean completed, List<StepResult> steps) {}

    private final PositionRepo positionRepo;
    private final OrderRepo orderRepo;
    private final SettlementRepo settlementRepo;
    private final MissionCompletionRepo missionRepo;
    private final AccountRepo accountRepo;
    private final MarketDataService market;
    private final SimpMessagingTemplate ws;

    public MissionEvaluationService(PositionRepo positionRepo, OrderRepo orderRepo, SettlementRepo settlementRepo,
                                    MissionCompletionRepo missionRepo, AccountRepo accountRepo,
                                    MarketDataService market, SimpMessagingTemplate ws) {
        this.positionRepo = positionRepo;
        this.orderRepo = orderRepo;
        this.settlementRepo = settlementRepo;
        this.missionRepo = missionRepo;
        this.accountRepo = accountRepo;
        this.market = market;
        this.ws = ws;
    }

    /** Read-only: current step status for every mission (drives the Progress tab). */
    public List<MissionResult> evaluate(long accountId) {
        Facts facts = gatherFacts(accountId);
        return List.of(Mission.values()).stream().map(m -> result(m, facts)).toList();
    }

    /** Same evaluation, but persists + awards XP for any mission newly completed. Safe to call often. */
    @Transactional
    public void evaluateAndAward(Account account, LocalDate simDate) {
        Facts facts = gatherFacts(account.id);
        boolean dirty = false;
        for (Mission m : Mission.values()) {
            if (missionRepo.existsByAccountIdAndCode(account.id, m.name())) continue;
            if (!result(m, facts).completed()) continue;
            MissionCompletion mc = new MissionCompletion();
            mc.accountId = account.id;
            mc.code = m.name();
            mc.xp = m.xp;
            mc.simDate = simDate;
            missionRepo.save(mc);
            account.xp += m.xp;
            dirty = true;

            Map<String, Object> msg = new LinkedHashMap<>();
            msg.put("type", "MISSION_COMPLETE");
            msg.put("detail", m.title + " (+" + m.xp + " XP) — " + m.description);
            msg.put("code", m.name());
            ws.convertAndSend("/topic/account/" + account.id, msg);
        }
        if (dirty) accountRepo.save(account);
    }

    private MissionResult result(Mission m, Facts facts) {
        boolean[] done = switch (m) {
            case FIRST_STEPS -> new boolean[]{facts.hasAnyFill, facts.hasOpenPosition};
            case COVERED_CALL -> {
                boolean ownsShares = facts.equityQtyByUnderlying.values().stream().anyMatch(q -> q >= 100);
                boolean shortCall = facts.openOptions.stream().anyMatch(p ->
                        p.qty < 0 && instr(p).callPut == CallPut.CALL
                                && facts.equityQtyByUnderlying.getOrDefault(instr(p).underlyingId, 0.0) >= 100);
                yield new boolean[]{ownsShares, shortCall};
            }
            case PROTECTIVE_PUT -> {
                boolean ownsShares = facts.equityQtyByUnderlying.values().stream().anyMatch(q -> q > 0);
                boolean longPut = facts.openOptions.stream().anyMatch(p ->
                        p.qty > 0 && instr(p).callPut == CallPut.PUT
                                && facts.equityQtyByUnderlying.getOrDefault(instr(p).underlyingId, 0.0) > 0);
                yield new boolean[]{ownsShares, longPut};
            }
            case LONG_STRADDLE -> {
                boolean longCall = facts.openOptions.stream().anyMatch(p -> p.qty > 0 && instr(p).callPut == CallPut.CALL);
                boolean matchingPut = facts.openOptions.stream().anyMatch(p -> p.qty > 0 && instr(p).callPut == CallPut.PUT
                        && facts.openOptions.stream().anyMatch(c -> c.qty > 0 && instr(c).callPut == CallPut.CALL
                                && instr(c).underlyingId.equals(instr(p).underlyingId)
                                && instr(c).strike.equals(instr(p).strike)
                                && instr(c).expiryDate.equals(instr(p).expiryDate)));
                yield new boolean[]{longCall, matchingPut};
            }
            case FUTURES_LAB -> new boolean[]{facts.tradedFuture, facts.hasFuturesMtm};
            case FX_DESK -> new boolean[]{facts.tradedFxAsTrader, facts.executedFxSalesRfq};
            case SWAP_LAB -> new boolean[]{facts.tradedSwap, facts.hasSwapFixing};
        };
        List<StepResult> steps = new java.util.ArrayList<>();
        for (int i = 0; i < m.steps.length; i++) steps.add(new StepResult(m.steps[i], done[i]));
        boolean completed = steps.stream().allMatch(StepResult::done);
        return new MissionResult(m, completed, steps);
    }

    private Instrument instr(Position p) {
        return market.instrument(p.instrumentId);
    }

    private Facts gatherFacts(long accountId) {
        List<Position> open = positionRepo.findByAccountIdAndQtyNot(accountId, 0);
        List<TradeOrder> orders = orderRepo.findByAccountIdOrderByIdDesc(accountId);
        List<Settlement> settlements = settlementRepo.findByAccountIdOrderByIdDesc(accountId);

        Facts f = new Facts();
        f.hasAnyFill = orders.stream().anyMatch(o -> o.status == OrderStatus.FILLED);
        f.hasOpenPosition = !open.isEmpty();

        for (Position p : open) {
            Instrument i = market.instrument(p.instrumentId);
            if (i.instrumentType == InstrumentType.EQUITY) {
                f.equityQtyByUnderlying.merge(i.id, p.qty, Double::sum);
            } else if (i.instrumentType == InstrumentType.OPTION) {
                f.openOptions.add(p);
            }
        }

        f.tradedFuture = orders.stream().anyMatch(o -> o.status == OrderStatus.FILLED
                && market.instrument(o.instrumentId).instrumentType == InstrumentType.FUTURE);
        f.hasFuturesMtm = settlements.stream().anyMatch(s -> s.kind == SettlementKind.FUTURES_MTM);
        f.tradedFxAsTrader = orders.stream().anyMatch(o -> o.status == OrderStatus.FILLED
                && o.viewContext == ViewContext.TRADER
                && isFx(market.instrument(o.instrumentId).instrumentType));
        f.executedFxSalesRfq = orders.stream().anyMatch(o -> o.status == OrderStatus.FILLED
                && o.viewContext == ViewContext.SALES);
        f.tradedSwap = orders.stream().anyMatch(o -> o.status == OrderStatus.FILLED
                && market.instrument(o.instrumentId).instrumentType == InstrumentType.SWAP);
        f.hasSwapFixing = settlements.stream().anyMatch(s -> s.kind == SettlementKind.SWAP_FIXING);
        return f;
    }

    private boolean isFx(InstrumentType t) {
        return t == InstrumentType.FX_PAIR || t == InstrumentType.FX_OPTION;
    }

    private static class Facts {
        boolean hasAnyFill, hasOpenPosition, tradedFuture, hasFuturesMtm,
                tradedFxAsTrader, executedFxSalesRfq, tradedSwap, hasSwapFixing;
        final Map<Long, Double> equityQtyByUnderlying = new LinkedHashMap<>();
        final List<Position> openOptions = new java.util.ArrayList<>();
    }
}
