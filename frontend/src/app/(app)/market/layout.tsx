import { publicPageMetadata } from '@/lib/page-metadata';

export const metadata = publicPageMetadata('/market', '시장 동향', '주요 지수와 시장 폭, 공포·탐욕 지수, 옵션 Put/Call 비율, 거시 지표와 섹터별 기간 수익률을 비교하세요.');

export default function MarketLayout({ children }: { children: React.ReactNode }) {
  return children;
}
