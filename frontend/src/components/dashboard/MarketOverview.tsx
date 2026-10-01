'use client';

import { useEffect, useState } from 'react';
import { MarketIndexCard } from '@/components/market/MarketIndexCard';
import { useAuth } from '@/lib/auth-context';
import * as api from '@/lib/api';
import type { MarketIndexInfo } from '@/lib/types';
import styles from './dashboard.module.css';

export function MarketOverview() {
  const { authFetch } = useAuth();
  const [indices, setIndices] = useState<MarketIndexInfo[]>([]);

  useEffect(() => {
    let cancelled = false;
    api.getMarketIndices(authFetch).then((data) => { if (!cancelled) setIndices(data.slice(0, 4)); }).catch(() => undefined);
    return () => { cancelled = true; };
  }, [authFetch]);

  return <section aria-label="주요 시장 지수" className={styles.indexGrid}>
    {indices.map((index) => <MarketIndexCard key={index.slug} slug={index.slug} name={index.name} />)}
    {indices.length === 0 && Array.from({ length: 4 }, (_, index) => <div className={styles.indexSkeleton} key={index} />)}
  </section>;
}
