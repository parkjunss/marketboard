'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { useAuth } from '@/lib/auth-context';
import { getMarketIndexHistory, getMarketBreadth, getPortfolios } from '@/lib/api';
import type { CandleResponse, MarketBreadthResponse, PortfolioSummaryResponse } from '@/lib/types';
import { reviewChange } from '@/lib/investment-review';
import styles from './review.module.css';

const indices = [
  { slug: 'SPX', name: 'S&P 500', role: '미국 대형주' },
  { slug: 'IXIC', name: 'NASDAQ', role: '성장주 흐름' },
  { slug: 'RUT', name: 'Russell 2000', role: '소형주 흐름' },
  { slug: 'VIX', name: 'VIX', role: '예상 변동성' },
  { slug: 'US10Y', name: '미국 10년 금리', role: '할인율 환경' },
  { slug: 'USDKRW', name: 'USD / KRW', role: '원화 투자자의 환율 노출' },
];
type Resource<T> = { data: T; error?: never } | { data?: never; error: string };
const quality = { EMPTY: '보유 없음', UNAVAILABLE: '평가 불가', PARTIAL: '부분 평가', UNVERIFIED: '품질 확인 필요', READY: '가격 관측 양호' };
const number = (value: number | null) => value === null ? '—' : value.toLocaleString('ko-KR', { maximumFractionDigits: 2 });

export default function ReviewPage() {
  const { authFetch } = useAuth();
  const [period, setPeriod] = useState<5 | 21>(5);
  const [revision, setRevision] = useState(0);
  const [histories, setHistories] = useState<Record<string, Resource<CandleResponse[]>>>({});
  const [breadth, setBreadth] = useState<Resource<MarketBreadthResponse> | null>(null);
  const [portfolios, setPortfolios] = useState<Resource<PortfolioSummaryResponse[]> | null>(null);

  useEffect(() => {
    let active = true;
    async function read<T>(request: Promise<T>, accept: (result: Resource<T>) => void) {
      try { const data = await request; if (active) accept({ data }); }
      catch { if (active) accept({ error: '자료를 불러오지 못했습니다. 다시 조회해 주세요.' }); }
    }
    for (const index of indices) {
      void read(getMarketIndexHistory(authFetch, index.slug), result => setHistories(previous => ({ ...previous, [index.slug]: result })));
    }
    void read(getMarketBreadth(authFetch), setBreadth);
    void read(getPortfolios(authFetch), setPortfolios);
    return () => { active = false; };
  }, [authFetch, revision]);

  const loading = !portfolios || !breadth || indices.some(index => !histories[index.slug]);
  const incomplete = portfolios?.data?.filter(portfolio => portfolio.valuationStatus !== 'READY' && portfolio.valuationStatus !== 'EMPTY') ?? [];

  return (
    <main className={styles.page}>
      <header className={styles.header}>
        <div><p className={styles.eyebrow}>INVESTMENT REVIEW</p><h1>투자 점검</h1><p>시장 변화를 읽고, 보유 자료를 확인한 뒤 판단하세요.</p></div>
        <button disabled={loading} onClick={() => { setHistories({}); setBreadth(null); setPortfolios(null); setRevision(value => value + 1); }}>{loading ? '조회 중…' : '다시 조회'}</button>
      </header>

      <div className={styles.toolbar} aria-label="점검 주기">
        <button aria-pressed={period === 5} onClick={() => setPeriod(5)}>주간 점검</button>
        <button aria-pressed={period === 21} onClick={() => setPeriod(21)}>월간 점검</button>
        <span>최근 {period}개 일봉 간격 비교 · 일정 설정과 별개</span>
      </div>

      <section className={styles.notice} aria-labelledby="attention-title">
        <p className={styles.eyebrow}>먼저 확인할 사항</p><h2 id="attention-title">전략 규칙을 연결하기 전입니다</h2>
        <p>현재는 시장과 보유 자료를 점검하는 단계입니다. 매수·매도·유지 신호와 목표 비중 편차는 아직 산출하지 않습니다.</p>
        <p aria-live="polite">{!portfolios ? '보유 자료 확인 중…' : portfolios.data === undefined ? portfolios.error : incomplete.length ? `${incomplete.length}개 포트폴리오의 가격 누락·관측 시점 확인이 필요합니다.` : '아래에서 포트폴리오별 자료 상태를 확인하세요.'}</p>
        <Link href="/backtest">전략 연구 · 기존 백테스트 살펴보기 →</Link>
      </section>

      <section aria-labelledby="market-title">
        <div className={styles.sectionHead}><div><p className={styles.eyebrow}>01 / 시장 환경</p><h2 id="market-title">{period === 5 ? '주간' : '월간'} 변화의 근거</h2></div><Link href="/market">시장 상세 →</Link></div>
        <p className={styles.caption}>yfinance 일봉의 서버 저장 자료. 장 마감 확정·최신 거래일·중간 거래일 누락은 미검증이며, 지표별 기준일이 다를 수 있습니다.</p>
        <div className={styles.grid}>
          {indices.map(index => {
            const resource = histories[index.slug];
            const result = resource?.data ? reviewChange(resource.data, period, index.slug === 'US10Y') : null;
            return <article key={index.slug} className={styles.card}>
              <p className={styles.caption}>{index.role}</p><h3>{index.name}</h3>
              {!resource ? <p>조회 중…</p> : resource.error ? <p role="status">{resource.error}</p> : !result ? <p>자료 부족 또는 일봉 오류 · 비교 불가</p> : <>
                <p className={styles.value}>{number(result.value)}{index.slug === 'US10Y' ? '%' : index.slug === 'USDKRW' ? '원' : ''}</p>
                <p className={styles.change}>{result.change > 0 ? '+' : ''}{number(result.change)} {result.unit}</p>
                <p className={styles.caption}>{result.from} → {result.to}</p>
              </>}
            </article>;
          })}
        </div>
        <article className={styles.breadth}><h3>시장 폭 · 최근 단일 스냅샷</h3>
          {!breadth ? <p>조회 중…</p> : breadth.data === undefined ? <p role="status">{breadth.error}</p> : <>
            <p>상승 {number(breadth.data.advancingCount)} / 하락 {number(breadth.data.decliningCount)} / 보합 {number(breadth.data.unchangedCount)} 종목</p>
            <p className={styles.caption}>대상 {number(breadth.data.universeSize)}종목 · 기준일 {breadth.data.snapshotDate} · 계산 시각 {breadth.data.computedAt}</p>
            <p className={styles.caption}>시장 폭의 주간·월간 변화는 제공하지 않습니다. 전체 시장을 대표하는지와 최신성은 별도 확인이 필요합니다.</p>
          </>}
        </article>
      </section>

      <section aria-labelledby="portfolio-title">
        <div className={styles.sectionHead}><div><p className={styles.eyebrow}>02 / 보유 자료</p><h2 id="portfolio-title">평가 금액보다 자료 상태부터</h2></div><Link href="/portfolio">보유·가격 근거 확인 →</Link></div>
        {!portfolios ? <p>포트폴리오 조회 중…</p> : portfolios.data === undefined ? <p role="status">{portfolios.error}</p> : portfolios.data.length === 0 ? <div className={styles.card}><h3>등록한 포트폴리오가 없습니다</h3><p>보유 종목과 수량을 등록하면 가격 누락과 평가 범위를 확인할 수 있습니다.</p><Link href="/portfolio">포트폴리오 등록 →</Link></div> :
          <div className={styles.grid}>{portfolios.data.map(portfolio => <article key={portfolio.id} className={styles.card}>
            <span className={styles.badge}>{quality[portfolio.valuationStatus]}</span><h3>{portfolio.name}</h3>
            <p className={styles.caption}>가격이 있는 보유분 평가액 · 현금 제외</p><p className={styles.value}>{number(portfolio.totalMarketValue)}</p>
            <p>가격 확인 {portfolio.pricedPositionCount} / {portfolio.positionCount}종목</p>
            <p className={styles.caption}>가격 누락 {portfolio.unpricedPositionCount} · 오래된 관측 {portfolio.stalePositionCount} · 미검증 {portfolio.unverifiedPositionCount}</p>
            <p className={styles.caption}>통화·환산 기준은 현재 API에 없어 합산 금액의 투자 판단 활용 전 확인이 필요합니다.</p>
          </article>)}</div>}
      </section>
      <footer className={styles.notice}><p className={styles.eyebrow}>03 / 판단 준비</p><h2>근거를 확인한 뒤, 직접 결정하세요</h2><p>{period === 5 ? '시장 변화와 가격 자료 상태를 검토하세요.' : '보유 현황을 확인하고, 전략 목표 비중이 정해진 뒤 리밸런싱 여부를 검토하세요.'} 전략 버전 연결, 판단 기록 저장, 이메일·모바일 알림은 후속 단계입니다.</p></footer>
    </main>
  );
}
