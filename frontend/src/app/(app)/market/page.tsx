'use client';

import { useResource } from '@/lib/use-resource';
import { ResourceState } from '@/components/ResourceState';

import { Grid } from '@astryxdesign/core/Grid';
import { PageLayout, PageSurface } from '@/components/layout/PageLayout';
import { MarketIndexCard } from '@/components/market/MarketIndexCard';
import { MarketBreadthPanel } from '@/components/market/MarketBreadthPanel';
import { MarketSentimentPanel } from '@/components/market/MarketSentimentPanel';
import { SectorRotationTable } from '@/components/market/SectorRotationTable';
import { MacroMarketGrid, MACRO_SLUGS } from '@/components/dashboard/MacroMarketGrid';
import * as api from '@/lib/api';

export default function MarketPage() {
  const resource = useResource(api.getMarketIndices);
  const indices = resource.data?.filter(index => !MACRO_SLUGS.has(index.slug)) ?? [];

  return <PageLayout title="시장 동향" description="시장 폭과 투자 심리의 세부 수치, 섹터별 기간 수익률과 거시 지표를 비교하세요. 각 자료의 기준 시각은 다를 수 있습니다.">
    <section id="sentiment" style={{ scrollMarginTop: 80 }}><PageSurface title="시장 심리 상세"><MarketSentimentPanel /></PageSurface></section>
    <section id="breadth" style={{ scrollMarginTop: 80 }}><PageSurface title="시장 폭 상세"><MarketBreadthPanel /></PageSurface></section>
    <PageSurface title="거시 지표"><MacroMarketGrid /></PageSurface>
    <PageSurface title="섹터 로테이션"><SectorRotationTable showTitle={false} /></PageSurface>
    <PageSurface title="주요 지수">
      {resource.status !== 'success' ? <ResourceState status={resource.status} label="주요 지수" onRetry={resource.retry} /> :
        indices.length === 0 ? <ResourceState status="empty" label="주요 지수" /> :
        <Grid columns={{ minWidth: 280, max: 3 }} gap={4}>{indices.map((index) => <MarketIndexCard key={index.slug} slug={index.slug} name={index.name} />)}</Grid>}
    </PageSurface>
  </PageLayout>;
}
