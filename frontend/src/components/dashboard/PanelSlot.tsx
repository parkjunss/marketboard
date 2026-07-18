'use client';

import { Card } from '@astryxdesign/core/Card';
import { VStack, HStack } from '@astryxdesign/core/Stack';
import { Section } from '@astryxdesign/core/Section';
import { Heading } from '@astryxdesign/core/Text';
import { SegmentedControl, SegmentedControlItem } from '@astryxdesign/core/SegmentedControl';
import { TextInput } from '@astryxdesign/core/TextInput';
import { IconButton } from '@astryxdesign/core/IconButton';
import { Icon } from '@astryxdesign/core/Icon';
import { XMarkIcon } from '@heroicons/react/24/outline';
import { ChartPanel } from './ChartPanel';
import { NewsPanel } from './NewsPanel';
import { WatchlistPanel } from './WatchlistPanel';
import { WatchlistIndicatorsPanel } from './WatchlistIndicatorsPanel';
import type { PanelConfig, PanelType } from '@/lib/types';

const PANEL_TYPE_LABELS: Record<PanelType, string> = {
  CHART: '차트',
  NEWS: '뉴스',
  WATCHLIST: '워치리스트',
  INDICATOR: '지표',
};

const PANEL_TYPES = Object.keys(PANEL_TYPE_LABELS) as PanelType[];

interface PanelSlotProps {
  slot: number;
  config: PanelConfig | undefined;
  defaultType?: PanelType;
  onChange: (config: PanelConfig) => void;
  onRemove?: () => void;
}

export function PanelSlot({ slot, config, defaultType = 'CHART', onChange, onRemove }: PanelSlotProps) {
  const type = config?.type ?? defaultType;
  const ticker = config?.ticker ?? '';

  function updateType(newType: PanelType) {
    onChange({ slot, type: newType, ticker: config?.ticker, timeframe: config?.timeframe });
  }
  function updateTicker(newTicker: string) {
    onChange({ slot, type, ticker: newTicker, timeframe: config?.timeframe });
  }

  return (
    <Card padding={0}>
      <VStack gap={0}>
        <Section padding={2} dividers={['bottom']}>
          <HStack justify="between" align="center" gap={2} wrap="wrap">
            <HStack gap={2} wrap="wrap" align="center">
              <SegmentedControl size="sm" value={type} onChange={(value) => updateType(value as PanelType)} label="패널 종류">
                {PANEL_TYPES.map((value) => (
                  <SegmentedControlItem key={value} value={value} label={PANEL_TYPE_LABELS[value]} />
                ))}
              </SegmentedControl>
              {(type === 'CHART' || type === 'NEWS') && (
                <TextInput
                  size="sm"
                  label="종목"
                  isLabelHidden
                  placeholder={type === 'NEWS' ? '전체 (비워두면 시장 뉴스)' : 'AAPL'}
                  value={ticker}
                  onChange={(value) => updateTicker(value.toUpperCase())}
                />
              )}
            </HStack>
            {onRemove && (
              <IconButton
                variant="ghost"
                size="sm"
                icon={<Icon icon={XMarkIcon} />}
                label="패널 삭제"
                tooltip="패널 삭제"
                clickAction={onRemove}
              />
            )}
          </HStack>
        </Section>
        <Section padding={4}>
          <VStack gap={3}>
            {ticker && (type === 'CHART' || type === 'NEWS') && <Heading level={3}>{ticker}</Heading>}
            {type === 'CHART' && <ChartPanel ticker={ticker} />}
            {type === 'NEWS' && <NewsPanel ticker={ticker || undefined} />}
            {type === 'WATCHLIST' && <WatchlistPanel />}
            {type === 'INDICATOR' && <WatchlistIndicatorsPanel />}
          </VStack>
        </Section>
      </VStack>
    </Card>
  );
}
