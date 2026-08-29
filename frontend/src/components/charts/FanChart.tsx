'use client';

import { useState } from 'react';
import { VStack, HStack } from '@astryxdesign/core/Stack';
import { Center } from '@astryxdesign/core/Center';
import { Text } from '@astryxdesign/core/Text';
import { ChartTooltip } from './ChartTooltip';

export interface FanChartBands {
  p5: number[];
  p25: number[];
  p50: number[];
  p75: number[];
  p95: number[];
}

const PADDING_LEFT = 52;
const PADDING_BOTTOM = 20;
const PADDING_TOP = 8;
const PADDING_RIGHT = 8;

/** Monte Carlo price-path fan chart: one sequential hue (light -> dark) encodes confidence width,
 * not a categorical split -- the 5-95% band is the lightest fill, 25-75% darker, median a solid
 * line. This is a single logical series (a price projection), so it's captioned rather than
 * legend-boxed.
 */
export function FanChart({
  categories,
  bands,
  referenceValue,
  referenceLabel = '현재가',
  width = 800,
  height = 320,
  valueFormatter = (v: number) => v.toFixed(2),
}: {
  categories: (string | number)[];
  bands: FanChartBands;
  referenceValue?: number;
  referenceLabel?: string;
  width?: number;
  height?: number;
  valueFormatter?: (value: number) => string;
}) {
  const [hoveredIndex, setHoveredIndex] = useState<number | null>(null);

  const allValues = [...bands.p5, ...bands.p95, ...(referenceValue != null ? [referenceValue] : [])];
  if (allValues.length === 0 || categories.length === 0) {
    return (
      <Center height={height}>
        <Text type="body" color="secondary">
          데이터가 없습니다
        </Text>
      </Center>
    );
  }

  const max = Math.max(...allValues);
  const min = Math.min(...allValues);
  const range = max - min || 1;
  const plotWidth = width - PADDING_LEFT - PADDING_RIGHT;
  const plotHeight = height - PADDING_BOTTOM - PADDING_TOP;
  const stepX = categories.length > 1 ? plotWidth / (categories.length - 1) : 0;

  function xFor(i: number): number {
    return PADDING_LEFT + i * stepX;
  }
  function yFor(value: number): number {
    return PADDING_TOP + plotHeight - ((value - min) / range) * plotHeight;
  }
  function bandPath(upper: number[], lower: number[]): string {
    const upperPoints = upper.map((v, i) => `${xFor(i)},${yFor(v)}`);
    const lowerPoints = lower
      .map((v, i) => `${xFor(i)},${yFor(v)}`)
      .slice()
      .reverse();
    return `M ${[...upperPoints, ...lowerPoints].join(' L ')} Z`;
  }
  function linePoints(values: number[]): string {
    return values.map((v, i) => `${xFor(i)},${yFor(v)}`).join(' ');
  }

  // Thin the x-axis labels to ~6 evenly spaced ticks regardless of horizon length.
  const tickEvery = Math.max(1, Math.ceil((categories.length - 1) / 6));
  const tickIndexes = categories
    .map((_, i) => i)
    .filter((i) => i === 0 || i === categories.length - 1 || i % tickEvery === 0);

  const hoveredCategoryX = hoveredIndex == null ? null : xFor(hoveredIndex);

  return (
    <VStack gap={2}>
      <svg viewBox={`0 0 ${width} ${height}`} style={{ width: '100%', height: 'auto' }} role="img" aria-hidden="true">
        <text x={0} y={PADDING_TOP + 8} fontSize={10} fill="var(--color-text-secondary)">
          {valueFormatter(max)}
        </text>
        <text x={0} y={height - PADDING_BOTTOM} fontSize={10} fill="var(--color-text-secondary)">
          {valueFormatter(min)}
        </text>

        <path d={bandPath(bands.p95, bands.p5)} fill="var(--color-icon-blue)" opacity={0.12} />
        <path d={bandPath(bands.p75, bands.p25)} fill="var(--color-icon-blue)" opacity={0.28} />

        {referenceValue != null && (
          <line
            x1={PADDING_LEFT}
            x2={width - PADDING_RIGHT}
            y1={yFor(referenceValue)}
            y2={yFor(referenceValue)}
            stroke="var(--color-border)"
            strokeDasharray="2,3"
          />
        )}

        <polyline points={linePoints(bands.p50)} fill="none" stroke="var(--color-icon-blue)" strokeWidth={2} strokeLinejoin="round" />

        {tickIndexes.map((i) => (
          <text
            key={String(categories[i])}
            x={xFor(i)}
            y={height - 4}
            fontSize={10}
            textAnchor={i === 0 ? 'start' : i === categories.length - 1 ? 'end' : 'middle'}
            fill="var(--color-text-secondary)"
          >
            {categories[i]}
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
        {hoveredIndex != null && (
          <circle cx={xFor(hoveredIndex)} cy={yFor(bands.p50[hoveredIndex])} r={3} fill="var(--color-icon-blue)" />
        )}

        {categories.map((category, i) => (
          <rect
            key={`hit-${String(category)}`}
            x={xFor(i) - stepX / 2}
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
            lines={[
              { label: '상위 5%', color: 'var(--color-icon-blue)', text: valueFormatter(bands.p95[hoveredIndex]) },
              { label: '상위 25%', color: 'var(--color-icon-blue)', text: valueFormatter(bands.p75[hoveredIndex]) },
              { label: '중앙값', color: 'var(--color-icon-blue)', text: valueFormatter(bands.p50[hoveredIndex]) },
              { label: '하위 25%', color: 'var(--color-icon-blue)', text: valueFormatter(bands.p25[hoveredIndex]) },
              { label: '하위 5%', color: 'var(--color-icon-blue)', text: valueFormatter(bands.p5[hoveredIndex]) },
            ]}
          />
        )}
      </svg>
      <HStack gap={4} wrap="wrap">
        <HStack gap={1.5} align="center">
          <svg width={10} height={10} role="img" aria-hidden="true">
            <rect width={10} height={10} rx={2} fill="var(--color-icon-blue)" />
          </svg>
          <Text type="supporting" size="xsm">
            중앙값 (50%)
          </Text>
        </HStack>
        <HStack gap={1.5} align="center">
          <svg width={10} height={10} role="img" aria-hidden="true">
            <rect width={10} height={10} rx={2} fill="var(--color-icon-blue)" opacity={0.28} />
          </svg>
          <Text type="supporting" size="xsm">
            25~75% 구간
          </Text>
        </HStack>
        <HStack gap={1.5} align="center">
          <svg width={10} height={10} role="img" aria-hidden="true">
            <rect width={10} height={10} rx={2} fill="var(--color-icon-blue)" opacity={0.12} />
          </svg>
          <Text type="supporting" size="xsm">
            5~95% 구간
          </Text>
        </HStack>
        {referenceValue != null && (
          <HStack gap={1.5} align="center">
            <svg width={10} height={10} role="img" aria-hidden="true">
              <line x1={0} y1={5} x2={10} y2={5} stroke="var(--color-border)" strokeDasharray="2,2" strokeWidth={1.5} />
            </svg>
            <Text type="supporting" size="xsm">
              {referenceLabel}
            </Text>
          </HStack>
        )}
      </HStack>
    </VStack>
  );
}
