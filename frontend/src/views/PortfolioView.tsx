import React, { useEffect } from 'react';
import { useServices } from '../AppContext';
import { ModelIds } from '../core/events';
import { TradingModel, TradingState } from '../models/TradingModel';
import { useModelState, fmtMoney, fmtNum } from './common';
import { Pnl } from './Pnl';

export function PortfolioView() {
  const { dataService } = useServices();
  const state = useModelState<TradingModel, TradingState>(ModelIds.trading, m => m.state);
  const p = state.portfolio;

  useEffect(() => { void dataService.refreshSettlements(); }, [dataService]);

  return (
    <div className="space-y-4">
      <div className="panel">
        <div className="panel-title">
          Positions — live mark-to-market
          {p && <span className="ml-3 normal-case text-desk-text num">
            cash {fmtMoney(p.cash, 0)} · margin held {fmtMoney(p.marginHeld, 0)} · equity {fmtMoney(p.equity, 0)}
          </span>}
        </div>
        <table className="tbl num">
          <thead>
            <tr>
              <th>Symbol</th><th>Type</th><th className="!text-right">Qty</th>
              <th className="!text-right">Avg / traded</th><th className="!text-right">Mark</th>
              <th className="!text-right">Value</th><th className="!text-right">Unrlzd P&L</th>
              <th className="!text-right">Rlzd P&L</th>
              <th className="!text-right">Δ</th><th className="!text-right">Θ/day</th>
              <th className="!text-right">Margin</th><th>Expiry</th>
            </tr>
          </thead>
          <tbody>
            {(p?.positions ?? []).map(pos => (
              <tr key={pos.positionId}>
                <td className="font-medium">{pos.symbol}</td>
                <td className="text-xs text-desk-dim">{pos.type}</td>
                <td className="text-right">{fmtNum(pos.qty, 2)}</td>
                <td className="text-right">{fmtNum(pos.avgPrice, 4)}</td>
                <td className="text-right">{fmtNum(pos.mark, 4)}</td>
                <td className="text-right">{fmtMoney(pos.marketValue)}</td>
                <td className="text-right"><Pnl value={pos.unrealizedPnl} /></td>
                <td className="text-right"><Pnl value={pos.realizedPnl} /></td>
                <td className="text-right">{pos.delta != null ? fmtNum(pos.delta, 2) : '—'}</td>
                <td className="text-right">{pos.theta != null ? fmtMoney(pos.theta) : '—'}</td>
                <td className="text-right">{pos.marginUsed != null ? fmtMoney(pos.marginUsed, 0) : '—'}</td>
                <td className="text-xs text-desk-dim">{pos.expiryDate ?? '—'}</td>
              </tr>
            ))}
            {(!p || p.positions.length === 0) &&
              <tr><td colSpan={12} className="text-desk-dim">No open positions.</td></tr>}
          </tbody>
        </table>
      </div>

      <div className="panel">
        <div className="panel-title">Settlements &amp; lifecycle events — daily futures MTM, margin calls, expiries, swap fixings</div>
        <table className="tbl num">
          <thead><tr><th>Sim date</th><th>Kind</th><th>Instrument</th><th className="!text-right">Cash flow</th><th>Detail</th></tr></thead>
          <tbody>
            {state.settlements.map(s => (
              <tr key={s.id}>
                <td>{s.simDate}</td>
                <td className={s.kind === 'MARGIN_CALL' || s.kind === 'LIQUIDATION' ? 'text-desk-down font-semibold' : 'text-desk-warn'}>{s.kind}</td>
                <td>{s.symbol ?? '—'}</td>
                <td className="text-right"><Pnl value={s.cashFlow} /></td>
                <td className="text-xs text-desk-dim">{s.detail}</td>
              </tr>
            ))}
            {state.settlements.length === 0 &&
              <tr><td colSpan={5} className="text-desk-dim">Nothing settled yet — step a sim day with futures or options on.</td></tr>}
          </tbody>
        </table>
      </div>
    </div>
  );
}
