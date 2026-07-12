package com.paperdesk.trading;

import com.paperdesk.domain.Account;
import com.paperdesk.domain.ClosedTrade;
import com.paperdesk.domain.Enums.InstrumentType;
import com.paperdesk.domain.EquitySnapshot;
import com.paperdesk.domain.Instrument;
import com.paperdesk.domain.Position;
import com.paperdesk.pricing.BlackScholes;
import com.paperdesk.repo.AccountRepo;
import com.paperdesk.repo.ClosedTradeRepo;
import com.paperdesk.repo.EquitySnapshotRepo;
import com.paperdesk.repo.PositionRepo;
import com.paperdesk.sim.MarketDataService;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Live valuation of an account: per-position marks, unrealized P&L, Greeks
 * (options) and margin usage (futures), plus the account-level equity used by
 * the dashboard, snapshots and leaderboards.
 */
@Service
public class PortfolioService {

    public record PositionView(long positionId, long instrumentId, String symbol, String name, String type,
                               double qty, double avgPrice, double mark, double marketValue, double unrealizedPnl,
                               double realizedPnl, Double delta, Double gamma, Double theta, Double vega,
                               Double marginUsed, Double lastSettlePrice, String expiryDate) {}

    public record PortfolioView(long accountId, double cash, double marginHeld, double positionsValue,
                                double equity, double startingBalance, double totalReturnPct, double dayPnl,
                                List<PositionView> positions) {}

    /** Personal performance analytics computed from closed trades and the equity curve. */
    public record ScorecardView(int totalTrades, int wins, int losses, double winRatePct,
                                double avgWin, double avgLoss, double avgHoldingPeriodHours,
                                double maxDrawdown, double maxDrawdownPct) {}

    private final PositionRepo positionRepo;
    private final AccountRepo accountRepo;
    private final EquitySnapshotRepo snapshotRepo;
    private final ClosedTradeRepo closedTradeRepo;
    private final MarketDataService market;

    public PortfolioService(PositionRepo positionRepo, AccountRepo accountRepo,
                            EquitySnapshotRepo snapshotRepo, ClosedTradeRepo closedTradeRepo,
                            MarketDataService market) {
        this.positionRepo = positionRepo;
        this.accountRepo = accountRepo;
        this.snapshotRepo = snapshotRepo;
        this.closedTradeRepo = closedTradeRepo;
        this.market = market;
    }

    public PortfolioView portfolio(long accountId) {
        Account account = accountRepo.findById(accountId).orElseThrow();
        List<PositionView> views = new ArrayList<>();
        double positionsValue = 0;
        for (Position pos : positionRepo.findByAccountIdAndQtyNot(accountId, 0)) {
            PositionView v = view(pos);
            positionsValue += v.marketValue();
            views.add(v);
        }
        double equity = account.cashBalance + account.marginHeld + positionsValue;
        double dayPnl = equity - snapshotRepo.findFirstByAccountIdOrderBySimDateDesc(accountId)
                .map(s -> s.equity).orElse(account.startingBalance);
        double ret = account.startingBalance == 0 ? 0 : (equity / account.startingBalance - 1) * 100;
        return new PortfolioView(accountId, account.cashBalance, account.marginHeld, positionsValue,
                equity, account.startingBalance, ret, dayPnl, views);
    }

    /** Equity only — used by day-roll snapshots and the leaderboard. */
    public double equity(Account account) {
        double positionsValue = 0;
        for (Position pos : positionRepo.findByAccountIdAndQtyNot(account.id, 0)) {
            positionsValue += view(pos).marketValue();
        }
        return account.cashBalance + account.marginHeld + positionsValue;
    }

    public ScorecardView scorecard(long accountId) {
        List<ClosedTrade> trades = closedTradeRepo.findByAccountIdOrderByClosedSimTimeDesc(accountId);
        int wins = 0, losses = 0;
        double sumWin = 0, sumLoss = 0, sumHoldingHours = 0;
        for (ClosedTrade t : trades) {
            if (t.realizedPnl > 0) { wins++; sumWin += t.realizedPnl; }
            else if (t.realizedPnl < 0) { losses++; sumLoss += t.realizedPnl; }
            sumHoldingHours += Duration.between(t.openedSimTime, t.closedSimTime).toMinutes() / 60.0;
        }
        int total = trades.size();
        double winRatePct = total == 0 ? 0 : (double) wins / total * 100;
        double avgWin = wins == 0 ? 0 : sumWin / wins;
        double avgLoss = losses == 0 ? 0 : sumLoss / losses;
        double avgHoldingPeriodHours = total == 0 ? 0 : sumHoldingHours / total;

        List<EquitySnapshot> curve = snapshotRepo.findByAccountIdOrderBySimDate(accountId);
        double peak = curve.isEmpty() ? 0 : curve.get(0).equity;
        double maxDrawdown = 0, maxDrawdownPct = 0;
        for (EquitySnapshot s : curve) {
            peak = Math.max(peak, s.equity);
            double dd = peak - s.equity;
            if (dd > maxDrawdown) maxDrawdown = dd;
            double ddPct = peak == 0 ? 0 : dd / peak * 100;
            if (ddPct > maxDrawdownPct) maxDrawdownPct = ddPct;
        }

        return new ScorecardView(total, wins, losses, winRatePct, avgWin, avgLoss,
                avgHoldingPeriodHours, maxDrawdown, maxDrawdownPct);
    }

    private PositionView view(Position pos) {
        Instrument instr = market.instrument(pos.instrumentId);
        MarketDataService.Quote quote = market.quote(instr);
        double mid = quote.mid();
        double size = instr.contractSize;

        // marketValue is the position's contribution to equity given how cash was
        // exchanged at trade time; unrealized is always mark vs entry basis.
        double marketValue;
        double unrealized;
        switch (instr.instrumentType) {
            case EQUITY, FX_PAIR, OPTION, FX_OPTION -> {
                marketValue = mid * pos.qty * size;                 // cash already paid/received
                unrealized = (mid - pos.avgPrice) * pos.qty * size;
            }
            case FUTURE -> {
                double basis = pos.lastSettlePrice != null ? pos.lastSettlePrice : pos.avgPrice;
                marketValue = (mid - basis) * pos.qty * size;       // variation since last settle
                unrealized = marketValue;
            }
            default -> { // FORWARD, SWAP: no cash at inception, value is mark vs traded level
                marketValue = (mid - pos.avgPrice) * pos.qty * size;
                unrealized = marketValue;
            }
        }

        Double delta = null, gamma = null, theta = null, vega = null, marginUsed = null;
        BlackScholes.Greeks g = quote.greeks();
        if (g != null) {
            double mult = pos.qty * size;
            delta = g.delta() * mult;
            gamma = g.gamma() * mult;
            theta = g.theta() * mult;
            vega = g.vega() * mult;
        }
        if (instr.instrumentType == InstrumentType.FUTURE && instr.initialMargin != null) {
            marginUsed = Math.abs(pos.qty) * instr.initialMargin;
        }
        return new PositionView(pos.id, instr.id, instr.symbol, instr.name, instr.instrumentType.name(),
                pos.qty, pos.avgPrice, mid, marketValue, unrealized, pos.realizedPnl,
                delta, gamma, theta, vega, marginUsed, pos.lastSettlePrice,
                instr.expiryDate == null ? null : instr.expiryDate.toString());
    }
}
