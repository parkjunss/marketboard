'use client';

import { useState, type FormEvent } from 'react';
import { useAuth } from '@/lib/auth-context';
import { portfolioPeriod } from '@/lib/portfolio-period';
import styles from './portfolio-designer.module.css';

type Asset = { ticker: string; observations: number; returnObservations: number; startDate: string | null; endDate: string | null;
  totalReturnPct: number | null; volatilityPct: number | null; maxDrawdownPct: number | null; issue: string | null;
  commonPeriodReturnPct: number | null; commonVolatilityPct: number | null };
type Allocation = { ticker: string; weightPct: number; amount: number; price: number | null; priceDate: string | null; quantity: number | null; purchaseAmount: number | null };
type Candidate = { id: string; label: string; periodReturnPct: number; volatilityPct: number;
  allocations: Allocation[]; cashAmount: number; integerShareCash: number | null };
type Result = { status: string; message: string; assets: Asset[]; candidates: Candidate[]; frontier: Candidate[];
  commonObservations: number; excludedIntervals: number; commonStart: string | null; commonEnd: string | null;
  correlation: (number | null)[][] | null; warnings: string[] };
const defaults = ['SPY', 'QQQ', 'EFA', 'EEM', 'IEF', 'GLD'];
const number = (v: number | null) => v == null ? '—' : v.toLocaleString('ko-KR', { maximumFractionDigits: 2 });
const pct = (v: number | null) => v == null ? '—' : `${number(v)}%`;

function Frontier({ result, selected, onSelect }: { result: Result; selected: string; onSelect: (id: string) => void }) {
  const points = [...result.candidates, ...result.frontier];
  const assets = result.assets.filter(a => a.commonPeriodReturnPct != null && a.commonVolatilityPct != null)
    .map(a => ({ticker:a.ticker, periodReturnPct:a.commonPeriodReturnPct, volatilityPct:a.commonVolatilityPct}));
  const values = [...points, ...assets];
  const maxX = Math.max(1, ...values.map(p => p.volatilityPct ?? 0))*1.1;
  const lowY = Math.min(0, ...values.map(p => p.periodReturnPct ?? 0));
  const highY = Math.max(1, ...values.map(p => p.periodReturnPct ?? 0));
  const spanY = Math.max(1, highY-lowY)*1.15;
  const x = (v: number) => 65+v/maxX*690;
  const y = (v: number) => 325-(v-lowY)/spanY*270;
  return <svg className={styles.chart} viewBox="0 0 800 385" role="group" aria-label="과거 기간 수익률–변동성 경계: 가로축 연환산 변동성, 세로축 과거 기간 수익률">
    {[0,1,2,3,4].map(i => <g key={i}>
      <line x1="65" x2="755" y1={y(lowY+spanY*i/4)} y2={y(lowY+spanY*i/4)} stroke="var(--mb-border)" />
      <text x="57" y={y(lowY+spanY*i/4)+4} textAnchor="end">{number(lowY+spanY*i/4)}%</text>
      <text x={x(maxX*i/4)} y="345" textAnchor="middle">{number(maxX*i/4)}%</text>
    </g>)}
    <text x="400" y="375" textAnchor="middle">연환산 변동성 (%) →</text>
    <text x="65" y="20">과거 기간 수익률 (%) ↑</text>
    <polyline points={result.frontier.map(p => `${x(p.volatilityPct)},${y(p.periodReturnPct)}`).join(' ')} fill="none" stroke="var(--mb-accent)" strokeWidth="3" />
    {assets.map(a => <g key={a.ticker}><circle cx={x(a.volatilityPct!)} cy={y(a.periodReturnPct!)} r="4" fill="var(--mb-text-muted)"/><text x={x(a.volatilityPct!)+7} y={y(a.periodReturnPct!)-7}>{a.ticker}</text></g>)}
    {points.map(p => <circle key={p.id} cx={x(p.volatilityPct)} cy={y(p.periodReturnPct)} r={p.id===selected?8:5}
      fill={p.id===selected?'#f4b85e':'var(--mb-accent)'} stroke="var(--mb-surface)" strokeWidth="2"
      role="button" tabIndex={0} aria-label={`${p.label}, 변동성 ${pct(p.volatilityPct)}, 기간 수익률 ${pct(p.periodReturnPct)}`}
      onClick={() => onSelect(p.id)} onKeyDown={e => { if(e.key==='Enter'||e.key===' ') {e.preventDefault();onSelect(p.id);} }}>
      <title>{p.label}: 변동성 {pct(p.volatilityPct)} / 기간 수익률 {pct(p.periodReturnPct)}</title>
    </circle>)}
  </svg>;
}

export function PortfolioDesigner() {
  const { authFetch } = useAuth();
  const [tickers, setTickers] = useState(defaults);
  const [ticker, setTicker] = useState('');
  const [result, setResult] = useState<Result | null>(null);
  const [selected, setSelected] = useState('minimum');
  const [busy, setBusy] = useState(false);
  const [dirty, setDirty] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [period, setPeriod] = useState('');
  const [dates, setDates] = useState({ startDate: '', endDate: '' });
  function add() {
    const value = ticker.trim().toUpperCase();
    if (!/^[A-Z][A-Z0-9.\-]{0,14}$/.test(value)) {setError('올바른 티커를 입력하세요. 예: AAPL, BRK-B'); return;}
    if (tickers.includes(value)) {setError('이미 선택한 종목입니다.'); return;}
    if (tickers.length >= 10) {setError('최대 10개까지 비교할 수 있습니다.'); return;}
    setTickers([...tickers,value]);setTicker('');setDirty(true);setError(null);
  }
  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (tickers.length<2) {setError('후보 자산을 2개 이상 선택하세요.');return;}
    const form = new FormData(e.currentTarget);
    setBusy(true);setError(null);
    try {
      const data = await authFetch<Result>('/api/portfolio/design', {method:'POST', signal:AbortSignal.timeout(90_000), body:{
        tickers, startDate:form.get('startDate'), endDate:form.get('endDate'),
        capital:Number(form.get('capital')),cash:Number(form.get('cash')),maxWeightPct:Number(form.get('maxWeightPct')),
        riskLimitPct:Number(form.get('riskLimitPct')),
      }});
      setResult(data);setSelected(data.candidates.find(c=>c.id==='risk-limit')?.id ?? data.candidates[0]?.id ?? '');setDirty(false);
    } catch(e) {setError(e instanceof Error?e.message:'계산에 실패했습니다.');setDirty(true);}
    finally {setBusy(false);}
  }
  const candidates = result ? [...result.candidates,...result.frontier] : [];
  const chosen = candidates.find(c=>c.id===selected);
  return <section className={styles.designer} aria-label="포트폴리오 설계">
    <p className={styles.lead}>투자금과 후보 자산을 정하고, 위험 대비 수익과 배분 금액을 비교하세요. USD 표시 주식·ETF 기준이며 환전·공매도·차입은 반영하지 않습니다.</p>
    <form onSubmit={submit} onChange={()=>setDirty(true)}><fieldset disabled={busy}>
      <legend>1. 투자 조건</legend>
      <div className={styles.grid}>
        <label>투자 가능 금액 (USD)<input name="capital" type="number" min="1" max="1000000000" step="0.01" defaultValue="10000" required/></label>
        <label>남길 현금 (USD)<input name="cash" type="number" min="0" max="1000000000" step="0.01" defaultValue="1000" required/></label>
        <label>기간 빠른 선택<select value={period} onChange={e => {
          const value = e.target.value;
          setPeriod(value);
          if (value) setDates(portfolioPeriod(Number(value)));
          setDirty(true);
        }}><option value="">직접 입력</option>{[[3,'3개월'],[6,'6개월'],[12,'1년'],[36,'3년'],[60,'5년']].map(([value,label]) => <option key={value} value={value}>{label}</option>)}</select><small>뉴욕 기준 어제까지 자동 설정 · 실제 일봉 기준일은 결과에 표시</small></label>
        <label>분석 시작일<input name="startDate" type="date" value={dates.startDate} onChange={e => {setDates({...dates,startDate:e.target.value});setPeriod('');}} required/></label>
        <label>분석 종료일<input name="endDate" type="date" value={dates.endDate} onChange={e => {setDates({...dates,endDate:e.target.value});setPeriod('');}} required/><small>뉴욕 오늘 이전, 60일~10년</small></label>
        <label>자산별 최대 비중 (%)<input name="maxWeightPct" type="number" min="0.1" max="100" step="0.1" defaultValue="40" required/><small>현금을 포함한 전체 자금 기준</small></label>
        <label>연환산 변동성 한도 (%)<input name="riskLimitPct" type="number" min="0.1" max="200" step="0.1" defaultValue="15" required/></label>
      </div>
      <label>후보 주식·ETF (2~10개)<div className={styles.add}><input aria-label="추가할 주식·ETF 티커" placeholder="예: AAPL, MSFT, TLT" value={ticker} onChange={e=>setTicker(e.target.value)} onKeyDown={e=>{if(e.key==='Enter'){e.preventDefault();add();}}}/><button type="button" onClick={add}>추가</button></div></label>
      <div className={styles.chips}>{tickers.map(t=><button type="button" key={t} aria-label={`${t} 제외`} onClick={()=>{setTickers(tickers.filter(x=>x!==t));setDirty(true);}}>{t} ×</button>)}<button type="button" onClick={()=>{setTickers(defaults);setDirty(true);}}>ETF 6종으로 초기화</button></div>
      <button className={styles.primary} type="submit">{busy?'계산 중…':'배분안 비교하기'}</button>
    </fieldset></form>
    {error && <p role="alert" className={styles.notice}>{error}</p>}
    {busy && <p role="status">일봉과 공통 수익률을 확인하고 배분안을 계산하고 있습니다.</p>}
    {!result && <p>조건 입력 후 개별 자산 분석, 과거 기간 수익률–변동성 경계, 종목별 배분 금액이 표시됩니다.</p>}
    {result && <>
      {dirty && <p role="status" className={styles.notice}>조건이 변경됐거나 새 계산에 실패했습니다. 아래는 이전 조건의 결과입니다. 다시 계산해 주세요.</p>}
      <h2>2. 데이터와 자산 분석</h2>
      <p role="status" className={styles.notice}>{result.message}</p>
      <p>공통 수익률 {result.commonObservations}개 · {result.commonStart??'—'} ~ {result.commonEnd??'—'} · 종목 간 불일치 제외 {result.excludedIntervals}구간</p>
      <div className={styles.scroll}><table><caption>개별 자산: 각 행의 관측 기간 기준 (최적화는 공통 구간 사용)</caption><thead><tr>{['자산','관측 기간','일봉 / 유효 수익률','기간 수익률','연환산 변동성','최대 낙폭','데이터 상태'].map(x=><th key={x}>{x}</th>)}</tr></thead><tbody>{result.assets.map(a=><tr key={a.ticker}><th>{a.ticker}</th><td>{a.startDate??'—'} ~ {a.endDate??'—'}</td><td>{a.observations} / {a.returnObservations}</td><td>{pct(a.totalReturnPct)}</td><td>{pct(a.volatilityPct)}</td><td>{pct(a.maxDrawdownPct)}</td><td>{a.issue??'개별 지표 계산 가능'}</td></tr>)}</tbody></table></div>
      {result.correlation && <details><summary>자산 간 상관관계</summary><div className={styles.scroll}><table><thead><tr><th>자산</th>{result.assets.map(a=><th key={a.ticker}>{a.ticker}</th>)}</tr></thead><tbody>{result.correlation.map((row,i)=><tr key={result.assets[i].ticker}><th>{result.assets[i].ticker}</th>{row.map((value,j)=><td key={j}>{number(value)}</td>)}</tr>)}</tbody></table></div></details>}
      {candidates.length>0 && <><h2>3. 위험–수익 조합 선택</h2><p>선: 제약 과거 기간 수익률–변동성 경계 · 회색 점: 개별 자산(최적화와 동일한 공통 구간) · 노란 점: 선택 조합. 수익률은 표시된 공통 기간의 시작·종료 가격 변화율입니다. 기간 초 비중으로 매수 후 보유한 사후 비교입니다.</p>
        <Frontier result={result} selected={selected} onSelect={setSelected}/>
        <label>비교할 배분안<select value={selected} onChange={e=>setSelected(e.target.value)}>{candidates.map(c=><option key={c.id} value={c.id}>{c.label} · 위험 {pct(c.volatilityPct)} · 기간 수익률 {pct(c.periodReturnPct)}</option>)}</select></label>
        {chosen && <><h2>4. {chosen.label} 배분</h2><div className={styles.stats}><p>과거 기간 수익률<strong>{pct(chosen.periodReturnPct)}</strong></p><p>연환산 변동성<strong>{pct(chosen.volatilityPct)}</strong></p><p>배분 후 현금<strong>${number(chosen.cashAmount)}</strong></p></div>
          <div className={styles.scroll}><table><thead><tr>{['자산','전체 자금 비중','배분액 (USD)','참고 종가 / 날짜','정수주 수량','참고 매수액 (USD)'].map(x=><th key={x}>{x}</th>)}</tr></thead><tbody>{chosen.allocations.map(a=><tr key={a.ticker}><th>{a.ticker}</th><td>{pct(a.weightPct)}</td><td>{number(a.amount)}</td><td>{number(a.price)} / {a.priceDate??'가격 없음'}</td><td>{number(a.quantity)}</td><td>{number(a.purchaseAmount)}</td></tr>)}<tr><th>현금</th><td>잔여</td><td>{number(chosen.cashAmount)}</td><td colSpan={3}>정수주 매수 후 현금: {number(chosen.integerShareCash)} USD</td></tr></tbody></table></div>
          <p>변동성은 선택 비중을 고정한 연환산 위험 모형입니다. 기간 초 매수 후 비중이 변하는 실제 보유 경로의 변동성과는 다릅니다.</p>
          <p>수량은 표에 표시한 최근 저장 일봉 종가 기준입니다. 현재 체결가가 아니며, 가격이 없거나 뉴욕 오늘 기준 4일보다 오래되면 수량을 계산하지 않습니다.</p>
        </>}
      </>}
      <details open><summary>계산 가정과 데이터 경고</summary><ul>{result.warnings.map(w=><li key={w}>{w}</li>)}</ul></details>
    </>}
  </section>;
}
