'use client';

import { DashboardHeader } from '@/components/dashboard/DashboardHeader';
import { MarketOverview } from '@/components/dashboard/MarketOverview';
import { WatchlistOverviewSection } from '@/components/dashboard/WatchlistOverviewSection';
import { MarketBreadthPanel } from '@/components/market/MarketBreadthPanel';
import { MarketSentimentPanel } from '@/components/market/MarketSentimentPanel';
import { SectorRotationTable } from '@/components/market/SectorRotationTable';
import { useAuth } from '@/lib/auth-context';
import styles from '@/components/dashboard/dashboard.module.css';

function DashboardSection({ title, children }: { title: string; children: React.ReactNode }) {
  return <section className={styles.section}><h2 className={styles.sectionTitle}>{title}</h2><div className={styles.sectionBody}>{children}</div></section>;
}

export default function DashboardPage() {
  const { user } = useAuth();
  const name = user?.email?.split('@')[0] || '투자자';

  return <main className={styles.page}>
    <DashboardHeader name={name} />
    <MarketOverview />
    <div className={styles.content}>
      <div className={styles.marketGrid}>
        <DashboardSection title="시장 현황"><MarketBreadthPanel /></DashboardSection>
        <DashboardSection title="투자 심리"><MarketSentimentPanel /></DashboardSection>
      </div>
      <DashboardSection title="섹터 로테이션"><SectorRotationTable /></DashboardSection>
      <DashboardSection title="관심종목"><WatchlistOverviewSection /></DashboardSection>
    </div>
  </main>;
}
