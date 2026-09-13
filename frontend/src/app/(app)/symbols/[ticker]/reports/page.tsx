'use client';

import { use, useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/lib/auth-context';
import { createStockReport, getStockReports } from '@/lib/api';
import type { StockReportSummary } from '@/lib/report-types';
import styles from '@/components/reports/report.module.css';

export default function ReportsPage({ params }: { params: Promise<{ ticker: string }> }) {
  const ticker = use(params).ticker.toUpperCase();
  const { authFetch } = useAuth();
  const router = useRouter();
  const [records, setRecords] = useState<StockReportSummary[] | null>(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const lock = useRef(false);
  const [revision, setRevision] = useState(0);
  useEffect(() => {
    let active = true;
    getStockReports(authFetch, ticker).then(rows => { if (active) { setRecords(rows); setError(''); } })
      .catch(() => { if (active) setError('보고서 목록을 불러오지 못했습니다.'); });
    return () => { active = false; };
  }, [authFetch, ticker, revision]);
  async function create() {
    if (lock.current) return;
    lock.current = true; setBusy(true); setError('');
    try { const report = await createStockReport(authFetch, ticker); router.push(`/reports/${report.id}`); }
    catch (e) { setError(e instanceof Error ? e.message : '보고서 생성에 실패했습니다.'); }
    finally { lock.current = false; setBusy(false); }
  }
  return <main className={styles.page}>
    <Link href={`/symbols/${encodeURIComponent(ticker)}`}>← 종목 상세</Link>
    <header><p className={styles.eyebrow}>INVESTMENT REPORT</p><h1>{ticker} 투자 보고서</h1>
      <p>가격·재무·위험 지표를 생성 당시의 자료로 저장합니다. 기존 보고서는 그대로 유지됩니다.</p></header>
    <section className={styles.notice}><h2>근거부터 확인하는 종목 분석</h2>
      <p>유료 AI 없이 실제 수집 자료를 정리합니다. 누락 자료와 최신성 한계를 표시하며, 목표가나 매수 등급은 임의로 만들지 않습니다.</p>
      <button disabled={busy} onClick={() => void create()}>{busy ? '자료 수집 및 보고서 저장 중…' : '새 보고서 생성'}</button>
      <p role="status">{busy ? '재무·정량 분석 수집에 시간이 걸릴 수 있습니다.' : '보고서는 로그인한 사용자만 조회할 수 있습니다.'}</p>
    </section>
    {error && <p role="alert">{error} <button onClick={() => setRevision(v => v + 1)}>목록 다시 조회</button></p>}
    <h2>저장된 보고서 · 최근 50개</h2>
    {records === null && !error && <p role="status">목록을 불러오는 중…</p>}
    {records?.length === 0 && <p>아직 저장된 보고서가 없습니다. 첫 보고서를 생성해 보세요.</p>}
    <ul className={styles.records}>{records?.map((r, index) => <li key={r.id}><Link href={`/reports/${r.id}`}>
      {index === 0 ? '최신 · ' : ''}{r.ticker} · {new Date(r.createdAt).toLocaleString('ko-KR', { timeZone: 'Asia/Seoul' })} KST →
    </Link></li>)}</ul>
  </main>;
}
