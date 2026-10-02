'use client';

import { useEffect, useRef } from 'react';
import {
  createChart,
  CandlestickSeries,
  ColorType,
  HistogramSeries,
  LineSeries,
  type IChartApi,
  type ISeriesApi,
  type UTCTimestamp,
} from 'lightweight-charts';
import { Card } from '@astryxdesign/core/Card';
import { useTheme } from '@astryxdesign/core/theme';
import type { CandleResponse } from '@/lib/types';

const UP_COLOR = '#00e5a0';
const DOWN_COLOR = '#ff5c67';

export interface SmaOverlay {
  period: number;
  /** Theme token key. Canvas rendering cannot resolve CSS var(...) references. */
  color: string;
  label: string;
}

export interface TechnicalChartSettings {
  emaPeriod?: number;
  bollingerPeriod?: number;
  rsiPeriod?: number;
  macd?: { fast: number; slow: number; signal: number };
  atrPeriod?: number;
  relativeVolumePeriod?: number;
}

interface CandleChartProps {
  candles: CandleResponse[];
  height?: number;
  smaOverlays?: SmaOverlay[];
  indicators?: TechnicalChartSettings;
}

function computeSma(values: number[], period: number): (number | null)[] {
  if (period < 1) return Array<number | null>(values.length).fill(null);
  let sum = 0;
  return values.map((value, index) => {
    sum += value;
    if (index >= period) sum -= values[index - period];
    return index < period - 1 ? null : sum / period;
  });
}

function computeEma(values: number[], period: number): (number | null)[] {
  const result = Array<number | null>(values.length).fill(null);
  if (period < 1 || values.length < period) return result;
  let current = values.slice(0, period).reduce((sum, value) => sum + value, 0) / period;
  result[period - 1] = current;
  const alpha = 2 / (period + 1);
  for (let i = period; i < values.length; i += 1) {
    current = alpha * values[i] + (1 - alpha) * current;
    result[i] = current;
  }
  return result;
}

function computeRsi(values: number[], period: number): (number | null)[] {
  const result = Array<number | null>(values.length).fill(null);
  if (period < 1 || values.length < period + 1) return result;
  let gain = 0;
  let loss = 0;
  for (let i = 1; i <= period; i += 1) {
    const change = values[i] - values[i - 1];
    gain += Math.max(change, 0);
    loss += Math.max(-change, 0);
  }
  let averageGain = gain / period;
  let averageLoss = loss / period;
  const value = () =>
    averageGain === 0 && averageLoss === 0 ? 50 : averageLoss === 0 ? 100 : 100 - 100 / (1 + averageGain / averageLoss);
  result[period] = value();
  for (let i = period + 1; i < values.length; i += 1) {
    const change = values[i] - values[i - 1];
    averageGain = (averageGain * (period - 1) + Math.max(change, 0)) / period;
    averageLoss = (averageLoss * (period - 1) + Math.max(-change, 0)) / period;
    result[i] = value();
  }
  return result;
}

function computeMacd(values: number[], fast: number, slow: number, signal: number) {
  const fastValues = computeEma(values, fast);
  const slowValues = computeEma(values, slow);
  const line = values.map((_, index) =>
    fastValues[index] == null || slowValues[index] == null ? null : fastValues[index] - slowValues[index],
  );
  const available = line.filter((item): item is number => item != null);
  const signalValues = computeEma(available, signal);
  const signalLine = Array<number | null>(values.length).fill(null);
  signalValues.forEach((item, index) => {
    signalLine[slow - 1 + index] = item;
  });
  return {
    line,
    signal: signalLine,
    histogram: line.map((item, index) => (item == null || signalLine[index] == null ? null : item - signalLine[index])),
  };
}

function computeBollinger(values: number[], period: number) {
  if (period < 2) return values.map(() => null);
  const middle = computeSma(values, period);
  return values.map((_, index) => {
    if (index < period - 1 || middle[index] == null) return null;
    const window = values.slice(index - period + 1, index + 1);
    const variance = window.reduce((sum, item) => sum + (item - middle[index]!) ** 2, 0) / (period - 1);
    const distance = 2 * Math.sqrt(variance);
    return { middle: middle[index]!, upper: middle[index]! + distance, lower: middle[index]! - distance };
  });
}

function computeAtr(candles: CandleResponse[], period: number): (number | null)[] {
  const result = Array<number | null>(candles.length).fill(null);
  if (period < 1 || candles.length < period) return result;
  const ranges = candles.map((candle, index) =>
    index === 0
      ? candle.high - candle.low
      : Math.max(
          candle.high - candle.low,
          Math.abs(candle.high - candles[index - 1].close),
          Math.abs(candle.low - candles[index - 1].close),
        ),
  );
  let current = ranges.slice(0, period).reduce((sum, item) => sum + item, 0) / period;
  result[period - 1] = current;
  for (let i = period; i < ranges.length; i += 1) {
    current = (current * (period - 1) + ranges[i]) / period;
    result[i] = current;
  }
  return result;
}

function points(candles: CandleResponse[], values: (number | null)[]) {
  return candles
    .map((candle, index) => ({ time: (new Date(candle.ts).getTime() / 1000) as UTCTimestamp, value: values[index] }))
    .filter((point): point is { time: UTCTimestamp; value: number } => point.value != null);
}

export function CandleChart({ candles, height = 420, smaOverlays = [], indicators = {} }: CandleChartProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const candleSeriesRef = useRef<ISeriesApi<'Candlestick'> | null>(null);
  const smaSeriesRef = useRef<ISeriesApi<'Line'>[]>([]);
  const emaSeriesRef = useRef<ISeriesApi<'Line'> | null>(null);
  const bollingerSeriesRef = useRef<ISeriesApi<'Line'>[]>([]);
  const rsiSeriesRef = useRef<ISeriesApi<'Line'> | null>(null);
  const macdSeriesRef = useRef<{
    line: ISeriesApi<'Line'>;
    signal: ISeriesApi<'Line'>;
    histogram: ISeriesApi<'Histogram'>;
  } | null>(null);
  const atrSeriesRef = useRef<ISeriesApi<'Line'> | null>(null);
  const volumeSeriesRef = useRef<ISeriesApi<'Histogram'> | null>(null);
  const { tokens } = useTheme();

  useEffect(() => {
    if (!containerRef.current) return undefined;
    const chart: IChartApi = createChart(containerRef.current, {
      layout: { background: { type: ColorType.Solid, color: 'transparent' }, textColor: tokens['--color-text-primary'] },
      grid: { vertLines: { color: tokens['--color-border'] }, horzLines: { color: tokens['--color-border'] } },
      timeScale: { borderColor: tokens['--color-border'] },
      rightPriceScale: { borderColor: tokens['--color-border'] },
      autoSize: true,
    });
    chart.panes()[0].setStretchFactor(3);
    candleSeriesRef.current = chart.addSeries(CandlestickSeries, {
      upColor: UP_COLOR,
      downColor: DOWN_COLOR,
      borderVisible: true,
      borderUpColor: UP_COLOR,
      borderDownColor: DOWN_COLOR,
      wickUpColor: UP_COLOR,
      wickDownColor: DOWN_COLOR,
    });
    smaSeriesRef.current = smaOverlays.map((overlay) =>
      chart.addSeries(LineSeries, {
        color: tokens[overlay.color] ?? overlay.color,
        lineWidth: 2,
        title: overlay.label,
      }),
    );
    if (indicators.emaPeriod) {
      emaSeriesRef.current = chart.addSeries(LineSeries, {
        color: tokens['--color-icon-teal'], lineWidth: 2, title: `EMA${indicators.emaPeriod}`,
      });
    }
    if (indicators.bollingerPeriod) {
      bollingerSeriesRef.current = ['상단', '중심', '하단'].map((label, index) =>
        chart.addSeries(LineSeries, {
          color: index === 1 ? tokens['--color-icon-purple'] : tokens['--color-icon-blue'],
          lineWidth: index === 1 ? 1 : 2,
          title: `BB ${label}`,
        }),
      );
    }
    if (indicators.rsiPeriod) {
      rsiSeriesRef.current = chart.addPane().addSeries(LineSeries, {
        color: tokens['--color-icon-purple'], lineWidth: 2, title: `RSI${indicators.rsiPeriod}`,
      });
    }
    if (indicators.macd) {
      const pane = chart.addPane();
      macdSeriesRef.current = {
        line: pane.addSeries(LineSeries, { color: tokens['--color-icon-blue'], lineWidth: 2, title: 'MACD' }),
        signal: pane.addSeries(LineSeries, { color: tokens['--color-icon-orange'], lineWidth: 2, title: 'Signal' }),
        histogram: pane.addSeries(HistogramSeries, { color: tokens['--color-icon-teal'], title: 'Histogram' }),
      };
    }
    if (indicators.atrPeriod) {
      atrSeriesRef.current = chart.addPane().addSeries(LineSeries, {
        color: tokens['--color-icon-orange'], lineWidth: 2, title: `ATR${indicators.atrPeriod}`,
      });
    }
    if (indicators.relativeVolumePeriod) {
      volumeSeriesRef.current = chart.addPane().addSeries(HistogramSeries, {
        color: tokens['--color-icon-teal'], title: `상대 거래량 ${indicators.relativeVolumePeriod}`,
      });
    }

    return () => {
      chart.remove();
      candleSeriesRef.current = null;
      smaSeriesRef.current = [];
      emaSeriesRef.current = null;
      bollingerSeriesRef.current = [];
      rsiSeriesRef.current = null;
      macdSeriesRef.current = null;
      atrSeriesRef.current = null;
      volumeSeriesRef.current = null;
    };
    // Configuration changes remount this component through its parent key.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tokens]);

  useEffect(() => {
    candleSeriesRef.current?.setData(
      candles.map((candle) => ({
        time: (new Date(candle.ts).getTime() / 1000) as UTCTimestamp,
        open: candle.open, high: candle.high, low: candle.low, close: candle.close,
      })),
    );
    const closes = candles.map((candle) => candle.close);
    smaOverlays.forEach((overlay, index) =>
      smaSeriesRef.current[index]?.setData(points(candles, computeSma(closes, overlay.period))),
    );
    if (indicators.emaPeriod) emaSeriesRef.current?.setData(points(candles, computeEma(closes, indicators.emaPeriod)));
    if (indicators.bollingerPeriod) {
      const bands = computeBollinger(closes, indicators.bollingerPeriod);
      const keys = ['upper', 'middle', 'lower'] as const;
      keys.forEach((key, index) =>
        bollingerSeriesRef.current[index]?.setData(points(candles, bands.map((band) => band?.[key] ?? null))),
      );
    }
    if (indicators.rsiPeriod) rsiSeriesRef.current?.setData(points(candles, computeRsi(closes, indicators.rsiPeriod)));
    if (indicators.macd && macdSeriesRef.current) {
      const values = computeMacd(closes, indicators.macd.fast, indicators.macd.slow, indicators.macd.signal);
      macdSeriesRef.current.line.setData(points(candles, values.line));
      macdSeriesRef.current.signal.setData(points(candles, values.signal));
      macdSeriesRef.current.histogram.setData(points(candles, values.histogram));
    }
    if (indicators.atrPeriod) atrSeriesRef.current?.setData(points(candles, computeAtr(candles, indicators.atrPeriod)));
    if (indicators.relativeVolumePeriod) {
      const averages = computeSma(candles.map((candle) => candle.volume ?? 0), indicators.relativeVolumePeriod);
      const relative = candles.map((candle, index) =>
        index === 0 || averages[index - 1] == null || averages[index - 1] === 0
          ? null
          : (candle.volume ?? 0) / averages[index - 1]!,
      );
      volumeSeriesRef.current?.setData(
        candles
          .map((candle, index) => ({
            time: (new Date(candle.ts).getTime() / 1000) as UTCTimestamp,
            value: relative[index],
            color: candle.close >= candle.open ? UP_COLOR : DOWN_COLOR,
          }))
          .filter((point): point is { time: UTCTimestamp; value: number; color: string } => point.value != null),
      );
    }
  }, [candles, indicators, smaOverlays]);

  return <Card ref={containerRef} padding={0} height={height} width="100%" />;
}
