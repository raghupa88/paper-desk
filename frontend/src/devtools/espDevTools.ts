import { Router } from 'esp-js';

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
 *     (if installed), one connection per esp model, so the familiar
 *     Chrome/Firefox DevTools UI can inspect esp event flow;
 *  4. expose a subscribe() feed that in-app UIs (see EspDevToolsPanel) can
 *     render as a live event log.
 *
 * Intended for development mode only — call installEspDevTools() behind a
 * NODE_ENV check so production bundles carry zero overhead.
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
  /** ring buffer size for the in-memory trace (default 500) */
  maxEvents?: number;
  /** event types to skip entirely — use for very chatty ticks (default []) */
  ignoredEvents?: string[];
  /** bridge into the Redux DevTools browser extension when present (default true) */
  reduxDevTools?: boolean;
  /** also expose the tracer at window.__ESP_DEVTOOLS__ for console poking (default true) */
  exposeOnWindow?: boolean;
}

type Listener = (event: EspTraceEvent, all: ReadonlyArray<EspTraceEvent>) => void;

interface ReduxDevToolsConnection {
  init(state: unknown): void;
  send(action: { type: string; payload?: unknown }, state: unknown): void;
}

export class EspDevTools {
  private seq = 0;
  private buffer: EspTraceEvent[] = [];
  private listeners = new Set<Listener>();
  private restoreFns: Array<() => void> = [];
  private modelSubscriptions: Array<{ dispose(): void }> = [];
  private reduxConnections = new Map<string, ReduxDevToolsConnection>();
  private lastEventByModel = new Map<string, EspTraceEvent>();
  private readonly maxEvents: number;
  private readonly ignored: Set<string>;
  private readonly useRedux: boolean;
  private disposed = false;

  constructor(private router: Router, options: EspDevToolsOptions = {}) {
    this.maxEvents = options.maxEvents ?? 500;
    this.ignored = new Set(options.ignoredEvents ?? []);
    this.useRedux = options.reduxDevTools ?? true;
  }

  get events(): ReadonlyArray<EspTraceEvent> {
    return this.buffer;
  }

  subscribe(listener: Listener): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  clear() {
    this.buffer = [];
    this.notify({ seq: -1, time: Date.now(), modelId: '*', eventType: '@@cleared', payload: null });
  }

  /** Instruments the router. Returns this for chaining. */
  install(): this {
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
    this.listeners.clear();
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
    this.buffer.push(traced);
    if (this.buffer.length > this.maxEvents) this.buffer.splice(0, this.buffer.length - this.maxEvents);
    if (modelId !== '*') this.lastEventByModel.set(modelId, traced);
    this.notify(traced);
  }

  private observeModel(modelId: string) {
    try {
      const sub = (this.router as any).getModelObservable(modelId).subscribe((model: any) => {
        const state = safeSnapshot(unwrapModel(model));
        const last = this.lastEventByModel.get(modelId);
        if (last && last.state === undefined) {
          last.state = state;
          this.notify(last);
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

  private notify(event: EspTraceEvent) {
    this.listeners.forEach(l => {
      try { l(event, this.buffer); } catch { /* a bad listener must not break dispatch */ }
    });
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
 * Entry point: instrument a router for development tracing.
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
