import type { MarketBreadthResponse, PortfolioSummaryResponse } from './types';

export interface ReviewMetric {
  label: string;
  value: string;
  detail: string;
}

interface IndexChange {
  slug: string;
  name: string;
  change: number;
  unit: string;
}

export function buildReviewInsights(
  changes: IndexChange[],
  breadth?: MarketBreadthResponse,
  portfolios: PortfolioSummaryResponse[] = [],
) {
  const advanceRate = breadth ? breadth.advancingCount / Math.max(breadth.universeSize, 1) * 100 : null;
  const rising = changes.filter(row => row.change > 0).length;
  const risks: string[] = [];
  const vix = changes.find(row => row.slug === 'VIX');
  const yield10y = changes.find(row => row.slug === 'US10Y');
  const weakPortfolios = portfolios.filter(row => !['READY', 'EMPTY'].includes(row.valuationStatus));
  const losingPortfolios = portfolios.filter(row => row.totalUnrealizedPnlPct !== null && row.totalUnrealizedPnlPct <= -10);

  if (vix && vix.change >= 15) risks.push(`VIX가 비교 기간 동안 ${vix.change.toFixed(1)}% 상승했습니다.`);
  if (yield10y && yield10y.change >= 10) risks.push(`미국 10년 금리가 ${yield10y.change.toFixed(1)}bp 상승했습니다.`);
  if (advanceRate !== null && advanceRate < 40) risks.push(`시장 상승 종목 비율이 ${advanceRate.toFixed(1)}%로 낮습니다.`);
  if (breadth && breadth.new52wLowCount > breadth.new52wHighCount) risks.push(`52주 신저가 ${breadth.new52wLowCount}개가 신고가 ${breadth.new52wHighCount}개보다 많습니다.`);
  if (weakPortfolios.length) risks.push(`${weakPortfolios.length}개 포트폴리오에 누락되거나 오래된 가격이 있습니다.`);
  if (losingPortfolios.length) risks.push(`${losingPortfolios.length}개 포트폴리오의 미실현 손익률이 -10% 이하입니다.`);

  const metrics: ReviewMetric[] = [
    { label: '시장 방향', value: `${rising} / ${changes.length} 상승`, detail: '확인 가능한 시장 지표 기준' },
    { label: '시장 폭', value: advanceRate === null ? '자료 없음' : `상승 ${advanceRate.toFixed(1)}%`, detail: breadth ? `${breadth.snapshotDate} · ${breadth.universeSize}종목` : '최근 스냅샷 없음' },
    { label: '포트폴리오 품질', value: weakPortfolios.length ? `${weakPortfolios.length}개 확인 필요` : portfolios.length ? '가격 관측 양호' : '보유 없음', detail: `${portfolios.length}개 포트폴리오 기준` },
  ];
  const riskText = risks.length ? risks.join(' ') : '현재 규칙에서 감지된 주요 위험 신호는 없습니다.';
  const draft = `시장 지표 ${changes.length}개 중 ${rising}개가 상승했고, ${advanceRate === null ? '시장 폭 자료는 확인하지 못했습니다' : `상승 종목 비율은 ${advanceRate.toFixed(1)}%입니다`}. ${riskText} 이 수치는 자동 매매 신호가 아니므로 목표 비중과 개별 종목 근거를 추가 확인합니다.`;

  return { metrics, risks, draft };
}
