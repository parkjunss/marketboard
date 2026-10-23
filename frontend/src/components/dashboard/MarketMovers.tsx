'use client';

import { useResource } from '@/lib/use-resource';
import { ResourceState } from '@/components/ResourceState';
import Link from 'next/link';
import { resolvePrevClose } from '@/lib/priceChange';
import * as api from '@/lib/api';
import styles from './dashboard.module.css';

interface Mover { ticker: string; name: string; price: number; changePct: number }

function MoversSection({ direction, movers }: { direction: 'up' | 'down'; movers: Mover[] | null }) {
  return <section className={styles.section}>
    <h2 className={styles.sectionTitle}>{direction === 'up' ? '상위 상승 종목' : '상위 하락 종목'}</h2>
    <div className={styles.moverList}>
      {movers === null ? <p className={styles.empty}>종목을 불러오는 중입니다</p> : movers.length === 0 ? <p className={styles.empty}>표시할 종목이 없습니다</p> : movers.map((mover, index) => <Link href={`/symbols/${mover.ticker}`} className={styles.moverRow} key={mover.ticker}>
        <span className={styles.rank}>{index + 1}</span><span className={styles.moverName}><strong>{mover.ticker}</strong><small>{mover.name}</small></span><span>${mover.price.toFixed(2)}</span><strong className={direction === 'up' ? styles.positive : styles.negative}>{mover.changePct > 0 ? '+' : ''}{mover.changePct.toFixed(2)}%</strong>
      </Link>)}
    </div>
  </section>;
}

async function loadMovers(fetcher: api.Fetcher): Promise<Mover[]> {
  const quotes = await api.getQuotes(fetcher);
  const usable = quotes.filter(quote => quote.price != null).slice(0, 40);
  const rows = await Promise.all(usable.map(async quote => {
    const candles = await api.getHistory(fetcher, quote.symbol, '1d', 2);
    const price = quote.price ?? candles.at(-1)?.close;
    const previous = resolvePrevClose(candles.map(candle => candle.close), price ?? null);
    if (price == null || previous == null || previous === 0) return null;
    return { ticker: quote.symbol, name: quote.name ?? quote.symbol, price, changePct: ((price - previous) / previous) * 100 };
  }));
  return rows.filter((row): row is Mover => row !== null);
}

export function MarketMovers() {
  const resource = useResource(loadMovers);
  if (resource.status !== 'success') return <section className={styles.section}><h2 className={styles.sectionTitle}>상승·하락 종목</h2><ResourceState status={resource.status} label="상승·하락 종목" onRetry={resource.retry} /></section>;
  const movers = resource.data;
  const rising = movers?.filter((row) => row.changePct > 0).sort((a, b) => b.changePct - a.changePct).slice(0, 5) ?? movers;
  const falling = movers?.filter((row) => row.changePct < 0).sort((a, b) => a.changePct - b.changePct).slice(0, 5) ?? movers;
  return <><MoversSection direction="up" movers={rising} /><MoversSection direction="down" movers={falling} /></>;
}
