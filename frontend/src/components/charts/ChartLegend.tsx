import { HStack } from '@astryxdesign/core/Stack';
import { Text } from '@astryxdesign/core/Text';

export interface ChartSeriesMeta {
  label: string;
  color: string;
}

export function ChartLegend({ series }: { series: ChartSeriesMeta[] }) {
  return (
    <HStack gap={4} wrap="wrap">
      {series.map((s) => (
        <HStack key={s.label} gap={1.5} align="center">
          <svg width={10} height={10} role="img" aria-hidden="true">
            <rect width={10} height={10} rx={2} fill={s.color} />
          </svg>
          <Text type="supporting" size="xsm">
            {s.label}
          </Text>
        </HStack>
      ))}
    </HStack>
  );
}
