'use client';

import { use, useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { VStack, HStack } from '@astryxdesign/core/Stack';
import { Section } from '@astryxdesign/core/Section';
import { Card } from '@astryxdesign/core/Card';
import { Grid } from '@astryxdesign/core/Grid';
import { Heading, Text } from '@astryxdesign/core/Text';
import { TextInput } from '@astryxdesign/core/TextInput';
import { Button } from '@astryxdesign/core/Button';
import { Link } from '@astryxdesign/core/Link';
import { Center } from '@astryxdesign/core/Center';
import { Spinner } from '@astryxdesign/core/Spinner';
import { EmptyState } from '@astryxdesign/core/EmptyState';
import { MultiLineChart } from '@/components/charts/MultiLineChart';
import { GroupedBarChart } from '@/components/charts/GroupedBarChart';
import { useAuth } from '@/lib/auth-context';
import * as api from '@/lib/api';
import type { FinancialsResponse } from '@/lib/types';

const COLORS = {
  revenue: 'var(--color-icon-blue)',
  grossProfit: 'var(--color-icon-teal)',
  operatingIncome: 'var(--color-icon-purple)',
  netIncome: 'var(--color-icon-green)',
  revenueGrowth: 'var(--color-icon-blue)',
  operatingIncomeGrowth: 'var(--color-icon-purple)',
  roe: 'var(--color-icon-green)',
  roa: 'var(--color-icon-orange)',
  operatingCashFlow: 'var(--color-icon-blue)',
  freeCashFlow: 'var(--color-icon-green)',
  grossMargin: 'var(--color-icon-teal)',
  operatingMargin: 'var(--color-icon-purple)',
  netMargin: 'var(--color-icon-green)',
  pe: 'var(--color-icon-red)',
  pb: 'var(--color-icon-green)',
  ps: 'var(--color-icon-teal)',
};

function toBillions(value: number | null): number | null {
  return value == null ? null : value / 1_000_000_000;
}

function kpiText(value: number | null, formatter: (v: number) => string): string {
  return value == null ? '—' : formatter(value);
}

function ChartCard({ title, children }: { title: string; children: React.ReactNode }) {
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

export default function FinancialsDetailPage({ params }: { params: Promise<{ ticker: string }> }) {
  const { ticker: rawTicker } = use(params);
  const ticker = rawTicker.toUpperCase();

  const router = useRouter();
  const { authFetch } = useAuth();
  const [tickerInput, setTickerInput] = useState(ticker);
  const [result, setResult] = useState<{ key: string; data: FinancialsResponse } | null>(null);
  const isLoading = result?.key !== ticker;

  useEffect(() => {
    let cancelled = false;
    api.getFinancials(authFetch, ticker).then((data) => {
      if (!cancelled) setResult({ key: ticker, data });
    });
    return () => {
      cancelled = true;
    };
  }, [authFetch, ticker]);

  const data = result?.key === ticker ? result.data : null;
  const years = data?.years ?? [];
  const hasYears = years.length > 0;

  return (
    <VStack gap={0}>
      <Section padding={4} dividers={['bottom']}>
        <HStack justify="between" align="center" wrap="wrap">
          <VStack gap={1}>
            <HStack gap={2} align="center">
              <Heading level={3}>재무 대시보드</Heading>
              <Link href="/financials">← 종목 비교로 돌아가기</Link>
            </HStack>
            <Text type="supporting" size="sm">
              yfinance 연간 재무제표 기반 현금흐름·수익성 분석 (실시간 아님, 연 단위 공시 데이터)
            </Text>
          </VStack>
          <HStack gap={2} align="end">
            <TextInput
              label="종목"
              size="sm"
              placeholder="AAPL"
              value={tickerInput}
              onChange={(value) => setTickerInput(value.toUpperCase())}
            />
            <Button
              variant="primary"
              label="조회"
              clickAction={() => router.push(`/financials/${tickerInput}`)}
            />
          </HStack>
        </HStack>
      </Section>

      <Section padding={4}>
        {isLoading ? (
          <Center height={400}>
            <Spinner size="lg" label="불러오는 중" />
          </Center>
        ) : !data || !hasYears ? (
          <Center height={400}>
            <EmptyState title="재무 데이터가 없습니다" description="종목 코드를 확인하고 다시 조회해보세요" />
          </Center>
        ) : (
          <VStack gap={4}>
            <VStack gap={1}>
              <Heading level={4}>
                {data.ticker} · {data.name}
              </Heading>
            </VStack>

            <Grid columns={5} gap={3}>
              <Card padding={3}>
                <VStack gap={1}>
                  <Text type="supporting" size="sm">
                    Quick Ratio
                  </Text>
                  <Heading level={4}>{kpiText(data.kpis.quickRatio, (v) => v.toFixed(2))}</Heading>
                </VStack>
              </Card>
              <Card padding={3}>
                <VStack gap={1}>
                  <Text type="supporting" size="sm">
                    Interest Coverage
                  </Text>
                  <Heading level={4}>{kpiText(data.kpis.interestCoverage, (v) => v.toFixed(1))}</Heading>
                </VStack>
              </Card>
              <Card padding={3}>
                <VStack gap={1}>
                  <Text type="supporting" size="sm">
                    Total Debt / FCF
                  </Text>
                  <Heading level={4}>{kpiText(data.kpis.totalDebtToFcf, (v) => v.toFixed(2))}</Heading>
                </VStack>
              </Card>
              <Card padding={3}>
                <VStack gap={1}>
                  <Text type="supporting" size="sm">
                    Cash &amp; Equivalents
                  </Text>
                  <Heading level={4}>{kpiText(toBillions(data.kpis.cashAndEquivalents), (v) => `$${v.toFixed(1)}B`)}</Heading>
                </VStack>
              </Card>
              <Card padding={3}>
                <VStack gap={1}>
                  <Text type="supporting" size="sm">
                    Debt to Capital
                  </Text>
                  <Heading level={4}>{kpiText(data.kpis.debtToCapitalPct, (v) => `${v.toFixed(1)}%`)}</Heading>
                </VStack>
              </Card>
            </Grid>

            <Grid columns={3} gap={4}>
              <ChartCard title="Earnings Analysis">
                <GroupedBarChart
                  categories={years}
                  valueFormatter={(v) => `$${v.toFixed(0)}B`}
                  series={[
                    { label: 'Revenue', color: COLORS.revenue, values: data.earningsAnalysis.map((y) => toBillions(y.revenue)) },
                    {
                      label: 'Gross Profit',
                      color: COLORS.grossProfit,
                      values: data.earningsAnalysis.map((y) => toBillions(y.grossProfit)),
                    },
                    {
                      label: 'Operating Income',
                      color: COLORS.operatingIncome,
                      values: data.earningsAnalysis.map((y) => toBillions(y.operatingIncome)),
                    },
                    {
                      label: 'Net Income',
                      color: COLORS.netIncome,
                      values: data.earningsAnalysis.map((y) => toBillions(y.netIncome)),
                    },
                  ]}
                />
              </ChartCard>

              <ChartCard title="Growth Analysis">
                <MultiLineChart
                  categories={data.growthAnalysis.map((y) => y.year)}
                  valueFormatter={(v) => `${v.toFixed(0)}%`}
                  series={[
                    {
                      label: 'Revenue Growth %',
                      color: COLORS.revenueGrowth,
                      values: data.growthAnalysis.map((y) => y.revenueGrowthPct),
                    },
                    {
                      label: 'Operating Income Growth %',
                      color: COLORS.operatingIncomeGrowth,
                      values: data.growthAnalysis.map((y) => y.operatingIncomeGrowthPct),
                    },
                  ]}
                />
              </ChartCard>

              <ChartCard title="Profitability Analysis">
                <MultiLineChart
                  categories={years}
                  valueFormatter={(v) => `${v.toFixed(0)}%`}
                  series={[
                    { label: 'ROE %', color: COLORS.roe, values: data.profitabilityAnalysis.map((y) => y.roePct) },
                    { label: 'ROA %', color: COLORS.roa, values: data.profitabilityAnalysis.map((y) => y.roaPct) },
                  ]}
                />
              </ChartCard>

              <ChartCard title="Cash Flow Analysis">
                <GroupedBarChart
                  categories={years}
                  valueFormatter={(v) => `$${v.toFixed(0)}B`}
                  series={[
                    {
                      label: 'Operating Cash Flow',
                      color: COLORS.operatingCashFlow,
                      values: data.cashFlowAnalysis.map((y) => toBillions(y.operatingCashFlow)),
                    },
                    {
                      label: 'Free Cash Flow',
                      color: COLORS.freeCashFlow,
                      values: data.cashFlowAnalysis.map((y) => toBillions(y.freeCashFlow)),
                    },
                  ]}
                />
              </ChartCard>

              <ChartCard title="Margins Analysis">
                <MultiLineChart
                  categories={years}
                  valueFormatter={(v) => `${v.toFixed(0)}%`}
                  series={[
                    { label: 'Gross Margin %', color: COLORS.grossMargin, values: data.marginsAnalysis.map((y) => y.grossMarginPct) },
                    {
                      label: 'Operating Margin %',
                      color: COLORS.operatingMargin,
                      values: data.marginsAnalysis.map((y) => y.operatingMarginPct),
                    },
                    { label: 'Net Margin %', color: COLORS.netMargin, values: data.marginsAnalysis.map((y) => y.netMarginPct) },
                  ]}
                />
              </ChartCard>

              <ChartCard title="Market Analysis">
                <MultiLineChart
                  categories={years}
                  valueFormatter={(v) => v.toFixed(1)}
                  series={[
                    { label: 'P/E Ratio', color: COLORS.pe, values: data.marketAnalysis.map((y) => y.peRatio) },
                    { label: 'P/B Ratio', color: COLORS.pb, values: data.marketAnalysis.map((y) => y.pbRatio) },
                    { label: 'P/S Ratio', color: COLORS.ps, values: data.marketAnalysis.map((y) => y.psRatio) },
                  ]}
                />
              </ChartCard>
            </Grid>
          </VStack>
        )}
      </Section>
    </VStack>
  );
}
