import React from 'react';
import { usePublishEvent } from 'esp-js-react';
import { useServices } from '../AppContext';
import { EventConst, ModelIds } from '../core/events';
import { FxModel, FxState, RfqForm } from '../models/FxModel';
import { useModelState, fmtMoney, fmtNum } from './common';

/**
 * FX Sales view: quote a client RFQ for a custom FX option. The all-in
 * premium is dealer mid plus the sales margin. Executing books the deal
 * (viewContext=SALES) so the Trader view sees the resulting risk.
 */
export function FxSalesView() {
  const { dataService } = useServices();
  const publish = usePublishEvent();
  const fx = useModelState<FxModel, FxState>(ModelIds.fx, m => m.state);

  const change = (patch: Partial<RfqForm>) => publish(ModelIds.fx, EventConst.rfqChanged, patch);
  const rfqBody = () => ({
    underlyingId: fx.rfq.underlyingId,
    strike: fx.rfq.strike,
    expiryDate: fx.rfq.expiryDate,
    callPut: fx.rfq.callPut,
    side: fx.rfq.side,
    notional: fx.rfq.notional,
    salesMarginBps: fx.rfq.salesMarginBps,
  });

  const requestQuote = () => {
    publish(ModelIds.fx, EventConst.rfqQuoteRequested, {});
    void dataService.rfq(rfqBody());
  };

  const pair = fx.pairs.find(p => p.instrumentId === fx.rfq.underlyingId);

  return (
    <div className="flex gap-4 items-start">
      <div className="panel p-5 w-[420px] space-y-3">
        <div className="panel-title !p-0">Client RFQ — FX option</div>
        <p className="text-xs text-desk-dim">
          You are the salesperson: price a client's option request. The client pays
          mid ± your sales margin. Executed deals book into your account.
        </p>

        <label className="block text-xs text-desk-dim">Currency pair
          <select className="input mt-1" value={fx.rfq.underlyingId ?? ''}
                  onChange={e => {
                    const p = fx.pairs.find(x => x.instrumentId === Number(e.target.value));
                    change({ underlyingId: Number(e.target.value), strike: p ? Math.round(p.mid * 10000) / 10000 : null });
                  }}>
            {fx.pairs.map(p => <option key={p.instrumentId} value={p.instrumentId}>{p.symbol}</option>)}
          </select>
        </label>

        <div className="grid grid-cols-2 gap-2">
          <label className="block text-xs text-desk-dim">Call / Put
            <select className="input mt-1" value={fx.rfq.callPut}
                    onChange={e => change({ callPut: e.target.value as any })}>
              <option value="CALL">Call</option><option value="PUT">Put</option>
            </select>
          </label>
          <label className="block text-xs text-desk-dim">Client direction
            <select className="input mt-1" value={fx.rfq.side}
                    onChange={e => change({ side: e.target.value as any })}>
              <option value="BUY">Client buys</option><option value="SELL">Client sells</option>
            </select>
          </label>
          <label className="block text-xs text-desk-dim">Strike {pair && <span>(spot {fmtNum(pair.mid)})</span>}
            <input className="input mt-1" type="number" step="0.0001" value={fx.rfq.strike ?? ''}
                   onChange={e => change({ strike: Number(e.target.value) })} />
          </label>
          <label className="block text-xs text-desk-dim">Expiry (sim date)
            <input className="input mt-1" type="date" value={fx.rfq.expiryDate}
                   onChange={e => change({ expiryDate: e.target.value })} />
          </label>
          <label className="block text-xs text-desk-dim">Notional (base ccy)
            <input className="input mt-1" type="number" min={1000} step={1000} value={fx.rfq.notional}
                   onChange={e => change({ notional: Number(e.target.value) })} />
          </label>
          <label className="block text-xs text-desk-dim">Sales margin (bps of spot)
            <input className="input mt-1" type="number" min={0} step={1} value={fx.rfq.salesMarginBps}
                   onChange={e => change({ salesMarginBps: Number(e.target.value) })} />
          </label>
        </div>

        <button className="btn btn-accent w-full" disabled={fx.quoting} onClick={requestQuote}>
          {fx.quoting ? 'Pricing…' : 'Quote client'}
        </button>
        {fx.quoteError && <div className="text-desk-down text-xs">{fx.quoteError}</div>}
      </div>

      <div className="space-y-4 w-[420px]">
        {fx.quote && (
          <div className="panel p-5 space-y-2">
            <div className="panel-title !p-0">Quote — {fx.quote.pair} {fx.quote.callPut} {fmtNum(fx.quote.strike)} exp {fx.quote.expiryDate}</div>
            <table className="w-full text-sm num">
              <tbody>
                <tr><td className="text-desk-dim py-0.5">Dealer mid (Garman-Kohlhagen)</td><td className="text-right">{fx.quote.midPrice.toFixed(6)}</td></tr>
                <tr><td className="text-desk-dim py-0.5">Sales margin ({fx.quote.salesMarginBps} bps)</td><td className="text-right">{fx.quote.salesMarginPerUnit.toFixed(6)}</td></tr>
                <tr className="font-semibold"><td className="py-0.5">All-in client price / unit</td><td className="text-right text-desk-accent">{fx.quote.allInPrice.toFixed(6)}</td></tr>
                <tr className="font-semibold"><td className="py-0.5">Premium on {fmtMoney(fx.quote.notional, 0)}</td><td className="text-right text-desk-accent">{fmtMoney(fx.quote.premiumTotal)}</td></tr>
                <tr><td className="text-desk-dim pt-2">Greeks /unit</td><td className="text-right pt-2 text-xs text-desk-dim">
                  Δ {fx.quote.greeks.delta.toFixed(3)} · Γ {fx.quote.greeks.gamma.toFixed(4)} · Θ {fx.quote.greeks.theta.toFixed(6)} · V {fx.quote.greeks.vega.toFixed(6)}
                </td></tr>
              </tbody>
            </table>
            <button className="btn btn-buy w-full"
                    onClick={() => void dataService.rfqExecute(rfqBody())}>
              Execute deal at all-in
            </button>
            <div className="text-[10px] text-desk-dim">Re-priced live at execution — the fill uses the fresh all-in price.</div>
          </div>
        )}

        {fx.lastDeal && (
          <div className="panel p-4 text-sm">
            <div className="panel-title !p-0 mb-1">Last deal captured ✓</div>
            <div className="num">
              {fx.lastDeal.side} {fmtMoney(fx.lastDeal.notional, 0)} {fx.lastDeal.pair} {fx.lastDeal.callPut}{' '}
              K={fmtNum(fx.lastDeal.strike)} exp {fx.lastDeal.expiryDate} @ {fx.lastDeal.allInPrice.toFixed(6)}
              <span className="text-desk-dim"> (order #{fx.lastDeal.orderId}, {fx.lastDeal.orderStatus})</span>
            </div>
            <div className="text-xs text-desk-dim mt-1">Flip to FX Trader to see the book's Greeks and risk ladder.</div>
          </div>
        )}
      </div>
    </div>
  );
}
