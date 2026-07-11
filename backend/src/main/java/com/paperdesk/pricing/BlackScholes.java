package com.paperdesk.pricing;

import com.paperdesk.domain.Enums.CallPut;

/**
 * Black-Scholes-Merton pricing with a continuous carry yield q.
 * For equity options q = dividend yield; for FX options (Garman-Kohlhagen)
 * q = foreign risk-free rate and r = domestic risk-free rate.
 *
 * Conventions: rates/vol are annualized decimals, T in years.
 * theta is reported PER DAY, vega and rho PER 1% move — learner-friendly units.
 */
public final class BlackScholes {
    private BlackScholes() {}

    public record Greeks(double price, double delta, double gamma, double theta, double vega, double rho) {}

    public static Greeks price(CallPut cp, double s, double k, double t, double vol, double r, double q) {
        boolean call = cp == CallPut.CALL;
        if (t <= 0 || vol <= 0) {
            double intrinsic = call ? Math.max(s - k, 0) : Math.max(k - s, 0);
            double delta = intrinsic == 0 ? 0 : (call ? 1 : -1);
            return new Greeks(intrinsic, delta, 0, 0, 0, 0);
        }
        double sqrtT = Math.sqrt(t);
        double d1 = (Math.log(s / k) + (r - q + vol * vol / 2) * t) / (vol * sqrtT);
        double d2 = d1 - vol * sqrtT;
        double dfR = Math.exp(-r * t);
        double dfQ = Math.exp(-q * t);
        double nd1 = cdf(d1), nd2 = cdf(d2);
        double pdfD1 = pdf(d1);

        double price, delta, theta, rho;
        if (call) {
            price = s * dfQ * nd1 - k * dfR * nd2;
            delta = dfQ * nd1;
            theta = -s * dfQ * pdfD1 * vol / (2 * sqrtT) - r * k * dfR * nd2 + q * s * dfQ * nd1;
            rho = k * t * dfR * nd2;
        } else {
            price = k * dfR * cdf(-d2) - s * dfQ * cdf(-d1);
            delta = dfQ * (nd1 - 1);
            theta = -s * dfQ * pdfD1 * vol / (2 * sqrtT) + r * k * dfR * cdf(-d2) - q * s * dfQ * cdf(-d1);
            rho = -k * t * dfR * cdf(-d2);
        }
        double gamma = dfQ * pdfD1 / (s * vol * sqrtT);
        double vega = s * dfQ * pdfD1 * sqrtT;
        return new Greeks(price, delta, gamma, theta / 365.0, vega / 100.0, rho / 100.0);
    }

    static double pdf(double x) {
        return Math.exp(-x * x / 2) / Math.sqrt(2 * Math.PI);
    }

    /** Standard normal CDF via Abramowitz & Stegun 7.1.26 erf approximation (|err| < 1.5e-7). */
    static double cdf(double x) {
        double t = 1 / (1 + 0.2316419 * Math.abs(x));
        double poly = t * (0.319381530 + t * (-0.356563782 + t * (1.781477937 + t * (-1.821255978 + t * 1.330274429))));
        double approx = 1 - pdf(Math.abs(x)) * poly;
        return x >= 0 ? approx : 1 - approx;
    }
}
