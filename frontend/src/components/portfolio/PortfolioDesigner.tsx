'use client';

import { useState, type FormEvent } from 'react';
import { useAuth } from '@/lib/auth-context';
import { portfolioPeriod, portfolioValidationPeriods } from '@/lib/portfolio-period';
import styles from './portfolio-designer.module.css';
import { PortfolioPerformance, type Performance, type PerformanceComparison } from './PortfolioPerformance';
import { PortfolioValidation, type ValidationResult } from './PortfolioValidation';

type Asset = { ticker: string; observations: number; returnObservations: number; startDate: string | null; endDate: string | null;
  totalReturnPct: number | null; volatilityPct: number | null; maxDrawdownPct: number | null; issue: string | null;
  commonPeriodReturnPct: number | null; commonVolatilityPct: number | null };
type Allocation = { ticker: string; weightPct: number; amount: number; price: number | null; priceDate: string | null; quantity: number | null; purchaseAmount: number | null };
type Candidate = { id: string; label: string; periodReturnPct: number; volatilityPct: number;
  performance?: Performance;
  allocations: Allocation[]; cashAmount: number; integerShareCash: number | null };
type Result = { status: string; message: string; assets: Asset[]; candidates: Candidate[]; frontier: Candidate[];
  performance?: PerformanceComparison | null;
  validation?: ValidationResult | null;
  commonObservations: number; excludedIntervals: number; commonStart: string | null; commonEnd: string | null;
  correlation: (number | null)[][] | null; warnings: string[] };
const defaults = ['SPY', 'QQQ', 'EFA', 'EEM', 'IEF', 'GLD'];
const number = (v: number | null) => v == null ? '—' : v.toLocaleString('ko-KR', { maximumFractionDigits: 2 });
const pct = (v: number | null) => v == null ? '—' : `${number(v)}%`;

function Frontier({ result, selected, onSelect }: { result: Result; selected: string; onSelect: (id: string) => void }) {
  // Named comparisons remain above frontier endpoints when coordinates coincide.
  const points = [...result.frontier, ...result.candidates];
  const chosen = points.find(p => p.id === selected);
  const assets = result.assets.filter(a => a.commonPeriodReturnPct != null && a.commonVolatilityPct != null)
    .map(a => ({ticker:a.ticker, periodReturnPct:a.commonPeriodReturnPct, volatilityPct:a.commonVolatilityPct}));
  const values = [...points, ...assets];
  const maxX = Math.ceil(Math.max(1, ...values.map(p => p.volatilityPct ?? 0))*1.1/5)*5;
  const low = Math.min(0, ...values.map(p => p.periodReturnPct ?? 0));
  const high = Math.max(1, ...values.map(p => p.periodReturnPct ?? 0));
  const step = Math.pow(10, Math.floor(Math.log10((high-low)/4)));
  const tick = Math.ceil((high-low)/4/step)*step;
  const lowY = Math.floor(low/tick)*tick;
  const highY = Math.ceil(high/tick)*tick+tick;
  const spanY = highY-lowY;
  const x = (v: number) => 65+v/maxX*690;
  const y = (v: number) => 325-(v-lowY)/spanY*270;
  return <div className={styles.chartPanel}>
    <div className={styles.chartHeading}><strong>과거 성과로 비교하는 배분 조합</strong><span>{result.commonStart} ~ {result.commonEnd}</span></div>
    <div className={styles.legend}><span><i className={styles.frontierKey}/>투자선</span><span><i className={styles.assetKey}/>개별 자산</span><span><i className={styles.selectedKey}/>선택한 조합</span></div>
    <div className={styles.comparisons}>{result.candidates.map(p => <button type="button" key={p.id} aria-pressed={p.id===selected} onClick={()=>onSelect(p.id)}><strong>{p.label}</strong><span>수익률 {pct(p.periodReturnPct)}</span><small>변동성 {pct(p.volatilityPct)}</small></button>)}</div>
    <div className={styles.chartScroll}><svg className={styles.chart} viewBox="0 0 800 385" role="group" aria-label="과거 기간 수익률–변동성 경계: 가로축 연환산 변동성, 세로축 과거 기간 수익률">
    {Array.from({length:Math.round(spanY/tick)+1},(_,i)=>lowY+tick*i).map(v => <g key={v}>
      <line x1="65" x2="755" y1={y(v)} y2={y(v)} stroke="var(--mb-border)" strokeDasharray={v===0?undefined:'3 6'} strokeWidth={v===0?2:1}/>
      <text x="53" y={y(v)+5} textAnchor="end">{number(v)}%</text>
    </g>)}
    {[0,1,2,3,4,5].map(i=><text key={i} x={x(maxX*i/5)} y="349" textAnchor="middle">{number(maxX*i/5)}%</text>)}
    <text x="400" y="375" textAnchor="middle">연환산 변동성 (%) →</text>
    <text x="65" y="20">과거 기간 수익률 (%) ↑</text>
    <polyline points={result.frontier.map(p => `${x(p.volatilityPct)},${y(p.periodReturnPct)}`).join(' ')} fill="none" stroke="var(--mb-accent)" strokeWidth="3" />
    {assets.map(a => <g key={a.ticker}><circle cx={x(a.volatilityPct!)} cy={y(a.periodReturnPct!)} r="4" fill="var(--mb-text-muted)"/><text x={x(a.volatilityPct!)+7} y={y(a.periodReturnPct!)-7}>{a.ticker}</text></g>)}
    {chosen && <g className={styles.selectionGuide} pointerEvents="none"><path d={`M65 ${y(chosen.periodReturnPct)} H${x(chosen.volatilityPct)} V325`} fill="none" stroke="#f4b85e" strokeDasharray="4 5"/><circle cx={x(chosen.volatilityPct)} cy={y(chosen.periodReturnPct)} r="15" fill="#f4b85e" fillOpacity=".16"/></g>}
    {points.map(p => <circle key={p.id} cx={x(p.volatilityPct)} cy={y(p.periodReturnPct)} r={p.id===selected?8:result.candidates.includes(p)?7:4}
      fill={p.id===selected?'#f4b85e':'var(--mb-accent)'} stroke="var(--mb-surface)" strokeWidth="2"
      role="button" tabIndex={0} aria-pressed={p.id===selected} aria-label={`${p.label}, 변동성 ${pct(p.volatilityPct)}, 기간 수익률 ${pct(p.periodReturnPct)}`}
      onClick={() => onSelect(p.id)} onKeyDown={e => { if(e.key==='Enter'||e.key===' ') {e.preventDefault();onSelect(p.id);} }}>
      <title>{p.label}: 변동성 {pct(p.volatilityPct)} / 기간 수익률 {pct(p.periodReturnPct)}</title>
    </circle>)}
    {chosen && <circle pointerEvents="none" cx={x(chosen.volatilityPct)} cy={y(chosen.periodReturnPct)} r="8" fill="#f4b85e" stroke="var(--mb-surface)" strokeWidth="2"/>}
  </svg></div>
    {chosen && <div className={styles.selectionSummary} role="status"><strong>{chosen.label}</strong><span>기간 수익률 <b>{pct(chosen.periodReturnPct)}</b></span><span>연환산 변동성 <b>{pct(chosen.volatilityPct)}</b></span></div>}
    <p className={styles.chartHint}>점을 선택하거나 아래 목록에서 배분안을 비교하세요. 왼쪽일수록 변동성이 낮고, 위쪽일수록 기간 수익률이 높습니다.</p>
  </div>;
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
  const [validatePeriod, setValidatePeriod] = useState(false);
  const [validationDates, setValidationDates] = useState({ startDate: '', endDate: '' });
  const [validationRequested, setValidationRequested] = useState(false);
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
    if (validatePeriod && (validationDates.startDate <= dates.endDate || validationDates.endDate <= validationDates.startDate)) {
      setError('검증 시작일은 비중 산출 종료일 이후, 검증 종료일은 검증 시작일 이후여야 합니다.'); setDirty(true); return;
    }
    setBusy(true);setError(null);
    try {
      const data = await authFetch<Result>('/api/portfolio/design', {method:'POST', signal:AbortSignal.timeout(90_000), body:{
        tickers, startDate:form.get('startDate'), endDate:form.get('endDate'),
        capital:Number(form.get('capital')),cash:Number(form.get('cash')),maxWeightPct:Number(form.get('maxWeightPct')),
        riskLimitPct:Number(form.get('riskLimitPct')),
        validationStartDate:validatePeriod ? validationDates.startDate : null,
        validationEndDate:validatePeriod ? validationDates.endDate : null,
      }});
      setResult(data);setValidationRequested(validatePeriod);setSelected(data.candidates.find(c=>c.id==='risk-limit')?.id ?? data.candidates[0]?.id ?? '');setDirty(false);
    } catch(e) {setError(e instanceof Error?e.message:'계산에 실패했습니다.');setDirty(true);}
    finally {setBusy(false);}
  }
  const candidates = result ? [...result.candidates,...result.frontier] : [];
  const chosen = candidates.find(c=>c.id===selected);
  return <section className={styles.designer} aria-label="포트폴리오 설계">
    <p className={styles.lead}>투자금과 후보 자산을 정하고, 위험 대비 수익과 배분 금액을 비교하세요. USD 표시 주식·ETF 기준이며 환전·공매도·차입은 반영하지 않습니다.</p>
    <form onSubmit={submit} onChange={()=>setDirty(true)}><fieldset disabled={busy}>
      <legend>1. 투자 조건</legend>
      <p>분석 기간의 데이터로 비중을 산출합니다. 이후 기간 검증을 켜면 산출 종료일 뒤의 가격으로 성과를 따로 비교합니다.</p>
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
      <div className={styles.notice}>
        <label>이후 기간 검증<select value={validatePeriod?'on':'off'} onChange={e=>{setValidatePeriod(e.target.value==='on');setDirty(true);}}><option value="off">사용 안 함</option><option value="on">비중 산출 이후 기간으로 검증</option></select></label>
        <button type="button" onClick={()=>{const p=portfolioValidationPeriods();setDates(p.training);setValidationDates(p.validation);setValidatePeriod(true);setPeriod('');setDirty(true);}}>1년 산출 + 이후 3개월 자동 설정</button>
        {validatePeriod && <div className={styles.grid}>
          <label>검증 시작일<input type="date" required value={validationDates.startDate} onChange={e=>setValidationDates({...validationDates,startDate:e.target.value})}/></label>
          <label>검증 종료일<input type="date" required value={validationDates.endDate} onChange={e=>setValidationDates({...validationDates,endDate:e.target.value})}/></label>
        </div>}
        <small>검증 기간은 분석 종료일 이후, 뉴욕 오늘 이전이어야 합니다. 검증 첫 공통 관측일 종가로 매수 후 보유하며 비중을 다시 최적화하지 않습니다.</small>
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
      {candidates.length>0 && <><h2>3. 위험–수익 조합 선택</h2><p>수익률은 공통 기간의 실제 가격 변화율입니다. 기간 초 비중으로 매수 후 보유한 사후 비교이며, 미래 수익률 예측이 아닙니다.</p>
        <Frontier result={result} selected={selected} onSelect={setSelected}/>
        <label>비교할 배분안<select value={selected} onChange={e=>setSelected(e.target.value)}>{candidates.map(c=><option key={c.id} value={c.id}>{c.label} · 위험 {pct(c.volatilityPct)} · 기간 수익률 {pct(c.periodReturnPct)}</option>)}</select></label>
        {chosen && <><h2>4. {chosen.label} 배분</h2><div className={styles.stats}><p>과거 기간 수익률<strong>{pct(chosen.periodReturnPct)}</strong></p><p>연환산 변동성<strong>{pct(chosen.volatilityPct)}</strong></p><p>배분 후 현금<strong>${number(chosen.cashAmount)}</strong></p></div>
          <div className={styles.scroll}><table><thead><tr>{['자산','전체 자금 비중','배분액 (USD)','참고 종가 / 날짜','정수주 수량','참고 매수액 (USD)'].map(x=><th key={x}>{x}</th>)}</tr></thead><tbody>{chosen.allocations.map(a=><tr key={a.ticker}><th>{a.ticker}</th><td>{pct(a.weightPct)}</td><td>{number(a.amount)}</td><td>{number(a.price)} / {a.priceDate??'가격 없음'}</td><td>{number(a.quantity)}</td><td>{number(a.purchaseAmount)}</td></tr>)}<tr><th>현금</th><td>잔여</td><td>{number(chosen.cashAmount)}</td><td colSpan={3}>정수주 매수 후 현금: {number(chosen.integerShareCash)} USD</td></tr></tbody></table></div>
          <p>변동성은 선택 비중을 고정한 연환산 위험 모형입니다. 기간 초 매수 후 비중이 변하는 실제 보유 경로의 변동성과는 다릅니다.</p>
          <p>수량은 표에 표시한 최근 저장 일봉 종가 기준입니다. 현재 체결가가 아니며, 가격이 없거나 뉴욕 오늘 기준 4일보다 오래되면 수량을 계산하지 않습니다.</p>
        </>}
      </>}
      {chosen && (result.performance ? <PortfolioPerformance key={`${result.commonStart}-${result.commonEnd}-${result.performance.capital}`} comparison={result.performance} chosen={chosen} equal={result.candidates.find(c=>c.id==='equal')?.performance}/> : <p role="status">{chosen.id==='cash' ? '전액 현금 조건으로 자산 추이 비교를 생략합니다.' : '자산 추이 데이터가 없습니다. 서버 업데이트 후 다시 계산해 주세요.'}</p>)}
      {result.validation && <PortfolioValidation result={result.validation} selected={selected}/>}
      {validationRequested && !result.validation && <p role="status" className={styles.notice}>서버에서 기간 분리 검증 결과를 받지 못했습니다. 서버 업데이트 상태를 확인하고 다시 계산해 주세요.</p>}
      <details open><summary>계산 가정과 데이터 경고</summary><ul>{result.warnings.map(w=><li key={w}>{w}</li>)}</ul></details>
    </>}
  </section>;
}
