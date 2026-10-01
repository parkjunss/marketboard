'use client';

import { useEffect, useState } from 'react';
import { Card } from '@astryxdesign/core/Card';
import { VStack, HStack } from '@astryxdesign/core/Stack';
import { Section } from '@astryxdesign/core/Section';
import { Heading, Text } from '@astryxdesign/core/Text';
import { Sparkline } from '@/components/Sparkline';
import { PriceChangeIndicator } from '@/components/PriceChangeIndicator';
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
    api
      .getMarketIndexHistory(authFetch, slug)
      .then((candles) => {
        if (!cancelled) setResult({ key: slug, candles });
      })
      .catch(() => {
        // Best-effort external data (yfinance via the collector) -- degrade to an empty chart
        // rather than leave the card spinning forever, e.g. right after a fresh deploy before the
        // first scheduled refresh has landed.
        if (!cancelled) setResult({ key: slug, candles: [] });
      });
    return () => {
      cancelled = true;
    };
  }, [authFetch, slug]);

  const candles = result?.key === slug ? result.candles : null;
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
      </Section>
    </Card>
  );
}
