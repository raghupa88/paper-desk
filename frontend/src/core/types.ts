// DTO shapes mirrored from the backend REST/WS payloads.

export interface UserInfo {
  id: number;
  email: string;
  displayName: string;
  role: 'STUDENT' | 'INSTRUCTOR';
}

export interface Scenario {
  id: number;
  name: string;
  description: string;
  acceleration: number;
}

export interface AccountInfo {
  accountId: number;
  sessionId: number;
  scenarioId: number;
  scenarioName: string;
  cohortId: number | null;
  cashBalance: number;
  startingBalance: number;
}

export interface ClockState {
  sessionId: number;
  simTime: string;
  simDate: string;
  paused: boolean;
  acceleration: number;
  floatingRate: number;
}

export interface Greeks {
  delta: number;
  gamma: number;
  theta: number;
  vega: number;
  rho?: number;
}

export interface Quote {
  instrumentId: number;
  symbol: string;
  name: string;
  type: string;
  underlyingId: number | null;
  strike: number | null;
  expiryDate: string | null;
  callPut: 'CALL' | 'PUT' | null;
  contractSize: number;
  initialMargin: number | null;
  maintenanceMargin: number | null;
  notional: number | null;
  fixedRate: number | null;
  payFreqMonths: number | null;
  mid: number;
  bid: number;
  ask: number;
  yearsToExpiry: number | null;
  greeks?: Greeks;
}

export interface ChainRow {
  strike: number;
  call?: Quote;
  put?: Quote;
}

export interface ChainExpiry {
  expiry: string;
  rows: ChainRow[];
}

export interface ChainData {
  underlying: Quote;
  expiries: ChainExpiry[];
}

export interface Bar {
  time: string;
  open: number;
  high: number;
  low: number;
  close: number;
}

export interface PositionView {
  positionId: number;
  instrumentId: number;
  symbol: string;
  name: string;
  type: string;
  qty: number;
  avgPrice: number;
  mark: number;
  marketValue: number;
  unrealizedPnl: number;
  realizedPnl: number;
  delta: number | null;
  gamma: number | null;
  theta: number | null;
  vega: number | null;
  marginUsed: number | null;
  lastSettlePrice: number | null;
  expiryDate: string | null;
}

export interface PortfolioView {
  accountId: number;
  cash: number;
  marginHeld: number;
  positionsValue: number;
  equity: number;
  startingBalance: number;
  totalReturnPct: number;
  dayPnl: number;
  positions: PositionView[];
}

export interface OrderFill {
  price: number;
  qty: number;
  simTime: string;
}

export interface OrderView {
  orderId: number;
  accountId: number;
  instrumentId: number;
  symbol: string;
  instrumentType: string;
  side: 'BUY' | 'SELL';
  orderType: 'MARKET' | 'LIMIT';
  limitPrice: number | null;
  qty: number;
  status: 'NEW' | 'FILLED' | 'CANCELLED' | 'REJECTED';
  viewContext: 'SALES' | 'TRADER' | null;
  rejectReason: string | null;
  placedSimTime: string | null;
  fills: OrderFill[];
}

export interface SettlementView {
  id: number;
  simDate: string;
  kind: string;
  cashFlow: number;
  detail: string;
  symbol: string | null;
}

export interface EquityPoint {
  simDate: string;
  equity: number;
}

export interface RfqQuote {
  pair: string;
  spot: number;
  strike: number;
  expiryDate: string;
  callPut: string;
  side: string;
  notional: number;
  midPrice: number;
  salesMarginBps: number;
  salesMarginPerUnit: number;
  allInPrice: number;
  premiumTotal: number;
  greeks: Greeks;
  orderId?: number;
  orderStatus?: string;
  instrumentId?: number;
}

export interface LadderRow {
  shiftPct: number;
  spot: number;
  pnl: number;
  delta: number;
  gamma: number;
  vega: number;
}

export interface PairLadder {
  pair: string;
  spot: number;
  rows: LadderRow[];
}

export interface Cohort {
  cohortId: number;
  name: string;
  scenarioId: number;
  scenarioName: string;
  sessionId: number;
  startingBalance: number;
  joinCode: string;
  instructorId: number;
  accountId?: number;
}

export interface StreakInfo {
  currentStreak: number;
  longestStreak: number;
  milestoneDays: number | null;
}

export interface LeaderboardRow {
  rank: number;
  displayName: string;
  equity: number;
  returnPct: number;
  maxDrawdownPct: number;
  xp: number;
  level: number;
  levelName: string;
}

export interface BadgeView {
  code: string;
  title: string;
  description: string;
  xp: number;
  earned: boolean;
  earnedSimDate: string | null;
}

export interface ProgressView {
  accountId: number;
  xp: number;
  level: number;
  levelName: string;
  levelFloorXp: number;
  nextLevelXp: number | null;
  earnedCount: number;
  badges: BadgeView[];
}

export interface MissionStepView {
  description: string;
  done: boolean;
}

export interface MissionView {
  code: string;
  title: string;
  description: string;
  xp: number;
  completed: boolean;
  steps: MissionStepView[];
}
