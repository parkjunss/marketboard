'use client';

import { useEffect, useState } from 'react';
import { VStack, HStack } from '@astryxdesign/core/Stack';
import { Section } from '@astryxdesign/core/Section';
import { Grid } from '@astryxdesign/core/Grid';
import { Heading, Text } from '@astryxdesign/core/Text';
import { Button } from '@astryxdesign/core/Button';
import { Icon } from '@astryxdesign/core/Icon';
import { Center } from '@astryxdesign/core/Center';
import { Spinner } from '@astryxdesign/core/Spinner';
import { ChartBarIcon } from '@heroicons/react/24/outline';
import { MarketIndexCard } from '@/components/market/MarketIndexCard';
import { MarketBreadthPanel } from '@/components/market/MarketBreadthPanel';
import { MarketSentimentPanel } from '@/components/market/MarketSentimentPanel';
import { SectorRotationTable } from '@/components/market/SectorRotationTable';
import { useAuth } from '@/lib/auth-context';
import * as api from '@/lib/api';
import type { MarketIndexInfo } from '@/lib/types';

/**
 * Public market overview -- no login required. Reuses the exact same data components as the
 * authenticated /market page (they already fetch via useAuth().authFetch, which just omits the
 * Authorization header when there's no session, and the backing endpoints are permitAll'd
 * precisely because none of this data is user-specific). Personalized features (watchlist,
 * portfolio, alerts) stay behind login -- this page is a teaser/showcase, not a full mirror.
 */
export default function OverviewPage() {
  const { user, authFetch } = useAuth();
  const [indices, setIndices] = useState<MarketIndexInfo[] | null>(null);

  useEffect(() => {
    let cancelled = false;
    // authFetch omits the Authorization header when there's no session (accessToken is null) --
    // it just becomes a plain fetch, which is exactly what an anonymous visitor needs here since
    // these endpoints are permitAll'd.
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
        <HStack justify="between" align="center" wrap="wrap">
          <HStack gap={2} align="center">
            <Icon icon={ChartBarIcon} size="md" />
            <Heading level={4}>MarketBoard</Heading>
          </HStack>
          <HStack gap={2}>
            {user ? (
              <Button variant="primary" label="대시보드로 이동" href="/stock-list" />
            ) : (
              <>
                <Button variant="secondary" label="로그인" href="/login" />
                <Button variant="primary" label="회원가입" href="/signup" />
              </>
            )}
          </HStack>
        </HStack>
      </Section>

      <Section padding={4} dividers={['bottom']}>
        <VStack gap={1}>
          <Heading level={3}>시장 개요</Heading>
          <Text type="supporting" size="sm">
            로그인 없이 볼 수 있는 공개 정보입니다. 관심종목·포트폴리오·실시간 알림 등 개인화 기능은 로그인 후 이용할 수 있습니다.
          </Text>
        </VStack>
      </Section>

      <Section padding={4}>
        <VStack gap={4}>
          <MarketSentimentPanel />
          <MarketBreadthPanel />
          <SectorRotationTable />

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
