'use client';

import { useEffect, useState } from 'react';
import { List, ListItem } from '@astryxdesign/core/List';
import { VStack } from '@astryxdesign/core/Stack';
import { Center } from '@astryxdesign/core/Center';
import { Spinner } from '@astryxdesign/core/Spinner';
import { Text } from '@astryxdesign/core/Text';
import { useAuth } from '@/lib/auth-context';
import * as api from '@/lib/api';
import type { NewsItem } from '@/lib/types';

export function NewsPanel({ ticker }: { ticker?: string }) {
  const { authFetch } = useAuth();
  // Keyed by ticker so isLoading derives from render-time comparison instead of an effect
  // calling setState synchronously (see react-hooks/set-state-in-effect).
  const requestKey = ticker ?? '__general__';
  const [result, setResult] = useState<{ key: string; items: NewsItem[] } | null>(null);
  const isLoading = result?.key !== requestKey;
  const items = isLoading ? [] : result.items;

  useEffect(() => {
    let cancelled = false;
    const fetchNews = ticker ? api.getCompanyNews(authFetch, ticker) : api.getGeneralNews(authFetch);
    fetchNews
      .then((data) => {
        if (!cancelled) setResult({ key: requestKey, items: data.slice(0, 8) });
      })
      .catch(() => {
        // Best-effort external data -- degrade to "뉴스가 없습니다" rather than spin forever,
        // e.g. right after a fresh deploy before the first scheduled refresh has landed.
        if (!cancelled) setResult({ key: requestKey, items: [] });
      });
    return () => {
      cancelled = true;
    };
  }, [authFetch, ticker, requestKey]);

  if (isLoading) {
    return (
      <Center height={280}>
        <Spinner size="md" label="뉴스 불러오는 중" />
      </Center>
    );
  }

  if (items.length === 0) {
    return (
      <Center height={280}>
        <Text type="body" color="secondary">
          뉴스가 없습니다
        </Text>
      </Center>
    );
  }

  return (
    <VStack height={320} isScrollable>
      <List hasDividers>
        {items.map((item) => (
          <ListItem
            key={item.id}
            label={item.headline}
            description={`${item.source} · ${new Date(item.datetime * 1000).toLocaleString('ko-KR')}`}
            href={item.url}
            target="_blank"
          />
        ))}
      </List>
    </VStack>
  );
}
