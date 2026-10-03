'use client';

import { useState } from 'react';
import Link from 'next/link';
import { resetPassword } from '@/lib/api';
import styles from '../auth.module.css';

export default function ResetPasswordPage() {
  const [password, setPassword] = useState('');
  const [done, setDone] = useState(false);
  const [error, setError] = useState('');

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setError('');
    const token = new URLSearchParams(window.location.search).get('token');
    if (!token) return setError('유효하지 않은 재설정 링크입니다.');
    try {
      await resetPassword(token, password);
      setDone(true);
    } catch {
      setError('링크가 만료되었거나 이미 사용되었습니다.');
    }
  }

  return <main className={styles.page}><form className={styles.card} onSubmit={submit}>
    <h1>새 비밀번호 설정</h1>
    {done ? <><p className={styles.success}>비밀번호가 변경되었습니다.</p><Link href="/login">로그인하기</Link></> : <>
      <label>새 비밀번호<input type="password" minLength={8} maxLength={72} value={password} onChange={e => setPassword(e.target.value)} required /></label>
      {error && <p className={styles.error}>{error}</p>}
      <button type="submit">비밀번호 변경</button>
    </>}
  </form></main>;
}
