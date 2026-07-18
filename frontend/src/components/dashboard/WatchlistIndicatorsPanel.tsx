'use client';

import { useEffect, useState } from 'react';
import { VStack, HStack } from '@astryxdesign/core/Stack';
import { Heading, Text } from '@astryxdesign/core/Text';
import { Link } from '@astryxdesign/core/Link';
import { Center } from '@astryxdesign/core/Center';
import { Spinner } from '@astryxdesign/core/Spinner';
import { EmptyState } from '@astryxdesign/core/EmptyState';
import { IndicatorPanel } from './IndicatorPanel';
import { useAuth } from '@/lib/auth-context';
import * as api from '@/lib/api';
import type { WatchlistItemResponse } from '@/lib/types';

export function WatchlistIndicatorsPanel() {
  const { authFetch } = useAuth();
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

  if (items === null) {
    return (
      <Center height={200}>
        <Spinner size="md" label="불러오는 중" />
      </Center>
    );
  }
  if (items.length === 0) {
    return (
      <Center height={200}>
        <EmptyState title="관심종목이 없습니다" description="종목 리스트에서 종목을 워치리스트에 추가해보세요" />
      </Center>
    );
  }

  return (
    <VStack height={320} isScrollable>
      <VStack gap={4}>
        {items.map((item) => (
          <VStack key={item.id} gap={2}>
            <Link href={`/symbols/${item.ticker}`} isStandalone>
              <HStack gap={2} align="end">
                <Heading level={3}>{item.ticker}</Heading>
                <Text type="supporting" size="base">
                  {item.name}
                </Text>
              </HStack>
            </Link>
            <IndicatorPanel ticker={item.ticker} />
          </VStack>
        ))}
      </VStack>
    </VStack>
  );
}
