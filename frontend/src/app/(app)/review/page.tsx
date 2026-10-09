'use client';

import { useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import {
  ChartBarSquareIcon, CircleStackIcon, ClipboardDocumentCheckIcon,
  LightBulbIcon, ShieldExclamationIcon,
} from '@heroicons/react/24/outline';
import { useAuth } from '@/lib/auth-context';
import { getMarketIndexHistory, getMarketBreadth, getPortfolios, createReview, getReviews, getReview, getReviewDecisions, createReviewDecision } from '@/lib/api';
import type { ReviewDecision, ReviewDecisionChoice, ReviewDetail, ReviewSummary, ReviewResource } from '@/lib/types';
import type { CandleResponse, MarketBreadthResponse, PortfolioSummaryResponse } from '@/lib/types';
import { reviewChange } from '@/lib/investment-review';
import { buildReviewInsights } from '@/lib/investment-review-insights';
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
const decisionLabel = { EXECUTE: '실행', DEFER: '보류', HOLD: '유지' };
const number = (value: number | null) => value === null ? '—' : value.toLocaleString('ko-KR', { maximumFractionDigits: 2 });
const restored = <T,>(resource: ReviewResource<T>): Resource<T> => resource.data === null
  ? { error: resource.error ?? '저장 당시 자료 없음' } : { data: resource.data };

export default function ReviewPage() {
  const { authFetch } = useAuth();
  const [period, setPeriod] = useState<5 | 21>(5);
  const [revision, setRevision] = useState(0);
  const [histories, setHistories] = useState<Record<string, Resource<CandleResponse[]>>>({});
  const [breadth, setBreadth] = useState<Resource<MarketBreadthResponse> | null>(null);
  const [portfolios, setPortfolios] = useState<Resource<PortfolioSummaryResponse[]> | null>(null);
  const [saved, setSaved] = useState<ReviewDetail | null>(null);
  const [records, setRecords] = useState<ReviewSummary[]>([]);
  const [recordError, setRecordError] = useState<string | null>(null);
  const [recordBusy, setRecordBusy] = useState(false);
  const [decisions, setDecisions] = useState<ReviewDecision[]>([]);
  const [choice, setChoice] = useState<ReviewDecisionChoice>('HOLD');
  const [reason, setReason] = useState('');
  const [followUpDate, setFollowUpDate] = useState('');
  const generation = useRef(0);

  useEffect(() => {
    let active = true;
    getReviews(authFetch).then(rows => { if (active) setRecords(rows); })
      .catch(() => { if (active) setRecordError('저장 기록 목록을 불러오지 못했습니다.'); });
    return () => { active = false; };
  }, [authFetch]);

  async function openRecord(id?: number) {
    if (recordBusy) return;
    setRecordBusy(true); setRecordError(null);
    try {
      const detail = id === undefined ? await createReview(authFetch, period) : await getReview(authFetch, id);
      const supportedVersion =
        (detail.payload.schemaVersion === 1 && detail.payload.calculationVersion === 'observed-bars-v1')
        || (detail.payload.schemaVersion === 2
          && detail.payload.calculationVersion === 'observed-bars-v1+portfolio-ledger-v1')
        || (detail.payload.schemaVersion === 3
          && detail.payload.calculationVersion === 'observed-bars-v1+portfolio-ledger-v1+strategy-rules-v1');
      if (!supportedVersion) {
        throw new Error('이 기록은 현재 화면에서 지원하지 않는 계산 버전입니다.');
      }
      generation.current++;
      setSaved(detail); setPeriod(detail.period);
      setHistories(Object.fromEntries(Object.entries(detail.payload.histories).map(([key, value]) => [key, restored(value)])));
      setBreadth(restored(detail.payload.breadth)); setPortfolios(restored(detail.payload.portfolios));
      void getReviewDecisions(authFetch, detail.id).then(setDecisions).catch(() => setDecisions([]));
      setRecords(previous => [{ id: detail.id, period: detail.period, createdAt: detail.createdAt }, ...previous.filter(row => row.id !== detail.id)].sort((a, b) => b.id - a.id).slice(0, 50));
    } catch (error) { setRecordError(error instanceof Error ? error.message : '점검 기록 처리에 실패했습니다.'); }
    finally { setRecordBusy(false); }
  }

  useEffect(() => {
    if (saved) return;
    let active = true;
    const current = generation.current;
    async function read<T>(request: Promise<T>, accept: (result: Resource<T>) => void) {
      try { const data = await request; if (active && current === generation.current) accept({ data }); }
      catch { if (active && current === generation.current) accept({ error: '자료를 불러오지 못했습니다. 다시 조회해 주세요.' }); }
    }
    for (const index of indices) {
      void read(getMarketIndexHistory(authFetch, index.slug), result => setHistories(previous => ({ ...previous, [index.slug]: result })));
    }
    void read(getMarketBreadth(authFetch), setBreadth);
    void read(getPortfolios(authFetch), setPortfolios);
    return () => { active = false; };
  }, [authFetch, revision, saved]);

  const loading = !portfolios || !breadth || indices.some(index => !histories[index.slug]);
  const incomplete = portfolios?.data?.filter(portfolio => portfolio.valuationStatus !== 'READY' && portfolio.valuationStatus !== 'EMPTY') ?? [];
  const changes = indices.map(index => {
    const data = histories[index.slug]?.data;
    const result = data ? reviewChange(data, period, index.slug === 'US10Y') : null;
    return result ? { ...result, slug: index.slug, name: index.name } : null;
  });
  const rising = changes.filter(result => result && result.change > 0).length;
  const falling = changes.filter(result => result && result.change < 0).length;
  const insights = buildReviewInsights(
    changes.filter(result => result !== null), breadth?.data, portfolios?.data,
    saved?.payload.strategy?.data ?? undefined,
  );

  async function saveDecision(event: React.FormEvent) {
    event.preventDefault();
    if (!saved || !reason.trim() || recordBusy) return;
    setRecordBusy(true); setRecordError(null);
    try {
      const decision = await createReviewDecision(authFetch, saved.id, { choice, reason: reason.trim(), followUpDate: choice === 'DEFER' && followUpDate ? followUpDate : null });
      setDecisions(previous => [decision, ...previous]); setReason(''); setFollowUpDate('');
    } catch (error) { setRecordError(error instanceof Error ? error.message : '판단 기록 저장에 실패했습니다.'); }
    finally { setRecordBusy(false); }
  }

  return (
    <main className={styles.page}>
      <header className={styles.header}>
        <div><h1>투자 점검</h1><p>시장 상황, 포트폴리오 위험, 투자 가설을 한 번에 점검합니다.</p></div>
        <div className={styles.headerMeta}>
          {saved && <span>불변 스냅샷 #{saved.id}</span>}
          <span>계산 버전 {saved?.payload.schemaVersion ?? 3}</span>
        </div>
        <button disabled={recordBusy} onClick={() => { generation.current++; setSaved(null); setHistories({}); setBreadth(null); setPortfolios(null); setRevision(value => value + 1); }}>{saved ? '현재 자료로 돌아가기' : loading ? '조회 다시 시작' : '다시 조회'}</button>
      </header>

      <div className={styles.periodBar} aria-label="점검 주기">
        <button disabled={!!saved || recordBusy} aria-pressed={period === 5} onClick={() => setPeriod(5)}>주간 점검</button>
        <button disabled={!!saved || recordBusy} aria-pressed={period === 21} onClick={() => setPeriod(21)}>월간 점검</button>
        <span>최근 {period}개 일봉 간격 비교 · 일정 설정과 별개</span>
      </div>

      <section className={styles.summaryGrid} aria-label="점검 요약">
        <article className={styles.summaryCard}><ChartBarSquareIcon /><div><span>시장 상태</span><strong>{rising >= falling ? '중립 ~ 강세' : '주의 필요'}</strong><small>상승 {rising} · 하락 {falling}</small></div></article>
        <article className={styles.summaryCard}><ShieldExclamationIcon /><div><span>포트폴리오 위험</span><strong>{insights.risks.length ? `${insights.risks.length}개 신호` : '안정'}</strong><small>{incomplete.length ? `${incomplete.length}개 자료 확인 필요` : '가격 관측 양호'}</small></div></article>
        <article className={styles.summaryCard}><CircleStackIcon /><div><span>데이터 품질</span><strong>{incomplete.length ? '확인 필요' : portfolios?.data ? '양호' : '확인 중'}</strong><small>포트폴리오 {portfolios?.data?.length ?? 0}개 기준</small></div></article>
        <article className={styles.summaryCard}><LightBulbIcon /><div><span>오늘의 핵심 판단</span><strong>{insights.risks.length ? '재검토 필요' : '주요 경고 없음'}</strong><small>{insights.risks[0] ?? '현재 규칙에서 감지된 위험 없음'}</small></div></article>
      </section>

      <div className={styles.heroGrid}>
      <section className={styles.sectionBlock} aria-labelledby="market-title">
        <div className={styles.sectionHead}><div><p className={styles.eyebrow}>시장 환경</p><h2 id="market-title">시장 점검</h2></div><Link href="/market">시장 상세 →</Link></div>
        <p className={styles.caption}>yfinance 일봉의 서버 저장 자료. 장 마감 확정·최신 거래일·중간 거래일 누락은 미검증이며, 지표별 기준일이 다를 수 있습니다.</p>
        <div className={styles.marketGrid}>
          {indices.map(index => {
            const resource = histories[index.slug];
            const result = resource?.data ? reviewChange(resource.data, period, index.slug === 'US10Y') : null;
            return <article key={index.slug} className={styles.card}>
              <p className={styles.caption}>{index.role}</p><h3>{index.name}</h3>
              {!resource ? <p>조회 중…</p> : resource.error ? <p role="status">{resource.error}</p> : !result ? <p>자료 부족 또는 일봉 오류 · 비교 불가</p> : <>
                <p className={styles.value}>{number(result.value)}{index.slug === 'US10Y' ? '%' : index.slug === 'USDKRW' ? '원' : ''}</p>
                <p className={result.change >= 0 ? styles.positive : styles.negative}>{result.change > 0 ? '+' : ''}{number(result.change)} {result.unit}</p>
                <p className={styles.caption}>{result.from} → {result.to}</p>
              </>}
            </article>;
          })}
        </div>
        <article className={styles.breadth}><h3>시장 폭 · 최근 단일 스냅샷</h3>
          {!breadth ? <p>조회 중…</p> : breadth.data === undefined ? <p role="status">{breadth.error}</p> : <>
            <div className={styles.breadthStats}><div><strong className={styles.positive}>{number(breadth.data.advancingCount)}</strong><span>상승</span></div><div><strong className={styles.negative}>{number(breadth.data.decliningCount)}</strong><span>하락</span></div><div><strong>{number(breadth.data.unchangedCount)}</strong><span>보합</span></div></div>
            <div className={styles.breadthBar}><span style={{ width: `${(breadth.data.advancingCount / Math.max(breadth.data.universeSize, 1)) * 100}%` }} /><span style={{ width: `${(breadth.data.decliningCount / Math.max(breadth.data.universeSize, 1)) * 100}%` }} /></div>
            <p className={styles.caption}>대상 {number(breadth.data.universeSize)}종목 · 기준일 {breadth.data.snapshotDate} · 계산 시각 {breadth.data.computedAt}</p>
            <p className={styles.caption}>시장 폭의 주간·월간 변화는 제공하지 않습니다. 전체 시장을 대표하는지와 최신성은 별도 확인이 필요합니다.</p>
          </>}
        </article>
      </section>

      <section className={styles.decisionDraft} aria-labelledby="insight-title">
        <div className={styles.sectionHead}><div><p className={styles.eyebrow}>판단 지원</p><h2 id="insight-title">판단 초안</h2></div><small>규칙 기반 · 최종 판단은 사용자 책임</small></div>
        <p className={styles.draftText}>{insights.draft}</p>
        <div className={styles.signalList}>{insights.risks.slice(0, 3).map(risk => <p key={risk}><ShieldExclamationIcon />{risk}</p>)}{!insights.risks.length && <p>현재 규칙에서 감지된 주요 위험 신호가 없습니다.</p>}</div>
        <div className={styles.decisionActions}>
          {(['EXECUTE', 'DEFER', 'HOLD'] as ReviewDecisionChoice[]).map(value => <button type="button" key={value} aria-pressed={choice === value} onClick={() => setChoice(value)}>{decisionLabel[value]}</button>)}
          <button type="button" disabled={!saved} onClick={() => setReason(insights.draft)}><ClipboardDocumentCheckIcon /> 판단 기록 준비</button>
        </div>
      </section>
      </div>

      <section className={styles.analysisGrid} aria-label="위험과 가설 점검">
        <article className={styles.panel}><div className={styles.sectionHead}><h2>위험 신호</h2><span>{insights.risks.length}개</span></div>{insights.risks.length ? <ul>{insights.risks.map(risk => <li key={risk}>{risk}</li>)}</ul> : <p>현재 규칙에서 감지된 주요 위험 신호가 없습니다.</p>}</article>
        <article className={styles.panel}><div className={styles.sectionHead}><h2>투자 가설 점검</h2><Link href="/portfolio">가설 관리 →</Link></div><div className={styles.metricList}>{insights.metrics.map(metric => <div key={metric.label}><span>{metric.label}</span><strong>{metric.value}</strong><small>{metric.detail}</small></div>)}</div></article>
        <article className={styles.panel}><div className={styles.sectionHead}><h2>데이터 품질 / 커버리지</h2><span>{incomplete.length ? '확인 필요' : '양호'}</span></div><div className={styles.qualityList}><p><strong>보유 종목 평가</strong><span>{portfolios?.data?.reduce((sum, row) => sum + row.pricedPositionCount, 0) ?? 0}개 가격 확인</span></p><p><strong>가격 최신성</strong><span>{incomplete.length ? `${incomplete.length}개 포트폴리오 확인` : '주요 오류 없음'}</span></p><p><strong>시장 자료</strong><span>{changes.filter(Boolean).length} / {indices.length}개 확인</span></p></div></article>
      </section>

      <div className={styles.bottomGrid}>
      <section className={styles.sectionBlock} aria-labelledby="portfolio-title">
        <div className={styles.sectionHead}><div><p className={styles.eyebrow}>재검토 대상</p><h2 id="portfolio-title">포트폴리오 점검</h2></div><Link href="/portfolio">현재 보유·가격 확인 →</Link></div>
        {!portfolios ? <p>포트폴리오 조회 중…</p> : portfolios.data === undefined ? <p role="status">{portfolios.error}</p> : portfolios.data.length === 0 ? <div className={styles.card}><h3>등록한 포트폴리오가 없습니다</h3><p>보유 종목과 수량을 등록하면 가격 누락과 평가 범위를 확인할 수 있습니다.</p><Link href="/portfolio">포트폴리오 등록 →</Link></div> :
          <div className={styles.grid}>{portfolios.data.map(portfolio => <article key={portfolio.id} className={styles.card}>
            <span className={styles.badge}>{quality[portfolio.valuationStatus]}</span><h3>{portfolio.name}</h3>
            <p className={styles.caption}>가격이 있는 보유분 평가액 · 현금 제외</p><p className={styles.value}>{number(portfolio.totalMarketValue)}</p>
            <p>가격 확인 {portfolio.pricedPositionCount} / {portfolio.positionCount}종목</p>
            <p className={styles.caption}>가격 누락 {portfolio.unpricedPositionCount} · 오래된 관측 {portfolio.stalePositionCount} · 미검증 {portfolio.unverifiedPositionCount}</p>
            <p className={styles.caption}>통화·환산 기준은 현재 API에 없어 합산 금액의 투자 판단 활용 전 확인이 필요합니다.</p>
            {saved && <details><summary>저장 당시 보유 근거</summary>
              {saved.payload.ledgerBasis?.data?.[String(portfolio.id)] && <p className={styles.caption}>
                원장 {saved.payload.ledgerBasis.data[String(portfolio.id)].transactionCount}건 · 마지막 거래 #{saved.payload.ledgerBasis.data[String(portfolio.id)].lastTransactionId ?? '없음'} · {saved.payload.ledgerBasis.data[String(portfolio.id)].lastOccurredAt ?? '거래 시각 없음'}
              </p>}
              {saved.payload.ledgerBasis?.error && <p className={styles.caption}>원장 기준점: {saved.payload.ledgerBasis.error}</p>}
              {!saved.payload.ledgerBasis && <p className={styles.caption}>원장 기준점 도입 전 저장 기록입니다.</p>}
              {saved.payload.strategy?.data?.[String(portfolio.id)]?.rule && <p className={styles.caption}>
                기본 최대 비중 {(saved.payload.strategy.data[String(portfolio.id)].rule!.maxPositionWeight * 100).toFixed(1)}%
              </p>}
              {saved.payload.strategy?.data?.[String(portfolio.id)]?.theses.map(thesis => <p key={thesis.id} className={styles.caption}>
                {thesis.ticker} 가설 r{thesis.revision} · 목표 {(thesis.targetWeight * 100).toFixed(1)}% · 최대 {(thesis.maxWeight * 100).toFixed(1)}% · 무효화: {thesis.invalidationCondition}
              </p>)}
              {saved.payload.strategy?.error && <p className={styles.caption}>가설·비중 규칙: {saved.payload.strategy.error}</p>}
              {(saved.payload.positions[String(portfolio.id)] ?? []).map(position => <p key={position.id} className={styles.caption}>
              {position.ticker} · {position.quantity}주 · 평단 {number(position.avgCost)} · 가격 {number(position.currentPrice)} · {position.priceProvider} / {position.priceStatus} · {position.priceAsOf ?? position.priceSessionDate ?? '관측 시점 미상'} · 보유 버전 {position.version}
            </p>)}</details>}
          </article>)}</div>}
      </section>

      <section className={styles.reviewControl} aria-label="판단 기록">
        <div className={styles.sectionHead}><h2>{saved ? `판단 기록 · 점검 #${saved.id}` : '판단 기록'}</h2><span>{decisions.length}건</span></div>
        <div className={styles.recordTools}>
          <button disabled={recordBusy || !!saved} onClick={() => void openRecord()}>{recordBusy ? '처리 중…' : '현재 근거 저장'}</button>
          <select aria-label="최근 저장 기록" disabled={recordBusy} value={saved?.id ?? ''} onChange={event => { if (event.target.value) void openRecord(Number(event.target.value)); }}><option value="">저장 기록 선택</option>{records.map(row => <option key={row.id} value={row.id}>#{row.id} · {row.period === 5 ? '주간' : '월간'} · {new Date(row.createdAt).toLocaleString('ko-KR')}</option>)}</select>
        </div>
        {recordError && <p role="alert">{recordError}</p>}
        {saved && <form className={styles.decisionForm} onSubmit={saveDecision}>
          <label>판단 근거<textarea required maxLength={2000} value={reason} onChange={event => setReason(event.target.value)} /></label>
          {choice === 'DEFER' && <label>다시 확인할 날짜<input type="date" value={followUpDate} onChange={event => setFollowUpDate(event.target.value)} /></label>}
          <button disabled={recordBusy || !reason.trim()} type="submit">{decisionLabel[choice]} 판단 저장</button>
        </form>}
        <div className={styles.history}>{decisions.map(decision => <article key={decision.id} className={styles.decisionItem}><strong>{decisionLabel[decision.choice]}</strong><span>{new Date(decision.createdAt).toLocaleString('ko-KR')}</span><p>{decision.reason}</p>{decision.followUpDate && <small>재점검 {decision.followUpDate}</small>}</article>)}</div>
      </section>
      </div>
      <footer className={styles.disclaimer}>이 화면은 추천 종목 제안이 아니라 투자 판단 전 근거를 점검하기 위한 도구입니다. 모든 투자 결정은 사용자의 판단과 책임 아래 이루어져야 합니다.</footer>
    </main>
  );
}
