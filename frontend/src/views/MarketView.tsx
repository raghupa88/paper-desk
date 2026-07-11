import React, { useEffect } from 'react';
import { usePublishEvent } from 'esp-js-react';
import { useServices } from '../AppContext';
import { EventConst, ModelIds } from '../core/events';
import { MarketModel, MarketState } from '../models/MarketModel';
import { Quote } from '../core/types';
import { useModelState, fmtNum } from './common';
import { CandleChart } from './ChartView';
import { TicketView } from './TicketView';

const GROUPS: Array<{ label: string; types: string[] }> = [
  { label: 'Equities', types: ['EQUITY'] },
  { label: 'FX', types: ['FX_PAIR'] },
  { label: 'Futures', types: ['FUTURE'] },
  { label: 'Forwards', types: ['FORWARD'] },
  { label: 'Swaps', types: ['SWAP'] },
];

export function MarketView() {
  const { dataService } = useServices();
  const publish = usePublishEvent();
  const market = useModelState<MarketModel, MarketState>(ModelIds.market, m => m.state);

  const selected = market.selectedSymbol;
  useEffect(() => {
    if (!selected) return;
    void dataService.loadBars(selected);
    const t = window.setInterval(() => void dataService.loadBars(selected), 5000);
    return () => window.clearInterval(t);
  }, [selected, dataService]);

  const pick = (q: Quote) => {
    const chartSymbol = q.type === 'EQUITY' || q.type === 'FX_PAIR' ? q.symbol : null;
    if (chartSymbol) publish(ModelIds.market, EventConst.symbolSelected, { symbol: chartSymbol });
    publish(ModelIds.trading, EventConst.instrumentChosen, { instrument: q });
  };

  return (
    <div className="flex flex-col lg:flex-row gap-4 items-start">
      <div className="panel w-full lg:w-[430px] lg:shrink-0 max-h-72 lg:max-h-[calc(100vh-140px)] overflow-auto">
        <div className="panel-title">Watchlist — live simulated prices</div>
        {GROUPS.map(g => {
          const rows = market.quotes.filter(q => g.types.includes(q.type));
          if (rows.length === 0) return null;
          return (
            <div key={g.label}>
              <div className="px-3 py-1 text-[10px] uppercase tracking-wider text-desk-dim bg-desk-bg/50">{g.label}</div>
              <table className="tbl">
                <tbody>
                  {rows.map(q => (
                    <tr key={q.instrumentId}
                        className={`cursor-pointer hover:bg-desk-bg/60 ${selected === q.symbol ? 'bg-desk-bg' : ''}`}
                        onClick={() => pick(q)}>
                      <td className="font-medium">{q.symbol}</td>
                      <td className="num text-right text-desk-down">{fmtNum(q.bid)}</td>
                      <td className="num text-right">{fmtNum(q.mid)}</td>
                      <td className="num text-right text-desk-up">{fmtNum(q.ask)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          );
        })}
        {market.quotes.length === 0 && <div className="p-4 text-desk-dim text-sm">Loading quotes…</div>}
      </div>

      <div className="panel flex-1 p-3">
        <div className="panel-title !p-0 mb-2">
          {selected ? `${selected} — daily candles (sim days)` : 'Select a symbol'}
        </div>
        <CandleChart bars={market.bars} />
        {market.bars.length === 0 && (
          <div className="text-desk-dim text-xs mt-2">
            No completed sim days yet — bars appear as sim days close (use ⏭ +1 day to fast-forward).
          </div>
        )}
      </div>

      <TicketView />
    </div>
  );
}
