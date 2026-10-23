import assert from 'node:assert/strict';
import test from 'node:test';
import { summarizeDispatchEvidence } from './dispatch-evidence.mjs';

const row = (id, wallNanos, reasonsHex = '0', extra = {}) => ({
  id, thread: 47, wallNanos, reasonsHex, nativeMatched: true, cycles: 100,
  switches: reasonsHex === '0' ? 0 : 2, beforeReadNanos: 30, afterReadNanos: 40,
  classLoads: 0, stallTotals: {}, ...extra,
});

test('reason cohorts retain every root, use strict thresholds and do not convert cycles to time', () => {
  const result = summarizeDispatchEvidence([
    row(1, 500_000), row(2, 500_001, '180000000'), row(3, 1_000_000, '20'),
    row(4, 1_000_001, '1000'), row(5, 2_000_000, '0'),
    row(6, 2_000_001, '180000020', { classLoads: 1, stallTotals: { sleep: { count: 1 } } }),
  ]);
  assert.deepEqual(result.map(s => s.roots), [6, 5, 3, 1]);
  assert.equal(result[0].preemptedWithinNativeWindow, 2);
  assert.equal(result[0].suspendedWithinNativeWindow, 3);
  assert.equal(result[0].zeroSwitch, 2);
  assert.equal(result[3].preemptedWithinNativeWindow, 1);
  assert.equal(result[3].suspendedWithinNativeWindow, 1);
  assert.equal(result[3].withClassLoadOverlap, 1);
  assert.equal(result[3].withRecordedStallOverlap, 1);
  assert.deepEqual(result[3].bitmaps, { '180000020': 1 });
  assert(!JSON.stringify(result).includes('cpu'));
  for (const subset of result)
    assert.equal(Object.values(subset.bitmaps).reduce((a, b) => a + b, 0), subset.roots);
});

test('zero-switch long roots and overlapping evidence are not discarded or forced into exclusive causes', () => {
  const [summary] = summarizeDispatchEvidence([row(1, 12_000_000, '0', { classLoads: 1 }), row(2, 10, '180000020')]);
  assert.equal(summary.roots, 2);
  assert.equal(summary.zeroSwitch, 1);
  assert.equal(summary.preemptedWithinNativeWindow, 1);
  assert.equal(summary.suspendedWithinNativeWindow, 1);
  assert.equal(summary.withClassLoadOverlap, 1);
});

test('reason summary rejects incomplete, duplicate, oversized or invalid native input', () => {
  for (const input of [null, [], Array(2401).fill(row(1, 10)), [row(1, 10), row(1, 20)],
    [row(1, 10, '0', { nativeMatched: false })], [row(1, 10, '0', { cycles: -1 })],
    [row(1, Number.NaN)], [row(1, 10, '0', { switches: 0x1_0000_0000 })],
    [row(1, 10, '-1')], [row(1, 10, '10000000000000000')], [row(1, 10, '0', { stallTotals: [] })]])
    assert.throws(() => summarizeDispatchEvidence(input));
});
