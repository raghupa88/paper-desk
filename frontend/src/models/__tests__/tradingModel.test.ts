import { describe, expect, it, beforeEach } from 'vitest';
import { Router } from 'esp-js';
import 'esp-js-polimer';
import { registerTradingModel, TradingModel } from '../TradingModel';
import { EventConst, ModelIds } from '../../core/events';
import { OrderView, Quote } from '../../core/types';

const quote = (over: Partial<Quote> = {}): Quote => ({
  instrumentId: 1, symbol: 'ACME', name: 'Acme', type: 'EQUITY', underlyingId: null,
  strike: null, expiryDate: null, callPut: null, contractSize: 1, initialMargin: null,
  maintenanceMargin: null, notional: null, fixedRate: null, payFreqMonths: null,
  mid: 100, bid: 99.9, ask: 100.1, yearsToExpiry: null, ...over,
});

describe('TradingModel (polimer state driven by esp events)', () => {
  let router: Router;
  let getState: () => TradingModel['state'];

  beforeEach(() => {
    router = new Router();
    const model = registerTradingModel(router);
    getState = () => (model as any).getImmutableModel().state;
  });

  it('loads the ticket when an instrument is chosen', () => {
    router.publishEvent(ModelIds.trading, EventConst.instrumentChosen,
      { instrument: quote(), side: 'SELL', viewContext: 'TRADER' });
    const s = getState();
    expect(s.ticket.instrument?.symbol).toBe('ACME');
    expect(s.ticket.side).toBe('SELL');
    expect(s.ticket.viewContext).toBe('TRADER');
  });

  it('tracks submit lifecycle: pending then filled', () => {
    router.publishEvent(ModelIds.trading, EventConst.submitOrderRequested, {});
    expect(getState().ticket.submitting).toBe(true);

    const order: OrderView = {
      orderId: 7, accountId: 1, instrumentId: 1, symbol: 'ACME', instrumentType: 'EQUITY',
      side: 'BUY', orderType: 'MARKET', limitPrice: null, qty: 10, status: 'FILLED',
      viewContext: null, rejectReason: null, placedSimTime: null,
      fills: [{ price: 100.05, qty: 10, simTime: 't' }],
    };
    router.publishEvent(ModelIds.trading, EventConst.orderResultReceived, { order });
    const s = getState();
    expect(s.ticket.submitting).toBe(false);
    expect(s.ticket.lastResult?.ok).toBe(true);
    expect(s.ticket.lastResult?.message).toContain('Filled BUY 10 ACME');
  });

  it('surfaces rejections with the reason', () => {
    const order: OrderView = {
      orderId: 8, accountId: 1, instrumentId: 1, symbol: 'NIMBUS', instrumentType: 'EQUITY',
      side: 'BUY', orderType: 'MARKET', limitPrice: null, qty: 1e6, status: 'REJECTED',
      viewContext: null, rejectReason: 'Insufficient cash', placedSimTime: null, fills: [],
    };
    router.publishEvent(ModelIds.trading, EventConst.orderResultReceived, { order });
    expect(getState().ticket.lastResult).toEqual({ ok: false, message: 'Rejected: Insufficient cash' });
  });

  it('streams account events into the notifications feed, newest first', () => {
    router.publishEvent(ModelIds.trading, EventConst.accountEventReceived, { type: 'FILL', detail: 1 });
    router.publishEvent(ModelIds.trading, EventConst.accountEventReceived, { type: 'MARGIN_CALL', detail: '2026-07-11' });
    const s = getState();
    expect(s.notifications.map(n => n.type)).toEqual(['MARGIN_CALL', 'FILL']);
  });

  it('resets everything when the account switches', () => {
    router.publishEvent(ModelIds.trading, EventConst.instrumentChosen, { instrument: quote() });
    router.broadcastEvent(EventConst.accountSelected, { accountId: 2 });
    const s = getState();
    expect(s.ticket.instrument).toBeNull();
    expect(s.blotter).toEqual([]);
  });
});
