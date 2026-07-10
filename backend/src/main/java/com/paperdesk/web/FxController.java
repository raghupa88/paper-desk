package com.paperdesk.web;

import com.paperdesk.domain.Account;
import com.paperdesk.domain.Enums.CallPut;
import com.paperdesk.domain.Enums.InstrumentType;
import com.paperdesk.domain.Enums.OrderSide;
import com.paperdesk.domain.Enums.ViewContext;
import com.paperdesk.domain.Instrument;
import com.paperdesk.domain.Position;
import com.paperdesk.domain.TradeOrder;
import com.paperdesk.pricing.BlackScholes;
import com.paperdesk.repo.InstrumentRepo;
import com.paperdesk.repo.PositionRepo;
import com.paperdesk.sim.MarketDataService;
import com.paperdesk.sim.ScenarioParams;
import com.paperdesk.sim.SessionRuntime;
import com.paperdesk.sim.SimEngine;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

/**
 * FX derivatives desk endpoints.
 *
 * Sales view: RFQ-style quote for a custom-strike/expiry FX option — the
 * client-facing all-in premium is mid plus a sales margin; executing books
 * the deal at the all-in price with viewContext=SALES.
 *
 * Trader view: raw mid pricing and a spot risk ladder (delta/gamma/vega/P&L
 * across spot shifts) over the account's whole FX book.
 */
@RestController
@RequestMapping("/api/fx")
public class FxController {

    public record RfqRequest(long accountId, long underlyingId, double strike, String expiryDate,
                             CallPut callPut, double notional, OrderSide side, Double salesMarginBps) {}

    private static final double DEFAULT_SALES_MARGIN_BPS = 20;
    private static final double[] LADDER_SHIFTS = {-0.03, -0.02, -0.01, 0, 0.01, 0.02, 0.03};

    private final SimEngine engine;
    private final MarketDataService market;
    private final InstrumentRepo instrumentRepo;
    private final PositionRepo positionRepo;
    private final com.paperdesk.trading.OrderService orders;
    private final AccountGuard guard;

    public FxController(SimEngine engine, MarketDataService market, InstrumentRepo instrumentRepo,
                        PositionRepo positionRepo, com.paperdesk.trading.OrderService orders, AccountGuard guard) {
        this.engine = engine;
        this.market = market;
        this.instrumentRepo = instrumentRepo;
        this.positionRepo = positionRepo;
        this.orders = orders;
        this.guard = guard;
    }

    @PostMapping("/rfq")
    public Map<String, Object> rfq(@RequestBody RfqRequest req) {
        guard.owned(req.accountId());
        return quoteRfq(req);
    }

    @PostMapping("/rfq/execute")
    public Map<String, Object> executeRfq(@RequestBody RfqRequest req) {
        Account account = guard.owned(req.accountId());
        Map<String, Object> quote = quoteRfq(req); // re-quoted live at execution
        double allIn = (Double) quote.get("allInPrice");
        Instrument under = market.instrument(req.underlyingId());

        Instrument opt = new Instrument();
        opt.sessionId = account.sessionId;
        opt.symbol = under.symbol + " " + req.expiryDate() + " " + req.strike()
                + (req.callPut() == CallPut.CALL ? "C" : "P") + " RFQ#" + Long.toHexString(System.nanoTime());
        opt.name = under.name + " " + req.callPut().name().toLowerCase() + " (sales RFQ)";
        opt.instrumentType = InstrumentType.FX_OPTION;
        opt.underlyingId = under.id;
        opt.strike = req.strike();
        opt.expiryDate = LocalDate.parse(req.expiryDate());
        opt.callPut = req.callPut();
        opt.contractSize = 1; // qty carries the notional in base units
        instrumentRepo.save(opt);

        TradeOrder order = orders.placeAtPrice(account.id, opt.id, req.side(), req.notional(), allIn, ViewContext.SALES);
        quote.put("orderId", order.id);
        quote.put("orderStatus", order.status.name());
        quote.put("instrumentId", opt.id);
        return quote;
    }

    /** Spot risk ladder for the account's FX book, per currency pair. */
    @GetMapping("/ladder")
    public List<Map<String, Object>> ladder(@RequestParam long accountId) {
        Account account = guard.owned(accountId);
        SessionRuntime rt = engine.runtime(account.sessionId);

        // group FX positions by underlying pair
        Map<String, List<Position>> byPair = new LinkedHashMap<>();
        Map<Long, Instrument> instruments = new HashMap<>();
        for (Position pos : positionRepo.findByAccountIdAndQtyNot(accountId, 0)) {
            Instrument instr = market.instrument(pos.instrumentId);
            String pair = switch (instr.instrumentType) {
                case FX_PAIR -> instr.symbol;
                case FX_OPTION -> market.instrument(instr.underlyingId).symbol;
                default -> null;
            };
            if (pair == null) continue;
            instruments.put(instr.id, instr);
            byPair.computeIfAbsent(pair, p -> new ArrayList<>()).add(pos);
        }

        List<Map<String, Object>> out = new ArrayList<>();
        for (var entry : byPair.entrySet()) {
            String pair = entry.getKey();
            double spot = rt.price(pair);
            ScenarioParams.SymbolSpec spec = rt.params.spec(pair);
            double baseValue = bookValue(entry.getValue(), instruments, rt, spec, spot);

            List<Map<String, Object>> rows = new ArrayList<>();
            for (double shift : LADDER_SHIFTS) {
                double s = spot * (1 + shift);
                double delta = 0, gamma = 0, vega = 0;
                for (Position pos : entry.getValue()) {
                    Instrument instr = instruments.get(pos.instrumentId);
                    if (instr.instrumentType == InstrumentType.FX_PAIR) {
                        delta += pos.qty * instr.contractSize;
                    } else {
                        BlackScholes.Greeks g = optionGreeks(instr, rt, spec, s);
                        double mult = pos.qty * instr.contractSize;
                        delta += g.delta() * mult;
                        gamma += g.gamma() * mult;
                        vega += g.vega() * mult;
                    }
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("shiftPct", shift * 100);
                row.put("spot", s);
                row.put("pnl", bookValue(entry.getValue(), instruments, rt, spec, s) - baseValue);
                row.put("delta", delta);
                row.put("gamma", gamma);
                row.put("vega", vega);
                rows.add(row);
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("pair", pair);
            m.put("spot", spot);
            m.put("rows", rows);
            out.add(m);
        }
        return out;
    }

    private Map<String, Object> quoteRfq(RfqRequest req) {
        if (req.notional() <= 0) throw new IllegalArgumentException("Notional must be positive");
        Account account = guard.owned(req.accountId());
        Instrument under = market.instrument(req.underlyingId());
        if (under.instrumentType != InstrumentType.FX_PAIR)
            throw new IllegalArgumentException("RFQ underlying must be an FX pair");
        SessionRuntime rt = engine.runtime(account.sessionId);
        LocalDate expiry = LocalDate.parse(req.expiryDate());
        if (!expiry.isAfter(rt.simDate())) throw new IllegalArgumentException("Expiry must be after the current sim date");

        ScenarioParams.SymbolSpec spec = rt.params.spec(under.symbol);
        double spot = rt.price(under.symbol);
        double t = Duration.between(rt.simTime, expiry.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant())
                .getSeconds() / 86400.0 / 365.0;
        BlackScholes.Greeks g = BlackScholes.price(req.callPut(), spot, req.strike(), t,
                spec.vol() * rt.volMult, spec.domesticRate(), spec.foreignRate());

        double marginBps = req.salesMarginBps() == null ? DEFAULT_SALES_MARGIN_BPS : req.salesMarginBps();
        double salesMargin = spot * marginBps / 10000.0; // per unit of notional, in quote ccy
        double allIn = req.side() == OrderSide.BUY ? g.price() + salesMargin
                                                   : Math.max(0, g.price() - salesMargin);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("pair", under.symbol);
        m.put("spot", spot);
        m.put("strike", req.strike());
        m.put("expiryDate", expiry.toString());
        m.put("callPut", req.callPut().name());
        m.put("side", req.side().name());
        m.put("notional", req.notional());
        m.put("midPrice", g.price());
        m.put("salesMarginBps", marginBps);
        m.put("salesMarginPerUnit", salesMargin);
        m.put("allInPrice", allIn);
        m.put("premiumTotal", allIn * req.notional());
        m.put("greeks", Map.of("delta", g.delta(), "gamma", g.gamma(), "theta", g.theta(), "vega", g.vega()));
        return m;
    }

    private double bookValue(List<Position> positions, Map<Long, Instrument> instruments,
                             SessionRuntime rt, ScenarioParams.SymbolSpec spec, double spot) {
        double value = 0;
        for (Position pos : positions) {
            Instrument instr = instruments.get(pos.instrumentId);
            if (instr.instrumentType == InstrumentType.FX_PAIR) {
                value += spot * pos.qty * instr.contractSize;
            } else {
                value += optionGreeks(instr, rt, spec, spot).price() * pos.qty * instr.contractSize;
            }
        }
        return value;
    }

    private BlackScholes.Greeks optionGreeks(Instrument opt, SessionRuntime rt,
                                             ScenarioParams.SymbolSpec spec, double spot) {
        double t = Duration.between(rt.simTime,
                opt.expiryDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()).getSeconds() / 86400.0 / 365.0;
        return BlackScholes.price(opt.callPut, spot, opt.strike, Math.max(t, 0),
                spec.vol() * rt.volMult, spec.domesticRate(), spec.foreignRate());
    }
}
