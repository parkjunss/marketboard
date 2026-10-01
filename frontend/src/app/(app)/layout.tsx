'use client';

import { AppNavigation } from '@/components/app-shell/AppNavigation';
import { RequireAuth } from '@/components/RequireAuth';
import { QuoteStreamProvider } from '@/lib/quote-stream-context';

export default function AppLayout({ children }: { children: React.ReactNode }) {
  return <RequireAuth><QuoteStreamProvider><AppNavigation>{children}</AppNavigation></QuoteStreamProvider></RequireAuth>;
}
