import React, { useEffect, useState } from 'react';
import { ModelIds } from '../core/events';
import { TradingModel, Notification } from '../models/TradingModel';
import { useModelState } from './common';

const LABELS: Record<string, { title: string; cls: string; icon: string }> = {
  ACHIEVEMENT: { title: 'Achievement unlocked!', cls: 'border-desk-warn text-desk-warn', icon: '🏆' },
  MISSION_COMPLETE: { title: 'Mission complete!', cls: 'border-desk-up text-desk-up', icon: '🎯' },
  FILL: { title: 'Order filled', cls: 'border-desk-up text-desk-up', icon: '✓' },
  MARGIN_CALL: { title: 'Margin call', cls: 'border-desk-down text-desk-down', icon: '⚠' },
  SETTLEMENT: { title: 'Settlement', cls: 'border-desk-accent text-desk-accent', icon: '⚖' },
  ORDER_REJECTED: { title: 'Order rejected', cls: 'border-desk-down text-desk-down', icon: '✕' },
  ORDER_CANCELLED: { title: 'Order cancelled', cls: 'border-desk-border text-desk-dim', icon: '—' },
};

/**
 * Global toast feed for desk events (fills, achievements, margin calls,
 * settlements) — mounted once in the shell so the experience is consistent on
 * every tab. Reads the trading model's notification stream; visibility is
 * purely local (auto-hide after 6s).
 */
export function ToastHost() {
  const notifications = useModelState<TradingModel, Notification[]>(
    ModelIds.trading, m => m.state.notifications);
  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    const t = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(t);
  }, []);

  const visible = notifications.filter(n => now - n.at < 6000).slice(0, 4);
  if (visible.length === 0) return null;

  return (
    <div className="fixed top-14 right-4 z-50 space-y-2 w-96">
      {visible.map(n => {
        const meta = LABELS[n.type] ?? { title: n.type, cls: 'border-desk-border', icon: '•' };
        return (
          <div key={n.at + n.type}
               className={`panel border-l-4 px-4 py-2 shadow-lg ${meta.cls}`}>
            <div className="font-semibold text-sm">{meta.icon} {meta.title}</div>
            <div className="text-xs text-desk-text mt-0.5">{n.detail}</div>
          </div>
        );
      })}
    </div>
  );
}
