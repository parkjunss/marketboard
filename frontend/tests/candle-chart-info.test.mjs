import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import ts from 'typescript';

const source = readFileSync(new URL('../src/lib/candle-chart-info.ts', import.meta.url), 'utf8');
const compiled = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext } }).outputText;
const { formatCandleInfo } = await import(`data:text/javascript;base64,${Buffer.from(compiled).toString('base64')}`);
const candle = { ts: '2026-10-14T14:30:00Z', open: 101, high: 106, low: 99, close: 105, volume: 12345 };

test('shows OHLC, UTC and change against the previous close rather than the open', () => {
  const text = formatCandleInfo(candle, 100);
  for (const part of ['UTC', '시가 101', '고가 106', '저가 99', '종가 105', '전봉 대비 +5.00%', '거래량 12,345']) {
    assert.ok(text.includes(part), text);
  }
  assert.ok(formatCandleInfo({ ...candle, close: 95 }, 100).includes('전봉 대비 -5.00%'));
});

test('missing evidence stays unavailable; zero volume is retained', () => {
  assert.equal(formatCandleInfo(undefined), '봉 데이터가 없습니다');
  for (const previous of [undefined, 0, NaN]) assert.ok(formatCandleInfo(candle, previous).includes('전봉 대비 —'));
  assert.ok(formatCandleInfo({ ...candle, volume: undefined }).includes('거래량 —'));
  assert.ok(formatCandleInfo({ ...candle, volume: 0 }).includes('거래량 0'));
});
