import { Router, observeEvent } from 'esp-js';
import { ImmutableModel } from 'esp-js-polimer';
import { EventConst, ModelIds } from '../core/events';
import { ChainData } from '../core/types';

export interface ChainState {
  underlyingId: number | null;
  selectedExpiry: string | null;
  chain: ChainData | null;
}

export interface ChainModel extends ImmutableModel {
  state: ChainState;
}

class ChainStateHandlers {
  @observeEvent(EventConst.chainUnderlyingSelected)
  onUnderlying(draft: ChainState, ev: { underlyingId: number }) {
    if (draft.underlyingId !== ev.underlyingId) {
      draft.underlyingId = ev.underlyingId;
      draft.chain = null;
      draft.selectedExpiry = null;
    }
  }

  @observeEvent(EventConst.chainLoaded)
  onChain(draft: ChainState, chain: ChainData) {
    if (draft.underlyingId !== chain.underlying.instrumentId) return; // stale response
    draft.chain = chain;
    const expiries = chain.expiries.map(e => e.expiry);
    if (!draft.selectedExpiry || !expiries.includes(draft.selectedExpiry)) {
      draft.selectedExpiry = expiries[0] ?? null;
    }
  }

  @observeEvent(EventConst.chainExpirySelected)
  onExpirySelected(draft: ChainState, ev: { expiry: string }) {
    draft.selectedExpiry = ev.expiry;
  }

  @observeEvent(EventConst.accountSelected)
  onAccountSelected(draft: ChainState) {
    draft.underlyingId = null;
    draft.selectedExpiry = null;
    draft.chain = null;
  }
}

export function registerChainModel(router: Router) {
  return router.modelBuilder!<ChainModel>()
    .withInitialModel({
      modelId: ModelIds.chain,
      state: { underlyingId: null, selectedExpiry: null, chain: null },
    })
    .withStateHandlers('state', new ChainStateHandlers())
    .registerWithRouter();
}
