'use client';

import { use, useEffect, useState } from 'react';
import Link from 'next/link';
import { useAuth } from '@/lib/auth-context';
import { getStockReport } from '@/lib/api';
import type { StockReportDetail } from '@/lib/report-types';
import { ReportView } from '@/components/reports/ReportView';
import styles from '@/components/reports/report.module.css';

export default function ReportPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  const { authFetch } = useAuth();
  const [state, setState] = useState<{ id: string; report?: StockReportDetail; error?: string } | null>(null);
  const [revision, setRevision] = useState(0);
  useEffect(() => {
    let active = true;
    if (!/^[1-9]\d*$/.test(id) || !Number.isSafeInteger(Number(id))) return;
    getStockReport(authFetch, Number(id)).then(report => { if (active) setState({ id, report }); })
      .catch(() => { if (active) setState({ id, error: '보고서를 찾을 수 없거나 조회하지 못했습니다.' }); });
    return () => { active = false; };
  }, [authFetch, id, revision]);
  const report = state?.id === id ? state.report : undefined;
  if (report) return <ReportView report={report} />;
  const error = !/^[1-9]\d*$/.test(id) || !Number.isSafeInteger(Number(id)) ? '잘못된 보고서 주소입니다.' : state?.id === id ? state.error : undefined;
  return <main className={styles.page}><Link href="/stock-list">종목 리스트</Link><p role={error ? 'alert' : 'status'}>{error ?? '보고서를 불러오는 중…'}</p>
    {error && <button onClick={() => setRevision(v => v + 1)}>다시 조회</button>}</main>;
}
