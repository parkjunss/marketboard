'use client';

import { useState } from 'react';
import { sortSectors, type SectorPeriod } from '@/lib/sector-performance';
import styles from './sector-rotation.module.css';

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
  if (value == null || !Number.isFinite(value)) {
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
  const [period, setPeriod] = useState<SectorPeriod>('changePct1m');
  const resource = useResource(api.getSectorPerformance);
  if (resource.status !== 'success') return <ResourceState status={resource.status} label="섹터 로테이션" onRetry={resource.retry} />;
  const sectors = sortSectors(resource.data, period);
  const periods = { changePct1d: '1일', changePct1w: '1주', changePct1m: '1개월' };
  const maximum = Math.max(0, ...sectors.map(sector => Number.isFinite(sector[period]) ? Math.abs(sector[period] ?? 0) : 0));
  if (sectors.length === 0) return <ResourceState status="empty" label="섹터 로테이션" />;

  return (
    <Card padding={0}>
      <VStack gap={0}>
        <Section padding={3} dividers={['bottom']}>
          <VStack gap={1}>
            {showTitle && <Heading level={5}>섹터 로테이션</Heading>}
            <Text type="supporting" size="sm">
              GICS 11개 섹터 SPDR ETF의 기간별 수익률 · 벤치마크 대비 초과수익률이 아닙니다.
            </Text>
            <div className={styles.controls} role="group" aria-label="섹터 비교 기간">
              {(Object.keys(periods) as SectorPeriod[]).map(key => <button key={key} type="button" aria-pressed={period === key} onClick={() => setPeriod(key)}>{periods[key]}</button>)}
            </div>
            <Text type="supporting" size="sm" aria-live="polite">{periods[period]} 수익률 높은 순 · 미제공 값은 마지막 · 막대 중앙은 0%</Text>
          </VStack>
        </Section>
        <Section padding={3}><DataStamp source="Yahoo Finance (yfinance) · SPDR ETF" note="일봉 수익률 · 종목별 기준 시각 미제공" /></Section>
        <Section padding={3}>
          <ol className={styles.chart} aria-label={`${periods[period]} 섹터 수익률 비교`}>
            {sectors.map(sector => {
              const value = sector[period];
              const valid = value != null && Number.isFinite(value);
              const width = valid && maximum > 0 ? Math.abs(value) / maximum * 50 : 0;
              return <li className={styles.row} key={sector.slug}>
                <span className={styles.name}>{sector.name}</span>
                <span className={styles.track} aria-hidden="true">{valid && <span className={styles.bar} style={{ width: `${width}%`, left: value < 0 ? `${50 - width}%` : '50%', background: value < 0 ? 'var(--color-text-blue)' : 'var(--color-text-red)' }} />}</span>
                <span className={styles.value}>{valid ? `${value >= 0 ? '+' : ''}${value.toFixed(2)}%` : '미제공'}</span>
              </li>;
            })}
          </ol>
        </Section>
        <Section padding={0}>
          <Table data={sectors as SectorRow[]} columns={columns} idKey="slug" hasHover />
        </Section>
      </VStack>
    </Card>
  );
}
