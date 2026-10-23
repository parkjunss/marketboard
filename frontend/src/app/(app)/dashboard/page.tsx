'use client';

import { DashboardHeader } from '@/components/dashboard/DashboardHeader';
import { MainIndexChart } from '@/components/dashboard/MainIndexChart';
import { MarketMovers } from '@/components/dashboard/MarketMovers';
import { MarketOverview } from '@/components/dashboard/MarketOverview';
import { NewsPanel } from '@/components/dashboard/NewsPanel';
import { WatchlistOverviewSection } from '@/components/dashboard/WatchlistOverviewSection';
import { MarketBreadthPanel } from '@/components/market/MarketBreadthPanel';
import { FearGreedSummary } from '@/components/market/MarketSentimentPanel';
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
          <DashboardSection title="시장 폭 요약" moreHref="/market#breadth"><MarketBreadthPanel compact /></DashboardSection>
          <DashboardSection title="투자 심리 요약" moreHref="/market#sentiment"><FearGreedSummary /></DashboardSection>
        </div>
      </div>
      <DashboardSection title="시장 상세 비교" moreHref="/market"><p>거시 지표, 섹터별 기간 수익률과 옵션 거래량은 시장 동향에서 비교하세요.</p></DashboardSection>
      <div className={styles.moversGrid}>
        <MarketMovers />
        <DashboardSection title="주요 뉴스" moreHref="/news"><NewsPanel limit={4} compact /></DashboardSection>
      </div>
      {user ? <DashboardSection title="관심종목" moreHref="/watchlist"><WatchlistOverviewSection /></DashboardSection> :
        <DashboardSection title="나만의 관심종목"><p>관심 있는 종목을 저장하고 가격과 거래량을 함께 확인하세요.</p><Link href="/watchlist">로그인하고 관심종목 관리하기 →</Link></DashboardSection>}
    </div>
  </main>;
}
