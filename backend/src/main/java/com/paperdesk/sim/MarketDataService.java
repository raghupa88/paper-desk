package com.paperdesk.sim;

import com.paperdesk.domain.Enums.OrderSide;
import com.paperdesk.domain.Instrument;
import com.paperdesk.pricing.BlackScholes;
import com.paperdesk.pricing.Forwards;
import com.paperdesk.pricing.Swaps;
import com.paperdesk.repo.InstrumentRepo;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prices any instrument off the session runtime's current simulated state.
 * Spot comes straight from the GBM path; derivatives are priced on demand
 * (Black-Scholes / Garman-Kohlhagen / cost-of-carry / simplified swap PV).
 */
@Service
public class MarketDataService {

    private static final double DAYS_PER_YEAR = 365.0;

    private final SimEngine engine;
    private final InstrumentRepo instrumentRepo;
    private final SimProps props;
    private final Map<Long, Instrument> instrumentCache = new ConcurrentHashMap<>();

    public MarketDataService(SimEngine engine, InstrumentRepo instrumentRepo, SimProps props) {
        this.engine = engine;
        this.instrumentRepo = instrumentRepo;
        this.props = props;
    }

    public record Quote(long instrumentId, String symbol, String type, double mid, double bid, double ask,
                        BlackScholes.Greeks greeks, Double yearsToExpiry) {}

    public Instrument instrument(long id) {
        return instrumentCache.computeIfAbsent(id, i -> instrumentRepo.findById(i).orElseThrow());
    }

    public void evictInstrument(long id) {
        instrumentCache.remove(id);
    }

    public Quote quote(Instrument instr) {
        SessionRuntime rt = engine.runtime(instr.sessionId);
        double mid;
        BlackScholes.Greeks greeks = null;
        Double tYears = null;
        switch (instr.instrumentType) {
            case EQUITY, FX_PAIR -> mid = rt.price(instr.symbol);
            case OPTION, FX_OPTION -> {
                greeks = greeks(instr, rt);
                mid = greeks.price();
                tYears = yearsToExpiry(rt, instr);
            }
            case FUTURE, FORWARD -> {
                Instrument under = instrument(instr.underlyingId);
                ScenarioParams.SymbolSpec spec = rt.params.spec(under.symbol);
                tYears = yearsToExpiry(rt, instr);
                mid = Forwards.fairValue(rt.price(under.symbol), carryRate(rt, spec), carryYield(spec), tYears);
            }
            case SWAP -> {
                tYears = yearsToExpiry(rt, instr);
                mid = Swaps.pvPayFixed(instr.notional, instr.fixedRate, rt.floatingRate,
                        rt.params.rate(), remainingMonths(rt, instr), instr.payFreqMonths);
            }
            default -> throw new IllegalArgumentException("Unpriceable type " + instr.instrumentType);
        }
        double halfSpread = instr.instrumentType == com.paperdesk.domain.Enums.InstrumentType.SWAP
                ? 0 : Math.abs(mid) * props.spreadBps() / 2 / 10000.0;
        return new Quote(instr.id, instr.symbol, instr.instrumentType.name(),
                mid, mid - halfSpread, mid + halfSpread, greeks, tYears);
    }

    public double mid(Instrument instr) {
        return quote(instr).mid();
    }

    /** The executable simulated price: buyers lift the offer, sellers hit the bid. */
    public double fillPrice(Instrument instr, OrderSide side) {
        Quote q = quote(instr);
        return side == OrderSide.BUY ? q.ask() : q.bid();
    }

    public BlackScholes.Greeks greeks(Instrument instr, SessionRuntime rt) {
        Instrument under = instrument(instr.underlyingId);
        ScenarioParams.SymbolSpec spec = rt.params.spec(under.symbol);
        double s = rt.price(under.symbol);
        double t = yearsToExpiry(rt, instr);
        double vol = spec.vol() * rt.volMult;
        double r = carryRate(rt, spec);
        double q = carryYield(spec);
        return BlackScholes.price(instr.callPut, s, instr.strike, t, vol, r, q);
    }

    /** Domestic/discount rate: scenario rate for equities, pair's domestic rate for FX. */
    private double carryRate(SessionRuntime rt, ScenarioParams.SymbolSpec spec) {
        return "FX_PAIR".equals(spec.type()) ? spec.domesticRate() : rt.params.rate();
    }

    /** Carry yield q: dividend yield for equities, foreign rate for FX (Garman-Kohlhagen). */
    private double carryYield(ScenarioParams.SymbolSpec spec) {
        return "FX_PAIR".equals(spec.type()) ? spec.foreignRate() : spec.divYield();
    }

    /** Expiry is treated as the END of the expiry date, so lifecycle settles at that day's close. */
    public double yearsToExpiry(SessionRuntime rt, Instrument instr) {
        if (instr.expiryDate == null) return 0;
        Instant expiry = instr.expiryDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        double days = Duration.between(rt.simTime, expiry).getSeconds() / 86400.0;
        return Math.max(0, days / DAYS_PER_YEAR);
    }

    public int remainingMonths(SessionRuntime rt, Instrument instr) {
        if (instr.expiryDate == null) return 0;
        long days = Duration.between(rt.simTime, instr.expiryDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant())
                .toDays();
        return (int) Math.max(0, Math.round(days / 30.0));
    }
}
