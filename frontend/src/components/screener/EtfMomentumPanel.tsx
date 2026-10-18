'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { ApiError, getEtfMomentum } from '@/lib/api';
import { useAuth } from '@/lib/auth-context';
import type { EtfMomentumResult } from '@/lib/etf-momentum-types';
import styles from './screener.module.css';

export function EtfMomentumPanel() {
  const { authFetch } = useAuth();
  const [result, setResult] = useState<EtfMomentumResult | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [refresh, setRefresh] = useState(0);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      try {
        const data = await getEtfMomentum(authFetch);
        if (!cancelled) setResult(data);
      } catch (cause) {
        if (!cancelled) setError(cause instanceof ApiError ? cause.message : 'ETF 전략 조회에 실패했습니다. 다시 조회해 주세요.');
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    void load();
    return () => { cancelled = true; };
  }, [authFetch, refresh]);

  const number = (value: number | null) => value === null ? '—' : value.toFixed(2);
  const percent = (value: number | null) => value === null ? '—'
    : new Intl.NumberFormat('ko-KR', { style: 'percent', minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(value);
  return <section className={styles.panel} aria-labelledby="etf-momentum-title" aria-busy={loading}>
    <div className={styles.panelHeader}>
      <h2 id="etf-momentum-title">ETF 모멘텀 전략 <span>연구용</span></h2>
      <Link href="/backtest#etf-comparison" className={styles.secondaryButton}>전략 백테스트 비교</Link>
      <button type="button" className={styles.secondaryButton} disabled={loading} onClick={() => {
        setLoading(true); setResult(null); setError(null); setRefresh((value) => value + 1);
      }}>다시 조회</button>
    </div>
    <div className={styles.etfExplanation}>
      <p>매월 말 상위 3개를 먼저 고른 뒤, 종가가 200일선보다 높은 ETF에 각각 ⅓을 배정합니다. 미충족 비중은 현금으로 유지합니다.</p>
      <p>월 1회 판단하는 전략이므로 종가와 200일선은 지난 확정 월말 기준입니다. 월말이 휴장일이면 그 이전 마지막 일봉을 사용하며, 현재 가격을 뜻하지 않습니다. 200일선은 해당 기준일까지 관측된 최근 200개 일봉 종가의 평균입니다.</p>
      <p>배당·분할 조정 여부가 API에서 확인되지 않은 참고 계산입니다. 자동 매매 신호가 아닙니다.</p>
      {loading && <p role="status">서버의 ETF 전략 결과를 조회하고 있습니다.</p>}
      {error && <p role="alert">{error}</p>}
      {result && <>
        <p>판단 월말: {result.asOf} · 모멘텀: {result.startDate} → {result.endDate} (각 날짜 이전 마지막 일봉)</p>
        <p role="status">{result.ready
          ? `규칙상 현금 비중: ${percent(result.cashWeight)} · 실제 주문 전 가격 및 데이터 검증 필요`
          : '제안 보류: 6개 ETF의 충분한 이력과 동일 거래일이 필요합니다. 종목 등록·일봉 수집 상태를 확인해 주세요.'}</p>
      </>}
    </div>
    {result && <div className={styles.tableScroll}>
      <table className={styles.table}>
        <caption className={styles.etfCaption}>최근 1개월을 제외한 11개월 모멘텀 · 동률은 티커 알파벳순</caption>
        <thead><tr>{['순위', 'ETF', '일봉 기준일', '모멘텀', '판단 기준 종가 (USD)', '판단 기준 200일선 (USD)', '규칙상 비중', '판단 근거'].map((label) => <th key={label} scope="col">{label}</th>)}</tr></thead>
        <tbody>{result.rows.map((row) => <tr key={row.ticker}>
          <td>{row.rank ?? '—'}</td><th scope="row">{row.ticker}</th><td>{row.sessionDate ?? '—'}</td>
          <td>{number(row.momentum)}{row.momentum !== null && '%'}</td><td>{number(row.close)}</td><td>{number(row.sma200)}</td>
          <td>{percent(row.weight)}</td><td>{row.reason}</td>
        </tr>)}</tbody>
      </table>
    </div>}
  </section>;
}
