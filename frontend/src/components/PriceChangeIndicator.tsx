import { HStack } from '@astryxdesign/core/Stack';
import { Text } from '@astryxdesign/core/Text';
import { Icon } from '@astryxdesign/core/Icon';

/**
 * 국내 증시 표기 관행(상승 = 빨강, 하락 = 파랑)을 따름 — Astryx의 success/error(초록/빨강) 시맨틱
 * 색상은 이 방향과 반대라 재사용하지 않고, 실제 컬러 토큰(--color-text-red/--color-text-blue)을
 * 직접 사용함(financials 페이지 차트 색상에서도 이미 같은 방식으로 --color-icon-* 토큰을 직접 씀).
 */
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
    <span style={{ color: isUp ? 'var(--color-text-red)' : 'var(--color-text-blue)' }}>
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
