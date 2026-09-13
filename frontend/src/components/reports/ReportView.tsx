import Link from 'next/link';
import type { StockReportDetail } from '@/lib/report-types';
import styles from './report.module.css';

const num = (v: number | null | undefined, suffix = '') => v == null || !Number.isFinite(v) ? '—' : `${v.toLocaleString('ko-KR', { maximumFractionDigits: 2 })}${suffix}`;
const time = (v: string | null | undefined) => v ? `${new Date(v).toLocaleString('ko-KR', { timeZone: 'Asia/Seoul' })} KST` : '확인 불가';

export function ReportView({ report }: { report: StockReportDetail }) {
  const p = report.payload;
  if (p.schemaVersion !== 1 || p.calculationVersion !== 'stock-report-v1') return <main className={styles.page}>지원하지 않는 보고서 버전입니다.</main>;
  const price = p.price.data;
  const f = p.financials.data?.statements;
  const q = p.analysis.data;
  const history = (p.history.data ?? []).filter(c => Number.isFinite(c.close) && c.close > 0);
  const low = history.length ? Math.min(...history.map(c => c.close)) : 0;
  const high = history.length ? Math.max(...history.map(c => c.close)) : 0;
  const points = history.map((c, i) => `${10 + i / Math.max(1, history.length - 1) * 780},${160 - (c.close - low) / (high - low || 1) * 140}`).join(' ');
  const latest = f?.earningsAnalysis.at(-1);
  const growth = f?.growthAnalysis.at(-1);
  const cash = f?.cashFlowAnalysis.at(-1);
  const margin = f?.marginsAnalysis.at(-1);
  const source = `https://finance.yahoo.com/quote/${encodeURIComponent(report.ticker)}`;
  const rows: [string, string | undefined, string, string][] = [
    ['가격', price?.provider, time(price?.asOf), price ? `${price.source} · ${price.status}` : p.price.error ?? '자료 없음'],
    ['일별 종가', 'MarketBoard 저장 이력 · 공급처/조정 여부 미확인', history.length ? time(history.at(-1)?.ts) : '확인 불가', `${history.length}개 관측값`],
    ['연간 재무', 'Yahoo Finance / yfinance', time(p.financials.data?.fetchedAt), f ? '수집 시각 · 공시일 및 최신 공시 반영 여부 미확인' : p.financials.error ?? '자료 없음'],
    ['정량 분석', 'yfinance 가격 기반 · MarketBoard 계산', q?.asOfDate ?? '확인 불가', q ? `요청 관측 기간 ${q.lookbackDays} 거래일` : p.analysis.error ?? '자료 없음'],
  ];
  return <main className={styles.page}>
    <nav className={styles.toolbar}><Link href={`/symbols/${encodeURIComponent(report.ticker)}/reports`}>← 보고서 이력 / 새 보고서 생성</Link><button onClick={() => window.print()}>인쇄 / PDF 저장</button></nav>
    <header><p className={styles.eyebrow}>EVIDENCE-BASED RESEARCH · SNAPSHOT</p><h1>{report.ticker} 투자 보고서</h1>
      <p>{f?.name ?? report.ticker} · #{report.id} · 생성 {time(report.createdAt)}</p>
      <p className={styles.muted}>자료 취합 {time(p.startedAt)} ~ {time(p.capturedAt)} · 저장 당시 자료이며 실시간으로 바뀌지 않습니다.</p></header>
    <section className={styles.notice}><h2>투자 판단 요약</h2>
      <p>{latest ? `${latest.year} 회계연도 매출 ${num(latest.revenue)}, 순이익 ${num(latest.netIncome)}입니다. 금액은 공급처 원단위이며 통화 확인이 필요합니다.` : '연간 실적 자료가 부족해 기업 실적을 평가할 수 없습니다.'}</p>
      <p>{growth ? `${growth.year} 회계연도 매출 성장률은 ${num(growth.revenueGrowthPct, '%')}입니다. 직전 제공 회계연도 대비 변화이며 향후 성장률 예측이 아닙니다.` : '성장률 자료가 없습니다.'}</p>
      <p>{q ? `과거 가격의 연환산 변동성은 ${num(q.volatility?.annualizedPct, '%')}, 최대 낙폭은 ${num(q.drawdown?.maxDrawdownPct, '%')}입니다.` : '위험 분석 자료가 없습니다.'}</p>
      <p>가격 매력도 판단 보류: 검증된 이익 추정치와 적정 배수 가정이 없어 목표가·매수 등급을 산출하지 않았습니다.</p>
    </section>
    <section><h2>가격과 위험</h2><div className={styles.grid}>
      <article className={styles.card}><h3>저장된 기준 가격</h3><p className={styles.value}>{num(price?.price)}</p><p className={styles.muted}>통화 미확인 · {time(price?.asOf)}<br />{price?.source ?? '자료 없음'} · {price?.provider ?? '공급처 미확인'}</p></article>
      <article className={styles.card}><h3>연환산 변동성</h3><p className={styles.value}>{num(q?.volatility?.annualizedPct, '%')}</p><p className={styles.muted}>EWMA 일별 변동성 × √252 · 감쇠계수 0.94<br />분석 기준일 {q?.asOfDate ?? '확인 불가'}</p></article>
      <article className={styles.card}><h3>최대 낙폭</h3><p className={styles.value}>{num(q?.drawdown?.maxDrawdownPct, '%')}</p><p className={styles.muted}>분석 구간 내 고점 대비 최대 하락<br />미래 최대 손실을 의미하지 않습니다.</p></article>
    </div>
      <h3>저장된 일별 종가</h3>
      {history.length > 1 ? <><svg className={styles.chart} viewBox="0 0 800 180" role="img" aria-label={`${history.length}개 일별 종가 추이, 최저 ${num(low)}, 최고 ${num(high)}`}><polyline points={points} fill="none" stroke="currentColor" strokeWidth="2" /></svg><p className={styles.muted}>{time(history[0].ts)} ~ {time(history.at(-1)?.ts)} · 최저 {num(low)} / 최고 {num(high)} · 거래일 관측 순서, 주식분할 조정 여부 미확인</p></> : <p>{p.history.error ?? '차트를 표시할 가격 이력이 부족합니다.'}</p>}
      <p>베타 ({q?.benchmark?.ticker ?? 'SPY'}): {num(q?.benchmark?.beta)} · 일간 역사적 VaR 95%: {num(q?.risk?.['95']?.varPct, '%')} · CVaR 95%: {num(q?.risk?.['95']?.cvarPct, '%')}</p>
      <p className={styles.muted}>VaR는 과거 일별 수익률 분포의 손실 분위수, CVaR는 그 꼬리 구간의 평균 손실입니다. 미래 손실 한도를 보장하지 않습니다.</p>
    </section>
    <section><h2>실적과 현금흐름</h2><p className={styles.muted}>회계연도 기준 · 금액은 공급처 원단위, 통화 미확인 · —는 자료 없음이며 0이 아닙니다.</p>
      {f?.earningsAnalysis.length ? <div className={styles.table}><table><caption>제공된 연간 실적</caption><thead><tr><th>회계연도</th><th>매출</th><th>영업이익</th><th>순이익</th></tr></thead><tbody>{f.earningsAnalysis.map(r => <tr key={r.year}><th scope="row">{r.year}</th><td>{num(r.revenue)}</td><td>{num(r.operatingIncome)}</td><td>{num(r.netIncome)}</td></tr>)}</tbody></table></div> : <p>{p.financials.error ?? '연간 실적 자료 없음'}</p>}
      <div className={styles.grid}><article className={styles.card}><h3>잉여현금흐름 · {cash?.year ?? '—'}</h3><p className={styles.value}>{num(cash?.freeCashFlow)}</p><p className={styles.muted}>공급처 제공 FCF · 통화 미확인</p></article>
        <article className={styles.card}><h3>영업이익률 · {margin?.year ?? '—'}</h3><p className={styles.value}>{num(margin?.operatingMarginPct, '%')}</p><p className={styles.muted}>영업이익 ÷ 매출 × 100</p></article>
        <article className={styles.card}><h3>순이익률 · {margin?.year ?? '—'}</h3><p className={styles.value}>{num(margin?.netMarginPct, '%')}</p><p className={styles.muted}>순이익 ÷ 매출 × 100</p></article></div>
    </section>
    <section><h2>다음 판단 전에 확인할 사항</h2><ul><li>회사 공시에서 최근 분기 성장률과 가이던스가 연간 성장 흐름을 지지하는지 확인하세요.</li><li>영업이익 증가가 현금흐름 증가로 이어지는지, 수익성 악화가 지속되는지 확인하세요.</li><li>현재 가격을 정당화할 예상 이익과 적정 PER의 근거를 확보한 뒤 가치평가를 진행하세요.</li></ul></section>
    <section className={styles.notice}><h2>데이터 상태와 한계</h2><ul>{p.checks.map(check => <li key={check}>{check}</li>)}</ul></section>
    <section><h2>출처와 관측 기준</h2><div className={styles.table}><table><thead><tr><th>자료</th><th>공급·계산</th><th>기준일 / 수집 시각</th><th>상태</th></tr></thead><tbody>{rows.map(([name, provider, date, status]) => <tr key={name}><th scope="row">{name}</th><td>{provider ?? '확인 불가'}</td><td>{date}</td><td>{status}</td></tr>)}</tbody></table></div>
      <p><a href={`${source}/financials/`} target="_blank" rel="noopener noreferrer">Yahoo Finance 재무 페이지</a> · <a href={`${source}/history/`} target="_blank" rel="noopener noreferrer">Yahoo Finance 가격 이력</a></p>
      <p className={styles.muted}>링크는 현재 공급처 페이지입니다. 생성 당시 공시 원문을 보존한 인용이 아니며 원문 대조는 미완료입니다. 가격 공급처는 위 표의 실제 메타데이터를 따릅니다.</p></section>
    <footer className={styles.muted}>보고서 버전 {p.calculationVersion} · 수치 기반 요약 · AI 생성 문구 없음 · 예상 수익률·목표가 없음</footer>
  </main>;
}
