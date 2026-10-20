'use client';

import { useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { useAuth } from '@/lib/auth-context';
import { ApiError } from '@/lib/api';
import styles from './auto-trading.module.css';
import EquityChart from './equity-chart';
import PeriodValidation from './period-validation';
import StrategyWorkbench from './strategy-workbench';
import type { Config, State, Run, Comparison } from './types';

type Entry = { step: number; recordedAt: string; payload: { date: string; action: string; reason: string; signalDate: string | null;
  signalClose: string | null; movingAverage: string | null; quantity: string; fillPrice: string | null; fee: string; state: State } };
type Detail = { run: Run; journal: Entry[]; controls: { action: string; recordedAt: string }[] };
type Range = { ticker: string; firstDate: string; lastDate: string; count: number };

const statusLabel: Record<string, string> = { RUNNING: '자동 실행 중', PAUSED: '일시정지', ERROR: '오류로 정지', COMPLETED: '완료', STOPPED: '종료' };
const initial: Config = { version: 'sma-paper-v1', ticker: '', startDate: '', endDate: '', window: 20, initialCapital: '10000',
  feeBps: '5', slippageBps: '10', maxPositionWeight: '0.8', maxOrderValue: '5000', intervalSeconds: 5 };
function dates(range: Range) {
  const start = new Date(`${range.lastDate}T00:00:00Z`); start.setUTCDate(start.getUTCDate() - 180);
  return { startDate: start.toISOString().slice(0, 10) < range.firstDate ? range.firstDate : start.toISOString().slice(0, 10), endDate: range.lastDate };
}
function amount(value: string | null | undefined) {
  return value == null ? '—' : (Math.abs(Number(value)) < .00005 ? 0 : Number(value)).toLocaleString('ko-KR', { maximumFractionDigits: 4 });
}

export default function AutoTradingPage() {
  const { authFetch } = useAuth();
  const [ranges, setRanges] = useState<Range[]>([]);
  const [config, setConfig] = useState<Config>(initial);
  const [runs, setRuns] = useState<Run[]>([]);
  const [selected, setSelected] = useState<number | null>(null);
  const [detail, setDetail] = useState<Detail | null>(null);
  const [comparisonData, setComparison] = useState<{ id: number; data: Comparison } | null>(null);
  const [comparisonError, setComparisonError] = useState('');
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);
  const [tradesOnly, setTradesOnly] = useState(false);
  const [tab, setTab] = useState<'overview' | 'strategies' | 'validation' | 'journal'>('overview');
  const [showSetup, setShowSetup] = useState(false);
  const [pending, setPending] = useState<{ key: string; body: Config } | null>(null);
  const flight = useRef(false);
  const generation = useRef(0);
  const cursor = detail?.run.id === selected ? detail.run.cursor : null;
  const comparison = comparisonData?.id === selected ? comparisonData.data : null;
  useEffect(() => {
    if (selected === null || cursor === null) return;
    const abort = new AbortController();
    void authFetch<Comparison>(`/api/auto-trading/${selected}/comparison`, { signal: abort.signal })
      .then(data => { if (!abort.signal.aborted) { setComparison({ id: selected, data }); setComparisonError(''); } })
      .catch(e => { if (!abort.signal.aborted) setComparisonError(e instanceof Error ? e.message : '비교 조회 실패'); });
    return () => abort.abort();
  }, [authFetch, selected, cursor]);
  useEffect(() => {
    const abort = new AbortController();
    void authFetch<Range[]>('/api/auto-trading/prices', { signal: abort.signal }).then(data => {
      setRanges(data);
      const first = data.find(v => v.ticker === 'SPY') ?? data.find(v => v.ticker === 'AAPL') ?? data[0];
      if (first) setConfig(current => ({ ...current, ticker: first.ticker, ...dates(first) }));
    }).catch(e => { if (!abort.signal.aborted) setError(e.message); });
    return () => abort.abort();
  }, [authFetch]);
  useEffect(() => {
    const abort = new AbortController();
    let fetching = false;
    async function poll() {
      if (fetching || flight.current) return;
      fetching = true;
      const stamp = generation.current;
      try {
        const list = await authFetch<Run[]>('/api/auto-trading', { signal: abort.signal });
        const data = selected === null ? null : await authFetch<Detail>(`/api/auto-trading/${selected}`, { signal: abort.signal });
        if (!abort.signal.aborted && stamp === generation.current) {
          setRuns(list); setDetail(data);
          if (selected === null && list.length) setSelected(list[0].id);
        }
      } catch (e) { if (!abort.signal.aborted) setError(e instanceof Error ? e.message : '기록 조회 실패'); }
      finally { fetching = false; }
    }
    void poll();
    const timer = setInterval(() => void poll(), 3000);
    return () => { abort.abort(); clearInterval(timer); };
  }, [authFetch, selected]);
  async function create() {
    if (flight.current) return;
    const request = pending ?? { key: crypto.randomUUID(), body: { ...config } };
    setPending(request); flight.current = true; generation.current++; setBusy(true); setError(''); setMessage('');
    try {
      const run = await authFetch<Run>('/api/auto-trading', { method: 'POST', headers: { 'Idempotency-Key': request.key }, body: request.body });
      setSelected(run.id); setDetail({ run, journal: [], controls: [] }); setPending(null); setShowSetup(false); setTab('overview');
      setRuns(current => [run, ...current.filter(r => r.id !== run.id)]);
      setMessage('전략과 가격을 저장했습니다. 화면을 닫아도 서버가 다음 거래일을 자동 처리합니다.');
    } catch (e) {
      if (e instanceof ApiError && e.status >= 400 && e.status < 500) setPending(null);
      setError(e instanceof Error ? e.message : '자동 실행 생성 실패');
    } finally { flight.current = false; setBusy(false); }
  }
  async function action(action: 'PAUSE' | 'RESUME' | 'STOP' | 'VERIFY') {
    if (selected === null || flight.current) return;
    flight.current = true; generation.current++; setBusy(true); setError(''); setMessage('');
    try {
      if (action === 'VERIFY') {
        const result = await authFetch<{ matches: boolean; steps: number }>(`/api/auto-trading/${selected}/verify`, { method: 'POST' });
        setMessage(`${result.steps}개 거래일의 잔고·매매 일지 재생: ${result.matches ? '전체 일치' : '불일치 — 실행을 정지하고 기록을 점검하세요.'}`);
      } else {
        const run = await authFetch<Run>(`/api/auto-trading/${selected}/control`, { method: 'POST', body: { action } });
        setDetail(current => current ? { ...current, run } : null);
        setMessage(action === 'PAUSE' ? '일시정지했습니다. 보유 종목과 기록을 유지합니다.' : action === 'STOP' ? '실행을 종료했습니다. 모든 기록은 남으며 다시 시작하려면 새 실행을 만드세요.' : '다음 거래일부터 자동 실행을 재개했습니다.');
      }
    } catch (e) { setError(e instanceof Error ? e.message : '요청 실패'); }
    finally { flight.current = false; setBusy(false); }
  }
  const run = detail?.run.id === selected ? detail.run : null;
  const locked = busy || pending !== null;
  return <main className={styles.page}>
    <header className={styles.header}>
      <div><h1>자동 모의 매매</h1><p>전략을 실행하고, 기록으로 비교하세요.</p></div>
      <button className={styles.primary} aria-expanded={showSetup} disabled={locked} onClick={() => setShowSetup(v => !v)}>{showSetup ? '설정 닫기' : '+ 새 모의 실행'}</button>
    </header>
    {showSetup && <section className={styles.panel}>
      <div className={styles.panelHeader}><h2>새 실행 설정</h2><span>이동평균 추세 전략</span></div>
      <p>전일 종가가 이동평균 위이면 매수, 아래이면 매도합니다. 다음 거래일 시가에 비용을 반영합니다.</p>
      <form onSubmit={e => { e.preventDefault(); void create(); }}>
        <fieldset disabled={locked} style={{ border: 0, padding: 0, margin: 0 }}>
          <div className={styles.controls}>
            <label htmlFor="auto-symbol">종목<select id="auto-symbol" value={config.ticker} onChange={e => {
              const range = ranges.find(v => v.ticker === e.target.value);
              if (range) setConfig(c => ({ ...c, ticker: range.ticker, ...dates(range) }));
            }}><option value="">저장된 가격 선택</option>{ranges.map(r => <option key={r.ticker}>{r.ticker}</option>)}</select></label>
            <label htmlFor="auto-start">시작일<input id="auto-start" type="date" required value={config.startDate} onChange={e => setConfig(c => ({ ...c, startDate: e.target.value }))} /></label>
            <label htmlFor="auto-end">종료일<input id="auto-end" type="date" required value={config.endDate} onChange={e => setConfig(c => ({ ...c, endDate: e.target.value }))} /></label>
            <label htmlFor="auto-window">이동평균 거래일<input id="auto-window" type="number" min="2" max="200" required value={config.window} onChange={e => setConfig(c => ({ ...c, window: Number(e.target.value) }))} /></label>
            <label htmlFor="auto-interval">거래일 진행 간격 (초)<input id="auto-interval" type="number" min="2" max="300" required value={config.intervalSeconds} onChange={e => setConfig(c => ({ ...c, intervalSeconds: Number(e.target.value) }))} /></label>
            {([['initialCapital', '초기 자금 (USD)'], ['maxOrderValue', '1회 주문 한도 (USD)'], ['maxPositionWeight', '매수 시 최대 비중 (0~1)'], ['feeBps', '수수료 (bp)'], ['slippageBps', '슬리피지 (bp)']] as const).map(([key, label]) =>
              <label key={key} htmlFor={`auto-${key}`}>{label}<input id={`auto-${key}`} required inputMode="decimal" value={config[key]} onChange={e => setConfig(c => ({ ...c, [key]: e.target.value }))} /></label>)}
          </div>
        </fieldset>
        <p>{ranges.find(r => r.ticker === config.ticker) && (() => { const r = ranges.find(v => v.ticker === config.ticker)!; return `저장된 가격: ${r.firstDate} ~ ${r.lastDate} (${r.count}개)`; })()}</p>
        <p className={styles.notice}>과거 가격 최대 1,000일 · 실제 주문 없음 · 수정주가·배당 반영 미확인</p>
        <button className={styles.primary} type="submit" disabled={busy || !config.ticker}>{pending ? '동일 요청으로 다시 확인' : '자동 실행 시작'}</button>
      </form>
    </section>}
    {error && <p role="alert" className={styles.error}>{error}</p>}
    {(busy || message) && <p role="status" aria-live="polite" className={styles.message}>{busy ? '처리 중…' : message}</p>}
    <div className={styles.runBar}>
      <div className={styles.runIdentity}>
        <strong className={styles.ticker}>{run?.configuration.ticker ?? '실행 기록'}</strong>
        {run && <><span className={styles.badge}>{statusLabel[run.status]}</span><span className={styles.muted}>SMA {run.configuration.window} · {run.cursor}/{run.barCount} 거래일</span></>}
      </div>
      <label><span className={styles.muted}>실행 기록</span><select id="auto-run" aria-label="실행 기록 선택" value={selected ?? ''} disabled={busy} onChange={e => { setSelected(Number(e.target.value) || null); setDetail(null); setMessage(''); }}>
        <option value="">선택</option>{runs.map(r => <option key={r.id} value={r.id}>#{r.id} {r.configuration.ticker} · SMA {r.configuration.window} · {statusLabel[r.status]}</option>)}</select></label>
    </div>
    {!runs.length && <div className={styles.empty}><h2>첫 전략을 실행해 보세요</h2><p>새 모의 실행에서 종목과 기간을 선택하면 자산 추이와 매매 기록이 쌓입니다.</p></div>}
    {run && <>
      {run.error && <p role="alert" className={styles.error}>{run.error}</p>}
      <dl className={styles.metrics}>
        <div><dt>평가 자산 · USD</dt><dd>{Number(run.state.equity).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</dd></div>
        <div><dt>수익률</dt><dd className={Number(run.state.returnPct) < 0 ? styles.negative : Number(run.state.returnPct) > 0 ? styles.positive : undefined}>{amount(run.state.returnPct)}<small>%</small></dd></div>
        <div><dt>최대 낙폭</dt><dd>{amount(run.state.maxDrawdownPct)}<small>%</small></dd></div>
        <div><dt>매수·보유 대비</dt><dd>{comparison ? amount(comparison.excessReturnPct) : '—'}<small>%p</small></dd></div>
      </dl>
    </>}
    <nav className={styles.tabs} aria-label="자동 매매 보기">
      {([['overview', '성과 개요'], ['strategies', '전략 보관 · 비교'], ['validation', '기간 검증'], ['journal', '매매 기록']] as const).map(([key, label]) => <button key={key} aria-current={tab === key} onClick={() => setTab(key)}>{label}</button>)}
    </nav>
    {tab === 'overview' && run && <>
      <section className={styles.panel}>
        <div className={styles.panelHeader}><h2>자산 추이</h2><span>{run.configuration.startDate} — {comparison?.asOf ?? run.configuration.endDate} · USD</span></div>
        {comparisonError ? <p role="alert" className={styles.error}>{comparisonError}</p> : comparison ? <EquityChart points={comparison.curve} /> : <div className={styles.empty}>자산 추이를 불러오는 중…</div>}
      </section>
      <div className={styles.bottomGrid}>
        <section className={styles.panel}><div className={styles.panelHeader}><h2>성과 비교</h2><span>동일 투자 한도 기준</span></div>
          {comparison && <div className={styles.table}><table><thead><tr><th>지표</th><th>내 전략</th><th>매수·보유</th></tr></thead><tbody>
            {([['returnPct', '수익률 (%)'], ['maxDrawdownPct', '최대 낙폭 (%)'], ['fees', '수수료 (USD)'], ['trades', '체결 횟수']] as const).map(([key, label]) => <tr key={key}><th>{label}</th><td>{amount(String(comparison.strategy[key]))}</td><td>{amount(String(comparison.benchmark[key]))}</td></tr>)}
          </tbody></table></div>}
          <details><summary>비교 기준</summary><p>매수·보유는 SMA 준비 기간 뒤 첫 시가에 같은 비중·주문 한도·비용으로 한 번 매수합니다. 두 전략 모두 마지막 종가로 평가하며 강제 매도하지 않습니다.</p></details>
        </section>
        <section className={styles.panel}><div className={styles.panelHeader}><h2>실행 정보</h2><span>#{run.id}</span></div>
          <dl className={styles.settings}>{([['현금', `$${amount(run.state.cash)}`], ['보유 수량', `${amount(run.state.shares)}주`], ['실현 손익', `$${amount(run.state.realizedPnl)}`], ['미실현 손익', `$${amount(run.state.unrealizedPnl)}`], ['최대 비중 / 주문 한도', `${run.configuration.maxPositionWeight} / $${run.configuration.maxOrderValue}`], ['수수료 / 슬리피지', `${run.configuration.feeBps} / ${run.configuration.slippageBps} bp`]]).map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value}</dd></div>)}</dl>
          <div className={styles.controls}>
            {run.status === 'RUNNING' && <button disabled={busy} onClick={() => void action('PAUSE')}>일시정지</button>}
            {['PAUSED', 'ERROR'].includes(run.status) && <button disabled={busy} onClick={() => void action('RESUME')}>자동 실행 재개</button>}
            {!['COMPLETED', 'STOPPED'].includes(run.status) && <button disabled={busy} onClick={() => void action('STOP')}>실행 종료</button>}
            <button disabled={busy} onClick={() => void action('VERIFY')}>기록 검증</button>
          </div>
          <details><summary>진행 방식</summary><p>{run.configuration.intervalSeconds}초마다 1거래일을 처리합니다. 화면을 닫아도 서버가 진행하며, 정지·종료 시 보유 종목을 강제 매도하지 않습니다.</p></details>
        </section>
      </div>
      <p className={styles.notice}>과거 저장 가격의 모의 결과 · 수정주가·배당 반영 미확인 · 실제 주문 없음</p>
      <Link href="/execution">주문 장애 검증실 ↗</Link>
    </>}
    <div hidden={tab !== 'strategies'}><StrategyWorkbench run={run} runs={runs} disabled={locked} onStarted={created => {
      generation.current++;
      setSelected(created.id); setDetail({ run: created, journal: [], controls: [] });
      setRuns(current => [created, ...current.filter(r => r.id !== created.id)]);
    }} /></div>
    <div hidden={tab !== 'validation'}><PeriodValidation active={tab === 'validation'} /></div>
    {tab === 'journal' && run && detail && <section className={styles.panel}>
      <div className={styles.panelHeader}><h2>매매 기록 <span>· {detail.journal.length}거래일</span></h2><label className={styles.filter}><input type="checkbox" checked={tradesOnly} onChange={e => setTradesOnly(e.target.checked)} /> 매수·매도만</label></div>
      <div className={styles.table}><table><thead><tr><th>거래일 / 저장 시각</th><th>판단</th><th>판단 근거</th><th>전일 종가 / SMA</th><th>체결 수량 / 가격</th><th>수수료</th><th>현금 / 평가 자산</th></tr></thead>
        <tbody>{detail.journal.filter(e => !tradesOnly || e.payload.action !== 'HOLD').map(({ step, recordedAt, payload: e }) => <tr key={step}>
          <td>{e.date}<br /><small>{new Date(recordedAt).toLocaleString()}</small></td><td><span className={e.action === 'BUY' ? styles.positive : e.action === 'SELL' ? styles.negative : styles.muted}>{e.action === 'BUY' ? '매수' : e.action === 'SELL' ? '매도' : '관망'}</span></td><td>{e.reason}<br /><small>신호일 {e.signalDate ?? '—'}</small></td>
          <td>{amount(e.signalClose)} / {amount(e.movingAverage)}</td><td>{amount(e.quantity)} / {amount(e.fillPrice)}</td><td>{amount(e.fee)}</td><td>{amount(e.state.cash)} / {amount(e.state.equity)}</td>
        </tr>)}</tbody></table></div>
      {!detail.journal.length && <p>첫 거래일을 처리하면 판단과 잔고가 자동 기록됩니다.</p>}
      <details><summary>실행 제어 이력</summary><ol className={styles.log}>{detail.controls.map((c, i) => <li key={i}>{new Date(c.recordedAt).toLocaleString()} · {c.action}</li>)}</ol></details>

    </section>}
  </main>;
}
