'use client';

import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import * as api from './api';
import { ApiError, request } from './api';
import { decodeJwt } from './jwt';
import type { Role, TokenResponse } from './types';

const REFRESH_TOKEN_STORAGE_KEY = 'marketboard.refreshToken';

export interface AuthUser {
  id: number;
  email: string;
  role: Role;
}

interface RequestOptions {
  headers?: Record<string, string>;
  method?: string;
  body?: unknown;
}

interface AuthContextValue {
  user: AuthUser | null;
  accessToken: string | null;
  isInitializing: boolean;
  login: (email: string, password: string) => Promise<void>;
  signup: (input: {
    email: string;
    password: string;
    passwordConfirm: string;
    username: string;
    termsAgreed: boolean;
  }) => Promise<void>;
  logout: () => Promise<void>;
  authFetch: <T>(path: string, options?: RequestOptions) => Promise<T>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

function userFromAccessToken(token: string): AuthUser | null {
  const claims = decodeJwt(token);
  if (!claims) return null;
  return { id: Number(claims.sub), email: claims.email, role: claims.role };
}

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [accessToken, setAccessToken] = useState<string | null>(null);
  // Always the same on server and client (no `typeof window` branch) so hydration doesn't see a
  // mismatch — the actual localStorage check happens in the effect below instead.
  const [isInitializing, setIsInitializing] = useState(true);
  const accessTokenRef = useRef<string | null>(null);
  const refreshTokenRef = useRef<string | null>(null);
  const sessionVersion = useRef(0);
  const refreshFlight = useRef<{ token: string; version: number; promise: Promise<TokenResponse> } | null>(null);

  const applyTokens = useCallback((accessToken: string, refreshToken: string) => {
    accessTokenRef.current = accessToken;
    refreshTokenRef.current = refreshToken;
    localStorage.setItem(REFRESH_TOKEN_STORAGE_KEY, refreshToken);
    setUser(userFromAccessToken(accessToken));
    setAccessToken(accessToken);
  }, []);

  const clearTokens = useCallback(() => {
    sessionVersion.current++;
    accessTokenRef.current = null;
    refreshTokenRef.current = null;
    localStorage.removeItem(REFRESH_TOKEN_STORAGE_KEY);
    setUser(null);
    setAccessToken(null);
  }, []);

  const refreshSession = useCallback((token: string) => {
    const version = sessionVersion.current;
    const existing = refreshFlight.current;
    if (existing?.token === token && existing.version === version) return existing.promise;
    const promise = api.refresh(token).then(tokens => {
      if (sessionVersion.current !== version) throw new Error('Session changed');
      applyTokens(tokens.accessToken, tokens.refreshToken);
      return tokens;
    }).catch(error => {
      if (sessionVersion.current === version && error instanceof ApiError && error.status === 401) clearTokens();
      throw error;
    }).finally(() => {
      if (refreshFlight.current?.promise === promise) refreshFlight.current = null;
    });
    refreshFlight.current = { token, version, promise };
    return promise;
  }, [applyTokens, clearTokens]);

  useEffect(() => {
    let cancelled = false;
    const storedRefreshToken = localStorage.getItem(REFRESH_TOKEN_STORAGE_KEY);
    const resolveSession = storedRefreshToken
      ? refreshSession(storedRefreshToken).catch(() => undefined)
      : Promise.resolve();
    resolveSession.finally(() => {
      if (!cancelled) setIsInitializing(false);
    });
    return () => {
      cancelled = true;
    };
  }, [refreshSession]);

  const login = useCallback(
    async (email: string, password: string) => {
      const version = ++sessionVersion.current;
      const tokens = await api.login({ email, password });
      if (sessionVersion.current === version) applyTokens(tokens.accessToken, tokens.refreshToken);
    },
    [applyTokens],
  );

  const signup = useCallback(
    async (input: { email: string; password: string; passwordConfirm: string; username: string; termsAgreed: boolean }) => {
      await api.signup(input);
    },
    [],
  );

  const logout = useCallback(async () => {
    const accessToken = accessTokenRef.current;
    clearTokens();
    if (accessToken) {
      await api.logout(accessToken).catch(() => undefined);
    }
  }, [clearTokens]);

  const authFetch = useCallback(
    async <T,>(path: string, options: RequestOptions = {}): Promise<T> => {
      const version = sessionVersion.current;
      const sentToken = accessTokenRef.current;
      try {
        return await request<T>(path, { ...options, accessToken: sentToken });
      } catch (err) {
        if (sessionVersion.current === version && err instanceof ApiError && err.status === 401 && refreshTokenRef.current) {
          const token = accessTokenRef.current !== sentToken
            ? accessTokenRef.current
            : (await refreshSession(refreshTokenRef.current)).accessToken;
          if (sessionVersion.current !== version) throw err;
          return request<T>(path, { ...options, accessToken: token });
        }
        throw err;
      }
    },
    [refreshSession],
  );

  const value = useMemo<AuthContextValue>(
    () => ({ user, accessToken, isInitializing, login, signup, logout, authFetch }),
    [user, accessToken, isInitializing, login, signup, logout, authFetch],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
