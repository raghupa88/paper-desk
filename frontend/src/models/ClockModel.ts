import { Router, observeEvent } from 'esp-js';
import { ImmutableModel } from 'esp-js-polimer';
import { EventConst, ModelIds } from '../core/events';
import { ClockState } from '../core/types';

export interface ClockSlice {
  clock: ClockState | null;
  pendingAction: string | null;
}

export interface ClockModel extends ImmutableModel {
  state: ClockSlice;
}

class ClockStateHandlers {
  @observeEvent(EventConst.clockTick)
  onTick(draft: ClockSlice, clock: ClockState) {
    draft.clock = clock;
    draft.pendingAction = null;
  }

  @observeEvent(EventConst.clockControlRequested)
  onControl(draft: ClockSlice, ev: { action: string }) {
    draft.pendingAction = ev.action;
  }
}

export function registerClockModel(router: Router) {
  return router.modelBuilder!<ClockModel>()
    .withInitialModel({ modelId: ModelIds.clock, state: { clock: null, pendingAction: null } })
    .withStateHandlers('state', new ClockStateHandlers())
    .registerWithRouter();
}
