package com.paperdesk.pricing;

/** Cost-of-carry fair value shared by futures and forwards. */
public final class Forwards {
    private Forwards() {}

    /** F = S * e^((r - q) * T). q = dividend yield (equity) or foreign rate (FX). */
    public static double fairValue(double spot, double r, double q, double tYears) {
        if (tYears <= 0) return spot;
        return spot * Math.exp((r - q) * tYears);
    }

    /** Value today of a forward struck at K: (F - K) discounted. Per unit of underlying. */
    public static double markToMarket(double spot, double strike, double r, double q, double tYears) {
        double f = fairValue(spot, r, q, tYears);
        return (f - strike) * Math.exp(-r * Math.max(tYears, 0));
    }
}
