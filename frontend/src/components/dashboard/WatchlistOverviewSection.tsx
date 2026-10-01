'use client';

import { useEffect, useState } from 'react';
import { VStack, HStack } from '@astryxdesign/core/Stack';
import { Section } from '@astryxdesign/core/Section';
import { Heading, Text } from '@astryxdesign/core/Text';
import { Link } from '@astryxdesign/core/Link';
import { Center } from '@astryxdesign/core/Center';
import { Spinner } from '@astryxdesign/core/Spinner';
import { EmptyState } from '@astryxdesign/core/EmptyState';
import { Table, proportional } from '@astryxdesign/core/Table';
import type { TableColumn } from '@astryxdesign/core/Table';
import { useAuth } from '@/lib/auth-context';
import { useQuoteStream } from '@/lib/quote-stream-context';
import * as api from '@/lib/api';
import type { WatchlistItemResponse } from '@/lib/types';

type WatchlistRow = WatchlistItemResponse & Record<string, unknown>;

export function WatchlistOverviewSection() {
  const { authFetch } = useAuth();
  const { quotes } = useQuoteStream();
  const [items, setItems] = useState<WatchlistItemResponse[] | null>(null);

  useEffect(() => {
    let cancelled = false;
    api.getWatchlist(authFetch).then((data) => {
      if (!cancelled) setItems(data);
    });
    return () => {
      cancelled = true;
    };
  }, [authFetch]);

  const columns: TableColumn<WatchlistRow>[] = [
    {
      key: 'ticker',
      header: '종목',
      width: proportional(1.5),
      renderCell: (item) => (
        <Link href={`/symbols/${item.ticker}`} isStandalone>
          <HStack gap={2} align="end">
            <Heading level={5}>{item.ticker}</Heading>
            <Text type="supporting" size="sm" maxLines={1}>{item.name}</Text>
          </HStack>
        </Link>
      ),
    },
    {
      key: 'price',
      header: '현재가',
      width: proportional(1),
      renderCell: (item) => (
        <Text type="body" hasTabularNumbers>{quotes[item.ticker]?.price?.toFixed(2) ?? '—'}</Text>
      ),
    },
    {
      key: 'volume',
      header: '거래량',
      width: proportional(1),
      renderCell: (item) => (
        <Text type="body" hasTabularNumbers>
          {quotes[item.ticker]?.volume != null ? Math.round(quotes[item.ticker].volume!).toLocaleString('ko-KR') : '—'}
        </Text>
      ),
    },
  ];

  return (
    <Section padding={4} dividers={['top']}>
      <VStack gap={6}>
        <VStack gap={1}>
          <Heading level={4}>관심종목 한눈에 보기</Heading>
          <Text type="supporting" size="sm">
            저장한 종목의 현재가와 거래량을 빠르게 확인하세요
          </Text>
        </VStack>

        {items === null ? (
          <Center height={200}>
            <Spinner size="md" label="불러오는 중" />
          </Center>
        ) : items.length === 0 ? (
          <Center height={200}>
            <EmptyState title="관심종목이 없습니다" description="종목 리스트에서 종목을 워치리스트에 추가해보세요" />
          </Center>
        ) : (
          <Table data={items as WatchlistRow[]} columns={columns} idKey="id" hasHover />
        )}
      </VStack>
    </Section>
  );
}
