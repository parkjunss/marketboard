// Momentum screener domain types, split out of the shared ./types so the screening-condition
// shape and its preset templates live next to each other instead of buried in one large file.

export interface MomentumScreenerParams {
  topN?: number;
  momentumWindowDays?: number;
  trendMaWindow?: number;
  correlationThreshold?: number;
  minMomentumPct?: number;
  maxRsi?: number;
  minMarketCap?: number;
  minRevenue?: number;
}

export interface MomentumScreenerCandidate {
  ticker: string;
  momentumPct: number;
  volatilityPct: number;
  trendUp: boolean;
  rsi14: number;
  revenueGrowthPct: number | null;
  returnOnEquityPct: number | null;
  profitMarginPct: number | null;
  trailingPE: number | null;
  marketCap: number | null;
  totalRevenue: number | null;
  newsSentiment: number | null;
  newsCount: number;
}

export interface MomentumScreenerResult {
  universeSize: number;
  screenedCount: number;
  candidateCount: number;
  results: MomentumScreenerCandidate[];
}

export type MomentumPeriod = 'THREE_MONTHS' | 'SIX_MONTHS' | 'TWELVE_MONTHS';
export type ScreenerSortField =
  | 'MOMENTUM_3M' | 'MOMENTUM_6M' | 'MOMENTUM_12M' | 'RSI_14' | 'VOLATILITY_20D'
  | 'EMA_20' | 'MACD_HISTOGRAM' | 'BOLLINGER_PERCENT_B_20' | 'ATR_PCT_14' | 'RELATIVE_VOLUME_20'
  | 'MARKET_CAP' | 'REVENUE_GROWTH' | 'ROE' | 'TRAILING_PE' | 'NEWS_SENTIMENT' | 'TICKER';

export interface ScreenerSearchRequest {
  momentumPeriod?: MomentumPeriod;
  minMomentumPct?: number;
  maxRsi?: number;
  aboveSma200?: boolean;
  aboveEma20?: boolean;
  minMacdHistogram?: number;
  minBollingerPercentB?: number;
  maxBollingerPercentB?: number;
  maxAtrPct?: number;
  minRelativeVolume?: number;
  minMarketCap?: number;
  minRevenue?: number;
  minRevenueGrowth?: number;
  minRoe?: number;
  maxTrailingPe?: number;
  minNewsSentiment?: number;
  sort?: { field: ScreenerSortField; direction: 'ASC' | 'DESC' };
  page?: number;
  size?: number;
}

export interface ScreenerSnapshotItem {
  ticker: string;
  price: number;
  momentum3m: number | null;
  momentum6m: number | null;
  momentum12m: number | null;
  volatility20d: number | null;
  rsi14: number | null;
  sma50: number | null;
  sma100: number | null;
  sma200: number | null;
  aboveSma200: boolean | null;
  ema20: number | null;
  ema60: number | null;
  macdLine: number | null;
  macdSignal: number | null;
  macdHistogram: number | null;
  bollingerPercentB20: number | null;
  atrPct14: number | null;
  relativeVolume20: number | null;
  marketCap: number | null;
  revenueTtm: number | null;
  revenueGrowth: number | null;
  roe: number | null;
  profitMargin: number | null;
  trailingPe: number | null;
  newsSentiment: number | null;
  newsCount: number;
}

export interface ScreenerSearchResponse {
  snapshotDate: string;
  calculatedAt: string;
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
  items: ScreenerSnapshotItem[];
}

export type ScreenerTemplateId = 'MOMENTUM_GROWTH' | 'SHORT_TERM_MOMENTUM' | 'LARGE_CAP_QUALITY';

export interface ScreenerTemplate {
  id: ScreenerTemplateId;
  label: string;
  description: string;
  // Billions of USD, not raw dollars -- the settings panel's market cap/revenue inputs are in $B
  // for readability; conversion to the API's raw-dollar params happens where the request is built.
  params: MomentumScreenerParams & { minMarketCapB?: number; minRevenueB?: number };
}

export const SCREENER_TEMPLATES: ScreenerTemplate[] = [
  {
    id: 'MOMENTUM_GROWTH',
    label: '모멘텀 성장주',
    description: '6개월 모멘텀 + 200일 추세를 기준으로 넓게 스크리닝합니다. 다른 조건 제한 없음.',
    params: {
      momentumWindowDays: 126,
      trendMaWindow: 200,
      correlationThreshold: 0.6,
    },
  },
  {
    id: 'SHORT_TERM_MOMENTUM',
    label: '단기 강세 종목',
    description: '최근 3개월 사이 15% 이상 급등하며 50일 추세를 상회하는 종목. 과매수(RSI 75 이상)는 제외합니다.',
    params: {
      momentumWindowDays: 63,
      trendMaWindow: 50,
      correlationThreshold: 0.5,
      minMomentumPct: 15,
      maxRsi: 75,
    },
  },
  {
    id: 'LARGE_CAP_QUALITY',
    label: '우량 대형주 모멘텀',
    description: '시가총액 $50B, 매출 $10B 이상인 대형주 중에서만 모멘텀을 봅니다. 과매수(RSI 70 이상)는 제외합니다.',
    params: {
      momentumWindowDays: 126,
      trendMaWindow: 200,
      correlationThreshold: 0.6,
      maxRsi: 70,
      minMarketCapB: 50,
      minRevenueB: 10,
    },
  },
];
