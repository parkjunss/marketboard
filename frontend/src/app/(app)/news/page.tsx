'use client';

import { NewsPanel } from '@/components/dashboard/NewsPanel';
import { PageLayout } from '@/components/layout/PageLayout';

export default function NewsPage() {
  return (
    <PageLayout title="주요 뉴스" description="시장에 영향을 주는 최신 주요 뉴스를 한곳에서 확인하세요.">
      <NewsPanel />
    </PageLayout>
  );
}
