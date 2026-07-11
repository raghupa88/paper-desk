import { afterEach, describe, expect, it, vi } from 'vitest';
import { Router } from 'esp-js';
import 'esp-js-polimer';
import { EspDevTools } from '../espDevTools';
import { DEVTOOLS_CHANNEL, DevToolsMessage } from '../protocol';
import { registerClockModel } from '../../models/ClockModel';
import { EventConst, ModelIds } from '../../core/events';

/** Mimics the standalone panel window: a second BroadcastChannel on the same name. */
function listenAsPanel(): { messages: DevToolsMessage[]; channel: BroadcastChannel } {
  const messages: DevToolsMessage[] = [];
  const channel = new BroadcastChannel(DEVTOOLS_CHANNEL);
  channel.onmessage = (ev: MessageEvent<DevToolsMessage>) => messages.push(ev.data);
  return { messages, channel };
}

function install(router: Router, opts: ConstructorParameters<typeof EspDevTools>[1] = {}) {
  return new EspDevTools(router, { reduxDevTools: false, ...opts }).install();
}

async function flush() {
  // BroadcastChannel delivery in Node is scheduled on the microtask/task queue
  await new Promise(r => setTimeout(r, 0));
}

describe('EspDevTools (recorder broadcasts to a separate process, not an in-page buffer)', () => {
  let openChannels: BroadcastChannel[] = [];
  afterEach(() => {
    openChannels.forEach(c => c.close());
    openChannels = [];
  });

  it('broadcasts publishEvent as a wire message a separate listener receives', async () => {
    const router = new Router();
    const tools = install(router);
    registerClockModel(router);
    const panel = listenAsPanel();
    openChannels.push(panel.channel);

    router.publishEvent(ModelIds.clock, EventConst.clockControlRequested, { action: 'PAUSE' });
    await flush();

    const evt = panel.messages.find(m => m.kind === 'event' && m.event.eventType === EventConst.clockControlRequested);
    expect(evt).toBeDefined();
    if (evt?.kind === 'event') {
      expect(evt.event.modelId).toBe(ModelIds.clock);
      expect(evt.event.payload).toEqual({ action: 'PAUSE' });
    }
    tools.dispose();
  });

  it('re-sends the event once its post-dispatch state snapshot resolves', async () => {
    const router = new Router();
    const tools = install(router);
    registerClockModel(router);
    const panel = listenAsPanel();
    openChannels.push(panel.channel);

    router.publishEvent(ModelIds.clock, EventConst.clockTick,
      { sessionId: 1, simTime: 't', simDate: 'd', paused: false, acceleration: 300, floatingRate: 0.04 });
    await flush();

    const withState = panel.messages.find(m => m.kind === 'event' && m.event.state !== undefined);
    expect(withState).toBeDefined();
    if (withState?.kind === 'event') {
      expect((withState.event.state as any).state.clock.acceleration).toBe(300);
    }
    tools.dispose();
  });

  it('broadcastEvent is recorded against the * pseudo-model', async () => {
    const router = new Router();
    const tools = install(router);
    registerClockModel(router);
    const panel = listenAsPanel();
    openChannels.push(panel.channel);

    router.broadcastEvent(EventConst.loggedOut, {});
    await flush();

    expect(panel.messages.some(m => m.kind === 'event' && m.event.modelId === '*'
        && m.event.eventType === EventConst.loggedOut)).toBe(true);
    tools.dispose();
  });

  it('answers a hello with a backfill of recent events, for a panel opened after activity started', async () => {
    const router = new Router();
    const tools = install(router);
    registerClockModel(router);

    router.publishEvent(ModelIds.clock, EventConst.clockControlRequested, { action: 'PAUSE' });
    router.publishEvent(ModelIds.clock, EventConst.clockControlRequested, { action: 'RESUME' });
    await flush();

    const panel = listenAsPanel();
    openChannels.push(panel.channel);
    panel.channel.postMessage({ kind: 'hello' } satisfies DevToolsMessage);
    await flush();

    const backfill = panel.messages.find(m => m.kind === 'backfill');
    expect(backfill).toBeDefined();
    if (backfill?.kind === 'backfill') {
      expect(backfill.events.length).toBeGreaterThanOrEqual(2);
    }
    tools.dispose();
  });

  it('trims the backfill buffer to backfillSize', async () => {
    const router = new Router();
    const tools = install(router, { backfillSize: 3 });
    registerClockModel(router);
    for (let i = 0; i < 10; i++) {
      router.publishEvent(ModelIds.clock, EventConst.clockControlRequested, { action: `A${i}` });
    }
    expect(tools.events.length).toBeLessThanOrEqual(3);
    tools.dispose();
  });

  it('dispose() restores the router and stops broadcasting', async () => {
    const router = new Router() as any;
    const originalPublish = router.publishEvent;
    const tools = install(router);
    expect(router.publishEvent).not.toBe(originalPublish);
    tools.dispose();
    expect(router.publishEvent).toBe(originalPublish);

    registerClockModel(router);
    const panel = listenAsPanel();
    openChannels.push(panel.channel);
    router.publishEvent(ModelIds.clock, EventConst.clockControlRequested, { action: 'AFTER_DISPOSE' });
    await flush();
    expect(panel.messages.some(m => m.kind === 'event' && (m.event.payload as any)?.action === 'AFTER_DISPOSE')).toBe(false);
  });

  it('respects ignoredEvents', async () => {
    const router = new Router();
    const tools = install(router, { ignoredEvents: [EventConst.clockControlRequested] });
    registerClockModel(router);
    const panel = listenAsPanel();
    openChannels.push(panel.channel);

    router.publishEvent(ModelIds.clock, EventConst.clockControlRequested, { action: 'PAUSE' });
    await flush();
    expect(panel.messages.some(m => m.kind === 'event')).toBe(false);
    tools.dispose();
  });
});
