import React from 'react';
import { fmtMoney, fmtPct, pnlCls } from './common';

/**
 * Renders a signed value with a shape (▲/▼/–) alongside its color, so
 * direction isn't conveyed by color alone (WCAG 1.4.1) — a colorblind
 * student can still tell a gain from a loss without relying on red/green.
 */
export function Pnl({ value, kind = 'money', dp }: {
  value: number | null | undefined; kind?: 'money' | 'pct'; dp?: number;
}) {
  if (value == null) return <span className="text-desk-dim">—</span>;
  const arrow = value > 0 ? '▲' : value < 0 ? '▼' : '–';
  const text = kind === 'pct' ? fmtPct(value, dp) : fmtMoney(value, dp);
  return <span className={pnlCls(value)}>{arrow} {text}</span>;
}
