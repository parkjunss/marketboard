'use client';

import { useEffect, useRef, useState } from 'react';
import { useAuth } from '@/lib/auth-context';
import { ApiError } from '@/lib/api';
import Link from 'next/link';
import styles from './execution.module.css';

type Order = { id: string; side: string; quantity: string; filled: string; status: string; referencePrice: string };
type State = {
  cash: string; availableCash: string; reservedCash: string; shares: string; averageCost: string;
  realizedPnl: string; fees: string; stopped: boolean; reentryBlocked: boolean; economicHash: string;
  orders: Record<string, Order>; broker: Record<string, { status: string }>;
  log: { kind: string; orderId: string | null; reason: string }[];
};
type Run = { id: number; version: number; state: State };
type Incident = { kind: string; active: boolean; openedAt: string; resolvedAt: string | null; occurrences: number; ageSeconds: number; message: string };
type Entry = { sequence: number; recordedAt: string; command: { operation: string; orderId: string | null; quantity: string | null } };
type Verification = { matches: boolean; commandCount: number; differences: { path: string; stored: unknown; replayed: unknown }[] };
type Pending = { path: string; body: unknown; headers?: Record<string, string> };
const operations = [
  ['DISPATCH', '거래소 접수'], ['TIMEOUT', '응답 유실'], ['PARTIAL_FILL', '부분 체결'], ['FILL', '잔량 체결'],
  ['CANCEL', '잔량 취소'], ['REJECT', '주문 거절'], ['RECONCILE', '체결 대사'], ['REVERSE', '역순 수신·대사'], ['DUPLICATE', '중복 수신·대사'],
] as const;

export default function ExecutionPage() {
  const { authFetch, user } = useAuth();
  const [runs, setRuns] = useState<Run[]>([]);
  const [run, setRun] = useState<Run | null>(null);
  const [orderId, setOrderId] = useState('');
  const [quantity, setQuantity] = useState('10');
  const [fillQuantity, setFillQuantity] = useState('4');
  const [capital, setCapital] = useState('10000');
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [pending, setPending] = useState<Pending | null>(null);
  const [monitor, setMonitor] = useState<{ id: number; incidents: Incident[]; timeline: Entry[]; error?: string } | null>(null);
  const [verification, setVerification] = useState<{ id: number; version: number; result: Verification } | null>(null);
  const runId = run?.id;
  const runVersion = run?.version;
  const flight = useRef(false);
  useEffect(() => {
    if (!user) return;
    const controller = new AbortController();
    void authFetch<Run[]>('/api/execution', { signal: controller.signal }).then(values => {
      if (!controller.signal.aborted) { setRuns(values); setRun(values[0] ?? null); }
    }).catch(e => { if (!controller.signal.aborted) setError(e instanceof Error ? e.message : '조회 실패'); });
    return () => controller.abort();
  }, [authFetch, user]);
  useEffect(() => {
    if (!runId) return;
    const controller = new AbortController();
    let timer: ReturnType<typeof setTimeout>;
    const poll = async () => {
      try {
        const [incidents, timeline, latest] = await Promise.all([
          authFetch<Incident[]>(`/api/execution/${runId}/incidents`, { signal: controller.signal }),
          authFetch<Entry[]>(`/api/execution/${runId}/timeline`, { signal: controller.signal }),
          authFetch<Run>(`/api/execution/${runId}`, { signal: controller.signal }),
        ]);
        if (!controller.signal.aborted) {
          setMonitor({ id: runId, incidents, timeline });
          setRun(current => current?.id === runId && latest.version > current.version ? latest : current);
        }
      } catch {
        if (!controller.signal.aborted) setMonitor(current => ({ id: runId, incidents: current?.id === runId ? current.incidents : [], timeline: current?.id === runId ? current.timeline : [], error: '운영 상태 갱신 실패. 이전 결과일 수 있습니다.' }));
      } finally { if (!controller.signal.aborted) timer = setTimeout(poll, 5000); }
    };
    void poll();
    return () => { controller.abort(); clearTimeout(timer); };
  }, [authFetch, runId, runVersion]);

  async function send(request: Pending) {
    if (flight.current) return;
    flight.current = true; setBusy(true); setError(''); setMessage(''); setPending(request);
    try {
      const value = await authFetch<Run>(request.path, { method: 'POST', body: request.body, headers: request.headers });
      setRun(value); setRuns(current => [value, ...current.filter(v => v.id !== value.id)]);
      setOrderId(current => value.state.orders[current] ? current : Object.keys(value.state.orders).at(-1) ?? '');
      setPending(null); setMessage('저장되었습니다.');
    } catch (e) {
      if (e instanceof ApiError && e.status >= 400 && e.status < 500) setPending(null);
      setError(e instanceof Error ? e.message : '요청 실패');
    } finally { flight.current = false; setBusy(false); }
  }
  function create() {
    void send({ path: '/api/execution', headers: { 'Idempotency-Key': crypto.randomUUID() }, body: {
      version: 'execution-v1', initialCapital: capital, feeBps: '5', maxOrderValue: capital, maxPositionWeight: '0.8',
    } });
  }
  function command(operation: string) {
    if (!run) return;
    void send({ path: `/api/execution/${run.id}/commands`, body: {
      key: crypto.randomUUID(), operation, orderId: orderId || null,
      quantity: operation === 'PARTIAL_FILL' ? fillQuantity : ['BUY', 'SELL', 'STALE_BUY'].includes(operation) ? quantity : null,
    } });
  }
  async function verify() {
    if (!run || flight.current) return;
    flight.current = true; setBusy(true); setMessage(''); setError('');
    try {
      const value = await authFetch<Verification>(`/api/execution/${run.id}/verify`, { method: 'POST' });
      setVerification({ id: run.id, version: run.version, result: value });
      setMessage(value.matches ? `${value.commandCount}개 저장 명령 재실행: 전체 상태 일치` : '저장 상태와 리플레이 결과가 다릅니다.');
    } catch (e) { setError(e instanceof Error ? e.message : '검증 실패'); }
    finally { flight.current = false; setBusy(false); }
  }
  const disabled = busy || pending !== null;
  return <main className={styles.page}>
    <header><p className={styles.eyebrow}>EXECUTION SANDBOX · 운영 검증</p><h1>모의 주문 실행</h1>
      <p>SPY 보유 전략의 주문 실행을 검증합니다. 고정 모의 가격 $100, 수수료 5bp를 사용하며 실제 시세·실거래와 연결되지 않습니다.</p><Link href="/execution-demo">공개 복구 예제 보기 →</Link></header>
    <section className={styles.panel} aria-label="실행 설정">
      <h2>1. 실행 준비</h2><div className={styles.controls}>
        <label>초기 자금 (USD)<input value={capital} onChange={e => setCapital(e.target.value)} inputMode="decimal" disabled={disabled} /></label>
        <button disabled={disabled} onClick={create}>새 모의 실행</button>
        <label>저장된 실행<select value={run?.id ?? ''} disabled={disabled} onChange={e => { setRun(runs.find(v => v.id === Number(e.target.value)) ?? null); setOrderId(''); setMessage(''); }}>
          <option value="">선택</option>{runs.map(v => <option key={v.id} value={v.id}>실행 #{v.id} · 명령 {v.version}개</option>)}</select></label>
      </div><p>종목 최대 비중 80%. 주문 금액 한도는 초기 자금입니다. 전량 청산 후 해당 실행에서는 재진입을 차단합니다.</p>
    </section>
    {error && <p role="alert" className={styles.error}>{error}</p>}
    {pending && !busy && <p>응답을 확인하지 못했습니다. 같은 요청 키로 안전하게 다시 확인하세요. <button onClick={() => void send(pending)}>동일 요청 재시도</button></p>}
    <p role="status" aria-live="polite">{busy ? '처리 중…' : message}</p>
    {run && <>
      <section className={styles.panel} aria-label="운영 알림"><h2>운영 알림 · 5초마다 확인</h2>
        {monitor?.id !== run.id ? <p>상태 확인 중…</p> : <>
          {monitor.error && <p role="alert" className={styles.error}>{monitor.error}</p>}
          {!monitor.error && !monitor.incidents.some(i => i.active) && <p>현재 활성 알림이 없습니다.</p>}
          {monitor.incidents.map(i => <article key={i.kind} className={i.active ? styles.incident : styles.resolved}>
            <strong>{i.active ? '조치 필요' : '복구 완료'} · {i.kind}</strong><p>{i.message}</p>
            <small>{i.ageSeconds}초 {i.active ? '지속' : '후 복구'} · 발생 {i.occurrences}회 · {new Date(i.openedAt).toLocaleString()}{i.resolvedAt ? ` → ${new Date(i.resolvedAt).toLocaleString()}` : ''}</small>
          </article>)}
        </>}
        <details><summary>복구 절차</summary><p>결과 불명: 주문을 다시 만들지 말고 체결 대사 → 거래소 미접수일 때만 동일 주문 접수. 미반영 체결: 체결 대사. 계산 실패: 동일 요청 재시도. 리플레이 불일치: 변경이 자동 차단되며 원본 기록 점검 후 재검증이 필요합니다.</p><p>알림은 이 실행 화면에 저장됩니다. 이메일·외부 메신저로 발송하지 않습니다. 시세 지연은 고정 장애 시나리오이며 실제 시세 지연 측정이 아닙니다.</p></details>
      </section>
      <section className={styles.panel}><h2>실행 #{run.id} {run.state.stopped ? '· 신규 주문 중지' : ''}</h2>
        <dl className={styles.metrics}>{[['현금', run.state.cash], ['주문 가능 현금', run.state.availableCash], ['예약 현금', run.state.reservedCash], ['SPY 보유 수량', run.state.shares], ['수수료 포함 평균단가', run.state.averageCost], ['실현 손익', run.state.realizedPnl]].map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value}</dd></div>)}</dl>
        {run.state.reentryBlocked && <p>전량 청산 완료: 재진입 금지</p>}
      </section>
      <section className={styles.panel}><h2>2. 주문 의도 저장</h2><div className={styles.controls}>
        <button disabled={disabled || run.state.stopped} onClick={() => command('STRATEGY_BUY')}>SPY 보유 전략으로 주문 생성</button>
        <label>주문 수량<input inputMode="decimal" value={quantity} onChange={e => setQuantity(e.target.value)} disabled={disabled} /></label>
        <button disabled={disabled || run.state.stopped} onClick={() => command('BUY')}>매수 주문</button>
        <button disabled={disabled || run.state.stopped} onClick={() => command('SELL')}>매도 주문</button>
        <button disabled={disabled} onClick={() => command('STALE_BUY')}>오래된 가격으로 주문 시도</button>
        <button disabled={disabled || run.state.stopped} onClick={() => command('STOP')}>신규 주문 중지</button>
      </div><p>전략 주문은 서버에서 비용을 반영해 목표 비중까지 수량을 산출합니다. 중지는 기존 주문을 취소하거나 보유 종목을 청산하지 않습니다.</p></section>
      <section className={styles.panel}><h2>3. 거래소 처리와 장애 재현</h2>
        <p>주문 선택 → 거래소 접수 → 부분 체결 또는 취소 → 체결 대사 순서로 진행하세요. 대사 전에는 거래소 이벤트만 저장됩니다.</p>
        <div className={styles.controls}><label>대상 주문<select value={orderId} onChange={e => setOrderId(e.target.value)} disabled={disabled}><option value="">주문 선택</option>{Object.values(run.state.orders).map(o => <option key={o.id} value={o.id}>{o.side} {o.quantity} · {o.status} · {o.id.slice(0, 8)}</option>)}</select></label>
          <label>부분 체결 수량<input inputMode="decimal" value={fillQuantity} onChange={e => setFillQuantity(e.target.value)} disabled={disabled} /></label></div>
        <div className={styles.controls}>{operations.map(([op, label]) => <button key={op} disabled={disabled || !orderId} onClick={() => command(op)}>{label}</button>)}</div>
        <div className={styles.table}><table><thead><tr><th>주문</th><th>방향</th><th>기준가 (USD)</th><th>수량 / 체결</th><th>내부 상태</th><th>거래소 상태</th></tr></thead><tbody>{Object.values(run.state.orders).map(o => <tr key={o.id}><td title={o.id}>{o.id.slice(0, 8)}</td><td>{o.side}</td><td>{o.referencePrice}</td><td>{o.quantity} / {o.filled}</td><td>{o.status}</td><td>{run.state.broker[o.id]?.status ?? '미제출'}</td></tr>)}</tbody></table></div>
      </section>
      <section className={styles.panel}><h2>4. 저장 기록 검증</h2><button disabled={disabled} onClick={() => void verify()}>DB 기록으로 리플레이 검증</button>
        <p className={styles.hash}>경제적 상태 SHA-256: {run.state.economicHash}</p>
        {verification?.id === run.id && verification.version === run.version && <div aria-label="리플레이 비교"><p>{verification.result.matches ? '저장 상태와 재생 상태가 모두 일치합니다.' : '불일치 항목 (최대 50개)'}</p>{verification.result.differences.map(d => <div className={styles.incident} key={d.path}><strong>{d.path}</strong><p>저장: {JSON.stringify(d.stored)}</p><p>재생: {JSON.stringify(d.replayed)}</p></div>)}</div>}
        <details><summary>명령 타임라인 · 서버 기록 시각</summary><ol className={styles.log}>{monitor?.id === run.id && monitor.timeline.map(e => <li key={e.sequence}><time>{new Date(e.recordedAt).toLocaleString()}</time> · #{e.sequence} <strong>{e.command.operation}</strong> · 주문 {e.command.orderId?.slice(0, 8) ?? '—'} · 수량 {e.command.quantity ?? '—'}</li>)}</ol></details>
        <ol className={styles.log}>{run.state.log.map((entry, i) => <li key={i}><strong>{entry.kind}</strong> {entry.orderId?.slice(0, 8)} — {entry.reason}</li>)}</ol>
      </section>
    </>}
  </main>;
}
