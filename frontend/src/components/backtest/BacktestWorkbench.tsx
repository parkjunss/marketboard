'use client';

import { useEffect, useMemo, useState } from 'react';
import { CandleChart } from '@/components/CandleChart';
import { MultiLineChart } from '@/components/charts/MultiLineChart';
import { useAuth } from '@/lib/auth-context';
import * as api from '@/lib/api';
import type { BacktestEngineResult, BacktestRunResponse, CandleResponse } from '@/lib/types';
import styles from './backtest-workbench.module.css';

const SMA_OVERLAYS = [
  { period: 20, color: '--color-icon-blue', label: 'SMA 20' },
  { period: 60, color: '--color-icon-orange', label: 'SMA 60' },
];

interface ReturnSeries {
  categories: string[];
  portfolio: number[];
  benchmark: number[];
}

function pct(value: number | null | undefined) {
  return value == null ? '—' : `${value.toFixed(2)}%`;
}

function money(value: number | null | undefined) {
  return value == null ? '—' : `${Math.round(value).toLocaleString('ko-KR')}원`;
}

export function BacktestWorkbench({ tickers, startDate, endDate, run, result, returnSeries }: {
  tickers: string[];
  startDate?: string;
  endDate?: string;
  run: BacktestRunResponse | null;
  result: BacktestEngineResult | null;
  returnSeries: ReturnSeries | null;
}) {
  const { authFetch } = useAuth();
  const [chartTicker, setChartTicker] = useState(tickers[0] ?? '');
  const [history, setHistory] = useState<{ ticker: string; candles: CandleResponse[] } | null>(null);

  const availableTickers = run?.tickers ?? tickers;
  const selectedTicker = availableTickers.includes(chartTicker) ? chartTicker : (availableTickers[0] ?? '');

  useEffect(() => {
    if (!selectedTicker) return;
    let cancelled = false;
    api.getHistory(authFetch, selectedTicker, '1d', 1000)
      .then((candles) => {
        if (cancelled) return;
        setHistory({ ticker: selectedTicker, candles: candles.filter((c) => (!startDate || c.ts.slice(0, 10) >= startDate) && (!endDate || c.ts.slice(0, 10) <= endDate)) });
      })
      .catch(() => { if (!cancelled) setHistory({ ticker: selectedTicker, candles: [] }); });
    return () => { cancelled = true; };
  }, [authFetch, selectedTicker, startDate, endDate]);

  const latest = history?.ticker === selectedTicker ? history.candles.at(-1) : null;
  const stats = useMemo(() => result?.tickerStats ?? [], [result]);
  const finalValue = result?.equityCurve.at(-1)?.portfolioValue;

  return <section className={styles.workbench}>
    <header className={styles.header}>
      <div><span>BACKTEST ANALYSIS</span><h2>{run?.name ?? '전략 미리보기'}</h2><p>{run ? `${run.startDate} — ${run.endDate}` : '전략을 실행하면 성과 지표와 자산곡선이 채워집니다.'}</p></div>
      <div className={styles.tickerTabs}>{availableTickers.map((ticker) => <button key={ticker} className={selectedTicker === ticker ? styles.active : ''} onClick={() => setChartTicker(ticker)}>{ticker}</button>)}</div>
    </header>

    <div className={styles.topGrid}>
      <aside className={styles.metrics}>
        <h3>성과 요약</h3>
        <dl>
          <div><dt>총 수익률</dt><dd className={(result?.metrics.totalReturnPct ?? 0) >= 0 ? styles.positive : styles.negative}>{pct(result?.metrics.totalReturnPct)}</dd></div>
          <div><dt>CAGR</dt><dd>{pct(result?.metrics.cagrPct)}</dd></div>
          <div><dt>최대 낙폭</dt><dd className={styles.negative}>{pct(result?.metrics.maxDrawdownPct)}</dd></div>
          <div><dt>연 변동성</dt><dd>{pct(result?.metrics.volatilityPct)}</dd></div>
          <div><dt>샤프 비율</dt><dd>{result?.metrics.sharpeRatio?.toFixed(2) ?? '—'}</dd></div>
          <div><dt>초기 자본</dt><dd>{money(run?.initialCapital)}</dd></div>
          <div><dt>최종 자산</dt><dd>{money(finalValue)}</dd></div>
        </dl>
      </aside>

      <div className={styles.chartPanel}>
        <div className={styles.panelTitle}><div><h3>{selectedTicker} 주가</h3><p>일봉 · SMA 20 / SMA 60</p></div><strong>{latest ? latest.close.toLocaleString('ko-KR', { maximumFractionDigits: 2 }) : '—'}</strong></div>
        <div className={styles.candleArea}>{history?.ticker !== selectedTicker ? <div className={styles.empty}>차트를 불러오는 중입니다</div> : history.candles.length > 0 ? <CandleChart candles={history.candles} height={390} smaOverlays={SMA_OVERLAYS} /> : <div className={styles.empty}>선택 기간의 가격 데이터가 없습니다</div>}</div>
      </div>

      <aside className={styles.tickerStats}>
        <h3>종목 성과</h3>
        {stats.length > 0 ? <div>{stats.map((stat) => <article key={stat.ticker}><div><strong>{stat.ticker}</strong><span>변동성 {pct(stat.volatilityPct)}</span></div><b className={stat.returnPct >= 0 ? styles.positive : styles.negative}>{pct(stat.returnPct)}</b></article>)}</div> : <div className={styles.empty}>실행 후 종목별 결과가 표시됩니다</div>}
      </aside>
    </div>

    <div className={styles.equityPanel}>
      <div className={styles.panelTitle}><div><h3>자산곡선</h3><p>전략 누적 수익률과 SPY 벤치마크 비교</p></div></div>
      {returnSeries ? <MultiLineChart categories={returnSeries.categories} series={[{ label: run?.name ?? '전략', color: 'var(--mb-accent)', values: returnSeries.portfolio }, { label: `벤치마크 (${result?.benchmarkStats?.ticker ?? 'SPY'})`, color: 'var(--mb-text-muted)', values: returnSeries.benchmark }]} width={1200} height={260} valueFormatter={(value) => `${value.toFixed(1)}%`} /> : <div className={styles.equityEmpty}>백테스트를 실행하면 자산곡선이 표시됩니다.</div>}
    </div>
  </section>;
}
