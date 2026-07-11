import React from 'react';
import { useRouter } from 'esp-js-react';
import { activateAndOpenDevTools, isDevToolsEnabled } from './index';

/**
 * Tiny always-mounted launcher (a single button, no state, negligible cost
 * even in a production bundle that never activates devtools). Click is a
 * user gesture, so the popup it opens isn't blocked. Same effect as
 * Ctrl+Shift+E.
 */
export function DevToolsLauncher() {
  const router = useRouter();
  return (
    <button
      onClick={() => activateAndOpenDevTools(router)}
      title="Open esp DevTools in its own window (Ctrl+Shift+E) — safe to use in production for live debugging"
      style={{
        position: 'fixed', bottom: 10, right: 10, zIndex: 9999, background: '#1f6feb',
        color: '#fff', border: 'none', borderRadius: 16, padding: '4px 10px',
        fontSize: 11, fontFamily: 'monospace', cursor: 'pointer', opacity: 0.55,
      }}
      onMouseEnter={e => (e.currentTarget.style.opacity = '1')}
      onMouseLeave={e => (e.currentTarget.style.opacity = '0.55')}
    >
      esp ⚡{isDevToolsEnabled() ? '' : ' (off)'}
    </button>
  );
}
