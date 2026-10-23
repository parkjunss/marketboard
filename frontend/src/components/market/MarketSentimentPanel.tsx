'use client';

import { DataStamp } from '@/components/DataStamp';


import { useResource } from '@/lib/use-resource';
import { ResourceState } from '@/components/ResourceState';

import { Card } from '@astryxdesign/core/Card';
import { VStack, HStack } from '@astryxdesign/core/Stack';
import { Grid } from '@astryxdesign/core/Grid';
import { Heading, Text } from '@astryxdesign/core/Text';
import * as api from '@/lib/api';
import type { FearGreedResponse, PutCallRatioResponse } from '@/lib/types';
import { Sparkline } from '@/components/Sparkline';

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

function FearGreedGauge({ data, compact = false }: { data: FearGreedResponse; compact?: boolean }) {
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
      {!compact && <Text type="supporting" size="sm">
        1주 전 {data.history['1w']?.toFixed(0) ?? '—'} · 1개월 전 {data.history['1m']?.toFixed(0) ?? '—'}
      </Text>}
      <DataStamp source="CNN Fear & Greed" at={data.timestamp} note="제공자 기준 · 참고용" />
    </VStack>
  );
}

function PutCallCard({ data }: { data: PutCallRatioResponse }) {
  return (
    <VStack gap={3}>
      <Text type="supporting" size="sm">
        Put/Call 거래량 비율 ({data.ticker}, 근접 만기 {data.expirationsUsed}개 합산)
      </Text>
      <HStack gap={3} align="center">
        <Heading level={3}>{data.putCallRatio.toFixed(2)}</Heading>
        {data.history && data.history.length > 1 && (
          <Sparkline values={data.history.map((point) => point.ratio)} width={120} height={32} />
        )}
      </HStack>
      <Text type="supporting" size="sm">
        1보다 높으면 풋(하락 베팅) 거래량이 콜보다 많다는 뜻 — 방향성 자체보다 평소 대비 급등락 여부를 참고하세요.
      </Text>
      <DataStamp source="Yahoo Finance (yfinance)" note="옵션 거래량 스냅샷 · 실시간 시세 아님" />
    </VStack>
  );
}

export function FearGreedSummary() {
  const resource = useResource(api.getFearGreed);
  if (resource.status !== 'success') return <ResourceState status={resource.status} label="공포·탐욕 지수" onRetry={resource.retry} />;
  return resource.data ? <FearGreedGauge data={resource.data} compact /> : <ResourceState status="empty" label="공포·탐욕 지수" />;
}

export function MarketSentimentPanel() {
  const fearGreed = useResource(api.getFearGreed);
  const putCall = useResource(api.getPutCallRatio);
  return <Grid columns={{ minWidth: 200, max: 2 }} gap={4}>
    <Card padding={4}>
      {fearGreed.status !== 'success' ? <ResourceState status={fearGreed.status} label="공포·탐욕 지수" onRetry={fearGreed.retry} /> :
        fearGreed.data ? <FearGreedGauge data={fearGreed.data} /> : <ResourceState status="empty" label="공포·탐욕 지수" />}
    </Card>
    <Card padding={4}>
      {putCall.status !== 'success' ? <ResourceState status={putCall.status} label="Put/Call 비율" onRetry={putCall.retry} /> :
        putCall.data ? <PutCallCard data={putCall.data} /> : <ResourceState status="empty" label="Put/Call 비율" />}
    </Card>
  </Grid>;
}
