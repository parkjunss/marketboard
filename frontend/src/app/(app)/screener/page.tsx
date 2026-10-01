'use client';

import { useCallback, useEffect, useState } from 'react';
import { Banner } from '@astryxdesign/core/Banner';
import { ApiError } from '@/lib/api';
import * as api from '@/lib/api';
import { useAuth } from '@/lib/auth-context';
import type { ScreenerSearchResponse, ScreenerSortField } from '@/lib/screener-types';
import { DEFAULT_FILTERS, ScreenerFilters, toSearchRequest, type ScreenerFilterValues } from '@/components/screener/ScreenerFilters';
import { ScreenerResults } from '@/components/screener/ScreenerResults';
import { PERIOD_OPTIONS } from '@/components/screener/screener-format';
import styles from '@/components/screener/screener.module.css';

export default function ScreenerPage() {
  const { authFetch } = useAuth();
  const [filters, setFilters] = useState<ScreenerFilterValues>(DEFAULT_FILTERS);
  const [result, setResult] = useState<ScreenerSearchResponse | null>(null);
  const [sortField, setSortField] = useState<ScreenerSortField>('MOMENTUM_6M');
  const [sortDirection, setSortDirection] = useState<'ASC' | 'DESC'>('DESC');
  const [size, setSize] = useState(20);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const search = useCallback(async (page = 0, next = filters, field = sortField, direction = sortDirection, pageSize = size) => {
    setIsLoading(true);
    setError(null);
    try {
      const data = await api.searchScreener(authFetch, {
        ...toSearchRequest(next), sort: { field, direction }, page, size: pageSize,
      });
      setResult(data);
    } catch (cause) {
      setError(cause instanceof ApiError ? cause.message : '스크리너 조회에 실패했습니다.');
    } finally {
      setIsLoading(false);
    }
  }, [authFetch, filters, size, sortDirection, sortField]);

  useEffect(() => {
    let cancelled = false;
    api.searchScreener(authFetch, toSearchRequest(DEFAULT_FILTERS))
      .then((data) => { if (!cancelled) setResult(data); })
      .catch((cause) => { if (!cancelled) setError(cause instanceof ApiError ? cause.message : '스크리너 조회에 실패했습니다.'); })
      .finally(() => { if (!cancelled) setIsLoading(false); });
    return () => { cancelled = true; };
  }, [authFetch]);

  function reset() {
    const next = { ...DEFAULT_FILTERS };
    const field = PERIOD_OPTIONS.find((option) => option.value === next.momentumPeriod)!.field;
    setFilters(next); setSortField(field); setSortDirection('DESC');
    void search(0, next, field, 'DESC', size);
  }

  function submit() {
    const field = PERIOD_OPTIONS.find((option) => option.value === filters.momentumPeriod)!.field;
    setSortField(field); setSortDirection('DESC');
    void search(0, filters, field, 'DESC', size);
  }

  return <main className={styles.page}>
    <h1 className={styles.title}>스크리너</h1>
    <p className={styles.subtitle}>다양한 조건으로 미국 주식을 검색하고 투자 기회를 찾아보세요.</p>
    {error && <Banner status="error" title="조회 실패" description={error} />}
    <ScreenerFilters values={filters} onChange={setFilters} onReset={reset} onSubmit={submit} isLoading={isLoading} />
    <ScreenerResults
      result={result} period={filters.momentumPeriod} sortField={sortField} sortDirection={sortDirection} size={size}
      onSortField={(field) => { setSortField(field); void search(0, filters, field, sortDirection, size); }}
      onSortDirection={(direction) => { setSortDirection(direction); void search(0, filters, sortField, direction, size); }}
      onSize={(nextSize) => { setSize(nextSize); void search(0, filters, sortField, sortDirection, nextSize); }}
      onPage={(page) => void search(page)}
    />
  </main>;
}
