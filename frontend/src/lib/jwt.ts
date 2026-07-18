import type { Role } from './types';

export interface JwtClaims {
  sub: string;
  email: string;
  role: Role;
  type: 'ACCESS' | 'REFRESH';
  iat: number;
  exp: number;
}

export function decodeJwt(token: string): JwtClaims | null {
  try {
    const payload = token.split('.')[1];
    const json = atob(payload.replace(/-/g, '+').replace(/_/g, '/'));
    return JSON.parse(json) as JwtClaims;
  } catch {
    return null;
  }
}
