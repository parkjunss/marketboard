'use client';

import { useState } from 'react';
import Link from 'next/link';
import { forgotPassword } from '@/lib/api';
import styles from '../auth.module.css';

export default function ForgotPasswordPage() {
  const [email, setEmail] = useState('');
  const [sent, setSent] = useState(false);
  const [error, setError] = useState('');

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setError('');
    try {
      await forgotPassword(email);
      setSent(true);
    } catch {
      setError('요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.');
    }
  }

  return <main className={styles.page}><form className={styles.card} onSubmit={submit}>
    <h1>비밀번호 찾기</h1>
    <p>가입한 이메일로 30분 동안 유효한 재설정 링크를 보냅니다.</p>
    {sent ? <p className={styles.success}>계정이 존재하면 이메일이 발송됩니다.</p> : <>
      <label>이메일<input type="email" value={email} onChange={e => setEmail(e.target.value)} required /></label>
      {error && <p className={styles.error}>{error}</p>}
      <button type="submit">재설정 링크 받기</button>
    </>}
    <Link href="/login">로그인으로 돌아가기</Link>
  </form></main>;
}
