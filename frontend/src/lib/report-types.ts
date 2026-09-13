import type { CandleResponse, FinancialsResponse, ReviewResource } from './types';
import type { StockAnalysisResult } from './analysis-types';

export interface StockReportSummary { id: number; ticker: string; createdAt: string }
export interface StockReportDetail extends StockReportSummary {
  payload: {
    schemaVersion: number;
    calculationVersion: string;
    ticker: string;
    startedAt: string;
    capturedAt: string;
    price: ReviewResource<{ price: number; source: string; provider: string; asOf: string | null; fetchedAt: string | null; sessionDate: string | null; status: string }>;
    history: ReviewResource<CandleResponse[]>;
    financials: ReviewResource<{ statements: FinancialsResponse; fetchedAt: string | null }>;
    analysis: ReviewResource<StockAnalysisResult>;
    checks: string[];
  };
}
