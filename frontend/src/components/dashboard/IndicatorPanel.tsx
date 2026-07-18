'use client';

import { useEffect, useState } from 'react';
import { Grid } from '@astryxdesign/core/Grid';
import { VStack } from '@astryxdesign/core/Stack';
import { Text, Heading } from '@astryxdesign/core/Text';
import { Center } from '@astryxdesign/core/Center';
import { Spinner } from '@astryxdesign/core/Spinner';
import { useAuth } from '@/lib/auth-context';
import * as api from '@/lib/api';
import type { IndicatorResponse, IndicatorTypeName } from '@/lib/types';

const LABELS: Record<IndicatorTypeName, string> = {
  SMA20: 'SMA(20)',
  SMA50: 'SMA(50)',
  RSI14: 'RSI(14)',
};

export function IndicatorPanel({ ticker, indicator }: { ticker: string; indicator?: string }) {
  const { authFetch } = useAuth();
  // Keyed by ticker so isLoading derives from render-time comparison instead of an effect
  // calling setState synchronously (see react-hooks/set-state-in-effect).
  const [result, setResult] = useState<{ key: string; items: IndicatorResponse[] } | null>(null);

  useEffect(() => {
    if (!ticker) return undefined;
    let cancelled = false;
    api.getIndicators(authFetch, ticker).then((data) => {
      if (!cancelled) setResult({ key: ticker, items: data });
    });
    return () => {
      cancelled = true;
    };
  }, [authFetch, ticker]);

  if (!ticker) {
    return (
      <Center height={160}>
        <Text type="body" color="secondary">
          종목을 입력하세요
        </Text>
      </Center>
    );
  }
  if (!result || result.key !== ticker) {
    return (
      <Center height={160}>
        <Spinner size="md" label="불러오는 중" />
      </Center>
    );
  }
  const items = result.items;

  const filtered = indicator ? items.filter((item) => item.type === indicator) : items;
  if (filtered.length === 0) {
    return (
      <Center height={160}>
        <Text type="body" color="secondary">
          아직 계산된 지표가 없습니다
        </Text>
      </Center>
    );
  }

  return (
    <Grid columns={filtered.length > 1 ? 3 : 1} gap={4}>
      {filtered.map((item) => (
        <VStack key={item.type} gap={1}>
          <Text type="supporting" size="sm">
            {LABELS[item.type]}
          </Text>
          <Heading level={4}>{item.value.toFixed(2)}</Heading>
        </VStack>
      ))}
    </Grid>
  );
}
