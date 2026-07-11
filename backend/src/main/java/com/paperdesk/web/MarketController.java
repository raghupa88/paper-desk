package com.paperdesk.web;

import com.paperdesk.domain.DailyBar;
import com.paperdesk.domain.Enums.CallPut;
import com.paperdesk.domain.Enums.InstrumentType;
import com.paperdesk.domain.Instrument;
import com.paperdesk.repo.DailyBarRepo;
import com.paperdesk.repo.InstrumentRepo;
import com.paperdesk.sim.MarketDataService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/market")
public class MarketController {

    private final InstrumentRepo instrumentRepo;
    private final DailyBarRepo barRepo;
    private final MarketDataService market;

    public MarketController(InstrumentRepo instrumentRepo, DailyBarRepo barRepo, MarketDataService market) {
        this.instrumentRepo = instrumentRepo;
        this.barRepo = barRepo;
        this.market = market;
    }

    @GetMapping("/{sessionId}/instruments")
    public List<Instrument> instruments(@PathVariable long sessionId, @RequestParam(required = false) String type) {
        if (type == null) return instrumentRepo.findBySessionIdAndActiveTrue(sessionId);
        return instrumentRepo.findBySessionIdAndInstrumentTypeAndActiveTrue(sessionId, InstrumentType.valueOf(type));
    }

    /** Live quotes for the non-option universe (watchlist / market view). */
    @GetMapping("/{sessionId}/quotes")
    public List<Map<String, Object>> quotes(@PathVariable long sessionId, @RequestParam(required = false) String types) {
        Set<InstrumentType> wanted = types == null
                ? EnumSet.of(InstrumentType.EQUITY, InstrumentType.FX_PAIR, InstrumentType.FUTURE,
                             InstrumentType.FORWARD, InstrumentType.SWAP)
                : Arrays.stream(types.split(",")).map(InstrumentType::valueOf)
                        .collect(Collectors.toCollection(() -> EnumSet.noneOf(InstrumentType.class)));
        List<Map<String, Object>> out = new ArrayList<>();
        for (Instrument i : instrumentRepo.findBySessionIdAndActiveTrue(sessionId)) {
            if (!wanted.contains(i.instrumentType)) continue;
            out.add(quoteJson(i));
        }
        return out;
    }

    @GetMapping("/{sessionId}/quote/{instrumentId}")
    public Map<String, Object> quote(@PathVariable long sessionId, @PathVariable long instrumentId) {
        Instrument i = market.instrument(instrumentId);
        if (!i.sessionId.equals(sessionId)) throw new NoSuchElementException();
        return quoteJson(i);
    }

    /** Options chain: strikes x expiries with live premiums and Greeks. */
    @GetMapping("/{sessionId}/chain/{underlyingId}")
    public Map<String, Object> chain(@PathVariable long sessionId, @PathVariable long underlyingId) {
        Instrument under = market.instrument(underlyingId);
        InstrumentType optType = under.instrumentType == InstrumentType.FX_PAIR
                ? InstrumentType.FX_OPTION : InstrumentType.OPTION;
        List<Instrument> options = instrumentRepo
                .findBySessionIdAndUnderlyingIdAndInstrumentTypeAndActiveTrue(sessionId, underlyingId, optType);

        Map<LocalDate, Map<Double, Map<String, Object>>> byExpiry = new TreeMap<>();
        for (Instrument opt : options) {
            Map<String, Object> row = byExpiry
                    .computeIfAbsent(opt.expiryDate, d -> new TreeMap<>())
                    .computeIfAbsent(opt.strike, s -> {
                        Map<String, Object> r = new LinkedHashMap<>();
                        r.put("strike", s);
                        return r;
                    });
            row.put(opt.callPut == CallPut.CALL ? "call" : "put", quoteJson(opt));
        }

        List<Map<String, Object>> expiries = byExpiry.entrySet().stream().map(e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("expiry", e.getKey().toString());
            m.put("rows", new ArrayList<>(e.getValue().values()));
            return m;
        }).toList();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("underlying", quoteJson(under));
        out.put("expiries", expiries);
        return out;
    }

    @GetMapping("/{sessionId}/bars/{symbol}")
    public List<Map<String, Object>> bars(@PathVariable long sessionId, @PathVariable String symbol) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (DailyBar b : barRepo.findBySessionIdAndSymbolOrderBySimDate(sessionId, symbol)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("time", b.simDate.toString());
            m.put("open", b.open);
            m.put("high", b.high);
            m.put("low", b.low);
            m.put("close", b.close);
            out.add(m);
        }
        return out;
    }

    private Map<String, Object> quoteJson(Instrument i) {
        MarketDataService.Quote q = market.quote(i);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("instrumentId", i.id);
        m.put("symbol", i.symbol);
        m.put("name", i.name);
        m.put("type", i.instrumentType.name());
        m.put("underlyingId", i.underlyingId);
        m.put("strike", i.strike);
        m.put("expiryDate", i.expiryDate == null ? null : i.expiryDate.toString());
        m.put("callPut", i.callPut == null ? null : i.callPut.name());
        m.put("contractSize", i.contractSize);
        m.put("initialMargin", i.initialMargin);
        m.put("maintenanceMargin", i.maintenanceMargin);
        m.put("notional", i.notional);
        m.put("fixedRate", i.fixedRate);
        m.put("payFreqMonths", i.payFreqMonths);
        m.put("mid", q.mid());
        m.put("bid", q.bid());
        m.put("ask", q.ask());
        m.put("yearsToExpiry", q.yearsToExpiry());
        if (q.greeks() != null) {
            Map<String, Object> g = new LinkedHashMap<>();
            g.put("delta", q.greeks().delta());
            g.put("gamma", q.greeks().gamma());
            g.put("theta", q.greeks().theta());
            g.put("vega", q.greeks().vega());
            g.put("rho", q.greeks().rho());
            m.put("greeks", g);
        }
        return m;
    }
}
