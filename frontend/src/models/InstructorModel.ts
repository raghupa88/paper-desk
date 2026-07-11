import { Router, observeEvent } from 'esp-js';
import { ImmutableModel } from 'esp-js-polimer';
import { EventConst, ModelIds } from '../core/events';
import { Cohort, LeaderboardRow } from '../core/types';

export interface InstructorState {
  cohorts: Cohort[];
  selectedCohortId: number | null;
  leaderboard: LeaderboardRow[];
  busy: boolean;
}

export interface InstructorModel extends ImmutableModel {
  state: InstructorState;
}

class InstructorStateHandlers {
  @observeEvent(EventConst.cohortsLoaded)
  onCohorts(draft: InstructorState, cohorts: Cohort[]) {
    draft.cohorts = cohorts;
    draft.busy = false;
    if (draft.selectedCohortId == null && cohorts.length > 0) {
      draft.selectedCohortId = cohorts[0].cohortId;
    }
  }

  @observeEvent(EventConst.createCohortRequested)
  @observeEvent(EventConst.joinCohortRequested)
  onBusy(draft: InstructorState) {
    draft.busy = true;
  }

  @observeEvent(EventConst.cohortJoined)
  onJoined(draft: InstructorState, cohort: Cohort) {
    draft.selectedCohortId = cohort.cohortId;
    draft.busy = false;
  }

  @observeEvent(EventConst.leaderboardRequested)
  onLeaderboardRequested(draft: InstructorState, ev: { cohortId: number }) {
    draft.selectedCohortId = ev.cohortId;
  }

  @observeEvent(EventConst.leaderboardLoaded)
  onLeaderboard(draft: InstructorState, ev: { cohortId: number; rows: LeaderboardRow[] }) {
    if (ev.cohortId === draft.selectedCohortId) draft.leaderboard = ev.rows;
  }
}

export function registerInstructorModel(router: Router) {
  return router.modelBuilder!<InstructorModel>()
    .withInitialModel({
      modelId: ModelIds.instructor,
      state: { cohorts: [], selectedCohortId: null, leaderboard: [], busy: false },
    })
    .withStateHandlers('state', new InstructorStateHandlers())
    .registerWithRouter();
}
