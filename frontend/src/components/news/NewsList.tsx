'use client';

import { useState } from 'react';
import { NewspaperIcon } from '@heroicons/react/24/outline';
import type { NewsItem } from '@/lib/types';
import { newsSummary, uniqueNews } from '@/lib/news-content';
import { formatDataTime } from '@/lib/data-time';
import styles from './news-list.module.css';

function NewsThumbnail({ item }: { item: NewsItem }) {
  const [failed, setFailed] = useState(false);
  if (!item.image || failed) return <span className={styles.placeholder}><NewspaperIcon /></span>;
  // Finnhub returns thumbnails from multiple publisher hosts, so Next Image cannot whitelist them reliably.
  // eslint-disable-next-line @next/next/no-img-element
  return <img className={styles.thumbnail} src={item.image} alt="" loading="lazy" onError={() => setFailed(true)} />;
}

export function NewsList({ items, compact = false, limit }: { items: NewsItem[]; compact?: boolean; limit?: number }) {
  const unique = uniqueNews(items);
  const imageCounts = new Map<string, number>();
  unique.forEach(item => { if (item.image) imageCounts.set(item.image, (imageCounts.get(item.image) ?? 0) + 1); });
  const visible = limit ? unique.slice(0, limit) : unique;
  return <div className={`${styles.list} ${compact ? styles.compact : ''}`}>
    {visible.map((item) => {
      const summary = newsSummary(item.headline, item.summary);
      const showImage = !compact && item.image && imageCounts.get(item.image) === 1;
      const date = new Date(item.datetime * 1000);
      const publishedAt = Number.isFinite(date.getTime()) && item.datetime > 0 ? date.toISOString() : undefined;
      return <a key={item.id} className={`${styles.card} ${showImage ? '' : styles.textOnly}`} href={item.url} target="_blank" rel="noopener noreferrer">
      {showImage && <NewsThumbnail item={item} />}
      <div className={styles.content}>
        <div className={styles.meta}><span>{item.source || 'Market News'}</span><time dateTime={publishedAt}>{formatDataTime(publishedAt) ?? '발행 시각 미제공'}</time></div>
        <h2>{item.headline}</h2>
        {!compact && summary && <p>{summary}</p>}
      </div>
    </a>; })}
  </div>;
}
