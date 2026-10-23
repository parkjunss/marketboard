import { publicPageMetadata } from '@/lib/page-metadata';

export const metadata = publicPageMetadata('/stock-list', '종목 검색', '티커와 이름으로 종목을 검색하고 가격, 등락률, 거래량과 추이를 비교하세요. 종목 상세와 관심종목 관리는 로그인 후 이용할 수 있습니다.');

export default function StockListLayout({ children }: { children: React.ReactNode }) {
  return children;
}
