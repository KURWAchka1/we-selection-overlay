import assert from 'node:assert/strict';
import test from 'node:test';
import { verifyLoadEvidence } from './load-evidence.mjs';

const options = { players: 2, samples: 100, installed: false, baselineOnly: true };
function evidence() {
  const raw = { validated: true, label: 'silent', samples: 100, players: 2,
    tickMillis: Array.from({ length: 100 }, (_, i) => i),
    wallMillis: 5000, sent: [0, 0], received: [0, 0], generatedChunks: 0, chunkLoads: 0,
    p50: 49, p95: 94, p99: 98, max: 99, ownerCpuMillis: 10, ownerAllocatedBytes: 100,
    gcCount: 0, gcMillis: 0, usedHeapBytes: 1000,
    startedAt: '2026-10-13T00:00:00.000Z', endedAt: '2026-10-13T00:00:05.000Z' };
  const { tickMillis, validated, ...values } = raw;
  return { raw, summary: { run: '1-absent', worldDigest: 'fixture', ...structuredClone(values) } };
}

test('retained raw ticks and summary reconcile without modifying either', () => {
  const { raw, summary } = evidence(), before = structuredClone(raw);
  verifyLoadEvidence(raw, summary, options); assert.deepEqual(raw, before);
});
test('acceptance flags and matching summary are not substitutes for valid statistics', () => {
  for (const key of ['p50', 'p95', 'p99', 'max']) {
    const { raw, summary } = evidence(); raw[key]++; summary[key]++;
    assert.throws(() => verifyLoadEvidence(raw, summary, options), /Incorrect/);
  }
});
test('post-acceptance corruption and stale summaries are rejected', () => {
  for (const key of ['wallMillis', 'p99', 'sent', 'ownerAllocatedBytes']) {
    const { raw, summary } = evidence(); raw[key] = key === 'sent' ? [1, 0] : 12345;
    assert.throws(() => verifyLoadEvidence(raw, summary, options), /Summary differs/);
  }
  const { raw, summary } = evidence(); raw.validated = false;
  assert.throws(() => verifyLoadEvidence(raw, summary, options), /not accepted/);
});
test('invalid resource counters and timestamps cannot become cost evidence', () => {
  for (const key of ['ownerCpuMillis', 'ownerAllocatedBytes', 'gcCount', 'gcMillis', 'usedHeapBytes']) {
    const { raw, summary } = evidence(); raw[key] = -1; summary[key] = -1;
    assert.throws(() => verifyLoadEvidence(raw, summary, options), /Invalid/);
  }
  const { raw, summary } = evidence(); raw.endedAt = raw.startedAt; summary.endedAt = raw.endedAt;
  assert.throws(() => verifyLoadEvidence(raw, summary, options), /timestamps/);
});
