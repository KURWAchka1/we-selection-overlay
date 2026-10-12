import assert from 'node:assert/strict';
import test from 'node:test';
import { request } from './wire-codec.mjs';
import { queuedRequest, validateQueuedFault, validateLifecycleWindow, validateActiveCycle, sendPlayMovement } from './active-lifecycle.mjs';

test('reconnecting actors cannot send play movement before the real play login', () => {
  const writes = [], packet = { x: 16, y: 80, z: 16 };
  const peer = { ended: false, playReady: false, client: { state: 'configuration', write: (...args) => writes.push(args) } };
  for (const state of ['handshaking', 'login', 'configuration', 'play']) {
    peer.client.state = state; assert.equal(sendPlayMovement(peer, packet), false);
  }
  assert.equal(writes.length, 0);
  peer.playReady = true; assert.equal(sendPlayMovement(peer, packet), true);
  assert.deepEqual(writes, [['position', packet]]);
  peer.client.state = 'configuration'; assert.equal(sendPlayMovement(peer, packet), false);
  peer.client.state = 'play'; peer.ended = true; assert.equal(sendPlayMovement(peer, packet), false);
  assert.equal(writes.length, 1);
});

function fault(action) {
  const before = { metrics: { connections: 28, admitted: 78, throttled: 1, malformed: 0, selectionReads: 50, replies: 78, encodedReplyBytes: 4572 },
    queue: { pending: 20, peakPending: 28, queued: 98, expired: 0, cancelled: 0, maxWaitNanos: 200000000, maxSliceNanos: 500000 } };
  const after = structuredClone(before);
  if (action === 'disconnect') { after.metrics.connections--; after.queue.pending--; after.queue.cancelled++; }
  if (['bridgeoff', 'stopwe'].includes(action)) {
    after.metrics.connections = after.queue.pending = 0; after.queue.cancelled = 20;
    after.metrics.replies += 28; after.metrics.encodedReplyBytes += 28 * 38;
  }
  return { version: 1, action, player: 'WesoLoad01', attempts: 1, requestHex: request(Buffer.alloc(16, 1), 9).toString('hex'),
    bridgeEnabled: ['revoke', 'disconnect'].includes(action), retainedTarget: action === 'revoke', before, after };
}

test('queued fault gates require actual bounded input and exact lifecycle cleanup', () => {
  for (const action of ['revoke', 'disconnect', 'bridgeoff', 'stopwe']) validateQueuedFault(fault(action));
  assert.equal(queuedRequest(fault('revoke')).id, 9n);
  for (const mutate of [f => f.before.queue.pending = 0, f => f.after.queue.cancelled--,
    f => f.after.metrics.selectionReads++, f => f.retainedTarget = true, f => f.after.metrics.replies++,
    f => f.requestHex = '00', f => f.attempts = 201, f => delete f.before.metrics.admitted,
    f => f.after.queue.maxWaitNanos = NaN]) {
    const value = fault('bridgeoff'); mutate(value); assert.throws(() => validateQueuedFault(value));
  }
  const disconnected = fault('disconnect'); disconnected.after.metrics.connections++;
  assert.throws(() => validateQueuedFault(disconnected));
  const revoked = fault('revoke'); revoked.after.queue.pending--;
  assert.throws(() => validateQueuedFault(revoked));
});

function window(label = 'flood') {
  return { label, samples: 200, players: 32, tickMillis: Array(200).fill(1), tickTimelineVersion: 1, wallMillis: 10000,
    chunkLoads: 0, generatedChunks: 0,
    gameplay: { version: 1, ticks: 200, invalidMoves: 0, entities: 16, entityTickDeltas: Array(16).fill(200),
      acceptedMoves: Array(32).fill(40), editsEnabled: true, editBatches: 10, changedBlocks: 5120 },
    sent: Array.from({ length: 32 }, (_, i) => i < 28 ? 20 : 0),
    received: Array.from({ length: 32 }, (_, i) => i < 28 ? 20 : 0), movementPackets: Array(32).fill(50),
    ordinaryReplyLatencies: { count: 20, maxMillis: 300 },
    bridgeBefore: { connections: 28, expired: 0, pending: 10, dispatchTasks: 1 },
    bridgeAfter: { connections: 28, expired: 0, pending: 10, dispatchTasks: 1 } };
}

test('lifecycle workload cannot pass stopped gameplay, leakage or ordinary starvation', () => {
  validateLifecycleWindow(window(), 200);
  for (const mutate of [d => d.gameplay.acceptedMoves[31] = 0, d => d.gameplay.entityTickDeltas[0] = 0,
    d => d.gameplay.changedBlocks--, d => d.gameplay.editsEnabled = false, d => d.sent[28]++,
    d => d.received[31]++, d => d.received[0] = d.ordinaryReplyLatencies.count = 0,
    d => d.sent[0] = 100, d => d.received[27] = 0,
    d => d.ordinaryReplyLatencies.maxMillis = 2001, d => d.chunkLoads++, d => d.players = 31,
    d => d.movementPackets[0] = 0, d => d.tickMillis[0] = NaN,
    d => delete d.bridgeBefore, d => delete d.bridgeAfter]) {
    const value = window(); mutate(value); assert.throws(() => validateLifecycleWindow(value, 200));
  }
});

test('disabled dependency stops only the WorldEdit workload; idle cannot hide source work', () => {
  const disabled = window('stopwe');
  disabled.gameplay.editsEnabled = false; disabled.gameplay.editBatches = disabled.gameplay.changedBlocks = 0;
  disabled.bridgeAfter = null;
  disabled.sent.fill(0); disabled.received.fill(0); disabled.ordinaryReplyLatencies.count = 0;
  validateLifecycleWindow(disabled, 200);
  disabled.gameplay.acceptedMoves[0] = 0; assert.throws(() => validateLifecycleWindow(disabled, 200));
  const idle = window('idle'); idle.sent.fill(0); idle.received.fill(0); idle.ordinaryReplyLatencies.count = 0;
  idle.bridgeBefore = { reads: 12, replies: 40, bytes: 2500, connections: 28, expired: 0, pending: 0, dispatchTasks: 0 };
  idle.bridgeAfter = { ...idle.bridgeBefore, pending: 0, dispatchTasks: 0 };
  validateLifecycleWindow(idle, 200);
  idle.bridgeAfter.reads++; assert.throws(() => validateLifecycleWindow(idle, 200));
});

test('whole-cycle acceptance requires all faults inside measured windows and exact restart counts', () => {
  const labels = ['flood', 'revoke', 'disconnect', 'restart-1', 'restart-2', 'restart-3', 'idle', 'stopwe'];
  const windows = labels.map(label => ({ ...window(label), startedAt: '2026-10-12T12:00:00Z', endedAt: '2026-10-12T12:00:10Z' }));
  const idle = windows[6]; idle.sent.fill(0); idle.received.fill(0); idle.ordinaryReplyLatencies.count = 0;
  idle.bridgeBefore = idle.bridgeAfter = { reads: 12, replies: 40, bytes: 2500, connections: 28, expired: 0, pending: 0, dispatchTasks: 0 };
  windows[7].gameplay.editsEnabled = false; windows[7].gameplay.editBatches = windows[7].gameplay.changedBlocks = 0;
  windows[7].bridgeAfter = null;
  const result = { windows, faults: ['revoke', 'disconnect', 'bridgeoff', 'bridgeoff', 'bridgeoff', 'stopwe']
    .map(action => ({ ...fault(action), at: '2026-10-12T12:00:01Z' })),
    recoveries: [1, 2, 3].map(cycle => ({ cycle, restartHints: 28, hintBytes: 616, recoveryMillis: 400 })) };
  validateActiveCycle(result, 200);
  for (const mutate of [r => r.faults.pop(), r => r.windows.reverse(), r => r.faults[0].at = '2026-10-12T12:00:11Z',
    r => r.recoveries[1].hintBytes++, r => r.faults[2].action = 'revoke']) {
    const copy = structuredClone(result); mutate(copy); assert.throws(() => validateActiveCycle(copy, 200));
  }
});
