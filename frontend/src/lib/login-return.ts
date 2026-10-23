const KEY = 'marketboard.loginReturn';
const ROUTES = new Set(['review', 'watchlist', 'portfolio', 'backtest', 'auto-trading', 'execution', 'screener', 'etf', 'financials', 'notifications', 'profile', 'admin', 'reports', 'symbols']);

export function safeLoginReturn(value: string | null): string | null {
  if (!value || !/^\/[A-Za-z0-9_/-]+$/.test(value) || value.includes('//')) return null;
  return ROUTES.has(value.split('/')[1]) ? value : null;
}

export function rememberLoginReturn(path: string) {
  const safe = safeLoginReturn(path);
  try { if (safe) sessionStorage.setItem(KEY, safe); } catch { /* Storage may be disabled. */ }
}

export function takeLoginReturn(): string {
  try {
    const path = safeLoginReturn(sessionStorage.getItem(KEY));
    sessionStorage.removeItem(KEY);
    return path ?? '/review';
  } catch { return '/review'; }
}

export function clearLoginReturn() {
  try { sessionStorage.removeItem(KEY); } catch { /* Storage may be disabled. */ }
}
