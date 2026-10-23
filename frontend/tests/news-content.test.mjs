import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { runInNewContext } from 'node:vm';
import ts from 'typescript';

const exports = {};
runInNewContext(ts.transpileModule(readFileSync(new URL('../src/lib/news-content.ts', import.meta.url), 'utf8'), {
  compilerOptions: { module: ts.ModuleKind.CommonJS },
}).outputText, { exports });
const { newsSummary, uniqueNews } = exports;

test('repeated headline is hidden despite case, spacing and punctuation', () => {
  assert.equal(newsSummary('Markets rise: Tech leads', ' MARKETS RISE — Tech leads. '), null);
  assert.equal(newsSummary('시장 상승', '시장 상승!'), null);
  assert.equal(newsSummary('Markets rise', ''), null);
});
test('additional facts and opposing conclusions remain visible', () => {
  assert.equal(newsSummary('Markets rise', 'Markets rise as earnings improve.'), 'Markets rise as earnings improve.');
  assert.equal(newsSummary('Markets rise', 'Markets fall'), 'Markets fall');
});
test('dedup preserves order and independent coverage from other publishers', () => {
  const items = [
    { id: 1, url: 'https://example.com/a', source: 'One', headline: 'Market update' },
    { id: 2, url: 'https://example.com/a', source: 'Two', headline: 'Other headline' },
    { id: 3, url: 'https://example.com/b', source: 'One', headline: 'Market update!' },
    { id: 4, url: 'https://example.com/c', source: 'Two', headline: 'Market update' },
    { id: 5, url: 'https://example.com/d', source: 'One', headline: 'New facts' },
  ];
  assert.deepEqual(Array.from(uniqueNews(items), item => item.id), [1, 4, 5]);
  assert.equal(items.length, 5);
});
