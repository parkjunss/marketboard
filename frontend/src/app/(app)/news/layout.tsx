import { publicPageMetadata } from '@/lib/page-metadata';

export const metadata = publicPageMetadata('/news', '시장 뉴스', '시장 뉴스의 제목, 출처와 발행 시각을 확인하고 관심 있는 기사의 원문을 읽어보세요.');

export default function NewsLayout({ children }: { children: React.ReactNode }) {
  return children;
}
