'use client';

import { useState } from 'react';
import styles from './auto-trading.module.css';

type Point = { date: string; strategy: string; benchmark: string };
export default function EquityChart({ points, labels = ['내 전략', '매수·보유'] }: { points: Point[]; labels?: [string, string] }) {
  const [active, setActive] = useState<number | null>(null);
  if (!points.length) return <div className={styles.empty}>첫 거래일이 처리되면 자산 추이가 표시됩니다.</div>;
  const values = points.flatMap(p => [Number(p.strategy), Number(p.benchmark)]);
  const min = Math.min(...values), max = Math.max(...values);
  const pad = Math.max((max - min) * .18, max * .0002, .01);
  const low = min - pad, high = max + pad;
  const x = (i: number) => 16 + i * 704 / Math.max(1, points.length - 1);
  const y = (v: number) => 244 - (v - low) / (high - low) * 216;
  const line = (key: 'strategy' | 'benchmark') => points.map((p, i) => `${x(i)},${y(Number(p[key]))}`).join(' ');
  const index = Math.min(active ?? points.length - 1, points.length - 1);
  const point = points[index];
  const money = (value: string | number) => Number(value).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  return <figure className={styles.chart}>
    <figcaption className={styles.chartLegend}>
      <span className={styles.chartDate}>{point.date}</span>
      <span><i className={styles.mintDot} />{labels[0]} <strong>${money(point.strategy)}</strong></span>
      <span><i className={styles.grayDot} />{labels[1]} <strong>${money(point.benchmark)}</strong></span>
    </figcaption>
    <svg viewBox="0 0 800 280" role="img" aria-label={`${labels[0]}와 ${labels[1]}의 일별 평가 자산 추이. 아래 날짜 선택으로 값을 확인하세요.`}
      onPointerMove={e => { const box = e.currentTarget.getBoundingClientRect(); setActive(Math.max(0, Math.min(points.length - 1, Math.round(((e.clientX - box.left) / box.width * 800 - 16) / 704 * (points.length - 1))))); }}
      onPointerLeave={() => setActive(null)}>
      {[0, 1, 2, 3].map(i => {
        const value = low + (high - low) * i / 3;
        return <g key={i}><line x1="16" x2="720" y1={y(value)} y2={y(value)} className={styles.gridLine} />
          <text x="788" y={y(value) + 4} textAnchor="end">{money(value)}</text></g>;
      })}
      <polygon points={`16,244 ${line('strategy')} ${x(points.length - 1)},244`} fill="var(--mb-accent)" opacity=".035" />
      <polyline points={line('benchmark')} fill="none" stroke="var(--mb-text-muted)" strokeWidth="1.5" strokeDasharray="5 5" />
      <polyline points={line('strategy')} fill="none" stroke="var(--mb-accent)" strokeWidth="2.4" strokeLinejoin="round" />
      <line x1={x(index)} x2={x(index)} y1="22" y2="244" className={styles.crosshair} />
      <circle cx={x(index)} cy={y(Number(point.strategy))} r="4" fill="var(--mb-accent)" />
      <text x="16" y="273">{points[0].date}</text>
      {points.length > 3 && <text x="368" y="273" textAnchor="middle">{points[Math.floor(points.length / 2)].date}</text>}
      <text x="720" y="273" textAnchor="end">{points[points.length - 1].date}</text>
    </svg>
    <label className={styles.chartScrubber}>날짜 탐색
      <input aria-label="그래프 날짜 선택" type="range" min="0" max={points.length - 1} value={index} onChange={e => setActive(Number(e.target.value))} />
      <span>{index + 1} / {points.length}</span>
    </label>
  </figure>;
}
