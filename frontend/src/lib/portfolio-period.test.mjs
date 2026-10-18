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
const { portfolioValidationPeriods } = context.exports;
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

test('validation preset keeps one year training strictly before three month holdout', () => {
  const p = JSON.parse(JSON.stringify(portfolioValidationPeriods(new Date('2026-10-18T16:00:00Z'))));
  assert.deepEqual(p, {training:{startDate:'2025-07-16',endDate:'2026-07-16'}, validation:{startDate:'2026-07-17',endDate:'2026-10-17'}});
});
test('validation preset handles leap days and New York date boundaries', () => {
  for (const now of ['2024-06-01T16:00:00Z','2025-06-01T16:00:00Z','2026-10-18T01:00:00Z']) {
    const p = portfolioValidationPeriods(new Date(now));
    assert.equal(Date.parse(p.validation.startDate)-Date.parse(p.training.endDate), 86400000);
    assert.equal(p.validation.endDate, portfolioPeriod(3,new Date(now)).endDate);
    assert.ok(p.training.startDate < p.training.endDate);
  }
});
