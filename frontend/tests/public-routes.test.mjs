import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { runInNewContext } from 'node:vm';
import ts from 'typescript';

const exports = {};
runInNewContext(ts.transpileModule(readFileSync(new URL('../src/lib/public-routes.ts', import.meta.url), 'utf8'), {
  compilerOptions: { module: ts.ModuleKind.CommonJS },
}).outputText, { exports });

test('guests can explore market data and analysis pages', () => {
  for (const path of ['/dashboard', '/stock-list', '/market', '/news', '/market-reports/12',
    '/symbols/AAPL', '/symbols/BRK-B', '/financials', '/financials/MSFT', '/screener', '/etf']) {
    assert.equal(exports.isPublicPage(path), true, path);
  }
});

test('private pages and nested stock reports remain protected', () => {
  for (const path of ['/watchlist', '/portfolio', '/review', '/execution', '/auto-trading',
    '/profile', '/notifications', '/admin/symbols', '/reports/1', '/symbols/AAPL/reports',
    '/financials/AAPL/private', '/etf/private', '/symbols/', '/market-reports/new']) {
    assert.equal(exports.isPublicPage(path), false, path);
  }
});
