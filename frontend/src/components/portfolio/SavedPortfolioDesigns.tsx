'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { useAuth } from '@/lib/auth-context';
import type { DesignConfiguration, DesignResult } from './PortfolioDesigner';
import styles from './portfolio-designer.module.css';

type Summary = { id: number; name: string; selectedId: string; createdAt: string };
export type SavedDesign = Summary & { payload: { configuration: DesignConfiguration; result: DesignResult } };

export function SavedPortfolioDesigns({ result, selected, dirty, busy, onBusy, onRestore }: {
  result: DesignResult | null; selected: string; dirty: boolean; busy: boolean;
  onBusy: (busy: boolean) => void; onRestore: (saved: SavedDesign) => void;
}) {
  const { authFetch } = useAuth();
  const [records, setRecords] = useState<Summary[]>([]);
  const [name, setName] = useState('');
  const [recordId, setRecordId] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState('');
  const [loading, setLoading] = useState(true);
  const pending = useRef<{ input: string; key: string } | null>(null);
  const refresh = useCallback(async (signal?: AbortSignal) => {
    try { const list=await authFetch<Summary[]>('/api/portfolio/designs', { signal }); if (!signal?.aborted) setRecords(list); }
    catch (e) { if (!signal?.aborted) setError(e instanceof Error ? e.message : '저장 목록을 불러오지 못했습니다.'); }
    finally { if (!signal?.aborted) setLoading(false); }
  }, [authFetch]);
  useEffect(()=>{
    const controller=new AbortController();
    authFetch<Summary[]>('/api/portfolio/designs',{signal:controller.signal})
      .then(list=>{if(!controller.signal.aborted)setRecords(list);})
      .catch(e=>{if(!controller.signal.aborted)setError(e instanceof Error?e.message:'저장 목록을 불러오지 못했습니다.');})
      .finally(()=>{if(!controller.signal.aborted)setLoading(false);});
    return ()=>controller.abort();
  },[authFetch]);
  async function load() {
    onBusy(true);setError(null);setMessage('');
    try { const saved=await authFetch<SavedDesign>(`/api/portfolio/designs/${recordId}`); onRestore(saved);setName(saved.name);setMessage('저장 당시 조건과 결과를 불러왔습니다.'); }
    catch(e) {setError(e instanceof Error?e.message:'설계를 불러오지 못했습니다.');}
    finally {onBusy(false);}
  }
  async function save() {
    if (!result?.configuration || !name.trim() || dirty) return;
    const body={name:name.trim(),selectedId:selected,configuration:result.configuration};
    const input=JSON.stringify(body);
    if (pending.current?.input!==input) pending.current={input,key:crypto.randomUUID()};
    onBusy(true);setError(null);setMessage('서버에서 재계산 후 저장하고 있습니다.');
    try {
      const saved=await authFetch<SavedDesign>('/api/portfolio/designs',{method:'POST',body,
        headers:{'Idempotency-Key':pending.current!.key},signal:AbortSignal.timeout(90_000)});
      pending.current=null;onRestore(saved);setRecordId(String(saved.id));setMessage('새 기록으로 저장했습니다. 화면은 저장된 재계산 결과입니다.');
      setRecords(old=>[saved,...old.filter(r=>r.id!==saved.id)].slice(0,50));
    } catch(e) {setMessage('');setError(e instanceof Error?e.message:'저장하지 못했습니다.');}
    finally {onBusy(false);}
  }
  return <section className={styles.notice} aria-label="설계 저장과 불러오기">
    <h2>설계 저장과 불러오기</h2>
    <p>계정별 최근 50개 기록입니다. 저장 당시 결과는 보존되며, 다시 계산해도 원본은 바뀌지 않습니다.</p>
    <div className={styles.grid}>
      <label>저장한 설계<select value={recordId} disabled={busy||loading} onChange={e=>setRecordId(e.target.value)}><option value="">기록 선택</option>{records.map(r=><option key={r.id} value={r.id}>{r.name} · {new Date(r.createdAt).toLocaleString('ko-KR')}</option>)}</select></label>
      <label>새 기록 이름<input value={name} maxLength={100} disabled={busy} placeholder="예: ETF 6종 · 현금 10%" onChange={e=>setName(e.target.value)}/></label>
    </div>
    <button type="button" disabled={busy||loading} onClick={()=>{setLoading(true);setError(null);void refresh();}}>목록 새로고침</button>
    <button type="button" disabled={busy||!recordId} onClick={()=>void load()}>저장 기록 불러오기</button>
    <button type="button" disabled={busy||dirty||result?.status!=='READY'||!result.configuration||!name.trim()} onClick={()=>void save()}>서버 재계산 후 새 기록 저장</button>
    <p className={styles.chartHint}>저장 시 데이터가 갱신되면 현재 비교 결과와 달라질 수 있습니다. 조건을 바꿨다면 먼저 배분안을 다시 비교하세요.</p>
    {loading && <p role="status">저장 목록을 불러오고 있습니다.</p>}
    {!loading && !error && records.length===0 && <p>저장된 설계가 없습니다. 조건을 입력해 배분안을 비교한 뒤 저장하세요.</p>}
    {message && <p role="status">{message}</p>}
    {error && <p role="alert">{error}</p>}
  </section>;
}
