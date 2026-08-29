'use client';

import { useEffect, useMemo, useState } from 'react';
import { VStack, HStack } from '@astryxdesign/core/Stack';
import { Section } from '@astryxdesign/core/Section';
import { Grid } from '@astryxdesign/core/Grid';
import { Card } from '@astryxdesign/core/Card';
import { Heading, Text } from '@astryxdesign/core/Text';
import { TextInput } from '@astryxdesign/core/TextInput';
import { NumberInput } from '@astryxdesign/core/NumberInput';
import { DateRangeInput, type DateRange } from '@astryxdesign/core/DateRangeInput';
import { Button } from '@astryxdesign/core/Button';
import { IconButton } from '@astryxdesign/core/IconButton';
import { Icon } from '@astryxdesign/core/Icon';
import { Table, proportional } from '@astryxdesign/core/Table';
import type { TableColumn } from '@astryxdesign/core/Table';
import { Banner } from '@astryxdesign/core/Banner';
import { Center } from '@astryxdesign/core/Center';
import { Spinner } from '@astryxdesign/core/Spinner';
import { TabList, Tab } from '@astryxdesign/core/TabList';
import { SegmentedControl, SegmentedControlItem } from '@astryxdesign/core/SegmentedControl';
import { XMarkIcon } from '@heroicons/react/24/outline';
import { MultiLineChart } from '@/components/charts/MultiLineChart';
import { ScatterChart, type ScatterPoint } from '@/components/charts/ScatterChart';
import { useAuth } from '@/lib/auth-context';
import { ApiError } from '@/lib/api';
import * as api from '@/lib/api';
import type { BacktestRunResponse, BacktestStrategyType, RebalanceFrequency } from '@/lib/types';

const MAX_TICKERS = 10;
const DEFAULT_INITIAL_CAPITAL = 10_000_000;
const DEFAULT_RISK_FREE_RATE_PCT = 3;
const DEFAULT_SMA_SHORT_WINDOW = 20;
const DEFAULT_SMA_LONG_WINDOW = 60;
const DEFAULT_TARGET_VOLATILITY_PCT = 15;
const DEFAULT_VIX_THRESHOLD = 35;

const STRATEGY_LABELS: Record<BacktestStrategyType, string> = {
  BUY_AND_HOLD: '매수 후 보유',
  SMA_CROSSOVER: 'SMA 크로스오버',
  PERIODIC_REBALANCE: '정기 리밸런싱',
  VOLATILITY_TARGET: '변동성 타겟팅',
};

const REBALANCE_FREQUENCY_LABELS: Record<RebalanceFrequency, string> = {
  MONTHLY: '매월',
  QUARTERLY: '분기별',
  YEARLY: '매년',
};

function strategySummary(run: {
  strategyType: BacktestStrategyType | null;
  smaShortWindow: number | null;
  smaLongWindow: number | null;
  rebalanceFrequency: RebalanceFrequency | null;
  targetVolatilityPct: number | null;
  vixThreshold: number | null;
}): string {
  const type = run.strategyType ?? 'BUY_AND_HOLD';
  if (type === 'SMA_CROSSOVER') {
    return `${STRATEGY_LABELS[type]} (${run.smaShortWindow ?? '?'}/${run.smaLongWindow ?? '?'}일)`;
  }
  if (type === 'PERIODIC_REBALANCE') {
    return `${STRATEGY_LABELS[type]} (${run.rebalanceFrequency ? REBALANCE_FREQUENCY_LABELS[run.rebalanceFrequency] : '?'})`;
  }
  if (type === 'VOLATILITY_TARGET') {
    return `${STRATEGY_LABELS[type]} (목표 ${run.targetVolatilityPct ?? '?'}%, VIX<${run.vixThreshold ?? '?'})`;
  }
  return STRATEGY_LABELS[type];
}

function isoDate(d: Date): string {
  return d.toISOString().slice(0, 10);
}
function yearsAgo(years: number): string {
  const d = new Date();
  d.setFullYear(d.getFullYear() - years);
  return isoDate(d);
}
const TODAY_ISO = isoDate(new Date());

type ResultTab = 'strategy' | 'returns' | 'scatter' | 'history';

interface BacktestRunRow extends BacktestRunResponse, Record<string, unknown> {}

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

function MetricStat({ label, value, color }: { label: string; value: string; color?: string }) {
  return (
    <VStack gap={1}>
      <Text type="supporting" size="sm">
        {label}
      </Text>
      <Heading level={4} style={color ? { color } : undefined}>
        {value}
      </Heading>
    </VStack>
  );
}

function pct(value: number | null): string {
  return value != null ? `${value.toFixed(2)}%` : '—';
}

function money(value: number): string {
  return `${Math.round(value).toLocaleString('ko-KR')}원`;
}

/** 국내 증시 표기 관행(상승 = 빨강, 하락 = 파랑)을 따름 — PriceChangeIndicator와 동일 컨벤션. */
function pctColor(value: number | null): string | undefined {
  if (value == null || value === 0) return undefined;
  return value > 0 ? 'var(--color-text-red)' : 'var(--color-text-blue)';
}

export default function BacktestPage() {
  const { authFetch } = useAuth();

  const [name, setName] = useState('나의 전략');
  const [tickers, setTickers] = useState<string[]>(['AAPL', 'MSFT']);
  const [newTicker, setNewTicker] = useState('');
  const [dateRange, setDateRange] = useState<DateRange | null>({ start: yearsAgo(3), end: TODAY_ISO } as DateRange);
  const [initialCapital, setInitialCapital] = useState<number | null>(DEFAULT_INITIAL_CAPITAL);
  const [riskFreeRatePct, setRiskFreeRatePct] = useState<number | null>(DEFAULT_RISK_FREE_RATE_PCT);
  const [strategyType, setStrategyType] = useState<BacktestStrategyType>('BUY_AND_HOLD');
  const [smaShortWindow, setSmaShortWindow] = useState<number | null>(DEFAULT_SMA_SHORT_WINDOW);
  const [smaLongWindow, setSmaLongWindow] = useState<number | null>(DEFAULT_SMA_LONG_WINDOW);
  const [rebalanceFrequency, setRebalanceFrequency] = useState<RebalanceFrequency>('MONTHLY');
  const [targetVolatilityPct, setTargetVolatilityPct] = useState<number | null>(DEFAULT_TARGET_VOLATILITY_PCT);
  const [vixThreshold, setVixThreshold] = useState<number | null>(DEFAULT_VIX_THRESHOLD);

  const [isRunning, setIsRunning] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [activeRun, setActiveRun] = useState<BacktestRunResponse | null>(null);
  const [activeTab, setActiveTab] = useState<ResultTab>('strategy');

  const [pastRuns, setPastRuns] = useState<BacktestRunResponse[]>([]);
  const [isLoadingRuns, setIsLoadingRuns] = useState(true);

  useEffect(() => {
    api
      .getBacktestRuns(authFetch)
      .then(setPastRuns)
      .finally(() => setIsLoadingRuns(false));
  }, [authFetch]);

  function addTicker() {
    const t = newTicker.trim().toUpperCase();
    if (!t || tickers.includes(t) || tickers.length >= MAX_TICKERS) return;
    setTickers((prev) => [...prev, t]);
    setNewTicker('');
  }

  function removeTicker(ticker: string) {
    setTickers((prev) => prev.filter((t) => t !== ticker));
  }

  const isSmaRangeValid = smaShortWindow != null && smaLongWindow != null && smaShortWindow > 0 && smaShortWindow < smaLongWindow;
  const isVolTargetValid = targetVolatilityPct != null && targetVolatilityPct > 0 && vixThreshold != null && vixThreshold > 0;
  const isStrategyParamsValid =
    (strategyType !== 'SMA_CROSSOVER' || isSmaRangeValid) && (strategyType !== 'VOLATILITY_TARGET' || isVolTargetValid);

  async function handleRun() {
    if (tickers.length === 0 || !dateRange || initialCapital == null || riskFreeRatePct == null || !isStrategyParamsValid) return;
    setIsRunning(true);
    setError(null);
    try {
      const run = await api.runBacktest(authFetch, {
        name,
        tickers,
        startDate: dateRange.start,
        endDate: dateRange.end,
        initialCapital,
        riskFreeRate: riskFreeRatePct / 100,
        strategyType,
        ...(strategyType === 'SMA_CROSSOVER' && smaShortWindow != null && smaLongWindow != null
          ? { smaShortWindow, smaLongWindow }
          : {}),
        ...(strategyType === 'PERIODIC_REBALANCE' ? { rebalanceFrequency } : {}),
        ...(strategyType === 'VOLATILITY_TARGET' && targetVolatilityPct != null && vixThreshold != null
          ? { targetVolatilityPct, vixThreshold }
          : {}),
      });
      setActiveRun(run);
      setPastRuns((prev) => [run, ...prev]);
      if (run.status === 'FAILED') {
        setError(run.errorMessage ?? '백테스트 실행에 실패했습니다');
      } else {
        setActiveTab('returns');
      }
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '백테스트 실행에 실패했습니다');
    } finally {
      setIsRunning(false);
    }
  }

  function viewRun(run: BacktestRunResponse) {
    setActiveRun(run);
    setActiveTab('returns');
  }

  const result = activeRun?.result ?? null;

  const cumulativeReturnSeries = useMemo(() => {
    if (!result || result.equityCurve.length === 0) return null;
    const firstPortfolio = result.equityCurve[0].portfolioValue;
    const firstBenchmark = result.equityCurve[0].benchmarkValue;
    return {
      categories: result.equityCurve.map((p) => p.date),
      portfolio: result.equityCurve.map((p) => (p.portfolioValue / firstPortfolio - 1) * 100),
      benchmark: result.equityCurve.map((p) => (p.benchmarkValue / firstBenchmark - 1) * 100),
    };
  }, [result]);

  const scatterPoints: ScatterPoint[] = useMemo(() => {
    if (!result) return [];
    const points: ScatterPoint[] = (result.tickerStats ?? [])
      .filter((s) => s.volatilityPct != null)
      .map((s) => ({ label: s.ticker, returnPct: s.returnPct, volatilityPct: s.volatilityPct as number, color: 'var(--color-icon-teal)' }));
    if (result.metrics.volatilityPct != null) {
      points.push({
        label: activeRun?.name ?? '내 전략',
        returnPct: result.metrics.totalReturnPct,
        volatilityPct: result.metrics.volatilityPct,
        color: 'var(--color-icon-blue)',
      });
    }
    if (result.benchmarkStats?.volatilityPct != null) {
      points.push({
        label: `벤치마크 (${result.benchmarkStats.ticker})`,
        returnPct: result.benchmarkStats.returnPct,
        volatilityPct: result.benchmarkStats.volatilityPct,
        color: 'var(--color-icon-orange)',
      });
    }
    return points;
  }, [result, activeRun]);

  const runColumns: TableColumn<BacktestRunRow>[] = [
    { key: 'name', header: '이름', width: proportional(1.2), renderCell: (row) => <Text type="body">{row.name}</Text> },
    {
      key: 'tickers',
      header: '종목',
      width: proportional(1.4),
      renderCell: (row) => <Text type="body">{row.tickers.join(', ')}</Text>,
    },
    {
      key: 'period',
      header: '기간',
      width: proportional(1.2),
      renderCell: (row) => (
        <Text type="body">
          {row.startDate} ~ {row.endDate}
        </Text>
      ),
    },
    {
      key: 'strategy',
      header: '전략',
      width: proportional(1.2),
      renderCell: (row) => <Text type="body">{strategySummary(row)}</Text>,
    },
    {
      key: 'totalReturnPct',
      header: '총수익률',
      width: proportional(0.8),
      renderCell: (row) => <Text type="body">{row.status === 'DONE' ? pct(row.result?.metrics.totalReturnPct ?? null) : '—'}</Text>,
    },
    {
      key: 'status',
      header: '상태',
      width: proportional(0.6),
      renderCell: (row) => (
        <Text type="body" style={row.status === 'FAILED' ? { color: 'var(--color-text-red)' } : undefined}>
          {row.status === 'DONE' ? '완료' : row.status === 'FAILED' ? '실패' : '대기'}
        </Text>
      ),
    },
    {
      key: 'view',
      header: '',
      width: proportional(0.5),
      renderCell: (row) => (
        <Button variant="ghost" size="sm" label="결과 보기" isDisabled={row.status !== 'DONE'} clickAction={() => viewRun(row)} />
      ),
    },
  ];

  return (
    <VStack gap={0}>
      <Section padding={4} dividers={['bottom']}>
        <Heading level={3}>백테스팅</Heading>
      </Section>

      <Section padding={4}>
        <VStack gap={4}>
          {result && (
            <PanelCard title={`결과 — ${activeRun?.name}`}>
              <VStack gap={3}>
                <Text type="supporting" size="sm">
                  전략: {activeRun ? strategySummary(activeRun) : '—'}
                </Text>
                <Grid columns={4} gap={4}>
                  <MetricStat
                    label="총수익률"
                    value={pct(result.metrics.totalReturnPct)}
                    color={pctColor(result.metrics.totalReturnPct)}
                  />
                  <MetricStat label="CAGR" value={pct(result.metrics.cagrPct)} color={pctColor(result.metrics.cagrPct)} />
                  <MetricStat
                    label="MDD"
                    value={pct(result.metrics.maxDrawdownPct)}
                    color={pctColor(result.metrics.maxDrawdownPct)}
                  />
                  <MetricStat label="변동성 (연율화)" value={pct(result.metrics.volatilityPct)} />
                  <MetricStat
                    label="샤프비율"
                    value={result.metrics.sharpeRatio != null ? result.metrics.sharpeRatio.toFixed(2) : '—'}
                  />
                  <MetricStat label="투자원금" value={money(activeRun?.initialCapital ?? 0)} />
                  <MetricStat
                    label="총손익"
                    value={money(result.equityCurve[result.equityCurve.length - 1].portfolioValue - (activeRun?.initialCapital ?? 0))}
                    color={pctColor(result.metrics.totalReturnPct)}
                  />
                  <MetricStat label="현재 자산" value={money(result.equityCurve[result.equityCurve.length - 1].portfolioValue)} />
                </Grid>
              </VStack>
            </PanelCard>
          )}

          <TabList value={activeTab} onChange={(value) => setActiveTab(value as ResultTab)}>
            <Tab value="strategy" label="전략 설정" />
            <Tab value="returns" label="수익률" />
            <Tab value="scatter" label="종목 비교" />
            <Tab value="history" label="실행 이력" />
          </TabList>

          {activeTab === 'strategy' && (
            <PanelCard title="전략 설정">
              <VStack gap={4}>
                <TextInput label="이름" value={name} onChange={setName} />

                <VStack gap={2}>
                  <Text type="label">종목 (최대 {MAX_TICKERS}개)</Text>
                  <HStack gap={2} wrap="wrap" align="center">
                    {tickers.map((ticker) => (
                      <HStack key={ticker} gap={1} align="center">
                        <Text type="body">{ticker}</Text>
                        <IconButton
                          variant="ghost"
                          size="sm"
                          icon={<Icon icon={XMarkIcon} />}
                          label={`${ticker} 제거`}
                          clickAction={() => removeTicker(ticker)}
                        />
                      </HStack>
                    ))}
                    <TextInput
                      label="티커 추가"
                      isLabelHidden
                      placeholder="예: AAPL"
                      value={newTicker}
                      onChange={setNewTicker}
                    />
                    <Button
                      variant="secondary"
                      label="추가"
                      isDisabled={!newTicker.trim() || tickers.length >= MAX_TICKERS}
                      clickAction={addTicker}
                    />
                  </HStack>
                </VStack>

                <Grid columns={3} gap={4}>
                  <DateRangeInput label="백테스트 기간" value={dateRange} onChange={setDateRange} max={TODAY_ISO as never} />
                  <NumberInput label="초기 자본" value={initialCapital} onChange={setInitialCapital} min={0} step={100000} units="$" />
                  <NumberInput
                    label="무위험 이자율"
                    value={riskFreeRatePct}
                    onChange={setRiskFreeRatePct}
                    min={0}
                    max={100}
                    step={0.1}
                    units="%"
                  />
                </Grid>

                <VStack gap={2}>
                  <Text type="label">전략 유형</Text>
                  <SegmentedControl value={strategyType} onChange={(v) => setStrategyType(v as BacktestStrategyType)} label="전략 유형">
                    <SegmentedControlItem value="BUY_AND_HOLD" label={STRATEGY_LABELS.BUY_AND_HOLD} />
                    <SegmentedControlItem value="SMA_CROSSOVER" label={STRATEGY_LABELS.SMA_CROSSOVER} />
                    <SegmentedControlItem value="PERIODIC_REBALANCE" label={STRATEGY_LABELS.PERIODIC_REBALANCE} />
                    <SegmentedControlItem value="VOLATILITY_TARGET" label={STRATEGY_LABELS.VOLATILITY_TARGET} />
                  </SegmentedControl>

                  {strategyType === 'BUY_AND_HOLD' && (
                    <Text type="supporting" size="sm">
                      동일 비중으로 매수 후 보유합니다. 리밸런싱 없음.
                    </Text>
                  )}

                  {strategyType === 'SMA_CROSSOVER' && (
                    <VStack gap={2}>
                      <Grid columns={2} gap={4}>
                        <NumberInput
                          label="단기 이동평균(일)"
                          value={smaShortWindow}
                          onChange={setSmaShortWindow}
                          min={1}
                          step={1}
                        />
                        <NumberInput
                          label="장기 이동평균(일)"
                          value={smaLongWindow}
                          onChange={setSmaLongWindow}
                          min={1}
                          step={1}
                        />
                      </Grid>
                      <Text type="supporting" size="sm" style={isSmaRangeValid ? undefined : { color: 'var(--color-text-red)' }}>
                        {isSmaRangeValid
                          ? '종목별로 단기 이평선이 장기 이평선을 상향 돌파(골든크로스)하면 다음 날부터 편입하고, 하향 돌파하면 현금으로 전환합니다.'
                          : '단기 이동평균 일수는 장기 이동평균 일수보다 작아야 합니다.'}
                      </Text>
                    </VStack>
                  )}

                  {strategyType === 'PERIODIC_REBALANCE' && (
                    <VStack gap={2}>
                      <SegmentedControl
                        value={rebalanceFrequency}
                        onChange={(v) => setRebalanceFrequency(v as RebalanceFrequency)}
                        label="리밸런싱 주기"
                      >
                        <SegmentedControlItem value="MONTHLY" label={REBALANCE_FREQUENCY_LABELS.MONTHLY} />
                        <SegmentedControlItem value="QUARTERLY" label={REBALANCE_FREQUENCY_LABELS.QUARTERLY} />
                        <SegmentedControlItem value="YEARLY" label={REBALANCE_FREQUENCY_LABELS.YEARLY} />
                      </SegmentedControl>
                      <Text type="supporting" size="sm">
                        선택한 주기마다 동일 비중으로 다시 맞춥니다(리밸런싱).
                      </Text>
                    </VStack>
                  )}

                  {strategyType === 'VOLATILITY_TARGET' && (
                    <VStack gap={2}>
                      <Grid columns={2} gap={4}>
                        <NumberInput
                          label="목표 변동성(연율화, %)"
                          value={targetVolatilityPct}
                          onChange={setTargetVolatilityPct}
                          min={0.1}
                          step={1}
                          units="%"
                        />
                        <NumberInput
                          label="VIX 비상탈출 임계값"
                          value={vixThreshold}
                          onChange={setVixThreshold}
                          min={0.1}
                          step={1}
                        />
                      </Grid>
                      <Text
                        type="supporting"
                        size="sm"
                        style={isVolTargetValid ? undefined : { color: 'var(--color-text-red)' }}
                      >
                        {isVolTargetValid
                          ? '벤치마크(SPY)가 자체 200일 이동평균보다 낮거나 VIX가 임계값 이상이면 전량 현금으로 전환합니다. 그 외에는 최근 20일 실현 변동성 대비 목표 변동성 비율만큼 편입하되 최대 100%를 넘지 않습니다.'
                          : '목표 변동성과 VIX 임계값은 0보다 커야 합니다.'}
                      </Text>
                    </VStack>
                  )}
                </VStack>

                {error && <Banner status="error" title="실행 실패" description={error} />}

                <Button
                  variant="primary"
                  label="백테스트 실행"
                  isLoading={isRunning}
                  isDisabled={tickers.length === 0 || !dateRange || !isStrategyParamsValid}
                  clickAction={handleRun}
                />
                <Text type="supporting" size="sm">
                  벤치마크(SPY)와 비교합니다. 파라미터를 바꾸고 다시 실행하면 아래 결과와 차트가 갱신됩니다.
                </Text>
              </VStack>
            </PanelCard>
          )}

          {activeTab === 'returns' &&
            (isRunning ? (
              <Center height={200}>
                <Spinner size="lg" label="백테스트 실행 중" />
              </Center>
            ) : result && cumulativeReturnSeries ? (
              <PanelCard title="누적 수익률 비교 (전략 vs 벤치마크)">
                <MultiLineChart
                  categories={cumulativeReturnSeries.categories}
                  series={[
                    { label: activeRun?.name ?? '전략', color: 'var(--color-icon-blue)', values: cumulativeReturnSeries.portfolio },
                    {
                      label: `벤치마크 (${result.benchmarkStats?.ticker ?? 'SPY'})`,
                      color: 'var(--color-icon-orange)',
                      values: cumulativeReturnSeries.benchmark,
                    },
                  ]}
                  width={900}
                  height={340}
                  valueFormatter={(v) => `${v.toFixed(2)}%`}
                />
              </PanelCard>
            ) : (
              <Center height={160}>
                <Text type="body" color="secondary">
                  &apos;전략 설정&apos; 탭에서 백테스트를 실행하면 결과가 여기에 표시됩니다
                </Text>
              </Center>
            ))}

          {activeTab === 'scatter' &&
            (result ? (
              <PanelCard title="종목 · 전략 · 벤치마크 — 수익률 대비 변동성">
                <ScatterChart points={scatterPoints} width={900} height={420} />
              </PanelCard>
            ) : (
              <Center height={160}>
                <Text type="body" color="secondary">
                  &apos;전략 설정&apos; 탭에서 백테스트를 실행하면 결과가 여기에 표시됩니다
                </Text>
              </Center>
            ))}

          {activeTab === 'history' && (
            <PanelCard title="지난 백테스트 실행 이력">
              {isLoadingRuns ? (
                <Center height={120}>
                  <Spinner size="md" label="불러오는 중" />
                </Center>
              ) : pastRuns.length === 0 ? (
                <Text type="body" color="secondary">
                  아직 실행한 백테스트가 없습니다
                </Text>
              ) : (
                <Table data={pastRuns as BacktestRunRow[]} columns={runColumns} idKey="id" hasHover />
              )}
            </PanelCard>
          )}
        </VStack>
      </Section>
    </VStack>
  );
}
