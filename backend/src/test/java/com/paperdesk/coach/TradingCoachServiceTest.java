package com.paperdesk.coach;

import com.paperdesk.domain.Account;
import com.paperdesk.domain.Enums.CallPut;
import com.paperdesk.domain.Enums.InstrumentType;
import com.paperdesk.domain.Enums.OrderSide;
import com.paperdesk.domain.Enums.OrderStatus;
import com.paperdesk.domain.Enums.OrderType;
import com.paperdesk.domain.Fill;
import com.paperdesk.domain.Instrument;
import com.paperdesk.domain.TradeOrder;
import com.paperdesk.trading.PortfolioService.PositionView;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Plain-JUnit unit test (no Spring context) -- exercises the not-configured degrade path, the
 *  configured/success path against a fake AnthropicClient, and the configured/error path. */
class TradingCoachServiceTest {

    private TradeOrder order() {
        TradeOrder o = new TradeOrder();
        o.id = 1L;
        o.accountId = 10L;
        o.instrumentId = 20L;
        o.side = OrderSide.BUY;
        o.orderType = OrderType.MARKET;
        o.qty = 5;
        o.status = OrderStatus.FILLED;
        return o;
    }

    private Instrument equity() {
        Instrument i = new Instrument();
        i.id = 20L;
        i.symbol = "ACME";
        i.instrumentType = InstrumentType.EQUITY;
        return i;
    }

    private Instrument option() {
        Instrument i = new Instrument();
        i.id = 20L;
        i.symbol = "ACME_C150";
        i.instrumentType = InstrumentType.OPTION;
        i.callPut = CallPut.CALL;
        i.strike = 150.0;
        i.expiryDate = LocalDate.of(2026, 8, 15);
        return i;
    }

    private Account account() {
        Account a = new Account();
        a.id = 10L;
        a.cashBalance = 92000.0;
        return a;
    }

    private List<Fill> fills() {
        Fill f = new Fill();
        f.orderId = 1L;
        f.price = 3.25;
        f.qty = 5;
        f.fillSimTime = Instant.parse("2026-07-10T14:00:00Z");
        return List.of(f);
    }

    @Test
    void notConfiguredWhenApiKeyIsBlank() {
        TradingCoachService coach = new TradingCoachService(
                (system, user) -> { throw new AssertionError("client should never be called when not configured"); },
                "", "claude-haiku-4-5-20251001");

        var result = coach.explain(order(), fills(), equity(), null, account());

        assertFalse(result.configured());
        assertNull(result.explanation());
        assertNull(result.error());
    }

    @Test
    void configuredAndSuccessfulReturnsTheExplanationVerbatim() {
        TradingCoachService coach = new TradingCoachService(
                (system, user) -> "You bought ACME because you think it will go up.",
                "sk-ant-test-key", "claude-haiku-4-5-20251001");

        PositionView position = new PositionView(1, 20, "ACME", "Acme Corp", "EQUITY",
                5, 3.25, 3.40, 17.0, 0.75, 0.0, null, null, null, null, null, null, null);

        var result = coach.explain(order(), fills(), equity(), position, account());

        assertTrue(result.configured());
        assertEquals("You bought ACME because you think it will go up.", result.explanation());
        assertNull(result.error());
        assertEquals("claude-haiku-4-5-20251001", result.model());
    }

    @Test
    void configuredButClientThrowsReturnsAFriendlyError() {
        TradingCoachService coach = new TradingCoachService(
                (system, user) -> { throw new RuntimeException("HTTP 500"); },
                "sk-ant-test-key", "claude-haiku-4-5-20251001");

        var result = coach.explain(order(), fills(), option(), null, account());

        assertTrue(result.configured());
        assertNull(result.explanation());
        assertNotNull(result.error());
    }

    @Test
    void promptBuildingHandlesOptionInstrumentsWithGreeksWithoutThrowing() {
        TradingCoachService coach = new TradingCoachService(
                (system, user) -> {
                    assertTrue(user.contains("Option detail"));
                    assertTrue(user.contains("Greeks"));
                    return "Explanation referencing the Greeks.";
                },
                "sk-ant-test-key", "claude-haiku-4-5-20251001");

        PositionView position = new PositionView(2, 20, "ACME_C150", "Acme 150 Call", "OPTION",
                5, 3.25, 3.40, 17.0, 0.75, 0.0, 0.55, 0.03, -0.08, 0.12, 500.0, null, "2026-08-15");

        var result = coach.explain(order(), fills(), option(), position, account());

        assertTrue(result.configured());
        assertEquals("Explanation referencing the Greeks.", result.explanation());
    }
}
