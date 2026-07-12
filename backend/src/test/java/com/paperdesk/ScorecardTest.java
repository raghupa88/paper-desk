package com.paperdesk;

import com.paperdesk.domain.Account;
import com.paperdesk.domain.ClosedTrade;
import com.paperdesk.domain.Enums.*;
import com.paperdesk.domain.Instrument;
import com.paperdesk.domain.ScenarioSession;
import com.paperdesk.domain.TradeOrder;
import com.paperdesk.domain.User;
import com.paperdesk.repo.*;
import com.paperdesk.sim.SessionService;
import com.paperdesk.trading.OrderService;
import com.paperdesk.trading.PortfolioService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Verifies closed-trade recording in OrderService.applyFill() and the scorecard rollup. */
@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ScorecardTest {

    @Autowired OrderService orders;
    @Autowired PortfolioService portfolio;
    @Autowired ScenarioRepo scenarioRepo;
    @Autowired SessionService sessions;
    @Autowired AccountRepo accountRepo;
    @Autowired InstrumentRepo instrumentRepo;
    @Autowired ClosedTradeRepo closedTradeRepo;
    @Autowired UserRepo userRepo;

    long calmScenarioId;
    long userId;

    @BeforeAll
    void setup() {
        calmScenarioId = scenarioRepo.findByName("Calm market").orElseThrow().id;
        User u = new User();
        u.email = "scorecard@test.io";
        u.passwordHash = "x";
        u.displayName = "Scorecard Student";
        u.role = Role.STUDENT;
        userRepo.save(u);
        userId = u.id;
    }

    private Account newAccount() {
        ScenarioSession session = sessions.createSession(calmScenarioId, null);
        return sessions.openAccount(userId, session.id, 100000);
    }

    @Test
    void closingATradeAtTheSameQuoteRecordsALossFromTheSpread() {
        Account acct = newAccount();
        Instrument acme = instrumentRepo.findBySessionIdAndSymbol(acct.sessionId, "ACME").orElseThrow();

        orders.place(acct.id, acme.id, OrderSide.BUY, OrderType.MARKET, 10, null, null);
        TradeOrder closeOrder = orders.place(acct.id, acme.id, OrderSide.SELL, OrderType.MARKET, 10, null, null);
        assertEquals(OrderStatus.FILLED, closeOrder.status);

        List<ClosedTrade> trades = closedTradeRepo.findByAccountIdOrderByClosedSimTimeDesc(acct.id);
        assertEquals(1, trades.size());
        ClosedTrade trade = trades.get(0);
        assertEquals(10, trade.qty, 1e-9);
        assertTrue(trade.realizedPnl < 0, "buy at ask then sell at bid realizes a spread loss");
        assertEquals(trade.openedSimTime, trade.closedSimTime, "no engine tick occurred between open and close");

        PortfolioService.ScorecardView card = portfolio.scorecard(acct.id);
        assertEquals(1, card.totalTrades());
        assertEquals(0, card.wins());
        assertEquals(1, card.losses());
        assertEquals(0, card.winRatePct(), 1e-9);
        assertEquals(0, card.avgWin(), 1e-9);
        assertTrue(card.avgLoss() < 0);
        assertEquals(0, card.avgHoldingPeriodHours(), 1e-9);
    }

    @Test
    void reopeningAFlatPositionResetsTheHoldingPeriodStart() {
        Account acct = newAccount();
        Instrument acme = instrumentRepo.findBySessionIdAndSymbol(acct.sessionId, "ACME").orElseThrow();

        orders.place(acct.id, acme.id, OrderSide.BUY, OrderType.MARKET, 5, null, null);
        orders.place(acct.id, acme.id, OrderSide.SELL, OrderType.MARKET, 5, null, null); // flat again
        var reopenTime = orders.place(acct.id, acme.id, OrderSide.BUY, OrderType.MARKET, 3, null, null).placedSimTime;

        assertEquals(1, closedTradeRepo.findByAccountIdOrderByClosedSimTimeDesc(acct.id).size(),
                "only the first close produced a closed trade so far");

        TradeOrder finalClose = orders.place(acct.id, acme.id, OrderSide.SELL, OrderType.MARKET, 3, null, null);
        assertEquals(OrderStatus.FILLED, finalClose.status);
        List<ClosedTrade> trades = closedTradeRepo.findByAccountIdOrderByClosedSimTimeDesc(acct.id);
        assertEquals(2, trades.size(), "the reopen-then-close cycle produced a second closed trade");
        assertEquals(reopenTime, trades.get(0).openedSimTime, "holding period restarted at the reopening fill");
    }
}
