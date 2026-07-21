'use client';

import { useEffect, useState } from 'react';
import { Card } from '@astryxdesign/core/Card';
import { VStack, HStack } from '@astryxdesign/core/Stack';
import { Grid } from '@astryxdesign/core/Grid';
import { Heading, Text } from '@astryxdesign/core/Text';
import { Center } from '@astryxdesign/core/Center';
import { Spinner } from '@astryxdesign/core/Spinner';
import { useAuth } from '@/lib/auth-context';
import * as api from '@/lib/api';
import type { MarketBreadthResponse } from '@/lib/types';

function BreadthStat({ label, value, color }: { label: string; value: number; color?: string }) {
  return (
    <VStack gap={0}>
      <Heading level={4} style={color ? { color } : undefined}>
        {value.toLocaleString('ko-KR')}
      </Heading>
      <Text type="supporting" size="sm">
        {label}
      </Text>
    </VStack>
  );
}

/** Proportional advance/decline bar — a quick "market mood at a glance" visual, same custom-SVG-
 * style approach as Sparkline/PriceChangeIndicator elsewhere in this app (no chart library needed
 * for something this simple). Widths are relative (%), not px, per the Astryx no-raw-px rule. */
function AdvanceDeclineBar({ advancing, declining, unchanged }: { advancing: number; declining: number; unchanged: number }) {
  const total = advancing + declining + unchanged;
  if (total === 0) return null;
  const advancingPct = (advancing / total) * 100;
  const decliningPct = (declining / total) * 100;
  const unchangedPct = 100 - advancingPct - decliningPct;
  return (
    <HStack gap={0} style={{ height: 8, borderRadius: 'var(--radius-full)', overflow: 'hidden', width: '100%' }}>
      <HStack style={{ width: `${advancingPct}%`, backgroundColor: 'var(--color-icon-red)' }} />
      <HStack style={{ width: `${unchangedPct}%`, backgroundColor: 'var(--color-background-gray)' }} />
      <HStack style={{ width: `${decliningPct}%`, backgroundColor: 'var(--color-icon-blue)' }} />
    </HStack>
  );
}

export function MarketBreadthPanel() {
  const { authFetch } = useAuth();
  const [breadth, setBreadth] = useState<MarketBreadthResponse | null | undefined>(undefined);

  useEffect(() => {
    let cancelled = false;
    api
      .getMarketBreadth(authFetch)
      .then((data) => {
        if (!cancelled) setBreadth(data);
      })
      .catch(() => {
        if (!cancelled) setBreadth(null);
      });
    return () => {
      cancelled = true;
    };
  }, [authFetch]);

  if (breadth === undefined) {
    return (
      <Center height={140}>
        <Spinner size="md" label="불러오는 중" />
      </Center>
    );
  }

  if (breadth === null) {
    return null; // no snapshot computed yet -- fail quietly rather than block the rest of the page
  }

  return (
    <Grid columns={2} gap={4}>
      <Card padding={4}>
        <VStack gap={3}>
          <Text type="supporting" size="sm">
            상승/하락 종목 ({breadth.universeSize.toLocaleString('ko-KR')}개 중, {breadth.snapshotDate} 기준)
          </Text>
          <HStack gap={6}>
            <BreadthStat label="상승" value={breadth.advancingCount} color="var(--color-text-red)" />
            <BreadthStat label="하락" value={breadth.decliningCount} color="var(--color-text-blue)" />
            <BreadthStat label="보합" value={breadth.unchangedCount} />
          </HStack>
          <AdvanceDeclineBar
            advancing={breadth.advancingCount}
            declining={breadth.decliningCount}
            unchanged={breadth.unchangedCount}
          />
        </VStack>
      </Card>
      <Card padding={4}>
        <VStack gap={3}>
          <Text type="supporting" size="sm">52주 신고가·신저가 갱신</Text>
          <HStack gap={6}>
            <BreadthStat label="신고가" value={breadth.new52wHighCount} color="var(--color-text-red)" />
            <BreadthStat label="신저가" value={breadth.new52wLowCount} color="var(--color-text-blue)" />
          </HStack>
        </VStack>
      </Card>
    </Grid>
  );
}
