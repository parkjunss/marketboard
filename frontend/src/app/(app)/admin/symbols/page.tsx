'use client';

import { useEffect, useState } from 'react';
import { VStack, HStack } from '@astryxdesign/core/Stack';
import { Card } from '@astryxdesign/core/Card';
import { Heading, Text } from '@astryxdesign/core/Text';
import { TextInput } from '@astryxdesign/core/TextInput';
import { NumberInput } from '@astryxdesign/core/NumberInput';
import { Switch } from '@astryxdesign/core/Switch';
import { Button } from '@astryxdesign/core/Button';
import { Table, proportional, pixel, useTableSelection, useTableSelectionState } from '@astryxdesign/core/Table';
import type { TableColumn } from '@astryxdesign/core/Table';
import { Center } from '@astryxdesign/core/Center';
import { Spinner } from '@astryxdesign/core/Spinner';
import { Banner } from '@astryxdesign/core/Banner';
import { AlertDialog } from '@astryxdesign/core/AlertDialog';
import { useAuth } from '@/lib/auth-context';
import { ApiError } from '@/lib/api';
import * as api from '@/lib/api';
import type { SymbolResponse } from '@/lib/types';

interface SymbolRow extends SymbolResponse, Record<string, unknown> {}

export default function AdminSymbolsPage() {
  const { authFetch } = useAuth();
  const [isActivateDialogOpen, setIsActivateDialogOpen] = useState(false);
  const [symbols, setSymbols] = useState<SymbolRow[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [ticker, setTicker] = useState('');
  const [name, setName] = useState('');
  const [exchange, setExchange] = useState('US');
  const [priority, setPriority] = useState<number | null>(0);
  const [isCreating, setIsCreating] = useState(false);
  const [pendingId, setPendingId] = useState<number | null>(null);

  const [selectedKeys, setSelectedKeys] = useState<Set<string>>(new Set());
  const [isBulkActivating, setIsBulkActivating] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<SymbolRow | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);

  useEffect(() => {
    api
      .getAdminSymbols(authFetch)
      .then((data) => setSymbols(data as SymbolRow[]))
      .finally(() => setIsLoading(false));
  }, [authFetch]);

  async function handleCreate(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setIsCreating(true);
    try {
      const created = await api.createAdminSymbol(authFetch, { ticker, name, exchange, priority: priority ?? 0 });
      setSymbols((prev) => [...prev, created as SymbolRow]);
      setTicker('');
      setName('');
      setExchange('US');
      setPriority(0);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '종목 추가에 실패했습니다.');
    } finally {
      setIsCreating(false);
    }
  }

  async function toggleActive(symbol: SymbolRow, active: boolean) {
    setPendingId(symbol.id);
    try {
      const updated = await api.updateAdminSymbol(authFetch, symbol.id, {
        name: symbol.name,
        exchange: symbol.exchange,
        priority: symbol.priority,
        active,
      });
      setSymbols((prev) => prev.map((s) => (s.id === updated.id ? (updated as SymbolRow) : s)));
    } finally {
      setPendingId(null);
    }
  }

  async function handleBulkActivate() {
    const ids = [...selectedKeys].map(Number);
    if (ids.length === 0) return;
    setIsBulkActivating(true);
    try {
      const updated = await api.bulkSetAdminSymbolsActive(authFetch, ids, true);
      const updatedById = new Map(updated.map((s) => [s.id, s]));
      setSymbols((prev) => prev.map((s) => (updatedById.has(s.id) ? (updatedById.get(s.id) as SymbolRow) : s)));
      setSelectedKeys(new Set());
      setIsActivateDialogOpen(false);
    } finally {
      setIsBulkActivating(false);
    }
  }

  async function handleDelete() {
    if (!deleteTarget) return;
    setIsDeleting(true);
    try {
      await api.deleteAdminSymbol(authFetch, deleteTarget.id);
      setSymbols((prev) => prev.filter((s) => s.id !== deleteTarget.id));
      setDeleteTarget(null);
    } finally {
      setIsDeleting(false);
    }
  }

  const { selectionConfig } = useTableSelectionState<SymbolRow>({
    data: symbols,
    idKey: (item) => String(item.id),
    selectedKeys,
    setSelectedKeys,
    getIsItemSelectable: (item) => !item.active,
  });
  const selectionPlugin = useTableSelection<SymbolRow>(selectionConfig);

  const selectedCount = selectedKeys.size;

  const columns: TableColumn<SymbolRow>[] = [
    { key: 'ticker', header: '티커', width: pixel(100) },
    { key: 'name', header: '이름', width: proportional(2) },
    { key: 'exchange', header: '거래소', width: pixel(100) },
    { key: 'priority', header: '우선순위', width: pixel(100) },
    {
      key: 'active',
      header: '활성',
      width: pixel(100),
      renderCell: (row) => (
        <Switch
          label="활성"
          isLabelHidden
          value={row.active}
          isLoading={pendingId === row.id}
          changeAction={(checked) => toggleActive(row, checked)}
        />
      ),
    },
    {
      key: 'actions',
      header: '',
      width: pixel(100),
      renderCell: (row) => (
        <Button variant="destructive" size="sm" label="삭제" onClick={() => setDeleteTarget(row)} />
      ),
    },
  ];

  return (
    <VStack gap={6}>
      <Card padding={4}>
        <form onSubmit={handleCreate}>
          <VStack gap={4}>
            <Heading level={4}>새 종목 추가</Heading>
            {error && <Banner status="error" title="추가 실패" description={error} />}
            <HStack gap={4} wrap="wrap">
              <TextInput label="티커" value={ticker} onChange={(v) => setTicker(v.toUpperCase())} isRequired />
              <TextInput label="이름" value={name} onChange={setName} isRequired />
              <TextInput label="거래소" value={exchange} onChange={setExchange} isRequired />
              <NumberInput label="우선순위" value={priority} onChange={setPriority} />
            </HStack>
            <Button type="submit" variant="primary" label="추가" isLoading={isCreating} />
          </VStack>
        </form>
      </Card>

      {isLoading ? (
        <Center height={240}>
          <Spinner size="lg" label="불러오는 중" />
        </Center>
      ) : (
        <VStack gap={3}>
          {selectedCount > 0 && (
            <HStack gap={3} align="center">
              <Text type="body">{selectedCount}개 선택됨</Text>
              <Button
                variant="primary"
                size="sm"
                label={`선택 항목 활성화 (${selectedCount})`}
                onClick={() => setIsActivateDialogOpen(true)}
              />
              <Button variant="ghost" size="sm" label="선택 해제" onClick={() => setSelectedKeys(new Set())} />
            </HStack>
          )}
          <Table data={symbols} columns={columns} idKey="id" hasHover plugins={{ selection: selectionPlugin }} />
        </VStack>
      )}

      <AlertDialog
        isOpen={isActivateDialogOpen}
        onOpenChange={setIsActivateDialogOpen}
        title="선택한 종목을 활성화할까요?"
        description={`${selectedCount}개 종목이 실시간 시세 구독 대상에 추가됩니다. 한 번에 너무 많은 종목을 활성화하면 Finnhub 실시간 연결의 동시구독 제한에 걸려 기존 종목의 실시간 시세가 끊길 수 있습니다.`}
        actionLabel="활성화"
        actionVariant="primary"
        isActionLoading={isBulkActivating}
        onAction={handleBulkActivate}
      />

      <AlertDialog
        isOpen={deleteTarget !== null}
        onOpenChange={(open) => !open && setDeleteTarget(null)}
        title="종목을 삭제할까요?"
        description={
          deleteTarget
            ? `${deleteTarget.ticker} 종목과 관련된 모든 사용자의 시세 이력/지표/워치리스트/알림/포트폴리오 보유 내역이 함께 삭제됩니다. 되돌릴 수 없습니다. 잠시 비활성화하려면 삭제 대신 활성 토글을 사용하세요.`
            : ''
        }
        actionLabel="삭제"
        actionVariant="destructive"
        isActionLoading={isDeleting}
        onAction={handleDelete}
      />
    </VStack>
  );
}
