import React, { useEffect } from 'react';
import { usePublishEvent } from 'esp-js-react';
import { useServices } from '../AppContext';
import { EventConst, ModelIds } from '../core/events';
import { FxModel, FxState } from '../models/FxModel';
import { TradingModel, TradingState } from '../models/TradingModel';
import { useModelState, fmtMoney, fmtNum } from './common';
import { Pnl } from './Pnl';
import { TicketView } from './TicketView';

/**
 * FX Trader view: raw mid pricing on the pairs, the FX book with live
 * Greeks, and a spot risk ladder (P&L/delta/gamma/vega across spot shifts).
 * Trades placed from here execute at market (mid ± spread), viewContext=TRADER.
 */
export function FxTraderView() {
  const { dataService } = useServices();
  const publish = usePublishEvent();
  const fx = useModelState<FxModel, FxState>(ModelIds.fx, m => m.state);
  const trading = useModelState<TradingModel, TradingState>(ModelIds.trading, m => m.state);

  useEffect(() => {
    void dataService.refreshLadder();
    const t = window.setInterval(() => void dataService.refreshLadder(), 3000);
    return () => window.clearInterval(t);
  }, [dataService]);

  const fxPositions = (trading.portfolio?.positions ?? [])
    .filter(p => p.type === 'FX_PAIR' || p.type === 'FX_OPTION');
  const bookDelta = fxPositions.reduce((s, p) => s + (p.delta ?? (p.type === 'FX_PAIR' ? p.qty : 0)), 0);
  const bookVega = fxPositions.reduce((s, p) => s + (p.vega ?? 0), 0);

  return (
    <div className="flex gap-4 items-start">
      <div className="flex-1 space-y-4">
        <div className="panel">
          <div className="panel-title">Pairs — dealer mids (click to trade spot at market)</div>
          <table className="tbl num">
            <thead><tr><th>Pair</th><th className="!text-right">Bid</th><th className="!text-right">Mid</th><th className="!text-right">Ask</th></tr></thead>
            <tbody>
              {fx.pairs.map(p => (
                <tr key={p.instrumentId} className="cursor-pointer hover:bg-desk-bg/60"
                    onClick={() => publish(ModelIds.trading, EventConst.instrumentChosen,
                      { instrument: p, viewContext: 'TRADER' })}>
                  <td className="font-medium">{p.symbol}</td>
                  <td className="text-right text-desk-down">{fmtNum(p.bid)}</td>
                  <td className="text-right">{fmtNum(p.mid)}</td>
                  <td className="text-right text-desk-up">{fmtNum(p.ask)}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <div className="px-3 py-2 text-xs text-desk-dim">
            FX options at dealer mid: use the Options Chain tab on a pair — orders from there
            can be tagged TRADER via this desk's ticket.
          </div>
        </div>

        <div className="panel">
          <div className="panel-title">FX book — positions &amp; Greeks
            <span className="ml-3 normal-case text-desk-text">book Δ {fmtNum(bookDelta, 0)} · book vega {fmtNum(bookVega, 2)}</span>
          </div>
          <table className="tbl num">
            <thead><tr><th>Instrument</th><th className="!text-right">Qty</th><th className="!text-right">Mark</th>
              <th className="!text-right">Δ</th><th className="!text-right">Γ</th><th className="!text-right">Θ/day</th>
              <th className="!text-right">Vega</th><th className="!text-right">Unrlzd</th></tr></thead>
            <tbody>
              {fxPositions.map(p => (
                <tr key={p.positionId}>
                  <td>{p.symbol}</td>
                  <td className="text-right">{fmtNum(p.qty, 0)}</td>
                  <td className="text-right">{fmtNum(p.mark, 6)}</td>
                  <td className="text-right">{p.delta != null ? fmtNum(p.delta, 0) : '—'}</td>
                  <td className="text-right">{p.gamma != null ? fmtNum(p.gamma, 2) : '—'}</td>
                  <td className="text-right">{p.theta != null ? fmtMoney(p.theta) : '—'}</td>
                  <td className="text-right">{p.vega != null ? fmtNum(p.vega, 2) : '—'}</td>
                  <td className="text-right"><Pnl value={p.unrealizedPnl} /></td>
                </tr>
              ))}
              {fxPositions.length === 0 &&
                <tr><td colSpan={8} className="text-desk-dim">No FX risk yet — book a deal in FX Sales or trade a pair.</td></tr>}
            </tbody>
          </table>
        </div>

        {fx.ladder.map(l => (
          <div key={l.pair} className="panel">
            <div className="panel-title">{l.pair} risk ladder — spot {fmtNum(l.spot)}</div>
            <table className="tbl num">
              <thead><tr><th className="!text-right">Shift</th><th className="!text-right">Spot</th>
                <th className="!text-right">P&L</th><th className="!text-right">Δ</th>
                <th className="!text-right">Γ</th><th className="!text-right">Vega</th></tr></thead>
              <tbody>
                {l.rows.map(r => (
                  <tr key={r.shiftPct} className={r.shiftPct === 0 ? 'bg-desk-bg' : ''}>
                    <td className="text-right">{r.shiftPct > 0 ? '+' : ''}{r.shiftPct.toFixed(0)}%</td>
                    <td className="text-right">{fmtNum(r.spot)}</td>
                    <td className="text-right"><Pnl value={r.pnl} /></td>
                    <td className="text-right">{fmtNum(r.delta, 0)}</td>
                    <td className="text-right">{fmtNum(r.gamma, 2)}</td>
                    <td className="text-right">{fmtNum(r.vega, 2)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ))}
      </div>
      <TicketView />
    </div>
  );
}
