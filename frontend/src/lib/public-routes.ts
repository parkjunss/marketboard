const PUBLIC_PATHS = new Set([
  '/', '/dashboard', '/stock-list', '/market', '/news', '/execution-demo',
  '/market-reports', '/financials', '/screener', '/etf',
]);

export function isPublicPage(pathname: string): boolean {
  return PUBLIC_PATHS.has(pathname)
    || /^\/market-reports\/\d+$/.test(pathname)
    || /^\/(symbols|financials)\/[^/]+$/.test(pathname);
}
