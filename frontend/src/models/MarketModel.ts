import { Router, observeEvent } from 'esp-js';
import { ImmutableModel } from 'esp-js-polimer';
import { EventConst, ModelIds } from '../core/events';
import { Bar, Quote } from '../core/types';

export interface MarketState {
  quotes: Quote[];
  /** live spots straight off the price WebSocket, keyed by symbol */
  spots: Record<string, number>;
  selectedSymbol: string | null;
  bars: Bar[];
}

export interface MarketModel extends ImmutableModel {
  state: MarketState;
}

class MarketStateHandlers {
  @observeEvent(EventConst.quotesLoaded)
  onQuotes(draft: MarketState, quotes: Quote[]) {
    draft.quotes = quotes;
    if (!draft.selectedSymbol && quotes.length > 0) {
      draft.selectedSymbol = quotes[0].symbol;
    }
  }

  @observeEvent(EventConst.pricesTick)
  onPrices(draft: MarketState, ev: { prices: Record<string, number> }) {
    Object.assign(draft.spots, ev.prices);
    // keep underlying quote mids fresh between REST refreshes
    for (const q of draft.quotes) {
      if ((q.type === 'EQUITY' || q.type === 'FX_PAIR') && ev.prices[q.symbol] !== undefined) {
        q.mid = ev.prices[q.symbol];
      }
    }
  }

  @observeEvent(EventConst.symbolSelected)
  onSymbolSelected(draft: MarketState, ev: { symbol: string }) {
    draft.selectedSymbol = ev.symbol;
    draft.bars = [];
  }

  @observeEvent(EventConst.barsLoaded)
  onBars(draft: MarketState, ev: { symbol: string; bars: Bar[] }) {
    if (ev.symbol === draft.selectedSymbol) draft.bars = ev.bars;
  }

  @observeEvent(EventConst.accountSelected)
  onAccountSelected(draft: MarketState) {
    draft.quotes = [];
    draft.spots = {};
    draft.selectedSymbol = null;
    draft.bars = [];
  }
}

export function registerMarketModel(router: Router) {
  return router.modelBuilder!<MarketModel>()
    .withInitialModel({
      modelId: ModelIds.market,
      state: { quotes: [], spots: {}, selectedSymbol: null, bars: [] },
    })
    .withStateHandlers('state', new MarketStateHandlers())
    .registerWithRouter();
}
