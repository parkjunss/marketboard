import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import ts from 'typescript';

const compiled = ts.transpileModule(readFileSync(new URL('../src/lib/idempotent-post.ts', import.meta.url), 'utf8'), {
  compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 },
}).outputText;
const { idempotentPost } = await import(`data:text/javascript;base64,${Buffer.from(compiled).toString('base64')}`);
const storage = new Map();
Object.defineProperty(globalThis, 'sessionStorage', { value: {
  getItem: key => storage.get(key) ?? null, setItem: (key, value) => storage.set(key, value), removeItem: key => storage.delete(key),
}, configurable: true });

test('uncertain response reuses key, acknowledged completion permits a new operation', async () => {
  const keys = [];
  const post = async (_, options) => {
    keys.push(options.headers['Idempotency-Key']);
    if (keys.length === 1) throw new Error('response lost after server commit');
    return { id: 1 };
  };
  await assert.rejects(idempotentPost(post, '/portfolio', { name: 'test' }, '1'));
  assert.deepEqual(await idempotentPost(post, '/portfolio', { name: 'test' }, '1'), { id: 1 });
  await idempotentPost(post, '/portfolio', { name: 'test' }, '1');
  assert.equal(keys[0], keys[1]);
  assert.notEqual(keys[1], keys[2]);
});

test('concurrent identical submissions share the in-flight request', async () => {
  let calls = 0;
  const post = async () => { calls++; await new Promise(resolve => setTimeout(resolve, 30)); return 42; };
  assert.deepEqual(await Promise.all(Array.from({ length: 8 }, () => idempotentPost(post, '/backtest', { period: 21 }, '2'))), Array(8).fill(42));
  assert.equal(calls, 1);
});

test('different inputs and users have independent pending keys', async () => {
  const keys = [];
  const post = async (_, options) => { keys.push(options.headers['Idempotency-Key']); throw new Error('offline'); };
  for (const [user, period] of [['1', 5], ['1', 21], ['2', 5]]) {
    await assert.rejects(idempotentPost(post, '/review', { period }, user));
  }
  assert.equal(new Set(keys).size, 3);
});
