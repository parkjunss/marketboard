import type { MomentumPeriod, ScreenerSnapshotItem, ScreenerSortField } from '@/lib/screener-types';

export const PERIOD_OPTIONS: { value: MomentumPeriod; label: string; field: ScreenerSortField }[] = [
  { value: 'THREE_MONTHS', label: '3개월', field: 'MOMENTUM_3M' },
  { value: 'SIX_MONTHS', label: '6개월', field: 'MOMENTUM_6M' },
  { value: 'TWELVE_MONTHS', label: '12개월', field: 'MOMENTUM_12M' },
];

export function momentumFor(item: ScreenerSnapshotItem, period: MomentumPeriod) {
  if (period === 'THREE_MONTHS') return item.momentum3m;
  if (period === 'TWELVE_MONTHS') return item.momentum12m;
  return item.momentum6m;
}

export function formatPercent(value: number | null) {
  return value == null ? '—' : `${value >= 0 ? '+' : ''}${value.toFixed(2)}%`;
}

export function formatUsd(value: number | null) {
  if (value == null) return '—';
  if (value >= 1_000_000_000_000) return `$${(value / 1_000_000_000_000).toFixed(2)}T`;
  if (value >= 1_000_000_000) return `$${(value / 1_000_000_000).toFixed(1)}B`;
  if (value >= 1_000_000) return `$${(value / 1_000_000).toFixed(1)}M`;
  return `$${value.toLocaleString('en-US', { maximumFractionDigits: 0 })}`;
}
