import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { runInNewContext } from 'node:vm';
import ts from 'typescript';
const exports = {};
runInNewContext(ts.transpileModule(readFileSync(new URL('../src/lib/sector-performance.ts', import.meta.url), 'utf8'), {
  compilerOptions: { module: ts.ModuleKind.CommonJS },
}).outputText, { exports });
const { sortSectors } = exports;
const rows = [
  { slug: 'B', changePct1d: -2, changePct1w: 5, changePct1m: null },
  { slug: 'A', changePct1d: 0, changePct1w: 5, changePct1m: -3 },
  { slug: 'C', changePct1d: 3, changePct1w: -1, changePct1m: 2 },
];
test('selected period changes order without mutating provider data', () => {
  assert.deepEqual(Array.from(sortSectors(rows, 'changePct1d'), x => x.slug), ['C', 'A', 'B']);
  assert.deepEqual(Array.from(sortSectors(rows, 'changePct1w'), x => x.slug), ['A', 'B', 'C']);
  assert.deepEqual(Array.from(sortSectors(rows, 'changePct1m'), x => x.slug), ['C', 'A', 'B']);
  assert.deepEqual(rows.map(x => x.slug), ['B', 'A', 'C']);
});
test('unknown and non-finite values rank below valid negative values', () => {
  const data = [{slug:'D',changePct1d:NaN},{slug:'B',changePct1d:Infinity},{slug:'C',changePct1d:null},{slug:'A',changePct1d:-100}];
  assert.deepEqual(Array.from(sortSectors(data, 'changePct1d'), x => x.slug), ['A','B','C','D']);
  assert.equal(sortSectors([], 'changePct1m').length, 0);
});
