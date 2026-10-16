'use client';

import { useState, type FormEvent } from 'react';
import Link from 'next/link';
import { ApiError, compareEtfStrategies } from '@/lib/api';
import { useAuth } from '@/lib/auth-context';
import type { EtfComparisonResult } from '@/lib/etf-comparison-types';
import { MultiLineChart } from '@/components/charts/MultiLineChart';
import styles from '@/components/screener/screener.module.css';

const colors = ['var(--mb-text-muted)', 'var(--color-icon-orange)', 'var(--mb-accent)'];
const pct = (value: number | null) => value === null ? '—' : `${value.toFixed(2)}%`;
const number = (value: number | null) => value === null ? '—' : value.toLocaleString('ko-KR', { maximumFractionDigits: 2 });
const weight = (value: number) => new Intl.NumberFormat('ko-KR', { style: 'percent', maximumFractionDigits: 2 }).format(value);

export function EtfComparisonPanel() {
  const { authFetch } = useAuth();
  const [result, setResult] = useState<EtfComparisonResult | null>(null);
  const [running, setRunning] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setRunning(true); setError(null); setResult(null);
    try {
      setResult(await compareEtfStrategies(authFetch, {
        startDate: String(form.get('startDate')), endDate: String(form.get('endDate')),
        initialCapital: Number(form.get('initialCapital')),
        feeBps: Number(form.get('feeBps')), slippageBps: Number(form.get('slippageBps')),
      }));
    } catch (cause) {
      setError(cause instanceof ApiError ? cause.message : 'ETF 비교 조회에 실패했습니다. 잠시 후 다시 시도해 주세요.');
    } finally { setRunning(false); }
  }

  return <section id="etf-comparison" className={styles.panel} aria-labelledby="etf-comparison-title" aria-busy={running}>
    <div className={styles.panelHeader}><h2 id="etf-comparison-title">ETF 전략 3종 비교 <span>연구용</span></h2></div>
    <div className={styles.etfExplanation}>
      <p>SPY 단순 보유 · 모멘텀 상위 3개 · 모멘텀 + 200일선을 같은 조건에서 비교합니다.</p>
      <p>SPY·QQQ·EFA·EEM·IEF·GLD의 시작 전 약 13개월 일봉이 필요합니다. 월말 신호를 다음 관측 거래일 시가에 체결하며, 기간 중간에 시작하면 첫 거래일에 직전 월말 신호를 적용합니다.</p>
    </div>
    <p><Link href="/etf">ETF 전략 검토 저장 · 모의 운용 · 리밸런싱 제안</Link></p>
    <form onSubmit={submit}>
      <div className={styles.filterGrid}>
        <label className={styles.field}>시작일<input name="startDate" type="date" required /></label>
        <label className={styles.field}>종료일 (뉴욕 오늘 이전)<input name="endDate" type="date" required /></label>
        <label className={styles.field}>초기 자본 (USD)<input name="initialCapital" type="number" min="1" max="1000000000" step="0.01" defaultValue="10000" required /></label>
        <label className={styles.field}>편도 수수료 (bp)<input name="feeBps" type="number" min="0" max="100" step="0.1" defaultValue="5" required /></label>
        <label className={styles.field}>편도 슬리피지 (bp)<input name="slippageBps" type="number" min="0" max="100" step="0.1" defaultValue="5" required /></label>
        <button className={styles.primaryButton} type="submit" disabled={running}>{running ? '서버에서 계산 중…' : '3개 전략 비교'}</button>
      </div>
    </form>
    <div className={styles.etfExplanation}>
      <p>1bp = 0.01%. 시가 기준 거래대금에 수수료·슬리피지를 비용으로 차감합니다. 소수점 수량 허용, 현금 이자·샤프 무위험 금리 0%, 세금·종료일 강제 매도 없음. 결과는 저장되지 않습니다.</p>
      <p>과거 가격의 배당·분할 조정 일관성 및 거래소 휴장일 대조는 미검증입니다. 자동 매매 신호나 수익 보장이 아닙니다.</p>
      {error && <p role="alert">{error}</p>}
      {result && !result.ready && <p role="status">비교 보류: {result.message}</p>}
    </div>
    {result?.ready && <>
      <div className={styles.etfExplanation}>
        <p>요청: {result.requestedStartDate} ~ {result.requestedEndDate} · 실제 관측 거래일: {result.startDate} ~ {result.endDate}</p>
        <p>초기 자본 {number(result.initialCapital)} {result.currency} · 수수료 {number(result.feeBps)}bp · 슬리피지 {number(result.slippageBps)}bp · 현금 이자 {pct(result.cashRatePct)}</p>
      </div>
      <div className={styles.tableScroll}><table className={styles.table}>
        <caption className={styles.etfCaption}>첫 매수 비용을 포함한 성과 · 회전율은 매수+매도 거래대금/NAV의 누적 합계</caption>
        <thead><tr>{['전략', '수익률', 'CAGR', '최대 낙폭', '연 변동성', '샤프', '수수료 (USD)', '슬리피지 (USD)', '누적 회전율'].map((label) => <th key={label} scope="col">{label}</th>)}</tr></thead>
        <tbody>{result.strategies.map((strategy) => <tr key={strategy.strategyId}>
          <th scope="row">{strategy.label}</th><td>{pct(strategy.metrics.totalReturnPct)}</td><td>{pct(strategy.metrics.cagrPct)}</td>
          <td>{pct(strategy.metrics.maxDrawdownPct)}</td><td>{pct(strategy.metrics.volatilityPct)}</td><td>{number(strategy.metrics.sharpeRatio)}</td>
          <td>{number(strategy.totalFees)}</td><td>{number(strategy.totalSlippage)}</td><td>{pct(strategy.turnoverPct)}</td>
        </tr>)}</tbody>
      </table></div>
      <div className={styles.etfExplanation}>
        <h3>비용 차감 누적 수익률</h3>
        <MultiLineChart categories={result.strategies[0]?.equityCurve.map((point) => point.date) ?? []}
          series={result.strategies.map((strategy, index) => ({ label: strategy.label, color: colors[index], values: strategy.equityCurve.map((point) => point.returnPct) }))}
          width={1100} height={260} valueFormatter={pct} />
        <p>시장 구간은 전 거래일까지의 SPY 63거래일 수익률로 구분합니다: +5% 초과 상승, -5% 미만 하락, 나머지 횡보. 구간 수익률은 불연속 날짜들의 일간 수익률을 연결한 값입니다.</p>
      </div>
      <div className={styles.tableScroll}><table className={styles.table}>
        <thead><tr><th scope="col">전략</th><th scope="col">시장 구간</th><th scope="col">일수</th><th scope="col">구간 수익률</th></tr></thead>
        <tbody>{result.strategies.flatMap((strategy) => strategy.regimes.map((regime) => <tr key={`${strategy.strategyId}-${regime.label}`}>
          <th scope="row">{strategy.label}</th><td>{regime.label}</td><td>{regime.days}</td><td>{pct(regime.returnPct)}</td>
        </tr>))}</tbody>
      </table></div>
      {result.strategies.map((strategy) => <details key={strategy.strategyId} className={styles.advanced}>
        <summary>{strategy.label} — 리밸런싱 {strategy.rebalances.length}회 · 거래 {strategy.trades.length}건</summary>
        <div className={styles.tableScroll}><table className={styles.table}>
          <caption className={styles.etfCaption}>서버가 계산한 월별 목표 비중</caption>
          <thead><tr><th scope="col">신호 월말</th><th scope="col">체결일</th><th scope="col">목표 비중</th><th scope="col">현금</th></tr></thead>
          <tbody>{strategy.rebalances.map((rebalance) => <tr key={rebalance.executionDate}>
            <td>{rebalance.signalDate}</td><td>{rebalance.executionDate}</td>
            <td>{rebalance.targets.map((target) => `${target.ticker} ${weight(target.weight)}`).join(' · ')}</td><td>{weight(rebalance.cashWeight)}</td>
          </tr>)}</tbody>
        </table></div>
        <div className={styles.tableScroll}><table className={styles.table}>
          <caption className={styles.etfCaption}>시가 기준 거래 기록 (USD)</caption>
          <thead><tr>{['신호 월말', '체결일', 'ETF', '방향', '수량', '기준 시가', '거래대금', '수수료', '슬리피지'].map((label) => <th key={label} scope="col">{label}</th>)}</tr></thead>
          <tbody>{strategy.trades.map((trade, index) => <tr key={index}>
            <td>{trade.signalDate}</td><td>{trade.executionDate}</td><th scope="row">{trade.ticker}</th><td>{trade.side === 'BUY' ? '매수' : '매도'}</td>
            <td>{trade.quantity.toFixed(6)}</td><td>{number(trade.price)}</td><td>{number(trade.notional)}</td><td>{number(trade.fee)}</td><td>{number(trade.slippage)}</td>
          </tr>)}</tbody>
        </table></div>
      </details>)}
    </>}
  </section>;
}
