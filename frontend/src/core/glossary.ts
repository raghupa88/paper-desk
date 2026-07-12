/**
 * Plain-language definitions for the jargon this desk surfaces, tied to how
 * Paper Desk actually computes and uses each term (not generic textbook
 * copy) — shown via <InfoTip> next to the abbreviations/labels in tables
 * and the order ticket.
 */
export type TermKey = keyof typeof GLOSSARY;

export const GLOSSARY = {
  delta: {
    title: 'Delta (Δ)',
    description: 'How much the position’s value moves for a $1 move in the underlying. '
      + 'A delta of 0.50 means the option gains about $0.50 per $1 the stock rises.',
  },
  gamma: {
    title: 'Gamma (Γ)',
    description: 'How fast delta itself changes as the underlying moves. High gamma means delta '
      + 'can swing quickly — common for at-the-money options close to expiry.',
  },
  theta: {
    title: 'Theta (Θ) — time decay',
    description: 'How much value the position loses per sim day just from time passing, everything '
      + 'else held constant. Usually negative for a long option — the clock is working against you.',
  },
  vega: {
    title: 'Vega',
    description: 'Sensitivity to volatility. A vega of 0.10 means the position gains about $0.10 in '
      + 'value for each 1 percentage point rise in the underlying’s simulated volatility.',
  },
  initialMargin: {
    title: 'Initial margin',
    description: 'Cash set aside as collateral when you open a futures position — not a fee. '
      + 'It’s released back to your cash balance when you close the position.',
  },
  maintenanceMargin: {
    title: 'Maintenance margin',
    description: 'The minimum equity a futures position must keep. If daily losses push your account '
      + 'below this, you get a margin call and the position may be force-closed.',
  },
  markToMarket: {
    title: 'Mark-to-market',
    description: 'A futures position’s daily settlement: gains and losses versus the previous close '
      + 'are calculated and posted to your cash every sim day, not only when you close the trade.',
  },
  premium: {
    title: 'Premium',
    description: 'The price to buy or sell an option, shown as bid (what you’d receive selling now) '
      + 'and ask (what you’d pay buying now).',
  },
  notional: {
    title: 'Notional',
    description: 'The face value a swap’s cash flows are calculated on. No principal actually changes '
      + 'hands — only the periodic difference between the fixed and floating rate is paid.',
  },
  fixedFloating: {
    title: 'Pay fixed / receive floating',
    description: 'In this swap, buying means you pay the fixed rate and receive the floating (index) '
      + 'rate each period — the classic ‘pay fixed’ position, a bet that rates will rise.',
  },
  winRate: {
    title: 'Win rate',
    description: 'The share of your closed trades that made money. A closing or flipping fill counts '
      + 'as one trade — a losing trade with a small loss can still beat a rare, unusually large win.',
  },
  maxDrawdown: {
    title: 'Max drawdown',
    description: 'The largest drop in your account equity from a prior peak to a later low, measured '
      + 'across your daily equity history. A common risk gauge alongside total return.',
  },
} as const;
