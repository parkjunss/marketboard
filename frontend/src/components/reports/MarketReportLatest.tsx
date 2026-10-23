'use client';

import Link from 'next/link';
import { useResource } from '@/lib/use-resource';
import { getLatestMarketReport } from '@/lib/market-report-api';

export function MarketReportLatest() {
  const resource = useResource(getLatestMarketReport);
  if (resource.status === 'loading') return <p role="status">시황 보고서 발행 현황을 확인하는 중…</p>;
  if (resource.status === 'error') return <p role="alert">발행 현황을 조회하지 못했습니다. <button onClick={resource.retry}>다시 조회</button></p>;
  const { report, state, expectedTradingDate } = resource.data;
  return <div>
    {state === 'UNAVAILABLE' ? <p>미국장 기준 거래일을 확인하지 못했습니다. 저장된 보고서는 계속 읽을 수 있습니다.</p>
      : state === 'DELAYED' ? <p>{expectedTradingDate} 미국장 보고서는 마감 자료를 확인하며 발행을 준비 중입니다.</p>
        : <p>최근 완료된 미국장 기준 보고서입니다. 휴장일에는 같은 보고서를 다시 발행하지 않습니다.</p>}
    {report ? <p><Link href={`/market-reports/${report.id}`}>{report.tradingDate} 미국장 마감 보고서 →</Link>
      {' · '}{report.status === 'PARTIAL' ? '일부 자료 미확인' : '완료'}</p>
      : <p>아직 발행된 보고서가 없습니다.</p>}
  </div>;
}
