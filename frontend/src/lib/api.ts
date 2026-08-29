import type {
  AlertResponse,
  BacktestRunRequest,
  BacktestRunResponse,
  CandleResponse,
  ChartIndicatorSettingsResponse,
  CollectorStatusResponse,
  DashboardConfigResponse,
  FearGreedResponse,
  FinancialsResponse,
  IndicatorResponse,
  MarketBreadthResponse,
  MarketIndexInfo,
  NewsItem,
  OptionsLevelsResponse,
  PortfolioPositionResponse,
  PortfolioSummaryResponse,
  PutCallRatioResponse,
  QuoteResponse,
  SectorPerformance,
  SymbolProfileResponse,
  SymbolResponse,
  SystemStatusResponse,
  TokenResponse,
  UserResponse,
  WatchlistItemResponse,
} from './types';
import type { MomentumScreenerParams, MomentumScreenerResult } from './screener-types';
import type { StockAnalysisResult } from './analysis-types';

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:8080';

export class ApiError extends Error {
  status: number;

  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

interface RequestOptions extends Omit<RequestInit, 'body'> {
  accessToken?: string | null;
  body?: unknown;
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { accessToken, body, headers, ...rest } = options;

  const res = await fetch(`${API_BASE_URL}${path}`, {
    ...rest,
    headers: {
      ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
      ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
      ...headers,
    },
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });

  if (res.status === 204) {
    return undefined as T;
  }

  const text = await res.text();
  const data = text ? JSON.parse(text) : undefined;

  if (!res.ok) {
    const message =
      data && typeof data === 'object' && 'message' in data
        ? String((data as { message: unknown }).message)
        : res.statusText;
    throw new ApiError(res.status, message);
  }

  return data as T;
}

export function signup(input: {
  email: string;
  password: string;
  passwordConfirm: string;
  username: string;
  termsAgreed: boolean;
}): Promise<void> {
  return request<void>('/api/auth/signup', { method: 'POST', body: input });
}

export function login(input: { email: string; password: string }): Promise<TokenResponse> {
  return request<TokenResponse>('/api/auth/login', { method: 'POST', body: input });
}

export function refresh(refreshToken: string): Promise<TokenResponse> {
  return request<TokenResponse>('/api/auth/refresh', { method: 'POST', body: { refreshToken } });
}

export function logout(accessToken: string): Promise<void> {
  return request<void>('/api/auth/logout', { method: 'POST', accessToken });
}

/** Authenticated calls below take a fetcher (normally AuthContext's `authFetch`) so 401s trigger a refresh-and-retry. */
export type Fetcher = <T>(path: string, options?: { method?: string; body?: unknown }) => Promise<T>;

export function getQuotes(fetcher: Fetcher): Promise<QuoteResponse[]> {
  return fetcher<QuoteResponse[]>('/api/quotes');
}

export function getHistory(
  fetcher: Fetcher,
  ticker: string,
  timeframe: string,
  limit = 200,
): Promise<CandleResponse[]> {
  const params = new URLSearchParams({ timeframe, limit: String(limit) });
  return fetcher<CandleResponse[]>(`/api/quotes/${ticker}/history?${params}`);
}

export function getSymbolProfile(fetcher: Fetcher, ticker: string): Promise<SymbolProfileResponse> {
  return fetcher<SymbolProfileResponse>(`/api/symbols/${ticker}/profile`);
}

export interface StockAnalysisParams {
  lookbackDays?: number;
  monteCarloHorizonDays?: number;
  monteCarloPaths?: number;
}

export function getStockAnalysis(fetcher: Fetcher, ticker: string, params: StockAnalysisParams = {}): Promise<StockAnalysisResult> {
  const query = new URLSearchParams();
  if (params.lookbackDays != null) query.set('lookbackDays', String(params.lookbackDays));
  if (params.monteCarloHorizonDays != null) query.set('monteCarloHorizonDays', String(params.monteCarloHorizonDays));
  if (params.monteCarloPaths != null) query.set('monteCarloPaths', String(params.monteCarloPaths));
  const qs = query.toString();
  return fetcher<StockAnalysisResult>(`/api/analysis/${ticker}${qs ? `?${qs}` : ''}`);
}

export function getWatchlist(fetcher: Fetcher): Promise<WatchlistItemResponse[]> {
  return fetcher<WatchlistItemResponse[]>('/api/watchlist');
}

export function addWatchlistItem(fetcher: Fetcher, ticker: string): Promise<WatchlistItemResponse> {
  return fetcher<WatchlistItemResponse>('/api/watchlist', { method: 'POST', body: { ticker } });
}

export function removeWatchlistItem(fetcher: Fetcher, id: number): Promise<void> {
  return fetcher<void>(`/api/watchlist/${id}`, { method: 'DELETE' });
}

export function getAlerts(fetcher: Fetcher): Promise<AlertResponse[]> {
  return fetcher<AlertResponse[]>('/api/alerts');
}

export function createAlert(
  fetcher: Fetcher,
  input: { ticker: string; condition: 'ABOVE' | 'BELOW'; targetPrice: number },
): Promise<AlertResponse> {
  return fetcher<AlertResponse>('/api/alerts', { method: 'POST', body: input });
}

export function deleteAlert(fetcher: Fetcher, id: number): Promise<void> {
  return fetcher<void>(`/api/alerts/${id}`, { method: 'DELETE' });
}

export function getAdminSymbols(fetcher: Fetcher): Promise<SymbolResponse[]> {
  return fetcher<SymbolResponse[]>('/api/admin/symbols');
}

export function createAdminSymbol(
  fetcher: Fetcher,
  input: { ticker: string; name: string; exchange: string; priority: number },
): Promise<SymbolResponse> {
  return fetcher<SymbolResponse>('/api/admin/symbols', { method: 'POST', body: input });
}

export function updateAdminSymbol(
  fetcher: Fetcher,
  id: number,
  input: { name: string; exchange: string; active: boolean; priority: number },
): Promise<SymbolResponse> {
  return fetcher<SymbolResponse>(`/api/admin/symbols/${id}`, { method: 'PATCH', body: input });
}

export function bulkSetAdminSymbolsActive(
  fetcher: Fetcher,
  ids: number[],
  active: boolean,
): Promise<SymbolResponse[]> {
  return fetcher<SymbolResponse[]>('/api/admin/symbols/bulk-active', { method: 'PATCH', body: { ids, active } });
}

export function deleteAdminSymbol(fetcher: Fetcher, id: number): Promise<void> {
  return fetcher<void>(`/api/admin/symbols/${id}`, { method: 'DELETE' });
}

export function backfillAdminSymbol(fetcher: Fetcher, id: number, period = '5y'): Promise<void> {
  return fetcher<void>(`/api/admin/symbols/${id}/backfill?period=${period}`, { method: 'POST' });
}

export function getAdminUsers(fetcher: Fetcher): Promise<UserResponse[]> {
  return fetcher<UserResponse[]>('/api/admin/users');
}

export function updateAdminUser(
  fetcher: Fetcher,
  id: number,
  input: { role: 'USER' | 'ADMIN'; status: 'ACTIVE' | 'SUSPENDED' },
): Promise<UserResponse> {
  return fetcher<UserResponse>(`/api/admin/users/${id}`, { method: 'PATCH', body: input });
}

export function revokeAdminUserToken(fetcher: Fetcher, id: number): Promise<void> {
  return fetcher<void>(`/api/admin/users/${id}/revoke-token`, { method: 'POST' });
}

export function deleteAdminUser(fetcher: Fetcher, id: number): Promise<void> {
  return fetcher<void>(`/api/admin/users/${id}`, { method: 'DELETE' });
}

export function getCollectorStatus(fetcher: Fetcher): Promise<CollectorStatusResponse> {
  return fetcher<CollectorStatusResponse>('/api/admin/collector/status');
}

export function getSystemStatus(fetcher: Fetcher): Promise<SystemStatusResponse> {
  return fetcher<SystemStatusResponse>('/api/admin/system/status');
}

export function getGeneralNews(fetcher: Fetcher): Promise<NewsItem[]> {
  return fetcher<NewsItem[]>('/api/news');
}

export function getCompanyNews(fetcher: Fetcher, ticker: string): Promise<NewsItem[]> {
  return fetcher<NewsItem[]>(`/api/news/${ticker}`);
}

export function getIndicators(fetcher: Fetcher, ticker: string): Promise<IndicatorResponse[]> {
  return fetcher<IndicatorResponse[]>(`/api/indicators/${ticker}`);
}

export function getDashboardConfig(fetcher: Fetcher): Promise<DashboardConfigResponse> {
  return fetcher<DashboardConfigResponse>('/api/dashboard');
}

export function saveDashboardConfig(
  fetcher: Fetcher,
  config: DashboardConfigResponse,
): Promise<DashboardConfigResponse> {
  return fetcher<DashboardConfigResponse>('/api/dashboard', { method: 'PUT', body: config });
}

export function getChartIndicatorSettings(fetcher: Fetcher): Promise<ChartIndicatorSettingsResponse> {
  return fetcher<ChartIndicatorSettingsResponse>('/api/chart-indicator-settings');
}

export function saveChartIndicatorSettings(
  fetcher: Fetcher,
  settings: ChartIndicatorSettingsResponse,
): Promise<ChartIndicatorSettingsResponse> {
  return fetcher<ChartIndicatorSettingsResponse>('/api/chart-indicator-settings', { method: 'PUT', body: settings });
}

export function runBacktest(fetcher: Fetcher, request: BacktestRunRequest): Promise<BacktestRunResponse> {
  return fetcher<BacktestRunResponse>('/api/backtest/runs', { method: 'POST', body: request });
}

export function getBacktestRuns(fetcher: Fetcher): Promise<BacktestRunResponse[]> {
  return fetcher<BacktestRunResponse[]>('/api/backtest/runs');
}

export function getBacktestRun(fetcher: Fetcher, id: number): Promise<BacktestRunResponse> {
  return fetcher<BacktestRunResponse>(`/api/backtest/runs/${id}`);
}

export function getMarketIndices(fetcher: Fetcher): Promise<MarketIndexInfo[]> {
  return fetcher<MarketIndexInfo[]>('/api/market-indices');
}

// Slow (~15-30s): scans the whole S&P 500 universe, then fetches live fundamentals/news sentiment
// for the shortlist. No client-side timeout here on purpose -- the backend's collector call itself
// has a generous 60s timeout (see CollectorClient.getMomentumScreener), so the fetcher should just
// wait it out rather than aborting early. Any omitted param falls back to the collector's default
// screening condition (see app/screener.py).
export function getMomentumScreener(fetcher: Fetcher, params: MomentumScreenerParams = {}): Promise<MomentumScreenerResult> {
  const query = new URLSearchParams();
  if (params.topN != null) query.set('topN', String(params.topN));
  if (params.momentumWindowDays != null) query.set('momentumWindowDays', String(params.momentumWindowDays));
  if (params.trendMaWindow != null) query.set('trendMaWindow', String(params.trendMaWindow));
  if (params.correlationThreshold != null) query.set('correlationThreshold', String(params.correlationThreshold));
  if (params.minMomentumPct != null) query.set('minMomentumPct', String(params.minMomentumPct));
  if (params.maxRsi != null) query.set('maxRsi', String(params.maxRsi));
  if (params.minMarketCap != null) query.set('minMarketCap', String(params.minMarketCap));
  if (params.minRevenue != null) query.set('minRevenue', String(params.minRevenue));
  return fetcher<MomentumScreenerResult>(`/api/screener/momentum?${query.toString()}`);
}

export function getSectorPerformance(fetcher: Fetcher): Promise<SectorPerformance[]> {
  return fetcher<SectorPerformance[]>('/api/market-indices/sectors/performance');
}

export function getMarketBreadth(fetcher: Fetcher): Promise<MarketBreadthResponse> {
  return fetcher<MarketBreadthResponse>('/api/market-breadth');
}

export function getFearGreed(fetcher: Fetcher): Promise<FearGreedResponse> {
  return fetcher<FearGreedResponse>('/api/market-sentiment/fear-greed');
}

export function getPutCallRatio(fetcher: Fetcher): Promise<PutCallRatioResponse> {
  return fetcher<PutCallRatioResponse>('/api/market-sentiment/put-call-ratio');
}

export function getPutCallRatioForTicker(fetcher: Fetcher, ticker: string): Promise<PutCallRatioResponse> {
  return fetcher<PutCallRatioResponse>(`/api/market-sentiment/put-call-ratio/${ticker}`);
}

export function getOptionsLevels(fetcher: Fetcher, ticker: string): Promise<OptionsLevelsResponse> {
  return fetcher<OptionsLevelsResponse>(`/api/market-sentiment/options-levels/${ticker}`);
}

export function getMarketIndexHistory(fetcher: Fetcher, slug: string): Promise<CandleResponse[]> {
  return fetcher<CandleResponse[]>(`/api/market-indices/${slug}/history`);
}

export function getFinancials(fetcher: Fetcher, ticker: string): Promise<FinancialsResponse> {
  return fetcher<FinancialsResponse>(`/api/financials/${ticker}`);
}

export function getPortfolios(fetcher: Fetcher): Promise<PortfolioSummaryResponse[]> {
  return fetcher<PortfolioSummaryResponse[]>('/api/portfolios');
}

export function createPortfolio(fetcher: Fetcher, name: string): Promise<PortfolioSummaryResponse> {
  return fetcher<PortfolioSummaryResponse>('/api/portfolios', { method: 'POST', body: { name } });
}

export function renamePortfolio(fetcher: Fetcher, id: number, name: string): Promise<PortfolioSummaryResponse> {
  return fetcher<PortfolioSummaryResponse>(`/api/portfolios/${id}`, { method: 'PATCH', body: { name } });
}

export function deletePortfolio(fetcher: Fetcher, id: number): Promise<void> {
  return fetcher<void>(`/api/portfolios/${id}`, { method: 'DELETE' });
}

export function getPortfolioPositions(fetcher: Fetcher, portfolioId: number): Promise<PortfolioPositionResponse[]> {
  return fetcher<PortfolioPositionResponse[]>(`/api/portfolios/${portfolioId}/positions`);
}

export function addPortfolioPosition(
  fetcher: Fetcher,
  portfolioId: number,
  input: { ticker: string; quantity: number; avgCost: number },
): Promise<PortfolioPositionResponse> {
  return fetcher<PortfolioPositionResponse>(`/api/portfolios/${portfolioId}/positions`, { method: 'POST', body: input });
}

export function updatePortfolioPosition(
  fetcher: Fetcher,
  portfolioId: number,
  positionId: number,
  input: { quantity: number; avgCost: number },
): Promise<PortfolioPositionResponse> {
  return fetcher<PortfolioPositionResponse>(`/api/portfolios/${portfolioId}/positions/${positionId}`, {
    method: 'PATCH',
    body: input,
  });
}

export function removePortfolioPosition(fetcher: Fetcher, portfolioId: number, positionId: number): Promise<void> {
  return fetcher<void>(`/api/portfolios/${portfolioId}/positions/${positionId}`, { method: 'DELETE' });
}
