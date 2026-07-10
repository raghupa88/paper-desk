package com.paperdesk.seed;

import com.paperdesk.domain.Scenario;
import com.paperdesk.repo.ScenarioRepo;
import com.paperdesk.sim.ScenarioParams;
import com.paperdesk.sim.ScenarioParams.Crash;
import com.paperdesk.sim.ScenarioParams.Floating;
import com.paperdesk.sim.ScenarioParams.SymbolSpec;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Seeds the four named teaching scenarios on first boot. */
@Component
@Order(1)
public class DataSeeder implements CommandLineRunner {

    private final ScenarioRepo scenarioRepo;

    public DataSeeder(ScenarioRepo scenarioRepo) {
        this.scenarioRepo = scenarioRepo;
    }

    @Override
    public void run(String... args) {
        if (scenarioRepo.count() > 0) return;
        save("Calm market", "Low volatility, gentle drift — a friendly market to learn the mechanics in.",
                1001, scenario(0.5, 0.04, null));
        save("Bull run", "Strong upward drift with slightly damped volatility.",
                1002, scenario(0.8, 0.18, null));
        save("High volatility", "Twice the volatility, weak drift — options premiums are rich, spot is wild.",
                1003, scenario(2.0, 0.02, null));
        save("Crash", "Normal market until sim day 10, then a -25% jump and elevated volatility after.",
                1004, scenario(1.0, 0.05, new Crash(10, -0.25, 2.2)));
    }

    private void save(String name, String description, long seed, ScenarioParams params) {
        Scenario s = new Scenario();
        s.name = name;
        s.description = description;
        s.seed = seed;
        s.acceleration = 300; // 1 real second = 5 sim minutes
        s.paramsJson = ScenarioParams.toJson(params);
        scenarioRepo.save(s);
    }

    private ScenarioParams scenario(double volMult, double equityDrift, Crash crash) {
        List<SymbolSpec> symbols = new ArrayList<>();
        equity(symbols, "ACME", "Acme Industrial", 100, equityDrift, 0.20 * volMult, 0.015);
        equity(symbols, "GLOBO", "Globo Media", 55, equityDrift * 0.9, 0.28 * volMult, 0.0);
        equity(symbols, "NIMBUS", "Nimbus Cloud", 210, equityDrift * 1.2, 0.35 * volMult, 0.0);
        equity(symbols, "ORION", "Orion Mining", 32, equityDrift * 0.7, 0.32 * volMult, 0.03);
        equity(symbols, "PULSE", "Pulse Health", 78, equityDrift, 0.24 * volMult, 0.01);
        equity(symbols, "QUANTA", "Quanta Semiconductors", 145, equityDrift * 1.4, 0.40 * volMult, 0.0);
        equity(symbols, "RIVET", "Rivet Steel", 18, equityDrift * 0.5, 0.26 * volMult, 0.045);
        equity(symbols, "SOLAR", "Solar Grid", 64, equityDrift * 1.1, 0.30 * volMult, 0.0);
        fx(symbols, "EURUSD", "Euro / US Dollar", 1.10, 0.08 * volMult, 0.045, 0.025);
        fx(symbols, "USDJPY", "US Dollar / Japanese Yen", 148, 0.09 * volMult, 0.001, 0.045);
        fx(symbols, "GBPUSD", "British Pound / US Dollar", 1.27, 0.085 * volMult, 0.045, 0.0475);
        return new ScenarioParams(0.04, symbols, crash, new Floating(0.04, 0.04, 0.05, 0.012));
    }

    private void equity(List<SymbolSpec> out, String sym, String name, double s0,
                        double drift, double vol, double div) {
        out.add(new SymbolSpec(sym, name, "EQUITY", s0, drift, vol, div, 0, 0));
    }

    /** For FX the quote convention is domestic = quote ccy; drift 0 means covered interest parity. */
    private void fx(List<SymbolSpec> out, String sym, String name, double s0,
                    double vol, double domesticRate, double foreignRate) {
        out.add(new SymbolSpec(sym, name, "FX_PAIR", s0, 0, vol, 0, domesticRate, foreignRate));
    }
}
