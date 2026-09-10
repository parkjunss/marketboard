import type { CandleResponse } from './types';

/** Compare observed daily bars, without claiming calendar completeness or finality. */
export function reviewChange(candles: CandleResponse[], intervals: number, yieldIndex = false) {
  const rows = [...candles].sort((a, b) => Date.parse(a.ts) - Date.parse(b.ts));
  if (rows.some((row, i) => !Number.isFinite(Date.parse(row.ts)) ||
    !Number.isFinite(row.close) || row.close <= 0 ||
    (i > 0 && row.ts.slice(0, 10) === rows[i - 1].ts.slice(0, 10)))) return null;
  if (rows.length <= intervals || intervals < 1) return null;
  const latest = rows[rows.length - 1];
  const base = rows[rows.length - 1 - intervals];
  return {
    value: latest.close,
    change: yieldIndex ? (latest.close - base.close) * 100 : (latest.close / base.close - 1) * 100,
    unit: yieldIndex ? 'bp' : '%',
    from: base.ts.slice(0, 10),
    to: latest.ts.slice(0, 10),
  };
}
