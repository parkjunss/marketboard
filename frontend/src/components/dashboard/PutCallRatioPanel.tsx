'use client';

import { useEffect, useState } from 'react';
import { Grid } from '@astryxdesign/core/Grid';
import { VStack } from '@astryxdesign/core/Stack';
import { Text, Heading } from '@astryxdesign/core/Text';
import { Center } from '@astryxdesign/core/Center';
import { Spinner } from '@astryxdesign/core/Spinner';
import { useAuth } from '@/lib/auth-context';
import * as api from '@/lib/api';
import type { PutCallRatioResponse } from '@/lib/types';

export function PutCallRatioPanel({ ticker }: { ticker: string }) {
  const { authFetch } = useAuth();
  // Keyed by ticker so isLoading derives from render-time comparison instead of an effect
  // calling setState synchronously (see react-hooks/set-state-in-effect).
  const [result, setResult] = useState<{ key: string; data: PutCallRatioResponse | null } | null>(null);

  useEffect(() => {
    if (!ticker) return undefined;
    let cancelled = false;
    api
      .getPutCallRatioForTicker(authFetch, ticker)
      .then((data) => {
        if (!cancelled) setResult({ key: ticker, data });
      })
      .catch(() => {
        // No listed options for this ticker, or a yfinance/collector hiccup -- degrade quietly
        // rather than spin forever, same as the other best-effort external-data panels.
        if (!cancelled) setResult({ key: ticker, data: null });
      });
    return () => {
      cancelled = true;
    };
  }, [authFetch, ticker]);

  if (!result || result.key !== ticker) {
    return (
      <Center height={120}>
        <Spinner size="md" label="불러오는 중" />
      </Center>
    );
  }

  const data = result.data;
  if (!data) {
    return (
      <Center height={120}>
        <Text type="body" color="secondary">
          옵션 데이터가 없습니다
        </Text>
      </Center>
    );
  }

  return (
    <VStack gap={3}>
      <Grid columns={3} gap={4}>
        <VStack gap={1}>
          <Text type="supporting" size="sm">
            Put/Call 비율
          </Text>
          <Heading level={4}>{data.putCallRatio.toFixed(2)}</Heading>
        </VStack>
        <VStack gap={1}>
          <Text type="supporting" size="sm">
            콜 거래량
          </Text>
          <Heading level={4}>{Math.round(data.callVolume).toLocaleString('ko-KR')}</Heading>
        </VStack>
        <VStack gap={1}>
          <Text type="supporting" size="sm">
            풋 거래량
          </Text>
          <Heading level={4}>{Math.round(data.putVolume).toLocaleString('ko-KR')}</Heading>
        </VStack>
      </Grid>
      <Text type="supporting" size="sm">
        근접 만기 {data.expirationsUsed}개 합산 — 1보다 높으면 풋(하락 베팅) 거래량이 콜보다 많다는 뜻입니다.
      </Text>
    </VStack>
  );
}
