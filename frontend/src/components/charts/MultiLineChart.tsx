'use client';

import { useState } from 'react';
import { VStack } from '@astryxdesign/core/Stack';
import { Center } from '@astryxdesign/core/Center';
import { Text } from '@astryxdesign/core/Text';
import { ChartLegend, type ChartSeriesMeta } from './ChartLegend';
import { ChartTooltip } from './ChartTooltip';

export interface LineSeries extends ChartSeriesMeta {
  values: (number | null)[];
}

const PADDING_LEFT = 44;
const PADDING_BOTTOM = 20;
const PADDING_TOP = 8;
const PADDING_RIGHT = 8;

export function MultiLineChart({
  categories,
  series,
  width = 520,
  height = 220,
  valueFormatter = (v: number) => v.toFixed(1),
}: {
  categories: (string | number)[];
  series: LineSeries[];
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
  const stepX = categories.length > 1 ? plotWidth / (categories.length - 1) : 0;

  function yFor(value: number): number {
    return PADDING_TOP + plotHeight - ((value - min) / range) * plotHeight;
  }

  function pointsFor(values: (number | null)[]): string {
    return values
      .map((v, i) => (v == null ? null : `${PADDING_LEFT + i * stepX},${yFor(v)}`))
      .filter((p): p is string => p != null)
      .join(' ');
  }

  const hoveredCategoryX = hoveredIndex == null ? null : PADDING_LEFT + hoveredIndex * stepX;

  return (
    <VStack gap={2}>
      <svg viewBox={`0 0 ${width} ${height}`} style={{ width: '100%', height: 'auto' }} role="img" aria-hidden="true">
        <text x={0} y={PADDING_TOP + 8} fontSize={10} fill="var(--color-text-secondary)">
          {valueFormatter(max)}
        </text>
        <text x={0} y={height - PADDING_BOTTOM} fontSize={10} fill="var(--color-text-secondary)">
          {valueFormatter(min)}
        </text>
        {min < 0 && max > 0 && (
          <line
            x1={PADDING_LEFT}
            x2={width - PADDING_RIGHT}
            y1={yFor(0)}
            y2={yFor(0)}
            stroke="var(--color-border)"
            strokeDasharray="2,3"
          />
        )}
        {series.map((s) => (
          <polyline
            key={s.label}
            points={pointsFor(s.values)}
            fill="none"
            stroke={s.color}
            strokeWidth={2}
            strokeLinejoin="round"
            strokeLinecap="round"
          />
        ))}
        {categories.map((category, i) => (
          <text
            key={String(category)}
            x={PADDING_LEFT + i * stepX}
            y={height - 4}
            fontSize={10}
            textAnchor={i === 0 ? 'start' : i === categories.length - 1 ? 'end' : 'middle'}
            fill="var(--color-text-secondary)"
          >
            {category}
          </text>
        ))}

        {hoveredCategoryX != null && (
          <line
            x1={hoveredCategoryX}
            x2={hoveredCategoryX}
            y1={PADDING_TOP}
            y2={PADDING_TOP + plotHeight}
            stroke="var(--color-text-secondary)"
            strokeDasharray="2,3"
          />
        )}
        {hoveredIndex != null &&
          series.map((s) => {
            const value = s.values[hoveredIndex];
            if (value == null) return null;
            return <circle key={s.label} cx={PADDING_LEFT + hoveredIndex * stepX} cy={yFor(value)} r={3} fill={s.color} />;
          })}

        {/* Invisible hover targets, one per category, drawn last so they always receive pointer events. */}
        {categories.map((category, i) => (
          <rect
            key={`hit-${String(category)}`}
            x={PADDING_LEFT + i * stepX - stepX / 2}
            y={0}
            width={stepX || plotWidth}
            height={height}
            fill="transparent"
            onMouseEnter={() => setHoveredIndex(i)}
            onMouseLeave={() => setHoveredIndex(null)}
          />
        ))}

        {hoveredIndex != null && hoveredCategoryX != null && (
          <ChartTooltip
            x={hoveredCategoryX}
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
