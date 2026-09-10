import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { runInNewContext } from 'node:vm';
import ts from 'typescript';

const compiled = ts.transpileModule(readFileSync(new URL('../src/lib/auth-context.tsx', import.meta.url), 'utf8'), {
  compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX },
}).outputText;
const tick = () => new Promise(resolve => setImmediate(resolve));
function deferred() {
  let resolve, reject;
  const promise = new Promise((yes, no) => { resolve = yes; reject = no; });
  return { promise, resolve, reject };
}
class ApiError extends Error { constructor(status) { super(String(status)); this.status = status; } }

// Exercise provider request/session callbacks with controlled HTTP completion ordering.
// This harness does not replace React mounting or browser verification.
function session(overrides = {}, stored = null) {
  const storage = new Map(stored ? [['marketboard.refreshToken', stored]] : []);
  const effects = [];
  const api = {
    ApiError,
    login: async () => ({ accessToken: 'old-access', refreshToken: 'old-refresh' }),
    logout: async () => {},
    request: async (_, options) => {
      if (options.accessToken === 'old-access') throw new ApiError(401);
      return options.accessToken;
    },
    ...overrides,
  };
  const exports = {};
  runInNewContext(compiled, {
    exports,
    localStorage: { setItem: (k, v) => storage.set(k, v), getItem: k => storage.get(k) ?? null, removeItem: k => storage.delete(k) },
    require: name => {
      if (name === './api') return api;
      if (name === './jwt') return { decodeJwt: () => ({ sub: '1', email: 'test@example.com', role: 'USER' }) };
      if (name === 'react/jsx-runtime') return { jsx: (_, props) => props };
      if (name === 'react') return {
        createContext: () => ({ Provider: {} }), useState: initial => [initial, () => {}],
        useRef: initial => ({ current: initial }), useCallback: fn => fn,
        useMemo: fn => fn(), useEffect: fn => effects.push(fn),
      };
      throw new Error(name);
    },
  });
  return { auth: exports.AuthProvider({ children: null }).value, effects, storage };
}

test('eight concurrent 401 responses share one refresh', async () => {
  const pending = deferred(); let calls = 0;
  const { auth } = session({ refresh: () => { calls++; return pending.promise; } });
  await auth.login('a', 'b');
  const requests = Array.from({ length: 8 }, () => auth.authFetch('/data'));
  await tick();
  assert.equal(calls, 1);
  pending.resolve({ accessToken: 'new-access', refreshToken: 'new-refresh' });
  assert.deepEqual(await Promise.all(requests), Array(8).fill('new-access'));
});

test('late old-token 401 reuses the already refreshed access token', async () => {
  const late = deferred(); let calls = 0;
  const { auth } = session({
    refresh: async () => { calls++; return { accessToken: 'new-access', refreshToken: 'new-refresh' }; },
    request: async (path, options) => {
      if (options.accessToken !== 'old-access') return options.accessToken;
      if (path === '/late') return late.promise;
      throw new ApiError(401);
    },
  });
  await auth.login('a', 'b');
  const request = auth.authFetch('/late');
  await auth.authFetch('/first');
  late.reject(new ApiError(401));
  assert.equal(await request, 'new-access');
  assert.equal(calls, 1);
});

test('logout prevents a late refresh from restoring the session', async () => {
  const pending = deferred();
  const { auth, storage } = session({ refresh: () => pending.promise });
  await auth.login('a', 'b');
  const request = auth.authFetch('/data');
  const rejected = assert.rejects(request, /Session changed/);
  await tick();
  await auth.logout();
  pending.resolve({ accessToken: 'new-access', refreshToken: 'new-refresh' });
  await rejected;
  assert.equal(storage.size, 0);
});

test('effect replay shares initialization refresh', async () => {
  const pending = deferred(); let calls = 0;
  const { effects, storage } = session({ refresh: () => { calls++; return pending.promise; } }, 'stored');
  effects[0]()();
  effects[0]();
  assert.equal(calls, 1);
  pending.resolve({ accessToken: 'new-access', refreshToken: 'new-refresh' });
  await tick();
  assert.equal(storage.get('marketboard.refreshToken'), 'new-refresh');
});

test('a retried resource 500 does not clear the successfully refreshed session', async () => {
  const { auth, storage } = session({
    refresh: async () => ({ accessToken: 'new-access', refreshToken: 'new-refresh' }),
    request: async (_, options) => { throw new ApiError(options.accessToken === 'old-access' ? 401 : 500); },
  });
  await auth.login('a', 'b');
  await assert.rejects(auth.authFetch('/data'), { status: 500 });
  assert.equal(storage.get('marketboard.refreshToken'), 'new-refresh');
});
