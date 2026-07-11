import React, { useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { GLOSSARY, TermKey } from '../core/glossary';

/**
 * A small "?" affordance that shows a plain-language definition — the
 * in-app teaching layer for Greeks/margin/swap jargon. Deliberately built
 * as the accessible ARIA "tooltip" pattern, not a click-toggle popover:
 * it shows on hover AND on keyboard focus (so Tab alone reveals it, no
 * Enter/Space needed) and dismisses on blur/mouseleave/Escape. Rendered
 * via a portal to document.body so it's never clipped by a scrollable
 * table/panel — the abbreviations it explains live almost exclusively in
 * scrolling table headers.
 */
export function InfoTip({ term }: { term: TermKey }) {
  const [open, setOpen] = useState(false);
  const [pos, setPos] = useState<{ top: number; left: number } | null>(null);
  const btnRef = useRef<HTMLButtonElement>(null);
  const entry = GLOSSARY[term];
  const id = `infotip-${term}`;

  const show = () => {
    const rect = btnRef.current?.getBoundingClientRect();
    if (rect) {
      setPos({ top: rect.bottom + 6, left: Math.min(rect.left, window.innerWidth - 272) });
    }
    setOpen(true);
  };
  const hide = () => setOpen(false);

  return (
    <>
      <button
        ref={btnRef}
        type="button"
        tabIndex={0}
        className="ml-1 inline-flex items-center justify-center w-3.5 h-3.5 rounded-full
                   border border-desk-dim text-desk-dim text-[9px] leading-none normal-case font-normal
                   hover:border-desk-accent hover:text-desk-accent
                   focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-desk-accent"
        aria-label={`What is ${entry.title}?`}
        aria-describedby={open ? id : undefined}
        onMouseEnter={show}
        onMouseLeave={hide}
        onFocus={show}
        onBlur={hide}
        onKeyDown={e => { if (e.key === 'Escape') hide(); }}
      >
        ?
      </button>
      {open && pos && createPortal(
        <span
          id={id}
          role="tooltip"
          style={{ position: 'fixed', top: pos.top, left: pos.left }}
          className="z-50 w-64 normal-case font-normal text-left text-xs bg-desk-panel
                     border border-desk-border rounded-lg shadow-lg p-3 pointer-events-none"
        >
          <div className="font-semibold text-desk-text mb-1">{entry.title}</div>
          <div className="text-desk-dim leading-snug">{entry.description}</div>
        </span>,
        document.body,
      )}
    </>
  );
}
