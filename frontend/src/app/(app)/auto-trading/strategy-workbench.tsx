'use client';

import { useEffect, useRef, useState } from 'react';
import { useAuth } from '@/lib/auth-context';
import { ApiError } from '@/lib/api';
import type { Config, Run, Comparison } from './types';
import styles from './auto-trading.module.css';
import EquityChart from './equity-chart';

type Strategy = { id: number; name: string; sourceRunId: number; configuration: Config };
type Draft = { name: string; sourceRunId: number; configuration: Config };
type Pair = { left: Run; right: Run; comparable: boolean; differences: string[]; steps: number;
  leftResult: Comparison | null; rightResult: Comparison | null };
const labels: Record<string, string> = { version: '전략 버전', ticker: '종목', startDate: '시작일', endDate: '종료일',
  initialCapital: '초기 자금', feeBps: '수수료', slippageBps: '슬리피지', prices: '저장 가격 사본' };
const format = (value: string | number) => Number(value).toLocaleString('ko-KR', { maximumFractionDigits: 4 });

export default function StrategyWorkbench({ run, runs, disabled, onStarted }: {
  run: Run | null; runs: Run[]; disabled: boolean; onStarted: (run: Run) => void;
}) {
  const { authFetch } = useAuth();
  const [strategies, setStrategies] = useState<Strategy[]>([]);
  const [selected, setSelected] = useState<number | null>(null);
  const [draft, setDraft] = useState<Draft | null>(null);
  const [saveRequest, setSaveRequest] = useState<{ key: string; body: Draft } | null>(null);
  const [runRequest, setRunRequest] = useState<{ key: string; id: number } | null>(null);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [other, setOther] = useState<number | null>(null);
  const [pairData, setPair] = useState<Pair | null>(null);
  const [pairFailure, setPairFailure] = useState<{ left: number | null; right: number | null; message: string } | null>(null);
  const flight = useRef(false);
  const chosen = strategies.find(s => s.id === selected);
  const locked = disabled || busy || saveRequest !== null || runRequest !== null;
  const runId = run?.id ?? null;
  const pair = pairData?.left.id === runId && pairData?.right.id === other && runId !== other ? pairData : null;
  const pairError = pairFailure?.left === runId && pairFailure?.right === other ? pairFailure.message : '';
  useEffect(() => {
    const abort = new AbortController();
    void authFetch<Strategy[]>('/api/auto-trading/strategies', { signal: abort.signal })
      .then(data => { if (!abort.signal.aborted) setStrategies(data); })
      .catch(e => { if (!abort.signal.aborted) setError(e.message); });
    return () => abort.abort();
  }, [authFetch]);
  useEffect(() => {
    if (runId === null || other === null || runId === other) return;
    const abort = new AbortController();
    let fetching = false;
    async function poll() {
      if (fetching) return;
      fetching = true;
      try {
        const data = await authFetch<Pair>(`/api/auto-trading/${runId}/compare/${other}`, { signal: abort.signal });
        if (!abort.signal.aborted) { setPair(data); setPairFailure(null); }
      } catch (e) { if (!abort.signal.aborted) { setPair(null); setPairFailure({ left: runId, right: other, message: e instanceof Error ? e.message : '실행 비교 실패' }); } }
      finally { fetching = false; }
    }
    void poll();
    const timer = setInterval(() => void poll(), 3000);
    return () => { abort.abort(); clearInterval(timer); };
  }, [authFetch, runId, other]);
  function copy(name: string, sourceRunId: number, configuration: Config) {
    setDraft({ name, sourceRunId, configuration: { ...configuration } }); setMessage('설정을 복사했습니다. 이름과 전략 값을 정한 뒤 별도 저장하세요.'); setError('');
  }
  async function save() {
    if (!draft || flight.current || disabled) return;
    const request = saveRequest ?? { key: crypto.randomUUID(), body: draft };
    setSaveRequest(request); flight.current = true; setBusy(true); setError('');
    try {
      const saved = await authFetch<Strategy>('/api/auto-trading/strategies', {
        method: 'POST', headers: { 'Idempotency-Key': request.key }, body: request.body,
      });
      setStrategies(current => [saved, ...current.filter(s => s.id !== saved.id)]);
      setSelected(saved.id); setDraft(null); setSaveRequest(null);
      setMessage(`“${saved.name}” 저장 완료. 아래에서 같은 가격으로 실행하거나 복제할 수 있습니다.`);
    } catch (e) {
      if (e instanceof ApiError && e.status >= 400 && e.status < 500) setSaveRequest(null);
      setError(e instanceof Error ? e.message : '전략 저장 실패');
    } finally { flight.current = false; setBusy(false); }
  }
  async function start() {
    if (!chosen || flight.current || disabled) return;
    const request = runRequest ?? { key: crypto.randomUUID(), id: chosen.id };
    setRunRequest(request); flight.current = true; setBusy(true); setError('');
    try {
      const created = await authFetch<Run>(`/api/auto-trading/strategies/${request.id}/runs`, {
        method: 'POST', headers: { 'Idempotency-Key': request.key },
      });
      setOther(run?.id ?? chosen.sourceRunId); onStarted(created); setRunRequest(null);
      setMessage(`실행 #${created.id} 시작. 새로운 거래 기록을 남기며 원본 가격 사본으로 진행합니다.`);
    } catch (e) {
      if (e instanceof ApiError && e.status >= 400 && e.status < 500) setRunRequest(null);
      setError(e instanceof Error ? e.message : '재실행 실패');
    } finally { flight.current = false; setBusy(false); }
  }
  function title(item: Run) {
    return `#${item.id} ${strategies.find(s => s.id === item.strategyId)?.name ?? item.configuration.ticker} · SMA ${item.configuration.window}`;
  }
  return <section className={styles.panel}><div className={styles.panelHeader}><h2>전략 보관함</h2><span>{strategies.length}개 저장됨</span></div>
    <p>같은 가격으로 설정만 바꿔 비교하세요. 원본 기록은 그대로 보관됩니다.</p>
    <button disabled={!run || locked} onClick={() => run && copy(`${run.configuration.ticker} SMA ${run.configuration.window}`, run.id, run.configuration)}>현재 설정 저장</button>
    {draft && <form onSubmit={e => { e.preventDefault(); void save(); }}>
      <fieldset disabled={locked} style={{ border: 0, padding: '16px 0' }}>
        <legend>새 전략 · 가격 원본 실행 #{draft.sourceRunId}</legend>
        <p>{draft.configuration.ticker} · {draft.configuration.startDate} ~ {draft.configuration.endDate} · 자금 ${draft.configuration.initialCapital} · 수수료 {draft.configuration.feeBps}bp · 슬리피지 {draft.configuration.slippageBps}bp</p>
        <div className={styles.controls}>
          <label>전략 이름<input required maxLength={80} value={draft.name} onChange={e => setDraft({ ...draft, name: e.target.value })} /></label>
          <label>복제 전략 이동평균 거래일<input type="number" min="2" max="200" required value={draft.configuration.window} onChange={e => setDraft({ ...draft, configuration: { ...draft.configuration, window: Number(e.target.value) } })} /></label>
          <label>복제 전략 최대 비중<input required value={draft.configuration.maxPositionWeight} onChange={e => setDraft({ ...draft, configuration: { ...draft.configuration, maxPositionWeight: e.target.value } })} /></label>
          <label>복제 전략 주문 한도 (USD)<input required value={draft.configuration.maxOrderValue} onChange={e => setDraft({ ...draft, configuration: { ...draft.configuration, maxOrderValue: e.target.value } })} /></label>
          <label>복제 전략 진행 간격 (초)<input required type="number" min="2" max="300" value={draft.configuration.intervalSeconds} onChange={e => setDraft({ ...draft, configuration: { ...draft.configuration, intervalSeconds: Number(e.target.value) } })} /></label>
        </div>
      </fieldset>
      <button className={styles.primary} type="submit" disabled={busy || disabled || runRequest !== null}>{saveRequest ? '전략 저장 요청 다시 확인' : '이름 붙여 별도 저장'}</button>
      <button type="button" disabled={locked} onClick={() => setDraft(null)}>편집 취소</button>
    </form>}
    <div className={styles.controls}>
      <label>저장 전략 선택<select value={selected ?? ''} disabled={locked} onChange={e => setSelected(Number(e.target.value) || null)}>
        <option value="">선택</option>{strategies.map(s => <option key={s.id} value={s.id}>#{s.id} {s.name}</option>)}</select></label>
      <button disabled={!chosen || locked} onClick={() => chosen && copy(`${chosen.name.slice(0, 75)} 복제`, chosen.sourceRunId, chosen.configuration)}>복제 · 수정</button>
      <button disabled={!chosen || busy || disabled || saveRequest !== null} onClick={() => void start()}>{runRequest ? '재실행 요청 다시 확인' : '저장 전략 실행'}</button>
    </div>
    {!strategies.length && <p>저장한 전략이 없습니다.</p>}
    {chosen && <p>가격 원본 #{chosen.sourceRunId} · {chosen.configuration.ticker} · SMA {chosen.configuration.window} · 비중 {chosen.configuration.maxPositionWeight} · 주문 한도 ${chosen.configuration.maxOrderValue}</p>}
    {error && <p role="alert" className={styles.error}>{error}</p>}
    {(busy || message) && <p role="status" className={styles.message}>{busy ? '처리 중…' : message}</p>}
    <h3 style={{ marginTop: 32 }}>실행 비교</h3>
    <p>두 실행이 모두 처리한 날짜까지 맞춰 비교합니다.</p>
    <label>비교할 실행<select value={other ?? ''} onChange={e => setOther(Number(e.target.value) || null)}>
      <option value="">선택</option>{runs.filter(r => r.id !== runId).map(r => <option key={r.id} value={r.id}>{title(r)} · {r.cursor}/{r.barCount}</option>)}</select></label>
    {pairError && <p role="alert">{pairError}</p>}
    {pair && !pair.comparable && <p role="status">비교 조건이 다릅니다: {pair.differences.map(d => labels[d] ?? d).join(', ')}. 같은 가격 사본으로 재실행한 기록을 선택하세요.</p>}
    {pair?.comparable && pair.leftResult && pair.rightResult && <>
      <p>동일 가격·기간·자금·비용 확인 · 공통 {pair.steps}거래일 · 평가일 {pair.leftResult.asOf ?? '처리 전'}{pair.left.status !== 'COMPLETED' || pair.right.status !== 'COMPLETED' ? ' · 중간 결과' : ' · 완료 결과'}</p>
      <EquityChart points={pair.leftResult.curve.map((p, i) => ({ ...p, benchmark: pair.rightResult!.curve[i].strategy }))} labels={[`#${pair.left.id} SMA ${pair.left.configuration.window}`, `#${pair.right.id} SMA ${pair.right.configuration.window}`]} />
      <div className={styles.table}><table><thead><tr><th>지표</th><th>{title(pair.left)}</th><th>{title(pair.right)}</th></tr></thead><tbody>
        <tr><th>이동평균 / 비중 / 주문 한도</th>{[pair.left, pair.right].map(r => <td key={r.id}>{r.configuration.window}일 / {r.configuration.maxPositionWeight} / ${r.configuration.maxOrderValue}</td>)}</tr>
        {([['equity', '평가 자산 (USD)'], ['returnPct', '수익률 (%)'], ['maxDrawdownPct', '최대 낙폭 (%)'], ['fees', '수수료 (USD)'], ['trades', '체결 횟수']] as const).map(([key, label]) =>
          <tr key={key}><th>{label}</th><td>{format(pair.leftResult!.strategy[key])}</td><td>{format(pair.rightResult!.strategy[key])}</td></tr>)}
      </tbody></table></div>
      <p className={styles.notice}>수정주가·배당 반영 미확인 · 준비 기간은 각 전략의 현금 보유 성과에 포함됩니다.</p>
    </>}
  </section>;
}
