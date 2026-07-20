'use client';

import { useEffect, useState } from 'react';
import { Table, proportional, pixel } from '@astryxdesign/core/Table';
import type { TableColumn } from '@astryxdesign/core/Table';
import { SegmentedControl, SegmentedControlItem } from '@astryxdesign/core/SegmentedControl';
import { Switch } from '@astryxdesign/core/Switch';
import { Button } from '@astryxdesign/core/Button';
import { HStack } from '@astryxdesign/core/Stack';
import { Center } from '@astryxdesign/core/Center';
import { Spinner } from '@astryxdesign/core/Spinner';
import { AlertDialog } from '@astryxdesign/core/AlertDialog';
import { useAuth } from '@/lib/auth-context';
import * as api from '@/lib/api';
import type { UserResponse } from '@/lib/types';

interface UserRow extends UserResponse, Record<string, unknown> {}

export default function AdminUsersPage() {
  const { authFetch, user: currentUser } = useAuth();
  const [users, setUsers] = useState<UserRow[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [pendingId, setPendingId] = useState<number | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<UserRow | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);

  useEffect(() => {
    api
      .getAdminUsers(authFetch)
      .then((data) => setUsers(data as UserRow[]))
      .finally(() => setIsLoading(false));
  }, [authFetch]);

  async function changeRole(row: UserRow, role: 'USER' | 'ADMIN') {
    setPendingId(row.id);
    try {
      const updated = await api.updateAdminUser(authFetch, row.id, { role, status: row.status });
      setUsers((prev) => prev.map((u) => (u.id === updated.id ? (updated as UserRow) : u)));
    } finally {
      setPendingId(null);
    }
  }

  async function toggleSuspended(row: UserRow, suspended: boolean) {
    setPendingId(row.id);
    try {
      const updated = await api.updateAdminUser(authFetch, row.id, {
        role: row.role,
        status: suspended ? 'SUSPENDED' : 'ACTIVE',
      });
      setUsers((prev) => prev.map((u) => (u.id === updated.id ? (updated as UserRow) : u)));
    } finally {
      setPendingId(null);
    }
  }

  async function revokeToken(row: UserRow) {
    setPendingId(row.id);
    try {
      await api.revokeAdminUserToken(authFetch, row.id);
    } finally {
      setPendingId(null);
    }
  }

  async function handleDelete() {
    if (!deleteTarget) return;
    setIsDeleting(true);
    try {
      await api.deleteAdminUser(authFetch, deleteTarget.id);
      setUsers((prev) => prev.filter((u) => u.id !== deleteTarget.id));
      setDeleteTarget(null);
    } finally {
      setIsDeleting(false);
    }
  }

  const columns: TableColumn<UserRow>[] = [
    { key: 'email', header: '이메일', width: proportional(2) },
    { key: 'username', header: '이름', width: proportional(1) },
    {
      key: 'role',
      header: '권한',
      width: pixel(160),
      renderCell: (row) => (
        <SegmentedControl
          value={row.role}
          onChange={(value) => changeRole(row, value as 'USER' | 'ADMIN')}
          label="권한"
          size="sm"
          isDisabled={pendingId === row.id || row.id === currentUser?.id}
        >
          <SegmentedControlItem value="USER" label="USER" />
          <SegmentedControlItem value="ADMIN" label="ADMIN" />
        </SegmentedControl>
      ),
    },
    {
      key: 'status',
      header: '정지',
      width: pixel(100),
      renderCell: (row) => (
        <Switch
          label="정지됨"
          isLabelHidden
          value={row.status === 'SUSPENDED'}
          isLoading={pendingId === row.id}
          isDisabled={row.id === currentUser?.id}
          changeAction={(checked) => toggleSuspended(row, checked)}
        />
      ),
    },
    {
      key: 'actions',
      header: '',
      width: pixel(280),
      renderCell: (row) => (
        <HStack gap={2}>
          <Button
            variant="secondary"
            size="sm"
            label="토큰 강제 만료"
            isLoading={pendingId === row.id}
            clickAction={() => revokeToken(row)}
          />
          <Button
            variant="destructive"
            size="sm"
            label="삭제"
            isDisabled={row.id === currentUser?.id}
            onClick={() => setDeleteTarget(row)}
          />
        </HStack>
      ),
    },
  ];

  return (
    <>
      {isLoading ? (
        <Center height={240}>
          <Spinner size="lg" label="불러오는 중" />
        </Center>
      ) : (
        <Table data={users} columns={columns} idKey="id" hasHover />
      )}

      <AlertDialog
        isOpen={deleteTarget !== null}
        onOpenChange={(open) => !open && setDeleteTarget(null)}
        title="계정을 삭제할까요?"
        description={
          deleteTarget
            ? `${deleteTarget.email} 계정과 이 계정의 워치리스트/알림/포트폴리오/대시보드 설정이 모두 삭제됩니다. 되돌릴 수 없습니다.`
            : ''
        }
        actionLabel="삭제"
        actionVariant="destructive"
        isActionLoading={isDeleting}
        onAction={handleDelete}
      />
    </>
  );
}
