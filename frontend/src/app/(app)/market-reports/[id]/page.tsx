import { notFound } from 'next/navigation';
import { MarketReportReader } from '@/components/reports/MarketReports';
import { publicPageMetadata } from '@/lib/page-metadata';

type Props = { params: Promise<{ id: string }> };
export async function generateMetadata({ params }: Props) {
  const { id } = await params;
  return publicPageMetadata(`/market-reports/${id}`, '미국장 시황 보고서', '발행 당시의 시장 수치와 기준 거래일, 출처 및 누락 자료를 확인하세요.');
}
export default async function Page({ params }: Props) {
  const { id } = await params;
  if (!/^[1-9]\d*$/.test(id) || !Number.isSafeInteger(Number(id))) notFound();
  return <MarketReportReader id={Number(id)} />;
}
