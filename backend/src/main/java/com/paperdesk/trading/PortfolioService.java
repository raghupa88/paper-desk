package com.paperdesk.trading;

import com.paperdesk.domain.Account;
import com.paperdesk.domain.Enums.InstrumentType;
import com.paperdesk.domain.Instrument;
import com.paperdesk.domain.Position;
import com.paperdesk.pricing.BlackScholes;
import com.paperdesk.repo.AccountRepo;
import com.paperdesk.repo.EquitySnapshotRepo;
import com.paperdesk.repo.PositionRepo;
import com.paperdesk.sim.MarketDataService;
import org.springframework.stereotype.Service;

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

    private final PositionRepo positionRepo;
    private final AccountRepo accountRepo;
    private final EquitySnapshotRepo snapshotRepo;
    private final MarketDataService market;

    public PortfolioService(PositionRepo positionRepo, AccountRepo accountRepo,
                            EquitySnapshotRepo snapshotRepo, MarketDataService market) {
        this.positionRepo = positionRepo;
        this.accountRepo = accountRepo;
        this.snapshotRepo = snapshotRepo;
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
