import { formatDataTime } from '@/lib/data-time';
import styles from './data-stamp.module.css';

export function DataStamp({ source, at, label = '기준 시각', note }: {
  source: string; at?: string | null; label?: string; note?: string;
}) {
  const formatted = formatDataTime(at);
  return <div className={styles.stamp}>
    <span>출처: {source}</span>
    <span>{label}: {formatted ? <time dateTime={at!}>{formatted}</time> : '미제공'}</span>
    {note && <span>{note}</span>}
  </div>;
}
