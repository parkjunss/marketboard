import { PortfolioPerformance, type Performance, type PerformanceComparison } from './PortfolioPerformance';
import styles from './portfolio-designer.module.css';

export type ValidationResult = {
  status: string; message: string; requestedStart: string; requestedEnd: string;
  observations: number; excludedDates: number;
  assets: { ticker: string; observations: number; startDate: string | null; endDate: string | null; issue: string | null }[];
  comparison: PerformanceComparison | null;
  candidates: { id: string; label: string; performance: Performance }[];
};

export function PortfolioValidation({ result, selected }: { result: ValidationResult; selected: string }) {
  const chosen = result.candidates.find(c=>c.id===selected);
  return <section aria-label="기간 분리 검증">
    <h2>6. 기간 분리 검증</h2>
    <p>요청 검증 기간 {result.requestedStart} ~ {result.requestedEnd}</p>
    <p role="status" className={styles.notice}>{result.message}</p>
    <p>공통 관측일 {result.observations}개 · 종목 간 날짜 불일치 제외 {result.excludedDates}일</p>
    {result.comparison && <p>실제 검증 구간 {result.comparison.dates[0]} ~ {result.comparison.dates.at(-1)}. 휴장일·수집 상태에 따라 요청 날짜와 다를 수 있습니다. 초기 자금은 검증 시작일에 새로 투입하며 산출 기간 수익을 이어 붙이지 않습니다.</p>}
    {result.assets.length > 0 && <details open={!result.comparison}><summary>검증 기간 종목별 데이터 상태</summary><div className={styles.scroll}><table><thead><tr><th>자산</th><th>관측 시작</th><th>관측 종료</th><th>일봉 수</th><th>상태</th></tr></thead><tbody>{result.assets.map(a=><tr key={a.ticker}><th scope="row">{a.ticker}</th><td>{a.startDate??'—'}</td><td>{a.endDate??'—'}</td><td>{a.observations}</td><td>{a.issue??'관측 가능'}</td></tr>)}</tbody></table></div></details>}
    {result.comparison && chosen && <PortfolioPerformance key={`${result.requestedStart}-${result.requestedEnd}`} comparison={result.comparison} chosen={chosen} equal={result.candidates.find(c=>c.id==='equal')?.performance} holdout/>}
  </section>;
}
