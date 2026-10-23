'use client';

import { useEffect, useRef, useState } from 'react';
import { useAuth } from './auth-context';
import type { Fetcher } from './api';

/** Pass a stable loader (an API function or useCallback) to avoid repeat requests. */
export function useResource<T>(load: (fetcher: Fetcher) => Promise<T>) {
  const { authFetch, user } = useAuth();
  const owner = user?.id;
  const [attempt, setAttempt] = useState(0);
  const busy = useRef(false);
  const [result, setResult] = useState<{
    load: typeof load; owner: typeof owner; attempt: number;
    outcome: { status: 'success'; data: T } | { status: 'error'; data: null };
  } | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    const signal = AbortSignal.any([controller.signal, AbortSignal.timeout(10_000)]);
    let active = true;
    busy.current = true;
    const fetcher: Fetcher = (path, options) => authFetch(path, { ...options, signal });
    Promise.resolve().then(() => load(fetcher)).then(
      data => { if (active) setResult({ load, owner, attempt, outcome: { status: 'success', data } }); },
      () => { if (active) setResult({ load, owner, attempt, outcome: { status: 'error', data: null } }); },
    ).finally(() => { if (active) busy.current = false; });
    return () => { active = false; controller.abort(); };
  }, [load, authFetch, owner, attempt]);

  const state = result?.load === load && result.owner === owner && result.attempt === attempt
    ? result.outcome : { status: 'loading' as const, data: null };
  function retry() {
    if (busy.current) return;
    busy.current = true;
    setAttempt(value => value + 1);
  }
  return { ...state, retry };
}
