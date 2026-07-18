'use client';

import { useState } from 'react';
import { VStack } from '@astryxdesign/core/Stack';
import { Center } from '@astryxdesign/core/Center';
import { Text } from '@astryxdesign/core/Text';
import { ChartLegend, type ChartSeriesMeta } from './ChartLegend';
import { ChartTooltip } from './ChartTooltip';

export interface BarSeries extends ChartSeriesMeta {
  values: (number | null)[];
}

const PADDING_LEFT = 44;
const PADDING_BOTTOM = 20;
const PADDING_TOP = 8;
const PADDING_RIGHT = 8;
const GROUP_GAP = 4;

export function GroupedBarChart({
  categories,
  series,
  width = 520,
  height = 220,
  valueFormatter = (v: number) => v.toFixed(0),
}: {
  categories: (string | number)[];
  series: BarSeries[];
  width?: number;
  height?: number;
  valueFormatter?: (value: number) => string;
}) {
  const [hoveredIndex, setHoveredIndex] = useState<number | null>(null);
  const allValues = series.flatMap((s) => s.values).filter((v): v is number => v != null);

  if (allValues.length === 0 || categories.length === 0) {
    return (
      <Center height={height}>
        <Text type="body" color="secondary">
          데이터가 없습니다
        </Text>
      </Center>
    );
  }

  const max = Math.max(...allValues, 0);
  const min = Math.min(...allValues, 0);
  const range = max - min || 1;
  const plotWidth = width - PADDING_LEFT - PADDING_RIGHT;
  const plotHeight = height - PADDING_BOTTOM - PADDING_TOP;
  const groupWidth = plotWidth / categories.length;
  const barWidth = Math.max((groupWidth - GROUP_GAP * (series.length + 1)) / series.length, 1);
  const zeroY = PADDING_TOP + plotHeight - ((0 - min) / range) * plotHeight;

  return (
    <VStack gap={2}>
      <svg viewBox={`0 0 ${width} ${height}`} style={{ width: '100%', height: 'auto' }} role="img" aria-hidden="true">
        <text x={0} y={PADDING_TOP + 8} fontSize={10} fill="var(--color-text-secondary)">
          {valueFormatter(max)}
        </text>
        <text x={0} y={height - PADDING_BOTTOM} fontSize={10} fill="var(--color-text-secondary)">
          {valueFormatter(min)}
        </text>
        <line x1={PADDING_LEFT} x2={width - PADDING_RIGHT} y1={zeroY} y2={zeroY} stroke="var(--color-border)" />
        {categories.map((category, ci) => (
          <g key={String(category)}>
            {series.map((s, si) => {
              const value = s.values[ci];
              if (value == null) return null;
              const barHeight = Math.max((Math.abs(value) / range) * plotHeight, 1);
              const y = value >= 0 ? zeroY - barHeight : zeroY;
              const x = PADDING_LEFT + ci * groupWidth + GROUP_GAP + si * (barWidth + GROUP_GAP);
              return (
                <rect
                  key={s.label}
                  x={x}
                  y={y}
                  width={barWidth}
                  height={barHeight}
                  fill={s.color}
                  rx={1}
                  opacity={hoveredIndex == null || hoveredIndex === ci ? 1 : 0.4}
                />
              );
            })}
            <text
              x={PADDING_LEFT + ci * groupWidth + groupWidth / 2}
              y={height - 4}
              fontSize={10}
              textAnchor="middle"
              fill="var(--color-text-secondary)"
            >
              {category}
            </text>
          </g>
        ))}

        {/* Invisible hover targets, one per category, drawn last so they always receive pointer events. */}
        {categories.map((category, ci) => (
          <rect
            key={`hit-${String(category)}`}
            x={PADDING_LEFT + ci * groupWidth}
            y={0}
            width={groupWidth}
            height={height}
            fill="transparent"
            onMouseEnter={() => setHoveredIndex(ci)}
            onMouseLeave={() => setHoveredIndex(null)}
          />
        ))}

        {hoveredIndex != null && (
          <ChartTooltip
            x={PADDING_LEFT + hoveredIndex * groupWidth + groupWidth / 2}
            chartWidth={width}
            title={String(categories[hoveredIndex])}
            lines={series
              .filter((s) => s.values[hoveredIndex] != null)
              .map((s) => ({ label: s.label, color: s.color, text: valueFormatter(s.values[hoveredIndex] as number) }))}
          />
        )}
      </svg>
      <ChartLegend series={series} />
    </VStack>
  );
}
