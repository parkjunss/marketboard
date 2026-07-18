'use client';

import { useEffect, useState } from 'react';
import { Card } from '@astryxdesign/core/Card';
import { VStack, HStack } from '@astryxdesign/core/Stack';
import { Section } from '@astryxdesign/core/Section';
import { Grid } from '@astryxdesign/core/Grid';
import { Heading, Text } from '@astryxdesign/core/Text';
import { Link } from '@astryxdesign/core/Link';
import { Center } from '@astryxdesign/core/Center';
import { Spinner } from '@astryxdesign/core/Spinner';
import { EmptyState } from '@astryxdesign/core/EmptyState';
import { ChartPanel } from './ChartPanel';
import { useAuth } from '@/lib/auth-context';
import * as api from '@/lib/api';
import type { WatchlistItemResponse } from '@/lib/types';

export function WatchlistOverviewSection() {
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

  return (
    <Section padding={4} dividers={['top']}>
      <VStack gap={6}>
        <VStack gap={1}>
          <Heading level={4}>관심종목 한눈에 보기</Heading>
          <Text type="supporting" size="sm">
            워치리스트에 담은 종목의 차트를 자동으로 표시합니다
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
          <Grid columns={2} gap={4}>
            {items.map((item) => (
              <Card key={item.id} padding={0}>
                <VStack gap={0}>
                  <Section padding={3} dividers={['bottom']}>
                    <Link href={`/symbols/${item.ticker}`} isStandalone>
                      <HStack gap={2} align="end">
                        <Heading level={3}>{item.ticker}</Heading>
                        <Text type="supporting" size="base">
                          {item.name}
                        </Text>
                      </HStack>
                    </Link>
                  </Section>
                  <Section padding={4}>
                    <ChartPanel ticker={item.ticker} />
                  </Section>
                </VStack>
              </Card>
            ))}
          </Grid>
        )}
      </VStack>
    </Section>
  );
}
