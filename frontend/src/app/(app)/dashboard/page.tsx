'use client';

import { DashboardHeader } from '@/components/dashboard/DashboardHeader';
import { MainIndexChart } from '@/components/dashboard/MainIndexChart';
import { MacroMarketGrid } from '@/components/dashboard/MacroMarketGrid';
import { MarketMovers } from '@/components/dashboard/MarketMovers';
import { MarketOverview } from '@/components/dashboard/MarketOverview';
import { NewsPanel } from '@/components/dashboard/NewsPanel';
import { WatchlistOverviewSection } from '@/components/dashboard/WatchlistOverviewSection';
import { MarketBreadthPanel } from '@/components/market/MarketBreadthPanel';
import { MarketSentimentPanel } from '@/components/market/MarketSentimentPanel';
import { SectorRotationTable } from '@/components/market/SectorRotationTable';
import { useAuth } from '@/lib/auth-context';
import Link from 'next/link';
import styles from '@/components/dashboard/dashboard.module.css';

function DashboardSection({ title, moreHref, children }: { title: string; moreHref?: string; children: React.ReactNode }) {
  return <section className={styles.section}><div className={styles.sectionHeader}><h2 className={styles.sectionTitle}>{title}</h2>{moreHref && <Link href={moreHref}>더보기</Link>}</div><div className={styles.sectionBody}>{children}</div></section>;
}

export default function DashboardPage() {
  const { user } = useAuth();
  const name = user?.email?.split('@')[0] || '게스트';

  return <main className={styles.page}>
    <DashboardHeader name={name} showWatchlist={Boolean(user)} />
    <MarketOverview />
    <div className={styles.content}>
      <div className={styles.heroGrid}>
        <MainIndexChart />
        <div className={styles.heroSide}>
          <DashboardSection title="시장 현황"><MarketBreadthPanel /></DashboardSection>
          <DashboardSection title="공포·탐욕 지수"><MarketSentimentPanel /></DashboardSection>
        </div>
      </div>
      <MacroMarketGrid />
      <div className={styles.moversGrid}>
        <MarketMovers />
        <DashboardSection title="주요 뉴스" moreHref="/news"><NewsPanel limit={4} compact /></DashboardSection>
      </div>
      <div className={styles.secondaryGrid}>
        <DashboardSection title="섹터 로테이션"><SectorRotationTable /></DashboardSection>
        {user && <DashboardSection title="관심종목"><WatchlistOverviewSection /></DashboardSection>}
      </div>
    </div>
  </main>;
}
