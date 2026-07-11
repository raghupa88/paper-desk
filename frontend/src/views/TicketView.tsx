import React from 'react';
import { usePublishEvent } from 'esp-js-react';
import { useServices } from '../AppContext';
import { EventConst, ModelIds } from '../core/events';
import { SessionModel } from '../models/SessionModel';
import { TradingModel, TicketState } from '../models/TradingModel';
import { useModelState, fmtMoney, fmtNum } from './common';
import { InfoTip } from './InfoTip';

/**
 * The order ticket. Instrument-aware: shows premium/Greeks for options,
 * margin requirement before confirm for futures, notional/fixed-rate for
 * swaps. Pure esp flow: edits publish ticketChanged, submit goes through
 * DataService which publishes the result back into the trading model.
 */
export function TicketView() {
  const { dataService } = useServices();
  const publish = usePublishEvent();
  const ticket = useModelState<TradingModel, TicketState>(ModelIds.trading, m => m.state.ticket);
  const acct = useModelState<SessionModel, SessionModel['state']>(ModelIds.session, m => m.state).activeAccount;

  const instr = ticket.instrument;
  const change = (patch: Partial<TicketState>) =>
    publish(ModelIds.trading, EventConst.ticketChanged, patch);

  if (!instr) {
    return (
      <div className="panel p-4 w-72 shrink-0">
        <div className="panel-title !p-0 mb-2">Order ticket</div>
        <div className="text-desk-dim text-sm">Select an instrument from the market,
          options chain or FX view to load the ticket.</div>
      </div>
    );
  }

  const est = ticket.orderType === 'LIMIT' && ticket.limitPrice != null
    ? ticket.limitPrice : (ticket.side === 'BUY' ? instr.ask : instr.bid);
  const notionalCost = est * ticket.qty * instr.contractSize;
  const marginNeeded = instr.type === 'FUTURE' && instr.initialMargin != null
    ? instr.initialMargin * ticket.qty : null;

  const submit = () => {
    if (!acct) return;
    void dataService.placeOrder({
      accountId: acct.accountId,
      instrumentId: instr.instrumentId,
      side: ticket.side,
      type: ticket.orderType,
      qty: ticket.qty,
      limitPrice: ticket.orderType === 'LIMIT' ? ticket.limitPrice : null,
      viewContext: ticket.viewContext,
    });
  };

  return (
    <div className="panel p-4 w-72 shrink-0 space-y-3">
      <div className="panel-title !p-0">Order ticket {ticket.viewContext && (
        <span className="text-desk-warn">({ticket.viewContext.toLowerCase()} view)</span>)}</div>
      <div>
        <div className="font-semibold">{instr.symbol}</div>
        <div className="text-xs text-desk-dim">{instr.name}</div>
        <div className="num text-sm mt-1">
          <span className="text-desk-down"><span className="text-[10px] text-desk-dim align-top">bid</span> {fmtNum(instr.bid)}</span>
          <span className="text-desk-dim mx-1">/</span>
          <span className="text-desk-up"><span className="text-[10px] text-desk-dim align-top">ask</span> {fmtNum(instr.ask)}</span>
          {instr.greeks && (
            <span className="text-xs text-desk-dim ml-2">Δ {instr.greeks.delta.toFixed(3)}</span>
          )}
        </div>
      </div>

      <div className="flex gap-1">
        <button className={`btn flex-1 ${ticket.side === 'BUY' ? 'btn-buy' : ''}`}
                onClick={() => change({ side: 'BUY' })}>BUY</button>
        <button className={`btn flex-1 ${ticket.side === 'SELL' ? 'btn-sell' : ''}`}
                onClick={() => change({ side: 'SELL' })}>SELL</button>
      </div>

      <div className="flex gap-2">
        <select className="input" aria-label="Order type" value={ticket.orderType}
                onChange={e => change({ orderType: e.target.value as any })}>
          <option value="MARKET">Market</option>
          <option value="LIMIT">Limit</option>
        </select>
        <input className="input" aria-label="Quantity" type="number" min={0} step="any" value={ticket.qty}
               onChange={e => change({ qty: Number(e.target.value) })} />
      </div>
      {ticket.orderType === 'LIMIT' && (
        <input className="input" aria-label="Limit price" type="number" step="any" placeholder="Limit price"
               value={ticket.limitPrice ?? ''}
               onChange={e => change({ limitPrice: e.target.value === '' ? null : Number(e.target.value) })} />
      )}

      <div className="text-xs space-y-1 num border border-desk-border rounded p-2">
        <div className="flex justify-between"><span className="text-desk-dim">Contract size</span>
          <span>{fmtNum(instr.contractSize, 0)}</span></div>
        {instr.type !== 'SWAP' && (
          <div className="flex justify-between"><span className="text-desk-dim">Est. {ticket.side === 'BUY' ? 'cost' : 'proceeds'}</span>
            <span>{fmtMoney(Math.abs(notionalCost))}</span></div>
        )}
        {marginNeeded != null && (
          <div className="flex justify-between text-desk-warn">
            <span>Initial margin required<InfoTip term="initialMargin" /></span><span>{fmtMoney(marginNeeded)}</span></div>
        )}
        {instr.type === 'SWAP' && (
          <>
            <div className="flex justify-between"><span className="text-desk-dim">Notional<InfoTip term="notional" /></span>
              <span>{fmtMoney(instr.notional, 0)}</span></div>
            <div className="flex justify-between"><span className="text-desk-dim">Fixed rate</span>
              <span>{((instr.fixedRate ?? 0) * 100).toFixed(2)}%</span></div>
            <div className="text-desk-dim">BUY = pay fixed / receive floating<InfoTip term="fixedFloating" /></div>
          </>
        )}
        {instr.greeks && (
          <div className="flex justify-between"><span className="text-desk-dim">Theta/day (per contract)<InfoTip term="theta" /></span>
            <span>{fmtMoney(instr.greeks.theta * instr.contractSize)}</span></div>
        )}
        {instr.expiryDate && (
          <div className="flex justify-between"><span className="text-desk-dim">Expiry</span>
            <span>{instr.expiryDate}</span></div>
        )}
      </div>

      <button className={`btn w-full ${ticket.side === 'BUY' ? 'btn-buy' : 'btn-sell'}`}
              disabled={ticket.submitting || ticket.qty <= 0}
              onClick={submit}>
        {ticket.submitting ? 'Sending…' : `${ticket.side} ${ticket.qty} ${instr.symbol}`}
      </button>

      {ticket.lastResult && (
        <div role="status" className={`text-xs ${ticket.lastResult.ok ? 'text-desk-up' : 'text-desk-down'}`}>
          {ticket.lastResult.ok ? '✓' : '✕'} {ticket.lastResult.message}
        </div>
      )}
    </div>
  );
}
