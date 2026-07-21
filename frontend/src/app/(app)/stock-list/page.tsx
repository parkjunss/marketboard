'use client';

import { useEffect, useMemo, useRef, useState } from 'react';
import { VStack, HStack } from '@astryxdesign/core/Stack';
import { Section } from '@astryxdesign/core/Section';
import { Heading, Text } from '@astryxdesign/core/Text';
import { SegmentedControl, SegmentedControlItem } from '@astryxdesign/core/SegmentedControl';
import { TextInput } from '@astryxdesign/core/TextInput';
import { Table, proportional, pixel } from '@astryxdesign/core/Table';
import type { TableColumn } from '@astryxdesign/core/Table';
import { IconButton } from '@astryxdesign/core/IconButton';
import { Icon } from '@astryxdesign/core/Icon';
import { Link } from '@astryxdesign/core/Link';
import { Center } from '@astryxdesign/core/Center';
import { Spinner } from '@astryxdesign/core/Spinner';
import { EmptyState } from '@astryxdesign/core/EmptyState';
import { StarIcon as StarOutlineIcon } from '@heroicons/react/24/outline';
import { StarIcon as StarSolidIcon } from '@heroicons/react/24/solid';
import { PriceChangeIndicator } from '@/components/PriceChangeIndicator';
import { Sparkline } from '@/components/Sparkline';
import { useAuth } from '@/lib/auth-context';
import { resolvePrevClose } from '@/lib/priceChange';
import { useQuoteStream } from '@/lib/quote-stream-context';
import * as api from '@/lib/api';
import type { CandleResponse, QuoteResponse, WatchlistItemResponse } from '@/lib/types';

const HISTORY_LIMIT = 250; // ~1 trading year of daily candles
const FLASH_DURATION_MS = 700;

interface StockStats {
  ticker: string;
  name: string;
  high: number;
  low: number;
  closes: number[];
  volumeSeries: number[];
  volumeChangePct: number;
}

interface StockRow extends StockStats, Record<string, unknown> {
  price: number | null;
  latestVolume: number | null;
  changeValue: number | null;
  changePct: number | null;
}

function computeStats(ticker: string, name: string, candles: CandleResponse[]): StockStats | null {
  if (candles.length === 0) return null;
  const high = Math.max(...candles.map((c) => c.high));
  const low = Math.min(...candles.map((c) => c.low));
  const closes = candles.map((c) => c.close);
  const volumeSeries = candles.map((c) => c.volume);
  const firstVolume = volumeSeries[0] || 1;
  const lastVolume = volumeSeries[volumeSeries.length - 1];
  const volumeChangePct = ((lastVolume - firstVolume) / firstVolume) * 100;
  return { ticker, name, high, low, closes, volumeSeries, volumeChangePct };
}

function usePriceFlash(quotes: Record<string, QuoteResponse>) {
  const prevPricesRef = useRef<Record<string, number>>({});
  const timersRef = useRef<Record<string, ReturnType<typeof setTimeout>>>({});
  const [flashes, setFlashes] = useState<Record<string, 'up' | 'down'>>({});

  useEffect(() => {
    Object.values(quotes).forEach((quote) => {
      if (quote.price == null) return;
      const prevPrice = prevPricesRef.current[quote.symbol];
      if (prevPrice !== undefined && prevPrice !== quote.price) {
        const direction = quote.price > prevPrice ? 'up' : 'down';
        setFlashes((prev) => ({ ...prev, [quote.symbol]: direction }));
        clearTimeout(timersRef.current[quote.symbol]);
        timersRef.current[quote.symbol] = setTimeout(() => {
          setFlashes((prev) => {
            const next = { ...prev };
            delete next[quote.symbol];
            return next;
          });
        }, FLASH_DURATION_MS);
      }
      prevPricesRef.current[quote.symbol] = quote.price;
    });
  }, [quotes]);

  return flashes;
}

export default function StockListPage() {
  const { authFetch } = useAuth();
  const { quotes, tickers, isConnected, isLoading: isQuoteLoading } = useQuoteStream();
  const flashes = usePriceFlash(quotes);

  const [filter, setFilter] = useState<'all' | 'watchlist'>('all');
  const [search, setSearch] = useState('');
  const [watchlist, setWatchlist] = useState<WatchlistItemResponse[]>([]);
  const [isWatchlistLoading, setIsWatchlistLoading] = useState(true);
  const [pendingTicker, setPendingTicker] = useState<string | null>(null);

  useEffect(() => {
    api
      .getWatchlist(authFetch)
      .then(setWatchlist)
      .finally(() => setIsWatchlistLoading(false));
  }, [authFetch]);

  const watchlistByTicker = useMemo(() => {
    const map = new Map<string, WatchlistItemResponse>();
    watchlist.forEach((item) => map.set(item.ticker, item));
    return map;
  }, [watchlist]);

  async function toggleWatchlist(ticker: string) {
    setPendingTicker(ticker);
    try {
      const existing = watchlistByTicker.get(ticker);
      if (existing) {
        await api.removeWatchlistItem(authFetch, existing.id);
        setWatchlist((prev) => prev.filter((item) => item.id !== existing.id));
      } else {
        const created = await api.addWatchlistItem(authFetch, ticker);
        setWatchlist((prev) => [...prev, created]);
      }
    } finally {
      setPendingTicker(null);
    }
  }

  // Keyed by the ticker set so isLoading derives from render-time comparison instead of an
  // effect calling setState synchronously (see react-hooks/set-state-in-effect).
  const requestKey = tickers.join(',');
  const [result, setResult] = useState<{ key: string; statsByTicker: Record<string, StockStats> } | null>(null);
  const isStatsLoading = tickers.length > 0 && result?.key !== requestKey;

  useEffect(() => {
    if (tickers.length === 0) return undefined;
    let cancelled = false;
    Promise.all(
      tickers.map((ticker) =>
        api
          .getHistory(authFetch, ticker, '1d', HISTORY_LIMIT)
          .then((candles) => computeStats(ticker, quotes[ticker]?.name ?? ticker, candles)),
      ),
    ).then((results) => {
      if (cancelled) return;
      const statsByTicker: Record<string, StockStats> = {};
      results.forEach((stats) => {
        if (stats) statsByTicker[stats.ticker] = stats;
      });
      setResult({ key: requestKey, statsByTicker });
    });
    return () => {
      cancelled = true;
    };
    // quotes intentionally excluded: only re-fetch history when the active symbol set changes,
    // not on every live price tick.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [authFetch, tickers, requestKey]);

  const statsByTicker = result?.key === requestKey ? result.statsByTicker : {};
  const rows: StockRow[] = tickers
    .map((ticker) => {
      const stats = statsByTicker[ticker];
      if (!stats) return null;
      const quote = quotes[ticker];
      const price = quote?.price ?? null;
      const prevClose = resolvePrevClose(stats.closes, price);
      const changeValue = price != null && prevClose != null ? price - prevClose : null;
      const changePct = changeValue != null && prevClose ? (changeValue / prevClose) * 100 : null;
      return {
        ...stats,
        price,
        latestVolume: quote?.volume ?? null,
        changeValue,
        changePct,
      };
    })
    .filter((row): row is StockRow => row !== null);

  const filteredRows = (filter === 'watchlist' ? rows.filter((row) => watchlistByTicker.has(row.ticker)) : rows).filter((row) => {
    const query = search.trim().toUpperCase();
    if (!query) return true;
    return row.ticker.toUpperCase().includes(query) || row.name.toUpperCase().includes(query);
  });

  const columns: TableColumn<StockRow>[] = [
    {
      key: 'watch',
      header: '',
      width: pixel(48),
      renderCell: (row) => {
        const isWatched = watchlistByTicker.has(row.ticker);
        return (
          <IconButton
            variant="ghost"
            size="sm"
            icon={
              <Icon
                icon={isWatched ? StarSolidIcon : StarOutlineIcon}
                color={isWatched ? 'accent' : 'secondary'}
              />
            }
            label={isWatched ? `${row.ticker} 관심종목에서 제거` : `${row.ticker} 관심종목에 추가`}
            isLoading={pendingTicker === row.ticker}
            clickAction={() => toggleWatchlist(row.ticker)}
          />
        );
      },
    },
    {
      key: 'ticker',
      header: '종목',
      width: proportional(1.4),
      renderCell: (row) => (
        <Link href={`/symbols/${row.ticker}`} isStandalone>
          <HStack gap={2} align="end">
            <Heading level={5} style={{ flexShrink: 0 }}>
              {row.ticker}
            </Heading>
            <Text type="supporting" size="sm" maxLines={1} style={{ minWidth: 0, flex: 1 }}>
              {row.name}
            </Text>
          </HStack>
        </Link>
      ),
    },
    {
      key: 'price',
      header: '현재가',
      width: proportional(0.9),
      renderCell: (row) => {
        const flash = flashes[row.ticker];
        return (
          <HStack
            gap={1}
            align="center"
            style={{
              padding: 'var(--spacing-0-5) var(--spacing-1-5)',
              borderRadius: 'var(--radius-inner)',
              backgroundColor: flash
                ? flash === 'up'
                  ? 'var(--color-background-red)'
                  : 'var(--color-background-blue)'
                : 'transparent',
              transition: `background-color ${FLASH_DURATION_MS}ms ease-out`,
            }}
          >
            {flash && (
              <span style={{ color: flash === 'up' ? 'var(--color-text-red)' : 'var(--color-text-blue)' }}>
                <Icon icon={flash === 'up' ? 'arrowUp' : 'arrowDown'} color="inherit" size="sm" />
              </span>
            )}
            <Text type="body" hasTabularNumbers>
              {row.price != null ? row.price.toFixed(2) : '—'}
            </Text>
          </HStack>
        );
      },
    },
    {
      key: 'change',
      header: '전일대비',
      width: proportional(1.4),
      renderCell: (row) => <PriceChangeIndicator changeValue={row.changeValue} changePct={row.changePct} />,
    },
    {
      key: 'high',
      header: '고가 (1년)',
      width: proportional(1),
      renderCell: (row) => <Text type="body">{row.high.toFixed(2)}</Text>,
    },
    {
      key: 'low',
      header: '저가 (1년)',
      width: proportional(1),
      renderCell: (row) => <Text type="body">{row.low.toFixed(2)}</Text>,
    },
    {
      key: 'trend',
      header: '연간 추이',
      width: proportional(1.2),
      renderCell: (row) => <Sparkline values={row.closes} />,
    },
    {
      key: 'volume',
      header: '거래량',
      width: proportional(1.4),
      renderCell: (row) => (
        <HStack gap={2} align="center">
          <Text type="body">{row.latestVolume != null ? Math.round(row.latestVolume).toLocaleString('ko-KR') : '—'}</Text>
          <HStack gap={1} align="center">
            <Icon
              icon={row.volumeChangePct >= 0 ? 'arrowUp' : 'arrowDown'}
              color={row.volumeChangePct >= 0 ? 'success' : 'error'}
              size="sm"
            />
            <Text type="supporting" size="sm">
              {Math.abs(row.volumeChangePct).toFixed(1)}%
            </Text>
          </HStack>
        </HStack>
      ),
    },
    {
      key: 'volumeTrend',
      header: '거래량 추이',
      width: proportional(1.2),
      renderCell: (row) => <Sparkline values={row.volumeSeries} isPositive={row.volumeChangePct >= 0} />,
    },
  ];

  const isLoading = isQuoteLoading || isWatchlistLoading || isStatsLoading;

  return (
    <VStack gap={0}>
      <Section padding={4} dividers={['bottom']}>
        <HStack justify="between" align="center" wrap="wrap">
          <VStack gap={1}>
            <Heading level={3}>종목 리스트</Heading>
            <Text type="supporting" size="sm">
              {isConnected ? '실시간 연결됨' : '연결 중...'} · 최근 1년간 활성 종목의 고가·저가와 거래량 추이를 한눈에
              확인하세요
            </Text>
          </VStack>
          <HStack gap={3} align="center">
            <TextInput
              label="티커/이름 검색"
              isLabelHidden
              placeholder="티커 또는 이름 검색"
              value={search}
              onChange={setSearch}
              hasClear
            />
            <SegmentedControl value={filter} onChange={(value) => setFilter(value as 'all' | 'watchlist')} label="보기 필터">
              <SegmentedControlItem value="all" label="전체" />
              <SegmentedControlItem value="watchlist" label="관심종목" />
            </SegmentedControl>
          </HStack>
        </HStack>
      </Section>

      {isLoading ? (
        <Center height={320}>
          <Spinner size="lg" label="불러오는 중" />
        </Center>
      ) : filteredRows.length === 0 ? (
        <Center height={320}>
          <EmptyState
            title={search.trim() ? '검색 결과가 없습니다' : filter === 'watchlist' ? '관심종목이 없습니다' : '표시할 종목이 없습니다'}
            description={
              search.trim()
                ? undefined
                : filter === 'watchlist'
                  ? '전체 목록에서 별표를 눌러 관심종목에 추가하세요.'
                  : undefined
            }
          />
        </Center>
      ) : (
        <Table data={filteredRows} columns={columns} idKey="ticker" hasHover />
      )}
    </VStack>
  );
}
