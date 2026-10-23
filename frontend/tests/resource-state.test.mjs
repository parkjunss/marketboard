import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { runInNewContext } from 'node:vm';
import ts from 'typescript';

const compiled = ts.transpileModule(readFileSync(new URL('../src/lib/use-resource.ts', import.meta.url), 'utf8'), {
  compilerOptions: { module: ts.ModuleKind.CommonJS },
}).outputText;
const tick = () => new Promise(resolve => setImmediate(resolve));
function deferred() {
  let resolve, reject;
  const promise = new Promise((yes, no) => { resolve = yes; reject = no; });
  return { promise, resolve, reject };
}

// Controlled hook lifecycle, following the auth-refresh harness. Not a browser mount.
function mount(initialLoad, fetcher = async () => []) {
  const cells = [];
  let cursor, pending, cleanup, dependencies, load = initialLoad;
  let owner = 1;
  const auth = { authFetch: fetcher, user: { id: owner } };
  const exports = {};
  runInNewContext(compiled, {
    exports, AbortController,
    AbortSignal: { any: AbortSignal.any.bind(AbortSignal), timeout: () => AbortSignal.timeout(5) },
    require: name => {
      if (name === './auth-context') return { useAuth: () => auth };
      if (name === 'react') return {
        useState: initial => {
          const i = cursor++;
          if (!(i in cells)) cells[i] = initial;
          return [cells[i], value => { cells[i] = typeof value === 'function' ? value(cells[i]) : value; }];
        },
        useRef: initial => {
          const i = cursor++;
          if (!(i in cells)) cells[i] = { current: initial };
          return cells[i];
        },
        useEffect: (effect, deps) => {
          if (!dependencies || deps.some((dep, i) => dep !== dependencies[i])) {
            pending = effect; dependencies = deps;
          }
        },
      };
      throw new Error(name);
    },
  });
  return {
    render(nextLoad = load) {
      load = nextLoad; cursor = 0;
      const result = exports.useResource(load);
      if (pending) { cleanup?.(); cleanup = pending(); pending = null; }
      return result;
    },
    changeOwner() { auth.user = { id: ++owner }; },
    unmount() { cleanup?.(); },
  };
}

test('empty response is success, rejection is error, retry recovers and double-click sends once', async () => {
  let calls = 0;
  const load = async () => { calls++; if (calls === 1) throw new Error('HTTP 500'); return []; };
  const app = mount(load);
  assert.equal(app.render().status, 'loading');
  await tick();
  const failed = app.render();
  assert.equal(failed.status, 'error');
  failed.retry(); failed.retry();
  assert.equal(app.render().status, 'loading');
  await tick();
  const result = app.render();
  assert.equal(result.status, 'success');
  assert.equal(result.data.length, 0);
  assert.equal(calls, 2);
  app.unmount();
});

test('slow old ticker cannot overwrite new ticker; cleanup aborts requests', async () => {
  const old = deferred(); const current = deferred(); const signals = [];
  const app = mount(fetch => fetch('/old'), (path, options) => {
    signals.push(options.signal);
    return path === '/old' ? old.promise : current.promise;
  });
  app.render(); await tick();
  assert.equal(app.render(fetch => fetch('/new')).status, 'loading');
  await tick();
  assert.equal(signals[0].aborted, true);
  current.resolve(['new']); await tick();
  old.resolve(['old']); await tick();
  assert.equal(app.render().data[0], 'new');
  app.unmount();
  assert.equal(signals[1].aborted, true);
});

test('timeout and network rejection leave loading; owner change hides previous data', async () => {
  const app = mount(fetch => fetch('/slow'), (_, { signal }) => new Promise((_, reject) => {
    signal.addEventListener('abort', () => reject(signal.reason), { once: true });
  }));
  app.render();
  await new Promise(resolve => setTimeout(resolve, 20));
  assert.equal(app.render().status, 'error');
  const good = async () => ['private'];
  app.render(good); await tick();
  assert.equal(app.render().status, 'success');
  app.changeOwner();
  assert.equal(app.render().status, 'loading');
  app.unmount();
});
