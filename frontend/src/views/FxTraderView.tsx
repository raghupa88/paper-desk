import React, { useEffect } from 'react';
import { usePublishEvent } from 'esp-js-react';
import { useServices } from '../AppContext';
import { EventConst, ModelIds } from '../core/events';
import { FxModel, FxState } from '../models/FxModel';
import { TradingModel, TradingState } from '../models/TradingModel';
import { ChainModel, ChainState } from '../models/ChainModel';
import { Quote } from '../core/types';
import { useModelState, fmtMoney, fmtNum } from './common';
import { Pnl } from './Pnl';
import { InfoTip } from './InfoTip';
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
  const chainState = useModelState<ChainModel, ChainState>(ModelIds.chain, m => m.state);

  useEffect(() => {
    void dataService.refreshLadder();
    const t = window.setInterval(() => void dataService.refreshLadder(), 3000);
    return () => window.clearInterval(t);
  }, [dataService]);

  // Chain underlying is shared with the Options Chain tab (ModelIds.chain). Default to
  // whichever FX pair is already selected there; otherwise fall back to the first pair
  // in the table above (its "current" selection until the user picks another).
  const isFxUnderlying = fx.pairs.some(p => p.instrumentId === chainState.underlyingId);
  const chainUnderlyingId = isFxUnderlying ? chainState.underlyingId : fx.pairs[0]?.instrumentId ?? null;

  useEffect(() => {
    if (chainUnderlyingId == null) return;
    if (chainState.underlyingId !== chainUnderlyingId) {
      publish(ModelIds.chain, EventConst.chainUnderlyingSelected, { underlyingId: chainUnderlyingId });
    }
    void dataService.loadChain(chainUnderlyingId);
    const t = window.setInterval(() => void dataService.loadChain(chainUnderlyingId), 3000);
    return () => window.clearInterval(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [chainUnderlyingId]);

  const chain = chainState.chain;
  const chainExpiry = chain?.expiries.find(e => e.expiry === chainState.selectedExpiry) ?? chain?.expiries[0];
  const chainSpot = chain?.underlying.mid;

  const pickOption = (q: Quote | undefined, side: 'BUY' | 'SELL') => {
    if (!q) return;
    publish(ModelIds.trading, EventConst.instrumentChosen, { instrument: q, side, viewContext: 'TRADER' });
  };

  const fxPositions = (trading.portfolio?.positions ?? [])
    .filter(p => p.type === 'FX_PAIR' || p.type === 'FX_OPTION');
  const bookDelta = fxPositions.reduce((s, p) => s + (p.delta ?? (p.type === 'FX_PAIR' ? p.qty : 0)), 0);
  const bookVega = fxPositions.reduce((s, p) => s + (p.vega ?? 0), 0);

  return (
    <div className="flex flex-col lg:flex-row gap-4 items-start">
      <div className="flex-1 w-full space-y-4">
        <div className="panel overflow-x-auto">
          <div className="panel-title">Pairs — dealer mids (click to trade spot at market)</div>
          <table className="tbl num">
            <thead><tr><th>Pair</th><th className="!text-right">Bid</th><th className="!text-right">Mid</th><th className="!text-right">Ask</th></tr></thead>
            <tbody>
              {fx.pairs.map(p => (
                <tr key={p.instrumentId}
                    className={`cursor-pointer hover:bg-desk-bg/60 ${p.instrumentId === chainUnderlyingId ? 'bg-desk-bg' : ''}`}
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
        </div>

        <div className="panel">
          <div className="panel-title">FX options chain — dealer mid (click a premium to trade)</div>
          <div className="flex flex-wrap items-center gap-3 px-4 pb-3">
            <select className="input !w-56"
                    value={chainUnderlyingId ?? ''}
                    onChange={e => publish(ModelIds.chain, EventConst.chainUnderlyingSelected,
                      { underlyingId: Number(e.target.value) })}>
              {fx.pairs.map(p => (
                <option key={p.instrumentId} value={p.instrumentId}>{p.symbol} — {p.name}</option>
              ))}
            </select>
            {chainSpot != null && <span className="num text-sm">spot <strong>{fmtNum(chainSpot)}</strong></span>}
            <div className="flex gap-1 flex-wrap">
              {chain?.expiries.map(e => (
                <button key={e.expiry}
                        className={`btn text-xs ${chainExpiry?.expiry === e.expiry ? 'btn-accent' : ''}`}
                        onClick={() => publish(ModelIds.chain, EventConst.chainExpirySelected, { expiry: e.expiry })}>
                  {e.expiry}
                </button>
              ))}
            </div>
          </div>
          <div className="overflow-auto max-h-96 lg:max-h-[calc(100vh-220px)]">
            <table className="tbl num">
              <thead>
                <tr>
                  <th colSpan={5} className="!text-center text-desk-up">CALLS — click to trade</th>
                  <th className="!text-center">Strike</th>
                  <th colSpan={5} className="!text-center text-desk-down">PUTS — click to trade</th>
                </tr>
                <tr>
                  <th className="!text-right">Δ<InfoTip term="delta" /></th>
                  <th className="!text-right">Γ<InfoTip term="gamma" /></th>
                  <th className="!text-right">Θ/day<InfoTip term="theta" /></th>
                  <th className="!text-right">Vega<InfoTip term="vega" /></th>
                  <th className="!text-right">Premium<InfoTip term="premium" /></th>
                  <th className="!text-center"></th>
                  <th className="!text-right">Premium<InfoTip term="premium" /></th>
                  <th className="!text-right">Δ<InfoTip term="delta" /></th>
                  <th className="!text-right">Γ<InfoTip term="gamma" /></th>
                  <th className="!text-right">Θ/day<InfoTip term="theta" /></th>
                  <th className="!text-right">Vega<InfoTip term="vega" /></th>
                </tr>
              </thead>
              <tbody>
                {(chainExpiry?.rows ?? []).map(row => {
                  const itmCall = chainSpot != null && row.strike < chainSpot;
                  const itmPut = chainSpot != null && row.strike > chainSpot;
                  return (
                    <tr key={row.strike}>
                      <td className="text-right text-desk-dim">{row.call?.greeks ? row.call.greeks.delta.toFixed(3) : '—'}</td>
                      <td className="text-right text-desk-dim">{row.call?.greeks ? row.call.greeks.gamma.toFixed(4) : '—'}</td>
                      <td className="text-right text-desk-dim">{row.call?.greeks ? row.call.greeks.theta.toFixed(4) : '—'}</td>
                      <td className="text-right text-desk-dim">{row.call?.greeks ? row.call.greeks.vega.toFixed(4) : '—'}</td>
                      <td className={`text-right cursor-pointer hover:underline font-medium ${itmCall ? 'bg-desk-up/10' : ''}`}
                          onClick={() => pickOption(row.call, 'BUY')}>{fmtNum(row.call?.mid)}</td>
                      <td className="text-center font-semibold bg-desk-bg">{fmtNum(row.strike)}</td>
                      <td className={`text-right cursor-pointer hover:underline font-medium ${itmPut ? 'bg-desk-down/10' : ''}`}
                          onClick={() => pickOption(row.put, 'BUY')}>{fmtNum(row.put?.mid)}</td>
                      <td className="text-right text-desk-dim">{row.put?.greeks ? row.put.greeks.delta.toFixed(3) : '—'}</td>
                      <td className="text-right text-desk-dim">{row.put?.greeks ? row.put.greeks.gamma.toFixed(4) : '—'}</td>
                      <td className="text-right text-desk-dim">{row.put?.greeks ? row.put.greeks.theta.toFixed(4) : '—'}</td>
                      <td className="text-right text-desk-dim">{row.put?.greeks ? row.put.greeks.vega.toFixed(4) : '—'}</td>
                    </tr>
                  );
                })}
                {!chainExpiry && <tr><td colSpan={11} className="text-desk-dim">Loading chain…</td></tr>}
              </tbody>
            </table>
          </div>
        </div>

        <div className="panel overflow-x-auto">
          <div className="panel-title">FX book — positions &amp; Greeks
            <span className="ml-3 normal-case text-desk-text">book Δ {fmtNum(bookDelta, 0)} · book vega {fmtNum(bookVega, 2)}</span>
          </div>
          <table className="tbl num">
            <thead><tr><th>Instrument</th><th className="!text-right">Qty</th><th className="!text-right">Mark</th>
              <th className="!text-right">Δ<InfoTip term="delta" /></th>
              <th className="!text-right">Γ<InfoTip term="gamma" /></th>
              <th className="!text-right">Θ/day<InfoTip term="theta" /></th>
              <th className="!text-right">Vega<InfoTip term="vega" /></th>
              <th className="!text-right">Unrlzd</th></tr></thead>
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
          <div key={l.pair} className="panel overflow-x-auto">
            <div className="panel-title">{l.pair} risk ladder — spot {fmtNum(l.spot)}</div>
            <table className="tbl num">
              <thead><tr><th className="!text-right">Shift</th><th className="!text-right">Spot</th>
                <th className="!text-right">P&L</th><th className="!text-right">Δ<InfoTip term="delta" /></th>
                <th className="!text-right">Γ<InfoTip term="gamma" /></th>
                <th className="!text-right">Vega<InfoTip term="vega" /></th></tr></thead>
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
