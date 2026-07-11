import { Router } from 'esp-js';
import { EspDevTools, EspDevToolsOptions } from './espDevTools';

const STORAGE_KEY = 'paperdesk.devtoolsEnabled';
const PANEL_WINDOW_NAME = 'paperdesk-esp-devtools';

/**
 * Activation policy for the recorder. In dev builds it's always on (matches
 * the previous always-available dev experience). In production it stays off
 * — and its module code isn't even downloaded — until explicitly turned on
 * via `?devtools=1`, a persisted opt-in from a previous visit, or the
 * Ctrl+Shift+E shortcut. `?devtools=0` clears the opt-in, so a debugging
 * session can be handed off with a plain link afterwards.
 */
export function isDevToolsEnabled(): boolean {
  if (typeof window === 'undefined') return false;
  try {
    const params = new URLSearchParams(window.location.search);
    if (params.get('devtools') === '1') { localStorage.setItem(STORAGE_KEY, '1'); return true; }
    if (params.get('devtools') === '0') { localStorage.removeItem(STORAGE_KEY); return false; }
    return localStorage.getItem(STORAGE_KEY) === '1';
  } catch {
    return false; // storage unavailable (e.g. private mode) — falls back to off
  }
}

function persistEnabled() {
  try { localStorage.setItem(STORAGE_KEY, '1'); } catch { /* best effort */ }
}

/** Opens the standalone DevTools window. Must be called from a user gesture (click/keydown) to avoid popup blocking. */
export function openDevToolsWindow(): void {
  window.open('/devtools.html', PANEL_WINDOW_NAME, 'width=960,height=600');
}

let installPromise: Promise<EspDevTools> | null = null;

/** Dynamically imports the recorder — a separate webpack chunk, so production bundles that never activate it never fetch it. */
function installRecorder(router: Router, options?: EspDevToolsOptions): Promise<EspDevTools> {
  if (!installPromise) {
    installPromise = import(/* webpackChunkName: "devtools-recorder" */ './espDevTools')
      .then(mod => mod.installEspDevTools(router, options));
  }
  return installPromise;
}

/**
 * Wires up devtools activation. This function and its direct dependencies
 * are the only devtools code eagerly bundled into the main app — a few
 * hundred bytes — everything else (the recorder, the standalone panel) is
 * fetched only once actually activated.
 */
export function installDevToolsActivation(router: Router, options?: EspDevToolsOptions): void {
  const isDev = process.env.NODE_ENV === 'development';
  if (isDev || isDevToolsEnabled()) {
    void installRecorder(router, options);
  }

  window.addEventListener('keydown', e => {
    if (!(e.ctrlKey && e.shiftKey && e.key.toLowerCase() === 'e')) return;
    e.preventDefault();
    persistEnabled();
    openDevToolsWindow(); // synchronous within the gesture — install() below is async and must not block it
    void installRecorder(router, options);
  });
}

/** Used by the in-app launcher button: activates (if needed) and opens the window, from a click handler. */
export function activateAndOpenDevTools(router: Router, options?: EspDevToolsOptions): void {
  persistEnabled();
  openDevToolsWindow();
  void installRecorder(router, options);
}
