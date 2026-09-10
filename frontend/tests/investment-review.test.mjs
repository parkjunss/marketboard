import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import ts from 'typescript';

const source = readFileSync(new URL('../src/lib/investment-review.ts', import.meta.url), 'utf8');
const compiled = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext } }).outputText;
const { reviewChange } = await import(`data:text/javascript;base64,${Buffer.from(compiled).toString('base64')}`);
const bars = Array.from({ length: 22 }, (_, i) => ({ ts: `2026-08-${String(i + 1).padStart(2, '0')}T04:00:00Z`, close: 100 + i }));

test('weekly and monthly windows retain exact evidence dates and sort input', () => {
  const weekly = reviewChange([...bars].reverse(), 5);
  assert.equal(weekly.from, '2026-08-17');
  assert.equal(weekly.to, '2026-08-22');
  assert.ok(Math.abs(weekly.change - (121 / 116 - 1) * 100) < 1e-10);
  assert.ok(Math.abs(reviewChange(bars, 21).change - 21) < 1e-10);
});
test('yields report basis points rather than relative percentage', () => {
  assert.ok(Math.abs(reviewChange([{ ...bars[0], close: 4 }, { ...bars[1], close: 4.25 }], 1, true).change - 25) < 1e-10);
});
test('insufficient, duplicate, invalid and zero-priced evidence cannot produce a change', () => {
  assert.equal(reviewChange(bars.slice(0, 5), 5), null);
  assert.equal(reviewChange([], 5), null);
  for (const bad of [{ ...bars[1], close: 0 }, { ...bars[1], close: NaN }, { ...bars[1], ts: 'bad' }, bars[0]]) {
    assert.equal(reviewChange([bars[0], bad], 1), null);
  }
});
