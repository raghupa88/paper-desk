package com.paperdesk.pricing;

import com.paperdesk.domain.Enums.CallPut;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlackScholesTest {

    // Textbook reference: S=100, K=100, T=1y, vol=20%, r=5%, q=0
    @Test
    void matchesTextbookCallValue() {
        var g = BlackScholes.price(CallPut.CALL, 100, 100, 1, 0.20, 0.05, 0);
        assertEquals(10.4506, g.price(), 1e-3);
        assertEquals(0.6368, g.delta(), 1e-3);
        assertEquals(0.0188, g.gamma(), 1e-3);
        assertEquals(-6.414 / 365.0, g.theta(), 1e-3);   // annual theta -6.414 -> per day
        assertEquals(37.524 / 100.0, g.vega(), 1e-3);    // per 1% vol
    }

    @Test
    void matchesTextbookPutValue() {
        var g = BlackScholes.price(CallPut.PUT, 100, 100, 1, 0.20, 0.05, 0);
        assertEquals(5.5735, g.price(), 1e-3);
        assertEquals(0.6368 - 1, g.delta(), 1e-3);
    }

    @Test
    void putCallParityHoldsWithCarryYield() {
        double s = 105, k = 98, t = 0.75, vol = 0.3, r = 0.04, q = 0.02;
        double call = BlackScholes.price(CallPut.CALL, s, k, t, vol, r, q).price();
        double put = BlackScholes.price(CallPut.PUT, s, k, t, vol, r, q).price();
        double parity = s * Math.exp(-q * t) - k * Math.exp(-r * t);
        assertEquals(parity, call - put, 1e-6);
    }

    /** Garman-Kohlhagen is BS with q = foreign rate; sanity vs published example. */
    @Test
    void garmanKohlhagenFxOption() {
        // EURUSD 1.10 spot, 1.10 strike, 6m, 8% vol, USD 4.5%, EUR 2.5%
        var g = BlackScholes.price(CallPut.CALL, 1.10, 1.10, 0.5, 0.08, 0.045, 0.025);
        double call = g.price();
        double put = BlackScholes.price(CallPut.PUT, 1.10, 1.10, 0.5, 0.08, 0.045, 0.025).price();
        double parity = 1.10 * Math.exp(-0.025 * 0.5) - 1.10 * Math.exp(-0.045 * 0.5);
        assertEquals(parity, call - put, 1e-9);
        assertTrue(call > 0.02 && call < 0.05, "premium in a plausible band, got " + call);
    }

    @Test
    void expiredOptionIsIntrinsic() {
        assertEquals(7, BlackScholes.price(CallPut.CALL, 107, 100, 0, 0.2, 0.05, 0).price(), 1e-12);
        assertEquals(0, BlackScholes.price(CallPut.CALL, 93, 100, 0, 0.2, 0.05, 0).price(), 1e-12);
        assertEquals(7, BlackScholes.price(CallPut.PUT, 93, 100, 0, 0.2, 0.05, 0).price(), 1e-12);
    }

    @Test
    void deepItmCallDeltaApproachesOne() {
        var g = BlackScholes.price(CallPut.CALL, 200, 100, 0.25, 0.2, 0.05, 0);
        assertTrue(g.delta() > 0.999);
    }
}
