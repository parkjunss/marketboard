import { createRequire } from 'node:module';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { mkdir, writeFile } from 'node:fs/promises';
import { resolve, join } from 'node:path';
import { createServer } from 'node:net';
import assert from 'node:assert/strict';

const packageRoot = process.argv[2];
assert(packageRoot, 'Pass the installed rea-agents package directory.');
const require = createRequire(join(resolve(packageRoot), 'package.json'));
const { chromium } = require('playwright-core');
const run = promisify(execFile);
const output = resolve('docs/refactor/tradingview-evidence');
await mkdir(output, { recursive: true });
const server = createServer();
await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
const port = server.address().port;
await new Promise(resolve => server.close(resolve));
const endpoint = `http://127.0.0.1:${port}`;
const browser = await chromium.launch({
  executablePath: 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
  headless: true,
  args: [`--remote-debugging-port=${port}`, '--remote-debugging-address=127.0.0.1'],
});
const captures = [];
const cli = async (...args) => {
  const { stdout } = await run(process.execPath, [join(packageRoot, 'scripts/rea.mjs'), ...args, '--format', 'json'], {
    maxBuffer: 16 * 1024 * 1024, timeout: 45000, windowsHide: true,
  });
  const result = JSON.parse(stdout);
  assert(!result.error, JSON.stringify(result));
  return result;
};
try {
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 }, locale: 'en-US', timezoneId: 'America/New_York' });
  const page = await context.newPage();
  await page.goto('https://www.tradingview.com/chart/?symbol=NASDAQ%3AAAPL', { waitUntil: 'domcontentloaded', timeout: 45000 });
  await page.getByRole('region', { name: 'Chart #1', exact: true }).waitFor({ timeout: 30000 });
  await page.waitForTimeout(5000);
  const targets = await cli('list-browser-targets', endpoint);
  await writeFile(join(output, 'targets.json'), JSON.stringify(targets, null, 2));
  const targetResponse = await fetch(`${endpoint}/json/list`);
  const target = (await targetResponse.json()).find(item => item.type === 'page' && item.url === page.url());
  assert(target, 'Owned TradingView target not found.');
  const capture = async label => {
    const evidence = await cli('inspect-web-page', endpoint, target.id, '--include-accessibility-text', '--observation-ms', '100');
    assert(evidence.evidence_id, 'REA returned no Evidence ID.');
    await writeFile(join(output, `${label}.json`), JSON.stringify(evidence, null, 2));
    await page.screenshot({ path: join(output, `${label}.png`) });
    const snapshot = await page.locator('body').ariaSnapshot();
    await writeFile(join(output, `${label}.txt`), snapshot);
    captures.push({ label, evidence_id: evidence.evidence_id });
    console.log(label, evidence.evidence_id);
    console.log(snapshot.split('\n').filter(line => /text: O |Horizontal Line|Horizontal line|Trend Line|Trend line|Coordinates|Visibility|Style|price|dialog|Remove|Lock/.test(line)).join('\n'));
  };
  await capture('initial');
  const box = await page.getByRole('region', { name: 'Chart #1', exact: true }).boundingBox();
  assert(box && box.width > 200 && box.height > 200, 'Chart geometry is unavailable.');
  const point = (x, y) => ({ x: box.x + box.width * x, y: box.y + box.height * y });
  const move = async (x, y) => { const p = point(x, y); await page.mouse.move(p.x, p.y); await page.waitForTimeout(300); };
  const click = async (x, y) => { const p = point(x, y); await page.mouse.click(p.x, p.y); await page.waitForTimeout(300); };
  await move(0.35, 0.45);
  await capture('crosshair-left');
  await move(0.65, 0.45);
  await capture('crosshair-right');
  await page.getByRole('button', { name: 'Trend tools', exact: true }).click();
  await page.getByText('Horizontal line', { exact: true }).click();
  await click(0.5, 0.5);
  await capture('horizontal-attempt');
  const horizontalBlocked = await page.getByText('Join for free to access horizontal line and every other drawing tool.', { exact: false }).isVisible();
  if (horizontalBlocked) {
    console.log('Horizontal drawing creation blocked by the sign-up dialog.');
    await page.getByRole('button', { name: 'Close', exact: true }).click();
    await page.getByRole('button', { name: 'Trendline', exact: true }).click();
    await click(0.35, 0.45);
    await capture('trend-attempt');
    console.log('Drawing editing remains unverified; inspect the attempt screenshot.');
  } else {
  const middle = point(0.5, 0.5);
  const shifted = point(0.5, 0.6);
  await page.mouse.move(middle.x, middle.y);
  await page.mouse.down();
  await page.mouse.move(shifted.x, shifted.y, { steps: 10 });
  await page.mouse.up();
  await capture('horizontal-moved');
  await page.keyboard.press('Delete');
  await capture('horizontal-delete');
  await page.getByRole('button', { name: 'Trendline', exact: true }).click();
  await click(0.35, 0.45);
  await click(0.65, 0.65);
  await capture('trend-created');
  await page.keyboard.press('Escape');
  await click(0.5, 0.55);
  await page.keyboard.press('Delete');
  await capture('trend-delete');
  }
} finally {
  await writeFile(join(output, 'index.json'), JSON.stringify({ captures, interaction: 'Playwright mouse and keyboard; REA passive snapshots' }, null, 2));
  await browser.close();
}
