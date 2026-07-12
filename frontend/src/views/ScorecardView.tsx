import React, { useEffect } from 'react';
import { useServices } from '../AppContext';
import { ModelIds } from '../core/events';
import { TermKey } from '../core/glossary';
import { TradingModel, TradingState } from '../models/TradingModel';
import { useModelState, fmtMoney, pnlCls } from './common';
import { InfoTip } from './InfoTip';

const fmtHoldingPeriod = (hours: number) =>
  hours < 24 ? `${hours.toFixed(1)}h` : `${(hours / 24).toFixed(1)}d`;

function StatCard({ label, value, valueCls, term }:
  { label: string; value: string; valueCls?: string; term?: TermKey }) {
  return (
    <div className="panel">
      <div className="text-xs text-desk-dim uppercase tracking-wide flex items-center">
        {label}{term && <InfoTip term={term} />}
      </div>
      <div className={`text-2xl font-semibold num mt-1 ${valueCls ?? 'text-desk-text'}`}>{value}</div>
    </div>
  );
}

export function ScorecardView() {
  const { dataService } = useServices();
  const state = useModelState<TradingModel, TradingState>(ModelIds.trading, m => m.state);
  const card = state.scorecard;

  useEffect(() => { void dataService.refreshScorecard(); }, [dataService]);

  if (!card) return <div className="panel text-desk-dim">Loading scorecard…</div>;

  if (card.totalTrades === 0) {
    return (
      <div className="panel text-desk-dim">
        No closed trades yet. Your scorecard fills in once you close or flip a position —
        win rate, average win/loss, holding period, and max drawdown all come from that history.
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3">
        <StatCard label="Closed trades" value={String(card.totalTrades)} />
        <StatCard label="Win rate" value={`${card.winRatePct.toFixed(1)}%`} term="winRate"
                  valueCls={pnlCls(card.winRatePct - 50)} />
        <StatCard label="Wins / losses" value={`${card.wins} / ${card.losses}`} />
        <StatCard label="Avg win" value={fmtMoney(card.avgWin)} valueCls="text-desk-up" />
        <StatCard label="Avg loss" value={fmtMoney(card.avgLoss)} valueCls="text-desk-down" />
        <StatCard label="Avg holding period" value={fmtHoldingPeriod(card.avgHoldingPeriodHours)} />
      </div>
      <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3">
        <StatCard label="Max drawdown" value={fmtMoney(card.maxDrawdown, 0)} valueCls="text-desk-down" term="maxDrawdown" />
        <StatCard label="Max drawdown %" value={`${card.maxDrawdownPct.toFixed(1)}%`} valueCls="text-desk-down" />
      </div>
    </div>
  );
}
