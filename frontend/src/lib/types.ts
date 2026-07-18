export type Role = 'USER' | 'ADMIN';

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
}

export interface QuoteResponse {
  symbol: string;
  name: string | null;
  price: number | null;
  volume: number | null;
  ts: string | null;
}

export interface CandleResponse {
  ts: string;
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
}

export interface WatchlistItemResponse {
  id: number;
  symbolId: number;
  ticker: string;
  name: string;
  sortOrder: number;
}

export type UserStatus = 'ACTIVE' | 'SUSPENDED';

export interface SymbolResponse {
  id: number;
  ticker: string;
  name: string;
  exchange: string;
  active: boolean;
  priority: number;
}

export interface SymbolProfileResponse {
  ticker: string;
  name: string;
  exchange: string | null;
  sector: string | null;
  industry: string | null;
  currency: string | null;
  marketCap: number | null;
}

export interface UserResponse {
  id: number;
  email: string;
  username: string;
  role: Role;
  status: UserStatus;
  createdAt: string;
}

export interface CollectorHealth {
  ws_connected: boolean;
  reconnect_count: number;
  subscribed_symbols: string[];
  last_tick_at: Record<string, string>;
  last_error: string | null;
}

export interface CollectorStatusResponse {
  reachable: boolean;
  health: CollectorHealth | null;
}

export interface SystemStatusResponse {
  connectedUsers: number;
  activeSessions: number;
}

export type AlertCondition = 'ABOVE' | 'BELOW';

export interface AlertResponse {
  id: number;
  ticker: string;
  condition: AlertCondition;
  targetPrice: number;
  triggeredAt: string | null;
  createdAt: string;
}

export interface AlertNotification {
  alertId: number;
  ticker: string;
  condition: AlertCondition;
  targetPrice: number;
  price: number;
}

export interface NewsItem {
  category: string;
  datetime: number;
  headline: string;
  id: number;
  image: string;
  related: string;
  source: string;
  summary: string;
  url: string;
}

export type IndicatorTypeName = 'SMA20' | 'SMA50' | 'RSI14';

export interface IndicatorResponse {
  type: IndicatorTypeName;
  timeframe: string;
  value: number;
  computedAt: string;
}

export type PanelType = 'CHART' | 'NEWS' | 'WATCHLIST' | 'INDICATOR';

export interface PanelConfig {
  slot: number;
  type: PanelType;
  ticker?: string | null;
  timeframe?: string | null;
  indicator?: string | null;
}

export interface DashboardConfigResponse {
  layoutKey: string;
  panels: PanelConfig[];
}

export interface MarketIndexInfo {
  slug: string;
  name: string;
}

export interface FinancialsYearMetric {
  year: number;
  [metric: string]: number | null;
}

export interface FinancialsKpis {
  quickRatio: number | null;
  currentRatio: number | null;
  debtToCapitalPct: number | null;
  totalDebtToFcf: number | null;
  cashAndEquivalents: number | null;
  interestCoverage: number | null;
}

export interface FinancialsResponse {
  ticker: string;
  name: string;
  years: number[];
  earningsAnalysis: FinancialsYearMetric[];
  growthAnalysis: FinancialsYearMetric[];
  profitabilityAnalysis: FinancialsYearMetric[];
  cashFlowAnalysis: FinancialsYearMetric[];
  marginsAnalysis: FinancialsYearMetric[];
  marketAnalysis: FinancialsYearMetric[];
  kpis: FinancialsKpis;
}

export type PriceSource = 'LIVE' | 'CLOSE' | 'UNAVAILABLE';

export interface PortfolioSummaryResponse {
  id: number;
  name: string;
  positionCount: number;
  totalMarketValue: number | null;
  totalCostBasis: number | null;
  totalUnrealizedPnl: number | null;
  totalUnrealizedPnlPct: number | null;
  createdAt: string;
  updatedAt: string;
}

export interface PortfolioPositionResponse {
  id: number;
  symbolId: number;
  ticker: string;
  name: string;
  quantity: number;
  avgCost: number;
  currentPrice: number | null;
  priceSource: PriceSource;
  marketValue: number | null;
  costBasis: number;
  unrealizedPnl: number | null;
  unrealizedPnlPct: number | null;
}
