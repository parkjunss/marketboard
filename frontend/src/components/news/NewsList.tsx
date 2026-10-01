'use client';

import { useState } from 'react';
import { NewspaperIcon } from '@heroicons/react/24/outline';
import type { NewsItem } from '@/lib/types';
import styles from './news-list.module.css';

function NewsThumbnail({ item }: { item: NewsItem }) {
  const [failed, setFailed] = useState(false);
  if (!item.image || failed) return <span className={styles.placeholder}><NewspaperIcon /></span>;
  // Finnhub returns thumbnails from multiple publisher hosts, so Next Image cannot whitelist them reliably.
  // eslint-disable-next-line @next/next/no-img-element
  return <img className={styles.thumbnail} src={item.image} alt="" loading="lazy" onError={() => setFailed(true)} />;
}

export function NewsList({ items, compact = false }: { items: NewsItem[]; compact?: boolean }) {
  return <div className={`${styles.list} ${compact ? styles.compact : ''}`}>
    {items.map((item) => <a key={item.id} className={styles.card} href={item.url} target="_blank" rel="noopener noreferrer">
      <NewsThumbnail item={item} />
      <div className={styles.content}>
        <div className={styles.meta}><span>{item.source || 'Market News'}</span><time>{new Date(item.datetime * 1000).toLocaleString('ko-KR')}</time></div>
        <h2>{item.headline}</h2>
        {!compact && item.summary && <p>{item.summary}</p>}
      </div>
    </a>)}
  </div>;
}
