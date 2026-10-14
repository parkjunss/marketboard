import type { CandleResponse } from './types';

export function formatCandleInfo(candle: CandleResponse | undefined, previousClose?: number): string {
  if (!candle) return '봉 데이터가 없습니다';
  const number = (value: number | undefined) =>
    value != null && Number.isFinite(value) ? value.toLocaleString('ko-KR', { maximumFractionDigits: 4 }) : '—';
  const change = previousClose != null && previousClose > 0 && Number.isFinite(previousClose)
    ? (candle.close / previousClose - 1) * 100 : null;
  const changeText = change != null && Number.isFinite(change)
    ? `${change > 0 ? '+' : ''}${change.toFixed(2)}%` : '—';
  const date = new Date(candle.ts);
  const time = Number.isFinite(date.getTime())
    ? `${date.toLocaleString('ko-KR', { timeZone: 'UTC', hour12: false })} UTC` : '시각 미상';
  return `${time} · 시가 ${number(candle.open)} · 고가 ${number(candle.high)} · 저가 ${number(candle.low)} · 종가 ${number(candle.close)} · 전봉 대비 ${changeText} · 거래량 ${number(candle.volume)}`;
}
