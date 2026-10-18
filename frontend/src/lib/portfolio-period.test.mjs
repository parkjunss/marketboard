import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import ts from 'typescript';
import vm from 'node:vm';
const source = fs.readFileSync(new URL('./portfolio-period.ts', import.meta.url), 'utf8');
const compiled = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS } });
const context = { exports: {}, Intl, Date };
vm.runInNewContext(compiled.outputText, context);
const { portfolioPeriod } = context.exports;
const range = (months, now) => JSON.parse(JSON.stringify(portfolioPeriod(months, new Date(now))));

test('uses yesterday in New York, not the host calendar date', () => {
  assert.deepEqual(range(12, '2026-10-18T01:00:00Z'), {startDate:'2025-10-16',endDate:'2026-10-16'});
});
test('clamps leap day when subtracting one year', () => {
  assert.deepEqual(range(12, '2024-03-01T18:00:00Z'), {startDate:'2023-02-28',endDate:'2024-02-29'});
});
test('clamps month end rather than overflowing into March', () => {
  assert.deepEqual(range(3, '2025-06-01T16:00:00Z'), {startDate:'2025-02-28',endDate:'2025-05-31'});
});
test('supports every quick period', () => {
  for (const months of [3,6,12,36,60]) {
    const dates = range(months, '2026-10-18T16:00:00Z');
    assert.equal(dates.endDate, '2026-10-17');
    assert.ok(dates.startDate < dates.endDate);
  }
});
