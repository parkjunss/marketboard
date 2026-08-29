'use client';

import { useState } from 'react';
import { Grid } from '@astryxdesign/core/Grid';
import { VStack } from '@astryxdesign/core/Stack';
import { Text, Heading } from '@astryxdesign/core/Text';
import { Button } from '@astryxdesign/core/Button';
import { Badge } from '@astryxdesign/core/Badge';
import { Center } from '@astryxdesign/core/Center';
import { Spinner } from '@astryxdesign/core/Spinner';
import { Banner } from '@astryxdesign/core/Banner';
import { FanChart } from '@/components/charts/FanChart';
import { useAuth } from '@/lib/auth-context';
import { ApiError } from '@/lib/api';
import * as api from '@/lib/api';
import type { StockAnalysisResult } from '@/lib/analysis-types';

const HURST_LABELS = {
  TRENDING: '추세지속형',
  MEAN_REVERTING: '평균회귀형',
  RANDOM_WALK: '랜덤워크',
} as const;

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <VStack gap={1}>
      <Text type="supporting" size="sm">
        {label}
      </Text>
      <Heading level={4}>{value}</Heading>
    </VStack>
  );
}

function pct(value: number | null, digits = 2): string {
  return value != null ? `${value.toFixed(digits)}%` : '—';
}

export function AnalysisPanel({ ticker }: { ticker: string }) {
  const { authFetch } = useAuth();
  const [isRunning, setIsRunning] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<StockAnalysisResult | null>(null);

  async function handleRun() {
    setIsRunning(true);
    setError(null);
    try {
      const res = await api.getStockAnalysis(authFetch, ticker);
      setResult(res);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '분석에 실패했습니다');
    } finally {
      setIsRunning(false);
    }
  }

  const monteCarlo = result?.monteCarlo;
  const categories = monteCarlo ? monteCarlo.percentiles.p50.map((_, i) => i + 1) : [];

  return (
    <VStack gap={4}>
      {!result && !isRunning && (
        <VStack gap={2}>
          <Text type="supporting" size="sm">
            변동성·VaR/CVaR·허스트 지수(추세지속형/평균회귀형 판정)·최대낙폭·벤치마크 대비 베타를 계산하고, 몬테카를로
            시뮬레이션으로 향후 가격 분포를 추정합니다. 계산에 10~20초 정도 걸릴 수 있습니다.
          </Text>
          <Button variant="primary" label="정량 분석 실행" clickAction={handleRun} />
        </VStack>
      )}

      {isRunning && (
        <Center height={200}>
          <Spinner size="lg" label="분석 중 (최대 20초)" />
        </Center>
      )}

      {error && <Banner status="error" title="분석 실패" description={error} />}

      {!isRunning && result && (
        <VStack gap={4}>
          <Text type="supporting" size="sm">
            {result.asOfDate} 기준 · 최근 {result.lookbackDays}거래일
          </Text>

          <Grid columns={4} gap={4}>
            <Stat label="변동성 (연율화)" value={pct(result.volatility.annualizedPct)} />
            <Stat label="VaR 95% / CVaR 95%" value={`${pct(result.risk['95']?.varPct ?? null)} / ${pct(result.risk['95']?.cvarPct ?? null)}`} />
            <Stat label="VaR 99% / CVaR 99%" value={`${pct(result.risk['99']?.varPct ?? null)} / ${pct(result.risk['99']?.cvarPct ?? null)}`} />
            <Stat label="최대낙폭 (MDD)" value={pct(result.drawdown.maxDrawdownPct)} />
            <Stat label="MDD 지속 기간" value={`${result.drawdown.maxDrawdownDurationDays}거래일`} />
            <Stat
              label="왜도 / 첨도"
              value={`${result.distribution.skewness?.toFixed(2) ?? '—'} / ${result.distribution.excessKurtosis?.toFixed(2) ?? '—'}`}
            />
            <Stat
              label={`${result.benchmark.ticker} 대비 베타`}
              value={result.benchmark.beta != null ? result.benchmark.beta.toFixed(2) : '—'}
            />
            <Stat
              label={`${result.benchmark.ticker} 상관계수`}
              value={result.benchmark.correlation != null ? result.benchmark.correlation.toFixed(2) : '—'}
            />
          </Grid>

          <VStack gap={1}>
            <Text type="supporting" size="sm">
              허스트 지수
            </Text>
            <VStack gap={1}>
              <Heading level={4}>{result.hurstExponent != null ? result.hurstExponent.toFixed(3) : '—'}</Heading>
              {result.hurstInterpretation && (
                <Badge
                  variant={result.hurstInterpretation === 'RANDOM_WALK' ? 'neutral' : 'info'}
                  label={HURST_LABELS[result.hurstInterpretation]}
                />
              )}
            </VStack>
          </VStack>

          {monteCarlo && (
            <VStack gap={2}>
              <Text type="label">
                몬테카를로 가격 시나리오 (향후 {monteCarlo.horizonDays}거래일, {monteCarlo.paths.toLocaleString('ko-KR')}회 시뮬레이션)
              </Text>
              <FanChart
                categories={categories}
                bands={monteCarlo.percentiles}
                referenceValue={result.lastPrice}
                width={900}
                height={340}
                valueFormatter={(v) => v.toLocaleString('ko-KR', { maximumFractionDigits: 0 })}
              />
            </VStack>
          )}

          <Button variant="secondary" label="다시 실행" isLoading={isRunning} clickAction={handleRun} />
        </VStack>
      )}
    </VStack>
  );
}
