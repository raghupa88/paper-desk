import React, { useState } from 'react';
import { ModelIds } from '../core/events';
import { ProgressModel } from '../models/ProgressModel';
import { MissionView } from '../core/types';
import { useModelState } from './common';

const DISMISS_KEY = 'paperdesk.gettingStartedDismissed';

/**
 * A first-run walkthrough for brand-new students, built entirely on top of
 * the existing FIRST_STEPS mission (server-evaluated step completion) rather
 * than inventing separate onboarding-progress tracking. Rendered in normal
 * document flow (a banner between the tab strip and the page content, not a
 * fixed overlay) so it pushes content down instead of floating on top of
 * it — a fixed panel here previously intercepted clicks on page content that
 * happened to render underneath it (e.g. the FX Sales "Quote client" button).
 * Disappears once the mission completes — the mission-complete toast and the
 * Progress tab's badge grid already celebrate that moment elsewhere.
 */
export function GettingStartedGuide({ onGoToMarket, onGoToProgress }:
  { onGoToMarket: () => void; onGoToProgress: () => void }) {
  const missions = useModelState<ProgressModel, MissionView[]>(ModelIds.progress, m => m.state.missions);
  const [dismissed, setDismissed] = useState(() => {
    try { return localStorage.getItem(DISMISS_KEY) === '1'; } catch { return false; }
  });

  const firstSteps = missions.find(m => m.code === 'FIRST_STEPS');

  const dismiss = () => {
    setDismissed(true);
    try { localStorage.setItem(DISMISS_KEY, '1'); } catch { /* best effort, e.g. private mode */ }
  };

  if (dismissed || !firstSteps || firstSteps.completed) return null;

  return (
    <div role="region" aria-label="Getting started guide"
         className="flex flex-wrap items-center gap-x-4 gap-y-2 px-4 py-2 text-xs
                    border-b border-desk-border bg-desk-panel">
      <span className="font-semibold whitespace-nowrap">👋 New here?</span>
      <span className="text-desk-dim">{firstSteps.description}</span>
      {firstSteps.steps.map((s, i) => (
        <span key={i} className={`flex items-center gap-1.5 whitespace-nowrap ${s.done ? 'text-desk-up' : 'text-desk-text'}`}>
          <span aria-hidden="true">{s.done ? '☑' : '☐'}</span>{s.description}
        </span>
      ))}
      <button className="btn btn-accent text-xs" onClick={onGoToMarket}>Go to Market →</button>
      <button className="btn text-xs" onClick={onGoToProgress}>Progress</button>
      <button className="btn text-xs ml-auto" aria-label="Dismiss getting started guide" onClick={dismiss}>✕</button>
    </div>
  );
}
