import React, { useEffect } from 'react';
import { useServices } from '../AppContext';
import { ModelIds } from '../core/events';
import { ProgressModel, ProgressState } from '../models/ProgressModel';
import { useModelState, fmtNum } from './common';

/** The Progress tab: level, XP bar and the full badge collection. */
export function ProgressView() {
  const { dataService } = useServices();
  const state = useModelState<ProgressModel, ProgressState>(ModelIds.progress, m => m.state);
  const p = state.progress;

  useEffect(() => { void dataService.refreshProgress(); }, [dataService]);

  if (!p) return <div className="text-desk-dim">Loading progress…</div>;

  const span = p.nextLevelXp != null ? p.nextLevelXp - p.levelFloorXp : 1;
  const pct = p.nextLevelXp != null
    ? Math.min(100, Math.max(0, ((p.xp - p.levelFloorXp) / span) * 100)) : 100;
  const earned = p.badges.filter(b => b.earned);
  const locked = p.badges.filter(b => !b.earned);

  return (
    <div className="space-y-4 max-w-5xl">
      <div className="panel p-6 flex items-center gap-6">
        <div className="w-20 h-20 rounded-full bg-desk-bg border-2 border-desk-accent
                        flex items-center justify-center text-3xl font-bold text-desk-accent num">
          {p.level}
        </div>
        <div className="flex-1">
          <div className="text-xl font-semibold">{p.levelName}</div>
          <div className="text-desk-dim text-sm num">
            {fmtNum(p.xp, 0)} XP{p.nextLevelXp != null && <> — {fmtNum(p.nextLevelXp - p.xp, 0)} XP to the next level</>}
          </div>
          <div className="h-3 bg-desk-bg rounded-full mt-2 overflow-hidden border border-desk-border">
            <div className="h-full bg-desk-accent transition-all" style={{ width: `${pct}%` }} />
          </div>
        </div>
        <div className="text-center">
          <div className="text-2xl font-bold num">{p.earnedCount}<span className="text-desk-dim">/{p.badges.length}</span></div>
          <div className="text-xs uppercase tracking-wider text-desk-dim">badges</div>
        </div>
      </div>

      <div className="panel">
        <div className="panel-title">Earned</div>
        <div className="grid grid-cols-3 gap-3 p-4">
          {earned.map(b => (
            <div key={b.code}
                 className={`border rounded-lg p-3 ${state.recentUnlocks.includes(b.code)
                   ? 'border-desk-warn shadow-[0_0_12px_rgba(210,153,34,.4)]' : 'border-desk-border'}`}>
              <div className="font-semibold text-desk-warn">🏆 {b.title}
                <span className="text-xs text-desk-dim ml-2 num">+{b.xp} XP</span></div>
              <div className="text-xs text-desk-dim mt-1">{b.description}</div>
              {b.earnedSimDate && <div className="text-[10px] text-desk-dim mt-1 num">sim {b.earnedSimDate}</div>}
            </div>
          ))}
          {earned.length === 0 &&
            <div className="text-desk-dim text-sm col-span-3">Nothing yet — place your first trade!</div>}
        </div>

        <div className="panel-title border-t border-desk-border">Still locked — your curriculum</div>
        <div className="grid grid-cols-3 gap-3 p-4">
          {locked.map(b => (
            <div key={b.code} className="border border-desk-border/50 rounded-lg p-3 opacity-60">
              <div className="font-semibold">🔒 {b.title}
                <span className="text-xs text-desk-dim ml-2 num">+{b.xp} XP</span></div>
              <div className="text-xs text-desk-dim mt-1">{b.description}</div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
