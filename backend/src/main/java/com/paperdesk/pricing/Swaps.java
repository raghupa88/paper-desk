package com.paperdesk.pricing;

/**
 * Deliberately simplified plain-vanilla interest-rate swap math for teaching:
 * a flat discount curve and a single observable floating index. The par rate is
 * taken to be the current floating index, so a swap's PV is the classic
 * (par - fixed) * annuity * notional for the fixed-rate payer.
 */
public final class Swaps {
    private Swaps() {}

    /** Sum of discounted accrual periods over the remaining schedule. */
    public static double annuity(double discountRate, int remainingMonths, int payFreqMonths) {
        double tau = payFreqMonths / 12.0;
        double a = 0;
        for (int m = payFreqMonths; m <= remainingMonths; m += payFreqMonths) {
            double t = m / 12.0;
            a += tau * Math.exp(-discountRate * t);
        }
        return a;
    }

    /** PV for the payer of fixed / receiver of floating. Negate for the receiver. */
    public static double pvPayFixed(double notional, double fixedRate, double floatRate,
                                    double discountRate, int remainingMonths, int payFreqMonths) {
        return notional * (floatRate - fixedRate) * annuity(discountRate, remainingMonths, payFreqMonths);
    }

    /** Net cash exchanged at a fixing for the payer of fixed, for one accrual period. */
    public static double fixingCashFlow(double notional, double fixedRate, double floatRate, int payFreqMonths) {
        return notional * (floatRate - fixedRate) * (payFreqMonths / 12.0);
    }
}
