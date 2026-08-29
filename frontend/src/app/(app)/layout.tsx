'use client';

import { usePathname, useRouter } from 'next/navigation';
import { AppShell } from '@astryxdesign/core/AppShell';
import { SideNav, SideNavHeading, SideNavItem, SideNavSection } from '@astryxdesign/core/SideNav';
import { NavIcon } from '@astryxdesign/core/NavIcon';
import { VStack } from '@astryxdesign/core/Stack';
import { Text } from '@astryxdesign/core/Text';
import { Button } from '@astryxdesign/core/Button';
import { Divider } from '@astryxdesign/core/Divider';
import {
  BanknotesIcon,
  BriefcaseIcon,
  ChartBarIcon,
  GlobeAltIcon,
  ListBulletIcon,
  MagnifyingGlassIcon,
  PresentationChartLineIcon,
  ShieldCheckIcon,
  Squares2X2Icon,
} from '@heroicons/react/24/outline';
import { RequireAuth } from '@/components/RequireAuth';
import { useAuth } from '@/lib/auth-context';
import { QuoteStreamProvider } from '@/lib/quote-stream-context';

function AppNav({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const { user, logout } = useAuth();

  return (
    <AppShell
      contentPadding={0}
      sideNav={
        <SideNav
          header={
            <SideNavHeading
              icon={<NavIcon icon={<ChartBarIcon style={{ width: 16, height: 16 }} />} />}
              heading="MarketBoard"
              headingHref="/"
            />
          }
          footer={
            <VStack gap={2}>
              <Divider />
              <Text type="supporting" size="sm">
                {user?.email}
              </Text>
              <Button
                variant="secondary"
                label="로그아웃"
                onClick={() => {
                  void logout();
                  router.replace('/login');
                }}
              />
            </VStack>
          }
        >
          <SideNavSection title="메인" isHeaderHidden>
            <SideNavItem
              label="종목 리스트"
              icon={ListBulletIcon}
              isSelected={pathname === '/stock-list'}
              href="/stock-list"
            />
            <SideNavItem
              label="대시보드"
              icon={Squares2X2Icon}
              isSelected={pathname === '/dashboard'}
              href="/dashboard"
            />
            <SideNavItem
              label="시장 지표"
              icon={GlobeAltIcon}
              isSelected={pathname === '/market'}
              href="/market"
            />
            <SideNavItem
              label="재무 대시보드"
              icon={BanknotesIcon}
              isSelected={pathname.startsWith('/financials')}
              href="/financials"
            />
            <SideNavItem
              label="포트폴리오"
              icon={BriefcaseIcon}
              isSelected={pathname === '/portfolio'}
              href="/portfolio"
            />
            <SideNavItem
              label="백테스팅"
              icon={PresentationChartLineIcon}
              isSelected={pathname.startsWith('/backtest')}
              href="/backtest"
            />
            <SideNavItem
              label="종목 스크리너"
              icon={MagnifyingGlassIcon}
              isSelected={pathname.startsWith('/screener')}
              href="/screener"
            />
          </SideNavSection>
          {user?.role === 'ADMIN' && (
            <SideNavSection title="관리자">
              <SideNavItem
                label="관리자"
                icon={ShieldCheckIcon}
                isSelected={pathname.startsWith('/admin')}
                href="/admin/symbols"
              />
            </SideNavSection>
          )}
        </SideNav>
      }
    >
      {children}
    </AppShell>
  );
}

export default function AppLayout({ children }: { children: React.ReactNode }) {
  return (
    <RequireAuth>
      <QuoteStreamProvider>
        <AppNav>{children}</AppNav>
      </QuoteStreamProvider>
    </RequireAuth>
  );
}
