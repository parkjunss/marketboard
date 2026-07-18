import { useEffect, useMemo, useState } from 'react';
import * as api from './api';
import type { Fetcher } from './api';
import type { CandleResponse, QuoteResponse } from './types';

export type Timeframe = '1d' | '1m';

export function bucketStart(epochMs: number, timeframe: Timeframe): number {
  const date = new Date(epochMs);
  if (timeframe === '1m') {
    date.setSeconds(0, 0);
  } else {
    date.setHours(0, 0, 0, 0);
  }
  return date.getTime();
}

export function mergeLiveTick(
  candles: CandleResponse[],
  price: number,
  tickIso: string,
  timeframe: Timeframe,
): CandleResponse[] {
  const bucket = bucketStart(new Date(tickIso).getTime(), timeframe);
  const bucketIso = new Date(bucket).toISOString();

  if (candles.length === 0) {
    return [{ ts: bucketIso, open: price, high: price, low: price, close: price, volume: 0 }];
  }

  const last = candles[candles.length - 1];
  const lastBucket = bucketStart(new Date(last.ts).getTime(), timeframe);

  if (lastBucket === bucket) {
    return [
      ...candles.slice(0, -1),
      { ...last, high: Math.max(last.high, price), low: Math.min(last.low, price), close: price },
    ];
  }
  if (bucket > lastBucket) {
    return [...candles, { ts: bucketIso, open: price, high: price, low: price, close: price, volume: 0 }];
  }
  return candles;
}

/**
 * Fetches history for (ticker, timeframe) and keeps it live by merging the latest quote tick into
 * the current bucket. Shared by the symbol detail page and the dashboard's chart panel.
 */
export function useCandles(
  fetcher: Fetcher,
  ticker: string,
  timeframe: Timeframe,
  liveQuote: QuoteResponse | undefined,
): { candles: CandleResponse[]; isLoading: boolean } {
  // Keyed by `${ticker}:${timeframe}` so isLoading derives from render-time comparison instead of
  // an effect calling setState synchronously (see react-hooks/set-state-in-effect).
  const requestKey = `${ticker}:${timeframe}`;
  const [history, setHistory] = useState<{ key: string; candles: CandleResponse[] } | null>(null);
  const isLoading = history?.key !== requestKey;

  useEffect(() => {
    if (!ticker) return undefined;
    let cancelled = false;
    api
      .getHistory(fetcher, ticker, timeframe, timeframe === '1m' ? 300 : 200)
      .then((data) => {
        if (!cancelled) setHistory({ key: requestKey, candles: data });
      })
      .catch(() => {
        // Unknown/invalid ticker — caller renders its own empty/error state for zero candles.
        if (!cancelled) setHistory({ key: requestKey, candles: [] });
      });
    return () => {
      cancelled = true;
    };
  }, [fetcher, ticker, timeframe, requestKey]);

  const candles = useMemo(() => {
    const fetchedCandles = history?.key === requestKey ? history.candles : [];
    if (!liveQuote || liveQuote.price == null || !liveQuote.ts) return fetchedCandles;
    return mergeLiveTick(fetchedCandles, liveQuote.price, liveQuote.ts, timeframe);
  }, [history, requestKey, liveQuote, timeframe]);

  return { candles, isLoading };
}
