'use client';

import { useEffect, useRef } from 'react';
import {
  createChart,
  CandlestickSeries,
  ColorType,
  LineSeries,
  type IChartApi,
  type ISeriesApi,
  type UTCTimestamp,
} from 'lightweight-charts';
import { Card } from '@astryxdesign/core/Card';
import { useTheme } from '@astryxdesign/core/theme';
import type { CandleResponse } from '@/lib/types';

export interface SmaOverlay {
  period: number;
  /** A theme token key (e.g. `--color-icon-blue`), resolved via `useTheme().tokens` — canvas
   * rendering can't resolve `var(...)` references the way DOM/SVG styles can. */
  color: string;
  label: string;
}

interface CandleChartProps {
  candles: CandleResponse[];
  height?: number;
  smaOverlays?: SmaOverlay[];
}

function computeSma(candles: CandleResponse[], period: number): (number | null)[] {
  return candles.map((_, index) => {
    if (index < period - 1) return null;
    let sum = 0;
    for (let i = index - period + 1; i <= index; i += 1) {
      sum += candles[i].close;
    }
    return sum / period;
  });
}

export function CandleChart({ candles, height = 420, smaOverlays = [] }: CandleChartProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const seriesRef = useRef<ISeriesApi<'Candlestick'> | null>(null);
  const overlaySeriesRef = useRef<ISeriesApi<'Line'>[]>([]);
  const { tokens } = useTheme();

  useEffect(() => {
    if (!containerRef.current) return undefined;

    const chart: IChartApi = createChart(containerRef.current, {
      layout: {
        background: { type: ColorType.Solid, color: 'transparent' },
        textColor: tokens['--color-text-primary'],
      },
      grid: {
        vertLines: { color: tokens['--color-border'] },
        horzLines: { color: tokens['--color-border'] },
      },
      timeScale: { borderColor: tokens['--color-border'] },
      rightPriceScale: { borderColor: tokens['--color-border'] },
      autoSize: true,
    });

    const series = chart.addSeries(CandlestickSeries, {
      upColor: tokens['--color-success'],
      downColor: tokens['--color-error'],
      borderVisible: false,
      wickUpColor: tokens['--color-success'],
      wickDownColor: tokens['--color-error'],
    });
    seriesRef.current = series;

    overlaySeriesRef.current = smaOverlays.map((overlay) =>
      chart.addSeries(LineSeries, {
        color: tokens[overlay.color] ?? overlay.color,
        lineWidth: 2,
        title: overlay.label,
        priceLineVisible: false,
        lastValueVisible: false,
      }),
    );

    return () => {
      chart.remove();
      seriesRef.current = null;
      overlaySeriesRef.current = [];
    };
    // smaOverlays intentionally excluded: it's expected to be a stable (module-level or memoized)
    // reference from the caller — recreating the chart on every render would defeat autoSize.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tokens]);

  useEffect(() => {
    if (!seriesRef.current) return;
    seriesRef.current.setData(
      candles.map((candle) => ({
        time: (new Date(candle.ts).getTime() / 1000) as UTCTimestamp,
        open: candle.open,
        high: candle.high,
        low: candle.low,
        close: candle.close,
      })),
    );

    smaOverlays.forEach((overlay, index) => {
      const sma = computeSma(candles, overlay.period);
      overlaySeriesRef.current[index]?.setData(
        candles
          .map((candle, i) => ({ time: (new Date(candle.ts).getTime() / 1000) as UTCTimestamp, value: sma[i] }))
          .filter((point): point is { time: UTCTimestamp; value: number } => point.value != null),
      );
    });
  }, [candles, smaOverlays]);

  return <Card ref={containerRef} padding={0} height={height} width="100%" />;
}
