import React, { useEffect, useRef } from 'react';
import { createChart, IChartApi, ISeriesApi } from 'lightweight-charts';
import { Bar, EquityPoint } from '../core/types';

const CHART_OPTS = {
  layout: { background: { color: '#161b22' }, textColor: '#8b949e' },
  grid: { vertLines: { color: '#21262d' }, horzLines: { color: '#21262d' } },
  rightPriceScale: { borderColor: '#30363d' },
  timeScale: { borderColor: '#30363d' },
  // explicit locale: the browser's navigator.language can be an invalid BCP-47
  // tag (e.g. "en-US@posix" in some containers) which crashes toLocaleString
  localization: { locale: 'en-US' },
  autoSize: true,
} as const;

export function CandleChart({ bars, height = 320 }: { bars: Bar[]; height?: number }) {
  const el = useRef<HTMLDivElement>(null);
  const chart = useRef<IChartApi | null>(null);
  const series = useRef<ISeriesApi<'Candlestick'> | null>(null);

  useEffect(() => {
    if (!el.current) return;
    chart.current = createChart(el.current, { ...CHART_OPTS, height });
    series.current = chart.current.addCandlestickSeries({
      upColor: '#3fb950', downColor: '#f85149', borderVisible: false,
      wickUpColor: '#3fb950', wickDownColor: '#f85149',
    });
    return () => chart.current?.remove();
  }, [height]);

  useEffect(() => {
    series.current?.setData(bars.map(b => ({
      time: b.time, open: b.open, high: b.high, low: b.low, close: b.close,
    })) as any);
    chart.current?.timeScale().fitContent();
  }, [bars]);

  return <div ref={el} style={{ height }} />;
}

export function EquityChart({ points, height = 200 }: { points: EquityPoint[]; height?: number }) {
  const el = useRef<HTMLDivElement>(null);
  const chart = useRef<IChartApi | null>(null);
  const series = useRef<ISeriesApi<'Line'> | null>(null);

  useEffect(() => {
    if (!el.current) return;
    chart.current = createChart(el.current, { ...CHART_OPTS, height });
    series.current = chart.current.addLineSeries({ color: '#58a6ff', lineWidth: 2 });
    return () => chart.current?.remove();
  }, [height]);

  useEffect(() => {
    series.current?.setData(points.map(p => ({ time: p.simDate, value: p.equity })) as any);
    chart.current?.timeScale().fitContent();
  }, [points]);

  return <div ref={el} style={{ height }} />;
}
