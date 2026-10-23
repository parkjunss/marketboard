'use client';

import { useResource } from '@/lib/use-resource';
import { ResourceState } from '@/components/ResourceState';
import { MarketIndexCard } from '@/components/market/MarketIndexCard';
import * as api from '@/lib/api';
import styles from './dashboard.module.css';

export function MarketOverview() {
  const resource = useResource(api.getMarketIndices);
  const indices = resource.data?.slice(0, 4) ?? [];

  return <section aria-label="주요 시장 지수" className={styles.indexGrid}>
    {indices.map((index) => <MarketIndexCard key={index.slug} slug={index.slug} name={index.name} />)}
    {resource.status !== 'success' ? <ResourceState status={resource.status} label="주요 시장 지수" onRetry={resource.retry} /> : indices.length === 0 && <ResourceState status="empty" label="주요 시장 지수" />}
  </section>;
}
