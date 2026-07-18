'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { Center } from '@astryxdesign/core/Center';
import { Spinner } from '@astryxdesign/core/Spinner';
import { useAuth } from '@/lib/auth-context';

export function RequireAuth({ children }: { children: React.ReactNode }) {
  const { user, isInitializing } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (!isInitializing && !user) {
      router.replace('/login');
    }
  }, [isInitializing, user, router]);

  if (isInitializing || !user) {
    return (
      <Center height="100vh">
        <Spinner size="lg" label="불러오는 중" />
      </Center>
    );
  }

  return <>{children}</>;
}
