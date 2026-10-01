import { HStack } from '@astryxdesign/core/Stack';
import { Text } from '@astryxdesign/core/Text';
import { Icon } from '@astryxdesign/core/Icon';

export function PriceChangeIndicator({
  changeValue,
  changePct,
}: {
  changeValue: number | null;
  changePct: number | null;
}) {
  if (changeValue == null || changePct == null) {
    return <Text type="supporting">—</Text>;
  }
  if (changeValue === 0) {
    return (
      <Text type="body" color="secondary" hasTabularNumbers>
        0.00 (0.00%)
      </Text>
    );
  }
  const isUp = changeValue > 0;
  const sign = isUp ? '+' : '';
  return (
    <span style={{ color: isUp ? 'var(--mb-positive)' : 'var(--mb-negative)' }}>
      <HStack gap={1} align="center">
        <Icon icon={isUp ? 'arrowUp' : 'arrowDown'} color="inherit" size="sm" />
        <Text type="body" color="inherit" hasTabularNumbers>
          {sign}
          {changeValue.toFixed(2)} ({sign}
          {changePct.toFixed(2)}%)
        </Text>
      </HStack>
    </span>
  );
}
