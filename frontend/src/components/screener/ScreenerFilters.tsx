import { AdjustmentsHorizontalIcon, ArrowPathIcon, MagnifyingGlassIcon } from '@heroicons/react/24/outline';
import type { MomentumPeriod, ScreenerSearchRequest } from '@/lib/screener-types';
import { PERIOD_OPTIONS } from './screener-format';
import styles from './screener.module.css';

export interface ScreenerFilterValues {
  momentumPeriod: MomentumPeriod;
  minMomentumPct: string;
  maxRsi: string;
  aboveSma200: string;
  aboveEma20: string;
  minMacdHistogram: string;
  minBollingerPercentB: string;
  maxBollingerPercentB: string;
  maxAtrPct: string;
  minRelativeVolume: string;
  minMarketCapB: string;
  minRevenueB: string;
  minRevenueGrowth: string;
  minRoe: string;
  maxTrailingPe: string;
  minNewsSentiment: string;
}

export const DEFAULT_FILTERS: ScreenerFilterValues = {
  momentumPeriod: 'SIX_MONTHS', minMomentumPct: '10', maxRsi: '70', aboveSma200: 'true',
  aboveEma20: '', minMacdHistogram: '', minBollingerPercentB: '', maxBollingerPercentB: '',
  maxAtrPct: '', minRelativeVolume: '',
  minMarketCapB: '10', minRevenueB: '', minRevenueGrowth: '', minRoe: '', maxTrailingPe: '', minNewsSentiment: '',
};

function optionalNumber(value: string) {
  return value === '' ? undefined : Number(value);
}

export function toSearchRequest(values: ScreenerFilterValues): ScreenerSearchRequest {
  const period = PERIOD_OPTIONS.find((option) => option.value === values.momentumPeriod)!;
  const marketCapB = optionalNumber(values.minMarketCapB);
  const revenueB = optionalNumber(values.minRevenueB);
  return {
    momentumPeriod: values.momentumPeriod,
    minMomentumPct: optionalNumber(values.minMomentumPct),
    maxRsi: optionalNumber(values.maxRsi),
    aboveSma200: values.aboveSma200 === '' ? undefined : values.aboveSma200 === 'true',
    aboveEma20: values.aboveEma20 === '' ? undefined : values.aboveEma20 === 'true',
    minMacdHistogram: optionalNumber(values.minMacdHistogram),
    minBollingerPercentB: optionalNumber(values.minBollingerPercentB),
    maxBollingerPercentB: optionalNumber(values.maxBollingerPercentB),
    maxAtrPct: optionalNumber(values.maxAtrPct),
    minRelativeVolume: optionalNumber(values.minRelativeVolume),
    minMarketCap: marketCapB == null ? undefined : marketCapB * 1_000_000_000,
    minRevenue: revenueB == null ? undefined : revenueB * 1_000_000_000,
    minRevenueGrowth: optionalNumber(values.minRevenueGrowth),
    minRoe: optionalNumber(values.minRoe),
    maxTrailingPe: optionalNumber(values.maxTrailingPe),
    minNewsSentiment: optionalNumber(values.minNewsSentiment),
    sort: { field: period.field, direction: 'DESC' }, page: 0, size: 20,
  };
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return <label className={styles.field}><span>{label}</span>{children}</label>;
}

export function ScreenerFilters({ values, onChange, onReset, onSubmit, isLoading }: {
  values: ScreenerFilterValues;
  onChange: (values: ScreenerFilterValues) => void;
  onReset: () => void;
  onSubmit: () => void;
  isLoading: boolean;
}) {
  const update = (key: keyof ScreenerFilterValues, value: string) => onChange({ ...values, [key]: value });
  return (
    <section className={styles.panel}>
      <div className={styles.panelHeader}>
        <h2><AdjustmentsHorizontalIcon /> 필터 조건</h2>
        <div className={styles.actions}>
          <button className={styles.secondaryButton} onClick={onReset} type="button"><ArrowPathIcon /> 조건 초기화</button>
          <button className={styles.primaryButton} onClick={onSubmit} disabled={isLoading} type="button"><MagnifyingGlassIcon /> {isLoading ? '검색 중' : '검색하기'}</button>
        </div>
      </div>
      <div className={styles.filterGrid}>
        <Field label="모멘텀 기간"><select value={values.momentumPeriod} onChange={(e) => update('momentumPeriod', e.target.value)}>{PERIOD_OPTIONS.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}</select></Field>
        <Field label="최소 모멘텀 수익률 (%)"><input type="number" value={values.minMomentumPct} onChange={(e) => update('minMomentumPct', e.target.value)} /></Field>
        <Field label="RSI 최댓값"><input type="number" min="0" max="100" value={values.maxRsi} onChange={(e) => update('maxRsi', e.target.value)} /></Field>
        <Field label="200일 이동평균 위 위치"><select value={values.aboveSma200} onChange={(e) => update('aboveSma200', e.target.value)}><option value="">전체</option><option value="true">예 (위에 있음)</option><option value="false">아니요 (아래에 있음)</option></select></Field>
        <Field label="최소 시가총액 (USD)"><select value={values.minMarketCapB} onChange={(e) => update('minMarketCapB', e.target.value)}><option value="">전체</option><option value="1">10억 (1B)</option><option value="10">100억 (10B)</option><option value="50">500억 (50B)</option><option value="100">1,000억 (100B)</option></select></Field>
        <Field label="최소 매출 (USD B)"><input type="number" min="0" value={values.minRevenueB} placeholder="전체" onChange={(e) => update('minRevenueB', e.target.value)} /></Field>
      </div>
      <details className={styles.advanced}>
        <summary>고급 필터 (추가 조건)</summary>
        <div className={styles.filterGrid}>
          <Field label="최소 매출 성장률 (%)"><input type="number" value={values.minRevenueGrowth} onChange={(e) => update('minRevenueGrowth', e.target.value)} /></Field>
          <Field label="20일 EMA 위 위치"><select value={values.aboveEma20} onChange={(e) => update('aboveEma20', e.target.value)}><option value="">전체</option><option value="true">예 (위에 있음)</option><option value="false">아니요 (아래에 있음)</option></select></Field>
          <Field label="최소 MACD 히스토그램"><input type="number" step="0.01" value={values.minMacdHistogram} placeholder="전체" onChange={(e) => update('minMacdHistogram', e.target.value)} /></Field>
          <Field label="최소 볼린저 %B"><input type="number" step="0.1" value={values.minBollingerPercentB} placeholder="예: 0" onChange={(e) => update('minBollingerPercentB', e.target.value)} /></Field>
          <Field label="최대 볼린저 %B"><input type="number" step="0.1" value={values.maxBollingerPercentB} placeholder="예: 1" onChange={(e) => update('maxBollingerPercentB', e.target.value)} /></Field>
          <Field label="최대 ATR (14, %)"><input type="number" min="0" step="0.1" value={values.maxAtrPct} placeholder="전체" onChange={(e) => update('maxAtrPct', e.target.value)} /></Field>
          <Field label="최소 상대 거래량 (20일)"><input type="number" min="0" step="0.1" value={values.minRelativeVolume} placeholder="예: 1.5" onChange={(e) => update('minRelativeVolume', e.target.value)} /></Field>
          <Field label="최소 ROE (%)"><input type="number" value={values.minRoe} onChange={(e) => update('minRoe', e.target.value)} /></Field>
          <Field label="최대 PER"><input type="number" min="0" value={values.maxTrailingPe} onChange={(e) => update('maxTrailingPe', e.target.value)} /></Field>
          <Field label="최소 뉴스 심리"><input type="number" step="0.1" value={values.minNewsSentiment} onChange={(e) => update('minNewsSentiment', e.target.value)} /></Field>
        </div>
      </details>
    </section>
  );
}
