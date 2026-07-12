package com.paperdesk;

import com.paperdesk.domain.Account;
import com.paperdesk.domain.Enums.*;
import com.paperdesk.domain.Instrument;
import com.paperdesk.domain.Scenario;
import com.paperdesk.domain.ScenarioSession;
import com.paperdesk.domain.TradeOrder;
import com.paperdesk.domain.User;
import com.paperdesk.repo.*;
import com.paperdesk.sim.SessionService;
import com.paperdesk.sim.SimEngine;
import com.paperdesk.trading.OrderService;
import com.paperdesk.trading.PortfolioService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end mechanics through the real Spring context (H2 PostgreSQL mode,
 * Flyway schema, seeded scenarios) with the clock driven manually.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SimIntegrationTest {

    @Autowired SimEngine engine;
    @Autowired SessionService sessions;
    @Autowired OrderService orders;
    @Autowired PortfolioService portfolio;
    @Autowired ScenarioRepo scenarioRepo;
    @Autowired ScenarioSessionRepo sessionRepo;
    @Autowired AccountRepo accountRepo;
    @Autowired InstrumentRepo instrumentRepo;
    @Autowired PositionRepo positionRepo;
    @Autowired SettlementRepo settlementRepo;
    @Autowired UserRepo userRepo;

    long calmScenarioId;
    long userId;

    @BeforeAll
    void setup() {
        calmScenarioId = scenarioRepo.findByName("Calm market").orElseThrow().id;
        User u = new User();
        u.email = "student@test.io";
        u.passwordHash = "x";
        u.displayName = "Test Student";
        u.role = Role.STUDENT;
        userRepo.save(u);
        userId = u.id;
    }

    private Account newAccount(long scenarioId) {
        ScenarioSession session = sessions.createSession(scenarioId, null);
        return sessions.openAccount(userId, session.id, 100000);
    }

    private Instrument bySymbolPrefix(long sessionId, InstrumentType type, String prefix) {
        return instrumentRepo.findBySessionIdAndInstrumentTypeAndActiveTrue(sessionId, type).stream()
                .filter(i -> i.symbol.startsWith(prefix))
                .findFirst().orElseThrow();
    }

    @Test
    void sameSeedProducesIdenticalMarkets() {
        ScenarioSession a = sessions.createSession(calmScenarioId, null);
        ScenarioSession b = sessions.createSession(calmScenarioId, null);
        for (int i = 0; i < 3; i++) {
            engine.stepOneDay(a.id);
            engine.stepOneDay(b.id);
        }
        Map<String, Double> pa = engine.runtime(a.id).currentPrices();
        Map<String, Double> pb = engine.runtime(b.id).currentPrices();
        assertEquals(pa, pb, "same seed + same steps must reproduce the identical market");
        assertNotEquals(pa.get("ACME"), 100.0, "prices actually moved");
    }

    @Test
    void spotMarketOrderFillsAndUpdatesCashAndPosition() {
        Account acct = newAccount(calmScenarioId);
        Instrument acme = instrumentRepo.findBySessionIdAndSymbol(acct.sessionId, "ACME").orElseThrow();
        TradeOrder order = orders.place(acct.id, acme.id, OrderSide.BUY, OrderType.MARKET, 100, null, null);
        assertEquals(OrderStatus.FILLED, order.status);

        Account after = accountRepo.findById(acct.id).orElseThrow();
        assertTrue(after.cashBalance < 100000, "cash reduced by purchase");
        var pos = positionRepo.findByAccountIdAndInstrumentId(acct.id, acme.id).orElseThrow();
        assertEquals(100, pos.qty, 1e-9);
        assertTrue(pos.avgPrice > 90 && pos.avgPrice < 110);

        // equity should be ~100k minus the half-spread paid
        var view = portfolio.portfolio(acct.id);
        assertEquals(100000, view.equity(), 100);
    }

    @Test
    void limitOrderRestsThenCancels() {
        Account acct = newAccount(calmScenarioId);
        Instrument acme = instrumentRepo.findBySessionIdAndSymbol(acct.sessionId, "ACME").orElseThrow();
        double farBelow = engine.runtime(acct.sessionId).price("ACME") * 0.5;
        TradeOrder order = orders.place(acct.id, acme.id, OrderSide.BUY, OrderType.LIMIT, 10, farBelow, null);
        assertEquals(OrderStatus.NEW, order.status, "limit far below market should rest");

        TradeOrder cancelled = orders.cancel(order.id, acct.id);
        assertEquals(OrderStatus.CANCELLED, cancelled.status);
    }

    @Test
    void marketableLimitFillsImmediately() {
        Account acct = newAccount(calmScenarioId);
        Instrument acme = instrumentRepo.findBySessionIdAndSymbol(acct.sessionId, "ACME").orElseThrow();
        double farAbove = engine.runtime(acct.sessionId).price("ACME") * 1.5;
        TradeOrder order = orders.place(acct.id, acme.id, OrderSide.BUY, OrderType.LIMIT, 10, farAbove, null);
        assertEquals(OrderStatus.FILLED, order.status, "marketable limit crosses instantly");
    }

    @Test
    void insufficientCashIsRejected() {
        Account acct = newAccount(calmScenarioId);
        Instrument nimbus = instrumentRepo.findBySessionIdAndSymbol(acct.sessionId, "NIMBUS").orElseThrow();
        TradeOrder order = orders.place(acct.id, nimbus.id, OrderSide.BUY, OrderType.MARKET, 10000, null, null);
        assertEquals(OrderStatus.REJECTED, order.status);
        assertNotNull(order.rejectReason);
    }

    @Test
    void optionExpiresAndCashSettles() {
        Account acct = newAccount(calmScenarioId);
        // deep ITM call (lowest strike, nearest expiry) so it settles with intrinsic value
        Instrument acme = instrumentRepo.findBySessionIdAndSymbol(acct.sessionId, "ACME").orElseThrow();
        List<Instrument> chain = instrumentRepo
                .findBySessionIdAndUnderlyingIdAndInstrumentTypeAndActiveTrue(acct.sessionId, acme.id, InstrumentType.OPTION);
        Instrument call = chain.stream()
                .filter(o -> o.callPut == CallPut.CALL)
                .min((x, y) -> {
                    int c = x.expiryDate.compareTo(y.expiryDate);
                    return c != 0 ? c : Double.compare(x.strike, y.strike);
                }).orElseThrow();

        TradeOrder order = orders.place(acct.id, call.id, OrderSide.BUY, OrderType.MARKET, 1, null, null);
        assertEquals(OrderStatus.FILLED, order.status);

        LocalDate expiry = call.expiryDate;
        while (!engine.runtime(acct.sessionId).simDate().isAfter(expiry)) {
            engine.stepOneDay(acct.sessionId);
        }

        var pos = positionRepo.findByAccountIdAndInstrumentId(acct.id, call.id).orElseThrow();
        assertEquals(0, pos.qty, 1e-9, "option position closed at expiry");
        Instrument after = instrumentRepo.findById(call.id).orElseThrow();
        assertFalse(after.active, "expired instrument deactivated");
        boolean settled = settlementRepo.findByAccountIdOrderByIdDesc(acct.id).stream()
                .anyMatch(s -> s.kind == SettlementKind.OPTION_EXERCISE || s.kind == SettlementKind.OPTION_EXPIRY);
        assertTrue(settled, "an expiry settlement event was recorded");
    }

    @Test
    void futuresDailySettlementAndMargin() {
        Account acct = newAccount(calmScenarioId);
        Instrument fut = bySymbolPrefix(acct.sessionId, InstrumentType.FUTURE, "ACME-FUT");

        TradeOrder order = orders.place(acct.id, fut.id, OrderSide.BUY, OrderType.MARKET, 2, null, null);
        assertEquals(OrderStatus.FILLED, order.status);
        Account after = accountRepo.findById(acct.id).orElseThrow();
        assertEquals(2 * fut.initialMargin, after.marginHeld, 1e-6, "initial margin held");
        assertEquals(100000 - 2 * fut.initialMargin, after.cashBalance, 1e-6, "no premium, only margin moved");

        engine.stepOneDay(acct.sessionId);
        boolean mtm = settlementRepo.findByAccountIdOrderByIdDesc(acct.id).stream()
                .anyMatch(s -> s.kind == SettlementKind.FUTURES_MTM);
        assertTrue(mtm, "daily mark-to-market posted");
        var pos = positionRepo.findByAccountIdAndInstrumentId(acct.id, fut.id).orElseThrow();
        assertNotNull(pos.lastSettlePrice, "settle price recorded on the position");
    }

    @Test
    void swapFixingPostsCash() {
        Account acct = newAccount(calmScenarioId);
        Instrument swap = bySymbolPrefix(acct.sessionId, InstrumentType.SWAP, "IRS-6M");
        TradeOrder order = orders.place(acct.id, swap.id, OrderSide.BUY, OrderType.MARKET, 1, null, null);
        assertEquals(OrderStatus.FILLED, order.status);

        for (int i = 0; i < 31; i++) engine.stepOneDay(acct.sessionId);
        boolean fixed = settlementRepo.findByAccountIdOrderByIdDesc(acct.id).stream()
                .anyMatch(s -> s.kind == SettlementKind.SWAP_FIXING);
        assertTrue(fixed, "monthly fixing posted after 30 sim days");
    }
}
