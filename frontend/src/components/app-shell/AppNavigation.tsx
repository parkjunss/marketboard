'use client';

import { useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import {
  ArrowLeftStartOnRectangleIcon, BanknotesIcon, Bars3Icon, BeakerIcon, BellIcon, BriefcaseIcon,
  ChartBarIcon, ChartBarSquareIcon, Cog6ToothIcon, GlobeAltIcon, HeartIcon, HomeIcon,
  MagnifyingGlassIcon, NewspaperIcon, ShieldCheckIcon, XMarkIcon,
} from '@heroicons/react/24/outline';
import { useAuth } from '@/lib/auth-context';
import { getNotifications } from '@/lib/api';
import styles from './app-shell.module.css';

const marketNavigation = [
  { label: '대시보드', href: '/dashboard', icon: HomeIcon },
  { label: '투자 점검', href: '/review', icon: ShieldCheckIcon },
  { label: '종목 검색', href: '/stock-list', icon: MagnifyingGlassIcon },
  { label: '스크리너', href: '/screener', icon: BeakerIcon },
  { label: '포트폴리오 설계', href: '/etf', icon: ChartBarIcon },
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
  { label: '자동 모의 매매', href: '/auto-trading', icon: ChartBarIcon },
  { label: '프로필', href: '/profile', icon: Cog6ToothIcon },
];

function isActive(pathname: string, href: string) {
  return pathname === href || (href !== '/dashboard' && pathname.startsWith(`${href}/`));
}

export function AppNavigation({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const { user, logout, authFetch } = useAuth();
  const [open, setOpen] = useState(false);
  const menuButton = useRef<HTMLButtonElement>(null);
  const sidebar = useRef<HTMLElement>(null);
  const [query, setQuery] = useState('');
  const [unreadCount, setUnreadCount] = useState(0);
  const displayName = user?.username || user?.email?.split('@')[0] || '게스트';
  const visibleMarketNavigation = user ? marketNavigation : publicNavigation;

  useEffect(() => {
    if (!open) return;
    const opener = menuButton.current;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    const focusable = () => Array.from(sidebar.current?.querySelectorAll<HTMLElement>('a[href], button') ?? []).filter(element => element.getClientRects().length > 0);
    focusable()[0]?.focus();
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') { event.preventDefault(); setOpen(false); }
      if (event.key !== 'Tab') return;
      const items = focusable();
      const first = items[0];
      const last = items.at(-1);
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus(); }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus(); }
    };
    const onResize = () => { if (window.innerWidth > 820) setOpen(false); };
    document.addEventListener('keydown', onKeyDown);
    window.addEventListener('resize', onResize);
    return () => {
      document.body.style.overflow = previousOverflow;
      document.removeEventListener('keydown', onKeyDown);
      window.removeEventListener('resize', onResize);
      opener?.focus();
    };
  }, [open]);

  useEffect(() => {
    if (!user) return;
    const refreshUnreadCount = () => { void getNotifications(authFetch).then((result) => setUnreadCount(result.unreadCount)).catch(() => undefined); };
    void getNotifications(authFetch).then((result) => setUnreadCount(result.unreadCount)).catch(() => undefined);
    window.addEventListener('marketboard:notifications-changed', refreshUnreadCount);
    return () => window.removeEventListener('marketboard:notifications-changed', refreshUnreadCount);
  }, [authFetch, user]);

  function submitSearch(event: React.FormEvent) {
    event.preventDefault();
    const ticker = query.trim().toUpperCase();
    if (!ticker) return;
    router.push(`/symbols/${encodeURIComponent(ticker)}`);
    setQuery('');
  }

  return <div className={styles.shell}>
    <a className={styles.skipLink} href="#page-content">본문으로 건너뛰기</a>
    <button ref={menuButton} className={styles.mobileToggle} onClick={() => setOpen(true)} aria-label="메뉴 열기" aria-expanded={open} aria-controls="app-navigation"><Bars3Icon /></button>
    {open && <button className={styles.backdrop} onClick={() => setOpen(false)} aria-label="메뉴 닫기" tabIndex={-1} />}
    <aside ref={sidebar} id="app-navigation" role={open ? 'dialog' : undefined} aria-modal={open ? true : undefined} aria-label="사이트 메뉴" className={`${styles.sidebar} ${open ? styles.sidebarOpen : ''}`}>
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
    <div className={styles.workspace} inert={open}>
      <header className={styles.topbar}>
        <form onSubmit={submitSearch}><MagnifyingGlassIcon /><input aria-label="티커 검색" placeholder="종목명 또는 티커를 검색하세요..." value={query} onChange={(event) => setQuery(event.target.value)} /></form>
        {user && <Link className={styles.notificationButton} href="/notifications" aria-label={`알림${unreadCount ? ` ${unreadCount}개 미확인` : ''}`}><BellIcon />{unreadCount > 0 && <span className={styles.notificationBadge}>{unreadCount > 99 ? '99+' : unreadCount}</span>}</Link>}
        <div className={styles.userChip}><span>{displayName.slice(0, 1).toUpperCase()}</span><strong>{displayName}</strong></div>
      </header>
      <div id="page-content" tabIndex={-1} className={styles.content}>{children}</div>
      <footer className={styles.disclosure} aria-label="정보 이용 안내">
        <p>MarketBoard의 정보는 참고용이며 투자 권유를 목적으로 하지 않습니다. 데이터는 출처와 제공 조건에 따라 지연되거나 오류가 있을 수 있습니다. 투자 판단과 그에 따른 책임은 이용자 본인에게 있습니다.</p>
        <p>자료별 기준 시각과 출처를 확인하세요. 시세 연결 상태는 데이터의 최신성을 보장하지 않습니다.</p>
        <nav aria-label="이용 정책"><Link href="/terms">이용약관</Link><Link href="/privacy">개인정보 처리방침</Link></nav>
      </footer>
    </div>
  </div>;
}
