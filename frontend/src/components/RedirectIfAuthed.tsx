'use client';

import { useEffect, useRef } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/lib/auth-context';
import { takeLoginReturn } from '@/lib/login-return';

export function RedirectIfAuthed({ children }: { children: React.ReactNode }) {
  const { user, isInitializing } = useAuth();
  const router = useRouter();
  const redirected = useRef(false);

  useEffect(() => {
    if (!isInitializing && user && !redirected.current) {
      redirected.current = true;
      router.replace(takeLoginReturn());
    }
  }, [isInitializing, user, router]);

  if (isInitializing || user) {
    return null;
  }

  return <>{children}</>;
}
