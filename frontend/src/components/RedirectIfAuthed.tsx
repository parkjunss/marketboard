'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/lib/auth-context';

export function RedirectIfAuthed({ children }: { children: React.ReactNode }) {
  const { user, isInitializing } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (!isInitializing && user) {
      router.replace('/stock-list');
    }
  }, [isInitializing, user, router]);

  if (isInitializing || user) {
    return null;
  }

  return <>{children}</>;
}
