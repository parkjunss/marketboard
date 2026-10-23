'use client';
import { useCallback } from 'react';
import { NewsList } from '@/components/news/NewsList';
import { ResourceState } from '@/components/ResourceState';
import { useResource } from '@/lib/use-resource';
import * as api from '@/lib/api';

export function NewsPanel({ ticker, limit, compact = false }: { ticker?: string; limit?: number; compact?: boolean }) {
  const load = useCallback((fetcher: api.Fetcher) => ticker ? api.getCompanyNews(fetcher, ticker) : api.getGeneralNews(fetcher), [ticker]);
  const resource = useResource(load);
  if (resource.status !== 'success') return <ResourceState status={resource.status} label="뉴스" onRetry={resource.retry} />;
  if (resource.data.length === 0) return <ResourceState status="empty" label="뉴스" />;
  return <NewsList items={resource.data} limit={limit} compact={compact} />;
}
