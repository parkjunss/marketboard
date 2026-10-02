'use client';

import { useState } from 'react';
import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import {
  ArrowLeftStartOnRectangleIcon, BanknotesIcon, Bars3Icon, BeakerIcon, BellIcon, BriefcaseIcon,
  ChartBarIcon, ChartBarSquareIcon, Cog6ToothIcon, GlobeAltIcon, HeartIcon, HomeIcon,
  MagnifyingGlassIcon, NewspaperIcon, ShieldCheckIcon, XMarkIcon,
} from '@heroicons/react/24/outline';
import { useAuth } from '@/lib/auth-context';
import styles from './app-shell.module.css';

const marketNavigation = [
  { label: '대시보드', href: '/dashboard', icon: HomeIcon },
  { label: '투자 점검', href: '/review', icon: ShieldCheckIcon },
  { label: '종목 검색', href: '/stock-list', icon: MagnifyingGlassIcon },
  { label: '스크리너', href: '/screener', icon: BeakerIcon },
  { label: '시장 동향', href: '/market', icon: GlobeAltIcon },
  { label: '뉴스', href: '/news', icon: NewspaperIcon },
  { label: '재무 비교', href: '/financials', icon: BanknotesIcon },
];

const publicNavigation = marketNavigation.filter(({ href }) =>
  ['/dashboard', '/stock-list', '/market', '/news'].includes(href),
);

const accountNavigation = [
  { label: '관심종목', href: '/watchlist', icon: HeartIcon },
  { label: '포트폴리오', href: '/portfolio', icon: BriefcaseIcon },
  { label: '백테스팅', href: '/backtest', icon: ChartBarSquareIcon },
  { label: '프로필', href: '/profile', icon: Cog6ToothIcon },
];

function isActive(pathname: string, href: string) {
  return pathname === href || (href !== '/dashboard' && pathname.startsWith(`${href}/`));
}

export function AppNavigation({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const { user, logout } = useAuth();
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState('');
  const displayName = user?.username || user?.email?.split('@')[0] || '게스트';
  const visibleMarketNavigation = user ? marketNavigation : publicNavigation;

  function submitSearch(event: React.FormEvent) {
    event.preventDefault();
    const ticker = query.trim().toUpperCase();
    if (!ticker) return;
    router.push(`/symbols/${encodeURIComponent(ticker)}`);
    setQuery('');
  }

  return <div className={styles.shell}>
    <button className={styles.mobileToggle} onClick={() => setOpen(true)} aria-label="메뉴 열기"><Bars3Icon /></button>
    {open && <button className={styles.backdrop} onClick={() => setOpen(false)} aria-label="메뉴 닫기" />}
    <aside className={`${styles.sidebar} ${open ? styles.sidebarOpen : ''}`}>
      <div className={styles.brand}><span><ChartBarIcon /></span><strong>MarketBoard</strong><button onClick={() => setOpen(false)} aria-label="메뉴 닫기"><XMarkIcon /></button></div>
      <nav className={styles.nav} aria-label="주요 메뉴">
        <p>MARKET</p>
        {visibleMarketNavigation.map((item) => <Link key={`${item.label}-${item.href}`} href={item.href} onClick={() => setOpen(false)} className={isActive(pathname, item.href) ? styles.active : undefined}><item.icon /><span>{item.label}</span></Link>)}
        {user && <><p>ACCOUNT</p>{accountNavigation.map((item) => <Link key={`${item.label}-${item.href}`} href={item.href} onClick={() => setOpen(false)} className={isActive(pathname, item.href) ? styles.active : undefined}><item.icon /><span>{item.label}</span></Link>)}</>}
        {user?.role === 'ADMIN' && <Link href="/admin/symbols" className={pathname.startsWith('/admin') ? styles.active : undefined}><Cog6ToothIcon /><span>관리자</span></Link>}
      </nav>
      <div className={styles.sidebarFooter}>
        <div className={styles.profile}><span>{displayName.slice(0, 1).toUpperCase()}</span><div><strong>{displayName}</strong><small>{user?.role === 'ADMIN' ? '관리자' : '투자자'}</small></div></div>
        {user ? <button onClick={() => { void logout(); router.replace('/dashboard'); }}><ArrowLeftStartOnRectangleIcon />로그아웃</button> : <Link className={styles.loginLink} href="/login">로그인</Link>}
      </div>
    </aside>
    <div className={styles.workspace}>
      <header className={styles.topbar}>
        <form onSubmit={submitSearch}><MagnifyingGlassIcon /><input aria-label="티커 검색" placeholder="종목명 또는 티커를 검색하세요..." value={query} onChange={(event) => setQuery(event.target.value)} /></form>
        {user && <Link className={styles.notificationButton} href="/profile#notifications" aria-label="알림 설정"><BellIcon /></Link>}
        <div className={styles.userChip}><span>{displayName.slice(0, 1).toUpperCase()}</span><strong>{displayName}</strong></div>
      </header>
      <div className={styles.content}>{children}</div>
    </div>
  </div>;
}
