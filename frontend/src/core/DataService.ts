import { Router } from 'esp-js';
import { ApiClient } from './ApiClient';
import { AuthStore } from './AuthStore';
import { StompService } from './StompService';
import { EventConst, ModelIds } from './events';
import {
  AccountInfo, ChainData, ClockState, Cohort, LeaderboardRow, MissionView, OrderView, PairLadder,
  PortfolioView, ProgressView, Quote, RfqQuote, Scenario, ScorecardView, SettlementView, StreakInfo,
  UserInfo, Bar, EquityPoint,
} from './types';

/**
 * All backend interaction. Command methods perform the HTTP call and publish
 * the result back into the Router so the polimer models (and the devtools
 * event trace) see every state change as an event.
 */
export class DataService {
  private pollTimers: number[] = [];
  private sessionId: number | null = null;
  private accountId: number | null = null;

  constructor(private api: ApiClient, private auth: AuthStore,
              private stomp: StompService, private router: Router) {
    // achievements/missions are awarded server-side mid-flow; refresh on unlock
    this.stomp.accountEventHook = ev => {
      if (ev.type === 'ACHIEVEMENT' || ev.type === 'MISSION_COMPLETE') {
        void this.refreshProgress();
        void this.refreshMissions();
      }
    };
  }

  // ---- auth / session ----

  async signup(email: string, password: string, displayName: string, role: string): Promise<void> {
    const res = await this.api.post<{ token: string; user: UserInfo }>('/api/auth/signup',
      { email, password, displayName, role });
    this.auth.save(res.token, res.user);
    this.stomp.setToken(res.token);
    this.router.broadcastEvent(EventConst.loggedIn, res.user);
    await this.bootstrap();
  }

  async login(email: string, password: string): Promise<void> {
    const res = await this.api.post<{ token: string; user: UserInfo }>('/api/auth/login', { email, password });
    this.auth.save(res.token, res.user);
    this.stomp.setToken(res.token);
    this.router.broadcastEvent(EventConst.loggedIn, res.user);
    await this.bootstrap();
  }

  /** Call once at app startup if a token already exists (session restore on reload). */
  resumeSession(): void {
    this.stomp.setToken(this.auth.token);
  }

  logout() {
    this.stopPolling();
    this.stomp.setContext(null, null);
    this.stomp.setToken(null);
    this.auth.clear();
    this.router.broadcastEvent(EventConst.loggedOut, {});
  }

  /** Load scenarios + accounts after login / refresh; auto-select the first account. */
  async bootstrap(): Promise<void> {
    const [scenarios, accounts] = await Promise.all([
      this.api.get<Scenario[]>('/api/scenarios'),
      this.api.get<AccountInfo[]>('/api/accounts'),
    ]);
    this.router.broadcastEvent(EventConst.scenariosLoaded, scenarios);
    this.router.broadcastEvent(EventConst.accountsLoaded, accounts);
    if (accounts.length > 0) this.selectAccount(accounts[0]);
    void this.touchStreak();
  }

  /**
   * Marks today as a used day for the real-calendar login streak (idempotent
   * per day). On a milestone, reuses the exact same toast pipeline as fills/
   * achievements/missions/margin-calls — one consistent notification style
   * app-wide — even though this didn't arrive over the account WebSocket.
   */
  private async touchStreak(): Promise<void> {
    const streak = await this.api.post<StreakInfo>('/api/streak/touch');
    this.router.broadcastEvent(EventConst.streakLoaded, streak);
    if (streak.milestoneDays != null) {
      this.router.broadcastEvent(EventConst.accountEventReceived, {
        type: 'STREAK_MILESTONE',
        detail: `${streak.milestoneDays}-day streak! Come back tomorrow to keep it going.`,
      });
    }
  }

  async joinScenario(scenarioId: number): Promise<void> {
    this.router.publishEvent(ModelIds.session, EventConst.joinScenarioRequested, { scenarioId });
    const account = await this.api.post<AccountInfo>(`/api/scenarios/${scenarioId}/join`);
    const accounts = await this.api.get<AccountInfo[]>('/api/accounts');
    this.router.broadcastEvent(EventConst.accountsLoaded, accounts);
    this.selectAccount(account);
  }

  selectAccount(account: AccountInfo) {
    this.sessionId = account.sessionId;
    this.accountId = account.accountId;
    this.router.broadcastEvent(EventConst.accountSelected, account);
    this.stomp.setContext(account.sessionId, account.accountId);
    this.startPolling();
    void this.refreshClock();
    void this.refreshQuotes();
    void this.refreshPortfolio();
    void this.refreshBlotter();
    void this.refreshProgress();
    void this.refreshMissions();
  }

  // ---- sim clock ----

  async refreshClock(): Promise<void> {
    if (this.sessionId == null) return;
    const clock = await this.api.get<ClockState>(`/api/sessions/${this.sessionId}/clock`);
    this.router.broadcastEvent(EventConst.clockTick, clock);
  }

  async clockControl(action: string, acceleration?: number): Promise<void> {
    if (this.sessionId == null) return;
    this.router.publishEvent(ModelIds.clock, EventConst.clockControlRequested, { action, acceleration });
    const clock = await this.api.post<ClockState>(`/api/sessions/${this.sessionId}/clock`, { action, acceleration });
    this.router.broadcastEvent(EventConst.clockTick, clock);
    if (action === 'STEP_DAY') {
      void this.refreshPortfolio();
      void this.refreshQuotes();
      void this.refreshSettlements();
      void this.refreshProgress();
      void this.refreshMissions();
    }
  }

  // ---- market data ----

  async refreshQuotes(): Promise<void> {
    if (this.sessionId == null) return;
    const quotes = await this.api.get<Quote[]>(`/api/market/${this.sessionId}/quotes`);
    // broadcast: both the market watchlist and the FX views consume quotes
    this.router.broadcastEvent(EventConst.quotesLoaded, quotes);
  }

  async loadBars(symbol: string): Promise<void> {
    if (this.sessionId == null) return;
    const bars = await this.api.get<Bar[]>(`/api/market/${this.sessionId}/bars/${encodeURIComponent(symbol)}`);
    this.router.publishEvent(ModelIds.market, EventConst.barsLoaded, { symbol, bars });
  }

  async loadChain(underlyingId: number): Promise<void> {
    if (this.sessionId == null) return;
    const chain = await this.api.get<ChainData>(`/api/market/${this.sessionId}/chain/${underlyingId}`);
    this.router.publishEvent(ModelIds.chain, EventConst.chainLoaded, chain);
  }

  // ---- trading ----

  async placeOrder(req: {
    accountId: number; instrumentId: number; side: string; type: string;
    qty: number; limitPrice?: number | null; viewContext?: string | null;
  }): Promise<void> {
    this.router.publishEvent(ModelIds.trading, EventConst.submitOrderRequested, req);
    try {
      const order = await this.api.post<OrderView>('/api/orders', req);
      this.router.publishEvent(ModelIds.trading, EventConst.orderResultReceived, { order });
    } catch (e: any) {
      this.router.publishEvent(ModelIds.trading, EventConst.orderResultReceived, { error: e.message });
    }
    void this.refreshPortfolio();
    void this.refreshBlotter();
    void this.refreshProgress();
    void this.refreshMissions();
  }

  async cancelOrder(orderId: number): Promise<void> {
    if (this.accountId == null) return;
    this.router.publishEvent(ModelIds.trading, EventConst.cancelOrderRequested, { orderId });
    await this.api.post(`/api/orders/${orderId}/cancel`, { accountId: this.accountId });
    void this.refreshBlotter();
  }

  async refreshPortfolio(): Promise<void> {
    if (this.accountId == null) return;
    const [portfolio, history] = await Promise.all([
      this.api.get<PortfolioView>(`/api/portfolio/${this.accountId}`),
      this.api.get<EquityPoint[]>(`/api/portfolio/${this.accountId}/history`),
    ]);
    this.router.publishEvent(ModelIds.trading, EventConst.portfolioLoaded, { portfolio, history });
  }

  async refreshBlotter(): Promise<void> {
    if (this.accountId == null) return;
    const orders = await this.api.get<OrderView[]>(`/api/orders?accountId=${this.accountId}`);
    this.router.publishEvent(ModelIds.trading, EventConst.blotterLoaded, orders);
  }

  async refreshSettlements(): Promise<void> {
    if (this.accountId == null) return;
    const settlements = await this.api.get<SettlementView[]>(`/api/portfolio/${this.accountId}/settlements`);
    this.router.publishEvent(ModelIds.trading, EventConst.settlementsLoaded, settlements);
  }

  async refreshScorecard(): Promise<void> {
    if (this.accountId == null) return;
    const scorecard = await this.api.get<ScorecardView>(`/api/portfolio/${this.accountId}/scorecard`);
    this.router.publishEvent(ModelIds.trading, EventConst.scorecardLoaded, scorecard);
  }

  // ---- gamification ----

  async refreshProgress(): Promise<void> {
    if (this.accountId == null) return;
    const progress = await this.api.get<ProgressView>(`/api/progress/${this.accountId}`);
    this.router.publishEvent(ModelIds.progress, EventConst.progressLoaded, progress);
  }

  async refreshMissions(): Promise<void> {
    if (this.accountId == null) return;
    const missions = await this.api.get<MissionView[]>(`/api/missions/${this.accountId}`);
    this.router.publishEvent(ModelIds.progress, EventConst.missionsLoaded, missions);
  }

  // ---- fx sales / trader ----

  async rfq(req: object): Promise<void> {
    try {
      const quote = await this.api.post<RfqQuote>('/api/fx/rfq', { ...req, accountId: this.accountId });
      this.router.publishEvent(ModelIds.fx, EventConst.rfqQuoted, { quote });
    } catch (e: any) {
      this.router.publishEvent(ModelIds.fx, EventConst.rfqQuoted, { error: e.message });
    }
  }

  async rfqExecute(req: object): Promise<void> {
    try {
      const deal = await this.api.post<RfqQuote>('/api/fx/rfq/execute', { ...req, accountId: this.accountId });
      this.router.publishEvent(ModelIds.fx, EventConst.rfqExecuted, { deal });
      void this.refreshPortfolio();
      void this.refreshBlotter();
    } catch (e: any) {
      this.router.publishEvent(ModelIds.fx, EventConst.rfqExecuted, { error: e.message });
    }
  }

  async refreshLadder(): Promise<void> {
    if (this.accountId == null) return;
    const ladder = await this.api.get<PairLadder[]>(`/api/fx/ladder?accountId=${this.accountId}`);
    this.router.publishEvent(ModelIds.fx, EventConst.ladderLoaded, ladder);
  }

  // ---- instructor / cohorts ----

  async refreshCohorts(): Promise<void> {
    const cohorts = await this.api.get<Cohort[]>('/api/cohorts');
    this.router.publishEvent(ModelIds.instructor, EventConst.cohortsLoaded, cohorts);
  }

  async createCohort(name: string, scenarioId: number, startingBalance: number): Promise<void> {
    this.router.publishEvent(ModelIds.instructor, EventConst.createCohortRequested, { name, scenarioId });
    await this.api.post<Cohort>('/api/cohorts', { name, scenarioId, startingBalance });
    await this.refreshCohorts();
  }

  async joinCohort(joinCode: string): Promise<void> {
    this.router.publishEvent(ModelIds.instructor, EventConst.joinCohortRequested, { joinCode });
    const cohort = await this.api.post<Cohort>('/api/cohorts/join', { joinCode });
    this.router.publishEvent(ModelIds.instructor, EventConst.cohortJoined, cohort);
    const accounts = await this.api.get<AccountInfo[]>('/api/accounts');
    this.router.broadcastEvent(EventConst.accountsLoaded, accounts);
    await this.refreshCohorts();
  }

  async loadLeaderboard(cohortId: number): Promise<void> {
    this.router.publishEvent(ModelIds.instructor, EventConst.leaderboardRequested, { cohortId });
    const rows = await this.api.get<LeaderboardRow[]>(`/api/cohorts/${cohortId}/leaderboard`);
    this.router.publishEvent(ModelIds.instructor, EventConst.leaderboardLoaded, { cohortId, rows });
  }

  /**
   * Gradebook export. Goes around ApiClient (which is JSON-only) with a raw
   * fetch, because a plain <a href> download can't carry the Authorization
   * header this endpoint needs — instead we fetch the CSV as a blob and
   * trigger the download via a throwaway object URL.
   */
  async downloadLeaderboardCsv(cohortId: number): Promise<void> {
    const res = await fetch(`/api/cohorts/${cohortId}/leaderboard.csv`, {
      headers: this.auth.token ? { Authorization: `Bearer ${this.auth.token}` } : {},
    });
    if (!res.ok) throw new Error('Failed to export the leaderboard');
    const disposition = res.headers.get('Content-Disposition') ?? '';
    const filename = /filename="([^"]+)"/.exec(disposition)?.[1] ?? `leaderboard-${cohortId}.csv`;
    const blob = await res.blob();
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    a.click();
    URL.revokeObjectURL(url);
  }

  // ---- background polling ----

  private startPolling() {
    this.stopPolling();
    this.pollTimers.push(window.setInterval(() => void this.refreshQuotes(), 2500));
    this.pollTimers.push(window.setInterval(() => void this.refreshPortfolio(), 2500));
    this.pollTimers.push(window.setInterval(() => void this.refreshSettlements(), 6000));
  }

  private stopPolling() {
    this.pollTimers.forEach(t => window.clearInterval(t));
    this.pollTimers = [];
  }
}
