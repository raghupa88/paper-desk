package com.paperdesk.web;

import com.paperdesk.coach.TradingCoachService;
import com.paperdesk.domain.Account;
import com.paperdesk.domain.Fill;
import com.paperdesk.domain.Instrument;
import com.paperdesk.domain.TradeOrder;
import com.paperdesk.repo.AccountRepo;
import com.paperdesk.repo.FillRepo;
import com.paperdesk.repo.OrderRepo;
import com.paperdesk.sim.MarketDataService;
import com.paperdesk.trading.PortfolioService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** AI trading coach: a plain-language explanation of a single trade, grounded in the student's own
 *  order/fill/Greeks data -- never used to authorize or place trades, only to read and explain. */
@RestController
@RequestMapping("/api/orders")
public class CoachController {

    private final TradingCoachService coach;
    private final OrderRepo orderRepo;
    private final FillRepo fillRepo;
    private final AccountRepo accountRepo;
    private final MarketDataService market;
    private final PortfolioService portfolio;
    private final AccountGuard guard;

    public CoachController(TradingCoachService coach, OrderRepo orderRepo, FillRepo fillRepo,
                           AccountRepo accountRepo, MarketDataService market, PortfolioService portfolio,
                           AccountGuard guard) {
        this.coach = coach;
        this.orderRepo = orderRepo;
        this.fillRepo = fillRepo;
        this.accountRepo = accountRepo;
        this.market = market;
        this.portfolio = portfolio;
        this.guard = guard;
    }

    @PostMapping("/{orderId}/explain")
    public Map<String, Object> explain(@PathVariable long orderId) {
        TradeOrder order = orderRepo.findById(orderId).orElseThrow();
        guard.owned(order.accountId);

        List<Fill> fills = fillRepo.findByOrderIdIn(List.of(orderId));
        Instrument instrument = market.instrument(order.instrumentId);
        Account account = accountRepo.findById(order.accountId).orElseThrow();
        PortfolioService.PositionView position = portfolio.portfolio(order.accountId).positions().stream()
                .filter(p -> p.instrumentId() == order.instrumentId)
                .findFirst().orElse(null);

        TradingCoachService.CoachExplanation result = coach.explain(order, fills, instrument, position, account);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("configured", result.configured());
        m.put("explanation", result.explanation());
        m.put("model", result.model());
        m.put("error", result.error());
        return m;
    }
}
