package com.paperdesk.coach;

import com.paperdesk.domain.Account;
import com.paperdesk.domain.Fill;
import com.paperdesk.domain.Instrument;
import com.paperdesk.domain.TradeOrder;
import com.paperdesk.trading.PortfolioService.PositionView;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/**
 * "Explain this trade" -- a short, plain-language explanation of a single order, grounded only in
 * that order's own fills/instrument/Greeks/account data (never invented). Degrades gracefully to
 * configured=false when no ANTHROPIC_API_KEY is set, rather than failing startup: unlike the JWT
 * secret, this is a bonus feature the app is fully usable without.
 */
@Service
public class TradingCoachService {

    public record CoachExplanation(boolean configured, String explanation, String model, String error) {}

    private static final String SYSTEM_PROMPT = """
            You are an embedded trading coach on Paper Desk, a simulated (risk-free, no real money)
            trading platform for finance students. Explain the single trade described in the user
            message, using ONLY the data given there -- never invent prices, Greeks, or figures that
            aren't present. Cover in plain prose, no headers or bullet lists: (1) what the trade is
            (instrument, direction, size), (2) its payoff/risk profile in a sentence or two, using the
            Greeks if provided to explain what they mean for this specific position, and (3) one
            concrete thing the student should watch for given the position shown. Keep it to 4-6 short
            sentences, encouraging but honest, written for a student who is still learning -- and skip
            investment-advice disclaimers since this is a simulation, not real trading.""";

    private final AnthropicClient client;
    private final boolean configured;
    private final String model;

    public TradingCoachService(AnthropicClient client,
                               @Value("${paperdesk.coach.api-key:}") String apiKey,
                               @Value("${paperdesk.coach.model}") String model) {
        this.client = client;
        this.configured = apiKey != null && !apiKey.isBlank();
        this.model = model;
    }

    public CoachExplanation explain(TradeOrder order, List<Fill> fills, Instrument instrument,
                                    PositionView position, Account account) {
        if (!configured) {
            return new CoachExplanation(false, null, model, null);
        }
        try {
            String explanation = client.complete(SYSTEM_PROMPT, buildPrompt(order, fills, instrument, position, account));
            return new CoachExplanation(true, explanation, model, null);
        } catch (Exception e) {
            return new CoachExplanation(true, null, model, "The coach is temporarily unavailable, try again shortly.");
        }
    }

    private String buildPrompt(TradeOrder order, List<Fill> fills, Instrument instrument,
                               PositionView position, Account account) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT, "Trade: %s %s %s (%s)%n",
                order.side, fmt(order.qty), instrument.symbol, instrument.instrumentType));
        if (instrument.callPut != null) {
            sb.append(String.format(Locale.ROOT, "Option detail: %s, strike %s, expiry %s%n",
                    instrument.callPut, fmt(instrument.strike), instrument.expiryDate));
        }
        sb.append("Order status: ").append(order.status).append('\n');
        if (order.rejectReason != null) sb.append("Reject reason: ").append(order.rejectReason).append('\n');
        for (Fill f : fills) {
            sb.append(String.format(Locale.ROOT, "Fill: %s @ %s (sim time %s)%n",
                    fmt(f.qty), fmt(f.price), f.fillSimTime));
        }
        if (position != null) {
            sb.append(String.format(Locale.ROOT,
                    "Current position in this instrument: qty %s, avg price %s, mark %s, unrealized P&L %s%n",
                    fmt(position.qty()), fmt(position.avgPrice()), fmt(position.mark()), fmt(position.unrealizedPnl())));
            if (position.delta() != null) {
                sb.append(String.format(Locale.ROOT, "Greeks (per contract, at current mark): delta %s, gamma %s, theta %s, vega %s%n",
                        fmt(position.delta()), fmt(position.gamma()), fmt(position.theta()), fmt(position.vega())));
            }
            if (position.marginUsed() != null) {
                sb.append("Margin used: ").append(fmt(position.marginUsed())).append('\n');
            }
        }
        sb.append(String.format(Locale.ROOT, "Account: cash %s%n", fmt(account.cashBalance)));
        return sb.toString();
    }

    private static String fmt(Double d) {
        return d == null ? "n/a" : fmt(d.doubleValue());
    }

    private static String fmt(double d) {
        return String.format(Locale.ROOT, "%.4f", d);
    }
}
