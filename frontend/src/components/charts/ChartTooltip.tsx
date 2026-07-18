export interface TooltipLine {
  label: string;
  color: string;
  text: string;
}

const LINE_HEIGHT = 14;
const PADDING = 6;
const BOX_WIDTH = 160;

export function ChartTooltip({ x, chartWidth, title, lines }: { x: number; chartWidth: number; title: string; lines: TooltipLine[] }) {
  const boxHeight = PADDING * 2 + LINE_HEIGHT * (lines.length + 1);
  const flip = x + 10 + BOX_WIDTH > chartWidth;
  const boxX = flip ? x - 10 - BOX_WIDTH : x + 10;
  const boxY = 4;

  return (
    <g pointerEvents="none">
      <rect
        x={boxX}
        y={boxY}
        width={BOX_WIDTH}
        height={boxHeight}
        rx={4}
        fill="var(--color-background-popover)"
        stroke="var(--color-border)"
      />
      <text x={boxX + PADDING} y={boxY + PADDING + 9} fontSize={10} fontWeight={700} fill="var(--color-text-primary)">
        {title}
      </text>
      {lines.map((line, i) => (
        <g key={line.label}>
          <rect x={boxX + PADDING} y={boxY + PADDING + LINE_HEIGHT * (i + 1) + 2} width={8} height={8} rx={2} fill={line.color} />
          <text
            x={boxX + PADDING + 12}
            y={boxY + PADDING + LINE_HEIGHT * (i + 1) + 10}
            fontSize={10}
            fill="var(--color-text-secondary)"
          >
            {line.label}: {line.text}
          </text>
        </g>
      ))}
    </g>
  );
}
