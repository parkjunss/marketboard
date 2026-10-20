'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
import { request } from '@/lib/api';
import styles from '../execution/execution.module.css';

type Example = { cash: string; shares: string; reservedCash: string; economicHash: string; log: { kind: string; reason: string }[] };
export default function ExecutionDemoPage() {
  const [example, setExample] = useState<Example | null>(null);
  const [error, setError] = useState('');
  useEffect(() => {
    const controller = new AbortController();
    void request<Example>('/api/execution/demo', { signal: controller.signal }).then(setExample).catch(e => {
      if (!controller.signal.aborted) setError(e instanceof Error ? e.message : '예제를 불러올 수 없습니다.');
    });
    return () => controller.abort();
  }, []);
  return <main className={styles.page}><header><p className={styles.eyebrow}>READ-ONLY EXAMPLE</p><h1>주문이 접수됐는데 응답이 사라지면?</h1><p>고정된 모의 장애를 서버에서 재현하는 읽기 전용 예제입니다. 사용자 계좌를 조회하거나 실제 주문을 보내지 않습니다.</p></header>
    <section className={styles.panel}><h2>응답 유실 → 부분 체결 → 취소 → 복구</h2><p>초기 자금 $10,000 · 고정 모의 가격 $100 · 수수료 5bp. 10주 주문 후 응답이 유실되고, 4주 체결 뒤 잔량을 취소합니다. 거래소 이벤트를 역순으로 수신해 계좌에 반영합니다.</p>
      {error ? <p role="alert">{error}</p> : !example ? <p role="status">서버에서 시나리오 계산 중…</p> : <><dl className={styles.metrics}><div><dt>복구 후 현금</dt><dd>{example.cash}</dd></div><div><dt>보유 수량</dt><dd>{example.shares}</dd></div><div><dt>남은 예약금</dt><dd>{example.reservedCash}</dd></div></dl><ol className={styles.log}>{example.log.map((entry, i) => <li key={i}><strong>{entry.kind}</strong> — {entry.reason}</li>)}</ol><p className={styles.hash}>결과 해시: {example.economicHash}</p></>}
    </section><section className={styles.panel}><h2>확인할 수 있는 설계</h2><p>타임아웃 동안 예약금을 유지하며, 동일 주문 ID로 대사합니다. 시퀀스로 이벤트 순서를 복구하고 중복 체결을 제거합니다. 금액 계산은 Python Decimal, 저장은 DB 트랜잭션이 담당합니다.</p><p>이 예제는 실거래 수익률·외부 거래소 안정성을 증명하지 않습니다. 실제 시세, 호가, 네트워크 시장 지연은 연결하지 않았습니다.</p><Link href="/execution">로그인하고 직접 장애 재현하기 →</Link></section>
  </main>;
}
