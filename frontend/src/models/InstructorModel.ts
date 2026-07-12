import { Router, observeEvent } from 'esp-js';
import { ImmutableModel } from 'esp-js-polimer';
import { EventConst, ModelIds } from '../core/events';
import { Challenge, Cohort, CohortGrade, LeaderboardRow, StudentDetail, TradeComment } from '../core/types';

export interface InstructorState {
  cohorts: Cohort[];
  selectedCohortId: number | null;
  leaderboard: LeaderboardRow[];
  challenges: Challenge[];
  busy: boolean;
  /** The student currently open in the grading/review panel, if any. */
  reviewing: StudentDetail | null;
  reviewGrade: CohortGrade | null;
  reviewComments: Record<number, TradeComment[]>;
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

  @observeEvent(EventConst.challengesLoaded)
  onChallenges(draft: InstructorState, ev: { cohortId: number; challenges: Challenge[] }) {
    if (ev.cohortId === draft.selectedCohortId) draft.challenges = ev.challenges;
  }

  @observeEvent(EventConst.studentDetailLoaded)
  onStudentDetail(draft: InstructorState, detail: StudentDetail) {
    draft.reviewing = detail;
    draft.reviewComments = {};
  }

  @observeEvent(EventConst.studentGradeLoaded)
  onStudentGrade(draft: InstructorState, grade: CohortGrade) {
    draft.reviewGrade = grade;
  }

  @observeEvent(EventConst.studentReviewClosed)
  onStudentReviewClosed(draft: InstructorState) {
    draft.reviewing = null;
    draft.reviewGrade = null;
    draft.reviewComments = {};
  }

  @observeEvent(EventConst.reviewCommentsLoaded)
  onReviewComments(draft: InstructorState, ev: { orderId: number; comments: TradeComment[] }) {
    draft.reviewComments[ev.orderId] = ev.comments;
  }
}

export function registerInstructorModel(router: Router) {
  return router.modelBuilder!<InstructorModel>()
    .withInitialModel({
      modelId: ModelIds.instructor,
      state: {
        cohorts: [], selectedCohortId: null, leaderboard: [], challenges: [], busy: false,
        reviewing: null, reviewGrade: null, reviewComments: {},
      },
    })
    .withStateHandlers('state', new InstructorStateHandlers())
    .registerWithRouter();
}
