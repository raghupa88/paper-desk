package com.paperdesk.pricing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SwapsAndForwardsTest {

    @Test
    void futuresFairValueCostOfCarry() {
        // F = S e^{(r-q)T}
        assertEquals(100 * Math.exp(0.04 * 0.5), Forwards.fairValue(100, 0.04, 0, 0.5), 1e-9);
        assertEquals(100, Forwards.fairValue(100, 0.04, 0.04, 2), 1e-9);
        assertEquals(100, Forwards.fairValue(100, 0.04, 0, 0), 1e-9);
    }

    @Test
    void swapAtParHasZeroValue() {
        double pv = Swaps.pvPayFixed(1_000_000, 0.04, 0.04, 0.04, 12, 3);
        assertEquals(0, pv, 1e-9);
    }

    @Test
    void payerGainsWhenRatesRise() {
        double pv = Swaps.pvPayFixed(1_000_000, 0.04, 0.05, 0.04, 12, 3);
        assertTrue(pv > 0);
        // roughly 1% of notional times ~1y annuity
        assertEquals(0.01 * 1_000_000 * Swaps.annuity(0.04, 12, 3), pv, 1e-9);
    }

    @Test
    void fixingCashFlowIsPeriodAccrual() {
        // floating 5% vs fixed 4% on 1mm, quarterly: 1% * 0.25 * 1mm = 2500 to the payer
        assertEquals(2500, Swaps.fixingCashFlow(1_000_000, 0.04, 0.05, 3), 1e-9);
        assertEquals(-2500, Swaps.fixingCashFlow(1_000_000, 0.05, 0.04, 3), 1e-9);
    }
}
