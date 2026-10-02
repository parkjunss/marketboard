'use client';

import { useEffect } from 'react';
import { usePathname, useRouter } from 'next/navigation';
import { Center } from '@astryxdesign/core/Center';
import { Spinner } from '@astryxdesign/core/Spinner';
import { useAuth } from '@/lib/auth-context';

const PUBLIC_PATHS = new Set(['/', '/dashboard', '/stock-list', '/market', '/news']);

export function RequireAuth({ children }: { children: React.ReactNode }) {
  const { user, isInitializing } = useAuth();
  const router = useRouter();
  const pathname = usePathname();
  const isPublic = PUBLIC_PATHS.has(pathname);

  useEffect(() => {
    if (!isInitializing && !user && !isPublic) router.replace('/login');
  }, [isInitializing, isPublic, user, router]);

  if (isInitializing || (!user && !isPublic)) {
    return (
      <Center height="100vh">
        <Spinner size="lg" label="불러오는 중" />
      </Center>
    );
  }

  return <>{children}</>;
}
