package com.paperdesk.web;

import com.paperdesk.domain.Settlement;
import com.paperdesk.repo.EquitySnapshotRepo;
import com.paperdesk.repo.SettlementRepo;
import com.paperdesk.sim.MarketDataService;
import com.paperdesk.trading.PortfolioService;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/portfolio")
public class PortfolioController {

    private final PortfolioService portfolio;
    private final SettlementRepo settlementRepo;
    private final EquitySnapshotRepo snapshotRepo;
    private final MarketDataService market;
    private final AccountGuard guard;

    public PortfolioController(PortfolioService portfolio, SettlementRepo settlementRepo,
                               EquitySnapshotRepo snapshotRepo, MarketDataService market, AccountGuard guard) {
        this.portfolio = portfolio;
        this.settlementRepo = settlementRepo;
        this.snapshotRepo = snapshotRepo;
        this.market = market;
        this.guard = guard;
    }

    @GetMapping("/{accountId}")
    public PortfolioService.PortfolioView portfolio(@PathVariable long accountId) {
        guard.owned(accountId);
        return portfolio.portfolio(accountId);
    }

    /** Settlement/lifecycle events: daily futures MTM, margin calls, expiries, fixings. */
    @GetMapping("/{accountId}/settlements")
    public List<Map<String, Object>> settlements(@PathVariable long accountId) {
        guard.owned(accountId);
        return settlementRepo.findByAccountIdOrderByIdDesc(accountId).stream().map(this::settlementJson).toList();
    }

    /** End-of-sim-day equity history (for the dashboard equity curve). */
    @GetMapping("/{accountId}/history")
    public List<Map<String, Object>> history(@PathVariable long accountId) {
        guard.owned(accountId);
        return snapshotRepo.findByAccountIdOrderBySimDate(accountId).stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("simDate", s.simDate.toString());
            m.put("equity", s.equity);
            return m;
        }).toList();
    }

    private Map<String, Object> settlementJson(Settlement s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.id);
        m.put("simDate", s.simDate.toString());
        m.put("kind", s.kind.name());
        m.put("cashFlow", s.cashFlow);
        m.put("detail", s.detail);
        m.put("symbol", s.instrumentId == null ? null : market.instrument(s.instrumentId).symbol);
        return m;
    }
}
