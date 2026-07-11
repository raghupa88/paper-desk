import React, { useEffect, useMemo, useRef, useState } from 'react';
import { DEVTOOLS_CHANNEL, DevToolsMessage } from '../protocol';
import { EspTraceEvent } from '../espDevTools';

const MAX_EVENTS = 2000; // isolated from the app's frame budget, so a generous history is fine here

/**
 * The standalone esp DevTools window. Runs as its own browsing context
 * (its own tab/window, its own renderer process under Chrome's
 * site-per-process model) — the only link back to the app under test is a
 * same-origin BroadcastChannel. If the app tab reloads, hangs, or crashes,
 * this window keeps everything received so far; reopening the app just
 * resumes the stream (a fresh 'hello' pulls a backfill).
 */
export function PanelApp() {
  const [events, setEvents] = useState<EspTraceEvent[]>([]);
  const [connected, setConnected] = useState(false);
  const [filter, setFilter] = useState('');
  const [selected, setSelected] = useState<EspTraceEvent | null>(null);
  const listRef = useRef<HTMLDivElement>(null);
  const channelRef = useRef<BroadcastChannel | null>(null);

  useEffect(() => {
    const channel = new BroadcastChannel(DEVTOOLS_CHANNEL);
    channelRef.current = channel;
    let watchdog: number;

    const armWatchdog = () => {
      window.clearTimeout(watchdog);
      setConnected(true);
      watchdog = window.setTimeout(() => setConnected(false), 5000);
    };

    channel.onmessage = (ev: MessageEvent<DevToolsMessage>) => {
      const msg = ev.data;
      armWatchdog();
      if (msg.kind === 'event') {
        setEvents(prev => upsertBySeq(prev, msg.event));
      } else if (msg.kind === 'backfill') {
        setEvents(prev => {
          let next = prev;
          for (const e of msg.events) next = upsertBySeq(next, e);
          return next;
        });
      } else if (msg.kind === 'clear') {
        setEvents([]);
      }
    };
    channel.postMessage({ kind: 'hello' } satisfies DevToolsMessage);

    return () => {
      window.clearTimeout(watchdog);
      channel.close();
    };
  }, []);

  useEffect(() => {
    if (listRef.current) listRef.current.scrollTop = listRef.current.scrollHeight;
  }, [events.length]);

  const filtered = useMemo(() => {
    const f = filter.trim().toLowerCase();
    if (!f) return events;
    return events.filter(e => e.eventType.toLowerCase().includes(f) || e.modelId.toLowerCase().includes(f));
  }, [events, filter]);

  const S: Record<string, React.CSSProperties> = {
    page: {
      height: '100%', display: 'flex', flexDirection: 'column',
      color: '#c9d1d9', fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace', fontSize: 12,
    },
    header: {
      display: 'flex', gap: 10, alignItems: 'center', padding: '8px 12px',
      borderBottom: '1px solid #30363d', background: '#161b22',
    },
    input: {
      flex: 1, background: '#0d1117', color: '#c9d1d9', border: '1px solid #30363d',
      borderRadius: 4, padding: '4px 8px', fontSize: 12, fontFamily: 'inherit',
    },
    btn: {
      background: 'transparent', color: '#8b949e', border: '1px solid #30363d',
      borderRadius: 4, padding: '4px 10px', cursor: 'pointer', fontSize: 12,
    },
    body: { display: 'flex', flex: 1, minHeight: 0 },
    list: { flex: 1, overflowY: 'auto', borderRight: '1px solid #30363d' },
    row: { display: 'flex', gap: 8, padding: '3px 12px', cursor: 'pointer', whiteSpace: 'nowrap' },
    detail: { width: 420, overflow: 'auto', padding: 12, whiteSpace: 'pre-wrap', wordBreak: 'break-all' },
  };

  return (
    <div style={S.page}>
      <div style={S.header}>
        <strong style={{ color: '#58a6ff' }}>esp DevTools</strong>
        <span style={{
          display: 'inline-flex', alignItems: 'center', gap: 5,
          color: connected ? '#3fb950' : '#f85149',
        }}>
          <span style={{
            width: 7, height: 7, borderRadius: '50%',
            background: connected ? '#3fb950' : '#f85149',
          }} />
          {connected ? 'connected to app' : 'no app connected — open Paper Desk in another tab'}
        </span>
        <span style={{ color: '#8b949e' }}>{filtered.length} / {events.length} events</span>
        <input style={S.input} placeholder="filter by event type or model id…"
               value={filter} onChange={e => setFilter(e.target.value)} />
        <button style={S.btn} onClick={() => setEvents([])}>clear</button>
      </div>
      <div style={S.body}>
        <div style={S.list} ref={listRef}>
          {filtered.map(e => (
            <div key={e.seq}
                 style={{ ...S.row, background: selected?.seq === e.seq ? '#1f6feb33' : undefined }}
                 onClick={() => setSelected(e)}>
              <span style={{ color: '#484f58', width: 40, textAlign: 'right' }}>{e.seq}</span>
              <span style={{ color: '#8b949e' }}>{new Date(e.time).toLocaleTimeString()}</span>
              <span style={{ color: '#d29922', minWidth: 100 }}>{e.modelId}</span>
              <span style={{ color: '#3fb950' }}>{e.eventType}</span>
              {e.state !== undefined && <span style={{ color: '#30363d' }}>●</span>}
            </div>
          ))}
          {filtered.length === 0 && (
            <div style={{ padding: 16, color: '#484f58' }}>
              Waiting for events… trade in the app (in a different tab/window) to see them stream in here.
            </div>
          )}
        </div>
        <div style={S.detail}>
          {selected ? (
            <>
              <div style={{ color: '#58a6ff', marginBottom: 6 }}>#{selected.seq} {selected.eventType} → {selected.modelId}</div>
              <div style={{ color: '#8b949e' }}>payload</div>
              <div>{JSON.stringify(selected.payload, null, 2)}</div>
              {selected.state !== undefined && (
                <>
                  <div style={{ color: '#8b949e', marginTop: 10 }}>state after dispatch</div>
                  <div>{JSON.stringify(selected.state, null, 2)}</div>
                </>
              )}
            </>
          ) : <span style={{ color: '#484f58' }}>select an event…</span>}
        </div>
      </div>
    </div>
  );
}

function upsertBySeq(events: EspTraceEvent[], incoming: EspTraceEvent): EspTraceEvent[] {
  const idx = events.findIndex(e => e.seq === incoming.seq);
  let next: EspTraceEvent[];
  if (idx === -1) {
    next = [...events, incoming].sort((a, b) => a.seq - b.seq);
  } else {
    next = events.slice();
    next[idx] = incoming; // the host re-sends the same seq once its state snapshot resolves
  }
  return next.length > MAX_EVENTS ? next.slice(next.length - MAX_EVENTS) : next;
}
