'use client';

import { useState } from 'react';
import { VStack } from '@astryxdesign/core/Stack';
import { Section } from '@astryxdesign/core/Section';
import { Grid } from '@astryxdesign/core/Grid';
import { Card } from '@astryxdesign/core/Card';
import { Heading, Text } from '@astryxdesign/core/Text';
import { NumberInput } from '@astryxdesign/core/NumberInput';
import { Button } from '@astryxdesign/core/Button';
import { Badge } from '@astryxdesign/core/Badge';
import { SegmentedControl, SegmentedControlItem } from '@astryxdesign/core/SegmentedControl';
import { Table, proportional } from '@astryxdesign/core/Table';
import type { TableColumn } from '@astryxdesign/core/Table';
import { Banner } from '@astryxdesign/core/Banner';
import { Center } from '@astryxdesign/core/Center';
import { Spinner } from '@astryxdesign/core/Spinner';
import { ScatterChart, type ScatterPoint } from '@/components/charts/ScatterChart';
import { useAuth } from '@/lib/auth-context';
import { ApiError } from '@/lib/api';
import * as api from '@/lib/api';
import { SCREENER_TEMPLATES, type MomentumScreenerCandidate, type MomentumScreenerResult, type ScreenerTemplateId } from '@/lib/screener-types';

const DEFAULT_TOP_N = 10;
const MAX_TOP_N = 20;
const DEFAULT_CORRELATION_THRESHOLD = 0.6;
const USD_BILLION = 1_000_000_000;

const MOMENTUM_WINDOW_OPTIONS = [
  { value: 63, label: '3개월' },
  { value: 126, label: '6개월' },
  { value: 252, label: '12개월' },
] as const;
const DEFAULT_MOMENTUM_WINDOW_DAYS = 126;

const TREND_MA_OPTIONS = [
  { value: 50, label: '50일' },
  { value: 100, label: '100일' },
  { value: 200, label: '200일' },
] as const;
const DEFAULT_TREND_MA_WINDOW = 200;

interface CandidateRow extends MomentumScreenerCandidate, Record<string, unknown> {}

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

function pct(value: number | null, digits = 2): string {
  return value != null ? `${value.toFixed(digits)}%` : '—';
}

/** 국내 증시 표기 관행(상승 = 빨강, 하락 = 파랑)을 따름 — PriceChangeIndicator와 동일 컨벤션. */
function pctColor(value: number | null): string | undefined {
  if (value == null || value === 0) return undefined;
  return value > 0 ? 'var(--color-text-red)' : 'var(--color-text-blue)';
}

function usdB(value: number | null): string {
  return value != null ? `$${(value / USD_BILLION).toFixed(1)}B` : '—';
}

export default function ScreenerPage() {
  const { authFetch } = useAuth();

  const [templateId, setTemplateId] = useState<ScreenerTemplateId>('MOMENTUM_GROWTH');
  const [topN, setTopN] = useState<number | null>(DEFAULT_TOP_N);
  const [momentumWindowDays, setMomentumWindowDays] = useState(DEFAULT_MOMENTUM_WINDOW_DAYS);
  const [trendMaWindow, setTrendMaWindow] = useState(DEFAULT_TREND_MA_WINDOW);
  const [correlationThreshold, setCorrelationThreshold] = useState<number | null>(DEFAULT_CORRELATION_THRESHOLD);
  const [minMomentumPct, setMinMomentumPct] = useState<number | null>(null);
  const [maxRsi, setMaxRsi] = useState<number | null>(null);
  const [minMarketCapB, setMinMarketCapB] = useState<number | null>(null);
  const [minRevenueB, setMinRevenueB] = useState<number | null>(null);

  const [isRunning, setIsRunning] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<MomentumScreenerResult | null>(null);

  const isParamsValid =
    topN != null && correlationThreshold != null && correlationThreshold > 0 && correlationThreshold <= 1;

  function applyTemplate(id: ScreenerTemplateId) {
    const template = SCREENER_TEMPLATES.find((t) => t.id === id);
    if (!template) return;
    setTemplateId(id);
    setMomentumWindowDays(template.params.momentumWindowDays ?? DEFAULT_MOMENTUM_WINDOW_DAYS);
    setTrendMaWindow(template.params.trendMaWindow ?? DEFAULT_TREND_MA_WINDOW);
    setCorrelationThreshold(template.params.correlationThreshold ?? DEFAULT_CORRELATION_THRESHOLD);
    setMinMomentumPct(template.params.minMomentumPct ?? null);
    setMaxRsi(template.params.maxRsi ?? null);
    setMinMarketCapB(template.params.minMarketCapB ?? null);
    setMinRevenueB(template.params.minRevenueB ?? null);
  }

  async function handleRun() {
    if (!isParamsValid) return;
    setIsRunning(true);
    setError(null);
    try {
      const res = await api.getMomentumScreener(authFetch, {
        topN: topN!,
        momentumWindowDays,
        trendMaWindow,
        correlationThreshold: correlationThreshold!,
        ...(minMomentumPct != null ? { minMomentumPct } : {}),
        ...(maxRsi != null ? { maxRsi } : {}),
        ...(minMarketCapB != null ? { minMarketCap: minMarketCapB * USD_BILLION } : {}),
        ...(minRevenueB != null ? { minRevenue: minRevenueB * USD_BILLION } : {}),
      });
      setResult(res);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '스크리너 실행에 실패했습니다');
    } finally {
      setIsRunning(false);
    }
  }

  const scatterPoints: ScatterPoint[] =
    result?.results.map((c) => ({
      label: c.ticker,
      returnPct: c.momentumPct,
      volatilityPct: c.volatilityPct,
      color: 'var(--color-icon-blue)',
    })) ?? [];

  const columns: TableColumn<CandidateRow>[] = [
    { key: 'ticker', header: '종목', width: proportional(0.7), renderCell: (row) => <Text type="body">{row.ticker}</Text> },
    {
      key: 'momentumPct',
      header: `모멘텀 (${MOMENTUM_WINDOW_OPTIONS.find((o) => o.value === momentumWindowDays)?.label ?? momentumWindowDays})`,
      width: proportional(0.9),
      renderCell: (row) => (
        <Text type="body" style={{ color: pctColor(row.momentumPct) }}>
          {pct(row.momentumPct)}
        </Text>
      ),
    },
    {
      key: 'volatilityPct',
      header: '변동성 (연율화)',
      width: proportional(0.9),
      renderCell: (row) => <Text type="body">{pct(row.volatilityPct)}</Text>,
    },
    {
      key: 'trendUp',
      header: '추세',
      width: proportional(0.7),
      renderCell: (row) => <Badge variant={row.trendUp ? 'success' : 'error'} label={row.trendUp ? '상승' : '하락'} />,
    },
    {
      key: 'rsi14',
      header: 'RSI(14)',
      width: proportional(0.6),
      renderCell: (row) => <Text type="body">{row.rsi14.toFixed(1)}</Text>,
    },
    {
      key: 'revenueGrowthPct',
      header: '매출성장률',
      width: proportional(0.8),
      renderCell: (row) => (
        <Text type="body" style={{ color: pctColor(row.revenueGrowthPct) }}>
          {pct(row.revenueGrowthPct)}
        </Text>
      ),
    },
    {
      key: 'returnOnEquityPct',
      header: 'ROE',
      width: proportional(0.7),
      renderCell: (row) => <Text type="body">{pct(row.returnOnEquityPct)}</Text>,
    },
    {
      key: 'profitMarginPct',
      header: '순이익률',
      width: proportional(0.7),
      renderCell: (row) => <Text type="body">{pct(row.profitMarginPct)}</Text>,
    },
    {
      key: 'trailingPE',
      header: 'PER',
      width: proportional(0.6),
      renderCell: (row) => <Text type="body">{row.trailingPE != null ? row.trailingPE.toFixed(1) : '—'}</Text>,
    },
    {
      key: 'marketCap',
      header: '시가총액',
      width: proportional(0.8),
      renderCell: (row) => <Text type="body">{usdB(row.marketCap)}</Text>,
    },
    {
      key: 'totalRevenue',
      header: '매출 (TTM)',
      width: proportional(0.8),
      renderCell: (row) => <Text type="body">{usdB(row.totalRevenue)}</Text>,
    },
    {
      key: 'newsSentiment',
      header: '뉴스 심리',
      width: proportional(0.9),
      renderCell: (row) => (
        <Text type="body" style={{ color: pctColor(row.newsSentiment) }}>
          {row.newsSentiment != null ? row.newsSentiment.toFixed(2) : '—'}
          {row.newsCount > 0 && (
            <Text type="supporting" size="sm">
              {' '}
              ({row.newsCount}건)
            </Text>
          )}
        </Text>
      ),
    },
  ];

  return (
    <VStack gap={0}>
      <Section padding={4} dividers={['bottom']}>
        <Heading level={3}>모멘텀 종목 스크리너</Heading>
      </Section>

      <Section padding={4}>
        <VStack gap={4}>
          <PanelCard title="스크리너 템플릿">
            <VStack gap={3}>
              <SegmentedControl value={templateId} onChange={(v) => applyTemplate(v as ScreenerTemplateId)} label="스크리너 템플릿">
                {SCREENER_TEMPLATES.map((t) => (
                  <SegmentedControlItem key={t.id} value={t.id} label={t.label} />
                ))}
              </SegmentedControl>
              <Text type="supporting" size="sm">
                {SCREENER_TEMPLATES.find((t) => t.id === templateId)?.description}
              </Text>
              <Text type="supporting" size="sm">
                템플릿을 고르면 아래 조건이 한 번에 채워집니다 — 이후 자유롭게 조정할 수 있습니다.
              </Text>
            </VStack>
          </PanelCard>

          <PanelCard title="스크리너 설정">
            <VStack gap={4}>
              <Grid columns={3} gap={4}>
                <NumberInput label="선정 종목 수" value={topN} onChange={setTopN} min={1} max={MAX_TOP_N} step={1} />
                <NumberInput
                  label="최소 모멘텀 (%, 선택)"
                  value={minMomentumPct}
                  onChange={setMinMomentumPct}
                  step={1}
                  units="%"
                />
                <NumberInput label="최대 RSI (과매수 제외, 선택)" value={maxRsi} onChange={setMaxRsi} min={0} max={100} step={1} />
              </Grid>

              <Grid columns={3} gap={4}>
                <NumberInput
                  label="최소 시가총액 ($B, 선택)"
                  value={minMarketCapB}
                  onChange={setMinMarketCapB}
                  min={0}
                  step={1}
                />
                <NumberInput label="최소 매출 ($B, 선택)" value={minRevenueB} onChange={setMinRevenueB} min={0} step={1} />
              </Grid>

              <VStack gap={2}>
                <Text type="label">모멘텀 측정 기간</Text>
                <SegmentedControl
                  value={String(momentumWindowDays)}
                  onChange={(v) => setMomentumWindowDays(Number(v))}
                  label="모멘텀 측정 기간"
                >
                  {MOMENTUM_WINDOW_OPTIONS.map((o) => (
                    <SegmentedControlItem key={o.value} value={String(o.value)} label={o.label} />
                  ))}
                </SegmentedControl>
              </VStack>

              <VStack gap={2}>
                <Text type="label">추세 판단 이동평균</Text>
                <SegmentedControl value={String(trendMaWindow)} onChange={(v) => setTrendMaWindow(Number(v))} label="추세 판단 이동평균">
                  {TREND_MA_OPTIONS.map((o) => (
                    <SegmentedControlItem key={o.value} value={String(o.value)} label={o.label} />
                  ))}
                </SegmentedControl>
              </VStack>

              <Grid columns={3} gap={4}>
                <NumberInput
                  label="상관관계 임계값 (분산 기준)"
                  value={correlationThreshold}
                  onChange={setCorrelationThreshold}
                  min={0.05}
                  max={1}
                  step={0.05}
                />
              </Grid>

              {error && <Banner status="error" title="실행 실패" description={error} />}

              <Button
                variant="primary"
                label="스크리너 실행"
                isLoading={isRunning}
                isDisabled={!isParamsValid}
                clickAction={handleRun}
              />
              <Text type="supporting" size="sm">
                S&amp;P 500 전 종목을 위 조건으로 걸러낸 뒤(추세 이동평균 상회 + 최소 모멘텀/최대 RSI 조건 만족), 서로
                상관관계가 임계값 이상인 종목은 제외하며 상위 종목을 고릅니다. 시가총액·매출 조건은 실시간 조회가 필요해
                후보를 넉넉히 뽑아본 뒤 걸러내는 방식이라, 조건이 너무 까다로우면 선정 종목 수보다 적게 나올 수 있습니다.
                실행에 15~30초 정도 걸릴 수 있습니다.
              </Text>
            </VStack>
          </PanelCard>

          {isRunning && (
            <Center height={200}>
              <Spinner size="lg" label="스크리너 실행 중 (최대 30초)" />
            </Center>
          )}

          {!isRunning && result && (
            <>
              <PanelCard title="스크리닝 결과">
                <VStack gap={3}>
                  <Text type="supporting" size="sm">
                    전체 {result.universeSize}종목 중 {result.screenedCount}종목 분석 → 상승추세 후보{' '}
                    {result.candidateCount}종목 → 분산 선정 {result.results.length}종목
                  </Text>
                  {result.results.length === 0 ? (
                    <Text type="body" color="secondary">
                      조건을 만족하는 종목이 없습니다
                    </Text>
                  ) : (
                    <Table data={result.results as CandidateRow[]} columns={columns} idKey="ticker" hasHover />
                  )}
                </VStack>
              </PanelCard>

              {result.results.length > 0 && (
                <PanelCard title="모멘텀 대비 변동성">
                  <ScatterChart points={scatterPoints} width={900} height={420} />
                </PanelCard>
              )}
            </>
          )}
        </VStack>
      </Section>
    </VStack>
  );
}
