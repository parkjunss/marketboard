'use client';

import { DataStamp } from '@/components/DataStamp';


import { useResource } from '@/lib/use-resource';
import { ResourceState } from '@/components/ResourceState';

import { Card } from '@astryxdesign/core/Card';
import { VStack, HStack } from '@astryxdesign/core/Stack';
import { Grid } from '@astryxdesign/core/Grid';
import { Heading, Text } from '@astryxdesign/core/Text';
import * as api from '@/lib/api';

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

export function MarketBreadthPanel({ compact = false }: { compact?: boolean }) {
  const resource = useResource(api.getMarketBreadth);
  if (resource.status !== 'success') return <ResourceState status={resource.status} label="시장 현황" onRetry={resource.retry} />;
  const breadth = resource.data;
  if (!breadth) return <ResourceState status="empty" label="시장 현황" />;

  return (
    <div>
    <Grid columns={{ minWidth: 200, max: compact ? 1 : 2 }} gap={4}>
      <Card padding={4}>
        <VStack gap={3}>
          <Text type="supporting" size="sm">
            상승/하락 종목 ({breadth.universeSize.toLocaleString('ko-KR')}개 중)
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
      {!compact && <Card padding={4}>
        <VStack gap={3}>
          <Text type="supporting" size="sm">52주 신고가·신저가 갱신</Text>
          <HStack gap={6}>
            <BreadthStat label="신고가" value={breadth.new52wHighCount} color="var(--color-text-red)" />
            <BreadthStat label="신저가" value={breadth.new52wLowCount} color="var(--color-text-blue)" />
          </HStack>
        </VStack>
      </Card>}
    </Grid>
    <DataStamp source="MarketBoard 저장 일봉 집계" at={breadth.snapshotDate} label="스냅샷 날짜" />
    <DataStamp source="MarketBoard" at={breadth.computedAt} label="서버 산출 시각" note="개별 종목의 가격 기준 시각과 다를 수 있습니다." />
    </div>
  );
}
