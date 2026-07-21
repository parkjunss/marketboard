'use client';

import { useEffect, useState } from 'react';
import { VStack } from '@astryxdesign/core/Stack';
import { Section } from '@astryxdesign/core/Section';
import { Grid } from '@astryxdesign/core/Grid';
import { Heading, Text } from '@astryxdesign/core/Text';
import { Center } from '@astryxdesign/core/Center';
import { Spinner } from '@astryxdesign/core/Spinner';
import { MarketIndexCard } from '@/components/market/MarketIndexCard';
import { MarketBreadthPanel } from '@/components/market/MarketBreadthPanel';
import { MarketSentimentPanel } from '@/components/market/MarketSentimentPanel';
import { useAuth } from '@/lib/auth-context';
import * as api from '@/lib/api';
import type { MarketIndexInfo } from '@/lib/types';

export default function MarketPage() {
  const { authFetch } = useAuth();
  const [indices, setIndices] = useState<MarketIndexInfo[] | null>(null);

  useEffect(() => {
    let cancelled = false;
    api.getMarketIndices(authFetch).then((data) => {
      if (!cancelled) setIndices(data);
    });
    return () => {
      cancelled = true;
    };
  }, [authFetch]);

  return (
    <VStack gap={0}>
      <Section padding={4} dividers={['bottom']}>
        <VStack gap={1}>
          <Heading level={3}>시장 지표</Heading>
          <Text type="supporting" size="sm">
            S&amp;P 500, NASDAQ 등 주요 지수의 최근 일봉 추이 (yfinance 기반, 일 단위 갱신 — 실시간 아님)
          </Text>
        </VStack>
      </Section>

      <Section padding={4}>
        <VStack gap={4}>
          <MarketSentimentPanel />
          <MarketBreadthPanel />

          {indices === null ? (
            <Center height={320}>
              <Spinner size="lg" label="불러오는 중" />
            </Center>
          ) : (
            <Grid columns={3} gap={4}>
              {indices.map((index) => (
                <MarketIndexCard key={index.slug} slug={index.slug} name={index.name} />
              ))}
            </Grid>
          )}
        </VStack>
      </Section>
    </VStack>
  );
}
