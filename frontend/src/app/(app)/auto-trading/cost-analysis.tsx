'use client';

import { useEffect, useRef, useState } from 'react';
import { useAuth } from '@/lib/auth-context';
import { ApiError } from '@/lib/api';
import type { Comparison } from './types';
import EquityChart from './equity-chart';
import styles from './auto-trading.module.css';

type Scenario = { feeBps: string; slippageBps: string };
type Summary = { id: number; validationId: number; createdAt: string };
type Analysis = { summary: Summary; results: (Scenario & { baseline: boolean; result: Comparison })[] };
const amount = (v: string | number) => (Math.abs(Number(v)) < .00005 ? 0 : Number(v)).toLocaleString('ko-KR', { maximumFractionDigits: 4 });
export default function CostAnalysis({ validationId, feeBps, slippageBps }: { validationId: number; feeBps: string; slippageBps: string }) {
  const { authFetch } = useAuth();
  const [scenarios, setScenarios] = useState<Scenario[]>([{ feeBps: '0', slippageBps: '0' }, { feeBps: '10', slippageBps: '20' }]);
  const [records, setRecords] = useState<Summary[]>([]);
  const [selected, setSelected] = useState<number | null>(null);
  const [analysis, setAnalysis] = useState<Analysis | null>(null);
  const [line, setLine] = useState(1);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [pending, setPending] = useState<{ key: string; body: { scenarios: Scenario[] } } | null>(null);
  const flight = useRef(false);
  const generation = useRef(0);
  const current = analysis?.summary.id === selected ? analysis : null;
  useEffect(() => {
    const abort = new AbortController();
    const stamp = generation.current;
    void authFetch<Summary[]>(`/api/auto-trading/validations/${validationId}/cost-analyses`, { signal: abort.signal })
      .then(data => { if (!abort.signal.aborted && stamp === generation.current) { setRecords(data); if (data.length) setSelected(data[0].id); } })
      .catch(e => { if (!abort.signal.aborted) setError(e.message); });
    return () => abort.abort();
  }, [authFetch, validationId]);
  useEffect(() => {
    if (selected === null) return;
    const abort = new AbortController();
    void authFetch<Analysis>(`/api/auto-trading/cost-analyses/${selected}`, { signal: abort.signal })
      .then(data => { if (!abort.signal.aborted) { setAnalysis(data); setLine(1); } })
      .catch(e => { if (!abort.signal.aborted) setError(e.message); });
    return () => abort.abort();
  }, [authFetch, selected]);
  async function calculate() {
    if (flight.current) return;
    const request = pending ?? { key: crypto.randomUUID(), body: { scenarios: scenarios.map(s => ({ ...s })) } };
    generation.current++; flight.current = true; setBusy(true); setPending(request); setError(''); setMessage('');
    try {
      const data = await authFetch<Analysis>(`/api/auto-trading/validations/${validationId}/cost-analyses`, {
        method: 'POST', headers: { 'Idempotency-Key': request.key }, body: request.body,
      });
      setAnalysis(data); setSelected(data.summary.id); setLine(1); setPending(null);
      setRecords(rows => [data.summary, ...rows.filter(r => r.id !== data.summary.id)]);
      setMessage(`비용 비교 #${data.summary.id} 저장 완료`);
    } catch (e) {
      if (e instanceof ApiError && e.status >= 400 && e.status < 500) setPending(null);
      setError(e instanceof Error ? e.message : '비용 비교 실패');
    } finally { flight.current = false; setBusy(false); }
  }
  async function download(format: 'csv' | 'html') {
    if (!current || flight.current) return;
    flight.current = true; setBusy(true); setError('');
    try {
      const file = await authFetch<{ filename: string; contentType: string; content: string }>(`/api/auto-trading/cost-analyses/${current.summary.id}/export?format=${format}`);
      const url = URL.createObjectURL(new Blob([file.content], { type: file.contentType }));
      const link = document.createElement('a'); link.href = url; link.download = file.filename; document.body.appendChild(link); link.click(); link.remove();
      setTimeout(() => URL.revokeObjectURL(url), 60000);
      setMessage(`${file.filename} 다운로드를 시작했습니다.`);
    } catch (e) { setError(e instanceof Error ? e.message : '보고서 내보내기 실패'); }
    finally { flight.current = false; setBusy(false); }
  }
  const chosen = current?.results[line];
  return <section className={styles.panel}>
    <div className={styles.panelHeader}><h2>비용 민감도</h2><span>검증 #{validationId} · 동일 가격</span></div>
    <p>기준 수수료 {feeBps}bp / 슬리피지 {slippageBps}bp. 비용만 바꿔 같은 검증 구간을 다시 계산합니다. 1bp = 0.01%.</p>
    <form onSubmit={e => { e.preventDefault(); void calculate(); }}>
      <fieldset disabled={busy || pending !== null} style={{ border: 0, padding: 0 }}>
        {scenarios.map((s, i) => <div className={styles.controls} key={i}>
          <label>조건 {i + 1} 수수료 (bp)<input type="number" min="0" max="100" step="any" required value={s.feeBps} onChange={e => setScenarios(rows => rows.map((r, j) => j === i ? { ...r, feeBps: e.target.value } : r))} /></label>
          <label>조건 {i + 1} 슬리피지 (bp)<input type="number" min="0" max="100" step="any" required value={s.slippageBps} onChange={e => setScenarios(rows => rows.map((r, j) => j === i ? { ...r, slippageBps: e.target.value } : r))} /></label>
          <button type="button" aria-label={`조건 ${i + 1} 삭제`} disabled={scenarios.length === 1} onClick={() => setScenarios(rows => rows.filter((_, j) => j !== i))}>삭제</button>
        </div>)}
        <button type="button" disabled={scenarios.length >= 8} onClick={() => setScenarios(rows => [...rows, { feeBps: '', slippageBps: '' }])}>+ 비교 조건</button>
      </fieldset>
      <button className={styles.primary} style={{ marginTop: 12 }} disabled={busy} type="submit">{pending ? '동일 비교 요청 다시 확인' : '비용 비교 실행'}</button>
    </form>
    {error && <p className={styles.error} role="alert">{error}</p>}
    {(busy || message) && <p className={styles.message} role="status">{busy ? '처리 중…' : message}</p>}
    <div className={styles.controls}>
      <label>비용 비교 기록<select value={selected ?? ''} disabled={busy || pending !== null} onChange={e => setSelected(Number(e.target.value) || null)}>
        <option value="">선택</option>{records.map(r => <option key={r.id} value={r.id}>#{r.id} · {new Date(r.createdAt).toLocaleString()}</option>)}</select></label>
      <button disabled={!current || busy} onClick={() => void download('csv')}>CSV 내보내기</button>
      <button disabled={!current || busy} onClick={() => void download('html')}>HTML 보고서</button>
    </div>
    {current && <>
      <div className={styles.table}><table><thead><tr><th>조건 · 수수료 / 슬리피지</th><th>수익률 %</th><th>최대 낙폭 %</th><th>수수료 USD</th><th>체결 횟수</th></tr></thead>
        <tbody>{current.results.map((s, i) => <tr key={i}><th>{s.baseline ? '기준' : `조건 ${i}`} · {s.feeBps} / {s.slippageBps}bp</th>
          <td>{amount(s.result.strategy.returnPct)}</td><td>{amount(s.result.strategy.maxDrawdownPct)}</td><td>{amount(s.result.strategy.fees)}</td><td>{s.result.strategy.trades}</td></tr>)}</tbody></table></div>
      <div className={styles.controls}><label>그래프 비교 조건<select value={line} onChange={e => setLine(Number(e.target.value))}>
        {current.results.slice(1).map((s, i) => <option key={i} value={i + 1}>{s.feeBps} / {s.slippageBps}bp</option>)}</select></label></div>
      {chosen && <EquityChart points={chosen.result.curve.map((p, i) => ({ ...p, benchmark: current.results[0].result.curve[i].strategy }))}
        labels={[`${chosen.feeBps} / ${chosen.slippageBps}bp`, '기준 비용']} />}
    </>}
    <p className={styles.notice}>슬리피지는 체결 가격에 포함됩니다. 비용 변화는 수량·거래 횟수에도 영향을 줄 수 있습니다. HTML은 브라우저에서 열어 인쇄할 수 있습니다.</p>
  </section>;
}
