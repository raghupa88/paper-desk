import React, { useEffect, useState } from 'react';
import { useServices } from '../AppContext';
import { ModelIds } from '../core/events';
import { InstructorModel, InstructorState } from '../models/InstructorModel';
import { SessionModel } from '../models/SessionModel';
import { useModelState, fmtMoney, fmtPct, pnlCls } from './common';

/**
 * The lightweight classroom layer. Instructors create cohorts (each gets its
 * own dedicated seeded session) and share the join code; students join and the
 * leaderboard ranks everyone by equity.
 */
export function ClassroomView() {
  const { dataService, authStore } = useServices();
  const state = useModelState<InstructorModel, InstructorState>(ModelIds.instructor, m => m.state);
  const session = useModelState<SessionModel, SessionModel['state']>(ModelIds.session, m => m.state);
  const isInstructor = authStore.user?.role === 'INSTRUCTOR';

  const [name, setName] = useState('');
  const [scenarioId, setScenarioId] = useState<number | ''>('');
  const [balance, setBalance] = useState(100000);
  const [joinCode, setJoinCode] = useState('');
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    void dataService.refreshCohorts();
  }, [dataService]);

  const selected = state.cohorts.find(c => c.cohortId === state.selectedCohortId) ?? null;

  useEffect(() => {
    if (state.selectedCohortId == null) return;
    void dataService.loadLeaderboard(state.selectedCohortId);
    const t = window.setInterval(() => void dataService.loadLeaderboard(state.selectedCohortId!), 5000);
    return () => window.clearInterval(t);
  }, [state.selectedCohortId, dataService]);

  const run = (fn: () => Promise<void>) => {
    setError(null);
    fn().catch(e => setError(e.message));
  };

  return (
    <div className="flex gap-4 items-start">
      <div className="w-[380px] space-y-4">
        {isInstructor && (
          <div className="panel p-4 space-y-2">
            <div className="panel-title !p-0">Create a cohort</div>
            <input className="input" placeholder="Cohort name (e.g. FIN301 Spring)" value={name}
                   onChange={e => setName(e.target.value)} />
            <select className="input" value={scenarioId}
                    onChange={e => setScenarioId(Number(e.target.value))}>
              <option value="" disabled>Assign a scenario…</option>
              {session.scenarios.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
            </select>
            <input className="input" type="number" step={1000} min={1000} value={balance}
                   onChange={e => setBalance(Number(e.target.value))} />
            <button className="btn btn-accent w-full" disabled={!name || scenarioId === '' || state.busy}
                    onClick={() => run(() => dataService.createCohort(name, scenarioId as number, balance))}>
              Create cohort (dedicated seeded market)
            </button>
          </div>
        )}

        <div className="panel p-4 space-y-2">
          <div className="panel-title !p-0">Join a cohort</div>
          <div className="flex gap-2">
            <input className="input" placeholder="Join code" value={joinCode}
                   onChange={e => setJoinCode(e.target.value.toUpperCase())} />
            <button className="btn btn-accent" disabled={!joinCode || state.busy}
                    onClick={() => run(() => dataService.joinCohort(joinCode))}>Join</button>
          </div>
          <div className="text-xs text-desk-dim">Joining opens a trading account in the cohort's
            session with the instructor's starting balance.</div>
        </div>

        <div className="panel">
          <div className="panel-title">My cohorts</div>
          {state.cohorts.map(c => (
            <div key={c.cohortId}
                 className={`px-4 py-2 cursor-pointer border-b border-desk-border/40 hover:bg-desk-bg/50
                             ${c.cohortId === state.selectedCohortId ? 'bg-desk-bg' : ''}`}
                 onClick={() => void dataService.loadLeaderboard(c.cohortId)}>
              <div className="font-medium">{c.name}</div>
              <div className="text-xs text-desk-dim">
                {c.scenarioName} · start {fmtMoney(c.startingBalance, 0)}
                {(isInstructor && c.instructorId === authStore.user?.id) &&
                  <span className="text-desk-warn"> · code {c.joinCode}</span>}
              </div>
            </div>
          ))}
          {state.cohorts.length === 0 && <div className="p-4 text-desk-dim text-sm">No cohorts yet.</div>}
        </div>
        {error && <div className="text-desk-down text-sm">{error}</div>}
      </div>

      <div className="panel flex-1">
        <div className="panel-title">
          Leaderboard {selected ? `— ${selected.name} (${selected.scenarioName})` : ''}
        </div>
        <table className="tbl num">
          <thead><tr><th>#</th><th>Student</th><th className="!text-right">Equity</th>
            <th className="!text-right">Return</th><th className="!text-right">Max drawdown</th></tr></thead>
          <tbody>
            {state.leaderboard.map(r => (
              <tr key={r.rank}>
                <td className="font-semibold">{r.rank}</td>
                <td>{r.displayName}</td>
                <td className="text-right">{fmtMoney(r.equity, 0)}</td>
                <td className={`text-right ${pnlCls(r.returnPct)}`}>{fmtPct(r.returnPct)}</td>
                <td className="text-right text-desk-down">{r.maxDrawdownPct.toFixed(1)}%</td>
              </tr>
            ))}
            {state.leaderboard.length === 0 &&
              <tr><td colSpan={5} className="text-desk-dim">Select a cohort to see standings.</td></tr>}
          </tbody>
        </table>
      </div>
    </div>
  );
}
