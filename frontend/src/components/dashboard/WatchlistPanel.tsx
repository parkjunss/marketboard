'use client';

import { useEffect, useState } from 'react';
import { Table, proportional } from '@astryxdesign/core/Table';
import type { TableColumn } from '@astryxdesign/core/Table';
import { Text } from '@astryxdesign/core/Text';
import { Link } from '@astryxdesign/core/Link';
import { Center } from '@astryxdesign/core/Center';
import { Spinner } from '@astryxdesign/core/Spinner';
import { EmptyState } from '@astryxdesign/core/EmptyState';
import { useAuth } from '@/lib/auth-context';
import { useQuoteStream } from '@/lib/quote-stream-context';
import * as api from '@/lib/api';
import type { WatchlistItemResponse } from '@/lib/types';

interface WatchlistRow extends WatchlistItemResponse, Record<string, unknown> {}

export function WatchlistPanel() {
  const { authFetch } = useAuth();
  const { quotes } = useQuoteStream();
  const [items, setItems] = useState<WatchlistRow[] | null>(null);

  useEffect(() => {
    api.getWatchlist(authFetch).then((data) => setItems(data as WatchlistRow[]));
  }, [authFetch]);

  if (items === null) {
    return (
      <Center height={280}>
        <Spinner size="md" label="불러오는 중" />
      </Center>
    );
  }
  if (items.length === 0) {
    return (
      <Center height={280}>
        <EmptyState title="관심종목이 없습니다" />
      </Center>
    );
  }

  const columns: TableColumn<WatchlistRow>[] = [
    {
      key: 'ticker',
      header: '종목',
      width: proportional(1),
      renderCell: (row) => (
        <Link href={`/symbols/${row.ticker}`} isStandalone>
          {row.ticker}
        </Link>
      ),
    },
    {
      key: 'price',
      header: '현재가',
      width: proportional(1),
      renderCell: (row) => <Text type="body">{quotes[row.ticker]?.price?.toFixed(2) ?? '—'}</Text>,
    },
  ];

  return <Table data={items} columns={columns} idKey="id" hasHover />;
}
