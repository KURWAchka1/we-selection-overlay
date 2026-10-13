import assert from 'node:assert/strict';
import { validateLoadWindow } from './load-window.mjs';

export function verifyLoadEvidence(raw, summary, options) {
  assert.equal(raw.validated, true, 'Window was not accepted');
  validateLoadWindow(raw, options);
  const { tickMillis, validated, ...recorded } = raw;
  const { run, worldDigest, ...reported } = summary;
  assert.deepEqual(reported, recorded, 'Summary differs from retained window evidence');
  const sorted = [...tickMillis].sort((a, b) => a - b);
  for (const [key, fraction] of [['p50', .50], ['p95', .95], ['p99', .99], ['max', 1]])
    assert.equal(raw[key], sorted[Math.ceil(sorted.length * fraction) - 1], `Incorrect ${key} statistic`);
  for (const key of ['ownerCpuMillis', 'ownerAllocatedBytes', 'gcCount', 'gcMillis', 'usedHeapBytes'])
    assert(Number.isFinite(raw[key]) && raw[key] >= 0, `Invalid ${key}`);
  assert(Number.isFinite(Date.parse(raw.startedAt)) && Date.parse(raw.endedAt) > Date.parse(raw.startedAt), 'Invalid window timestamps');
}
