'use client';

import { useEffect, useState } from 'react';
import { Sparkline } from '@/components/Sparkline';
import { useAuth } from '@/lib/auth-context';
import * as api from '@/lib/api';
import type { CandleResponse } from '@/lib/types';
import styles from './dashboard.module.css';

const MARKETS = [
  { slug: 'VIX', label: '변동성 지수', suffix: '' },
  { slug: 'DXY', label: '달러 인덱스', suffix: '' },
  { slug: 'US10Y', label: '미국 10년물 금리', suffix: '%' },
  { slug: 'GOLD', label: '금', suffix: '' },
  { slug: 'WTI', label: 'WTI 유가', suffix: '' },
];

export function MacroMarketGrid() {
  const { authFetch } = useAuth();
  const [histories, setHistories] = useState<Record<string, CandleResponse[]>>({});

  useEffect(() => {
    let cancelled = false;
    Promise.all(MARKETS.map(async (market) => {
      try { return [market.slug, await api.getMarketIndexHistory(authFetch, market.slug)] as const; }
      catch { return [market.slug, []] as const; }
    })).then((entries) => { if (!cancelled) setHistories(Object.fromEntries(entries)); });
    return () => { cancelled = true; };
  }, [authFetch]);

  return <section className={styles.macroGrid} aria-label="거시 시장 지표">
    {MARKETS.map((market) => {
      const candles = histories[market.slug] ?? [];
      const latest = candles.at(-1);
      const previous = candles.at(-2);
      const changePct = latest && previous ? ((latest.close - previous.close) / previous.close) * 100 : null;
      return <article className={styles.macroCard} key={market.slug}>
        <div className={styles.macroHead}><span>{market.label}</span>{changePct != null && <strong className={changePct >= 0 ? styles.positive : styles.negative}>{changePct >= 0 ? '+' : ''}{changePct.toFixed(2)}%</strong>}</div>
        <div className={styles.macroValue}>{latest ? latest.close.toLocaleString('ko-KR', { maximumFractionDigits: 2 }) : '—'}{market.suffix}</div>
        <Sparkline values={candles.slice(-30).map((candle) => candle.close)} width={180} height={48} isPositive={(changePct ?? 0) >= 0} />
      </article>;
    })}
  </section>;
}
