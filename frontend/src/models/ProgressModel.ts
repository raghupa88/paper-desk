import { Router, observeEvent } from 'esp-js';
import { ImmutableModel } from 'esp-js-polimer';
import { EventConst, ModelIds } from '../core/events';
import { CohortGrade, MissionView, ProgressView } from '../core/types';

export interface ProgressState {
  progress: ProgressView | null;
  missions: MissionView[];
  /** codes unlocked live this session — the Progress tab highlights them */
  recentUnlocks: string[];
  /** Instructor feedback on the active account, if it's in a cohort and has been graded. */
  myGrade: CohortGrade | null;
}

export interface ProgressModel extends ImmutableModel {
  state: ProgressState;
}

class ProgressStateHandlers {
  @observeEvent(EventConst.progressLoaded)
  onProgress(draft: ProgressState, progress: ProgressView) {
    draft.progress = progress;
  }

  @observeEvent(EventConst.missionsLoaded)
  onMissions(draft: ProgressState, missions: MissionView[]) {
    draft.missions = missions;
  }

  @observeEvent(EventConst.accountEventReceived)
  onAccountEvent(draft: ProgressState, ev: { type: string; code?: string }) {
    if ((ev.type === 'ACHIEVEMENT' || ev.type === 'MISSION_COMPLETE') && ev.code) {
      if (!draft.recentUnlocks.includes(ev.code)) draft.recentUnlocks.push(ev.code);
    }
  }

  @observeEvent(EventConst.myGradeLoaded)
  onMyGrade(draft: ProgressState, grade: CohortGrade) {
    draft.myGrade = grade;
  }

  @observeEvent(EventConst.accountSelected)
  onAccountSelected(draft: ProgressState) {
    draft.progress = null;
    draft.missions = [];
    draft.recentUnlocks = [];
    draft.myGrade = null;
  }
}

export function registerProgressModel(router: Router) {
  return router.modelBuilder!<ProgressModel>()
    .withInitialModel({
      modelId: ModelIds.progress,
      state: { progress: null, missions: [], recentUnlocks: [], myGrade: null },
    })
    .withStateHandlers('state', new ProgressStateHandlers())
    .registerWithRouter();
}
