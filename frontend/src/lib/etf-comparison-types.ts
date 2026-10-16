import type { BacktestMetrics } from './types';

export interface EtfComparisonRequest {
  startDate: string;
  endDate: string;
  initialCapital: number;
  feeBps: number;
  slippageBps: number;
}

export interface EtfComparisonResult {
  ready: boolean;
  message: string | null;
  requestedStartDate: string | null;
  requestedEndDate: string | null;
  startDate: string | null;
  endDate: string | null;
  initialCapital: number | null;
  feeBps: number | null;
  slippageBps: number | null;
  cashRatePct: number | null;
  currency: string | null;
  strategies: {
    strategyId: string;
    label: string;
    metrics: BacktestMetrics;
    equityCurve: { date: string; value: number; returnPct: number }[];
    totalFees: number;
    totalSlippage: number;
    turnoverPct: number;
    trades: {
      ticker: string; side: 'BUY' | 'SELL'; quantity: number; price: number; notional: number;
      fee: number; slippage: number; signalDate: string; executionDate: string;
    }[];
    rebalances: {
      signalDate: string; executionDate: string; cashWeight: number;
      targets: { ticker: string; weight: number }[];
    }[];
    regimes: { label: string; days: number; returnPct: number | null }[];
  }[];
}
