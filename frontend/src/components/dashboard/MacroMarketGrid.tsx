'use client';

import { DataStamp } from '@/components/DataStamp';


import { useCallback } from 'react';
import { useResource } from '@/lib/use-resource';
import { ResourceState } from '@/components/ResourceState';
import { Sparkline } from '@/components/Sparkline';
import * as api from '@/lib/api';
import styles from './dashboard.module.css';

const MARKETS = [
  { slug: 'VIX', label: '변동성 지수', suffix: '' },
  { slug: 'DXY', label: '달러 인덱스', suffix: '' },
  { slug: 'US10Y', label: '미국 10년물 금리', suffix: '%' },
  { slug: 'GOLD', label: '금', suffix: '' },
  { slug: 'WTI', label: 'WTI 유가', suffix: '' },
];

export const MACRO_SLUGS = new Set(MARKETS.map(market => market.slug));

function MacroCard({ market }: { market: typeof MARKETS[number] }) {
  const load = useCallback((fetcher: api.Fetcher) => api.getMarketIndexHistory(fetcher, market.slug), [market.slug]);
  const resource = useResource(load);
  const candles = resource.data ?? [];
  const latest = candles.at(-1);
  const previous = candles.at(-2);
  const changePct = latest && previous ? ((latest.close - previous.close) / previous.close) * 100 : null;
  return <article className={styles.macroCard}>
    <div className={styles.macroHead}><span>{market.label}</span>{changePct != null && <strong className={changePct >= 0 ? styles.positive : styles.negative}>{changePct >= 0 ? '+' : ''}{changePct.toFixed(2)}%</strong>}</div>
    {resource.status !== 'success' ? <ResourceState status={resource.status} label={market.label} onRetry={resource.retry} /> :
      !latest ? <ResourceState status="empty" label={market.label} /> : <>
        <div className={styles.macroValue}>{latest.close.toLocaleString('ko-KR', { maximumFractionDigits: 2 })}{market.suffix}</div>
        <Sparkline values={candles.slice(-30).map(candle => candle.close)} width={180} height={48} isPositive={(changePct ?? 0) >= 0} />
        <DataStamp source="Yahoo Finance (yfinance)" at={latest.ts} label="최근 일봉 기준" note="일봉 · 실시간 시세 아님" />
      </>}
  </article>;
}

export function MacroMarketGrid() {
  return <section className={styles.macroGrid} aria-label="거시 시장 지표">
    {MARKETS.map(market => <MacroCard key={market.slug} market={market} />)}
  </section>;
}
