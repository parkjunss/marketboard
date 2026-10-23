'use client';

import { DataStamp } from '@/components/DataStamp';


import { useResource } from '@/lib/use-resource';
import { ResourceState } from '@/components/ResourceState';

import { Card } from '@astryxdesign/core/Card';
import { VStack } from '@astryxdesign/core/Stack';
import { Section } from '@astryxdesign/core/Section';
import { Heading, Text } from '@astryxdesign/core/Text';
import { Table, proportional } from '@astryxdesign/core/Table';
import type { TableColumn } from '@astryxdesign/core/Table';
import * as api from '@/lib/api';
import type { SectorPerformance } from '@/lib/types';

interface SectorRow extends SectorPerformance, Record<string, unknown> {}

function pctCell(value: number | null) {
  if (value == null) {
    return <Text type="body">—</Text>;
  }
  const isUp = value >= 0;
  return (
    <Text type="body" hasTabularNumbers style={{ color: isUp ? 'var(--color-text-red)' : 'var(--color-text-blue)' }}>
      {isUp ? '+' : ''}
      {value.toFixed(2)}%
    </Text>
  );
}

const columns: TableColumn<SectorRow>[] = [
  { key: 'name', header: '섹터', width: proportional(1.6), renderCell: (row) => <Text type="body">{row.name}</Text> },
  { key: 'changePct1d', header: '1일', width: proportional(1), renderCell: (row) => pctCell(row.changePct1d) },
  { key: 'changePct1w', header: '1주', width: proportional(1), renderCell: (row) => pctCell(row.changePct1w) },
  { key: 'changePct1m', header: '1개월', width: proportional(1), renderCell: (row) => pctCell(row.changePct1m) },
];

export function SectorRotationTable({ showTitle = true }: { showTitle?: boolean }) {
  const resource = useResource(api.getSectorPerformance);
  if (resource.status !== 'success') return <ResourceState status={resource.status} label="섹터 로테이션" onRetry={resource.retry} />;
  const sectors = resource.data;
  if (sectors.length === 0) return <ResourceState status="empty" label="섹터 로테이션" />;

  return (
    <Card padding={0}>
      <VStack gap={0}>
        <Section padding={3} dividers={['bottom']}>
          <VStack gap={1}>
            {showTitle && <Heading level={5}>섹터 로테이션</Heading>}
            <Text type="supporting" size="sm">
              GICS 11개 섹터 SPDR ETF의 기간별 수익률 — 1개월 수익률 기준 정렬
            </Text>
          </VStack>
        </Section>
        <Section padding={3}><DataStamp source="Yahoo Finance (yfinance) · SPDR ETF" note="일봉 수익률 · 종목별 기준 시각 미제공" /></Section>
        <Section padding={0}>
          <Table data={sectors as SectorRow[]} columns={columns} idKey="slug" hasHover />
        </Section>
      </VStack>
    </Card>
  );
}
