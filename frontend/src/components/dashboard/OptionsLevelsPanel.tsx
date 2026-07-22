'use client';

import { useEffect, useState } from 'react';
import { Grid } from '@astryxdesign/core/Grid';
import { HStack, VStack } from '@astryxdesign/core/Stack';
import { Text, Heading } from '@astryxdesign/core/Text';
import { Center } from '@astryxdesign/core/Center';
import { Spinner } from '@astryxdesign/core/Spinner';
import { useAuth } from '@/lib/auth-context';
import * as api from '@/lib/api';
import type { OptionsLevel, OptionsLevelsResponse } from '@/lib/types';

function LevelRow({ level, tone }: { level: OptionsLevel; tone: 'red' | 'blue' }) {
  return (
    <HStack justify="between" align="center">
      <Text type="body" hasTabularNumbers style={{ color: `var(--color-text-${tone})` }}>
        {level.strike.toFixed(2)}
      </Text>
      <Text type="supporting" size="sm">
        미결제약정 {level.openInterest.toLocaleString('ko-KR')}
      </Text>
    </HStack>
  );
}

export function OptionsLevelsPanel({ ticker }: { ticker: string }) {
  const { authFetch } = useAuth();
  // Keyed by ticker so isLoading derives from render-time comparison instead of an effect
  // calling setState synchronously (see react-hooks/set-state-in-effect).
  const [result, setResult] = useState<{ key: string; data: OptionsLevelsResponse | null } | null>(null);

  useEffect(() => {
    if (!ticker) return undefined;
    let cancelled = false;
    api
      .getOptionsLevels(authFetch, ticker)
      .then((data) => {
        if (!cancelled) setResult({ key: ticker, data });
      })
      .catch(() => {
        // No listed options for this ticker (CBOE has no listing, or a fetch hiccup) --
        // degrade quietly rather than spin forever, same as the other best-effort panels.
        if (!cancelled) setResult({ key: ticker, data: null });
      });
    return () => {
      cancelled = true;
    };
  }, [authFetch, ticker]);

  if (!result || result.key !== ticker) {
    return (
      <Center height={160}>
        <Spinner size="md" label="불러오는 중" />
      </Center>
    );
  }

  const data = result.data;
  if (!data || (data.resistanceLevels.length === 0 && data.supportLevels.length === 0)) {
    return (
      <Center height={160}>
        <Text type="body" color="secondary">
          옵션 데이터가 없습니다
        </Text>
      </Center>
    );
  }

  return (
    <VStack gap={4}>
      <HStack gap={6} wrap="wrap">
        <VStack gap={1}>
          <Text type="supporting" size="sm">
            맥스페인 (만기 {data.expiration})
          </Text>
          <Heading level={4}>{data.maxPain != null ? data.maxPain.toFixed(2) : '—'}</Heading>
        </VStack>
        <VStack gap={1}>
          <Text type="supporting" size="sm">
            현재가
          </Text>
          <Heading level={4}>{data.spotPrice != null ? data.spotPrice.toFixed(2) : '—'}</Heading>
        </VStack>
      </HStack>

      <Grid columns={{ minWidth: 240, max: 2 }} gap={4}>
        <VStack gap={2}>
          <Text type="label">저항 후보 (콜 미결제약정 상위)</Text>
          <VStack gap={1}>
            {data.resistanceLevels.length > 0 ? (
              [...data.resistanceLevels].reverse().map((level) => <LevelRow key={level.strike} level={level} tone="red" />)
            ) : (
              <Text type="body" color="secondary">
                —
              </Text>
            )}
          </VStack>
        </VStack>
        <VStack gap={2}>
          <Text type="label">지지 후보 (풋 미결제약정 상위)</Text>
          <VStack gap={1}>
            {data.supportLevels.length > 0 ? (
              [...data.supportLevels].reverse().map((level) => <LevelRow key={level.strike} level={level} tone="blue" />)
            ) : (
              <Text type="body" color="secondary">
                —
              </Text>
            )}
          </VStack>
        </VStack>
      </Grid>

      <Text type="supporting" size="sm">
        CBOE 지연시세 기준 · 미결제약정이 몰린 행사가는 딜러 헤지 수요로 지지/저항처럼 작용하는 경향이 있다는 경험칙이며, 확정된 가격대는 아닙니다.
      </Text>
    </VStack>
  );
}
