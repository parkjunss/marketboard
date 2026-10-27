'use client';

import { useEffect } from 'react';
import { usePathname, useRouter } from 'next/navigation';
import { Center } from '@astryxdesign/core/Center';
import { Spinner } from '@astryxdesign/core/Spinner';
import { useAuth } from '@/lib/auth-context';
import { rememberLoginReturn } from '@/lib/login-return';
import { isPublicPage } from '@/lib/public-routes';


export function RequireAuth({ children }: { children: React.ReactNode }) {
  const { user, isInitializing } = useAuth();
  const router = useRouter();
  const pathname = usePathname();
  const isPublic = isPublicPage(pathname);

  useEffect(() => {
    if (!isInitializing && !user && !isPublic) {
      rememberLoginReturn(pathname);
      router.replace(`/login?next=${encodeURIComponent(pathname)}`);
    }
  }, [isInitializing, isPublic, user, router, pathname]);

  if (isInitializing || (!user && !isPublic)) {
    return (
      <Center height="100vh">
        <Spinner size="lg" label="불러오는 중" />
      </Center>
    );
  }

  return <>{children}</>;
}
