'use client';

import { useState } from 'react';
import { VStack, HStack } from '@astryxdesign/core/Stack';
import { SegmentedControl, SegmentedControlItem } from '@astryxdesign/core/SegmentedControl';
import { Center } from '@astryxdesign/core/Center';
import { Spinner } from '@astryxdesign/core/Spinner';
import { Text } from '@astryxdesign/core/Text';
import { CandleChart } from '@/components/CandleChart';
import { useAuth } from '@/lib/auth-context';
import { useQuoteStream } from '@/lib/quote-stream-context';
import { useCandles, type Timeframe } from '@/lib/candles';

export function ChartPanel({ ticker }: { ticker: string }) {
  const { authFetch } = useAuth();
  const { quotes } = useQuoteStream();
  const [timeframe, setTimeframe] = useState<Timeframe>('1d');
  const { candles, isLoading } = useCandles(authFetch, ticker, timeframe, quotes[ticker]);

  if (!ticker) {
    return (
      <Center height={280}>
        <Text type="body" color="secondary">
          종목을 입력하세요
        </Text>
      </Center>
    );
  }

  return (
    <VStack gap={2}>
      <HStack justify="end">
        <SegmentedControl size="sm" value={timeframe} onChange={(value) => setTimeframe(value as Timeframe)} label="차트 기간">
          <SegmentedControlItem value="1d" label="일봉" />
          <SegmentedControlItem value="1m" label="분봉" />
        </SegmentedControl>
      </HStack>
      {isLoading ? (
        <Center height={280}>
          <Spinner size="md" label="차트 불러오는 중" />
        </Center>
      ) : (
        <CandleChart candles={candles} height={280} />
      )}
    </VStack>
  );
}
