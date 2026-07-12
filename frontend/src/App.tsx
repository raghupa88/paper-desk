import React, { useState } from 'react';
import { EspRouterContextProvider } from 'esp-js-react';
import { AppContext } from './AppContext';
import { AppServices } from './bootstrap';
import { ModelIds } from './core/events';
import { SessionModel } from './models/SessionModel';
import { useModelState } from './views/common';
import { LoginView } from './views/LoginView';
import { ScenarioPicker } from './views/ScenarioPicker';
import { HeaderBar } from './views/HeaderBar';
import { DashboardView } from './views/DashboardView';
import { MarketView } from './views/MarketView';
import { OptionsChainView } from './views/OptionsChainView';
import { FxSalesView } from './views/FxSalesView';
import { FxTraderView } from './views/FxTraderView';
import { PortfolioView } from './views/PortfolioView';
import { BlotterView } from './views/BlotterView';
import { ClassroomView } from './views/ClassroomView';
import { ProgressView } from './views/ProgressView';
import { ToastHost } from './views/ToastHost';
import { GettingStartedGuide } from './views/GettingStartedGuide';
import { DevToolsLauncher } from './devtools/DevToolsLauncher';

export type DeskTab = 'dashboard' | 'market' | 'options' | 'fx-sales' | 'fx-trader'
  | 'portfolio' | 'blotter' | 'progress' | 'classroom';

const TABS: Array<{ id: DeskTab; label: string }> = [
  { id: 'dashboard', label: 'Dashboard' },
  { id: 'market', label: 'Market' },
  { id: 'options', label: 'Options Chain' },
  { id: 'fx-sales', label: 'FX Sales' },
  { id: 'fx-trader', label: 'FX Trader' },
  { id: 'portfolio', label: 'Portfolio' },
  { id: 'blotter', label: 'Blotter' },
  { id: 'progress', label: 'Progress' },
  { id: 'classroom', label: 'Classroom' },
];

export function App({ services }: { services: AppServices }) {
  return (
    <AppContext.Provider value={services}>
      <EspRouterContextProvider router={services.router}>
        <Shell />
        <DevToolsLauncher />
      </EspRouterContextProvider>
    </AppContext.Provider>
  );
}

function Shell() {
  const session = useModelState<SessionModel, SessionModel['state']>(ModelIds.session, m => m.state);
  const [tab, setTab] = useState<DeskTab>('dashboard');
  const [pickingScenario, setPickingScenario] = useState(false);

  if (!session.user) return <LoginView />;
  if (!session.activeAccount || pickingScenario) {
    return <ScenarioPicker onDone={() => setPickingScenario(false)}
                           canCancel={!!session.activeAccount} />;
  }

  return (
    <div className="min-h-screen flex flex-col">
      <HeaderBar onAddScenario={() => setPickingScenario(true)} />
      <nav className="flex gap-1 px-4 border-b border-desk-border bg-desk-panel overflow-x-auto whitespace-nowrap">
        {TABS.map(t => (
          <div key={t.id} className={`tab shrink-0 ${tab === t.id ? 'tab-active' : ''}`}
               onClick={() => setTab(t.id)}>{t.label}</div>
        ))}
      </nav>
      <GettingStartedGuide onGoToMarket={() => setTab('market')} onGoToProgress={() => setTab('progress')} />
      <main className="flex-1 p-3 sm:p-4 overflow-auto">
        {tab === 'dashboard' && <DashboardView />}
        {tab === 'market' && <MarketView />}
        {tab === 'options' && <OptionsChainView />}
        {tab === 'fx-sales' && <FxSalesView />}
        {tab === 'fx-trader' && <FxTraderView />}
        {tab === 'portfolio' && <PortfolioView />}
        {tab === 'blotter' && <BlotterView />}
        {tab === 'progress' && <ProgressView />}
        {tab === 'classroom' && <ClassroomView />}
      </main>
      <ToastHost />
    </div>
  );
}
