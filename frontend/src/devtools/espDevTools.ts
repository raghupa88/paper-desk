import { Router } from 'esp-js';
import { DEVTOOLS_CHANNEL, DevToolsMessage } from './protocol';

/**
 * esp-js DevTools — a reusable, decoupled event-flow tracer for esp-js apps,
 * in the spirit of Redux DevTools. No dependency on any application code:
 * give it a Router and it will
 *
 *  1. record every event published through the Router (publishEvent,
 *     broadcastEvent) with timestamp, modelId, event type and payload;
 *  2. capture the model state snapshot that each dispatch produced (via
 *     Router.getModelObservable, unwrapping esp-js-polimer immutable models);
 *  3. forward each event + state to the Redux DevTools browser extension
 *     (if installed) — that extension is itself a separate-process viewer;
 *  4. broadcast every event over a same-origin BroadcastChannel so the
 *     standalone DevTools window (frontend/src/devtools/panel) — a genuinely
 *     separate browsing context/process, not an in-page overlay — can record
 *     and render history independently of this app's main thread.
 *
 * This class is the thin **recorder** that must live in-page (there is no
 * way to intercept a function call from outside its own JS realm). Nothing
 * else does: no buffering of real history, no rendering, no polling — those
 * live in the other window so a slow or frozen devtools view can never touch
 * this app's frame budget, and a crash/reload of either side doesn't affect
 * the other beyond a brief reconnect.
 */

export interface EspTraceEvent {
  seq: number;
  time: number;
  modelId: string;
  eventType: string;
  payload: unknown;
  /** state snapshot taken after the dispatch loop delivered this event */
  state?: unknown;
}

export interface EspDevToolsOptions {
  /** small backfill buffer kept in-page so a devtools window opened late can catch up (default 200) */
  backfillSize?: number;
  /** event types to skip entirely — use for very chatty ticks (default []) */
  ignoredEvents?: string[];
  /** bridge into the Redux DevTools browser extension when present (default true) */
  reduxDevTools?: boolean;
  /** also expose the tracer at window.__ESP_DEVTOOLS__ for console poking (default true) */
  exposeOnWindow?: boolean;
}

interface ReduxDevToolsConnection {
  init(state: unknown): void;
  send(action: { type: string; payload?: unknown }, state: unknown): void;
}

export class EspDevTools {
  private seq = 0;
  private backfill: EspTraceEvent[] = [];
  private restoreFns: Array<() => void> = [];
  private modelSubscriptions: Array<{ dispose(): void }> = [];
  private reduxConnections = new Map<string, ReduxDevToolsConnection>();
  private lastEventByModel = new Map<string, EspTraceEvent>();
  private channel: BroadcastChannel | null = null;
  private readonly backfillSize: number;
  private readonly ignored: Set<string>;
  private readonly useRedux: boolean;
  private disposed = false;

  constructor(private router: Router, options: EspDevToolsOptions = {}) {
    this.backfillSize = options.backfillSize ?? 200;
    this.ignored = new Set(options.ignoredEvents ?? []);
    this.useRedux = options.reduxDevTools ?? true;
  }

  /** Recent events, for callers that want an in-page peek (e.g. tests) without opening the panel window. */
  get events(): ReadonlyArray<EspTraceEvent> {
    return this.backfill;
  }

  clear() {
    this.backfill = [];
    this.postMessage({ kind: 'clear' });
  }

  /** Instruments the router and opens the broadcast channel. Returns this for chaining. */
  install(): this {
    if (typeof BroadcastChannel !== 'undefined') {
      this.channel = new BroadcastChannel(DEVTOOLS_CHANNEL);
      this.channel.onmessage = (ev: MessageEvent<DevToolsMessage>) => {
        if (ev.data?.kind === 'hello') {
          this.postMessage({ kind: 'backfill', events: this.backfill });
        }
      };
    }

    const router = this.router as any;

    const originalPublish = router.publishEvent;
    router.publishEvent = (target: any, eventType: string, event: any) => {
      const modelId = typeof target === 'string' ? target : target?.modelId ?? String(target);
      this.record(modelId, eventType, event);
      return originalPublish.call(router, target, eventType, event);
    };
    this.restoreFns.push(() => { router.publishEvent = originalPublish; });

    const originalBroadcast = router.broadcastEvent;
    router.broadcastEvent = (eventType: string, event: any) => {
      this.record('*', eventType, event);
      return originalBroadcast.call(router, eventType, event);
    };
    this.restoreFns.push(() => { router.broadcastEvent = originalBroadcast; });

    const originalAddModel = router.addModel;
    router.addModel = (modelId: string, model: any, eventProcessors?: any) => {
      const result = originalAddModel.call(router, modelId, model, eventProcessors);
      this.observeModel(modelId);
      return result;
    };
    this.restoreFns.push(() => { router.addModel = originalAddModel; });

    return this;
  }

  dispose() {
    if (this.disposed) return;
    this.disposed = true;
    this.restoreFns.reverse().forEach(fn => fn());
    this.restoreFns = [];
    this.modelSubscriptions.forEach(s => s.dispose());
    this.modelSubscriptions = [];
    this.channel?.close();
    this.channel = null;
  }

  private record(modelId: string, eventType: string, payload: unknown) {
    if (this.disposed || this.ignored.has(eventType)) return;
    const traced: EspTraceEvent = {
      seq: this.seq++,
      time: Date.now(),
      modelId,
      eventType,
      payload: safeSnapshot(payload),
    };
    this.backfill.push(traced);
    if (this.backfill.length > this.backfillSize) this.backfill.splice(0, this.backfill.length - this.backfillSize);
    if (modelId !== '*') this.lastEventByModel.set(modelId, traced);
    this.postMessage({ kind: 'event', event: traced });
  }

  private observeModel(modelId: string) {
    try {
      const sub = (this.router as any).getModelObservable(modelId).subscribe((model: any) => {
        const state = safeSnapshot(unwrapModel(model));
        const last = this.lastEventByModel.get(modelId);
        if (last && last.state === undefined) {
          last.state = state;
          this.postMessage({ kind: 'event', event: last }); // re-send with the state now attached
        }
        this.sendToReduxDevTools(modelId, last?.eventType ?? '@@modelUpdate', last?.payload, state);
      });
      this.modelSubscriptions.push(sub);
    } catch {
      // model observation is best-effort; tracing of events still works
    }
  }

  private sendToReduxDevTools(modelId: string, eventType: string, payload: unknown, state: unknown) {
    if (!this.useRedux) return;
    const ext = (window as any).__REDUX_DEVTOOLS_EXTENSION__;
    if (!ext) return;
    let conn = this.reduxConnections.get(modelId);
    if (!conn) {
      conn = ext.connect({ name: `esp:${modelId}` }) as ReduxDevToolsConnection;
      conn.init(state);
      this.reduxConnections.set(modelId, conn);
      return;
    }
    conn.send({ type: eventType, payload }, state);
  }

  private postMessage(msg: DevToolsMessage) {
    try { this.channel?.postMessage(msg); } catch { /* channel closed mid-flight; drop it */ }
  }
}

/** esp-js-polimer models expose getImmutableModel(); plain models are used as-is. */
function unwrapModel(model: any): unknown {
  if (model && typeof model.getImmutableModel === 'function') {
    try { return model.getImmutableModel(); } catch { return model; }
  }
  return model;
}

/** JSON-roundtrip snapshot so the trace is immune to later mutation; falls back gracefully. */
function safeSnapshot(value: unknown): unknown {
  if (value === undefined || value === null) return value;
  try {
    return JSON.parse(JSON.stringify(value));
  } catch {
    return `<unserializable: ${Object.prototype.toString.call(value)}>`;
  }
}

/**
 * Entry point: instrument a router for tracing. Cheap — recording is a
 * BroadcastChannel postMessage, no rendering and no growing buffer of
 * consequence happen in this process.
 *
 *   const devTools = installEspDevTools(router, {ignoredEvents: ['pricesTick']});
 */
export function installEspDevTools(router: Router, options: EspDevToolsOptions = {}): EspDevTools {
  const tools = new EspDevTools(router, options).install();
  if (options.exposeOnWindow ?? true) {
    (window as any).__ESP_DEVTOOLS__ = tools;
  }
  return tools;
}
