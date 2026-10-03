'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { BellAlertIcon, CheckIcon } from '@heroicons/react/24/outline';
import { getNotifications, markAllNotificationsRead, markNotificationRead } from '@/lib/api';
import type { NotificationListResponse, NotificationResponse, NotificationType } from '@/lib/types';
import { useAuth } from '@/lib/auth-context';
import styles from './notifications.module.css';

const typeLabels: Record<NotificationType, string> = { PRICE_ALERT: '가격 도달', DAILY_REPORT: '데일리 보고서', IMPORTANT_INFO: '중요 정보' };

export default function NotificationsPage() {
  const { authFetch } = useAuth();
  const router = useRouter();
  const [data, setData] = useState<NotificationListResponse>({ items: [], unreadCount: 0 });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    let active = true;
    void getNotifications(authFetch).then((result) => { if (active) { setData(result); setError(''); } }).catch(() => { if (active) setError('알림을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.'); }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [authFetch]);

  async function openNotification(notification: NotificationResponse) {
    if (!notification.readAt) {
      await markNotificationRead(authFetch, notification.id);
      setData((current) => ({ items: current.items.map((item) => item.id === notification.id ? { ...item, readAt: new Date().toISOString() } : item), unreadCount: Math.max(0, current.unreadCount - 1) }));
      window.dispatchEvent(new Event('marketboard:notifications-changed'));
    }
    if (notification.link) router.push(notification.link);
  }

  async function markAllRead() {
    await markAllNotificationsRead(authFetch);
    const readAt = new Date().toISOString();
    setData((current) => ({ items: current.items.map((item) => ({ ...item, readAt: item.readAt ?? readAt })), unreadCount: 0 }));
    window.dispatchEvent(new Event('marketboard:notifications-changed'));
  }

  return <main className={styles.page}>
    <header className={styles.header}><div><p>NOTIFICATIONS</p><h1>알림</h1><span>가격 도달, 데일리 보고서와 중요 정보를 한곳에서 확인하세요.</span></div><button type="button" onClick={() => void markAllRead()} disabled={data.unreadCount === 0}><CheckIcon />모두 읽음</button></header>
    {error && <p className={styles.error}>{error}</p>}
    {loading ? <p className={styles.state}>알림을 불러오는 중입니다.</p> : data.items.length === 0 ? <section className={styles.empty}><BellAlertIcon /><h2>아직 알림이 없습니다</h2><p>설정한 조건이 충족되면 이곳에 알림이 쌓입니다.</p></section> :
      <section className={styles.list} aria-label="알림 목록">{data.items.map((notification) => <button type="button" key={notification.id} className={`${styles.item} ${notification.readAt ? '' : styles.unread}`} onClick={() => void openNotification(notification)}><span className={styles.dot} aria-hidden="true" /><span className={styles.body}><span className={styles.meta}><strong>{typeLabels[notification.type]}</strong><time dateTime={notification.createdAt}>{new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(notification.createdAt))}</time></span><b>{notification.title}</b><span>{notification.message}</span></span></button>)}</section>}
  </main>;
}
