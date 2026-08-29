// Single-stock quantitative analysis domain types, split out the same way as ./screener-types.

export interface RiskLevel {
  varPct: number | null;
  cvarPct: number | null;
}

export interface StockAnalysisResult {
  ticker: string;
  asOfDate: string;
  lastPrice: number;
  lookbackDays: number;
  volatility: { annualizedPct: number | null };
  // Keyed by confidence level as a string ("95", "99") -- matches the collector's response.
  risk: Record<string, RiskLevel>;
  distribution: { skewness: number | null; excessKurtosis: number | null };
  hurstExponent: number | null;
  hurstInterpretation: 'TRENDING' | 'MEAN_REVERTING' | 'RANDOM_WALK' | null;
  drawdown: { maxDrawdownPct: number; maxDrawdownDurationDays: number };
  benchmark: { ticker: string; beta: number | null; correlation: number | null };
  monteCarlo: {
    horizonDays: number;
    paths: number;
    percentiles: { p5: number[]; p25: number[]; p50: number[]; p75: number[]; p95: number[] };
  };
}
