import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { runInNewContext } from 'node:vm';
import ts from 'typescript';

const exports = {};
runInNewContext(ts.transpileModule(readFileSync(new URL('../src/lib/data-time.ts', import.meta.url), 'utf8'), {
  compilerOptions: { module: ts.ModuleKind.CommonJS },
}).outputText, { exports });
const { formatDataTime } = exports;

test('explicit offsets normalize to UTC and include the calendar date', () => {
  assert.equal(formatDataTime('2026-10-23T01:30:00+09:00'), '2026-10-22 16:30:00 UTC');
  assert.equal(formatDataTime('2026-10-22T16:30:00Z'), '2026-10-22 16:30:00 UTC');
});
test('date-only snapshots remain dates, not a fabricated timestamp', () => {
  assert.equal(formatDataTime('2026-10-22'), '2026-10-22');
  assert.equal(formatDataTime('2026-02-30'), null);
});
test('missing, malformed, or timezone-less times stay unknown', () => {
  for (const value of [null, undefined, '', 'invalid', '2026-10-22T16:30:00']) {
    assert.equal(formatDataTime(value), null);
  }
});
