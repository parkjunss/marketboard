import type { Fetcher } from './api';
import type { FearGreedResponse, MarketBreadthResponse, PutCallRatioResponse, SectorPerformance } from './types';

export interface MarketReportSummary {
  id: number; market: string; tradingDate: string; version: number; status: 'READY' | 'PARTIAL'; publishedAt: string;
}
export interface LatestMarketReport {
  expectedTradingDate: string | null; state: 'CURRENT' | 'DELAYED' | 'UNAVAILABLE'; report: MarketReportSummary | null;
}
export interface ReportIndex {
  slug: string; name: string; unit: string; close: number;
  changePct1d: number | null; changePct1w: number | null; changePct1m: number | null;
  evidence: { tradingDate: string; fetchedAt: string; source: string; sessionStatus: string; adjustment: string };
}
export interface MarketReportDetail {
  report: MarketReportSummary;
  payload: {
    schemaVersion: number; calculationVersion: string;
    session: { tradingDate: string; closedAt: string; calendar: string; calendarVersion: string };
    startedAt: string; capturedAt: string;
    highlights: Array<{ text: string; section: string }>;
    indices: ReportIndex[]; macro: ReportIndex[];
    sectors: Array<SectorPerformance & { tradingDate: string; fetchedAt: string; source: string }>;
    breadth: (MarketBreadthResponse & { tradingDate: string; source: string; eligibleCount: number; status: string }) | null;
    fearGreed: FearGreedResponse | null; putCall: PutCallRatioResponse | null;
    news: Array<{ headline: string; source: string; publishedAt: string; url: string }>;
    checks: string[];
  };
}
export const getLatestMarketReport = (fetcher: Fetcher) => fetcher<LatestMarketReport>('/api/market-reports/latest');
export const getMarketReports = (fetcher: Fetcher, before = '') => fetcher<MarketReportSummary[]>(`/api/market-reports${before ? `?before=${encodeURIComponent(before)}` : ''}`);
export const getMarketReport = (fetcher: Fetcher, id: number) => fetcher<MarketReportDetail>(`/api/market-reports/${id}`);
