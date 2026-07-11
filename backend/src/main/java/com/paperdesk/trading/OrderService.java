package com.paperdesk.trading;

import com.paperdesk.domain.Account;
import com.paperdesk.domain.Enums.*;
import com.paperdesk.domain.Fill;
import com.paperdesk.domain.Instrument;
import com.paperdesk.domain.Position;
import com.paperdesk.domain.TradeOrder;
import com.paperdesk.gamification.GamificationService;
import com.paperdesk.gamification.MissionEvaluationService;
import com.paperdesk.repo.AccountRepo;
import com.paperdesk.repo.FillRepo;
import com.paperdesk.repo.OrderRepo;
import com.paperdesk.repo.PositionRepo;
import com.paperdesk.sim.MarketDataService;
import com.paperdesk.sim.SimEngine;
import com.paperdesk.sim.SimEvents;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Order placement, the simplified matching model (market orders fill at
 * mid +/- spread, limit orders rest and fill when the simulated price crosses),
 * and the position/cash/margin bookkeeping for every instrument type.
 */
@Service
public class OrderService {

    private final OrderRepo orderRepo;
    private final FillRepo fillRepo;
    private final AccountRepo accountRepo;
    private final PositionRepo positionRepo;
    private final MarketDataService market;
    private final SimEngine engine;
    private final SimpMessagingTemplate ws;
    private final GamificationService gamification;
    private final MissionEvaluationService missions;

    public OrderService(OrderRepo orderRepo, FillRepo fillRepo, AccountRepo accountRepo,
                        PositionRepo positionRepo, MarketDataService market, SimEngine engine,
                        SimpMessagingTemplate ws, GamificationService gamification,
                        MissionEvaluationService missions) {
        this.orderRepo = orderRepo;
        this.fillRepo = fillRepo;
        this.accountRepo = accountRepo;
        this.positionRepo = positionRepo;
        this.market = market;
        this.engine = engine;
        this.ws = ws;
        this.gamification = gamification;
        this.missions = missions;
    }

    @Transactional
    public TradeOrder place(long accountId, long instrumentId, OrderSide side, OrderType type,
                            double qty, Double limitPrice, ViewContext viewContext) {
        if (qty <= 0) throw new IllegalArgumentException("Quantity must be positive");
        if (type == OrderType.LIMIT && (limitPrice == null || limitPrice <= 0))
            throw new IllegalArgumentException("Limit orders need a positive limit price");
        Account account = accountRepo.findById(accountId).orElseThrow();
        Instrument instr = market.instrument(instrumentId);
        if (!instr.active) throw new IllegalArgumentException("Instrument no longer tradeable");
        if (!instr.sessionId.equals(account.sessionId))
            throw new IllegalArgumentException("Instrument belongs to a different session");

        TradeOrder order = new TradeOrder();
        order.accountId = accountId;
        order.instrumentId = instrumentId;
        order.side = side;
        order.orderType = type;
        order.limitPrice = limitPrice;
        order.qty = qty;
        order.viewContext = viewContext;
        order.placedSimTime = engine.runtime(instr.sessionId).simTime;
        orderRepo.save(order);

        MarketDataService.Quote quote = market.quote(instr);
        if (type == OrderType.MARKET) {
            tryExecute(order, account, instr, side == OrderSide.BUY ? quote.ask() : quote.bid());
        } else if (isCrossed(order, quote)) {
            // marketable limit: fill at the better of the limit and the current market
            double px = side == OrderSide.BUY ? Math.min(quote.ask(), order.limitPrice)
                                              : Math.max(quote.bid(), order.limitPrice);
            tryExecute(order, account, instr, px);
        }
        return order;
    }

    /** Execute an already-priced fill directly (used by the FX sales RFQ flow). */
    @Transactional
    public TradeOrder placeAtPrice(long accountId, long instrumentId, OrderSide side, double qty,
                                   double price, ViewContext viewContext) {
        Account account = accountRepo.findById(accountId).orElseThrow();
        Instrument instr = market.instrument(instrumentId);
        TradeOrder order = new TradeOrder();
        order.accountId = accountId;
        order.instrumentId = instrumentId;
        order.side = side;
        order.orderType = OrderType.MARKET;
        order.qty = qty;
        order.viewContext = viewContext;
        order.placedSimTime = engine.runtime(instr.sessionId).simTime;
        orderRepo.save(order);
        tryExecute(order, account, instr, price);
        return order;
    }

    @Transactional
    public TradeOrder cancel(long orderId, long accountId) {
        TradeOrder order = orderRepo.findById(orderId).orElseThrow();
        if (!order.accountId.equals(accountId)) throw new IllegalArgumentException("Not your order");
        if (order.status != OrderStatus.NEW) throw new IllegalArgumentException("Order is not open");
        order.status = OrderStatus.CANCELLED;
        orderRepo.save(order);
        notifyAccount(order.accountId, "ORDER_CANCELLED", order.id);
        return order;
    }

    /** Resting limit orders are re-checked against the fresh prices on every engine tick. */
    @EventListener
    @Transactional
    public void onSessionTicked(SimEvents.SessionTicked evt) {
        List<TradeOrder> open = orderRepo.findOpenBySession(OrderStatus.NEW, evt.sessionId());
        for (TradeOrder order : open) {
            Instrument instr = market.instrument(order.instrumentId);
            if (!instr.active) {
                order.status = OrderStatus.CANCELLED;
                order.rejectReason = "Instrument expired";
                orderRepo.save(order);
                continue;
            }
            MarketDataService.Quote quote = market.quote(instr);
            if (isCrossed(order, quote)) {
                Account account = accountRepo.findById(order.accountId).orElseThrow();
                tryExecute(order, account, instr, order.limitPrice);
            }
        }
    }

    private boolean isCrossed(TradeOrder order, MarketDataService.Quote quote) {
        if (order.orderType != OrderType.LIMIT) return false;
        return order.side == OrderSide.BUY ? quote.ask() <= order.limitPrice
                                           : quote.bid() >= order.limitPrice;
    }

    private void tryExecute(TradeOrder order, Account account, Instrument instr, double price) {
        String rejection = checkFunds(order, account, instr, price);
        if (rejection != null) {
            order.status = OrderStatus.REJECTED;
            order.rejectReason = rejection;
            orderRepo.save(order);
            notifyAccount(account.id, "ORDER_REJECTED", order.id);
            return;
        }
        applyFill(order, account, instr, price);
        LocalDate simDate = engine.runtime(instr.sessionId).simDate();
        gamification.onFill(account, instr, order, simDate);
        Fill fill = new Fill();
        fill.orderId = order.id;
        fill.price = price;
        fill.qty = order.qty;
        fill.fillSimTime = engine.runtime(instr.sessionId).simTime;
        fillRepo.save(fill);
        order.status = OrderStatus.FILLED;
        orderRepo.save(order);
        // MissionEvaluationService re-queries orders/positions/settlements fresh from the
        // repos rather than using this in-memory order, so it must run only after this
        // order's FILLED status is persisted — otherwise a mission whose steps this very
        // fill completes (e.g. "place a trade that fills") won't be detected until the
        // *next* fill, since its own order still reads back as NEW.
        missions.evaluateAndAward(account, simDate);
        accountRepo.save(account);
        notifyAccount(account.id, "FILL", order.id);
    }

    private String checkFunds(TradeOrder order, Account account, Instrument instr, double price) {
        double signedQty = order.side == OrderSide.BUY ? order.qty : -order.qty;
        switch (instr.instrumentType) {
            case EQUITY, FX_PAIR, OPTION, FX_OPTION -> {
                double cost = price * order.qty * instr.contractSize;
                if (order.side == OrderSide.BUY && cost > account.cashBalance)
                    return "Insufficient cash: need " + Math.round(cost) + ", have " + Math.round(account.cashBalance);
            }
            case FUTURE -> {
                Position pos = positionRepo.findByAccountIdAndInstrumentId(account.id, instr.id).orElse(null);
                double current = pos == null ? 0 : pos.qty;
                double addedContracts = Math.abs(current + signedQty) - Math.abs(current);
                if (addedContracts > 0) {
                    double needed = addedContracts * instr.initialMargin;
                    if (needed > account.cashBalance)
                        return "Insufficient margin: need " + Math.round(needed) + ", have " + Math.round(account.cashBalance);
                }
            }
            case FORWARD, SWAP -> { /* OTC teaching instruments: no upfront cash or margin */ }
        }
        return null;
    }

    /** Nets the fill into the position and applies the cash/margin flows for the instrument type. */
    private void applyFill(TradeOrder order, Account account, Instrument instr, double price) {
        double signedQty = order.side == OrderSide.BUY ? order.qty : -order.qty;
        Position pos = positionRepo.findByAccountIdAndInstrumentId(account.id, instr.id)
                .orElseGet(() -> {
                    Position p = new Position();
                    p.accountId = account.id;
                    p.instrumentId = instr.id;
                    p.openedSimTime = engine.runtime(instr.sessionId).simTime;
                    return p;
                });

        boolean cashInstrument = switch (instr.instrumentType) {
            case EQUITY, FX_PAIR, OPTION, FX_OPTION -> true;
            default -> false;
        };
        boolean margined = instr.instrumentType == InstrumentType.FUTURE;

        double oldQty = pos.qty;
        double newQty = oldQty + signedQty;
        double basis = margined && pos.lastSettlePrice != null ? pos.lastSettlePrice : pos.avgPrice;

        if (oldQty == 0 || Math.signum(oldQty) == Math.signum(signedQty)) {
            pos.avgPrice = (pos.avgPrice * Math.abs(oldQty) + price * Math.abs(signedQty)) / Math.abs(newQty);
        } else {
            double closed = Math.min(Math.abs(oldQty), Math.abs(signedQty));
            double realized = (price - basis) * closed * Math.signum(oldQty) * instr.contractSize;
            pos.realizedPnl += realized;
            if (!cashInstrument) account.cashBalance += realized; // cash instruments realize via proceeds
            if (Math.abs(signedQty) > Math.abs(oldQty)) {
                pos.avgPrice = price; // flipped through zero: remainder opens at the fill price
                if (margined) pos.lastSettlePrice = null;
            }
        }
        pos.qty = newQty;
        if (newQty == 0) {
            pos.avgPrice = 0;
            pos.lastSettlePrice = null;
        }

        if (cashInstrument) {
            account.cashBalance -= price * signedQty * instr.contractSize;
        }
        if (margined) {
            double marginDelta = (Math.abs(newQty) - Math.abs(oldQty)) * instr.initialMargin;
            account.cashBalance -= marginDelta;
            account.marginHeld += marginDelta;
            if (Math.abs(newQty) > Math.abs(oldQty) && pos.lastSettlePrice == null) {
                pos.lastSettlePrice = null; // marks start from the fill price via avgPrice basis
            }
        }
        positionRepo.save(pos);
    }

    public void notifyAccount(long accountId, String type, Object detail) {
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("type", type);
        msg.put("detail", detail);
        ws.convertAndSend("/topic/account/" + accountId, msg);
    }
}
