// UI authentication regression with synthetic API responses; no live market-data claim.
// node scripts/verify-public-access.cjs <playwright module> [web URL]
const { chromium } = require(process.argv[2] || 'playwright');
const assert = require('node:assert/strict');
const web = process.argv[3] || 'http://localhost:13110';

(async () => {
  const browser = await chromium.launch({ headless: true, channel: process.env.PLAYWRIGHT_CHANNEL || 'msedge' });
  try {
    const context = await browser.newContext();
    const page = await context.newPage();
    const errors = [];
    const privateCalls = [];
    let calculations = 0;
    page.on('pageerror', error => errors.push(error.message));
    await page.route('**/api/**', route => {
      const path = new URL(route.request().url()).pathname;
      if (/^\/api\/(watchlist|alerts|chart-indicator-settings|portfolios|portfolio\/designs|etf\/(reviews|paper)|notifications)(\/|$)/.test(path)) {
        privateCalls.push(path);
      }
      if (path === '/api/quotes') {
        return route.fulfill({ json: [{ symbol: 'AAPL', name: 'Synthetic Apple', price: 123.45, volume: 100, ts: '2026-10-26T20:00:00Z' }] });
      }
      if (path === '/api/portfolio/design') {
        calculations++;
        assert.equal(route.request().method(), 'POST');
        return route.fulfill({ json: { status: 'INSUFFICIENT_DATA', message: 'Synthetic comparison received',
          assets: [], candidates: [], frontier: [], commonObservations: 0, excludedIntervals: 0,
          commonStart: null, commonEnd: null, correlation: null, warnings: [] } });
      }
      // Data failures should render a recoverable empty/error state, never a login redirect.
      return route.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({ message: 'Synthetic data unavailable' }) });
    });
    for (const path of ['/symbols/AAPL', '/financials', '/financials/AAPL', '/screener', '/etf']) {
      await page.goto(web + path);
      await page.getByRole('navigation', { name: '주요 메뉴' }).waitFor();
      await page.waitForTimeout(500);
      assert.equal(new URL(page.url()).pathname, path);
      if (path === '/symbols/AAPL') await page.getByRole('heading', { name: '123.45', exact: true }).waitFor();
      for (const href of ['/financials', '/screener', '/etf']) {
        assert.equal(await page.locator(`nav[aria-label="주요 메뉴"] a[href="${href}"]`).count(), 1);
      }
      console.log('PASS guest page and navigation:', path);
    }
    await page.locator('input[name="startDate"]').fill('2024-01-01');
    await page.locator('input[name="endDate"]').fill('2024-12-31');
    await page.getByRole('button', { name: '배분안 비교하기', exact: true }).click();
    await page.getByText('Synthetic comparison received', { exact: true }).waitFor();
    assert.equal(calculations, 1);
    console.log('PASS guest quote snapshot and server portfolio calculation');
    await page.getByRole('link', { name: '로그인하고 설계 저장·불러오기' }).click();
    await page.waitForURL('**/login?next=%2Fetf');
    console.log('PASS ETF save requests login with return destination');
    await page.goto(web + '/symbols/AAPL');
    await page.getByRole('button', { name: '관심종목에 추가', exact: true }).click();
    await page.waitForURL('**/login?next=%2Fsymbols%2FAAPL');
    console.log('PASS watchlist action requests login with return destination');
    for (const path of ['/watchlist', '/symbols/AAPL/reports']) {
      await page.goto(web + path);
      await page.waitForURL('**/login?next=*');
      assert.equal(new URL(page.url()).searchParams.get('next'), path);
      console.log('PASS private page:', path);
    }
    assert.deepEqual(privateCalls, [], 'guest must not fetch private records');
    assert.deepEqual(errors, [], 'no unhandled browser errors');
    console.log('PASS no private API calls or unhandled browser errors');
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
