export type Config = { version: string; ticker: string; startDate: string; endDate: string; window: number; initialCapital: string;
  feeBps: string; slippageBps: string; maxPositionWeight: string; maxOrderValue: string; intervalSeconds: number };
export type State = { cash: string; shares: string; equity: string; realizedPnl: string; unrealizedPnl: string; fees: string; returnPct: string; maxDrawdownPct: string };
export type Run = { id: number; status: string; cursor: number; barCount: number; configuration: Config; state: State; createdAt: string; error: string | null; strategyId: number | null };
export type Performance = Pick<State, 'equity' | 'returnPct' | 'maxDrawdownPct' | 'fees'> & { trades: number };
export type Comparison = { steps: number; entryDate: string | null; asOf: string | null; excessReturnPct: string;
  strategy: Performance; benchmark: Performance; curve: { date: string; strategy: string; benchmark: string }[];
  priceQuality: { adjustment: string; dividends: string; missingSessions: string } };
