'use client';

import { useEffect, useState } from 'react';
import { changePassword, getProfile, updateProfile } from '@/lib/api';
import { useAuth } from '@/lib/auth-context';
import styles from './profile.module.css';

type ProfileTab = 'account' | 'notifications' | 'security';

export default function ProfilePage() {
  const { authFetch } = useAuth();
  const [tab, setTab] = useState<ProfileTab>('account');
  const [loaded, setLoaded] = useState(false);
  const [email, setEmail] = useState('');
  const [username, setUsername] = useState('');
  const [dailyReportEnabled, setDailyReportEnabled] = useState(false);
  const [priceAlertEnabled, setPriceAlertEnabled] = useState(true);
  const [importantInfoEnabled, setImportantInfoEnabled] = useState(false);
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');

  useEffect(() => {
    getProfile(authFetch).then(profile => {
      setEmail(profile.email);
      setUsername(profile.username);
      setDailyReportEnabled(profile.dailyReportEnabled);
      setPriceAlertEnabled(profile.priceAlertEnabled);
      setImportantInfoEnabled(profile.importantInfoEnabled);
      if (window.location.hash === '#notifications') setTab('notifications');
      setLoaded(true);
    }).catch(() => setError('프로필을 불러오지 못했습니다.'));
  }, [authFetch]);

  async function saveProfile(event: React.FormEvent) {
    event.preventDefault(); setError(''); setMessage('');
    try {
      await updateProfile(authFetch, { username, dailyReportEnabled, priceAlertEnabled, importantInfoEnabled });
      setMessage(tab === 'notifications' ? '알림 설정을 저장했습니다.' : '프로필을 저장했습니다.');
    } catch { setError('설정을 저장하지 못했습니다.'); }
  }

  async function savePassword(event: React.FormEvent) {
    event.preventDefault(); setError(''); setMessage('');
    try {
      await changePassword(authFetch, { currentPassword, newPassword });
      setCurrentPassword(''); setNewPassword(''); setMessage('비밀번호를 변경했습니다.');
    } catch { setError('현재 비밀번호를 확인해 주세요.'); }
  }

  function selectTab(next: ProfileTab) {
    setTab(next); setMessage(''); setError('');
    history.replaceState(null, '', next === 'notifications' ? '#notifications' : window.location.pathname);
  }

  return <main className={styles.page}>
    <header><h1>프로필</h1><p>계정 정보와 알림 수신 방식을 관리합니다.</p></header>
    <nav className={styles.tabs} aria-label="프로필 설정">
      <button className={tab === 'account' ? styles.activeTab : ''} onClick={() => selectTab('account')}>기본 정보</button>
      <button className={tab === 'notifications' ? styles.activeTab : ''} onClick={() => selectTab('notifications')}>알림 설정</button>
      <button className={tab === 'security' ? styles.activeTab : ''} onClick={() => selectTab('security')}>비밀번호</button>
    </nav>
    {(message || error) && <p className={error ? styles.error : styles.success}>{error || message}</p>}

    {tab === 'account' && <form className={styles.card} onSubmit={saveProfile}>
      <h2>기본 정보</h2>
      <label>이메일<input value={email} disabled /></label>
      <label>노출 ID<input minLength={2} maxLength={30} value={username} onChange={e => setUsername(e.target.value)} required /></label>
      <button type="submit" disabled={!loaded}>저장</button>
    </form>}

    {tab === 'notifications' && <form className={styles.card} onSubmit={saveProfile}>
      <h2>알림 설정</h2>
      <p className={styles.description}>받고 싶은 알림만 켜둘 수 있습니다.</p>
      <NotificationToggle title="데일리 보고서" description="매일 오전 8시 시장 요약을 이메일로 받습니다." checked={dailyReportEnabled} onChange={setDailyReportEnabled} />
      <NotificationToggle title="관심 종목 가격 도달" description="설정한 목표가에 도달하면 앱에서 알려드립니다." checked={priceAlertEnabled} onChange={setPriceAlertEnabled} />
      <NotificationToggle title="중요 정보" description="시장 위험 변화나 서비스 주요 공지를 받습니다." checked={importantInfoEnabled} onChange={setImportantInfoEnabled} />
      <button type="submit" disabled={!loaded}>알림 설정 저장</button>
    </form>}

    {tab === 'security' && <form className={styles.card} onSubmit={savePassword}>
      <h2>비밀번호 변경</h2>
      <label>현재 비밀번호<input type="password" value={currentPassword} onChange={e => setCurrentPassword(e.target.value)} required /></label>
      <label>새 비밀번호<input type="password" minLength={8} maxLength={72} value={newPassword} onChange={e => setNewPassword(e.target.value)} required /></label>
      <button type="submit">비밀번호 변경</button>
    </form>}
  </main>;
}

function NotificationToggle({ title, description, checked, onChange }: {
  title: string; description: string; checked: boolean; onChange: (value: boolean) => void;
}) {
  return <label className={styles.notificationRow}>
    <span><strong>{title}</strong><small>{description}</small></span>
    <input type="checkbox" role="switch" checked={checked} onChange={event => onChange(event.target.checked)} />
  </label>;
}
