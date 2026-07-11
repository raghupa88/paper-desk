package com.paperdesk.sim;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;

/**
 * In-memory state of one running scenario session. Fully reconstructable from
 * (seed, params, sim time) — the RNG streams are per-symbol and consumed in a
 * fixed order, so replaying the same number of grid steps reproduces the
 * identical market.
 */
public class SessionRuntime {

    public static class SymbolState {
        public final ScenarioParams.SymbolSpec spec;
        public double price;
        public final RandomGenerator rng;

        SymbolState(ScenarioParams.SymbolSpec spec, long seed) {
            this.spec = spec;
            this.price = spec.s0();
            this.rng = new SplittableRandom(seed);
        }
    }

    public static class Bar {
        public double open, high, low, close;
        Bar(double px) { open = high = low = close = px; }
        void update(double px) {
            high = Math.max(high, px);
            low = Math.min(low, px);
            close = px;
        }
    }

    public final long sessionId;
    public final long seed;
    public final ScenarioParams params;
    public final Instant simStart;

    public volatile double acceleration;   // sim seconds per real second
    public volatile boolean paused;
    public volatile Instant simTime;
    public long gridStep;                  // grid steps applied since simStart
    public double volMult = 1.0;           // scaled up by a crash
    public double floatingRate;
    public LocalDate currentDay;
    public final RandomGenerator ratesRng;
    public final Map<String, SymbolState> symbols = new LinkedHashMap<>();
    public final Map<String, Bar> dayBars = new LinkedHashMap<>();

    public SessionRuntime(long sessionId, long seed, ScenarioParams params, double acceleration, Instant simStart) {
        this.sessionId = sessionId;
        this.seed = seed;
        this.params = params;
        this.acceleration = acceleration;
        this.simStart = simStart;
        this.simTime = simStart;
        this.currentDay = LocalDate.ofInstant(simStart, ZoneOffset.UTC);
        this.floatingRate = params.floating() != null ? params.floating().r0() : params.rate();
        this.ratesRng = new SplittableRandom(seed ^ 0x9E3779B97F4A7C15L);
        for (ScenarioParams.SymbolSpec spec : params.symbols()) {
            symbols.put(spec.symbol(), new SymbolState(spec, seed * 31 + spec.symbol().hashCode()));
        }
    }

    public double price(String symbol) {
        SymbolState s = symbols.get(symbol);
        if (s == null) throw new IllegalArgumentException("Unknown symbol " + symbol);
        return s.price;
    }

    public Map<String, Double> currentPrices() {
        Map<String, Double> out = new LinkedHashMap<>();
        symbols.forEach((sym, st) -> out.put(sym, st.price));
        return out;
    }

    public LocalDate simDate() {
        return LocalDate.ofInstant(simTime, ZoneOffset.UTC);
    }
}
