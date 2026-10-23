import { MarketReportList } from '@/components/reports/MarketReports';
import { publicPageMetadata } from '@/lib/page-metadata';

export const metadata = publicPageMetadata('/market-reports', '시황 보고서', '미국장 마감 기준 지수·섹터·뉴스와 자료별 출처를 날짜별 보고서로 확인하세요.');
export default function Page() { return <MarketReportList />; }
