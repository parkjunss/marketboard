'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { useAuth } from '@/lib/auth-context';
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

export function MarketMovers() {
  const { authFetch } = useAuth();
  const [movers, setMovers] = useState<Mover[] | null>(null);

  useEffect(() => {
    let cancelled = false;
    api.getQuotes(authFetch).then(async (quotes) => {
      const usable = quotes.filter((quote) => quote.price != null).slice(0, 40);
      const rows = await Promise.all(usable.map(async (quote) => {
        try {
          const candles = await api.getHistory(authFetch, quote.symbol, '1d', 2);
          const price = quote.price ?? candles.at(-1)?.close;
          const previous = resolvePrevClose(candles.map((candle) => candle.close), price ?? null);
          if (price == null || previous == null) return null;
          return { ticker: quote.symbol, name: quote.name ?? quote.symbol, price, changePct: ((price - previous) / previous) * 100 };
        } catch { return null; }
      }));
      if (cancelled) return;
      setMovers(rows.filter((row): row is Mover => row !== null));
    }).catch(() => { if (!cancelled) setMovers([]); });
    return () => { cancelled = true; };
  }, [authFetch]);

  const rising = movers?.filter((row) => row.changePct > 0).sort((a, b) => b.changePct - a.changePct).slice(0, 5) ?? movers;
  const falling = movers?.filter((row) => row.changePct < 0).sort((a, b) => a.changePct - b.changePct).slice(0, 5) ?? movers;
  return <><MoversSection direction="up" movers={rising} /><MoversSection direction="down" movers={falling} /></>;
}
