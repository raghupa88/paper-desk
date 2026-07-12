import React, { useState } from 'react';
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
  const [expandedOrderId, setExpandedOrderId] = useState<number | null>(null);

  const toggleComments = (orderId: number) => {
    if (expandedOrderId === orderId) { setExpandedOrderId(null); return; }
    setExpandedOrderId(orderId);
    if (!state.orderComments[orderId]) void dataService.loadOrderComments(orderId);
  };

  return (
    <div className="panel">
      <div className="panel-title">Trade history — every order and fill (sim time)</div>
      <div className="overflow-x-auto">
      <table className="tbl num">
        <thead>
          <tr>
            <th>#</th><th>Placed (sim)</th><th>Symbol</th><th>Side</th><th>Type</th>
            <th className="!text-right">Qty</th><th className="!text-right">Limit</th>
            <th className="!text-right">Fill px</th><th>Status</th><th>View</th><th></th><th></th>
          </tr>
        </thead>
        <tbody>
          {state.blotter.map(o => (
            <React.Fragment key={o.orderId}>
            <tr>
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
              <td>
                <button className="btn text-xs normal-case" aria-label={`Instructor notes for order ${o.orderId}`}
                        onClick={() => toggleComments(o.orderId)}>💬</button>
              </td>
            </tr>
            {expandedOrderId === o.orderId && (
              <tr>
                <td colSpan={12} className="bg-desk-bg/40 !text-left">
                  <div className="p-2 space-y-1">
                    {(state.orderComments[o.orderId] ?? []).map(c => (
                      <div key={c.id} className="text-xs">
                        <span className="font-semibold">{c.instructorName}</span>
                        <span className="text-desk-dim ml-2">{fmtSimTime(c.createdAt)}</span>
                        <div>{c.comment}</div>
                      </div>
                    ))}
                    {(state.orderComments[o.orderId] ?? []).length === 0 &&
                      <div className="text-xs text-desk-dim">No instructor notes on this trade.</div>}
                  </div>
                </td>
              </tr>
            )}
            </React.Fragment>
          ))}
          {state.blotter.length === 0 &&
            <tr><td colSpan={12} className="text-desk-dim">No orders yet.</td></tr>}
        </tbody>
      </table>
      </div>
    </div>
  );
}
