'use client';

import { useEffect, useState } from 'react';
import { Grid } from '@astryxdesign/core/Grid';
import { Center } from '@astryxdesign/core/Center';
import { Spinner } from '@astryxdesign/core/Spinner';
import { PageLayout, PageSurface } from '@/components/layout/PageLayout';
import { MarketIndexCard } from '@/components/market/MarketIndexCard';
import { MarketBreadthPanel } from '@/components/market/MarketBreadthPanel';
import { MarketSentimentPanel } from '@/components/market/MarketSentimentPanel';
import { SectorRotationTable } from '@/components/market/SectorRotationTable';
import { useAuth } from '@/lib/auth-context';
import * as api from '@/lib/api';
import type { MarketIndexInfo } from '@/lib/types';

export default function MarketPage() {
  const { authFetch } = useAuth();
  const [indices, setIndices] = useState<MarketIndexInfo[] | null>(null);

  useEffect(() => {
    let cancelled = false;
    api.getMarketIndices(authFetch).then((data) => { if (!cancelled) setIndices(data); });
    return () => { cancelled = true; };
  }, [authFetch]);

  return <PageLayout title="시장 동향" description="주요 지수와 시장 폭, 투자 심리, 섹터 흐름을 한 화면에서 비교하세요.">
    <PageSurface title="시장 심리"><MarketSentimentPanel /></PageSurface>
    <PageSurface title="시장 현황"><MarketBreadthPanel /></PageSurface>
    <PageSurface title="섹터 로테이션"><SectorRotationTable /></PageSurface>
    <PageSurface title="주요 지수">
      {indices === null ? <Center height={280}><Spinner size="lg" label="불러오는 중" /></Center> :
        <Grid columns={{ minWidth: 280, max: 3 }} gap={4}>{indices.map((index) => <MarketIndexCard key={index.slug} slug={index.slug} name={index.name} />)}</Grid>}
    </PageSurface>
  </PageLayout>;
}
