'use client';

import { useEffect, useState } from 'react';
import { VStack, HStack } from '@astryxdesign/core/Stack';
import { Grid } from '@astryxdesign/core/Grid';
import { Card } from '@astryxdesign/core/Card';
import { Text, Heading } from '@astryxdesign/core/Text';
import { StatusDot } from '@astryxdesign/core/StatusDot';
import { Banner } from '@astryxdesign/core/Banner';
import { List, ListItem } from '@astryxdesign/core/List';
import { useAuth } from '@/lib/auth-context';
import * as api from '@/lib/api';
import type { CollectorStatusResponse, SystemStatusResponse } from '@/lib/types';

const POLL_INTERVAL_MS = 5000;

export default function AdminCollectorPage() {
  const { authFetch } = useAuth();
  const [collectorStatus, setCollectorStatus] = useState<CollectorStatusResponse | null>(null);
  const [systemStatus, setSystemStatus] = useState<SystemStatusResponse | null>(null);

  useEffect(() => {
    let cancelled = false;
    function poll() {
      Promise.all([api.getCollectorStatus(authFetch), api.getSystemStatus(authFetch)]).then(
        ([collector, system]) => {
          if (cancelled) return;
          setCollectorStatus(collector);
          setSystemStatus(system);
        },
      );
    }
    poll();
    const interval = setInterval(poll, POLL_INTERVAL_MS);
    return () => {
      cancelled = true;
      clearInterval(interval);
    };
  }, [authFetch]);

  const health = collectorStatus?.health;

  return (
    <VStack gap={6}>
      <Grid columns={3} gap={4}>
        <Card padding={4}>
          <VStack gap={1}>
            <Text type="supporting" size="sm">
              접속자 수
            </Text>
            <Heading level={4}>{systemStatus?.connectedUsers ?? '—'}</Heading>
          </VStack>
        </Card>
        <Card padding={4}>
          <VStack gap={1}>
            <Text type="supporting" size="sm">
              WebSocket 세션 수
            </Text>
            <Heading level={4}>{systemStatus?.activeSessions ?? '—'}</Heading>
          </VStack>
        </Card>
        <Card padding={4}>
          <VStack gap={1}>
            <Text type="supporting" size="sm">
              재접속 횟수
            </Text>
            <Heading level={4}>{health?.reconnect_count ?? '—'}</Heading>
          </VStack>
        </Card>
      </Grid>

      {collectorStatus && !collectorStatus.reachable && (
        <Banner status="error" title="수집기에 연결할 수 없습니다" description="collector 프로세스가 꺼져 있을 수 있습니다." />
      )}

      {health?.last_error && (
        <Banner status="warning" title="최근 수집기 오류" description={health.last_error} />
      )}

      {collectorStatus?.reachable && health && (
        <Card padding={4}>
          <VStack gap={4}>
            <HStack gap={2} align="center">
              <StatusDot
                variant={health.ws_connected ? 'success' : 'error'}
                label={health.ws_connected ? 'Finnhub WS 연결됨' : 'Finnhub WS 끊김'}
              />
              <Text type="body">{health.ws_connected ? 'Finnhub WebSocket 연결됨' : 'Finnhub WebSocket 끊김'}</Text>
            </HStack>
            <Text type="label">종목별 마지막 틱 시각</Text>
            <List hasDividers>
              {health.subscribed_symbols.map((ticker) => (
                <ListItem
                  key={ticker}
                  label={ticker}
                  description={
                    health.last_tick_at[ticker]
                      ? new Date(health.last_tick_at[ticker]).toLocaleString('ko-KR')
                      : '아직 틱 없음'
                  }
                />
              ))}
            </List>
          </VStack>
        </Card>
      )}
    </VStack>
  );
}
