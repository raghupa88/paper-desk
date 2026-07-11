import React, { useEffect } from 'react';
import { usePublishEvent } from 'esp-js-react';
import { useServices } from '../AppContext';
import { EventConst, ModelIds } from '../core/events';
import { ChainModel, ChainState } from '../models/ChainModel';
import { MarketModel, MarketState } from '../models/MarketModel';
import { Quote } from '../core/types';
import { useModelState, fmtNum } from './common';
import { TicketView } from './TicketView';

/** Options chain: strikes x expiries with live premiums and Greeks. */
export function OptionsChainView() {
  const { dataService } = useServices();
  const publish = usePublishEvent();
  const chainState = useModelState<ChainModel, ChainState>(ModelIds.chain, m => m.state);
  const market = useModelState<MarketModel, MarketState>(ModelIds.market, m => m.state);

  const underlyings = market.quotes.filter(q => q.type === 'EQUITY' || q.type === 'FX_PAIR');
  const underlyingId = chainState.underlyingId ?? underlyings[0]?.instrumentId ?? null;

  useEffect(() => {
    if (underlyingId == null) return;
    if (chainState.underlyingId !== underlyingId) {
      publish(ModelIds.chain, EventConst.chainUnderlyingSelected, { underlyingId });
    }
    void dataService.loadChain(underlyingId);
    const t = window.setInterval(() => void dataService.loadChain(underlyingId), 3000);
    return () => window.clearInterval(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [underlyingId]);

  const chain = chainState.chain;
  const expiry = chain?.expiries.find(e => e.expiry === chainState.selectedExpiry) ?? chain?.expiries[0];
  const spot = chain?.underlying.mid;

  const pick = (q: Quote | undefined, side: 'BUY' | 'SELL') => {
    if (!q) return;
    publish(ModelIds.trading, EventConst.instrumentChosen, { instrument: q, side });
  };

  return (
    <div className="flex gap-4 items-start">
      <div className="flex-1 space-y-3">
        <div className="flex items-center gap-3">
          <select className="input !w-56"
                  value={underlyingId ?? ''}
                  onChange={e => publish(ModelIds.chain, EventConst.chainUnderlyingSelected,
                    { underlyingId: Number(e.target.value) })}>
            {underlyings.map(u => (
              <option key={u.instrumentId} value={u.instrumentId}>{u.symbol} — {u.name}</option>
            ))}
          </select>
          {spot != null && <span className="num text-sm">spot <strong>{fmtNum(spot)}</strong></span>}
          <div className="flex gap-1">
            {chain?.expiries.map(e => (
              <button key={e.expiry}
                      className={`btn text-xs ${expiry?.expiry === e.expiry ? 'btn-accent' : ''}`}
                      onClick={() => publish(ModelIds.chain, EventConst.chainExpirySelected, { expiry: e.expiry })}>
                {e.expiry}
              </button>
            ))}
          </div>
        </div>

        <div className="panel overflow-auto max-h-[calc(100vh-220px)]">
          <table className="tbl num">
            <thead>
              <tr>
                <th colSpan={5} className="!text-center text-desk-up">CALLS — click to trade</th>
                <th className="!text-center">Strike</th>
                <th colSpan={5} className="!text-center text-desk-down">PUTS — click to trade</th>
              </tr>
              <tr>
                <th className="!text-right">Δ</th><th className="!text-right">Γ</th>
                <th className="!text-right">Θ/day</th><th className="!text-right">Vega</th>
                <th className="!text-right">Premium</th>
                <th className="!text-center"></th>
                <th className="!text-right">Premium</th>
                <th className="!text-right">Δ</th><th className="!text-right">Γ</th>
                <th className="!text-right">Θ/day</th><th className="!text-right">Vega</th>
              </tr>
            </thead>
            <tbody>
              {(expiry?.rows ?? []).map(row => {
                const itmCall = spot != null && row.strike < spot;
                const itmPut = spot != null && row.strike > spot;
                return (
                  <tr key={row.strike}>
                    <td className="text-right text-desk-dim">{row.call?.greeks ? row.call.greeks.delta.toFixed(3) : '—'}</td>
                    <td className="text-right text-desk-dim">{row.call?.greeks ? row.call.greeks.gamma.toFixed(4) : '—'}</td>
                    <td className="text-right text-desk-dim">{row.call?.greeks ? row.call.greeks.theta.toFixed(4) : '—'}</td>
                    <td className="text-right text-desk-dim">{row.call?.greeks ? row.call.greeks.vega.toFixed(4) : '—'}</td>
                    <td className={`text-right cursor-pointer hover:underline font-medium ${itmCall ? 'bg-desk-up/10' : ''}`}
                        onClick={() => pick(row.call, 'BUY')}>{fmtNum(row.call?.mid)}</td>
                    <td className="text-center font-semibold bg-desk-bg">{fmtNum(row.strike)}</td>
                    <td className={`text-right cursor-pointer hover:underline font-medium ${itmPut ? 'bg-desk-down/10' : ''}`}
                        onClick={() => pick(row.put, 'BUY')}>{fmtNum(row.put?.mid)}</td>
                    <td className="text-right text-desk-dim">{row.put?.greeks ? row.put.greeks.delta.toFixed(3) : '—'}</td>
                    <td className="text-right text-desk-dim">{row.put?.greeks ? row.put.greeks.gamma.toFixed(4) : '—'}</td>
                    <td className="text-right text-desk-dim">{row.put?.greeks ? row.put.greeks.theta.toFixed(4) : '—'}</td>
                    <td className="text-right text-desk-dim">{row.put?.greeks ? row.put.greeks.vega.toFixed(4) : '—'}</td>
                  </tr>
                );
              })}
              {!expiry && <tr><td colSpan={11} className="text-desk-dim">Loading chain…</td></tr>}
            </tbody>
          </table>
        </div>
      </div>
      <TicketView />
    </div>
  );
}
