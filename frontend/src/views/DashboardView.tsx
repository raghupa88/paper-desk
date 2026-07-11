import React from 'react';
import { ModelIds } from '../core/events';
import { TradingModel, TradingState } from '../models/TradingModel';
import { useModelState, fmtMoney, fmtPct, pnlCls } from './common';
import { EquityChart } from './ChartView';

function Stat({ label, value, cls }: { label: string; value: string; cls?: string }) {
  return (
    <div className="panel p-4 flex-1">
      <div className="text-xs uppercase tracking-wider text-desk-dim">{label}</div>
      <div className={`text-xl font-semibold num mt-1 ${cls ?? ''}`}>{value}</div>
    </div>
  );
}

export function DashboardView() {
  const state = useModelState<TradingModel, TradingState>(ModelIds.trading, m => m.state);
  const p = state.portfolio;

  return (
    <div className="space-y-4">
      <div className="flex gap-4">
        <Stat label="Portfolio value" value={fmtMoney(p?.equity, 0)} />
        <Stat label="Cash" value={fmtMoney(p?.cash, 0)} />
        <Stat label="Margin held" value={fmtMoney(p?.marginHeld, 0)} />
        <Stat label="Today's P&L" value={fmtMoney(p?.dayPnl, 0)} cls={pnlCls(p?.dayPnl)} />
        <Stat label="Total return" value={fmtPct(p?.totalReturnPct)} cls={pnlCls(p?.totalReturnPct)} />
      </div>

      <div className="flex gap-4 items-start">
        <div className="panel flex-1 p-3">
          <div className="panel-title !p-0 mb-2">Equity curve (end of sim day)</div>
          <EquityChart points={state.equityHistory} />
          {state.equityHistory.length === 0 &&
            <div className="text-desk-dim text-xs mt-2">Snapshots appear as sim days close.</div>}
        </div>

        <div className="panel w-96 shrink-0">
          <div className="panel-title">Open positions</div>
          <table className="tbl">
            <thead><tr><th>Symbol</th><th className="!text-right">Qty</th><th className="!text-right">Unrlzd P&L</th></tr></thead>
            <tbody>
              {(p?.positions ?? []).map(pos => (
                <tr key={pos.positionId}>
                  <td>{pos.symbol}</td>
                  <td className="num text-right">{pos.qty}</td>
                  <td className={`num text-right ${pnlCls(pos.unrealizedPnl)}`}>{fmtMoney(pos.unrealizedPnl)}</td>
                </tr>
              ))}
              {(!p || p.positions.length === 0) &&
                <tr><td colSpan={3} className="text-desk-dim">No open positions — head to the Market tab.</td></tr>}
            </tbody>
          </table>

          <div className="panel-title border-t border-desk-border mt-2">Desk events</div>
          <div className="max-h-48 overflow-auto px-3 pb-3 space-y-1">
            {state.notifications.map(n => (
              <div key={n.at + n.type} className="text-xs">
                <span className={n.type === 'MARGIN_CALL' ? 'text-desk-down font-bold' : 'text-desk-warn'}>{n.type}</span>
                <span className="text-desk-dim ml-2">{n.detail}</span>
              </div>
            ))}
            {state.notifications.length === 0 && <div className="text-xs text-desk-dim">Fills, margin calls and settlements will stream here.</div>}
          </div>
        </div>
      </div>
    </div>
  );
}
