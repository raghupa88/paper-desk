import React, { useEffect, useState } from 'react';
import { useServices } from '../AppContext';
import { ModelIds } from '../core/events';
import { InstructorModel, InstructorState } from '../models/InstructorModel';
import { SessionModel } from '../models/SessionModel';
import { StudentReviewPanel } from './StudentReviewPanel';
import { useModelState, fmtMoney } from './common';
import { Pnl } from './Pnl';

/** Mirrors the backend's Mission enum (com.paperdesk.gamification.Mission) for the
 * curriculum-builder picker -- titles/descriptions/xp for the full catalog live
 * server-side; this is just the small, stable set of codes + labels to pick from. */
const MISSION_CATALOG: Array<{ code: string; title: string }> = [
  { code: 'FIRST_STEPS', title: 'First Steps' },
  { code: 'COVERED_CALL', title: 'Covered Call Writer' },
  { code: 'PROTECTIVE_PUT', title: 'Protective Put' },
  { code: 'LONG_STRADDLE', title: 'Long Straddle' },
  { code: 'FUTURES_LAB', title: 'Futures Settlement Lab' },
  { code: 'FX_DESK', title: 'FX Desk Rotation' },
  { code: 'SWAP_LAB', title: 'Swap Lab' },
];

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
  const [exporting, setExporting] = useState(false);
  const [challengeName, setChallengeName] = useState('');
  const [challengeDays, setChallengeDays] = useState(7);
  const [creatingChallenge, setCreatingChallenge] = useState(false);
  const [curriculumName, setCurriculumName] = useState('');
  const [curriculumDescription, setCurriculumDescription] = useState('');
  const [selectedMissions, setSelectedMissions] = useState<string[]>([]);
  const [creatingCurriculum, setCreatingCurriculum] = useState(false);

  useEffect(() => {
    void dataService.refreshCohorts();
  }, [dataService]);

  const selected = state.cohorts.find(c => c.cohortId === state.selectedCohortId) ?? null;

  useEffect(() => {
    if (state.selectedCohortId == null) return;
    const refresh = () => {
      void dataService.loadLeaderboard(state.selectedCohortId!);
      void dataService.loadChallenges(state.selectedCohortId!);
      void dataService.loadCurricula(state.selectedCohortId!);
    };
    refresh();
    const t = window.setInterval(refresh, 5000);
    return () => window.clearInterval(t);
  }, [state.selectedCohortId, dataService]);

  const createChallenge = () => {
    if (!selected || !challengeName) return;
    setError(null);
    setCreatingChallenge(true);
    dataService.createChallenge(selected.cohortId, challengeName, challengeDays)
      .then(() => setChallengeName(''))
      .catch(e => setError(e.message))
      .finally(() => setCreatingChallenge(false));
  };

  const toggleMission = (code: string) => {
    setSelectedMissions(prev => prev.includes(code) ? prev.filter(c => c !== code) : [...prev, code]);
  };

  const createCurriculum = () => {
    if (!selected || !curriculumName || selectedMissions.length === 0) return;
    setError(null);
    setCreatingCurriculum(true);
    dataService.createCurriculum(selected.cohortId, curriculumName, curriculumDescription, selectedMissions)
      .then(() => { setCurriculumName(''); setCurriculumDescription(''); setSelectedMissions([]); })
      .catch(e => setError(e.message))
      .finally(() => setCreatingCurriculum(false));
  };

  const run = (fn: () => Promise<void>) => {
    setError(null);
    fn().catch(e => setError(e.message));
  };

  const exportCsv = () => {
    if (!selected) return;
    setError(null);
    setExporting(true);
    dataService.downloadLeaderboardCsv(selected.cohortId)
      .catch(e => setError(e.message))
      .finally(() => setExporting(false));
  };

  return (
    <div className="flex flex-col lg:flex-row gap-4 items-start">
      <div className="w-full lg:w-[380px] lg:shrink-0 space-y-4">
        {isInstructor && (
          <div className="panel p-4 space-y-2">
            <div className="panel-title !p-0">Create a cohort</div>
            <label htmlFor="cohortName" className="sr-only">Cohort name</label>
            <input id="cohortName" className="input" placeholder="Cohort name (e.g. FIN301 Spring)" value={name}
                   onChange={e => setName(e.target.value)} />
            <label htmlFor="cohortScenario" className="sr-only">Scenario</label>
            <select id="cohortScenario" className="input" value={scenarioId}
                    onChange={e => setScenarioId(Number(e.target.value))}>
              <option value="" disabled>Assign a scenario…</option>
              {session.scenarios.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
            </select>
            <label htmlFor="cohortBalance" className="sr-only">Starting balance</label>
            <input id="cohortBalance" className="input" type="number" step={1000} min={1000} value={balance}
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
            <label htmlFor="joinCode" className="sr-only">Join code</label>
            <input id="joinCode" className="input" placeholder="Join code" value={joinCode}
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
        {error && <div role="alert" className="text-desk-down text-sm">{error}</div>}
      </div>

      <div className="flex-1 w-full space-y-4">
        <div className="panel overflow-x-auto">
          <div className="panel-title flex flex-wrap items-center justify-between gap-2">
            <span>Leaderboard {selected ? `— ${selected.name} (${selected.scenarioName})` : ''}</span>
            {selected && (
              <button className="btn text-xs normal-case" disabled={exporting} onClick={exportCsv}>
                {exporting ? 'Exporting…' : '⬇ Export CSV'}
              </button>
            )}
          </div>
          <table className="tbl num">
            <thead><tr><th>#</th><th>Student</th><th>Level</th><th className="!text-right">Equity</th>
              <th className="!text-right">Return</th><th className="!text-right">Max drawdown</th>
              {isInstructor && <th></th>}</tr></thead>
            <tbody>
              {state.leaderboard.map(r => (
                <tr key={r.rank}>
                  <td className="font-semibold">{r.rank}</td>
                  <td>{r.displayName}</td>
                  <td>
                    <span className="text-desk-accent font-semibold">{r.level}</span>
                    <span className="text-xs text-desk-dim ml-1">{r.levelName}</span>
                  </td>
                  <td className="text-right">{fmtMoney(r.equity, 0)}</td>
                  <td className="text-right"><Pnl value={r.returnPct} kind="pct" /></td>
                  <td className="text-right text-desk-down">{r.maxDrawdownPct.toFixed(1)}%</td>
                  {isInstructor && (
                    <td>
                      <button className="btn text-xs normal-case"
                              onClick={() => void dataService.reviewStudent(r.accountId)}>
                        Review
                      </button>
                    </td>
                  )}
                </tr>
              ))}
              {state.leaderboard.length === 0 &&
                <tr><td colSpan={isInstructor ? 7 : 6} className="text-desk-dim">Select a cohort to see standings.</td></tr>}
            </tbody>
          </table>
        </div>

        {selected && (
          <div className="panel overflow-x-auto">
            <div className="panel-title">Challenges — timed sprints scored on equity change since kickoff</div>
            {isInstructor && (
              <div className="flex flex-wrap items-end gap-2 p-4 border-b border-desk-border">
                <label className="text-xs text-desk-dim flex flex-col gap-1">
                  Challenge name
                  <input className="input" placeholder="e.g. Week 1 Sprint" value={challengeName}
                         onChange={e => setChallengeName(e.target.value)} />
                </label>
                <label className="text-xs text-desk-dim flex flex-col gap-1">
                  Duration (sim days)
                  <input className="input w-24" type="number" min={1} value={challengeDays}
                         onChange={e => setChallengeDays(Number(e.target.value))} />
                </label>
                <button className="btn btn-accent text-xs normal-case" disabled={!challengeName || creatingChallenge}
                        onClick={createChallenge}>
                  {creatingChallenge ? 'Starting…' : 'Start challenge'}
                </button>
              </div>
            )}
            <div className="divide-y divide-desk-border">
              {state.challenges.map(c => (
                <div key={c.challengeId} className="p-4">
                  <div className="flex flex-wrap items-center gap-2 mb-2">
                    <span className="font-semibold">{c.name}</span>
                    <span className={`text-[10px] uppercase tracking-wider rounded px-1.5 py-0.5 border
                                      ${c.active ? 'text-desk-up border-desk-up' : 'text-desk-dim border-desk-dim'}`}>
                      {c.active ? 'Active' : 'Ended'}
                    </span>
                    <span className="text-xs text-desk-dim">
                      {c.startSimDate} → {c.endSimDate} ({c.durationSimDays} sim days)
                    </span>
                  </div>
                  <table className="tbl num">
                    <thead><tr><th>#</th><th>Student</th><th className="!text-right">Return since kickoff</th></tr></thead>
                    <tbody>
                      {c.leaderboard.map(r => (
                        <tr key={r.rank}>
                          <td className="font-semibold">{r.rank}</td>
                          <td>{r.displayName}</td>
                          <td className="text-right"><Pnl value={r.returnPct} kind="pct" /></td>
                        </tr>
                      ))}
                      {c.leaderboard.length === 0 &&
                        <tr><td colSpan={3} className="text-desk-dim">No entries.</td></tr>}
                    </tbody>
                  </table>
                </div>
              ))}
              {state.challenges.length === 0 &&
                <div className="p-4 text-desk-dim text-sm">
                  {isInstructor ? 'No challenges yet — start a timed sprint above.' : 'No challenges running yet.'}
                </div>}
            </div>
          </div>
        )}

        {selected && (
          <div className="panel">
            <div className="panel-title">Curricula — a guided sequence of missions, one step unlocks the next</div>
            {isInstructor && (
              <div className="p-4 border-b border-desk-border space-y-2">
                <div className="flex flex-wrap gap-2">
                  <label className="text-xs text-desk-dim flex flex-col gap-1 flex-1 min-w-[160px]">
                    Curriculum name
                    <input className="input" placeholder="e.g. Options 101" value={curriculumName}
                           onChange={e => setCurriculumName(e.target.value)} />
                  </label>
                  <label className="text-xs text-desk-dim flex flex-col gap-1 flex-1 min-w-[160px]">
                    Description (optional)
                    <input className="input" placeholder="What this path teaches" value={curriculumDescription}
                           onChange={e => setCurriculumDescription(e.target.value)} />
                  </label>
                </div>
                <div>
                  <div className="text-xs text-desk-dim mb-1">
                    Steps, in the order students should complete them — click to add/remove:
                  </div>
                  <div className="flex flex-wrap gap-2">
                    {MISSION_CATALOG.map(m => {
                      const idx = selectedMissions.indexOf(m.code);
                      return (
                        <button key={m.code} type="button"
                                className={`btn text-xs normal-case ${idx >= 0 ? 'btn-accent' : ''}`}
                                onClick={() => toggleMission(m.code)}>
                          {idx >= 0 ? `${idx + 1}. ` : ''}{m.title}
                        </button>
                      );
                    })}
                  </div>
                </div>
                <button className="btn btn-accent text-xs normal-case"
                        disabled={!curriculumName || selectedMissions.length === 0 || creatingCurriculum}
                        onClick={createCurriculum}>
                  {creatingCurriculum ? 'Publishing…' : 'Publish curriculum'}
                </button>
              </div>
            )}
            <div className="divide-y divide-desk-border">
              {state.curricula.map(c => (
                <div key={c.curriculumId} className="p-4">
                  <div className="font-semibold">{c.name}</div>
                  {c.description && <div className="text-xs text-desk-dim mb-2">{c.description}</div>}
                  <ol className="space-y-1 mt-2">
                    {c.steps.map(s => (
                      <li key={s.stepOrder}
                          className={`text-sm flex items-center gap-2 ${!s.unlocked ? 'opacity-50' : ''}`}>
                        <span aria-hidden="true">{s.complete ? '✅' : s.unlocked ? '🎯' : '🔒'}</span>
                        <span className={s.complete ? 'text-desk-up' : ''}>{s.title}</span>
                        <span className="text-xs text-desk-dim">+{s.xp} XP</span>
                      </li>
                    ))}
                  </ol>
                </div>
              ))}
              {state.curricula.length === 0 &&
                <div className="p-4 text-desk-dim text-sm">
                  {isInstructor ? 'No curricula yet — publish a guided path above.' : 'No curriculum assigned yet.'}
                </div>}
            </div>
          </div>
        )}
      </div>
      {isInstructor && <StudentReviewPanel />}
    </div>
  );
}
