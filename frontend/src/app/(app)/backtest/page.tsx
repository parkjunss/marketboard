'use client';

import { useEffect, useMemo, useState } from 'react';
import { VStack } from '@astryxdesign/core/Stack';
import { Section } from '@astryxdesign/core/Section';
import { Card } from '@astryxdesign/core/Card';
import { Heading, Text } from '@astryxdesign/core/Text';
import type { DateRange } from '@astryxdesign/core/DateRangeInput';
import { Button } from '@astryxdesign/core/Button';
import { Table, proportional } from '@astryxdesign/core/Table';
import type { TableColumn } from '@astryxdesign/core/Table';
import { Center } from '@astryxdesign/core/Center';
import { Spinner } from '@astryxdesign/core/Spinner';
import { TabList, Tab } from '@astryxdesign/core/TabList';
import { ScatterChart, type ScatterPoint } from '@/components/charts/ScatterChart';
import { BacktestWorkbench } from '@/components/backtest/BacktestWorkbench';
import { StrategySetupCard } from '@/components/backtest/StrategySetupCard';
import { useAuth } from '@/lib/auth-context';
import { ApiError } from '@/lib/api';
import * as api from '@/lib/api';
import type { BacktestRunResponse, BacktestStrategyType, RebalanceFrequency } from '@/lib/types';
import styles from './backtest.module.css';

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

type ResultTab = 'scatter' | 'history';

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

function pct(value: number | null): string {
  return value != null ? `${value.toFixed(2)}%` : '—';
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
  const [activeTab, setActiveTab] = useState<ResultTab>('scatter');

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
        setActiveTab('scatter');
      }
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '백테스트 실행에 실패했습니다');
    } finally {
      setIsRunning(false);
    }
  }

  function viewRun(run: BacktestRunResponse) {
    setActiveRun(run);
    setActiveTab('scatter');
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
          <StrategySetupCard
            name={name}
            setName={setName}
            tickers={tickers}
            newTicker={newTicker}
            setNewTicker={setNewTicker}
            addTicker={addTicker}
            removeTicker={removeTicker}
            maxTickers={MAX_TICKERS}
            dateRange={dateRange}
            setDateRange={(value) => setDateRange(value as DateRange | null)}
            today={TODAY_ISO}
            initialCapital={initialCapital}
            setInitialCapital={setInitialCapital}
            riskFreeRatePct={riskFreeRatePct}
            setRiskFreeRatePct={setRiskFreeRatePct}
            strategyType={strategyType}
            setStrategyType={setStrategyType}
            smaShortWindow={smaShortWindow}
            setSmaShortWindow={setSmaShortWindow}
            smaLongWindow={smaLongWindow}
            setSmaLongWindow={setSmaLongWindow}
            rebalanceFrequency={rebalanceFrequency}
            setRebalanceFrequency={setRebalanceFrequency}
            targetVolatilityPct={targetVolatilityPct}
            setTargetVolatilityPct={setTargetVolatilityPct}
            vixThreshold={vixThreshold}
            setVixThreshold={setVixThreshold}
            isSmaRangeValid={isSmaRangeValid}
            isVolTargetValid={isVolTargetValid}
            isStrategyParamsValid={isStrategyParamsValid}
            error={error}
            isRunning={isRunning}
            onRun={handleRun}
          />

          <BacktestWorkbench
            tickers={tickers}
            startDate={dateRange?.start}
            endDate={dateRange?.end}
            run={activeRun}
            result={result}
            returnSeries={cumulativeReturnSeries}
          />

          <div className={styles.resultTabs}>
          <TabList value={activeTab} onChange={(value) => setActiveTab(value as ResultTab)}>
            <Tab value="scatter" label="종목 비교" />
            <Tab value="history" label="실행 이력" />
          </TabList>
          </div>

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
