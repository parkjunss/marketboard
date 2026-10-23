'use client';

import { DataStamp } from '@/components/DataStamp';


import { useCallback, useState } from 'react';
import { useResource } from '@/lib/use-resource';
import { ResourceState } from '@/components/ResourceState';
import { CandleChart } from '@/components/CandleChart';
import * as api from '@/lib/api';
import styles from './dashboard.module.css';

const INDICES = [
  { slug: 'SPX', label: 'S&P 500' },
  { slug: 'IXIC', label: 'NASDAQ' },
  { slug: 'RUT', label: 'RUSSELL 2000' },
];

export function MainIndexChart() {
  const [selected, setSelected] = useState(INDICES[0]);
  const load = useCallback((fetcher: api.Fetcher) => api.getMarketIndexHistory(fetcher, selected.slug), [selected.slug]);
  const resource = useResource(load);
  const candles = resource.data;
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
    {latest && <DataStamp source="Yahoo Finance (yfinance)" at={latest.ts} label="최근 일봉 기준" note="일봉 · 실시간 시세 아님" />}
    <div className={styles.chartBody}>{resource.status !== 'success' ? <ResourceState status={resource.status} label={`${selected.label} 차트`} onRetry={resource.retry} /> : candles && candles.length > 0 ? <CandleChart key={selected.slug} candles={candles} height={310} /> : <ResourceState status="empty" label={`${selected.label} 차트`} />}</div>
  </section>;
}
