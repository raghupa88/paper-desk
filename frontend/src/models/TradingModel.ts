import { Router, observeEvent } from 'esp-js';
import { ImmutableModel } from 'esp-js-polimer';
import { EventConst, ModelIds } from '../core/events';
import { EquityPoint, OrderView, PortfolioView, Quote, ScorecardView, SettlementView, TradeComment } from '../core/types';

export interface TicketState {
  instrument: Quote | null;
  side: 'BUY' | 'SELL';
  orderType: 'MARKET' | 'LIMIT';
  qty: number;
  limitPrice: number | null;
  viewContext: 'SALES' | 'TRADER' | null;
  submitting: boolean;
  lastResult: { ok: boolean; message: string } | null;
}

export interface Notification {
  at: number;
  type: string;
  detail: string;
}

export interface TradingState {
  ticket: TicketState;
  portfolio: PortfolioView | null;
  equityHistory: EquityPoint[];
  blotter: OrderView[];
  settlements: SettlementView[];
  scorecard: ScorecardView | null;
  notifications: Notification[];
  /** Instructor comments on my own orders, keyed by orderId, fetched on demand. */
  orderComments: Record<number, TradeComment[]>;
}

export interface TradingModel extends ImmutableModel {
  state: TradingState;
}

const emptyTicket = (): TicketState => ({
  instrument: null, side: 'BUY', orderType: 'MARKET', qty: 1,
  limitPrice: null, viewContext: null, submitting: false, lastResult: null,
});

class TradingStateHandlers {
  @observeEvent(EventConst.instrumentChosen)
  onInstrumentChosen(draft: TradingState, ev: { instrument: Quote; side?: 'BUY' | 'SELL'; viewContext?: 'SALES' | 'TRADER' | null }) {
    draft.ticket.instrument = ev.instrument;
    if (ev.side) draft.ticket.side = ev.side;
    draft.ticket.viewContext = ev.viewContext ?? null;
    draft.ticket.limitPrice = null;
    draft.ticket.orderType = 'MARKET';
    draft.ticket.lastResult = null;
  }

  @observeEvent(EventConst.ticketChanged)
  onTicketChanged(draft: TradingState, ev: Partial<TicketState>) {
    Object.assign(draft.ticket, ev);
  }

  @observeEvent(EventConst.submitOrderRequested)
  onSubmit(draft: TradingState) {
    draft.ticket.submitting = true;
    draft.ticket.lastResult = null;
  }

  @observeEvent(EventConst.orderResultReceived)
  onOrderResult(draft: TradingState, ev: { order?: OrderView; error?: string }) {
    draft.ticket.submitting = false;
    if (ev.error) {
      draft.ticket.lastResult = { ok: false, message: ev.error };
    } else if (ev.order) {
      const o = ev.order;
      const filled = o.status === 'FILLED';
      const rejected = o.status === 'REJECTED';
      draft.ticket.lastResult = {
        ok: !rejected,
        message: rejected
          ? `Rejected: ${o.rejectReason ?? 'unknown reason'}`
          : filled
            ? `Filled ${o.side} ${o.qty} ${o.symbol} @ ${o.fills[0]?.price?.toFixed(4) ?? '?'}`
            : `Order resting (${o.orderType} ${o.limitPrice})`,
      };
    }
  }

  @observeEvent(EventConst.portfolioLoaded)
  onPortfolio(draft: TradingState, ev: { portfolio: PortfolioView; history: EquityPoint[] }) {
    draft.portfolio = ev.portfolio;
    draft.equityHistory = ev.history;
  }

  @observeEvent(EventConst.blotterLoaded)
  onBlotter(draft: TradingState, orders: OrderView[]) {
    draft.blotter = orders;
  }

  @observeEvent(EventConst.settlementsLoaded)
  onSettlements(draft: TradingState, settlements: SettlementView[]) {
    draft.settlements = settlements;
  }

  @observeEvent(EventConst.scorecardLoaded)
  onScorecard(draft: TradingState, scorecard: ScorecardView) {
    draft.scorecard = scorecard;
  }

  @observeEvent(EventConst.blotterCommentsLoaded)
  onBlotterComments(draft: TradingState, ev: { orderId: number; comments: TradeComment[] }) {
    draft.orderComments[ev.orderId] = ev.comments;
  }

  @observeEvent(EventConst.accountEventReceived)
  onAccountEvent(draft: TradingState, ev: { type: string; detail: unknown }) {
    draft.notifications.unshift({ at: Date.now(), type: ev.type, detail: String(ev.detail) });
    if (draft.notifications.length > 50) draft.notifications.length = 50;
  }

  @observeEvent(EventConst.accountSelected)
  onAccountSelected(draft: TradingState) {
    draft.ticket = emptyTicket();
    draft.portfolio = null;
    draft.equityHistory = [];
    draft.blotter = [];
    draft.settlements = [];
    draft.scorecard = null;
    draft.notifications = [];
    draft.orderComments = {};
  }
}

export function registerTradingModel(router: Router) {
  return router.modelBuilder!<TradingModel>()
    .withInitialModel({
      modelId: ModelIds.trading,
      state: {
        ticket: emptyTicket(), portfolio: null, equityHistory: [],
        blotter: [], settlements: [], scorecard: null, notifications: [], orderComments: {},
      },
    })
    .withStateHandlers('state', new TradingStateHandlers())
    .registerWithRouter();
}
