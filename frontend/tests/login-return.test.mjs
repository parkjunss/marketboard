import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { runInNewContext } from 'node:vm';
import ts from 'typescript';

function load(storage = new Map()) {
  const exports = {};
  runInNewContext(ts.transpileModule(readFileSync(new URL('../src/lib/login-return.ts', import.meta.url), 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS },
  }).outputText, { exports, sessionStorage: {
    setItem: (k, v) => storage.set(k, v), getItem: k => storage.get(k), removeItem: k => storage.delete(k),
  } });
  return exports;
}
test('only known internal paths can be login destinations', () => {
  const { safeLoginReturn } = load();
  assert.equal(safeLoginReturn('/symbols/BRK-B'), '/symbols/BRK-B');
  for (const path of ['//evil.test', 'https://evil.test', '/symbols/../login', '/login', '/oauth/callback', '/symbols/%2f%2fevil', '/symbols/NVDA?token=secret', '/symbols/\\evil']) {
    assert.equal(safeLoginReturn(path), null);
  }
});
test('destination is consumed once; ordinary login keeps the default', () => {
  const { rememberLoginReturn, takeLoginReturn } = load();
  rememberLoginReturn('/symbols/NVDA');
  assert.equal(takeLoginReturn(), '/symbols/NVDA');
  assert.equal(takeLoginReturn(), '/review');
});
test('disabled browser storage does not block login', () => {
  const { rememberLoginReturn, takeLoginReturn } = load({set() {throw Error();}, get() {throw Error();}});
  rememberLoginReturn('/symbols/NVDA');
  assert.equal(takeLoginReturn(), '/review');
});

test('cancelled login no longer returns to an abandoned destination', () => {
  const { rememberLoginReturn, clearLoginReturn, takeLoginReturn } = load();
  rememberLoginReturn('/symbols/NVDA');
  clearLoginReturn();
  assert.equal(takeLoginReturn(), '/review');
});
