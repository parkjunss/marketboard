'use client';

import { useEffect, useState } from 'react';
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
import { XMarkIcon } from '@heroicons/react/24/outline';
import { MultiLineChart } from '@/components/charts/MultiLineChart';
import { useAuth } from '@/lib/auth-context';
import { ApiError } from '@/lib/api';
import * as api from '@/lib/api';
import type { BacktestRunResponse } from '@/lib/types';

const MAX_TICKERS = 10;
const DEFAULT_INITIAL_CAPITAL = 10_000_000;
const DEFAULT_RISK_FREE_RATE_PCT = 3;

function isoDate(d: Date): string {
  return d.toISOString().slice(0, 10);
}
function yearsAgo(years: number): string {
  const d = new Date();
  d.setFullYear(d.getFullYear() - years);
  return isoDate(d);
}
const TODAY_ISO = isoDate(new Date());

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

function MetricStat({ label, value }: { label: string; value: string }) {
  return (
    <VStack gap={1}>
      <Text type="supporting" size="sm">
        {label}
      </Text>
      <Heading level={4}>{value}</Heading>
    </VStack>
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

  const [isRunning, setIsRunning] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [activeRun, setActiveRun] = useState<BacktestRunResponse | null>(null);

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

  async function handleRun() {
    if (tickers.length === 0 || !dateRange || initialCapital == null || riskFreeRatePct == null) return;
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
      });
      setActiveRun(run);
      setPastRuns((prev) => [run, ...prev]);
      if (run.status === 'FAILED') {
        setError(run.errorMessage ?? '백테스트 실행에 실패했습니다');
      }
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '백테스트 실행에 실패했습니다');
    } finally {
      setIsRunning(false);
    }
  }

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
        <Button variant="ghost" size="sm" label="결과 보기" isDisabled={row.status !== 'DONE'} clickAction={() => setActiveRun(row)} />
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

              {error && <Banner status="error" title="실행 실패" description={error} />}

              <Button
                variant="primary"
                label="백테스트 실행"
                isLoading={isRunning}
                isDisabled={tickers.length === 0 || !dateRange}
                clickAction={handleRun}
              />
              <Text type="supporting" size="sm">
                동일 비중으로 매수 후 보유(buy &amp; hold)하는 전략을 벤치마크(SPY)와 비교합니다. 리밸런싱 없음.
              </Text>
            </VStack>
          </PanelCard>

          {isRunning && (
            <Center height={200}>
              <Spinner size="lg" label="백테스트 실행 중" />
            </Center>
          )}

          {activeRun?.result && (
            <PanelCard title={`결과 — ${activeRun.name}`}>
              <VStack gap={4}>
                <Grid columns={5} gap={4}>
                  <MetricStat label="총수익률" value={pct(activeRun.result.metrics.totalReturnPct)} />
                  <MetricStat label="CAGR" value={pct(activeRun.result.metrics.cagrPct)} />
                  <MetricStat label="MDD" value={pct(activeRun.result.metrics.maxDrawdownPct)} />
                  <MetricStat label="변동성 (연율화)" value={pct(activeRun.result.metrics.volatilityPct)} />
                  <MetricStat
                    label="샤프비율"
                    value={activeRun.result.metrics.sharpeRatio != null ? activeRun.result.metrics.sharpeRatio.toFixed(2) : '—'}
                  />
                </Grid>
                <MultiLineChart
                  categories={activeRun.result.equityCurve.map((p) => p.date)}
                  series={[
                    {
                      label: '포트폴리오',
                      color: 'var(--color-icon-blue)',
                      values: activeRun.result.equityCurve.map((p) => p.portfolioValue),
                    },
                    {
                      label: '벤치마크 (SPY)',
                      color: 'var(--color-icon-purple)',
                      values: activeRun.result.equityCurve.map((p) => p.benchmarkValue),
                    },
                  ]}
                  width={900}
                  height={320}
                  valueFormatter={(v) => v.toLocaleString('ko-KR')}
                />
              </VStack>
            </PanelCard>
          )}

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
        </VStack>
      </Section>
    </VStack>
  );
}
