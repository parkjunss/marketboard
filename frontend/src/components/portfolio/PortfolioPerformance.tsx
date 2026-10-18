'use client';

import { useState } from 'react';
import { MultiLineChart } from '@/components/charts/MultiLineChart';
import styles from './portfolio-designer.module.css';

export type Performance = {
  values: number[]; endValue: number; periodReturnPct: number; maxDrawdownPct: number;
  peakDate: string; troughDate: string; recoveryDate: string | null;
  recoveryDays: number | null; underwaterDays: number;
};
export type PerformanceComparison = {
  dates: string[]; capital: number; cash: number;
  benchmark: Performance | null; benchmarkIssue: string | null;
};
const money = (v: number) => v.toLocaleString('en-US', { style: 'currency', currency: 'USD' });
const percent = (v: number) => `${v.toFixed(2)}%`;

export function PortfolioPerformance({ comparison, chosen, equal }: {
  comparison: PerformanceComparison;
  chosen: { id: string; label: string; performance?: Performance };
  equal?: Performance;
}) {
  const [dateIndex, setDateIndex] = useState<number | null>(null);
  if (!chosen.performance || !comparison.dates.length) return <p role="status">이 배분안의 자산 추이 데이터가 없습니다. 다시 계산해 주세요.</p>;
  const index = Math.min(dateIndex ?? comparison.dates.length - 1, comparison.dates.length - 1);
  const series = [
    { label: `선택 · ${chosen.label}`, color: '#f4b85e', data: chosen.performance },
    ...(equal && chosen.id !== 'equal' ? [{ label: '동일 비중', color: 'var(--mb-accent)', data: equal }] : []),
    ...(comparison.benchmark ? [{ label: 'SPY + 동일 현금', color: '#8dafff', data: comparison.benchmark }] : []),
  ];
  return <section aria-label="과거 자산 추이 비교">
    <h2>5. 과거 자산 추이 비교</h2>
    <p>초기 자금 {money(comparison.capital)} · 남긴 현금 {money(comparison.cash)} · {comparison.dates[0]} ~ {comparison.dates.at(-1)}</p>
    <div className={styles.chartPanel}>
      <div className={styles.chartScroll} style={{ minWidth: 0 }}>
        <div style={{ minWidth: 560 }}><MultiLineChart categories={comparison.dates}
          series={series.map(s => ({ label: s.label, color: s.color, values: s.data.values }))}
          width={800} height={320} includeZero={false} categoryLabelCount={5}
          valueFormatter={v => `$${v.toLocaleString('en-US', { maximumFractionDigits: 0 })}`}/></div>
      </div>
      <label>조회 날짜 · {comparison.dates[index]}<input type="range" min={0} max={comparison.dates.length-1}
        value={index} onChange={e => setDateIndex(Number(e.target.value))} aria-valuetext={comparison.dates[index]}/></label>
      <div className={styles.selectionSummary} aria-live="polite">{series.map(s => <span key={s.label}>{s.label} <b>{money(s.data.values[index])}</b></span>)}</div>
    </div>
    {comparison.benchmarkIssue && <p role="status" className={styles.notice}>{comparison.benchmarkIssue}</p>}
    <div className={styles.scroll}><table><caption>동일 초기 자금으로 매수 후 보유한 과거 성과</caption>
      <thead><tr>{['배분안', '최종 자산', '기간 수익률', '최대 낙폭', '최대 낙폭 회복 기간', '고점 → 저점 → 회복일'].map(t=><th key={t}>{t}</th>)}</tr></thead>
      <tbody>{series.map(({label, data})=><tr key={label}><th scope="row">{label}</th><td>{money(data.endValue)}</td><td>{percent(data.periodReturnPct)}</td><td>{percent(data.maxDrawdownPct)}</td>
        <td>{data.maxDrawdownPct === 0 ? '낙폭 없음' : data.recoveryDays == null ? `미회복 · 고점 이후 ${data.underwaterDays}일` : `${data.recoveryDays}일`}</td>
        <td>{data.maxDrawdownPct === 0 ? '—' : `${data.peakDate} → ${data.troughDate} → ${data.recoveryDate ?? '기간 내 미회복'}`}</td></tr>)}</tbody>
    </table></div>
    <p className={styles.chartHint}>회복 기간은 최대 낙폭 직전 고점부터 그 금액을 처음 회복한 관측일까지의 달력 일수입니다. SPY는 자산별 비중 상한을 적용하지 않는 비교 기준입니다.</p>
    <p className={styles.chartHint}>공통 관측일 기준 소수점 주식 매수 후 보유 모형이며, 현금 이자·수수료·세금·리밸런싱은 제외합니다. 관측일 사이의 누락 가격은 보간하지 않으며 그 사이의 낙폭은 알 수 없습니다. 같은 기간으로 비중을 선택한 사후 비교입니다.</p>
  </section>;
}
