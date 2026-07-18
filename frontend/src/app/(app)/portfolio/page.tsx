'use client';

import { useEffect, useState } from 'react';
import { VStack, HStack } from '@astryxdesign/core/Stack';
import { Section } from '@astryxdesign/core/Section';
import { Card } from '@astryxdesign/core/Card';
import { SelectableCard } from '@astryxdesign/core/SelectableCard';
import { Grid } from '@astryxdesign/core/Grid';
import { Heading, Text } from '@astryxdesign/core/Text';
import { Table, proportional } from '@astryxdesign/core/Table';
import type { TableColumn } from '@astryxdesign/core/Table';
import { Icon } from '@astryxdesign/core/Icon';
import { IconButton } from '@astryxdesign/core/IconButton';
import { Button } from '@astryxdesign/core/Button';
import { TextInput } from '@astryxdesign/core/TextInput';
import { NumberInput } from '@astryxdesign/core/NumberInput';
import { Badge } from '@astryxdesign/core/Badge';
import { Center } from '@astryxdesign/core/Center';
import { Spinner } from '@astryxdesign/core/Spinner';
import { EmptyState } from '@astryxdesign/core/EmptyState';
import { Banner } from '@astryxdesign/core/Banner';
import { useImperativeAlertDialog } from '@astryxdesign/core/AlertDialog';
import { PlusIcon, TrashIcon } from '@heroicons/react/24/outline';
import { useAuth } from '@/lib/auth-context';
import * as api from '@/lib/api';
import { ApiError } from '@/lib/api';
import type { PortfolioPositionResponse, PortfolioSummaryResponse } from '@/lib/types';

function formatMoney(value: number | null): string {
  return value == null ? '—' : value.toLocaleString('ko-KR', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function PnlText({ value, pct }: { value: number | null; pct: number | null }) {
  if (value == null || pct == null) return <Text type="supporting">—</Text>;
  return (
    <HStack gap={1} align="center">
      <Icon icon={value >= 0 ? 'arrowUp' : 'arrowDown'} color={value >= 0 ? 'success' : 'error'} size="sm" />
      <Text type="body">
        {formatMoney(Math.abs(value))} ({Math.abs(pct).toFixed(2)}%)
      </Text>
    </HStack>
  );
}

function PriceSourceBadge({ source }: { source: PortfolioPositionResponse['priceSource'] }) {
  if (source === 'LIVE') return null;
  if (source === 'CLOSE') return <Badge variant="neutral" label="전일 종가" />;
  return <Badge variant="warning" label="가격 없음" />;
}

interface PositionRow extends PortfolioPositionResponse, Record<string, unknown> {}

export default function PortfolioPage() {
  const { authFetch } = useAuth();
  const deleteDialog = useImperativeAlertDialog();

  const [portfolios, setPortfolios] = useState<PortfolioSummaryResponse[] | null>(null);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [positions, setPositions] = useState<{ key: number; data: PortfolioPositionResponse[] } | null>(null);
  const [isCreating, setIsCreating] = useState(false);
  const [newPortfolioName, setNewPortfolioName] = useState('');

  const [ticker, setTicker] = useState('');
  const [quantity, setQuantity] = useState<number | null>(null);
  const [avgCost, setAvgCost] = useState<number | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  // No portfolio explicitly selected yet -> default to the first one, derived at render time
  // rather than set via an effect (avoids a second setState cascading off the initial fetch).
  const effectiveSelectedId = selectedId ?? portfolios?.[0]?.id ?? null;
  const isLoadingPositions = effectiveSelectedId != null && positions?.key !== effectiveSelectedId;

  async function refreshPortfolios(): Promise<PortfolioSummaryResponse[]> {
    const data = await api.getPortfolios(authFetch);
    setPortfolios(data);
    return data;
  }

  async function refreshPositions(portfolioId: number) {
    const data = await api.getPortfolioPositions(authFetch, portfolioId);
    setPositions({ key: portfolioId, data });
  }

  useEffect(() => {
    let cancelled = false;
    api.getPortfolios(authFetch).then((data) => {
      if (!cancelled) setPortfolios(data);
    });
    return () => {
      cancelled = true;
    };
  }, [authFetch]);

  useEffect(() => {
    if (effectiveSelectedId == null) return undefined;
    let cancelled = false;
    api.getPortfolioPositions(authFetch, effectiveSelectedId).then((data) => {
      if (!cancelled) setPositions({ key: effectiveSelectedId, data });
    });
    return () => {
      cancelled = true;
    };
  }, [authFetch, effectiveSelectedId]);

  async function handleCreatePortfolio() {
    if (!newPortfolioName.trim()) return;
    const created = await api.createPortfolio(authFetch, newPortfolioName.trim());
    setNewPortfolioName('');
    setIsCreating(false);
    await refreshPortfolios();
    setSelectedId(created.id);
  }

  async function handleDeletePortfolio(portfolioId: number) {
    await api.deletePortfolio(authFetch, portfolioId);
    const remaining = await refreshPortfolios();
    setSelectedId(remaining.length > 0 ? remaining[0].id : null);
  }

  async function handleAddPosition() {
    if (effectiveSelectedId == null || !ticker.trim() || quantity == null || avgCost == null) return;
    setIsSubmitting(true);
    setFormError(null);
    try {
      await api.addPortfolioPosition(authFetch, effectiveSelectedId, {
        ticker: ticker.trim().toUpperCase(),
        quantity,
        avgCost,
      });
      setTicker('');
      setQuantity(null);
      setAvgCost(null);
      await Promise.all([refreshPositions(effectiveSelectedId), refreshPortfolios()]);
    } catch (err) {
      setFormError(err instanceof ApiError ? err.message : '포지션 추가에 실패했습니다.');
    } finally {
      setIsSubmitting(false);
    }
  }

  async function handleRemovePosition(positionId: number) {
    if (effectiveSelectedId == null) return;
    await api.removePortfolioPosition(authFetch, effectiveSelectedId, positionId);
    await Promise.all([refreshPositions(effectiveSelectedId), refreshPortfolios()]);
  }

  const selectedPortfolio = portfolios?.find((p) => p.id === effectiveSelectedId) ?? null;
  const rows: PositionRow[] = (positions?.key === effectiveSelectedId ? positions.data : []) as PositionRow[];

  const columns: TableColumn<PositionRow>[] = [
    {
      key: 'ticker',
      header: '종목',
      width: proportional(1.2),
      renderCell: (row) => (
        <HStack gap={2} align="end">
          <Heading level={5}>{row.ticker}</Heading>
          <Text type="supporting" size="sm">
            {row.name}
          </Text>
        </HStack>
      ),
    },
    {
      key: 'quantity',
      header: '수량',
      width: proportional(0.8),
      renderCell: (row) => <Text type="body">{row.quantity}</Text>,
    },
    {
      key: 'avgCost',
      header: '평단가',
      width: proportional(0.8),
      renderCell: (row) => <Text type="body">{formatMoney(row.avgCost)}</Text>,
    },
    {
      key: 'currentPrice',
      header: '현재가',
      width: proportional(1),
      renderCell: (row) => (
        <HStack gap={2} align="center">
          <Text type="body">{formatMoney(row.currentPrice)}</Text>
          <PriceSourceBadge source={row.priceSource} />
        </HStack>
      ),
    },
    {
      key: 'marketValue',
      header: '평가금액',
      width: proportional(1),
      renderCell: (row) => <Text type="body">{formatMoney(row.marketValue)}</Text>,
    },
    {
      key: 'unrealizedPnl',
      header: '평가손익',
      width: proportional(1.4),
      renderCell: (row) => <PnlText value={row.unrealizedPnl} pct={row.unrealizedPnlPct} />,
    },
    {
      key: 'actions',
      header: '',
      width: proportional(0.4),
      renderCell: (row) => (
        <IconButton
          icon={<Icon icon={TrashIcon} size="sm" />}
          label={`${row.ticker} 포지션 삭제`}
          variant="ghost"
          clickAction={() =>
            deleteDialog.show({
              title: '포지션을 삭제할까요?',
              description: `${row.ticker} 포지션을 이 포트폴리오에서 제거합니다. 되돌릴 수 없습니다.`,
              actionLabel: '삭제',
              onAction: () => handleRemovePosition(row.id),
            })
          }
        />
      ),
    },
  ];

  return (
    <VStack gap={0}>
      <Section padding={4} dividers={['bottom']}>
        <VStack gap={1}>
          <Heading level={3}>포트폴리오</Heading>
          <Text type="supporting" size="sm">
            여러 포트폴리오를 만들어 보유 종목의 수량·평단가를 관리하고, 실시간 시세 대비 평가손익을 확인하세요
          </Text>
        </VStack>
      </Section>

      <Section padding={4} dividers={['bottom']}>
        {portfolios === null ? (
          <Center height={120}>
            <Spinner size="md" label="불러오는 중" />
          </Center>
        ) : (
          <HStack gap={3} wrap="wrap">
            {portfolios.map((portfolio) => (
              <SelectableCard
                key={portfolio.id}
                label={portfolio.name}
                isSelected={portfolio.id === effectiveSelectedId}
                onChange={() => setSelectedId(portfolio.id)}
                width={220}
              >
                <VStack gap={1}>
                  <Heading level={5}>{portfolio.name}</Heading>
                  <Text type="supporting" size="sm">
                    {portfolio.positionCount}종목
                  </Text>
                  <Text type="body">{formatMoney(portfolio.totalMarketValue)}</Text>
                  <PnlText value={portfolio.totalUnrealizedPnl} pct={portfolio.totalUnrealizedPnlPct} />
                </VStack>
              </SelectableCard>
            ))}

            {isCreating ? (
              <Card padding={4} width={220}>
                <VStack gap={2}>
                  <TextInput
                    label="포트폴리오 이름"
                    size="sm"
                    value={newPortfolioName}
                    onChange={setNewPortfolioName}
                    hasAutoFocus
                    placeholder="예: 장기투자"
                  />
                  <HStack gap={2}>
                    <Button variant="primary" size="sm" label="만들기" clickAction={handleCreatePortfolio} />
                    <Button
                      variant="secondary"
                      size="sm"
                      label="취소"
                      onClick={() => {
                        setIsCreating(false);
                        setNewPortfolioName('');
                      }}
                    />
                  </HStack>
                </VStack>
              </Card>
            ) : (
              <Card padding={4} width={220}>
                <Center height={80}>
                  <IconButton
                    icon={<Icon icon={PlusIcon} size="lg" />}
                    label="새 포트폴리오 만들기"
                    variant="ghost"
                    size="lg"
                    clickAction={() => setIsCreating(true)}
                  />
                </Center>
              </Card>
            )}
          </HStack>
        )}
      </Section>

      {portfolios !== null && portfolios.length === 0 && !isCreating ? (
        <Section padding={4}>
          <Center height={280}>
            <EmptyState
              title="아직 포트폴리오가 없습니다"
              description="위의 + 버튼으로 첫 포트폴리오를 만들어보세요"
            />
          </Center>
        </Section>
      ) : selectedPortfolio ? (
        <VStack gap={0}>
          <Section padding={4} dividers={['bottom']}>
            <HStack justify="between" align="center" wrap="wrap">
              <Grid columns={4} gap={3}>
                <VStack gap={1}>
                  <Text type="supporting" size="sm">
                    총 평가금액
                  </Text>
                  <Heading level={4}>{formatMoney(selectedPortfolio.totalMarketValue)}</Heading>
                </VStack>
                <VStack gap={1}>
                  <Text type="supporting" size="sm">
                    총 매입금액
                  </Text>
                  <Heading level={4}>{formatMoney(selectedPortfolio.totalCostBasis)}</Heading>
                </VStack>
                <VStack gap={1}>
                  <Text type="supporting" size="sm">
                    평가손익
                  </Text>
                  <PnlText value={selectedPortfolio.totalUnrealizedPnl} pct={selectedPortfolio.totalUnrealizedPnlPct} />
                </VStack>
                <VStack gap={1}>
                  <Text type="supporting" size="sm">
                    보유 종목
                  </Text>
                  <Heading level={4}>{selectedPortfolio.positionCount}</Heading>
                </VStack>
              </Grid>
              <IconButton
                icon={<Icon icon={TrashIcon} />}
                label="포트폴리오 삭제"
                variant="ghost"
                clickAction={() =>
                  deleteDialog.show({
                    title: '포트폴리오를 삭제할까요?',
                    description: `"${selectedPortfolio.name}" 포트폴리오와 보유한 모든 포지션이 삭제됩니다. 되돌릴 수 없습니다.`,
                    actionLabel: '삭제',
                    onAction: () => handleDeletePortfolio(selectedPortfolio.id),
                  })
                }
              />
            </HStack>
          </Section>

          <Section padding={4} dividers={['bottom']}>
            <VStack gap={3}>
              <Heading level={5}>포지션 추가</Heading>
              {formError && <Banner status="error" title="추가 실패" description={formError} />}
              <HStack gap={3} align="end" wrap="wrap">
                <TextInput
                  label="티커"
                  size="sm"
                  placeholder="AAPL"
                  value={ticker}
                  onChange={(value) => setTicker(value.toUpperCase())}
                />
                <NumberInput label="수량" size="sm" value={quantity} onChange={setQuantity} min={0} step={1} />
                <NumberInput label="평단가" size="sm" value={avgCost} onChange={setAvgCost} min={0} step={0.01} />
                <Button
                  variant="primary"
                  size="sm"
                  label="추가"
                  isLoading={isSubmitting}
                  clickAction={handleAddPosition}
                />
              </HStack>
            </VStack>
          </Section>

          {isLoadingPositions ? (
            <Center height={200}>
              <Spinner size="lg" label="불러오는 중" />
            </Center>
          ) : rows.length === 0 ? (
            <Section padding={4}>
              <Center height={200}>
                <EmptyState title="포지션이 없습니다" description="위에서 티커/수량/평단가를 입력해 추가하세요" />
              </Center>
            </Section>
          ) : (
            <Table data={rows} columns={columns} idKey="id" hasHover />
          )}
        </VStack>
      ) : null}

      {deleteDialog.element}
    </VStack>
  );
}
