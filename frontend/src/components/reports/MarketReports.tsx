'use client';

import { useCallback, useState } from 'react';
import Link from 'next/link';
import { useResource } from '@/lib/use-resource';
import type { Fetcher } from '@/lib/api';
import { getMarketReport, getMarketReports, type ReportIndex } from '@/lib/market-report-api';
import { MarketReportLatest } from './MarketReportLatest';
import styles from './market-report.module.css';

const timestamp = (value: string | null | undefined) => value ? new Date(value).toLocaleString('ko-KR', { timeZone: 'Asia/Seoul', hour12: false }) + ' KST' : '미제공';
const number = (value: number | null | undefined) => value != null && Number.isFinite(value) ? value.toLocaleString('ko-KR', { maximumFractionDigits: 2 }) : '미제공';
const percent = (value: number | null) => value != null && Number.isFinite(value) ? `${value > 0 ? '+' : ''}${value.toFixed(2)}%` : '미제공';

export function MarketReportList() {
  const [before, setBefore] = useState('');
  const [input, setInput] = useState('');
  const load = useCallback((fetcher: Fetcher) => getMarketReports(fetcher, before), [before]);
  const resource = useResource(load);
  function previous() {
    if (resource.status !== 'success' || !resource.data.length) return;
    const date = new Date(`${resource.data.at(-1)!.tradingDate}T00:00:00Z`);
    date.setUTCDate(date.getUTCDate() - 1);
    const value = date.toISOString().slice(0, 10);
    setBefore(value); setInput(value);
  }
  return <main className={styles.page}>
    <p className={styles.eyebrow}>DAILY MARKET REPORT</p><h1>시황 보고서</h1>
    <p className={styles.lead}>미국장 마감 자료를 한 번 저장한 보고서입니다. 날짜를 골라 당시 수치와 근거를 다시 확인하세요.</p>
    <section className={styles.notice} aria-label="최근 발행 현황"><MarketReportLatest /></section>
    <form className={styles.toolbar} onSubmit={event => { event.preventDefault(); setBefore(input); }}>
      <label>이 거래일까지 조회 <input type="date" value={input} onChange={event => setInput(event.target.value)} /></label>
      <button type="submit">조회</button><button type="button" onClick={() => { setInput(''); setBefore(''); }}>최신 목록</button>
    </form>
    {resource.status === 'loading' && <p role="status">보고서 목록을 불러오는 중…</p>}
    {resource.status === 'error' && <p role="alert">목록을 조회하지 못했습니다. <button onClick={resource.retry}>다시 조회</button></p>}
    {resource.status === 'success' && <>
      {!resource.data.length && <p>이 기간에 저장된 보고서가 없습니다.</p>}
      <ul className={styles.records}>{resource.data.map(report => <li key={report.id}>
        <div><Link href={`/market-reports/${report.id}`}>{report.tradingDate} 미국장 마감 보고서</Link>
          <p className={styles.muted}>발행 {timestamp(report.publishedAt)} · 버전 {report.version}</p></div>
        <span className={styles.badge}>{report.status === 'PARTIAL' ? '일부 자료 미확인' : '완료'}</span>
      </li>)}</ul>
      {resource.data.length === 30 && <button onClick={previous}>이전 거래일 보고서</button>}
    </>}
  </main>;
}

function IndexCards({ rows }: { rows: ReportIndex[] }) {
  return <div className={styles.grid}>{rows.map(row => <article key={row.slug} className={styles.card}>
    <h3>{row.name}</h3><p className={styles.value}>{number(row.close)} <small>{row.unit}</small></p>
    <dl className={styles.metrics}><div><dt>1일</dt><dd>{percent(row.changePct1d)}</dd></div><div><dt>1주</dt><dd>{percent(row.changePct1w)}</dd></div><div><dt>1개월</dt><dd>{percent(row.changePct1m)}</dd></div></dl>
    <p className={styles.muted}>기준 {row.evidence.tradingDate} · {row.evidence.source}<br />수집 {timestamp(row.evidence.fetchedAt)}</p>
  </article>)}</div>;
}

export function MarketReportReader({ id }: { id: number }) {
  const load = useCallback((fetcher: Fetcher) => getMarketReport(fetcher, id), [id]);
  const resource = useResource(load);
  if (resource.status !== 'success') return <main className={styles.page}><Link href="/market-reports">← 시황 보고서 목록</Link>
    <p role={resource.status === 'error' ? 'alert' : 'status'}>{resource.status === 'error' ? '보고서가 없거나 조회하지 못했습니다.' : '보고서를 불러오는 중…'}</p>
    {resource.status === 'error' && <button onClick={resource.retry}>다시 조회</button>}</main>;
  const { report, payload: p } = resource.data;
  return <main className={styles.page}>
    <Link href="/market-reports">← 시황 보고서 목록</Link><p className={styles.eyebrow}>US MARKET CLOSE</p>
    <h1>{report.tradingDate} 미국장 시황</h1>
    <p className={styles.lead}>발행 {timestamp(report.publishedAt)} · 버전 {report.version} <span className={styles.badge}>{report.status === 'PARTIAL' ? '일부 자료 미확인' : '완료'}</span></p>
    <p className={styles.muted}>시장 마감 {timestamp(p.session.closedAt)} · 보고서 자료 확보 {timestamp(p.capturedAt)}</p>
    {!!p.checks.length && <section className={styles.notice} aria-labelledby="checks-title"><h2 id="checks-title">읽기 전에 확인하세요</h2><ul>{p.checks.map(check => <li key={check}>{check}</li>)}</ul></section>}
    <section aria-labelledby="summary-title"><h2 id="summary-title">핵심 요약</h2><ul className={styles.highlights}>{p.highlights.map(item => <li key={item.text}><a href={`#${item.section}`}>{item.text}</a></li>)}</ul></section>
    <section id="indices"><h2>주요 지수</h2><p className={styles.muted}>미국 주식장 완료 거래일 기준 · 수익률은 1·5·21거래일 전 종가 대비입니다.</p><IndexCards rows={p.indices} /></section>
    <section id="sectors"><h2>섹터 비교</h2>{p.sectors.length ? <div className={styles.table} role="region" aria-label="섹터 기간별 수익률" tabIndex={0}><table>
      <thead><tr><th scope="col">섹터</th><th scope="col">1일</th><th scope="col">1주</th><th scope="col">1개월</th><th scope="col">기준 거래일 · 출처</th></tr></thead>
      <tbody>{p.sectors.map(row => <tr key={row.slug}><th scope="row">{row.name}</th><td>{percent(row.changePct1d)}</td><td>{percent(row.changePct1w)}</td><td>{percent(row.changePct1m)}</td><td>{row.tradingDate}<br />{row.source}<br />수집 {timestamp(row.fetchedAt)}</td></tr>)}</tbody>
    </table></div> : <p>동일 거래일의 섹터 자료가 없습니다.</p>}</section>
    <section id="breadth"><h2>시장 폭</h2>{p.breadth ? <>
      <dl className={styles.metrics}><div><dt>상승</dt><dd>{p.breadth.advancingCount}</dd></div><div><dt>하락</dt><dd>{p.breadth.decliningCount}</dd></div><div><dt>보합</dt><dd>{p.breadth.unchangedCount}</dd></div></dl>
      <p>집계 {p.breadth.universeSize} / 대상 {p.breadth.eligibleCount}종목 · 신고가 {p.breadth.new52wHighCount} · 신저가 {p.breadth.new52wLowCount}</p>
      <p className={styles.muted}>기준 {p.breadth.tradingDate} · 계산 {timestamp(p.breadth.computedAt)}<br />출처: MarketBoard 저장 일봉 집계 · 원천 공급자·수집 시각 미확인. 신고가·신저가는 확보된 최대 252개 종가 범위입니다.</p>
    </> : <p>동일 거래일의 시장 폭 자료가 없습니다.</p>}</section>
    <section id="macro"><h2>거시 지표</h2><p className={styles.muted}>자산별 거래 시간이 다르며 미국장 마감 확정 여부는 미확인입니다. 기준일이 다른 자료는 당시 수집된 참고값입니다.</p>
      {p.macro.length ? <IndexCards rows={p.macro} /> : <p>확보된 거시 지표가 없습니다.</p>}</section>
    <section id="sentiment"><h2>투자 심리 · 참고 자료</h2><div className={styles.grid}>
      <article className={styles.card}><h3>공포·탐욕 지수</h3><p className={styles.value}>{number(p.fearGreed?.score)}</p><p>{p.fearGreed?.rating}</p><p className={styles.muted}>출처 CNN · 공급자 시각 {timestamp(p.fearGreed?.timestamp)} · 수집 시각 미제공</p></article>
      <article className={styles.card}><h3>SPY Put/Call</h3><p className={styles.value}>{number(p.putCall?.putCallRatio)}</p><p className={styles.muted}>출처 Yahoo Finance 옵션 집계 · 계산 이력 {timestamp(p.putCall?.history?.at(-1)?.timestamp)} · 원천 관측 시각 미제공</p></article>
    </div></section>
    <section id="news"><h2>당시 주요 뉴스</h2><p className={styles.muted}>직전 거래일 시작부터 대상 거래일 마감까지 발행된 뉴스 중 중복을 제외했습니다.</p>
      {p.news.length ? <ul className={styles.records}>{p.news.map(item => <li key={item.url}><div><a href={item.url} target="_blank" rel="noopener noreferrer">{item.headline} ↗</a><p className={styles.muted}>{item.source || '출처 미제공'} · {timestamp(item.publishedAt)}</p></div></li>)}</ul> : <p>대상 기간에 확보된 뉴스가 없습니다.</p>}</section>
    <section className={styles.notice}><h2>정보 이용 안내</h2><p>이 보고서는 생성 당시 자료를 보존한 참고 정보이며 투자 권유가 아닙니다. 뉴스와 가격 변화의 인과관계를 단정하지 않습니다. 가격은 yfinance 자동 조정 설정을 사용하며 공급자의 사후 정정 가능성이 있습니다.</p>
      <p className={styles.muted}>달력 {p.session.calendar} {p.session.calendarVersion} · 계산 {p.calculationVersion} · 자료 형식 {p.schemaVersion}</p></section>
  </main>;
}
