'use client';

import { useState } from 'react';
import { VStack } from '@astryxdesign/core/Stack';
import { Center } from '@astryxdesign/core/Center';
import { Text } from '@astryxdesign/core/Text';

export interface ScatterPoint {
  label: string;
  volatilityPct: number;
  returnPct: number;
  color: string;
}

const PADDING_LEFT = 48;
const PADDING_BOTTOM = 32;
const PADDING_TOP = 16;
const PADDING_RIGHT = 16;
const POINT_RADIUS = 5;
const TICK_COUNT = 4;

function niceRange(min: number, max: number): [number, number] {
  if (min === max) return [min - 1, max + 1];
  const pad = (max - min) * 0.15;
  return [min - pad, max + pad];
}

export function ScatterChart({ points, width = 640, height = 360 }: { points: ScatterPoint[]; width?: number; height?: number }) {
  const [hovered, setHovered] = useState<number | null>(null);

  const withVolatility = points.filter((p) => Number.isFinite(p.volatilityPct));
  if (withVolatility.length === 0) {
    return (
      <Center height={height}>
        <Text type="body" color="secondary">
          변동성 데이터가 없습니다
        </Text>
      </Center>
    );
  }

  const [xMin, xMax] = niceRange(0, Math.max(...withVolatility.map((p) => p.volatilityPct)));
  const [yMin, yMax] = niceRange(
    Math.min(...withVolatility.map((p) => p.returnPct), 0),
    Math.max(...withVolatility.map((p) => p.returnPct), 0),
  );
  const plotWidth = width - PADDING_LEFT - PADDING_RIGHT;
  const plotHeight = height - PADDING_BOTTOM - PADDING_TOP;

  function xFor(v: number): number {
    return PADDING_LEFT + ((v - xMin) / (xMax - xMin)) * plotWidth;
  }
  function yFor(v: number): number {
    return PADDING_TOP + plotHeight - ((v - yMin) / (yMax - yMin)) * plotHeight;
  }

  const hoveredPoint = hovered != null ? withVolatility[hovered] : null;
  const xTicks = Array.from({ length: TICK_COUNT + 1 }, (_, i) => xMin + ((xMax - xMin) * i) / TICK_COUNT);
  const yTicks = Array.from({ length: TICK_COUNT + 1 }, (_, i) => yMin + ((yMax - yMin) * i) / TICK_COUNT);

  return (
    <VStack gap={2}>
      <svg viewBox={`0 0 ${width} ${height}`} style={{ width: '100%', height: 'auto' }} role="img" aria-label="종목별 연환산 변동성과 수익률 비교 차트">
        {yTicks.map((tick) => <g key={`y-${tick}`}><line x1={PADDING_LEFT} x2={width - PADDING_RIGHT} y1={yFor(tick)} y2={yFor(tick)} stroke="var(--mb-border)" strokeWidth={0.7} /><text x={PADDING_LEFT - 8} y={yFor(tick) + 3} fontSize={9} textAnchor="end" fill="var(--mb-text-muted)">{tick.toFixed(0)}%</text></g>)}
        {xTicks.map((tick) => <g key={`x-${tick}`}><line x1={xFor(tick)} x2={xFor(tick)} y1={PADDING_TOP} y2={PADDING_TOP + plotHeight} stroke="var(--mb-border)" strokeWidth={0.7} /><text x={xFor(tick)} y={height - 17} fontSize={9} textAnchor="middle" fill="var(--mb-text-muted)">{tick.toFixed(0)}%</text></g>)}
        <line x1={PADDING_LEFT} x2={width - PADDING_RIGHT} y1={yFor(0)} y2={yFor(0)} stroke="var(--mb-text-muted)" strokeDasharray="3,4" />
        <line
          x1={xFor(Math.max(xMin, 0))}
          x2={xFor(Math.max(xMin, 0))}
          y1={PADDING_TOP}
          y2={PADDING_TOP + plotHeight}
          stroke="var(--mb-text-muted)"
          strokeDasharray="2,3"
        />
        <line x1={PADDING_LEFT} x2={width - PADDING_RIGHT} y1={PADDING_TOP + plotHeight} y2={PADDING_TOP + plotHeight} stroke="var(--mb-border)" />
        <line x1={PADDING_LEFT} x2={PADDING_LEFT} y1={PADDING_TOP} y2={PADDING_TOP + plotHeight} stroke="var(--mb-border)" />

        <text x={PADDING_LEFT + plotWidth / 2} y={height - 4} fontSize={10} textAnchor="middle" fill="var(--mb-text-muted)">
          변동성 (연율화, %)
        </text>
        <text
          x={-(PADDING_TOP + plotHeight / 2)}
          y={12}
          fontSize={10}
          textAnchor="middle"
          fill="var(--mb-text-muted)"
          transform="rotate(-90)"
        >
          수익률 (%)
        </text>

        {withVolatility.map((p, i) => {
          const isHovered = hovered === i;
          return (
            <g key={p.label} onMouseEnter={() => setHovered(i)} onMouseLeave={() => setHovered(null)}>
              {isHovered && <>
                <line x1={PADDING_LEFT} x2={xFor(p.volatilityPct)} y1={yFor(p.returnPct)} y2={yFor(p.returnPct)} stroke={p.color} strokeDasharray="3,4" opacity={0.65} />
                <line x1={xFor(p.volatilityPct)} x2={xFor(p.volatilityPct)} y1={yFor(p.returnPct)} y2={PADDING_TOP + plotHeight} stroke={p.color} strokeDasharray="3,4" opacity={0.65} />
              </>}
              <circle
                cx={xFor(p.volatilityPct)}
                cy={yFor(p.returnPct)}
                r={isHovered ? POINT_RADIUS + 2 : POINT_RADIUS}
                fill={p.color}
                stroke="var(--mb-surface)"
                strokeWidth={2}
              />
              <text
                x={xFor(p.volatilityPct) + POINT_RADIUS + 4}
                y={yFor(p.returnPct) + 3}
                fontSize={10}
                fill="var(--mb-text)"
              >
                {p.label}
              </text>
            </g>
          );
        })}

        {hoveredPoint && (
          <g pointerEvents="none">
            {(() => {
              const cx = xFor(hoveredPoint.volatilityPct);
              const cy = yFor(hoveredPoint.returnPct);
              const boxWidth = 130;
              const boxHeight = 40;
              const flip = cx + 12 + boxWidth > width;
              const boxX = flip ? cx - 12 - boxWidth : cx + 12;
              const boxY = Math.min(Math.max(cy - boxHeight / 2, PADDING_TOP), PADDING_TOP + plotHeight - boxHeight);
              return (
                <>
                  <rect x={boxX} y={boxY} width={boxWidth} height={boxHeight} rx={5} fill="var(--mb-surface-raised)" stroke="var(--mb-border)" />
                  <text x={boxX + 8} y={boxY + 15} fontSize={10} fontWeight={700} fill="var(--mb-text)">
                    {hoveredPoint.label}
                  </text>
                  <text x={boxX + 8} y={boxY + 29} fontSize={10} fill="var(--mb-text-muted)">
                    수익률 {hoveredPoint.returnPct.toFixed(2)}% · 변동성 {hoveredPoint.volatilityPct.toFixed(2)}%
                  </text>
                </>
              );
            })()}
          </g>
        )}
      </svg>
    </VStack>
  );
}
