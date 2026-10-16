// Run against compose.local.yml only; creates isolated QA users and stops its paper account.
const { chromium } = require(process.argv[2] || 'playwright');
const { randomUUID } = require('node:crypto');
const { mkdirSync, writeFileSync } = require('node:fs');
const { join } = require('node:path');
const assert = require('node:assert/strict');
const API = 'http://localhost:18081';
const WEB = 'http://localhost:13100';
const output = process.argv[3];
if (!output) throw new Error('Pass an output directory as argument 3.');
mkdirSync(output, { recursive: true });
const report = { checks: [], errors: [], startedAt: new Date().toISOString() };
function check(name) { report.checks.push(name); console.log('PASS', name); }
async function request(path, body, token, key, expected = 200, method, attempt = 0) {
  const response = await fetch(API + path, {
    method: method || (body === undefined ? 'GET' : 'POST'),
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(key ? { 'Idempotency-Key': key } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body), signal: AbortSignal.timeout(120000),
  });
  const text = await response.text();
  if (response.status === 429 && attempt < 5) {
    await new Promise(resolve => setTimeout(resolve, 13000));
    return request(path, body, token, key, expected, method, attempt + 1);
  }
  assert.equal(response.status, expected, `${path}: ${response.status} ${text.slice(0, 500)}`);
  return text ? JSON.parse(text) : null;
}
async function user(suffix) {
  const email = `etf-qa-${Date.now()}-${suffix}@example.test`, password = `Qa!${randomUUID()}a1`;
  await request('/api/auth/signup', { email, password, passwordConfirm: password, username: 'ETF local QA', termsAgreed: true }, null, null, 201);
  const tokens = await request('/api/auth/login', { email, password });
  return { email, password, token: tokens.accessToken };
}
(async () => {
  let browser, page, owner;
  try {
    await request('/api/etf/paper', undefined, null, null, 401); check('anonymous ETF access returns 401');
    owner = await user('owner');
    const stranger = await user('other');
    const signal = await request('/api/screener/etf-momentum', undefined, owner.token);
    report.signal = { ready: signal.ready, asOf: signal.asOf, reasons: signal.rows.map(r => ({ ticker: r.ticker, reason: r.reason })) };
    const configuration = { startDate: '2024-10-01', endDate: '2025-10-01', initialCapital: 10000, feeBps: 5, slippageBps: 5 };
    const comparison = await request('/api/backtest/etf-comparison', configuration, owner.token);
    assert.equal(comparison.ready, true, comparison.message); assert.equal(comparison.strategies.length, 3); check('three-strategy comparison with stored real prices');
    const body = { configuration, strategyId: 'MOMENTUM_TREND' }, key = randomUUID();
    const saved = await request('/api/etf/reviews', body, owner.token, key);
    const replay = await request('/api/etf/reviews', body, owner.token, key);
    assert.equal(saved.id, replay.id); assert.deepEqual(saved.payload, replay.payload); check('review save and idempotent replay retain exact evidence');
    report.reviewId = saved.id;
    await request(`/api/reviews/${saved.id}`, undefined, stranger.token, null, 404);
    check('another user cannot read review');
    // Signup/login/refresh share the production brute-force limiter; leave room for browser auth.
    await new Promise(resolve => setTimeout(resolve, 25000));
    browser = await chromium.launch({ headless: true });
    page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
    page.setDefaultTimeout(30000);
    page.on('pageerror', e => report.errors.push(e.message));
    page.on('response', r => {
      if (r.url().includes('/api/auth/')) console.log('AUTH', new URL(r.url()).pathname, r.status());
    });
    await page.goto(WEB + '/etf');
    await page.getByLabel('이메일', { exact: false }).fill(owner.email);
    await page.getByLabel('비밀번호', { exact: false }).fill(owner.password);
    await page.getByRole('button', { name: '로그인', exact: true }).click();
    await page.waitForURL(url => !url.pathname.includes('login'), { timeout: 30000 });
    console.log('SIGNED IN', page.url(), await page.evaluate(() => Boolean(localStorage.getItem('marketboard.refreshToken'))));
    await page.waitForLoadState('networkidle');
    await page.goto(WEB + '/etf');
    await page.getByRole('button', { name: new RegExp(`#${saved.id} ·`) }).click();
    await page.getByRole('heading', { name: `검토 #${saved.id} · MOMENTUM_TREND`, exact: true }).waitFor();
    assert.equal(await page.locator('h1').evaluate(e => getComputedStyle(e).color), 'rgb(240, 237, 230)');
    check('ETF heading inherits readable dark-theme text color');
    check('browser login and saved ETF review rendering');
    await page.getByLabel('근거와 위험 신호').fill('로컬 기능 검증용. 수정주가 기준 미확정과 월말 추세 위험을 확인. 실제 주문 없음.');
    await page.getByRole('button', { name: '판단 저장', exact: true }).click();
    const start = page.getByRole('button', { name: '저장된 조건으로 모의 운용 시작' });
    await start.waitFor();
    await page.waitForFunction(() => [...document.querySelectorAll('button')].some(b => b.textContent.includes('저장된 조건으로 모의 운용 시작') && !b.disabled));
    await start.click();
    await page.getByRole('heading', { name: /계좌 #\d+ · 검토/ }).waitFor({ timeout: 90000 });
    let accounts = await request('/api/etf/paper', undefined, owner.token);
    assert.equal(accounts.length, 1); const account = accounts[0]; report.accountId = account.id;
    check('browser adoption and paper account creation');
    const before = await request(`/api/etf/paper/${account.id}/snapshots`, undefined, owner.token);
    await request(`/api/etf/paper/${account.id}/refresh`, {}, owner.token);
    const after = await request(`/api/etf/paper/${account.id}/snapshots`, undefined, owner.token);
    assert.deepEqual(after, before); assert.equal(account.state.value, 10000);
    check('same-day refresh leaves journal unchanged and never fabricates historical fills');
    await request(`/api/etf/paper/${account.id}/snapshots`, undefined, stranger.token, null, 404);
    await request(`/api/etf/paper/${account.id}/stop`, {}, stranger.token, null, 404);
    check('another user cannot read or stop paper account');
    const portfolio = await request('/api/portfolios', { name: 'ETF local QA cash-only' }, owner.token, randomUUID(), 201);
    await page.reload();
    await page.getByRole('button', { name: new RegExp(`#${saved.id} ·`) }).click();
    await page.locator('select[name="portfolioId"]').selectOption(String(portfolio.id));
    await page.getByLabel('확인한 가용 현금 (USD)').fill('10000');
    const proposalResponse = page.waitForResponse(r => r.url().endsWith('/api/etf/rebalance') && r.request().method() === 'POST', { timeout: 90000 });
    await page.getByRole('button', { name: '제안 계산', exact: true }).click();
    const proposal = await (await proposalResponse).json();
    report.proposal = { ready: proposal.ready, message: proposal.message, priceDate: proposal.priceDate };
    if (signal.ready) { assert.equal(proposal.ready, true, proposal.message); assert.ok(proposal.rows.length === 6); }
    else assert.equal(proposal.ready, false);
    check(signal.ready ? 'browser displays server-computed cash-only rebalance proposal' : 'browser withholds proposal when current signal unavailable');
    await page.getByRole('button', { name: '신호·체결·평가 기록', exact: true }).click();
    await page.getByRole('heading', { name: `계좌 #${account.id}의 변경 불가 기록` }).waitFor();
    await page.evaluate(() => scrollTo(0, 0));
    await page.screenshot({ path: join(output, 'etf-desktop.png'), fullPage: true });
    await page.setViewportSize({ width: 390, height: 844 });
    // Let the responsive navigation's CSS transition finish before capturing the viewport.
    await page.waitForTimeout(350);
    await page.evaluate(() => scrollTo(0, 0));
    await page.screenshot({ path: join(output, 'etf-mobile.png'), fullPage: true });
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth > innerWidth);
    report.mobileHorizontalOverflow = overflow;
    assert.equal(overflow, false); check('mobile viewport has no document horizontal overflow');
    await page.getByRole('button', { name: '운용 중지', exact: true }).click();
    await page.getByRole('heading', { name: new RegExp(`계좌 #${account.id}.*중지`) }).waitFor();
    accounts = await request('/api/etf/paper', undefined, owner.token); assert.equal(accounts[0].active, false);
    check('browser stops paper account and keeps records');
    assert.deepEqual(report.errors, []); check('no browser page errors');
  } catch (e) {
    report.failure = e.message; console.error(e.message); process.exitCode = 1;
    if (page) {
      report.style = await page.evaluate(() => {
        const e = document.querySelector('h1');
        const parents = []; for (let p = e; p; p = p.parentElement) parents.push({ tag: p.tagName, cls: p.className, color: getComputedStyle(p).color });
        const matches = [];
        function visit(rules) { for (const rule of rules) { if (rule.selectorText && rule.style?.color && e?.matches(rule.selectorText)) matches.push(rule.cssText); if (rule.cssRules) visit(rule.cssRules); } }
        for (const sheet of document.styleSheets) { try { visit(sheet.cssRules); } catch {} }
        return { parents, matches };
      });
      console.log('PAGE', page.url(), await page.locator('body').innerText());
      await page.screenshot({ path: join(output, 'etf-failure.png'), fullPage: true }).catch(() => {});
    }
  }
  finally {
    if (owner && report.accountId) await request(`/api/etf/paper/${report.accountId}/stop`, {}, owner.token).catch(e => { report.cleanupError = e.message; });
    if (browser) await browser.close();
    report.finishedAt = new Date().toISOString(); writeFileSync(join(output, 'report.json'), JSON.stringify(report, null, 2));
  }
})();
