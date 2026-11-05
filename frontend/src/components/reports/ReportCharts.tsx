'use client';

import { MultiLineChart } from '@/components/charts/MultiLineChart';
import { GroupedBarChart } from '@/components/charts/GroupedBarChart';
import type { MarketReportDetail, ReportBreadthPoint, ReportSignal, ReportTrend, SignalTone } from '@/lib/market-report-api';
import type { MarketRiskState } from '@/lib/types';
import styles from './market-report.module.css';

const TONES: Record<SignalTone, { label: string; className: string }> = {
  POSITIVE: { label: '우호', className: styles.tonePositive },
  NEUTRAL: { label: '중립', className: styles.toneNeutral },
  CAUTION: { label: '경계', className: styles.toneCaution },
};
type Sectors = MarketReportDetail['payload']['sectors'];
const pct = (v: number) => `${v > 0 ? '+' : ''}${v.toFixed(1)}%`;
const shortDate = (date: string) => date.slice(5);

export function SignalSummary({ signals }: { signals: ReportSignal[] }) {
  const counts = signals.reduce((acc, s) => ({ ...acc, [s.tone]: (acc[s.tone] ?? 0) + 1 }), {} as Partial<Record<SignalTone, number>>);
  return <section aria-labelledby="state-title">
    <h2 id="state-title">시장 상태 요약</h2>
    <p className={styles.muted}>
      {signals.length}개 기준 중 우호 {counts.POSITIVE ?? 0} · 중립 {counts.NEUTRAL ?? 0} · 경계 {counts.CAUTION ?? 0}.
      단순 규칙으로 분류한 참고 정보이며 매수·매도 신호가 아닙니다. 변동성 위험 외의 기준은 예측력을 검증하지 않았습니다.
    </p>
    <ul className={styles.signals}>{signals.map(signal => <li key={signal.key}>
      <span className={`${styles.tone} ${TONES[signal.tone].className}`}>{TONES[signal.tone].label}</span>
      <div>
        <a href={`#${signal.section}`}><strong>{signal.label}</strong></a>
        <p>{signal.value}</p>
        <details><summary className={styles.muted}>판단 기준</summary><p className={styles.muted}>{signal.basis}</p></details>
      </div>
    </li>)}</ul>
  </section>;
}

export function IndexTrendCharts({ trends }: { trends: ReportTrend[] }) {
  return <div className={styles.grid}>{trends.map(trend => <article key={trend.slug} className={styles.card}>
    <h3>{trend.name} · 3개월</h3>
    <MultiLineChart
      height={200}
      includeZero={false}
      categoryLabelCount={4}
      categories={trend.points.map(p => shortDate(p.tradingDate))}
      valueFormatter={v => v.toLocaleString('ko-KR', { maximumFractionDigits: 0 })}
      series={[
        { label: '종가', color: 'var(--mb-accent)', values: trend.points.map(p => p.close) },
        { label: '50일 이동평균', color: 'var(--color-text-orange)', values: trend.points.map(p => p.sma50) },
      ]}
    />
  </article>)}</div>;
}

export function SectorReturnChart({ sectors }: { sectors: Sectors }) {
  return <div className={styles.chart}><GroupedBarChart
    width={760}
    height={240}
    valueFormatter={pct}
    categories={sectors.map(s => s.slug)}
    series={[
      { label: '1일', color: 'var(--mb-accent)', values: sectors.map(s => s.changePct1d) },
      { label: '1주', color: '#8dafff', values: sectors.map(s => s.changePct1w) },
      { label: '1개월', color: 'var(--color-text-orange)', values: sectors.map(s => s.changePct1m) },
    ]}
  /></div>;
}

export function BreadthTrendCharts({ history }: { history: ReportBreadthPoint[] }) {
  const dates = history.map(p => shortDate(p.tradingDate));
  return <div className={styles.chartPair}>
    <article className={styles.card}>
      <h3>상승 종목 비율 · 최근 {history.length}거래일</h3>
      <MultiLineChart
        height={200}
        categoryLabelCount={5}
        categories={dates}
        valueFormatter={v => `${v.toFixed(0)}%`}
        series={[
          { label: '상승 ÷ (상승+하락)', color: 'var(--mb-accent)', values: history.map(p => p.advancing + p.declining ? p.advancing * 100 / (p.advancing + p.declining) : null) },
          { label: '50% 기준선', color: 'var(--mb-text-muted)', values: history.map(() => 50) },
        ]}
      />
    </article>
    <article className={styles.card}>
      <h3>52주 신고가 · 신저가 종목 수</h3>
      <MultiLineChart
        height={200}
        categoryLabelCount={5}
        valueFormatter={v => v.toFixed(0)}
        categories={dates}
        series={[
          { label: '신고가', color: 'var(--mb-positive)', values: history.map(p => p.newHighs) },
          { label: '신저가', color: 'var(--mb-negative)', values: history.map(p => p.newLows) },
        ]}
      />
    </article>
  </div>;
}

export function PutCallTrendChart({ history }: { history: Array<{ timestamp: string; ratio: number }> }) {
  const label = (t: string) => new Date(t).toLocaleDateString('ko-KR', { timeZone: 'Asia/Seoul', month: '2-digit', day: '2-digit' });
  return <MultiLineChart
    width={760}
    height={160}
    includeZero={false}
    categoryLabelCount={4}
    categories={history.map(p => label(p.timestamp))}
    valueFormatter={v => v.toFixed(2)}
    series={[{ label: 'SPY Put/Call 비율', color: 'var(--color-text-orange)', values: history.map(p => p.ratio) }]}
  />;
}

const RISK_LABEL = { NORMAL: '정상', CAUTION: '주의', RISK: '위험', UNAVAILABLE: '미제공' } as const;

export function RiskStateBars({ risk }: { risk: MarketRiskState }) {
  const { caution, risk: riskCut } = risk.thresholds;
  const assets = [...risk.assets].sort((a, b) => (b.percentile ?? -1) - (a.percentile ?? -1));
  return <>
    <p className={styles.muted}>
      막대는 20일 변동성이 각 자산의 과거 변동성 중 몇 백분위인지 보여줍니다. 점선은 주의({caution}) · 위험({riskCut}) 경계입니다.
      15개 중 정상 {risk.counts.NORMAL ?? 0} · 주의 {risk.counts.CAUTION ?? 0} · 위험 {risk.counts.RISK ?? 0}
    </p>
    <ul className={styles.riskBars} style={{ ['--caution' as string]: `${caution}%`, ['--risk' as string]: `${riskCut}%` }}>
      {assets.map(asset => <li key={asset.ticker}>
        <span className={styles.riskName}><strong>{asset.ticker}</strong> {asset.name}</span>
        <span className={styles.riskTrack} aria-hidden="true">
          {asset.percentile != null && <span className={`${styles.riskFill} ${styles[`risk${asset.state}`] ?? ''}`} style={{ width: `${asset.percentile}%` }} />}
        </span>
        <span className={styles.riskValue}>{RISK_LABEL[asset.state]}{asset.percentile != null ? ` · ${Math.floor(asset.percentile)}` : ''}</span>
      </li>)}
    </ul>
    <p className={styles.muted}>기준 거래일 {risk.expectedTradingDate} · 출처 {risk.source} · 수집 {new Date(risk.fetchedAt).toLocaleString('ko-KR', { timeZone: 'Asia/Seoul', hour12: false })} KST</p>
  </>;
}
