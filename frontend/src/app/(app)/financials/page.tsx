'use client';

import { useEffect, useMemo, useState } from 'react';
import { VStack, HStack } from '@astryxdesign/core/Stack';
import { Section } from '@astryxdesign/core/Section';
import { Heading, Text } from '@astryxdesign/core/Text';
import { TextInput } from '@astryxdesign/core/TextInput';
import { Button } from '@astryxdesign/core/Button';
import { Token } from '@astryxdesign/core/Token';
import { Table, proportional } from '@astryxdesign/core/Table';
import type { TableColumn } from '@astryxdesign/core/Table';
import { Link } from '@astryxdesign/core/Link';
import { Center } from '@astryxdesign/core/Center';
import { Spinner } from '@astryxdesign/core/Spinner';
import { EmptyState } from '@astryxdesign/core/EmptyState';
import { Banner } from '@astryxdesign/core/Banner';
import { useAuth } from '@/lib/auth-context';
import * as api from '@/lib/api';
import type { FinancialsResponse, WatchlistItemResponse } from '@/lib/types';

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

  const columns: TableColumn<CompareRow>[] = [
    {
      key: 'ticker',
      header: '종목',
      width: proportional(1.6),
      renderCell: (row) => (
        <Link href={`/financials/${row.ticker}`} isStandalone>
          <HStack gap={2} align="end">
            <Heading level={5}>{row.ticker}</Heading>
            <Text type="supporting" size="sm">
              {row.name}
              {row.year != null ? ` · ${row.year}` : ''}
            </Text>
          </HStack>
        </Link>
      ),
    },
    {
      key: 'quickRatio',
      header: 'Quick Ratio',
      width: proportional(1),
      renderCell: (row) => <Text type="body">{fmt(row.quickRatio, (v) => v.toFixed(2))}</Text>,
    },
    {
      key: 'debtToCapitalPct',
      header: 'Debt to Capital',
      width: proportional(1),
      renderCell: (row) => <Text type="body">{fmt(row.debtToCapitalPct, (v) => `${v.toFixed(1)}%`)}</Text>,
    },
    {
      key: 'interestCoverage',
      header: 'Interest Coverage',
      width: proportional(1),
      renderCell: (row) => <Text type="body">{fmt(row.interestCoverage, (v) => v.toFixed(1))}</Text>,
    },
    {
      key: 'netMarginPct',
      header: 'Net Margin',
      width: proportional(1),
      renderCell: (row) => <Text type="body">{fmt(row.netMarginPct, (v) => `${v.toFixed(1)}%`)}</Text>,
    },
    {
      key: 'revenueGrowthPct',
      header: 'Revenue Growth',
      width: proportional(1),
      renderCell: (row) => <Text type="body">{fmt(row.revenueGrowthPct, (v) => `${v.toFixed(1)}%`)}</Text>,
    },
    {
      key: 'operatingIncomeGrowthPct',
      header: 'Op. Income Growth',
      width: proportional(1),
      renderCell: (row) => <Text type="body">{fmt(row.operatingIncomeGrowthPct, (v) => `${v.toFixed(1)}%`)}</Text>,
    },
    {
      key: 'roePct',
      header: 'ROE',
      width: proportional(1),
      renderCell: (row) => <Text type="body">{fmt(row.roePct, (v) => `${v.toFixed(1)}%`)}</Text>,
    },
    {
      key: 'peRatio',
      header: 'P/E',
      width: proportional(0.8),
      renderCell: (row) => <Text type="body">{fmt(row.peRatio, (v) => v.toFixed(1))}</Text>,
    },
  ];

  return (
    <VStack gap={0}>
      <Section padding={4} dividers={['bottom']}>
        <VStack gap={1}>
          <Heading level={3}>재무 종목 비교</Heading>
          <Text type="supporting" size="sm">
            재무 건전성(Quick Ratio, Debt to Capital, Interest Coverage, Net Margin)과 성장 잠재력(매출/영업이익
            성장률, ROE, P/E)을 여러 종목에서 나란히 비교하세요 — 각 지표는 최신 회계연도 기준입니다. 종목을
            선택하면 상세 재무 대시보드로 이동합니다. 관심종목은 자동으로 비교 목록에 추가됩니다.
          </Text>
        </VStack>
      </Section>

      <Section padding={4} dividers={['bottom']}>
        <VStack gap={3}>
          <HStack gap={2} align="end">
            <TextInput
              label="티커 추가"
              size="sm"
              placeholder="AAPL"
              value={tickerInput}
              onChange={(value) => setTickerInput(value.toUpperCase())}
            />
            <Button variant="primary" size="sm" label="추가" clickAction={() => addTicker(tickerInput)} />
          </HStack>

          {quickAddCandidates.length > 0 && (
            <VStack gap={1}>
              <Text type="supporting" size="sm">
                워치리스트에서 빠른 추가
              </Text>
              <HStack gap={2} wrap="wrap">
                {quickAddCandidates.map((item) => (
                  <Token key={item.id} label={item.ticker} onClick={() => addTicker(item.ticker)} />
                ))}
              </HStack>
            </VStack>
          )}

          {tickers.length > 0 && (
            <VStack gap={1}>
              <Text type="supporting" size="sm">
                비교 중
              </Text>
              <HStack gap={2} wrap="wrap">
                {tickers.map((ticker) => (
                  <Token key={ticker} label={ticker} color="blue" onRemove={() => removeTicker(ticker)} />
                ))}
              </HStack>
            </VStack>
          )}
        </VStack>
      </Section>

      <Section padding={4}>
        {tickers.length === 0 ? (
          <Center height={280}>
            <EmptyState
              title="비교할 종목을 추가하세요"
              description="티커를 입력하거나 워치리스트에서 빠르게 추가할 수 있습니다"
            />
          </Center>
        ) : isLoading ? (
          <Center height={280}>
            <Spinner size="lg" label="불러오는 중" />
          </Center>
        ) : (
          <VStack gap={4}>
            {data && data.failedTickers.length > 0 && (
              <Banner
                status="warning"
                title="일부 종목을 불러오지 못했습니다"
                description={`${data.failedTickers.join(', ')} — 티커를 확인하고 다시 시도해보세요.`}
              />
            )}
            {rows.length === 0 ? (
              <Center height={200}>
                <EmptyState title="표시할 재무 데이터가 없습니다" />
              </Center>
            ) : (
              <Table data={rows} columns={columns} idKey="ticker" hasHover />
            )}
          </VStack>
        )}
      </Section>
    </VStack>
  );
}
