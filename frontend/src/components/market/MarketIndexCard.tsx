'use client';

import { useEffect, useState } from 'react';
import { Card } from '@astryxdesign/core/Card';
import { VStack, HStack } from '@astryxdesign/core/Stack';
import { Section } from '@astryxdesign/core/Section';
import { Heading, Text } from '@astryxdesign/core/Text';
import { Icon } from '@astryxdesign/core/Icon';
import { Center } from '@astryxdesign/core/Center';
import { Spinner } from '@astryxdesign/core/Spinner';
import { CandleChart } from '@/components/CandleChart';
import { useAuth } from '@/lib/auth-context';
import * as api from '@/lib/api';
import type { CandleResponse } from '@/lib/types';

export function MarketIndexCard({ slug, name }: { slug: string; name: string }) {
  const { authFetch } = useAuth();
  // Keyed by slug so isLoading derives from render-time comparison instead of an effect
  // calling setState synchronously (see react-hooks/set-state-in-effect).
  const [result, setResult] = useState<{ key: string; candles: CandleResponse[] } | null>(null);

  useEffect(() => {
    let cancelled = false;
    api.getMarketIndexHistory(authFetch, slug).then((candles) => {
      if (!cancelled) setResult({ key: slug, candles });
    });
    return () => {
      cancelled = true;
    };
  }, [authFetch, slug]);

  const candles = result?.key === slug ? result.candles : null;
  const changePct =
    candles && candles.length >= 2 ? ((candles[candles.length - 1].close - candles[0].close) / candles[0].close) * 100 : null;

  return (
    <Card padding={0}>
      <VStack gap={0}>
        <Section padding={3} dividers={['bottom']}>
          <HStack justify="between" align="center">
            <Heading level={5}>{name}</Heading>
            {changePct != null && (
              <HStack gap={1} align="center">
                <Icon icon={changePct >= 0 ? 'arrowUp' : 'arrowDown'} color={changePct >= 0 ? 'success' : 'error'} size="sm" />
                <Text type="supporting" size="sm">
                  {Math.abs(changePct).toFixed(2)}%
                </Text>
              </HStack>
            )}
          </HStack>
        </Section>
        <Section padding={2}>
          {candles === null ? (
            <Center height={200}>
              <Spinner size="md" label="불러오는 중" />
            </Center>
          ) : (
            <CandleChart candles={candles} height={200} />
          )}
        </Section>
      </VStack>
    </Card>
  );
}
