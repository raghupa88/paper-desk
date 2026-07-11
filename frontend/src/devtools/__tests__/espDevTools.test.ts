import { describe, expect, it } from 'vitest';
import { Router } from 'esp-js';
import 'esp-js-polimer';
import { EspDevTools } from '../espDevTools';
import { registerClockModel } from '../../models/ClockModel';
import { EventConst, ModelIds } from '../../core/events';

function install(router: Router) {
  return new EspDevTools(router, { reduxDevTools: false }).install();
}

describe('EspDevTools (decoupled esp event tracer)', () => {
  it('records publishEvent with modelId, type and payload snapshot', () => {
    const router = new Router();
    const tools = install(router);
    registerClockModel(router);

    router.publishEvent(ModelIds.clock, EventConst.clockControlRequested, { action: 'PAUSE' });

    const traced = tools.events.find(e => e.eventType === EventConst.clockControlRequested);
    expect(traced).toBeDefined();
    expect(traced!.modelId).toBe(ModelIds.clock);
    expect(traced!.payload).toEqual({ action: 'PAUSE' });
  });

  it('captures the post-dispatch model state snapshot', () => {
    const router = new Router();
    const tools = install(router);
    registerClockModel(router);

    router.publishEvent(ModelIds.clock, EventConst.clockTick,
      { sessionId: 1, simTime: 't', simDate: 'd', paused: false, acceleration: 300, floatingRate: 0.04 });

    const traced = tools.events.find(e => e.eventType === EventConst.clockTick)!;
    expect(traced.state).toBeDefined();
    expect((traced.state as any).state.clock.acceleration).toBe(300);
  });

  it('records broadcastEvent against the * pseudo-model', () => {
    const router = new Router();
    const tools = install(router);
    registerClockModel(router);

    router.broadcastEvent(EventConst.loggedOut, {});
    expect(tools.events.some(e => e.modelId === '*' && e.eventType === EventConst.loggedOut)).toBe(true);
  });

  it('notifies subscribers and honours the ring buffer limit', () => {
    const router = new Router();
    const tools = new EspDevTools(router, { reduxDevTools: false, maxEvents: 5 }).install();
    registerClockModel(router);

    let notified = 0;
    tools.subscribe(() => notified++);
    for (let i = 0; i < 10; i++) {
      router.publishEvent(ModelIds.clock, EventConst.clockControlRequested, { action: `A${i}` });
    }
    expect(notified).toBeGreaterThanOrEqual(10);
    expect(tools.events.length).toBeLessThanOrEqual(5);
  });

  it('dispose() restores the router untouched', () => {
    const router = new Router() as any;
    const originalPublish = router.publishEvent;
    const tools = install(router);
    expect(router.publishEvent).not.toBe(originalPublish);
    tools.dispose();
    expect(router.publishEvent).toBe(originalPublish);
  });
});
