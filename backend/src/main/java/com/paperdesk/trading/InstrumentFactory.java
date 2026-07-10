package com.paperdesk.trading;

import com.paperdesk.domain.Enums.CallPut;
import com.paperdesk.domain.Enums.InstrumentType;
import com.paperdesk.domain.Instrument;
import com.paperdesk.repo.InstrumentRepo;
import com.paperdesk.sim.ScenarioParams;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates the tradeable universe for a session: underlyings, option chains
 * (strikes x expiries), futures, forwards and teaching swaps. Also rolls new
 * option expiries in as old ones expire so the chain never runs dry.
 */
@Service
public class InstrumentFactory {

    private static final int[] OPTION_EXPIRY_DAYS = {7, 14, 30, 60};
    private static final int[] FUTURE_EXPIRY_DAYS = {30, 90};
    private static final double[] STRIKE_MONEYNESS = {0.80, 0.85, 0.90, 0.95, 1.00, 1.05, 1.10, 1.15, 1.20};
    private static final double[] FX_STRIKE_MONEYNESS = {0.95, 0.9625, 0.975, 0.9875, 1.00, 1.0125, 1.025, 1.0375, 1.05};

    private final InstrumentRepo repo;

    public InstrumentFactory(InstrumentRepo repo) {
        this.repo = repo;
    }

    public void createForSession(long sessionId, ScenarioParams params, LocalDate simStartDate) {
        for (ScenarioParams.SymbolSpec spec : params.symbols()) {
            boolean fx = "FX_PAIR".equals(spec.type());
            Instrument under = base(sessionId, spec.symbol(), spec.name(),
                    fx ? InstrumentType.FX_PAIR : InstrumentType.EQUITY, 1);
            repo.save(under);

            for (int days : OPTION_EXPIRY_DAYS) {
                addOptionChain(under, spec, simStartDate.plusDays(days), spec.s0());
            }
            if (!fx) {
                for (int days : FUTURE_EXPIRY_DAYS) {
                    LocalDate expiry = simStartDate.plusDays(days);
                    Instrument fut = base(sessionId, spec.symbol() + "-FUT-" + expiry,
                            spec.name() + " future " + expiry, InstrumentType.FUTURE, 10);
                    fut.underlyingId = under.id;
                    fut.expiryDate = expiry;
                    fut.initialMargin = round2(0.15 * spec.s0() * fut.contractSize);
                    fut.maintenanceMargin = round2(0.10 * spec.s0() * fut.contractSize);
                    repo.save(fut);
                }
            }
            LocalDate fwdExpiry = simStartDate.plusDays(30);
            Instrument fwd = base(sessionId, spec.symbol() + "-FWD-" + fwdExpiry,
                    spec.name() + " forward " + fwdExpiry, InstrumentType.FORWARD, fx ? 10000 : 10);
            fwd.underlyingId = under.id;
            fwd.expiryDate = fwdExpiry;
            repo.save(fwd);
        }

        double parRate = params.floating() != null ? params.floating().r0() : params.rate();
        addSwap(sessionId, "IRS-6M", 6, 1, 1_000_000, parRate, simStartDate);
        addSwap(sessionId, "IRS-1Y", 12, 3, 1_000_000, parRate, simStartDate);
    }

    /** Keeps at least 3 live option expiries per underlying; strikes are re-centred on the current spot. */
    public void ensureOptionChains(long sessionId, ScenarioParams params, LocalDate today,
                                   java.util.Map<String, Double> spots) {
        List<Instrument> underlyings = new ArrayList<>();
        underlyings.addAll(repo.findBySessionIdAndInstrumentTypeAndActiveTrue(sessionId, InstrumentType.EQUITY));
        underlyings.addAll(repo.findBySessionIdAndInstrumentTypeAndActiveTrue(sessionId, InstrumentType.FX_PAIR));
        for (Instrument under : underlyings) {
            InstrumentType optType = under.instrumentType == InstrumentType.FX_PAIR
                    ? InstrumentType.FX_OPTION : InstrumentType.OPTION;
            List<LocalDate> expiries = repo
                    .findBySessionIdAndUnderlyingIdAndInstrumentTypeAndActiveTrue(sessionId, under.id, optType)
                    .stream().map(i -> i.expiryDate).distinct().sorted().toList();
            LocalDate latest = expiries.isEmpty() ? today : expiries.get(expiries.size() - 1);
            ScenarioParams.SymbolSpec spec = params.spec(under.symbol);
            double spot = spots.getOrDefault(under.symbol, spec.s0());
            int live = expiries.size();
            while (live < 3) {
                latest = latest.plusDays(30);
                addOptionChain(under, spec, latest, spot);
                live++;
            }
        }
    }

    private void addOptionChain(Instrument under, ScenarioParams.SymbolSpec spec, LocalDate expiry, double centre) {
        boolean fx = "FX_PAIR".equals(spec.type());
        double[] moneyness = fx ? FX_STRIKE_MONEYNESS : STRIKE_MONEYNESS;
        for (double m : moneyness) {
            double strike = fx ? round4(centre * m) : round2(centre * m);
            for (CallPut cp : CallPut.values()) {
                Instrument opt = base(under.sessionId,
                        under.symbol + " " + expiry + " " + trimNum(strike) + (cp == CallPut.CALL ? "C" : "P"),
                        under.name + " " + expiry + " " + trimNum(strike) + " " + cp.name().toLowerCase(),
                        fx ? InstrumentType.FX_OPTION : InstrumentType.OPTION,
                        fx ? 10000 : 100);
                opt.underlyingId = under.id;
                opt.strike = strike;
                opt.expiryDate = expiry;
                opt.callPut = cp;
                repo.save(opt);
            }
        }
    }

    private void addSwap(long sessionId, String name, int termMonths, int freqMonths,
                         double notional, double fixedRate, LocalDate start) {
        Instrument swap = base(sessionId, name + "-" + pct(fixedRate),
                "Interest rate swap " + termMonths + "m, fixed " + pct(fixedRate)
                        + " vs floating index (buy = pay fixed)", InstrumentType.SWAP, 1);
        swap.notional = notional;
        swap.fixedRate = fixedRate;
        swap.payFreqMonths = freqMonths;
        swap.termMonths = termMonths;
        swap.expiryDate = start.plusDays(30L * termMonths);
        repo.save(swap);
    }

    private Instrument base(long sessionId, String symbol, String name, InstrumentType type, double size) {
        Instrument i = new Instrument();
        i.sessionId = sessionId;
        i.symbol = symbol;
        i.name = name;
        i.instrumentType = type;
        i.contractSize = size;
        return i;
    }

    private static double round2(double x) { return Math.round(x * 100) / 100.0; }
    private static double round4(double x) { return Math.round(x * 10000) / 10000.0; }
    private static String trimNum(double x) {
        String s = x == Math.floor(x) ? String.valueOf((long) x) : String.valueOf(x);
        return s;
    }
    private static String pct(double r) { return trimNum(Math.round(r * 10000) / 100.0) + "%"; }
}
