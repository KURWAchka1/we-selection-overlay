import assert from 'node:assert/strict';
import { writeFileSync } from 'node:fs';

export function validateLoadWindow(data, { players, samples, installed, baselineOnly }) {
  assert.equal(data.samples, samples); assert.equal(data.players, players);
  assert.equal(data.tickMillis.length, samples);
  assert(data.tickMillis.every(n => Number.isFinite(n) && n >= 0));
  assert(Number.isFinite(data.wallMillis) && data.wallMillis > 0);
  for (const values of [data.sent, data.received]) {
    assert.equal(values.length, players);
    assert(values.every(n => Number.isSafeInteger(n) && n >= 0));
  }
  assert.equal(data.generatedChunks, 0, 'Measurement unexpectedly generated chunks');
  if (baselineOnly) assert.equal(data.chunkLoads, 0, 'Controlled silent baseline loaded chunks');
  if (!installed) return;
  assert(data.bridge.admitted <= 100 + Math.ceil(100 * (data.wallMillis / 1000 + .5)));
  assert.equal(data.bridge.malformed, 0);
  if (['silent', 'idle', 'recovery'].includes(data.label)) {
    assert.equal(data.bridge.reads, 0); assert.equal(data.bridge.bytes, 0); assert.equal(data.bridge.replies, 0);
  } else {
    assert(['one', 'many', 'flood', 'ordered-first', 'ordered-last'].includes(data.label), 'Unknown active measurement phase');
    assert(data.sent[0] > 0, 'Ordinary requester sent no workload');
    assert(data.bridge.reads > 0);
    assert(data.received[0] >= Math.floor(data.sent[0] * .9), 'Ordinary requester starved');
    if (data.label === 'many') assert(data.received.every(n => n > 0), 'An active player received no reply');
    if (data.label === 'flood') assert(data.bridge.throttled > 0, 'Flood did not exercise admission');
    if (data.label.startsWith('ordered-')) {
      assert(data.received.every(n => n > 0), 'An ordered active player received no reply');
      assert.equal(data.ordinaryReplyLatencies.count, data.received[0]);
      assert(Number.isFinite(data.ordinaryReplyLatencies.maxMillis) && data.ordinaryReplyLatencies.maxMillis <= 2000,
        'Ordinary response latency exceeded the fixture gate');
    }
  }
  if (data.queueAfter) {
    assert(data.queueAfter.pending >= 0 && data.queueAfter.pending <= players);
    assert(data.queueAfter.tasks >= 0 && data.queueAfter.tasks <= 1);
    assert.equal(data.queueAfter.expired, 0, 'Live fixture requests expired');
    if (['silent', 'idle', 'recovery'].includes(data.label)) {
      assert.equal(data.queueAfter.pending, 0); assert.equal(data.queueAfter.tasks, 0);
    }
  }
  if (baselineOnly) assert.equal(data.bridge.admitted, 0, 'Silent baseline admitted application work');
}

export function recordLoadWindow(path, data, options) {
  // Persist counters before acceptance, so failed assertions remain inspectable.
  writeFileSync(path, JSON.stringify({ ...data, validated: false }, null, 2));
  validateLoadWindow(data, options);
  writeFileSync(path, JSON.stringify({ ...data, validated: true }, null, 2));
}
