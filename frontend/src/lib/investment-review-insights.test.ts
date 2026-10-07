import assert from 'node:assert/strict';
import test from 'node:test';
import { buildReviewInsights } from './investment-review-insights.ts';

test('builds risk signals and an editable decision draft from observed data', () => {
  const result = buildReviewInsights(
    [{ slug: 'VIX', name: 'VIX', change: 20, unit: '%' }, { slug: 'US10Y', name: '미국 10년 금리', change: 12, unit: 'bp' }],
    { snapshotDate: '2026-10-07', advancingCount: 30, decliningCount: 65, unchangedCount: 5, new52wHighCount: 4, new52wLowCount: 9, universeSize: 100, computedAt: '2026-10-07T00:00:00Z' },
  );

  assert.equal(result.metrics[1].value, '상승 30.0%');
  assert.equal(result.risks.length, 4);
  assert.match(result.draft, /자동 매매 신호가 아니므로/);
});
