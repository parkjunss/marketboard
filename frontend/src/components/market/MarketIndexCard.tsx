'use client';

import { DataStamp } from '@/components/DataStamp';


import { useCallback } from 'react';
import { useResource } from '@/lib/use-resource';
import { ResourceState } from '@/components/ResourceState';

import { Card } from '@astryxdesign/core/Card';
import { VStack, HStack } from '@astryxdesign/core/Stack';
import { Section } from '@astryxdesign/core/Section';
import { Heading, Text } from '@astryxdesign/core/Text';
import { Sparkline } from '@/components/Sparkline';
import { PriceChangeIndicator } from '@/components/PriceChangeIndicator';
import * as api from '@/lib/api';

export function MarketIndexCard({ slug, name }: { slug: string; name: string }) {
  const load = useCallback((fetcher: api.Fetcher) => api.getMarketIndexHistory(fetcher, slug), [slug]);
  const resource = useResource(load);
  if (resource.status !== 'success') return <Card padding={0}><ResourceState status={resource.status} label={name} onRetry={resource.retry} /></Card>;
  const candles = resource.data;
  if (candles.length === 0) return <Card padding={0}><ResourceState status="empty" label={name} /></Card>;
  const latest = candles && candles.length > 0 ? candles[candles.length - 1] : null;
  // 전일대비(day-over-day), not the full fetched period's change -- a market-overview card should
  // show "오늘 값이 어제 대비 어떻게 움직였는지", not a 6개월 누적 변동률.
  const prevClose = candles && candles.length >= 2 ? candles[candles.length - 2].close : null;
  const changeValue = latest != null && prevClose != null ? latest.close - prevClose : null;
  const changePct = changeValue != null && prevClose ? (changeValue / prevClose) * 100 : null;

  return (
    <Card padding={0}>
      <Section padding={3}>
        <HStack justify="between" align="center" gap={3}>
          <VStack gap={1}>
            <Text type="supporting" size="sm">
              {name}
            </Text>
            <Heading level={4}>{latest != null ? latest.close.toLocaleString('ko-KR', { maximumFractionDigits: 2 }) : '—'}</Heading>
            <PriceChangeIndicator changeValue={changeValue} changePct={changePct} />
          </VStack>
          <Sparkline values={candles?.slice(-24).map((candle) => candle.close) ?? []} width={96} height={42} isPositive={(changeValue ?? 0) >= 0} />
        </HStack>
        <DataStamp source="Yahoo Finance (yfinance)" at={latest?.ts} label="최근 일봉 기준" note="일봉 · 실시간 시세 아님" />
      </Section>
    </Card>
  );
}
