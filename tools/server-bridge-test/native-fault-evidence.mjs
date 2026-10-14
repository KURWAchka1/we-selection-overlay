import assert from 'node:assert/strict';
import { channel } from './wire-codec.mjs';

export function faultPrefixes(report, minecraft) {
  assert.equal(report.minecraft, minecraft, 'Packet discovery came from another host');
  const tail = Buffer.concat([Buffer.from([Buffer.byteLength(channel)]), Buffer.from(channel)]);
  return Object.fromEntries(['toServer', 'toClient'].map(direction => {
    assert.match(report[direction], /^(?:[0-9a-f]{2})+$/);
    const bytes = Buffer.from(report[direction], 'hex'), size = bytes.length - tail.length;
    assert(size >= 1 && size <= 3, 'Invalid packet ID width');
    assert(bytes.subarray(size).equals(tail), 'Wrong channel or identifier encoding');
    let id = 0;
    for (let i = 0; i < size; i++) {
      assert.equal(Boolean(bytes[i] & 128), i < size - 1, 'Malformed packet ID');
      id |= (bytes[i] & 127) << (i * 7);
    }
    assert(size === 1 || id >= 2 ** (7 * (size - 1)), 'Noncanonical packet ID');
    return [direction, bytes];
  }));
}

function counters(value) {
  for (const key of ['connections', 'captures', 'commits', 'applied', 'pending', 'tasks'])
    assert(Number.isSafeInteger(value?.[key]) && value[key] >= 0, `Invalid counter: ${key}`);
}
export function verifyFaultEntry(entry) {
  assert(['fabric', 'neoforge'].includes(entry.loader));
  assert(['fault-slow', 'fault-late', 'fault-revoked'].includes(entry.scenario));
  const late = entry.scenario === 'fault-late', revoked = entry.scenario === 'fault-revoked';
  assert.deepEqual(entry.phases, ['setup', 'arm', ...(late ? ['timeout'] : []), 'verified', 'disconnect']);
  for (const value of [entry.baseline, entry.verified, entry.final]) counters(value);
  assert.equal(entry.baseline.connections, 0);
  for (const value of [entry.baseline, entry.verified, entry.final]) {
    assert.equal(value.pending, 0); assert.equal(value.tasks, 0);
  }
  assert.equal(entry.verified.connections, 1); assert.equal(entry.final.connections, 0);
  for (const value of [entry.verified, entry.final]) {
    assert.equal(value.captures - entry.baseline.captures, late ? 2 : 1);
    assert.equal(value.commits - entry.baseline.commits, revoked ? 0 : 1);
    assert.equal(value.applied - entry.baseline.applied, revoked ? 0 : 1);
  }
  assert.equal(entry.pendingObserved, true); assert.equal(entry.auditPassed, true);
  assert.deepEqual(entry.serverBounds, [10, 64, 20, 15, revoked ? 70 : 71, 27]);
  const relay = entry.relay;
  for (const key of ['commits', 'replies', 'held', 'released', 'peakBytes', 'delayedFrames'])
    assert(Number.isSafeInteger(relay?.[key]) && relay[key] >= 0, `Invalid relay counter: ${key}`);
  assert.equal(relay.commits, 1); assert.equal(relay.replies, 1);
  assert.equal(relay.held, late || revoked ? 1 : 0); assert.equal(relay.released, relay.held);
  assert(relay.peakBytes > 0 && relay.peakBytes <= 8 * 1024 * 1024);
  assert.equal(relay.delayedFrames > 0, !late && !revoked);
  if (late) {
    assert.match(entry.heldReply?.requestId, /^[1-9][0-9]*$/);
    assert.equal(entry.heldReply.commitStatus, 0);
    assert.deepEqual(entry.heldReply.bounds, entry.serverBounds);
  } else assert.equal(entry.heldReply, undefined);
  if (revoked) {
    counters(entry.beforeDelivery);
    assert.equal(entry.beforeDelivery.commits, entry.baseline.commits);
    assert.equal(entry.beforeDelivery.applied, entry.baseline.applied);
    assert.equal(entry.revokedBeforeDelivery, true);
  } else assert.equal(entry.revokedBeforeDelivery, false);
}
export function verifyFaultMatrix(entries) {
  assert.equal(entries.length, 6);
  for (const entry of entries) verifyFaultEntry(entry);
  assert.deepEqual(entries.map(e => `${e.loader}/${e.scenario}`).sort(),
    ['fabric', 'neoforge'].flatMap(loader => ['slow', 'late', 'revoked'].map(mode => `${loader}/fault-${mode}`)).sort());
}
