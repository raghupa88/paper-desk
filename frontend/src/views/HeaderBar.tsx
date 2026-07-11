import React from 'react';
import { useServices } from '../AppContext';
import { ModelIds } from '../core/events';
import { ClockModel } from '../models/ClockModel';
import { SessionModel } from '../models/SessionModel';
import { TradingModel } from '../models/TradingModel';
import { ProgressModel } from '../models/ProgressModel';
import { useModelState, fmtSimTime, fmtMoney, fmtPct, pnlCls } from './common';

export function HeaderBar({ onAddScenario }: { onAddScenario: () => void }) {
  const { dataService, authStore } = useServices();
  const session = useModelState<SessionModel, SessionModel['state']>(ModelIds.session, m => m.state);
  const clock = useModelState<ClockModel, ClockModel['state']>(ModelIds.clock, m => m.state).clock;
  const portfolio = useModelState<TradingModel, TradingModel['state']>(ModelIds.trading, m => m.state).portfolio;
  const progress = useModelState<ProgressModel, ProgressModel['state']>(ModelIds.progress, m => m.state).progress;

  const acct = session.activeAccount;
  const xpPct = progress && progress.nextLevelXp != null
    ? Math.min(100, ((progress.xp - progress.levelFloorXp) / (progress.nextLevelXp - progress.levelFloorXp)) * 100)
    : 100;

  return (
    <header className="flex items-center gap-4 px-4 py-2 bg-desk-panel border-b border-desk-border">
      <span className="font-bold text-desk-accent text-lg">Paper Desk</span>
      <span className="text-[10px] uppercase tracking-wider text-desk-warn border border-desk-warn rounded px-1.5 py-0.5">
        simulated
      </span>

      <select className="input !w-auto" value={acct?.accountId ?? ''}
              onChange={e => {
                const a = session.accounts.find(x => x.accountId === Number(e.target.value));
                if (a) dataService.selectAccount(a);
              }}>
        {session.accounts.map(a => (
          <option key={a.accountId} value={a.accountId}>
            {a.scenarioName}{a.cohortId ? ' (class)' : ''}
          </option>
        ))}
      </select>
      <button className="btn text-xs" onClick={onAddScenario}>+ scenario</button>

      <div className="flex items-center gap-2 ml-4 num">
        <span className={`w-2 h-2 rounded-full ${clock?.paused ? 'bg-desk-warn' : 'bg-desk-up animate-pulse'}`} />
        <span className="text-sm">{fmtSimTime(clock?.simTime)}</span>
        <span className="text-xs text-desk-dim">{clock?.acceleration ?? '—'}×</span>
        <span className="text-xs text-desk-dim">float {clock ? (clock.floatingRate * 100).toFixed(2) : '—'}%</span>
      </div>
      <div className="flex items-center gap-1">
        <button className="btn text-xs" title={clock?.paused ? 'Resume sim clock' : 'Pause sim clock'}
                onClick={() => void dataService.clockControl(clock?.paused ? 'RESUME' : 'PAUSE')}>
          {clock?.paused ? '▶ resume' : '⏸ pause'}
        </button>
        <button className="btn text-xs" title="Advance one sim day instantly (runs settlement & expiries)"
                onClick={() => void dataService.clockControl('STEP_DAY')}>⏭ +1 day</button>
        <select className="input !w-auto text-xs" value={clock?.acceleration ?? 300}
                onChange={e => void dataService.clockControl('SET_ACCELERATION', Number(e.target.value))}>
          <option value={60}>60×</option>
          <option value={300}>300×</option>
          <option value={1800}>1800×</option>
          <option value={7200}>7200×</option>
        </select>
      </div>

      <div className="ml-auto flex items-center gap-4 num text-sm">
        {progress && (
          <div className="flex items-center gap-2" title={`${progress.levelName} — ${Math.round(progress.xp)} XP`}>
            <span className="w-6 h-6 rounded-full bg-desk-bg border border-desk-accent text-desk-accent
                             flex items-center justify-center text-xs font-bold">{progress.level}</span>
            <div className="w-20 h-1.5 bg-desk-bg rounded-full overflow-hidden border border-desk-border">
              <div className="h-full bg-desk-accent" style={{ width: `${xpPct}%` }} />
            </div>
          </div>
        )}
        {portfolio && (
          <>
            <span>Equity <strong>{fmtMoney(portfolio.equity, 0)}</strong></span>
            <span className={pnlCls(portfolio.dayPnl)}>Day {fmtMoney(portfolio.dayPnl, 0)}</span>
            <span className={pnlCls(portfolio.totalReturnPct)}>{fmtPct(portfolio.totalReturnPct)}</span>
          </>
        )}
        <span className="text-desk-dim">{authStore.user?.displayName}</span>
        <button className="btn text-xs" onClick={() => dataService.logout()}>Log out</button>
      </div>
    </header>
  );
}
