import { EspTraceEvent } from './espDevTools';

/**
 * Wire protocol between the host app (the recorder living inside the app's
 * own process) and the standalone DevTools window (a separate browsing
 * context — its own renderer process in Chrome's site-per-process model,
 * its own event loop, its own memory). BroadcastChannel is same-origin-only
 * and needs no server, which keeps this working identically in `npm run dev`
 * and a production static deployment.
 */
export const DEVTOOLS_CHANNEL = 'paperdesk-esp-devtools';

export type DevToolsMessage =
  | { kind: 'event'; event: EspTraceEvent }
  | { kind: 'hello' }
  | { kind: 'backfill'; events: EspTraceEvent[] }
  | { kind: 'clear' };
