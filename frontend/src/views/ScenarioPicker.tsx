import React, { useEffect, useState } from 'react';
import { useServices } from '../AppContext';
import { ModelIds } from '../core/events';
import { SessionModel } from '../models/SessionModel';
import { useModelState, fmtMoney } from './common';

export function ScenarioPicker({ onDone, canCancel }: { onDone: () => void; canCancel: boolean }) {
  const { dataService } = useServices();
  const session = useModelState<SessionModel, SessionModel['state']>(ModelIds.session, m => m.state);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (session.scenarios.length === 0) void dataService.bootstrap().catch(() => {});
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const join = async (scenarioId: number) => {
    setError(null);
    try {
      await dataService.joinScenario(scenarioId);
      onDone();
    } catch (e: any) {
      setError(e.message);
    }
  };

  const existing = new Map(session.accounts.map(a => [a.scenarioId, a]));

  return (
    <div className="min-h-screen flex items-center justify-center p-6">
      <div className="max-w-3xl w-full space-y-4">
        <div className="flex items-baseline justify-between">
          <div>
            <h1 className="text-2xl font-bold text-desk-accent">Pick a market scenario</h1>
            <p className="text-desk-dim text-sm">
              Each scenario is a seeded simulated market. You start with virtual cash and can
              rejoin the same scenario any time — your account persists per scenario.
            </p>
          </div>
          {canCancel && <button className="btn" onClick={onDone}>Back to desk</button>}
        </div>
        {error && <div className="text-desk-down text-sm">{error}</div>}
        <div className="grid grid-cols-2 gap-4">
          {session.scenarios.map(s => {
            const acct = existing.get(s.id);
            return (
              <div key={s.id} className="panel p-5 space-y-2">
                <div className="text-lg font-semibold">{s.name}</div>
                <div className="text-desk-dim text-sm min-h-[3rem]">{s.description}</div>
                <div className="text-xs text-desk-dim">
                  clock speed: {s.acceleration}× (1 sim day ≈ {Math.round(86400 / s.acceleration / 60)} min)
                </div>
                {acct
                  ? <button className="btn btn-accent w-full"
                            onClick={() => { dataService.selectAccount(acct); onDone(); }}>
                      Resume — cash {fmtMoney(acct.cashBalance, 0)}
                    </button>
                  : <button className="btn btn-accent w-full"
                            disabled={session.joiningScenarioId === s.id}
                            onClick={() => void join(s.id)}>
                      {session.joiningScenarioId === s.id ? 'Joining…' : 'Join with 100,000 virtual cash'}
                    </button>}
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}
