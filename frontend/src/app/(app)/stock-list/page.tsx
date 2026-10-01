'use client';

import { useEffect, useMemo, useRef, useState } from 'react';
import { HStack } from '@astryxdesign/core/Stack';
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
import { PageLayout, PageSurface } from '@/components/layout/PageLayout';

const HISTORY_LIMIT = 250; // ~1 trading year of daily candles
const FLASH_DURATION_MS = 700;
const ROW_BATCH_SIZE = 50;

interface StockStats {
  ticker: string;
  name: string;
  high: number | null;
  low: number | null;
  closes: number[];
  volumeSeries: number[];
  volumeChangePct: number | null;
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
  const lastVolume = volumeSeries[volumeSeries.length - 1];
  const previousVolume = volumeSeries.at(-2);
  const volumeChangePct = previousVolume ? ((lastVolume - previousVolume) / previousVolume) * 100 : null;
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

export function StockListContent({ initialFilter = 'all' }: { initialFilter?: 'all' | 'watchlist' }) {
  const { authFetch } = useAuth();
  const { quotes, isConnected } = useQuoteStream();
  const flashes = usePriceFlash(quotes);

  const [filter, setFilter] = useState<'all' | 'watchlist'>(initialFilter);
  const [search, setSearch] = useState('');
  const [rowWindow, setRowWindow] = useState({ key: '', count: ROW_BATCH_SIZE });
  const loadMoreRef = useRef<HTMLDivElement>(null);
  const [watchlist, setWatchlist] = useState<WatchlistItemResponse[]>([]);
  const [catalog, setCatalog] = useState<QuoteResponse[] | null>(null);
  const [isWatchlistLoading, setIsWatchlistLoading] = useState(true);
  const [pendingTicker, setPendingTicker] = useState<string | null>(null);

  useEffect(() => {
    api.getAllQuotes(authFetch).then(setCatalog).catch(() => setCatalog([]));
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

  const [statsByTicker, setStatsByTicker] = useState<Record<string, StockStats>>({});
  const requestedTickers = useRef(new Set<string>());
  const rows: StockRow[] = (catalog ?? []).map((catalogQuote) => {
      const ticker = catalogQuote.symbol;
      const stats = statsByTicker[ticker];
      const quote = quotes[ticker] ?? catalogQuote;
      const closes = stats?.closes ?? [];
      const price = quote?.price ?? closes.at(-1) ?? null;
      const prevClose = resolvePrevClose(closes, price);
      const changeValue = price != null && prevClose != null ? price - prevClose : null;
      const changePct = changeValue != null && prevClose ? (changeValue / prevClose) * 100 : null;
      return {
        ticker,
        name: catalogQuote.name ?? ticker,
        high: stats?.high ?? null,
        low: stats?.low ?? null,
        closes,
        volumeSeries: stats?.volumeSeries ?? [],
        volumeChangePct: stats?.volumeChangePct ?? null,
        price,
        latestVolume: stats?.volumeSeries.at(-1) ?? null,
        changeValue,
        changePct,
      };
    });

  const filteredRows = (filter === 'watchlist' ? rows.filter((row) => watchlistByTicker.has(row.ticker)) : rows).filter((row) => {
    const query = search.trim().toUpperCase();
    if (!query) return true;
    return row.ticker.toUpperCase().includes(query) || row.name.toUpperCase().includes(query);
  });
  const rowWindowKey = `${filter}:${search.trim().toUpperCase()}:${watchlist.map((item) => item.ticker).join(',')}`;
  const visibleCount = rowWindow.key === rowWindowKey ? rowWindow.count : ROW_BATCH_SIZE;
  const visibleRows = filteredRows.slice(0, visibleCount);

  useEffect(() => {
    const missing = visibleRows.filter((row) => !requestedTickers.current.has(row.ticker));
    if (missing.length === 0) return undefined;
    missing.forEach((row) => requestedTickers.current.add(row.ticker));

    Promise.all(
      missing.map((row) =>
        api
          .getHistory(authFetch, row.ticker, '1d', HISTORY_LIMIT)
          .then((candles) => computeStats(row.ticker, row.name, candles))
          .catch(() => null),
      ),
    ).then((results) => {
      setStatsByTicker((current) => {
        const next = { ...current };
        results.forEach((stats) => {
          if (stats) next[stats.ticker] = stats;
        });
        return next;
      });
    });
  }, [authFetch, visibleRows]);

  useEffect(() => {
    const target = loadMoreRef.current;
    if (!target || visibleCount >= filteredRows.length) return undefined;
    const observer = new IntersectionObserver(([entry]) => {
      if (!entry.isIntersecting) return;
      setRowWindow((current) => ({
        key: rowWindowKey,
        count: (current.key === rowWindowKey ? current.count : ROW_BATCH_SIZE) + ROW_BATCH_SIZE,
      }));
    }, { rootMargin: '240px' });
    observer.observe(target);
    return () => observer.disconnect();
  }, [filteredRows.length, rowWindowKey, visibleCount]);

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
      renderCell: (row) => <Text type="body">{row.high != null ? row.high.toFixed(2) : '—'}</Text>,
    },
    {
      key: 'low',
      header: '저가 (1년)',
      width: proportional(1),
      renderCell: (row) => <Text type="body">{row.low != null ? row.low.toFixed(2) : '—'}</Text>,
    },
    {
      key: 'trend',
      header: '연간 추이',
      width: proportional(1.2),
      renderCell: (row) => <Sparkline values={row.closes} />,
    },
    {
      key: 'volume',
      header: '거래량 (일봉)',
      width: proportional(1.4),
      renderCell: (row) => (
        <HStack gap={2} align="center">
          <Text type="body">{row.latestVolume != null ? Math.round(row.latestVolume).toLocaleString('ko-KR') : '—'}</Text>
          {row.volumeChangePct != null && <HStack gap={1} align="center">
            <Icon
              icon={row.volumeChangePct >= 0 ? 'arrowUp' : 'arrowDown'}
              color={row.volumeChangePct >= 0 ? 'success' : 'error'}
              size="sm"
            />
            <span title="전일 대비">
              <Text type="supporting" size="sm">{Math.abs(row.volumeChangePct).toFixed(1)}%</Text>
            </span>
          </HStack>}
        </HStack>
      ),
    },
    {
      key: 'volumeTrend',
      header: '거래량 추이',
      width: proportional(1.2),
      renderCell: (row) => <Sparkline values={row.volumeSeries} isPositive={(row.volumeChangePct ?? 0) >= 0} />,
    },
  ];

  const isLoading = catalog === null || (filter === 'watchlist' && isWatchlistLoading);

  return (
    <PageLayout
      title={initialFilter === 'watchlist' ? '관심종목' : '종목 검색'}
      description={initialFilter === 'watchlist'
        ? `${isConnected ? '실시간 연결됨' : '연결 중'} · 저장한 관심종목의 가격과 거래량 추이를 확인하세요.`
        : `${isConnected ? '실시간 연결됨' : '연결 중'} · DB에 등록된 전체 종목을 검색하고 가격 자료를 비교하세요.`}
      actions={
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
      }
    >
      <PageSurface>
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
        <>
          <Table data={visibleRows} columns={columns} idKey="ticker" hasHover />
          {visibleRows.length < filteredRows.length && <div ref={loadMoreRef} aria-label="종목 더 불러오기"><Center height={56}><Spinner size="sm" label="더 불러오는 중" /></Center></div>}
        </>
      )}
      </PageSurface>
    </PageLayout>
  );
}

export default function StockListPage() {
  return <StockListContent />;
}
