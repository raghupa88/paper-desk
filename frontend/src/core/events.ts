/**
 * Every esp event flowing through the Router. Payload types are documented in
 * the model that observes the event.
 */
export const EventConst = {
  // session / auth
  loggedIn: 'loggedIn',
  loggedOut: 'loggedOut',
  scenariosLoaded: 'scenariosLoaded',
  accountsLoaded: 'accountsLoaded',
  accountSelected: 'accountSelected',
  joinScenarioRequested: 'joinScenarioRequested',
  streakLoaded: 'streakLoaded',

  // sim clock
  clockTick: 'clockTick',
  clockControlRequested: 'clockControlRequested',

  // market data
  pricesTick: 'pricesTick',
  quotesLoaded: 'quotesLoaded',
  symbolSelected: 'symbolSelected',
  barsLoaded: 'barsLoaded',

  // trading: ticket, portfolio, blotter
  instrumentChosen: 'instrumentChosen',
  ticketChanged: 'ticketChanged',
  submitOrderRequested: 'submitOrderRequested',
  orderResultReceived: 'orderResultReceived',
  cancelOrderRequested: 'cancelOrderRequested',
  portfolioLoaded: 'portfolioLoaded',
  blotterLoaded: 'blotterLoaded',
  settlementsLoaded: 'settlementsLoaded',
  accountEventReceived: 'accountEventReceived',

  // options chain
  chainUnderlyingSelected: 'chainUnderlyingSelected',
  chainExpirySelected: 'chainExpirySelected',
  chainLoaded: 'chainLoaded',

  // fx sales / trader views
  fxPairsLoaded: 'fxPairsLoaded',
  rfqChanged: 'rfqChanged',
  rfqQuoteRequested: 'rfqQuoteRequested',
  rfqQuoted: 'rfqQuoted',
  rfqExecuteRequested: 'rfqExecuteRequested',
  rfqExecuted: 'rfqExecuted',
  ladderLoaded: 'ladderLoaded',

  // gamification / progress
  progressLoaded: 'progressLoaded',
  missionsLoaded: 'missionsLoaded',

  // instructor
  cohortsLoaded: 'cohortsLoaded',
  createCohortRequested: 'createCohortRequested',
  joinCohortRequested: 'joinCohortRequested',
  cohortJoined: 'cohortJoined',
  leaderboardRequested: 'leaderboardRequested',
  leaderboardLoaded: 'leaderboardLoaded',
} as const;

export const ModelIds = {
  session: 'sessionModel',
  clock: 'clockModel',
  market: 'marketModel',
  trading: 'tradingModel',
  progress: 'progressModel',
  chain: 'chainModel',
  fx: 'fxModel',
  instructor: 'instructorModel',
} as const;
