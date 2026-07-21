'use client';

import { use, useEffect, useState } from 'react';
import { VStack, HStack } from '@astryxdesign/core/Stack';
import { Section } from '@astryxdesign/core/Section';
import { Grid } from '@astryxdesign/core/Grid';
import { Card } from '@astryxdesign/core/Card';
import { Heading, Text } from '@astryxdesign/core/Text';
import { Button } from '@astryxdesign/core/Button';
import { IconButton } from '@astryxdesign/core/IconButton';
import { NumberInput } from '@astryxdesign/core/NumberInput';
import { Icon } from '@astryxdesign/core/Icon';
import { Link } from '@astryxdesign/core/Link';
import { SegmentedControl, SegmentedControlItem } from '@astryxdesign/core/SegmentedControl';
import { Center } from '@astryxdesign/core/Center';
import { Spinner } from '@astryxdesign/core/Spinner';
import { Banner } from '@astryxdesign/core/Banner';
import { StarIcon as StarOutlineIcon, XMarkIcon } from '@heroicons/react/24/outline';
import { StarIcon as StarSolidIcon } from '@heroicons/react/24/solid';
import { CandleChart, type SmaOverlay } from '@/components/CandleChart';
import { AlertsPanel } from '@/components/AlertsPanel';
import { PriceChangeIndicator } from '@/components/PriceChangeIndicator';
import { IndicatorPanel } from '@/components/dashboard/IndicatorPanel';
import { NewsPanel } from '@/components/dashboard/NewsPanel';
import { useAuth } from '@/lib/auth-context';
import { resolvePrevClose } from '@/lib/priceChange';
import { useQuoteStream } from '@/lib/quote-stream-context';
import { useCandles, type Timeframe } from '@/lib/candles';
import * as api from '@/lib/api';
import { ApiError } from '@/lib/api';
import type { SymbolProfileResponse, WatchlistItemResponse } from '@/lib/types';

const DAILY_STATS_LIMIT = 250; // ~1 trading year of daily candles, independent of chart timeframe

// 일봉 차트 전용 기간 선택 — 분봉은 보관 기간이 짧아 기간 선택 없이 기본 limit(300)을 그대로 씀.
type ChartPeriod = '1mo' | '3mo' | '6mo' | '1y' | '5y' | 'all';
const DAILY_PERIOD_LIMITS: Record<ChartPeriod, number> = {
  '1mo': 21,
  '3mo': 63,
  '6mo': 126,
  '1y': 252,
  '5y': 1260,
  all: 1500,
};

// SMA is a daily-window indicator, so it's only overlaid on the 일봉 chart (see NO_OVERLAYS below).
// The actual SMA values are computed client-side (CandleChart's computeSma) from whatever candles
// are already on screen -- only *which periods* the user wants is a saved preference (see
// ChartIndicatorSettingsService on the backend), so any period the user types works with zero
// backend-side precomputation.
const DEFAULT_SMA_PERIODS = [20, 50];
const MAX_SMA_OVERLAYS = 5;
const SMA_OVERLAY_COLORS = ['--color-icon-blue', '--color-icon-purple', '--color-icon-teal', '--color-icon-orange', '--color-icon-green'];
function buildSmaOverlays(periods: number[]): SmaOverlay[] {
  return periods.map((period, index) => ({
    period,
    color: SMA_OVERLAY_COLORS[index % SMA_OVERLAY_COLORS.length],
    label: `SMA${period}`,
  }));
}
const NO_OVERLAYS: SmaOverlay[] = [];

function InfoCard({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <Card padding={4}>
      <VStack gap={1}>
        <Text type="supporting" size="sm">
          {label}
        </Text>
        {children}
      </VStack>
    </Card>
  );
}

function ProfileStat({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <VStack gap={1}>
      <Text type="supporting" size="sm">
        {label}
      </Text>
      <Text type="body">{value}</Text>
    </VStack>
  );
}

function PanelCard({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <Card padding={0}>
      <VStack gap={0}>
        <Section padding={3} dividers={['bottom']}>
          <Heading level={5}>{title}</Heading>
        </Section>
        <Section padding={3}>{children}</Section>
      </VStack>
    </Card>
  );
}

export default function SymbolDetailPage({ params }: { params: Promise<{ ticker: string }> }) {
  const { ticker: rawTicker } = use(params);
  const ticker = rawTicker.toUpperCase();

  const { authFetch } = useAuth();
  const { quotes } = useQuoteStream();
  const liveQuote = quotes[ticker];

  const [timeframe, setTimeframe] = useState<Timeframe>('1d');
  const [period, setPeriod] = useState<ChartPeriod>('1y');
  const chartLimit = timeframe === '1d' ? DAILY_PERIOD_LIMITS[period] : undefined;
  const { candles, isLoading: isChartLoading } = useCandles(authFetch, ticker, timeframe, liveQuote, chartLimit);

  // SMA overlay periods are a per-user preference (not per-ticker), loaded once on mount.
  const [smaPeriods, setSmaPeriods] = useState<number[]>(DEFAULT_SMA_PERIODS);
  const [newSmaPeriod, setNewSmaPeriod] = useState<number | null>(null);
  const [isSavingSmaSettings, setIsSavingSmaSettings] = useState(false);

  useEffect(() => {
    let cancelled = false;
    api.getChartIndicatorSettings(authFetch).then((settings) => {
      if (!cancelled) setSmaPeriods(settings.smaOverlays.map((overlay) => overlay.period));
    });
    return () => {
      cancelled = true;
    };
  }, [authFetch]);

  function removeSmaPeriod(periodToRemove: number) {
    setSmaPeriods((prev) => prev.filter((p) => p !== periodToRemove));
  }

  function addSmaPeriod() {
    if (newSmaPeriod == null) return;
    setSmaPeriods((prev) =>
      prev.includes(newSmaPeriod) || prev.length >= MAX_SMA_OVERLAYS ? prev : [...prev, newSmaPeriod].sort((a, b) => a - b),
    );
    setNewSmaPeriod(null);
  }

  async function saveSmaSettings() {
    setIsSavingSmaSettings(true);
    try {
      const saved = await api.saveChartIndicatorSettings(authFetch, { smaOverlays: smaPeriods.map((p) => ({ period: p })) });
      setSmaPeriods(saved.smaOverlays.map((overlay) => overlay.period));
    } finally {
      setIsSavingSmaSettings(false);
    }
  }

  const dailySmaOverlays = buildSmaOverlays(smaPeriods);

  // Fetched independently of the chart's selected timeframe so 전일대비/1년 고가·저가 stay
  // accurate even when the user is looking at 분봉 candles.
  const [dailyStats, setDailyStats] = useState<{ key: string; closes: number[]; high: number | null; low: number | null } | null>(
    null,
  );

  useEffect(() => {
    if (!ticker) return undefined;
    let cancelled = false;
    api
      .getHistory(authFetch, ticker, '1d', DAILY_STATS_LIMIT)
      .then((dailyCandles) => {
        if (cancelled) return;
        if (dailyCandles.length === 0) {
          setDailyStats({ key: ticker, closes: [], high: null, low: null });
          return;
        }
        setDailyStats({
          key: ticker,
          closes: dailyCandles.map((c) => c.close),
          high: Math.max(...dailyCandles.map((c) => c.high)),
          low: Math.min(...dailyCandles.map((c) => c.low)),
        });
      })
      .catch(() => {
        // Unknown/invalid ticker — surfaced separately via the profile fetch's 404 check below.
        if (!cancelled) setDailyStats({ key: ticker, closes: [], high: null, low: null });
      });
    return () => {
      cancelled = true;
    };
  }, [authFetch, ticker]);

  const closes = dailyStats?.key === ticker ? dailyStats.closes : [];
  const prevClose = resolvePrevClose(closes, liveQuote?.price ?? null);
  const changeValue = liveQuote?.price != null && prevClose != null ? liveQuote.price - prevClose : null;
  const changePct = changeValue != null && prevClose ? (changeValue / prevClose) * 100 : null;

  // Distinguishes "존재하지 않는 티커" from "valid ticker, just no data yet" — yfinance's `.info`
  // lookup 404s for genuinely unknown tickers, so it doubles as the page's validity check.
  const [profileState, setProfileState] = useState<{ key: string; profile: SymbolProfileResponse | null; isInvalid: boolean } | null>(
    null,
  );

  useEffect(() => {
    if (!ticker) return undefined;
    let cancelled = false;
    api
      .getSymbolProfile(authFetch, ticker)
      .then((profile) => {
        if (!cancelled) setProfileState({ key: ticker, profile, isInvalid: false });
      })
      .catch((err) => {
        if (cancelled) return;
        setProfileState({ key: ticker, profile: null, isInvalid: err instanceof ApiError && err.status === 404 });
      });
    return () => {
      cancelled = true;
    };
  }, [authFetch, ticker]);

  const profile = profileState?.key === ticker ? profileState.profile : null;
  const isInvalidTicker = profileState?.key === ticker && profileState.isInvalid;

  const [watchlistItem, setWatchlistItem] = useState<WatchlistItemResponse | null | undefined>(undefined);
  const [isTogglingWatchlist, setIsTogglingWatchlist] = useState(false);

  useEffect(() => {
    let cancelled = false;
    api.getWatchlist(authFetch).then((items) => {
      if (cancelled) return;
      setWatchlistItem(items.find((item) => item.ticker === ticker) ?? null);
    });
    return () => {
      cancelled = true;
    };
  }, [authFetch, ticker]);

  async function toggleWatchlist() {
    setIsTogglingWatchlist(true);
    try {
      if (watchlistItem) {
        await api.removeWatchlistItem(authFetch, watchlistItem.id);
        setWatchlistItem(null);
      } else {
        const created = await api.addWatchlistItem(authFetch, ticker);
        setWatchlistItem(created);
      }
    } finally {
      setIsTogglingWatchlist(false);
    }
  }

  const isWatched = Boolean(watchlistItem);

  return (
    <VStack gap={0}>
      <Section padding={4} dividers={['bottom']}>
        <VStack gap={2}>
          <Link href="/stock-list">← 종목 리스트로 돌아가기</Link>
          <HStack justify="between" align="center" wrap="wrap">
            <VStack gap={1}>
              <HStack gap={2} align="end">
                <Heading level={3}>{ticker}</Heading>
                <Text type="supporting" size="sm">
                  {liveQuote?.name ?? (liveQuote?.price != null ? '' : '실시간 시세 대기 중')}
                </Text>
              </HStack>
              <Link href={`/financials/${ticker}`}>재무 대시보드 보기 →</Link>
            </VStack>
            <Button
              variant={isWatched ? 'secondary' : 'primary'}
              icon={<Icon icon={isWatched ? StarSolidIcon : StarOutlineIcon} />}
              label={isWatched ? '관심종목에서 제거' : '관심종목에 추가'}
              isLoading={isTogglingWatchlist || watchlistItem === undefined}
              clickAction={toggleWatchlist}
            />
          </HStack>
        </VStack>
      </Section>

      <Section padding={4}>
        <VStack gap={4}>
          {isInvalidTicker && (
            <Banner
              status="warning"
              title="존재하지 않는 종목입니다"
              description={`"${ticker}"에 대한 정보를 찾을 수 없습니다. 티커를 다시 확인해주세요.`}
            />
          )}

          <Grid columns={5} gap={4}>
            <InfoCard label="현재가">
              <Heading level={4}>{liveQuote?.price != null ? liveQuote.price.toFixed(2) : '—'}</Heading>
              <PriceChangeIndicator changeValue={changeValue} changePct={changePct} />
            </InfoCard>
            <InfoCard label="거래량">
              <Heading level={4}>
                {liveQuote?.volume != null ? Math.round(liveQuote.volume).toLocaleString('ko-KR') : '—'}
              </Heading>
            </InfoCard>
            <InfoCard label="고가 (1년)">
              <Heading level={4}>{dailyStats?.key === ticker && dailyStats.high != null ? dailyStats.high.toFixed(2) : '—'}</Heading>
            </InfoCard>
            <InfoCard label="저가 (1년)">
              <Heading level={4}>{dailyStats?.key === ticker && dailyStats.low != null ? dailyStats.low.toFixed(2) : '—'}</Heading>
            </InfoCard>
            <InfoCard label="갱신 시각">
              <Heading level={4}>
                {liveQuote?.ts ? new Date(liveQuote.ts).toLocaleTimeString('ko-KR') : '—'}
              </Heading>
            </InfoCard>
          </Grid>

          <PanelCard title="기업 개요">
            {profileState?.key !== ticker ? (
              <Center height={80}>
                <Spinner size="md" label="불러오는 중" />
              </Center>
            ) : profile ? (
              <VStack gap={4}>
                {profile.longBusinessSummary && (
                  <Text type="body" color="secondary">
                    {profile.longBusinessSummary}
                  </Text>
                )}
                <Grid columns={4} gap={4}>
                  <ProfileStat label="거래소" value={profile.exchange ?? '—'} />
                  <ProfileStat label="섹터" value={profile.sector ?? '—'} />
                  <ProfileStat label="업종" value={profile.industry ?? '—'} />
                  <ProfileStat
                    label="시가총액"
                    value={profile.marketCap != null ? `$${(profile.marketCap / 1_000_000_000).toFixed(1)}B` : '—'}
                  />
                  <ProfileStat label="PER (Trailing)" value={profile.trailingPE != null ? profile.trailingPE.toFixed(1) : '—'} />
                  <ProfileStat label="PER (Forward)" value={profile.forwardPE != null ? profile.forwardPE.toFixed(1) : '—'} />
                  <ProfileStat
                    label="배당수익률"
                    value={profile.dividendYield != null ? `${profile.dividendYield.toFixed(2)}%` : '—'}
                  />
                  <ProfileStat label="베타" value={profile.beta != null ? profile.beta.toFixed(2) : '—'} />
                  <ProfileStat
                    label="평균 거래량"
                    value={profile.averageVolume != null ? profile.averageVolume.toLocaleString('ko-KR') : '—'}
                  />
                  <ProfileStat
                    label="직원 수"
                    value={profile.fullTimeEmployees != null ? profile.fullTimeEmployees.toLocaleString('ko-KR') : '—'}
                  />
                  <ProfileStat
                    label="본사"
                    value={[profile.city, profile.country].filter(Boolean).join(', ') || '—'}
                  />
                  <ProfileStat
                    label="애널리스트 의견"
                    value={
                      profile.recommendationKey
                        ? `${profile.recommendationKey.toUpperCase()}${
                            profile.targetMeanPrice != null ? ` · 목표가 $${profile.targetMeanPrice.toFixed(2)}` : ''
                          }${profile.numberOfAnalystOpinions != null ? ` (${profile.numberOfAnalystOpinions}명)` : ''}`
                        : '—'
                    }
                  />
                  <ProfileStat
                    label="웹사이트"
                    value={
                      profile.website ? (
                        <Link href={profile.website} isExternalLink isStandalone>
                          {profile.website.replace(/^https?:\/\//, '')}
                        </Link>
                      ) : (
                        '—'
                      )
                    }
                  />
                </Grid>
              </VStack>
            ) : (
              <Text type="body" color="secondary">
                기업 개요 정보를 불러올 수 없습니다
              </Text>
            )}
          </PanelCard>

          <PanelCard title="기술 지표">
            <IndicatorPanel ticker={ticker} />
          </PanelCard>

          <HStack justify="between" align="center" wrap="wrap">
            <Text type="label">가격 차트</Text>
            <HStack gap={2}>
              {timeframe === '1d' && (
                <SegmentedControl value={period} onChange={(value) => setPeriod(value as ChartPeriod)} label="조회 기간">
                  <SegmentedControlItem value="1mo" label="1개월" />
                  <SegmentedControlItem value="3mo" label="3개월" />
                  <SegmentedControlItem value="6mo" label="6개월" />
                  <SegmentedControlItem value="1y" label="1년" />
                  <SegmentedControlItem value="5y" label="5년" />
                  <SegmentedControlItem value="all" label="전체" />
                </SegmentedControl>
              )}
              <SegmentedControl value={timeframe} onChange={(value) => setTimeframe(value as Timeframe)} label="차트 단위">
                <SegmentedControlItem value="1d" label="일봉" />
                <SegmentedControlItem value="1m" label="분봉" />
              </SegmentedControl>
            </HStack>
          </HStack>

          {timeframe === '1d' && (
            <HStack justify="between" align="center" wrap="wrap">
              <Text type="label">SMA 지표 설정</Text>
              <HStack gap={2} align="center" wrap="wrap">
                {smaPeriods.map((smaPeriod) => (
                  <HStack key={smaPeriod} gap={1} align="center">
                    <Text type="body">SMA{smaPeriod}</Text>
                    <IconButton
                      variant="ghost"
                      size="sm"
                      icon={<Icon icon={XMarkIcon} />}
                      label={`SMA${smaPeriod} 제거`}
                      clickAction={() => removeSmaPeriod(smaPeriod)}
                    />
                  </HStack>
                ))}
                <NumberInput label="기간 추가" value={newSmaPeriod} onChange={setNewSmaPeriod} />
                <Button
                  variant="secondary"
                  label="추가"
                  isDisabled={newSmaPeriod == null || smaPeriods.length >= MAX_SMA_OVERLAYS}
                  clickAction={addSmaPeriod}
                />
                <Button variant="primary" label="저장" isLoading={isSavingSmaSettings} clickAction={saveSmaSettings} />
              </HStack>
            </HStack>
          )}

          {isChartLoading ? (
            <Center height={420}>
              <Spinner size="lg" label="차트 불러오는 중" />
            </Center>
          ) : (
            <CandleChart
              // lightweight-charts' setData() keeps whatever zoom/pan range was already visible
              // instead of re-fitting to the new data -- fine for live-tick merges (same key), but
              // switching 기간/차트 단위/SMA 설정 swaps in a differently-shaped series and needs a
              // fresh chart instance (which fits-to-content on its first setData) or the view
              // silently stays cropped to the old range and looks unchanged.
              key={`${ticker}:${timeframe}:${chartLimit ?? 'default'}:${smaPeriods.join(',')}`}
              candles={candles}
              smaOverlays={timeframe === '1d' ? dailySmaOverlays : NO_OVERLAYS}
            />
          )}

          <AlertsPanel ticker={ticker} />

          <PanelCard title="관련 뉴스">
            <NewsPanel ticker={ticker} />
          </PanelCard>
        </VStack>
      </Section>
    </VStack>
  );
}
