import { describe, expect, it, beforeEach } from 'vitest';
import { Router } from 'esp-js';
import 'esp-js-polimer';
import { registerSessionModel, SessionModel } from '../SessionModel';
import { EventConst, ModelIds } from '../../core/events';
import { StreakInfo } from '../../core/types';

describe('SessionModel — daily streak', () => {
  let router: Router;
  let getState: () => SessionModel['state'];

  beforeEach(() => {
    router = new Router();
    const model = registerSessionModel(router, null);
    getState = () => (model as any).getImmutableModel().state;
  });

  it('starts with no streak known', () => {
    expect(getState().streak).toBeNull();
  });

  it('stores the streak once loaded', () => {
    const streak: StreakInfo = { currentStreak: 3, longestStreak: 5, milestoneDays: 3 };
    router.broadcastEvent(EventConst.streakLoaded, streak);
    expect(getState().streak).toEqual(streak);
  });

  it('clears the streak on logout, alongside the rest of the session', () => {
    router.broadcastEvent(EventConst.streakLoaded, { currentStreak: 3, longestStreak: 5, milestoneDays: null });
    router.broadcastEvent(EventConst.loggedOut, {});
    const s = getState();
    expect(s.streak).toBeNull();
    expect(s.user).toBeNull();
  });
});
