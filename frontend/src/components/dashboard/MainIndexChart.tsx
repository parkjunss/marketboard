'use client';

import { useEffect, useState } from 'react';
import { CandleChart } from '@/components/CandleChart';
import { useAuth } from '@/lib/auth-context';
import * as api from '@/lib/api';
import type { CandleResponse } from '@/lib/types';
import styles from './dashboard.module.css';

const INDICES = [
  { slug: 'SPX', label: 'S&P 500' },
  { slug: 'IXIC', label: 'NASDAQ' },
  { slug: 'RUT', label: 'RUSSELL 2000' },
];

export function MainIndexChart() {
  const { authFetch } = useAuth();
  const [selected, setSelected] = useState(INDICES[0]);
  const [result, setResult] = useState<{ key: string; candles: CandleResponse[] } | null>(null);

  useEffect(() => {
    let cancelled = false;
    api.getMarketIndexHistory(authFetch, selected.slug)
      .then((data) => { if (!cancelled) setResult({ key: selected.slug, candles: data }); })
      .catch(() => { if (!cancelled) setResult({ key: selected.slug, candles: [] }); });
    return () => { cancelled = true; };
  }, [authFetch, selected]);

  const candles = result?.key === selected.slug ? result.candles : null;
  const latest = candles?.at(-1);
  const previous = candles && candles.length > 1 ? candles.at(-2) : null;
  const change = latest && previous ? latest.close - previous.close : null;
  const changePct = change != null && previous ? (change / previous.close) * 100 : null;

  return <section className={`${styles.section} ${styles.mainChart}`}>
    <div className={styles.chartHeader}>
      <div>
        <h2>주요 지수 캔들차트</h2>
        <div className={styles.chartValue}>
          <strong>{latest ? latest.close.toLocaleString('ko-KR', { maximumFractionDigits: 2 }) : '—'}</strong>
          {change != null && changePct != null && <span className={change >= 0 ? styles.positive : styles.negative}>{change >= 0 ? '+' : ''}{change.toFixed(2)} ({changePct.toFixed(2)}%)</span>}
        </div>
      </div>
      <div className={styles.tabs}>
        {INDICES.map((index) => <button key={index.slug} className={selected.slug === index.slug ? styles.activeTab : undefined} onClick={() => setSelected(index)}>{index.label}</button>)}
      </div>
    </div>
    <div className={styles.chartBody}>{candles ? <CandleChart key={selected.slug} candles={candles} height={310} /> : <div className={styles.chartLoading}>차트를 불러오는 중입니다</div>}</div>
  </section>;
}
