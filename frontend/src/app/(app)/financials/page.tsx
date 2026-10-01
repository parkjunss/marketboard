'use client';

import { useEffect, useMemo, useState } from 'react';
import Link from 'next/link';
import { ArrowTopRightOnSquareIcon, PlusIcon, XMarkIcon } from '@heroicons/react/24/outline';
import { PageLayout } from '@/components/layout/PageLayout';
import { useAuth } from '@/lib/auth-context';
import * as api from '@/lib/api';
import type { FinancialsResponse, WatchlistItemResponse } from '@/lib/types';
import styles from './financials.module.css';

interface CompareRow extends Record<string, unknown> {
  ticker: string;
  name: string;
  year: number | null;
  quickRatio: number | null;
  debtToCapitalPct: number | null;
  interestCoverage: number | null;
  netMarginPct: number | null;
  revenueGrowthPct: number | null;
  operatingIncomeGrowthPct: number | null;
  roePct: number | null;
  peRatio: number | null;
}

function fmt(value: number | null | undefined, formatter: (v: number) => string): string {
  return value == null ? '—' : formatter(value);
}

function tone(value: number | null | undefined) {
  if (value == null || value === 0) return '';
  return value > 0 ? styles.positive : styles.negative;
}

function toCompareRow(ticker: string, data: FinancialsResponse): CompareRow {
  const latestYear = data.years.length > 0 ? data.years[data.years.length - 1] : null;
  const latestMargins = data.marginsAnalysis[data.marginsAnalysis.length - 1];
  const latestGrowth = data.growthAnalysis[data.growthAnalysis.length - 1];
  const latestProfitability = data.profitabilityAnalysis[data.profitabilityAnalysis.length - 1];
  const latestMarket = data.marketAnalysis[data.marketAnalysis.length - 1];
  return {
    ticker,
    name: data.name,
    year: latestYear,
    quickRatio: data.kpis.quickRatio,
    debtToCapitalPct: data.kpis.debtToCapitalPct,
    interestCoverage: data.kpis.interestCoverage,
    netMarginPct: latestMargins?.netMarginPct ?? null,
    revenueGrowthPct: latestGrowth?.revenueGrowthPct ?? null,
    operatingIncomeGrowthPct: latestGrowth?.operatingIncomeGrowthPct ?? null,
    roePct: latestProfitability?.roePct ?? null,
    peRatio: latestMarket?.peRatio ?? null,
  };
}

export default function FinancialsPage() {
  const { authFetch } = useAuth();
  // Tickers explicitly added via the input or a watchlist quick-add chip, kept separate from
  // the watchlist itself so ordering/dedup with auto-added watchlist tickers can be derived below.
  const [manualTickers, setManualTickers] = useState<string[]>([]);
  const [tickerInput, setTickerInput] = useState('');
  const [watchlist, setWatchlist] = useState<WatchlistItemResponse[]>([]);
  // Tickers the user explicitly removed from the comparison — excluded from watchlist
  // auto-add so a dismissed watchlist ticker doesn't immediately reappear.
  const [removedTickers, setRemovedTickers] = useState<Set<string>>(new Set());

  useEffect(() => {
    api.getWatchlist(authFetch).then(setWatchlist);
  }, [authFetch]);

  // Watchlist tickers are auto-included in the comparison; manual additions/removals layer on top.
  const tickers = useMemo(() => {
    const combined = watchlist.map((item) => item.ticker).filter((ticker) => !removedTickers.has(ticker));
    manualTickers.forEach((ticker) => {
      if (!combined.includes(ticker)) combined.push(ticker);
    });
    return combined;
  }, [watchlist, removedTickers, manualTickers]);

  const requestKey = tickers.join(',');
  const [result, setResult] = useState<{
    key: string;
    dataByTicker: Record<string, FinancialsResponse>;
    failedTickers: string[];
  } | null>(null);
  const isLoading = tickers.length > 0 && result?.key !== requestKey;

  useEffect(() => {
    if (tickers.length === 0) return undefined;
    let cancelled = false;
    Promise.allSettled(
      tickers.map((ticker) => api.getFinancials(authFetch, ticker).then((data) => ({ ticker, data }))),
    ).then((settled) => {
      if (cancelled) return;
      const dataByTicker: Record<string, FinancialsResponse> = {};
      const failedTickers: string[] = [];
      settled.forEach((outcome, index) => {
        if (outcome.status === 'fulfilled') {
          dataByTicker[outcome.value.ticker] = outcome.value.data;
        } else {
          failedTickers.push(tickers[index]);
        }
      });
      setResult({ key: requestKey, dataByTicker, failedTickers });
    });
    return () => {
      cancelled = true;
    };
  }, [authFetch, tickers, requestKey]);

  function addTicker(raw: string) {
    const ticker = raw.trim().toUpperCase();
    if (!ticker) return;
    setManualTickers((prev) => (prev.includes(ticker) ? prev : [...prev, ticker]));
    setRemovedTickers((prev) => {
      if (!prev.has(ticker)) return prev;
      const next = new Set(prev);
      next.delete(ticker);
      return next;
    });
    setTickerInput('');
  }

  function removeTicker(ticker: string) {
    setManualTickers((prev) => prev.filter((t) => t !== ticker));
    setRemovedTickers((prev) => new Set(prev).add(ticker));
  }

  const data = result?.key === requestKey ? result : null;
  const rows: CompareRow[] = tickers
    .map((ticker) => {
      const financials = data?.dataByTicker[ticker];
      return financials ? toCompareRow(ticker, financials) : null;
    })
    .filter((row): row is CompareRow => row !== null);

  const quickAddCandidates = watchlist.filter((item) => !tickers.includes(item.ticker));

  return (
    <PageLayout title="재무 종목 비교" description="관심 종목의 건전성, 성장성, 수익성과 밸류에이션을 같은 기준으로 비교하세요.">
      <section className={styles.selector}>
        <div className={styles.selectorTop}>
          <div><h2>비교할 종목</h2><p>티커를 직접 입력하거나 관심종목에서 빠르게 추가할 수 있습니다.</p></div>
          <form onSubmit={(event) => { event.preventDefault(); addTicker(tickerInput); }} className={styles.addForm}>
            <label htmlFor="financial-ticker">티커 추가</label>
            <div><input id="financial-ticker" placeholder="예: AAPL" value={tickerInput} onChange={(event) => setTickerInput(event.target.value.toUpperCase())} /><button type="submit"><PlusIcon />추가</button></div>
          </form>
        </div>

        {quickAddCandidates.length > 0 && <div className={styles.quickAdd}><span>관심종목 빠른 추가</span><div>{quickAddCandidates.map((item) => <button key={item.id} onClick={() => addTicker(item.ticker)}>+ {item.ticker}</button>)}</div></div>}
        {tickers.length > 0 && <div className={styles.selected}><span>비교 중 · {tickers.length}개</span><div>{tickers.map((ticker) => <span key={ticker}>{ticker}<button onClick={() => removeTicker(ticker)} aria-label={`${ticker} 비교에서 제거`}><XMarkIcon /></button></span>)}</div></div>}
      </section>

      <section className={styles.comparison}>
        <div className={styles.comparisonHeader}><div><h2>핵심 재무 지표</h2><p>최신 회계연도 기준 · 종목을 누르면 상세 분석으로 이동합니다.</p></div>{rows.length > 0 && <strong>{rows.length}<small>종목 비교</small></strong>}</div>
        {tickers.length === 0 ? (
          <div className={styles.state}><strong>비교할 종목을 추가하세요</strong><span>위 입력창이나 관심종목 바로가기를 이용할 수 있습니다.</span></div>
        ) : isLoading ? (
          <div className={styles.state}><span className={styles.loader} /><strong>재무 데이터를 불러오는 중입니다</strong></div>
        ) : rows.length === 0 ? (
          <div className={styles.state}><strong>표시할 재무 데이터가 없습니다</strong></div>
        ) : (
          <>
            {data && data.failedTickers.length > 0 && <div className={styles.warning}><strong>일부 종목을 불러오지 못했습니다.</strong> {data.failedTickers.join(', ')}</div>}
            <div className={styles.tableScroll}>
              <table>
                <thead><tr><th>종목</th><th>유동비율</th><th>부채비율</th><th>이자보상배율</th><th>순이익률</th><th>매출 성장률</th><th>영업이익 성장률</th><th>ROE</th><th>P/E</th></tr></thead>
                <tbody>{rows.map((row) => <tr key={row.ticker}>
                  <th scope="row"><Link href={`/financials/${row.ticker}`}><span>{row.ticker}<ArrowTopRightOnSquareIcon /></span><small>{row.name}</small><em>{row.year ?? '연도 미상'}</em></Link></th>
                  <td>{fmt(row.quickRatio, (v) => v.toFixed(2))}</td>
                  <td>{fmt(row.debtToCapitalPct, (v) => `${v.toFixed(1)}%`)}</td>
                  <td>{fmt(row.interestCoverage, (v) => `${v.toFixed(1)}x`)}</td>
                  <td className={tone(row.netMarginPct)}>{fmt(row.netMarginPct, (v) => `${v.toFixed(1)}%`)}</td>
                  <td className={tone(row.revenueGrowthPct)}>{fmt(row.revenueGrowthPct, (v) => `${v.toFixed(1)}%`)}</td>
                  <td className={tone(row.operatingIncomeGrowthPct)}>{fmt(row.operatingIncomeGrowthPct, (v) => `${v.toFixed(1)}%`)}</td>
                  <td className={tone(row.roePct)}>{fmt(row.roePct, (v) => `${v.toFixed(1)}%`)}</td>
                  <td>{fmt(row.peRatio, (v) => v.toFixed(1))}</td>
                </tr>)}</tbody>
              </table>
            </div>
            <div className={styles.legend}><span><i className={styles.goodDot} />양수</span><span><i className={styles.badDot} />음수</span><p>지표의 높고 낮음이 투자 적합성을 단독으로 의미하지는 않습니다.</p></div>
          </>
        )}
      </section>
    </PageLayout>
  );
}
