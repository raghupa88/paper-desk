import React, { useEffect, useMemo, useRef, useState } from 'react';
import { EspDevTools, EspTraceEvent } from './espDevTools';

/**
 * In-app overlay showing the live esp event trace. Toggle with Ctrl+Shift+E.
 * Deliberately styled inline so it can be dropped into any esp-js app without
 * pulling in the host app's CSS pipeline.
 */
export function EspDevToolsPanel({ devTools }: { devTools: EspDevTools }) {
  const [open, setOpen] = useState(false);
  const [filter, setFilter] = useState('');
  const [selected, setSelected] = useState<EspTraceEvent | null>(null);
  const [, setVersion] = useState(0);
  const listRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const offKey = (e: KeyboardEvent) => {
      if (e.ctrlKey && e.shiftKey && e.key.toLowerCase() === 'e') {
        e.preventDefault();
        setOpen(o => !o);
      }
    };
    window.addEventListener('keydown', offKey);
    const unsub = devTools.subscribe(() => setVersion(v => v + 1));
    return () => {
      window.removeEventListener('keydown', offKey);
      unsub();
    };
  }, [devTools]);

  useEffect(() => {
    if (listRef.current) listRef.current.scrollTop = listRef.current.scrollHeight;
  });

  const events = useMemo(() => {
    const f = filter.trim().toLowerCase();
    if (!f) return devTools.events;
    return devTools.events.filter(e =>
      e.eventType.toLowerCase().includes(f) || e.modelId.toLowerCase().includes(f));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [filter, devTools.events.length, devTools.events[devTools.events.length - 1]?.seq]);

  if (!open) {
    return (
      <button onClick={() => setOpen(true)} title="esp DevTools (Ctrl+Shift+E)" style={{
        position: 'fixed', bottom: 10, right: 10, zIndex: 9999, background: '#1f6feb',
        color: '#fff', border: 'none', borderRadius: 16, padding: '4px 10px',
        fontSize: 11, fontFamily: 'monospace', cursor: 'pointer', opacity: 0.7,
      }}>esp ⚡ {devTools.events.length}</button>
    );
  }

  const S: Record<string, React.CSSProperties> = {
    panel: {
      position: 'fixed', bottom: 0, right: 0, width: 560, height: '55vh', zIndex: 9999,
      background: '#0d1117', color: '#c9d1d9', border: '1px solid #30363d',
      borderTopLeftRadius: 8, display: 'flex', flexDirection: 'column',
      fontFamily: 'ui-monospace, monospace', fontSize: 11, boxShadow: '0 0 24px rgba(0,0,0,.6)',
    },
    header: {
      display: 'flex', gap: 8, alignItems: 'center', padding: '6px 8px',
      borderBottom: '1px solid #30363d', background: '#161b22',
    },
    input: {
      flex: 1, background: '#0d1117', color: '#c9d1d9', border: '1px solid #30363d',
      borderRadius: 4, padding: '2px 6px', fontSize: 11, fontFamily: 'inherit',
    },
    btn: {
      background: 'transparent', color: '#8b949e', border: '1px solid #30363d',
      borderRadius: 4, padding: '2px 8px', cursor: 'pointer', fontSize: 11,
    },
    body: { display: 'flex', flex: 1, minHeight: 0 },
    list: { flex: 1, overflowY: 'auto', borderRight: '1px solid #30363d' },
    row: { display: 'flex', gap: 6, padding: '2px 8px', cursor: 'pointer', whiteSpace: 'nowrap' },
    detail: { width: 240, overflow: 'auto', padding: 8, whiteSpace: 'pre-wrap', wordBreak: 'break-all' },
  };

  return (
    <div style={S.panel}>
      <div style={S.header}>
        <strong style={{ color: '#58a6ff' }}>esp DevTools</strong>
        <span style={{ color: '#8b949e' }}>{events.length} events</span>
        <input style={S.input} placeholder="filter by event type or model id…"
               value={filter} onChange={e => setFilter(e.target.value)} />
        <button style={S.btn} onClick={() => devTools.clear()}>clear</button>
        <button style={S.btn} onClick={() => setOpen(false)}>✕</button>
      </div>
      <div style={S.body}>
        <div style={S.list} ref={listRef}>
          {events.map(e => (
            <div key={e.seq}
                 style={{ ...S.row, background: selected?.seq === e.seq ? '#1f6feb33' : undefined }}
                 onClick={() => setSelected(e)}>
              <span style={{ color: '#484f58', width: 34, textAlign: 'right' }}>{e.seq}</span>
              <span style={{ color: '#8b949e' }}>{new Date(e.time).toLocaleTimeString()}</span>
              <span style={{ color: '#d29922', minWidth: 90 }}>{e.modelId}</span>
              <span style={{ color: '#3fb950' }}>{e.eventType}</span>
            </div>
          ))}
        </div>
        <div style={S.detail}>
          {selected ? (
            <>
              <div style={{ color: '#58a6ff', marginBottom: 4 }}>#{selected.seq} {selected.eventType} → {selected.modelId}</div>
              <div style={{ color: '#8b949e' }}>payload</div>
              <div>{JSON.stringify(selected.payload, null, 1)}</div>
              {selected.state !== undefined && (
                <>
                  <div style={{ color: '#8b949e', marginTop: 6 }}>state after dispatch</div>
                  <div>{JSON.stringify(selected.state, null, 1)}</div>
                </>
              )}
            </>
          ) : <span style={{ color: '#484f58' }}>select an event…</span>}
        </div>
      </div>
    </div>
  );
}
