'use client';

import {
  ArrowPathIcon, BanknotesIcon, CalendarDaysIcon, ChartBarIcon, CheckCircleIcon,
  DocumentTextIcon, InformationCircleIcon, MagnifyingGlassIcon, PlayIcon,
  ScaleIcon, XMarkIcon,
} from '@heroicons/react/24/outline';
import type { BacktestStrategyType, RebalanceFrequency } from '@/lib/types';
import styles from './strategy-setup-card.module.css';

export interface BacktestDateRange { start: string; end: string }

const STRATEGIES: { value: BacktestStrategyType; title: string; description: string; icon: typeof ChartBarIcon }[] = [
  { value: 'BUY_AND_HOLD', title: '매수 후 보유', description: '동일 비중으로 매수 후 보유합니다.', icon: BanknotesIcon },
  { value: 'SMA_CROSSOVER', title: 'SMA 크로스오버', description: '이동평균선의 골든/데드 크로스 신호로 매매합니다.', icon: ChartBarIcon },
  { value: 'PERIODIC_REBALANCE', title: '정기 리밸런싱', description: '일정 주기로 포트폴리오를 리밸런싱합니다.', icon: ArrowPathIcon },
  { value: 'VOLATILITY_TARGET', title: '변동성 타기팅', description: '목표 변동성에 맞춰 자산 비중을 조절합니다.', icon: ScaleIcon },
];

function NumberField({ id, label, helper, value, onChange, min = 0, step = 1, prefix }: {
  id: string; label: string; helper: string; value: number | null; onChange: (value: number | null) => void;
  min?: number; step?: number; prefix?: string;
}) {
  return <label className={styles.field} htmlFor={id}><span>{label}</span><div className={styles.inputShell}>{prefix && <b>{prefix}</b>}<input id={id} type="number" value={value ?? ''} min={min} step={step} onChange={(e) => onChange(e.target.value === '' ? null : Number(e.target.value))} /></div><small>{helper}</small></label>;
}

export function StrategySetupCard(props: {
  name: string; setName: (value: string) => void;
  tickers: string[]; newTicker: string; setNewTicker: (value: string) => void; addTicker: () => void; removeTicker: (ticker: string) => void; maxTickers: number;
  dateRange: BacktestDateRange | null; setDateRange: (value: BacktestDateRange | null) => void; today: string;
  initialCapital: number | null; setInitialCapital: (value: number | null) => void;
  riskFreeRatePct: number | null; setRiskFreeRatePct: (value: number | null) => void;
  strategyType: BacktestStrategyType; setStrategyType: (value: BacktestStrategyType) => void;
  smaShortWindow: number | null; setSmaShortWindow: (value: number | null) => void;
  smaLongWindow: number | null; setSmaLongWindow: (value: number | null) => void;
  rebalanceFrequency: RebalanceFrequency; setRebalanceFrequency: (value: RebalanceFrequency) => void;
  targetVolatilityPct: number | null; setTargetVolatilityPct: (value: number | null) => void;
  vixThreshold: number | null; setVixThreshold: (value: number | null) => void;
  isSmaRangeValid: boolean; isVolTargetValid: boolean; isStrategyParamsValid: boolean;
  error: string | null; isRunning: boolean; onRun: () => void;
}) {
  const updateDate = (key: keyof BacktestDateRange, value: string) => props.setDateRange({ start: props.dateRange?.start ?? '', end: props.dateRange?.end ?? '', [key]: value });

  return <section className={styles.card}>
    <header><div className={styles.title}><span><DocumentTextIcon /></span><div><h2>전략 설정</h2><p>투자 전략을 설정하고 과거 데이터를 기반으로 성과를 검증해보세요.</p></div></div><div className={styles.limit}><InformationCircleIcon />최대 10개의 종목을 선택하여 백테스트할 수 있습니다.</div></header>

    <div className={styles.primaryRow}>
      <label className={styles.field} htmlFor="strategy-name"><span>이름</span><div className={styles.inputShell}><DocumentTextIcon /><input id="strategy-name" value={props.name} onChange={(e) => props.setName(e.target.value)} /></div><small>저장할 전략의 이름을 입력하세요.</small></label>
      <div className={styles.field}><span>종목 (최대 {props.maxTickers}개)</span><div className={styles.symbolBox}>{props.tickers.map((ticker) => <span className={styles.chip} key={ticker}>{ticker}<button type="button" onClick={() => props.removeTicker(ticker)} aria-label={`${ticker} 제거`}><XMarkIcon /></button></span>)}<div className={styles.symbolInput}><MagnifyingGlassIcon /><input aria-label="추가할 종목" placeholder="종목 코드나 이름을 검색하여 추가하세요. (예: AAPL)" value={props.newTicker} onChange={(e) => props.setNewTicker(e.target.value.toUpperCase())} onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); props.addTicker(); } }} /></div><button type="button" className={styles.addButton} disabled={!props.newTicker.trim() || props.tickers.length >= props.maxTickers} onClick={props.addTicker}>추가</button></div><small>최대 {props.maxTickers}개의 종목을 추가할 수 있습니다.</small></div>
    </div>

    <div className={styles.basicGrid}>
      <div className={styles.field}><span>백테스트 기간</span><div className={styles.dateRange}><CalendarDaysIcon /><input aria-label="시작일" type="date" max={props.dateRange?.end ?? props.today} value={props.dateRange?.start ?? ''} onChange={(e) => updateDate('start', e.target.value)} /><i>—</i><input aria-label="종료일" type="date" min={props.dateRange?.start} max={props.today} value={props.dateRange?.end ?? ''} onChange={(e) => updateDate('end', e.target.value)} /></div><small>분석할 기간을 선택하세요.</small></div>
      <NumberField id="initial-capital" label="초기 자본" helper="백테스트를 시작할 초기 자본을 입력하세요." value={props.initialCapital} onChange={props.setInitialCapital} step={100000} prefix="$" />
      <NumberField id="risk-free-rate" label="무위험 이자율" helper="연간 무위험 이자율을 입력하세요. (예: 3)" value={props.riskFreeRatePct} onChange={props.setRiskFreeRatePct} step={0.1} prefix="%" />
    </div>

    <div className={styles.strategySection}><div><h3>전략 유형</h3><p>백테스트에 적용할 투자 방식을 선택하세요.</p></div><div className={styles.strategyGrid}>{STRATEGIES.map((strategy) => { const SelectedIcon = strategy.icon; const selected = props.strategyType === strategy.value; return <button type="button" key={strategy.value} className={selected ? styles.selectedStrategy : ''} onClick={() => props.setStrategyType(strategy.value)} aria-pressed={selected}><SelectedIcon /><strong>{strategy.title}</strong><span>{strategy.description}</span>{selected && <CheckCircleIcon className={styles.check} />}</button>; })}</div></div>

    {props.strategyType !== 'BUY_AND_HOLD' && <div className={styles.parameterPanel}><h3>전략 파라미터</h3>{props.strategyType === 'SMA_CROSSOVER' && <><div className={styles.parameterGrid}><NumberField id="sma-short" label="단기 이동평균" helper="단기 추세 기간" value={props.smaShortWindow} onChange={props.setSmaShortWindow} min={1} /><NumberField id="sma-long" label="장기 이동평균" helper="장기 추세 기간" value={props.smaLongWindow} onChange={props.setSmaLongWindow} min={1} /></div>{!props.isSmaRangeValid && <p className={styles.error}>단기 이동평균 일수는 장기 이동평균 일수보다 작아야 합니다.</p>}</>}{props.strategyType === 'PERIODIC_REBALANCE' && <label className={styles.field} htmlFor="rebalance-frequency"><span>리밸런싱 주기</span><select id="rebalance-frequency" value={props.rebalanceFrequency} onChange={(e) => props.setRebalanceFrequency(e.target.value as RebalanceFrequency)}><option value="MONTHLY">월간</option><option value="QUARTERLY">분기</option><option value="YEARLY">연간</option></select><small>선택한 주기마다 동일 비중으로 다시 맞춥니다.</small></label>}{props.strategyType === 'VOLATILITY_TARGET' && <><div className={styles.parameterGrid}><NumberField id="target-volatility" label="목표 변동성 (%)" helper="연율화 기준" value={props.targetVolatilityPct} onChange={props.setTargetVolatilityPct} min={0.1} /><NumberField id="vix-threshold" label="VIX 비상탈출 임계값" helper="현금 전환 기준" value={props.vixThreshold} onChange={props.setVixThreshold} min={0.1} /></div>{!props.isVolTargetValid && <p className={styles.error}>목표 변동성과 VIX 임계값은 0보다 커야 합니다.</p>}</>}</div>}

    {props.error && <p className={styles.errorBox}>{props.error}</p>}
    <button type="button" className={styles.runButton} disabled={props.isRunning || props.tickers.length === 0 || !props.dateRange || !props.isStrategyParamsValid} onClick={props.onRun}>{props.isRunning ? <><span className={styles.spinner} />백테스트 실행 중...</> : <><PlayIcon />백테스트 실행</>}</button>
    <p className={styles.note}><InformationCircleIcon />벤치마크(SPY)와 비교합니다. 파라미터를 바꾸고 다시 실행하면 아래 결과와 차트가 갱신됩니다.</p>
  </section>;
}
