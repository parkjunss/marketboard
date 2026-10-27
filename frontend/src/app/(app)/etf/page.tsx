'use client';

import Link from 'next/link';
import { EtfMomentumPanel } from '@/components/screener/EtfMomentumPanel';
import { PortfolioDesigner } from '@/components/portfolio/PortfolioDesigner';
import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { useAuth } from '@/lib/auth-context';
import { createReviewDecision, getReviewDecisions, getPortfolios, retryablePost } from '@/lib/api';
import type { EtfComparisonRequest, EtfComparisonResult } from '@/lib/etf-comparison-types';
import type { PortfolioSummaryResponse, ReviewDecision, ReviewDecisionChoice } from '@/lib/types';
import styles from '@/components/screener/screener.module.css';
import pageStyles from './etf.module.css';

type Summary = { id: number; createdAt: string; strategyId: string };
type Evidence = { calculationVersion: string; strategyId: string; configuration: EtfComparisonRequest; comparison: EtfComparisonResult };
type Detail = { id: number; payload: { etf: Evidence } };
type Paper = { id: number; reviewId: number; active: boolean; message: string | null;
  state: { date: string; cash: number; value: number; returnPct: number; shares: Record<string, number> } };
type Snapshot = { id: number; kind: string; date: string; capturedAt: string; payload: unknown };
type Proposal = { ready: boolean; message: string | null; signalDate: string; priceDate: string;
  totalValue: number; proposedCash: number; totalFees: number; totalSlippage: number;
  rows: { ticker: string; quantity: number; version: number | null; price: number; currentWeight: number;
    targetWeight: number; targetValue: number; adjustmentValue: number; adjustmentQuantity: number }[] };
const number = (v: number | null) => v == null ? '—' : v.toLocaleString('ko-KR', { maximumFractionDigits: 4 });
const percent = (v: number) => new Intl.NumberFormat('ko-KR', { style: 'percent', maximumFractionDigits: 2 }).format(v);

export default function EtfWorkflowPage() {
  const { authFetch, user } = useAuth();
  const [reviews, setReviews] = useState<Summary[]>([]);
  const [detail, setDetail] = useState<Detail | null>(null);
  const [decisions, setDecisions] = useState<ReviewDecision[]>([]);
  const [papers, setPapers] = useState<Paper[]>([]);
  const [snapshots, setSnapshots] = useState<{ accountId: number; rows: Snapshot[] } | null>(null);
  const [portfolios, setPortfolios] = useState<PortfolioSummaryResponse[]>([]);
  const [proposal, setProposal] = useState<Proposal | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const slowFetch = useCallback(<T,>(path: string, options?: Parameters<typeof authFetch>[1]) =>
    authFetch<T>(path, { ...options, signal: AbortSignal.timeout(90_000) }), [authFetch]);
  const load = useCallback(() => Promise.all([
      authFetch<Summary[]>('/api/etf/reviews'), authFetch<Paper[]>('/api/etf/paper'), getPortfolios(authFetch),
    ]), [authFetch]);
  async function reload() {
    const [saved, accounts, owned] = await load();
    setReviews(saved); setPapers(accounts); setPortfolios(owned);
  }
  useEffect(() => {
    if (!user) return;
    let active = true;
    load().then(([saved, accounts, owned]) => {
      if (active) { setReviews(saved); setPapers(accounts); setPortfolios(owned); }
    }).catch(e => { if (active) setError(e.message); });
    return () => { active = false; };
  }, [load, user]);
  async function act(action: () => Promise<void>) {
    setBusy(true); setError(null);
    try { await action(); } catch (e) { setError(e instanceof Error ? e.message : '요청을 완료하지 못했습니다.'); }
    finally { setBusy(false); }
  }
  async function select(id: number) {
    const [record, history] = await Promise.all([authFetch<Detail>(`/api/reviews/${id}`), getReviewDecisions(authFetch, id)]);
    setDetail(record); setDecisions(history); setProposal(null);
  }
  function capture(e: FormEvent<HTMLFormElement>) {
    e.preventDefault(); const form = new FormData(e.currentTarget);
    void act(async () => {
      const saved = await retryablePost<Detail>(slowFetch, '/api/etf/reviews', {
        strategyId: form.get('strategyId'), configuration: {
          startDate: form.get('startDate'), endDate: form.get('endDate'), initialCapital: Number(form.get('initialCapital')),
          feeBps: Number(form.get('feeBps')), slippageBps: Number(form.get('slippageBps')),
        },
      });
      setDetail(saved); setDecisions([]); setProposal(null); await reload();
    });
  }
  function decide(e: FormEvent<HTMLFormElement>) {
    e.preventDefault(); const form = new FormData(e.currentTarget); if (!detail) return;
    void act(async () => {
      await createReviewDecision(authFetch, detail.id, { choice: String(form.get('choice')) as ReviewDecisionChoice,
        reason: String(form.get('reason')), followUpDate: String(form.get('followUpDate')) || null });
      setDecisions(await getReviewDecisions(authFetch, detail.id));
    });
  }
  function propose(e: FormEvent<HTMLFormElement>) {
    e.preventDefault(); const form = new FormData(e.currentTarget); if (!detail) return;
    setProposal(null);
    void act(async () => setProposal(await slowFetch<Proposal>('/api/etf/rebalance', { method: 'POST', body: {
      reviewId: detail.id, portfolioId: Number(form.get('portfolioId')), cashUsd: Number(form.get('cashUsd')),
    } })));
  }
  return <main className={pageStyles.page} aria-busy={busy}>
    <h1>포트폴리오 설계</h1>
    <PortfolioDesigner />
    <details><summary>월말 ETF 모멘텀 · 검토 기록 · 모의 운용</summary>
    <EtfMomentumPanel />
    {user ? <section className={styles.panel}>
      <div className={styles.panelHeader}><h2>전략 검토 · 모의 운용</h2><Link href="/backtest#etf-comparison">전략 백테스트 비교</Link></div>
      <div className={styles.etfExplanation}>
        <p>비교 근거를 저장하고 판단을 남긴 뒤 모의 운용을 시작합니다. 실제 주문과 보유 수량은 변경하지 않습니다.</p>
        <p>고정 ETF 6종 · 월말 상대 모멘텀 · 상위 3종 · 200일 추세 필터. 수정주가 여부를 확정하지 못한 연구용 결과이며 세금·배당 현금·환전은 반영하지 않습니다.</p>
      </div>
      {error && <p role="alert">{error}</p>}
      <form onSubmit={capture}><fieldset disabled={busy}><legend>서버에서 다시 계산하여 검토 근거 저장</legend>
        <div className={styles.filterGrid}>
          <label className={styles.field}>전략<select name="strategyId" defaultValue="MOMENTUM_TREND"><option value="MOMENTUM_TREND">모멘텀 + 추세 필터</option><option value="MOMENTUM">모멘텀</option><option value="SPY_HOLD">SPY 보유</option></select></label>
          <label className={styles.field}>시작일<input type="date" name="startDate" required /></label>
          <label className={styles.field}>종료일<input type="date" name="endDate" required /></label>
          <label className={styles.field}>초기 자본 (USD)<input type="number" name="initialCapital" min="1" max="1000000000" step="0.01" defaultValue="10000" required /></label>
          <label className={styles.field}>수수료 (bp)<input type="number" name="feeBps" min="0" max="100" step="0.1" defaultValue="5" required /></label>
          <label className={styles.field}>슬리피지 (bp)<input type="number" name="slippageBps" min="0" max="100" step="0.1" defaultValue="5" required /></label>
        </div><button className={styles.primaryButton}>근거 저장</button>
      </fieldset></form>
      <h2>최근 검토 기록</h2>
      <p>최근 투자 검토 50건 중 ETF 기록을 표시합니다.</p>
      {reviews.length === 0 && <p>저장한 ETF 검토가 없습니다.</p>}
      {reviews.map(r => <button key={r.id} disabled={busy} onClick={() => void act(() => select(r.id))}>#{r.id} · {r.strategyId} · {r.createdAt}</button>)}
      {detail && <>
        <h2>검토 #{detail.id} · {detail.payload.etf.strategyId}</h2>
        <p>{detail.payload.etf.configuration.startDate} ~ {detail.payload.etf.configuration.endDate} · 계산 버전 {detail.payload.etf.calculationVersion}</p>
        <div className={styles.tableScroll}><table className={styles.table}>
          <caption>저장 당시 서버 계산 결과</caption><thead><tr><th>전략</th><th>수익률 (%)</th><th>CAGR (%)</th><th>최대 낙폭 (%)</th><th>샤프</th></tr></thead>
          <tbody>{detail.payload.etf.comparison.strategies.map(s => <tr key={s.strategyId}><th>{s.label}</th><td>{number(s.metrics.totalReturnPct)}</td><td>{number(s.metrics.cagrPct)}</td><td>{number(s.metrics.maxDrawdownPct)}</td><td>{number(s.metrics.sharpeRatio)}</td></tr>)}</tbody>
        </table></div>
        <details><summary>저장된 전체 근거 · 입력 일봉 포함</summary><pre style={{ overflow: 'auto', maxHeight: 300 }}>{JSON.stringify(detail.payload.etf, null, 2)}</pre></details>
        <form onSubmit={decide}><fieldset disabled={busy}><legend>판단 기록</legend>
          <label className={styles.field}>판단<select name="choice"><option value="EXECUTE">채택 · 모의 운용 검토</option><option value="DEFER">추가 확인</option><option value="HOLD">보류</option></select></label>
          <label className={styles.field}>근거와 위험 신호<textarea name="reason" required maxLength={2000} /></label>
          <label className={styles.field}>다음 확인일<input type="date" name="followUpDate" /></label><button>판단 저장</button>
        </fieldset></form>
        {decisions.map(d => <p key={d.id}>{d.createdAt} · {d.choice} · {d.reason}{d.followUpDate && ` · 다음 확인 ${d.followUpDate}`}</p>)}
        <button className={styles.primaryButton} disabled={busy || decisions[0]?.choice !== 'EXECUTE'} onClick={() => void act(async () => {
          await retryablePost<Paper>(slowFetch, '/api/etf/paper', { reviewId: detail.id }); await reload();
        })}>저장된 조건으로 모의 운용 시작</button>
        <h2>실제 포트폴리오 리밸런싱 제안</h2>
        <p>현재 월말 신호와 공통 일봉 종가를 사용합니다. 입력 현금은 USD로 확인한 가용 잔액이며, 체결 가격과 다를 수 있습니다. 비중 제한과 충돌하면 제안을 보류합니다.</p>
        <form onSubmit={propose}><fieldset disabled={busy || portfolios.length === 0}>
          <label className={styles.field}>포트폴리오<select name="portfolioId" required>{portfolios.map(p => <option key={p.id} value={p.id}>{p.name}</option>)}</select></label>
          <label className={styles.field}>확인한 가용 현금 (USD)<input name="cashUsd" type="number" required min="0" max="1000000000" step="0.01" /></label>
          <button>제안 계산</button>
        </fieldset></form>
        {proposal && (proposal.ready ? <>
          <p>신호 {proposal.signalDate} · 가격 {proposal.priceDate} · 평가액 {number(proposal.totalValue)} USD · 예상 잔여 현금 {number(proposal.proposedCash)} USD · 수수료 {number(proposal.totalFees)} · 슬리피지 {number(proposal.totalSlippage)}</p>
          <div className={styles.tableScroll}><table className={styles.table}><thead><tr><th>ETF/종목</th><th>보유 버전</th><th>가격</th><th>현재 비중</th><th>목표 비중</th><th>조정 수량 (+매수/−매도)</th><th>조정 금액 (USD)</th></tr></thead>
            <tbody>{proposal.rows.map(r => <tr key={r.ticker}><th>{r.ticker}</th><td>{r.version ?? '—'}</td><td>{number(r.price)}</td><td>{percent(r.currentWeight)}</td><td>{percent(r.targetWeight)}</td><td>{number(r.adjustmentQuantity)}</td><td>{number(r.adjustmentValue)}</td></tr>)}</tbody>
          </table></div>
        </> : <p role="status">{proposal.message}</p>)}
      </>}
      <h2>모의 운용 계좌</h2>
      <p>신호를 실제 저장한 뉴욕 날짜의 다음 관측 거래일 시가부터 모의 체결합니다. 매일 08:30 UTC 갱신하며 누락한 과거 신호는 만들지 않습니다. 중지 후 기록은 보존됩니다.</p>
      {papers.length === 0 && <p>모의 운용 계좌가 없습니다.</p>}
      {papers.map(p => <article key={p.id}>
        <h3>계좌 #{p.id} · 검토 #{p.reviewId} · {p.active ? '운용 중' : '중지'}</h3>
        <p>{p.state.date} · 평가액 {number(p.state.value)} USD · 현금 {number(p.state.cash)} USD · 수익률 {number(p.state.returnPct)}%</p>
        <p>{Object.entries(p.state.shares).map(([ticker, quantity]) => `${ticker} ${number(quantity)}주`).join(' · ')}</p>
        {p.message && <p role="status">{p.message}</p>}
        <button disabled={busy || !p.active} onClick={() => void act(async () => { await slowFetch(`/api/etf/paper/${p.id}/refresh`, { method: 'POST' }); setSnapshots(null); await reload(); })}>평가 갱신</button>
        <button disabled={busy || !p.active} onClick={() => void act(async () => { await authFetch(`/api/etf/paper/${p.id}/stop`, { method: 'POST' }); await reload(); })}>운용 중지</button>
        <button disabled={busy} onClick={() => void act(async () => setSnapshots({ accountId: p.id, rows: await authFetch<Snapshot[]>(`/api/etf/paper/${p.id}/snapshots`) }))}>신호·체결·평가 기록</button>
      </article>)}
      {snapshots && <section><h3>계좌 #{snapshots.accountId}의 변경 불가 기록</h3>{snapshots.rows.length === 0 && <p>아직 기록이 없습니다.</p>}{snapshots.rows.map(s => <details key={s.id}><summary>{s.date} · {s.kind} · 저장 {s.capturedAt}</summary><pre style={{ overflow: 'auto', maxHeight: 300 }}>{JSON.stringify(s.payload, null, 2)}</pre></details>)}</section>}
    </section> : <p><Link href="/login?next=%2Fetf">로그인하고 검토 기록 저장·모의 운용 시작</Link></p>}
    </details>
  </main>;
}
