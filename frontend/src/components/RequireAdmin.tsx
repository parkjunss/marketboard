'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { Center } from '@astryxdesign/core/Center';
import { Spinner } from '@astryxdesign/core/Spinner';
import { useAuth } from '@/lib/auth-context';

/** Assumes it renders inside RequireAuth, so `user` is already guaranteed non-null once mounted. */
export function RequireAdmin({ children }: { children: React.ReactNode }) {
  const { user } = useAuth();
  const router = useRouter();
  const isAdmin = user?.role === 'ADMIN';

  useEffect(() => {
    if (user && !isAdmin) {
      router.replace('/stock-list');
    }
  }, [user, isAdmin, router]);

  if (!isAdmin) {
    return (
      <Center height="100vh">
        <Spinner size="lg" label="불러오는 중" />
      </Center>
    );
  }

  return <>{children}</>;
}
