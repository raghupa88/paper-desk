import React from 'react';
import { useServices } from '../AppContext';
import { ModelIds } from '../core/events';
import { TradingModel, TradingState } from '../models/TradingModel';
import { useModelState, fmtNum, fmtSimTime } from './common';

const STATUS_CLS: Record<string, string> = {
  FILLED: 'text-desk-up', NEW: 'text-desk-accent',
  CANCELLED: 'text-desk-dim', REJECTED: 'text-desk-down',
};

export function BlotterView() {
  const { dataService } = useServices();
  const state = useModelState<TradingModel, TradingState>(ModelIds.trading, m => m.state);

  return (
    <div className="panel">
      <div className="panel-title">Trade history — every order and fill (sim time)</div>
      <table className="tbl num">
        <thead>
          <tr>
            <th>#</th><th>Placed (sim)</th><th>Symbol</th><th>Side</th><th>Type</th>
            <th className="!text-right">Qty</th><th className="!text-right">Limit</th>
            <th className="!text-right">Fill px</th><th>Status</th><th>View</th><th></th>
          </tr>
        </thead>
        <tbody>
          {state.blotter.map(o => (
            <tr key={o.orderId}>
              <td className="text-desk-dim">{o.orderId}</td>
              <td>{fmtSimTime(o.placedSimTime)}</td>
              <td className="font-medium">{o.symbol}</td>
              <td className={o.side === 'BUY' ? 'text-desk-up' : 'text-desk-down'}>{o.side}</td>
              <td>{o.orderType}</td>
              <td className="text-right">{fmtNum(o.qty, 2)}</td>
              <td className="text-right">{o.limitPrice != null ? fmtNum(o.limitPrice) : '—'}</td>
              <td className="text-right">{o.fills[0] ? fmtNum(o.fills[0].price) : '—'}</td>
              <td className={STATUS_CLS[o.status] ?? ''}>{o.status}{o.rejectReason && (
                <span className="text-xs text-desk-dim block">{o.rejectReason}</span>)}</td>
              <td className="text-xs text-desk-dim">{o.viewContext ?? '—'}</td>
              <td>
                {o.status === 'NEW' &&
                  <button className="btn text-xs" onClick={() => void dataService.cancelOrder(o.orderId)}>cancel</button>}
              </td>
            </tr>
          ))}
          {state.blotter.length === 0 &&
            <tr><td colSpan={11} className="text-desk-dim">No orders yet.</td></tr>}
        </tbody>
      </table>
    </div>
  );
}
