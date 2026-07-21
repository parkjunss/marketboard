'use client';

import { useEffect, useState } from 'react';
import { Card } from '@astryxdesign/core/Card';
import { VStack } from '@astryxdesign/core/Stack';
import { Section } from '@astryxdesign/core/Section';
import { Heading, Text } from '@astryxdesign/core/Text';
import { Table, proportional } from '@astryxdesign/core/Table';
import type { TableColumn } from '@astryxdesign/core/Table';
import { Center } from '@astryxdesign/core/Center';
import { Spinner } from '@astryxdesign/core/Spinner';
import { useAuth } from '@/lib/auth-context';
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

export function SectorRotationTable() {
  const { authFetch } = useAuth();
  const [sectors, setSectors] = useState<SectorPerformance[] | null>(null);

  useEffect(() => {
    let cancelled = false;
    api
      .getSectorPerformance(authFetch)
      .then((data) => {
        if (!cancelled) setSectors(data);
      })
      .catch(() => {
        if (!cancelled) setSectors([]);
      });
    return () => {
      cancelled = true;
    };
  }, [authFetch]);

  if (sectors !== null && sectors.length === 0) return null; // best-effort external data -- fail quietly

  return (
    <Card padding={0}>
      <VStack gap={0}>
        <Section padding={3} dividers={['bottom']}>
          <VStack gap={1}>
            <Heading level={5}>섹터 로테이션</Heading>
            <Text type="supporting" size="sm">
              GICS 11개 섹터 SPDR ETF 상대강도 — 1개월 수익률 기준 정렬 (지수 자체이며 종목이 아님)
            </Text>
          </VStack>
        </Section>
        <Section padding={0}>
          {sectors === null ? (
            <Center height={200}>
              <Spinner size="md" label="불러오는 중" />
            </Center>
          ) : (
            <Table data={sectors as SectorRow[]} columns={columns} idKey="slug" hasHover />
          )}
        </Section>
      </VStack>
    </Card>
  );
}
