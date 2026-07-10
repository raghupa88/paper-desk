import { Router, observeEvent } from 'esp-js';
import { ImmutableModel } from 'esp-js-polimer';
import { EventConst, ModelIds } from '../core/events';
import { PairLadder, Quote, RfqQuote } from '../core/types';

export interface RfqForm {
  underlyingId: number | null;
  strike: number | null;
  expiryDate: string;
  callPut: 'CALL' | 'PUT';
  side: 'BUY' | 'SELL';
  notional: number;
  salesMarginBps: number;
}

export interface FxState {
  pairs: Quote[];
  rfq: RfqForm;
  quoting: boolean;
  quote: RfqQuote | null;
  quoteError: string | null;
  lastDeal: RfqQuote | null;
  ladder: PairLadder[];
}

export interface FxModel extends ImmutableModel {
  state: FxState;
}

class FxStateHandlers {
  @observeEvent(EventConst.quotesLoaded)
  onQuotes(draft: FxState, quotes: Quote[]) {
    draft.pairs = quotes.filter(q => q.type === 'FX_PAIR');
    if (draft.rfq.underlyingId == null && draft.pairs.length > 0) {
      draft.rfq.underlyingId = draft.pairs[0].instrumentId;
      draft.rfq.strike = round4(draft.pairs[0].mid);
    }
  }

  @observeEvent(EventConst.rfqChanged)
  onRfqChanged(draft: FxState, ev: Partial<RfqForm>) {
    Object.assign(draft.rfq, ev);
    draft.quote = null;
    draft.quoteError = null;
  }

  @observeEvent(EventConst.rfqQuoteRequested)
  onQuoteRequested(draft: FxState) {
    draft.quoting = true;
    draft.quoteError = null;
  }

  @observeEvent(EventConst.rfqQuoted)
  onQuoted(draft: FxState, ev: { quote?: RfqQuote; error?: string }) {
    draft.quoting = false;
    draft.quote = ev.quote ?? null;
    draft.quoteError = ev.error ?? null;
  }

  @observeEvent(EventConst.rfqExecuted)
  onExecuted(draft: FxState, ev: { deal?: RfqQuote; error?: string }) {
    if (ev.deal) {
      draft.lastDeal = ev.deal;
      draft.quote = null;
    } else {
      draft.quoteError = ev.error ?? 'Execution failed';
    }
  }

  @observeEvent(EventConst.ladderLoaded)
  onLadder(draft: FxState, ladder: PairLadder[]) {
    draft.ladder = ladder;
  }

  @observeEvent(EventConst.accountSelected)
  onAccountSelected(draft: FxState) {
    draft.pairs = [];
    draft.quote = null;
    draft.lastDeal = null;
    draft.ladder = [];
    draft.rfq.underlyingId = null;
  }
}

function round4(x: number) { return Math.round(x * 10000) / 10000; }

export function registerFxModel(router: Router) {
  const in30Days = new Date(Date.now() + 30 * 86400_000).toISOString().slice(0, 10);
  return router.modelBuilder!<FxModel>()
    .withInitialModel({
      modelId: ModelIds.fx,
      state: {
        pairs: [],
        rfq: {
          underlyingId: null, strike: null, expiryDate: in30Days,
          callPut: 'CALL', side: 'BUY', notional: 100000, salesMarginBps: 20,
        },
        quoting: false, quote: null, quoteError: null, lastDeal: null, ladder: [],
      },
    })
    .withStateHandlers('state', new FxStateHandlers())
    .registerWithRouter();
}
