'use client';

import { useEffect, useState } from 'react';
import { VStack, HStack } from '@astryxdesign/core/Stack';
import { Section } from '@astryxdesign/core/Section';
import { Grid } from '@astryxdesign/core/Grid';
import { Heading, Text } from '@astryxdesign/core/Text';
import { Button } from '@astryxdesign/core/Button';
import { IconButton } from '@astryxdesign/core/IconButton';
import { Icon } from '@astryxdesign/core/Icon';
import { PlusIcon } from '@heroicons/react/24/outline';
import { Center } from '@astryxdesign/core/Center';
import { Spinner } from '@astryxdesign/core/Spinner';
import { PanelSlot } from '@/components/dashboard/PanelSlot';
import { WatchlistOverviewSection } from '@/components/dashboard/WatchlistOverviewSection';
import { useAuth } from '@/lib/auth-context';
import * as api from '@/lib/api';
import type { DashboardConfigResponse, PanelConfig, PanelType } from '@/lib/types';

const DEFAULT_LAYOUT_KEY = 'GRID_2COL';

// First two panels default to News + Indicator; anything added afterward defaults to a chart.
function defaultTypeForSlot(slot: number): PanelType {
  if (slot === 0) return 'NEWS';
  if (slot === 1) return 'INDICATOR';
  return 'CHART';
}

export default function DashboardPage() {
  const { authFetch } = useAuth();
  const [config, setConfig] = useState<DashboardConfigResponse | null>(null);
  const [slotCount, setSlotCount] = useState(2);
  const [isSaving, setIsSaving] = useState(false);
  const [savedAt, setSavedAt] = useState<Date | null>(null);

  useEffect(() => {
    api.getDashboardConfig(authFetch).then((data) => {
      setConfig(data);
      setSlotCount(Math.max(2, data.panels.length));
    });
  }, [authFetch]);

  function updateSlot(panel: PanelConfig) {
    setConfig((prev) => {
      const base = prev ?? { layoutKey: DEFAULT_LAYOUT_KEY, panels: [] };
      const panels = [...base.panels.filter((existing) => existing.slot !== panel.slot), panel];
      return { ...base, panels };
    });
  }

  function addSlot() {
    setSlotCount((count) => count + 1);
  }

  function removeSlot(slot: number) {
    setConfig((prev) => {
      if (!prev) return prev;
      const panels = prev.panels
        .filter((existing) => existing.slot !== slot)
        .map((existing) => (existing.slot > slot ? { ...existing, slot: existing.slot - 1 } : existing));
      return { ...prev, panels };
    });
    setSlotCount((count) => Math.max(0, count - 1));
  }

  async function handleSave() {
    if (!config) return;
    setIsSaving(true);
    try {
      const saved = await api.saveDashboardConfig(authFetch, { ...config, layoutKey: DEFAULT_LAYOUT_KEY });
      setConfig(saved);
      setSavedAt(new Date());
    } finally {
      setIsSaving(false);
    }
  }

  if (!config) {
    return (
      <Center height="100vh">
        <Spinner size="lg" label="불러오는 중" />
      </Center>
    );
  }

  const panelBySlot = new Map(config.panels.map((panel) => [panel.slot, panel]));

  return (
    <VStack gap={0}>
      <Section padding={4} dividers={['bottom']}>
        <HStack justify="between" align="center" wrap="wrap">
          <VStack gap={1}>
            <Heading level={3}>대시보드</Heading>
            <Text type="supporting" size="sm">
              {savedAt ? `${savedAt.toLocaleTimeString('ko-KR')}에 저장됨` : '패널을 구성하고 저장하세요'}
            </Text>
          </VStack>
          <HStack gap={2} align="center">
            <IconButton
              variant="secondary"
              icon={<Icon icon={PlusIcon} />}
              label="패널 추가"
              tooltip="패널 추가"
              clickAction={addSlot}
            />
            <Button variant="primary" label="레이아웃 저장" isLoading={isSaving} clickAction={handleSave} />
          </HStack>
        </HStack>
      </Section>

      <Section padding={4}>
        <Grid columns={2} gap={4}>
          {Array.from({ length: slotCount }, (_, slot) => (
            <PanelSlot
              key={slot}
              slot={slot}
              config={panelBySlot.get(slot)}
              defaultType={defaultTypeForSlot(slot)}
              onChange={updateSlot}
              onRemove={() => removeSlot(slot)}
            />
          ))}
        </Grid>
      </Section>

      <WatchlistOverviewSection />
    </VStack>
  );
}
