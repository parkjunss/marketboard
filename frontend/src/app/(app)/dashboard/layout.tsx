import { publicPageMetadata } from '@/lib/page-metadata';

export const metadata = publicPageMetadata('/dashboard', '시장 대시보드', '주요 지수와 대표 차트, 시장 폭과 투자 심리 요약, 상승·하락 종목과 주요 뉴스를 한눈에 확인하세요.');

export default function DashboardLayout({ children }: { children: React.ReactNode }) {
  return children;
}
