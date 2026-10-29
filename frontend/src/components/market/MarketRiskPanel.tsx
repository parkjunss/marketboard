'use client';

import { DataStamp } from '@/components/DataStamp';
import { useResource } from '@/lib/use-resource';
import { ResourceState } from '@/components/ResourceState';

import { Card } from '@astryxdesign/core/Card';
import { HStack, VStack } from '@astryxdesign/core/Stack';
import { Section } from '@astryxdesign/core/Section';
import { StatusDot } from '@astryxdesign/core/StatusDot';
import { Text } from '@astryxdesign/core/Text';
import { Table, proportional } from '@astryxdesign/core/Table';
import type { TableColumn } from '@astryxdesign/core/Table';
import * as api from '@/lib/api';
import type { MarketRiskAsset, MarketRiskLevel } from '@/lib/types';

interface RiskRow extends MarketRiskAsset, Record<string, unknown> {}

const LEVELS: Record<MarketRiskLevel, { label: string; variant: 'success' | 'warning' | 'error' | 'neutral' }> = {
  NORMAL: { label: '정상', variant: 'success' },
  CAUTION: { label: '주의', variant: 'warning' },
  RISK: { label: '위험', variant: 'error' },
  UNAVAILABLE: { label: '미제공', variant: 'neutral' },
};

function level(state: MarketRiskLevel) {
  const { label, variant } = LEVELS[state];
  return <HStack gap={2} align="center"><StatusDot variant={variant} label={label} /><Text type="body">{label}</Text></HStack>;
}

function pct(value: number | null, digits = 1, suffix = '%') {
  return <Text type="body" hasTabularNumbers>{value == null || !Number.isFinite(value) ? '—' : `${value.toFixed(digits)}${suffix}`}</Text>;
}

// Floor, not round: 69.6 must not read as "70" while the state (cut-off 70) is still NORMAL.
function rank(percentile: number | null) {
  return percentile == null || !Number.isFinite(percentile) ? '—' : `${Math.floor(percentile)}번째 백분위`;
}

function pair(main: React.ReactNode, sub: string) {
  return <VStack gap={0}>{main}<Text type="supporting" size="sm" hasTabularNumbers>{sub}</Text></VStack>;
}

// Four columns so the state and its percentile stay in the first mobile viewport of the table.
const columns: TableColumn<RiskRow>[] = [
  { key: 'name', header: '자산', width: proportional(1.4), renderCell: (row) => pair(<Text type="body">{row.name}</Text>, `${row.ticker}${row.status === 'STALE' ? ' · 최신 거래일 아님' : ''}`) },
  { key: 'state', header: '상태 · 과거 대비', width: proportional(1.2), renderCell: (row) => pair(level(row.state), rank(row.percentile)) },
  { key: 'volatility20Pct', header: '20일 변동성', width: proportional(1), renderCell: (row) => pair(pct(row.volatility20Pct), `장기 중앙값 ${row.medianVolatilityPct?.toFixed(1) ?? '—'}%`) },
  { key: 'drawdown252Pct', header: '1년 고점 대비', width: proportional(1), renderCell: (row) => pct(row.drawdown252Pct == null ? null : -row.drawdown252Pct) },
];

export function MarketRiskPanel() {
  const resource = useResource(api.getMarketRiskState);
  if (resource.status !== 'success') return <ResourceState status={resource.status} label="시장 위험 상태" onRetry={resource.retry} />;
  const { assets, counts, thresholds, evidence, expectedTradingDate, fetchedAt } = resource.data;
  if (assets.length === 0) return <ResourceState status="empty" label="시장 위험 상태" />;
  const spy = assets.find(asset => asset.ticker === 'SPY');
  const rows = [...assets].sort((a, b) => (b.percentile ?? -1) - (a.percentile ?? -1)) as RiskRow[];
  const { development, validation, holdout } = evidence.medianSpearman;

  return (
    <Card padding={0}>
      <VStack gap={0}>
        <Section padding={3} dividers={['bottom']}>
          <VStack gap={2}>
            {spy && <VStack gap={0.5}>
              <HStack gap={2} align="center"><Text type="body">S&P 500(SPY)</Text>{level(spy.state)}</HStack>
              <Text type="supporting" size="sm">20일 변동성 {spy.volatility20Pct?.toFixed(1) ?? '—'}% · 과거 {rank(spy.percentile)}</Text>
            </VStack>}
            <Text type="supporting" size="sm" aria-live="polite">
              15개 ETF 중 정상 {counts.NORMAL} · 주의 {counts.CAUTION} · 위험 {counts.RISK}{counts.UNAVAILABLE ? ` · 미제공 ${counts.UNAVAILABLE}` : ''}
            </Text>
            <Text type="supporting" size="sm">
              최근 20일 변동성이 자산의 과거 기록 중 몇 번째 백분위인지 보여줍니다. {thresholds.caution} 이상은 주의, {thresholds.risk} 이상은 위험으로 표시합니다.
              이 경계값은 검증된 값이 아니라 보기 편하게 정한 기준입니다.
            </Text>
            <Text type="supporting" size="sm">
              검증 근거: {evidence.period}년 {evidence.assets}개 ETF에서 20일 변동성은 다음 21거래일 변동성과 순위 상관 중앙값이
              개발 {development.toFixed(2)} · 검증 {validation.toFixed(2)} · 보류 {holdout.toFixed(2)}였습니다.
              흔들림의 크기를 알려줄 뿐 오를지 내릴지는 예측하지 않으며, 이 상태로 자동 매매하지 않습니다.
            </Text>
          </VStack>
        </Section>
        <Section padding={3}><DataStamp source="Yahoo Finance (yfinance) · 조정 종가" at={fetchedAt} label="계산 시각" note={`기준 거래일 ${expectedTradingDate}`} /></Section>
        <Section padding={0}>
          <Table data={rows} columns={columns} idKey="ticker" hasHover />
        </Section>
      </VStack>
    </Card>
  );
}
