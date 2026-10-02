import { ChevronLeftIcon, ChevronRightIcon, ListBulletIcon } from '@heroicons/react/24/outline';
import type { MomentumPeriod, ScreenerSearchResponse, ScreenerSortField, ScreenerSnapshotItem } from '@/lib/screener-types';
import { formatPercent, formatUsd, momentumFor, PERIOD_OPTIONS } from './screener-format';
import styles from './screener.module.css';

const SORT_OPTIONS: { value: ScreenerSortField; label: string }[] = [
  { value: 'MOMENTUM_3M', label: '3개월 모멘텀' }, { value: 'MOMENTUM_6M', label: '6개월 모멘텀' },
  { value: 'MOMENTUM_12M', label: '12개월 모멘텀' }, { value: 'RSI_14', label: 'RSI' },

  { value: 'EMA_20', label: 'EMA 20' }, { value: 'MACD_HISTOGRAM', label: 'MACD 히스토그램' },

  { value: 'BOLLINGER_PERCENT_B_20', label: '볼린저 %B' }, { value: 'ATR_PCT_14', label: 'ATR (%)' },

  { value: 'RELATIVE_VOLUME_20', label: '상대 거래량' },
  { value: 'MARKET_CAP', label: '시가총액' }, { value: 'REVENUE_GROWTH', label: '매출 성장률' },
];

function ResultRow({ item, period }: { item: ScreenerSnapshotItem; period: MomentumPeriod }) {
  const momentum = momentumFor(item, period);
  return <tr>
    <td><div className={styles.symbol}><span className={styles.symbolMark}>{item.ticker.slice(0, 2)}</span><strong>{item.ticker}</strong></div></td>
    <td><strong>${item.price.toLocaleString('en-US', { maximumFractionDigits: 2 })}</strong></td>
    <td className={momentum != null && momentum >= 0 ? styles.positive : styles.negative}>{formatPercent(momentum)}</td>
  <td>{item.rsi14?.toFixed(1) ?? '—'}</td>

  <td><span className={item.ema20 != null && item.price > item.ema20 ? styles.successBadge : styles.neutralBadge}>{item.ema20 == null ? '—' : item.price > item.ema20 ? '위' : '아래'}</span></td>

  <td>{item.macdHistogram?.toFixed(2) ?? '—'}</td>

  <td>{item.bollingerPercentB20?.toFixed(2) ?? '—'}</td>

  <td>{formatPercent(item.atrPct14)}</td>

  <td>{item.relativeVolume20 != null ? `${item.relativeVolume20.toFixed(2)}×` : '—'}</td>
    <td><span className={item.aboveSma200 ? styles.successBadge : styles.neutralBadge}>{item.aboveSma200 == null ? '확인 불가' : item.aboveSma200 ? '위에 있음' : '아래에 있음'}</span></td>
    <td>{formatUsd(item.marketCap)}</td>
    <td>{formatPercent(item.revenueGrowth)}</td>
    <td>{formatPercent(item.roe)}</td>
    <td>{item.trailingPe?.toFixed(1) ?? '—'}</td>
  </tr>;
}

export function ScreenerResults({ result, period, sortField, sortDirection, size, onSortField, onSortDirection, onSize, onPage }: {
  result: ScreenerSearchResponse | null;
  period: MomentumPeriod;
  sortField: ScreenerSortField;
  sortDirection: 'ASC' | 'DESC';
  size: number;
  onSortField: (field: ScreenerSortField) => void;
  onSortDirection: (direction: 'ASC' | 'DESC') => void;
  onSize: (size: number) => void;
  onPage: (page: number) => void;
}) {
  const periodLabel = PERIOD_OPTIONS.find((option) => option.value === period)?.label;
  return <section className={styles.panel}>
    <div className={styles.resultsHeader}>
      <h2><ListBulletIcon /> 검색 결과 <span>{result ? `총 ${result.totalElements.toLocaleString('ko-KR')}개 종목` : '—'}</span></h2>
      <div className={styles.sortControls}>
        <label>정렬 기준<select value={sortField} onChange={(e) => onSortField(e.target.value as ScreenerSortField)}>{SORT_OPTIONS.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}</select></label>
        <select aria-label="정렬 방향" value={sortDirection} onChange={(e) => onSortDirection(e.target.value as 'ASC' | 'DESC')}><option value="DESC">내림차순</option><option value="ASC">오름차순</option></select>
        <label>페이지당<select value={size} onChange={(e) => onSize(Number(e.target.value))}><option value="10">10개</option><option value="20">20개</option><option value="50">50개</option></select></label>
      </div>
    </div>
    <div className={styles.tableScroll}>
      <table className={styles.table}><thead><tr><th>종목</th><th>현재가</th><th>모멘텀 ({periodLabel})</th><th>RSI (14)</th><th>EMA 20</th><th>MACD Hist.</th><th>볼린저 %B</th><th>ATR 14</th><th>상대 거래량</th><th>200일선</th><th>시가총액</th><th>매출성장</th><th>ROE</th><th>PER</th></tr></thead>
      <tbody>{result?.items.map((item) => <ResultRow key={item.ticker} item={item} period={period} />)}</tbody></table>
      {result && result.items.length === 0 && <div className={styles.empty}>조건을 만족하는 종목이 없습니다.</div>}
    </div>
    {result && result.totalPages > 0 && <div className={styles.pagination}><span>{result.page * result.size + 1}–{Math.min((result.page + 1) * result.size, result.totalElements)} / {result.totalElements}개 종목</span><div><button disabled={result.page === 0} onClick={() => onPage(result.page - 1)} aria-label="이전 페이지"><ChevronLeftIcon /></button><strong>{result.page + 1}</strong><button disabled={result.page + 1 >= result.totalPages} onClick={() => onPage(result.page + 1)} aria-label="다음 페이지"><ChevronRightIcon /></button></div></div>}
  </section>;
}
