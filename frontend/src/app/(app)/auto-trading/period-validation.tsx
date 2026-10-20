'use client';

import { useEffect, useRef, useState } from 'react';
import { useAuth } from '@/lib/auth-context';
import { ApiError } from '@/lib/api';
import type { Config, Comparison } from './types';
import EquityChart from './equity-chart';
import CostAnalysis from './cost-analysis';
import styles from './auto-trading.module.css';

type Strategy = { id: number; name: string; configuration: Config };
type Summary = { id: number; strategyId: number; startDate: string; endDate: string; createdAt: string };
type Validation = { summary: Summary; strategyName: string; configuration: Config; warmupCount: number;
  warmupStart: string; warmupEnd: string; result: { training: Comparison; validation: Comparison } };
type Range = { ticker: string; lastDate: string };
const nextDay = (day: string) => { const d = new Date(`${day}T00:00:00Z`); d.setUTCDate(d.getUTCDate() + 1); return d.toISOString().slice(0, 10); };
const amount = (value: string | number) => (Math.abs(Number(value)) < .00005 ? 0 : Number(value)).toLocaleString('ko-KR', { maximumFractionDigits: 4 });

export default function PeriodValidation({ active }: { active: boolean }) {
  const { authFetch } = useAuth();
  const [strategies, setStrategies] = useState<Strategy[]>([]);
  const [ranges, setRanges] = useState<Range[]>([]);
  const [strategyId, setStrategyId] = useState<number | null>(null);
  const [period, setPeriod] = useState({ startDate: '', endDate: '' });
  const [records, setRecords] = useState<Summary[]>([]);
  const [selected, setSelected] = useState<number | null>(null);
  const [result, setResult] = useState<Validation | null>(null);
  const [pending, setPending] = useState<{ key: string; strategyId: number; body: typeof period } | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const flight = useRef(false);
  const generation = useRef(0);
  const chosen = strategies.find(s => s.id === strategyId);
  const range = ranges.find(r => r.ticker === chosen?.configuration.ticker);
  const available = !!chosen && !!range && range.lastDate > chosen.configuration.endDate;
  const detail = result?.summary.id === selected ? result : null;
  useEffect(() => {
    if (!active) return;
    const stamp = generation.current;
    const abort = new AbortController();
    void Promise.all([
      authFetch<Strategy[]>('/api/auto-trading/strategies', { signal: abort.signal }),
      authFetch<Range[]>('/api/auto-trading/prices', { signal: abort.signal }),
      authFetch<Summary[]>('/api/auto-trading/validations', { signal: abort.signal }),
    ]).then(([saved, prices, history]) => {
      if (abort.signal.aborted) return;
      setStrategies(saved); setRanges(prices);
      if (stamp === generation.current) {
        setRecords(history);
        if (history.length) setSelected(current => current ?? history[0].id);
      }
    }).catch(e => { if (!abort.signal.aborted) setError(e.message); });
    return () => abort.abort();
  }, [authFetch, active]);
  useEffect(() => {
    if (selected === null) return;
    const abort = new AbortController();
    void authFetch<Validation>(`/api/auto-trading/validations/${selected}`, { signal: abort.signal })
      .then(data => { if (!abort.signal.aborted) setResult(data); })
      .catch(e => { if (!abort.signal.aborted) setError(e.message); });
    return () => abort.abort();
  }, [authFetch, selected]);
  function choose(id: number) {
    setStrategyId(id || null); setMessage(''); setError('');
    const strategy = strategies.find(s => s.id === id);
    const prices = ranges.find(r => r.ticker === strategy?.configuration.ticker);
    setPeriod(strategy && prices && prices.lastDate > strategy.configuration.endDate
      ? { startDate: nextDay(strategy.configuration.endDate), endDate: prices.lastDate }
      : { startDate: '', endDate: '' });
  }
  async function create() {
    if (!chosen || flight.current) return;
    const request = pending ?? { key: crypto.randomUUID(), strategyId: chosen.id, body: { ...period } };
    generation.current++;
    setPending(request); setBusy(true); flight.current = true; setError(''); setMessage('');
    try {
      const saved = await authFetch<Validation>(`/api/auto-trading/strategies/${request.strategyId}/validations`, {
        method: 'POST', headers: { 'Idempotency-Key': request.key }, body: request.body,
      });
      setRecords(current => [saved.summary, ...current.filter(r => r.id !== saved.summary.id)]);
      setResult(saved); setSelected(saved.summary.id); setPending(null);
      setMessage(`검증 #${saved.summary.id} 완료. 설정·가격·결과를 별도 기록으로 저장했습니다.`);
    } catch (e) {
      if (e instanceof ApiError && e.status >= 400 && e.status < 500) setPending(null);
      setError(e instanceof Error ? e.message : '기간 검증 실패');
    } finally { setBusy(false); flight.current = false; }
  }
  async function verify() {
    if (!detail || flight.current) return;
    setBusy(true); flight.current = true; setError(''); setMessage('');
    try {
      const check = await authFetch<{ matches: boolean }>(`/api/auto-trading/validations/${detail.summary.id}/verify`, { method: 'POST' });
      setMessage(check.matches ? '조정·검증 구간 모두 저장 결과와 재계산 결과가 일치합니다.' : '재계산 결과가 다릅니다. 저장 기록을 점검하세요.');
    } catch (e) { setError(e instanceof Error ? e.message : '재계산 실패'); }
    finally { setBusy(false); flight.current = false; }
  }
  return <>
    <section className={styles.panel}>
      <div className={styles.panelHeader}><h2>기간 분리 검증</h2><span>전략 설정 고정</span></div>
      <p>조정 기간 이후에서 같은 전략을 검증합니다. 초기 자금으로 새로 시작하며 결과는 한 번에 계산해 저장합니다.</p>
      <form onSubmit={e => { e.preventDefault(); void create(); }}>
        <fieldset disabled={busy || pending !== null} style={{ border: 0, margin: 0, padding: 0 }}>
          <div className={styles.controls}>
            <label>검증할 저장 전략<select required value={strategyId ?? ''} onChange={e => choose(Number(e.target.value))}>
              <option value="">전략 선택</option>{strategies.map(s => <option key={s.id} value={s.id}>#{s.id} {s.name}</option>)}
            </select></label>
            <label>검증 시작일<input type="date" required value={period.startDate} min={chosen ? nextDay(chosen.configuration.endDate) : undefined}
              max={range?.lastDate} onChange={e => setPeriod(p => ({ ...p, startDate: e.target.value }))} /></label>
            <label>검증 종료일<input type="date" required value={period.endDate} min={period.startDate} max={range?.lastDate}
              onChange={e => setPeriod(p => ({ ...p, endDate: e.target.value }))} /></label>
          </div>
        </fieldset>
        {chosen && <p>조정 {chosen.configuration.startDate} — {chosen.configuration.endDate} · {chosen.configuration.ticker} SMA {chosen.configuration.window} · 자금 ${chosen.configuration.initialCapital}</p>}
        {chosen && !available && <p role="status">조정 기간 이후의 저장 가격이 없습니다. 더 이른 기간으로 실행한 전략을 보관한 뒤 선택하세요.</p>}
        {!strategies.length && <p>전략 보관·비교 탭에서 먼저 전략을 저장하세요.</p>}
        <button className={styles.primary} disabled={busy || !available} type="submit">{pending ? '동일 검증 요청 다시 확인' : '설정 고정하고 검증'}</button>
      </form>
      <details><summary>준비 구간과 검증 기준</summary><p>직전 N개 종가는 이동평균 계산에만 사용하며 거래·성과에서 제외합니다. 첫 검증일 시가부터 거래할 수 있고 매수·보유도 그날 시작합니다. 자금·비중·주문 한도·비용은 저장 전략과 같습니다.</p></details>
    </section>
    {error && <p className={styles.error} role="alert">{error}</p>}
    {(busy || message) && <p className={styles.message} role="status">{busy ? '고정 설정으로 계산 중…' : message}</p>}
    <section className={styles.panel}>
      <div className={styles.panelHeader}><h2>검증 기록</h2><span>{records.length}개 보관</span></div>
      <label>기간 검증 기록 선택<select value={selected ?? ''} disabled={busy} onChange={e => { setSelected(Number(e.target.value) || null); setMessage(''); }}>
        <option value="">선택</option>{records.map(r => <option key={r.id} value={r.id}>#{r.id} · 전략 #{r.strategyId} · {r.startDate} — {r.endDate}</option>)}
      </select></label>
      <p className={styles.notice}>같은 구간을 반복 확인하며 설정을 바꾸면 그 구간도 조정에 사용한 것입니다. 과거에 보지 않은 데이터였는지는 보장하지 않습니다.</p>
      {detail && <>
        <div className={styles.panelHeader} style={{ marginTop: 24 }}><h3>{detail.strategyName}</h3><button disabled={busy} onClick={() => void verify()}>검증 기록 재계산</button></div>
        <p>{detail.configuration.ticker} · SMA {detail.configuration.window} · 비중 {detail.configuration.maxPositionWeight} · 한도 ${detail.configuration.maxOrderValue} · 비용 {detail.configuration.feeBps} / {detail.configuration.slippageBps}bp</p>
        <div className={styles.table}><table><thead><tr><th>지표</th><th>조정 기간</th><th>별도 검증 기간</th></tr></thead><tbody>
          <tr><th>기간</th><td>{detail.configuration.startDate} — {detail.configuration.endDate}</td><td>{detail.summary.startDate} — {detail.summary.endDate}</td></tr>
          <tr><th>처리 거래일</th><td>{detail.result.training.steps}</td><td>{detail.result.validation.steps}</td></tr>
          {([['returnPct', '수익률 (%)'], ['maxDrawdownPct', '최대 낙폭 (%)'], ['fees', '수수료 (USD)'], ['trades', '체결 횟수']] as const).map(([key, label]) =>
            <tr key={key}><th>{label}</th><td>{amount(detail.result.training.strategy[key])}</td><td>{amount(detail.result.validation.strategy[key])}</td></tr>)}
          <tr><th>매수·보유 대비 (%p)</th><td>{amount(detail.result.training.excessReturnPct)}</td><td>{amount(detail.result.validation.excessReturnPct)}</td></tr>
        </tbody></table></div>
        <p>준비 가격 {detail.warmupStart} — {detail.warmupEnd} ({detail.warmupCount}개, 성과 제외). 구간 길이가 다르므로 누적 수익률만으로 우열을 판단하지 마세요.</p>
        <h3 style={{ marginTop: 24 }}>검증 기간 자산 추이</h3><EquityChart points={detail.result.validation.curve} />
        <details><summary>조정 기간 자산 추이</summary><EquityChart points={detail.result.training.curve} /><p>원본 조정 구간에는 초기 SMA 준비 기간의 현금 보유가 포함됩니다.</p></details>
        <p className={styles.notice}>수정주가·배당 반영 미확인 · 두 구간 모두 강제 청산 없이 마지막 종가로 평가</p>
      </>}
      {!records.length && <div className={styles.empty}>아직 검증 기록이 없습니다.</div>}
    </section>
    {detail && <CostAnalysis key={detail.summary.id} validationId={detail.summary.id}
      feeBps={detail.configuration.feeBps} slippageBps={detail.configuration.slippageBps} />}
  </>;
}
