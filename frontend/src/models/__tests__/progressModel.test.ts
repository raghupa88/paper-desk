import { describe, expect, it } from 'vitest';
import { Router } from 'esp-js';
import 'esp-js-polimer';
import { registerProgressModel, ProgressModel } from '../ProgressModel';
import { EventConst, ModelIds } from '../../core/events';
import { MissionView, ProgressView } from '../../core/types';

const progress = (over: Partial<ProgressView> = {}): ProgressView => ({
  accountId: 1, xp: 105, level: 2, levelName: 'Apprentice', levelFloorXp: 100,
  nextLevelXp: 250, earnedCount: 2,
  badges: [
    { code: 'FIRST_TRADE', title: 'First Trade', description: '', xp: 50, earned: true, earnedSimDate: '2026-07-11' },
    { code: 'SWAP_STARTER', title: 'Swap Starter', description: '', xp: 100, earned: false, earnedSimDate: null },
  ],
  ...over,
});

const mission = (over: Partial<MissionView> = {}): MissionView => ({
  code: 'FIRST_STEPS', title: 'First Steps', description: '', xp: 50, completed: false,
  steps: [{ description: 'Place a trade that fills', done: true },
          { description: 'Hold at least one open position', done: false }],
  ...over,
});

describe('ProgressModel', () => {
  it('stores loaded progress', () => {
    const router = new Router();
    const model = registerProgressModel(router);
    router.publishEvent(ModelIds.progress, EventConst.progressLoaded, progress());
    const s = (model as any).getImmutableModel().state;
    expect(s.progress.levelName).toBe('Apprentice');
    expect(s.progress.badges).toHaveLength(2);
  });

  it('tracks live unlocks from achievement and mission events, resets on account switch', () => {
    const router = new Router();
    const model = registerProgressModel(router);
    router.broadcastEvent(EventConst.accountEventReceived, { type: 'ACHIEVEMENT', code: 'FIRST_TRADE' });
    router.broadcastEvent(EventConst.accountEventReceived, { type: 'MISSION_COMPLETE', code: 'FIRST_STEPS' });
    router.broadcastEvent(EventConst.accountEventReceived, { type: 'FILL', detail: 1 });
    let s = (model as any).getImmutableModel().state;
    expect(s.recentUnlocks).toEqual(['FIRST_TRADE', 'FIRST_STEPS']);

    router.broadcastEvent(EventConst.accountSelected, { accountId: 9 });
    s = (model as any).getImmutableModel().state;
    expect(s.recentUnlocks).toEqual([]);
    expect(s.progress).toBeNull();
    expect(s.missions).toEqual([]);
  });

  it('stores loaded missions', () => {
    const router = new Router();
    const model = registerProgressModel(router);
    router.publishEvent(ModelIds.progress, EventConst.missionsLoaded, [mission(), mission({ code: 'COVERED_CALL', completed: true })]);
    const s = (model as any).getImmutableModel().state;
    expect(s.missions).toHaveLength(2);
    expect(s.missions[1].completed).toBe(true);
  });
});
