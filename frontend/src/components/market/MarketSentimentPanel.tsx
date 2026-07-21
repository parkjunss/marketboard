'use client';

import { useEffect, useState } from 'react';
import { Card } from '@astryxdesign/core/Card';
import { VStack, HStack } from '@astryxdesign/core/Stack';
import { Grid } from '@astryxdesign/core/Grid';
import { Heading, Text } from '@astryxdesign/core/Text';
import { useAuth } from '@/lib/auth-context';
import * as api from '@/lib/api';
import type { FearGreedResponse, PutCallRatioResponse } from '@/lib/types';

// CNN's 5-way rating maps onto this app's red=상승(탐욕)/blue=하락(공포) convention -- fear
// correlates with selling, greed with buying, so it lines up with the existing price-direction
// color language rather than fighting it.
function ratingColor(rating: string): string {
  const r = rating.toLowerCase();
  if (r.includes('extreme fear') || r === 'fear') return 'var(--color-icon-blue)';
  if (r.includes('extreme greed') || r === 'greed') return 'var(--color-icon-red)';
  return 'var(--color-icon-gray)';
}

const RATING_LABELS_KO: Record<string, string> = {
  'extreme fear': '극단적 공포',
  fear: '공포',
  neutral: '중립',
  greed: '탐욕',
  'extreme greed': '극단적 탐욕',
};

function FearGreedGauge({ data }: { data: FearGreedResponse }) {
  const color = ratingColor(data.rating);
  const label = RATING_LABELS_KO[data.rating.toLowerCase()] ?? data.rating;
  return (
    <VStack gap={3}>
      <Text type="supporting" size="sm">
        공포·탐욕 지수 (CNN, 참고용)
      </Text>
      <HStack gap={3} align="end">
        <Heading level={3} style={{ color }}>
          {data.score.toFixed(0)}
        </Heading>
        <Text type="body" style={{ color }}>
          {label}
        </Text>
      </HStack>
      <HStack gap={0} style={{ height: 8, borderRadius: 'var(--radius-full)', overflow: 'hidden', width: '100%' }}>
        <HStack style={{ width: `${data.score}%`, backgroundColor: color }} />
        <HStack style={{ width: `${100 - data.score}%`, backgroundColor: 'var(--color-background-gray)' }} />
      </HStack>
      <Text type="supporting" size="sm">
        1주 전 {data.history['1w']?.toFixed(0) ?? '—'} · 1개월 전 {data.history['1m']?.toFixed(0) ?? '—'}
      </Text>
    </VStack>
  );
}

function PutCallCard({ data }: { data: PutCallRatioResponse }) {
  return (
    <VStack gap={3}>
      <Text type="supporting" size="sm">
        Put/Call 거래량 비율 ({data.ticker}, 근접 만기 {data.expirationsUsed}개 합산)
      </Text>
      <Heading level={3}>{data.putCallRatio.toFixed(2)}</Heading>
      <Text type="supporting" size="sm">
        1보다 높으면 풋(하락 베팅) 거래량이 콜보다 많다는 뜻 — 방향성 자체보다 평소 대비 급등락 여부를 참고하세요.
      </Text>
    </VStack>
  );
}

export function MarketSentimentPanel() {
  const { authFetch } = useAuth();
  const [fearGreed, setFearGreed] = useState<FearGreedResponse | null | undefined>(undefined);
  const [putCall, setPutCall] = useState<PutCallRatioResponse | null | undefined>(undefined);

  useEffect(() => {
    let cancelled = false;
    api
      .getFearGreed(authFetch)
      .then((data) => {
        if (!cancelled) setFearGreed(data);
      })
      .catch(() => {
        if (!cancelled) setFearGreed(null);
      });
    api
      .getPutCallRatio(authFetch)
      .then((data) => {
        if (!cancelled) setPutCall(data);
      })
      .catch(() => {
        if (!cancelled) setPutCall(null);
      });
    return () => {
      cancelled = true;
    };
  }, [authFetch]);

  // Both are best-effort external calls (CNN's unofficial endpoint, yfinance options data) --
  // fail quietly (hide the card) rather than block the rest of the page on either one.
  if (!fearGreed && !putCall) return null;

  return (
    <Grid columns={2} gap={4}>
      {fearGreed && (
        <Card padding={4}>
          <FearGreedGauge data={fearGreed} />
        </Card>
      )}
      {putCall && (
        <Card padding={4}>
          <PutCallCard data={putCall} />
        </Card>
      )}
    </Grid>
  );
}
