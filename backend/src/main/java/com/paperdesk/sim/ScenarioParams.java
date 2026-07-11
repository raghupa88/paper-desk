package com.paperdesk.sim;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;

/**
 * Parsed form of scenarios.params_json. Everything the simulator needs to
 * reproduce a market: per-symbol GBM parameters, the risk-free rate, an
 * optional scheduled crash, and the floating-rate index process for swaps.
 */
public record ScenarioParams(double rate, List<SymbolSpec> symbols, Crash crash, Floating floating) {

    /** type is EQUITY or FX_PAIR. drift==0 for FX means "use covered interest parity". */
    public record SymbolSpec(String symbol, String name, String type, double s0, double drift,
                             double vol, double divYield, double domesticRate, double foreignRate) {}

    /** A jump of `jump` (e.g. -0.22) applied at the open of sim day `day`, with vol scaled by volMult after. */
    public record Crash(int day, double jump, double volMult) {}

    /** Mean-reverting floating rate index: r += meanRev*(mean-r) + vol*sqrt(1/252)*Z per sim day. */
    public record Floating(double r0, double mean, double meanRev, double vol) {}

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public static ScenarioParams parse(String json) {
        try {
            return MAPPER.readValue(json, ScenarioParams.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Bad scenario params json", e);
        }
    }

    public static String toJson(ScenarioParams p) {
        try {
            return MAPPER.writeValueAsString(p);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    public SymbolSpec spec(String symbol) {
        return symbols.stream().filter(s -> s.symbol().equals(symbol)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown symbol " + symbol));
    }
}
