'use client';

import { useEffect, useState } from 'react';
import { VStack, HStack } from '@astryxdesign/core/Stack';
import { Card } from '@astryxdesign/core/Card';
import { Heading } from '@astryxdesign/core/Text';
import { NumberInput } from '@astryxdesign/core/NumberInput';
import { SegmentedControl, SegmentedControlItem } from '@astryxdesign/core/SegmentedControl';
import { Button } from '@astryxdesign/core/Button';
import { IconButton } from '@astryxdesign/core/IconButton';
import { Icon } from '@astryxdesign/core/Icon';
import { Banner } from '@astryxdesign/core/Banner';
import { List, ListItem } from '@astryxdesign/core/List';
import { useAuth } from '@/lib/auth-context';
import { ApiError } from '@/lib/api';
import * as api from '@/lib/api';
import type { AlertCondition, AlertResponse } from '@/lib/types';

export function AlertsPanel({ ticker }: { ticker: string }) {
  const { authFetch } = useAuth();
  const [alerts, setAlerts] = useState<AlertResponse[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [condition, setCondition] = useState<AlertCondition>('ABOVE');
  const [targetPrice, setTargetPrice] = useState<number | null>(null);
  const [isCreating, setIsCreating] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [deletingId, setDeletingId] = useState<number | null>(null);

  useEffect(() => {
    api
      .getAlerts(authFetch)
      .then(setAlerts)
      .finally(() => setIsLoading(false));
  }, [authFetch]);

  const tickerAlerts = alerts.filter((alert) => alert.ticker === ticker);

  async function handleCreate(e: React.FormEvent) {
    e.preventDefault();
    if (targetPrice == null) return;
    setError(null);
    setIsCreating(true);
    try {
      const created = await api.createAlert(authFetch, { ticker, condition, targetPrice });
      setAlerts((prev) => [created, ...prev]);
      setTargetPrice(null);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '알림 등록에 실패했습니다.');
    } finally {
      setIsCreating(false);
    }
  }

  async function handleDelete(id: number) {
    setDeletingId(id);
    try {
      await api.deleteAlert(authFetch, id);
      setAlerts((prev) => prev.filter((alert) => alert.id !== id));
    } finally {
      setDeletingId(null);
    }
  }

  return (
    <Card padding={4}>
      <VStack gap={4}>
        <Heading level={4}>가격 알림</Heading>
        {error && <Banner status="error" title="등록 실패" description={error} />}
        <form onSubmit={handleCreate}>
          <HStack gap={4} align="end" wrap="wrap">
            <SegmentedControl value={condition} onChange={(value) => setCondition(value as AlertCondition)} label="조건">
              <SegmentedControlItem value="ABOVE" label="이상" />
              <SegmentedControlItem value="BELOW" label="이하" />
            </SegmentedControl>
            <NumberInput label="목표가" value={targetPrice} onChange={setTargetPrice} isRequired min={0} />
            <Button type="submit" variant="primary" label="알림 등록" isLoading={isCreating} />
          </HStack>
        </form>

        {!isLoading && tickerAlerts.length > 0 && (
          <List hasDividers>
            {tickerAlerts.map((alert) => (
              <ListItem
                key={alert.id}
                label={`${alert.condition === 'ABOVE' ? '이상' : '이하'} ${alert.targetPrice}`}
                description={
                  alert.triggeredAt ? `도달함 (${new Date(alert.triggeredAt).toLocaleString('ko-KR')})` : '대기 중'
                }
                endContent={
                  <IconButton
                    variant="ghost"
                    size="sm"
                    icon={<Icon icon="close" />}
                    label="알림 삭제"
                    isLoading={deletingId === alert.id}
                    clickAction={() => handleDelete(alert.id)}
                  />
                }
              />
            ))}
          </List>
        )}
      </VStack>
    </Card>
  );
}
