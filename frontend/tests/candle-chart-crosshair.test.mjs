import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import ts from 'typescript';

test('crosshair maps the selected timestamp to a candle and resets outside the chart', () => {
  const effects = [];
  let state;
  let onCrosshair;
  const series = { setData() {} };
  const chart = {
    panes: () => [{ setStretchFactor() {} }],
    addSeries: () => series,
    subscribeCrosshairMove: handler => { onCrosshair = handler; },
    unsubscribeCrosshairMove: handler => { assert.equal(handler, onCrosshair); },
    remove() {},
  };
  const modules = {
    react: {
      useEffect: effect => effects.push(effect),
      useRef: value => ({ current: value === null ? {} : value }),
      useState: () => [null, next => { state = next; }],
    },
    'react/jsx-runtime': { jsx: (type, props) => ({ type, props }), jsxs: (type, props) => ({ type, props }) },
    'lightweight-charts': { createChart: () => chart, ColorType: { Solid: 'solid' } },
    '@astryxdesign/core/Card': { Card: 'div' },
    '@astryxdesign/core/theme': { useTheme: () => ({ tokens: {} }) },
    '@/lib/candle-chart-info': { formatCandleInfo: () => '' },
  };
  const source = readFileSync(new URL('../src/components/CandleChart.tsx', import.meta.url), 'utf8');
  const compiled = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX },
  }).outputText;
  const exports = {};
  new Function('require', 'exports', compiled)(name => {
    assert.ok(name in modules, `Unexpected dependency: ${name}`);
    return modules[name];
  }, exports);
  const candles = [
    { ts: '2026-10-13T00:00:00Z', open: 100, high: 104, low: 99, close: 103, volume: 0 },
    { ts: '2026-10-14T00:00:00Z', open: 103, high: 106, low: 101, close: 105, volume: 100 },
  ];
  exports.CandleChart({ candles });
  const cleanups = effects.map(effect => effect());
  onCrosshair({ point: { x: 10, y: 20 }, time: Date.parse(candles[0].ts) / 1000 });
  assert.equal(state.source, candles);
  assert.equal(state.index, 0);
  onCrosshair({ point: { x: 20, y: 20 }, time: Date.parse(candles[1].ts) / 1000 });
  assert.equal(state.index, 1);
  onCrosshair({});
  assert.equal(state, null);
  onCrosshair({ point: { x: 30, y: 20 }, time: 1 });
  assert.equal(state, null);
  for (const cleanup of cleanups.reverse()) cleanup?.();
});
