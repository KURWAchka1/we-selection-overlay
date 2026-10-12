import assert from 'node:assert/strict';

export function sendPlayMovement(peer, packet) {
  if (peer.ended || !peer.playReady || peer.client.state !== 'play') return false;
  peer.client.write('position', packet);
  return true;
}

export function queuedRequest(fault) {
  assert.equal(fault.version, 1);
  assert.match(fault.player, /^WesoLoad[0-9]{2}$/);
  assert(Number.isInteger(fault.attempts) && fault.attempts >= 1 && fault.attempts <= 200);
  assert.match(fault.requestHex, /^5745534f0103[0-9a-f]{48}$/);
  const bytes = Buffer.from(fault.requestHex, 'hex');
  assert(!bytes.subarray(6, 22).equals(Buffer.alloc(16)), 'Queued input has no epoch');
  assert(bytes.readBigInt64BE(22) > 0n);
  return { epoch: bytes.subarray(6, 22), id: bytes.readBigInt64BE(22) };
}

export function validateQueuedFault(fault) {
  queuedRequest(fault);
  const before = fault.before, after = fault.after;
  for (const state of [before, after]) {
    for (const key of ['connections', 'admitted', 'throttled', 'malformed', 'selectionReads', 'replies', 'encodedReplyBytes'])
      assert(Number.isSafeInteger(state.metrics[key]) && state.metrics[key] >= 0, `Invalid metric ${key}`);
    for (const key of ['pending', 'peakPending', 'queued', 'expired', 'cancelled', 'maxWaitNanos', 'maxSliceNanos'])
      assert(Number.isSafeInteger(state.queue[key]) && state.queue[key] >= 0, `Invalid queue metric ${key}`);
    assert.equal(state.metrics.malformed, 0); assert.equal(state.queue.expired, 0);
  }
  assert.equal(before.metrics.connections, 28);
  assert(before.queue.pending > 0 && before.queue.pending <= 28);
  assert.equal(after.metrics.selectionReads, before.metrics.selectionReads, 'Fault performed a source read');
  assert.equal(after.queue.expired, before.queue.expired);
  if (fault.action === 'revoke') {
    assert.equal(fault.bridgeEnabled, true);
    assert.equal(fault.retainedTarget, true);
    assert.equal(after.queue.pending, before.queue.pending, 'Revocation test lost its queued request');
  } else if (fault.action === 'disconnect') {
    assert.equal(fault.bridgeEnabled, true);
    assert.equal(fault.retainedTarget, false, 'Disconnected peer retained');
    assert.equal(after.queue.pending, before.queue.pending - 1);
    assert.equal(after.metrics.connections, before.metrics.connections - 1);
    assert.equal(after.queue.cancelled, before.queue.cancelled + 1);
  } else {
    assert(['bridgeoff', 'stopwe'].includes(fault.action));
    assert.equal(fault.bridgeEnabled, false);
    assert.equal(fault.retainedTarget, false);
    assert.equal(after.metrics.connections, 0);
    assert.equal(after.queue.pending, 0);
    assert.equal(after.queue.cancelled, before.queue.cancelled + before.queue.pending);
    assert.equal(after.metrics.replies - before.metrics.replies, before.metrics.connections);
    assert.equal(after.metrics.encodedReplyBytes - before.metrics.encodedReplyBytes, before.metrics.connections * 38);
  }
}

export function validateLifecycleWindow(data, samples) {
  assert(['flood', 'revoke', 'disconnect', 'restart-1', 'restart-2', 'restart-3', 'idle', 'stopwe'].includes(data.label));
  assert.equal(data.samples, samples); assert.equal(data.players, 32);
  assert.equal(data.tickMillis.length, samples);
  assert(data.tickMillis.every(n => Number.isFinite(n) && n >= 0));
  assert.equal(data.tickTimelineVersion, 1);
  assert(Number.isFinite(data.wallMillis) && data.wallMillis > 0);
  assert.equal(data.chunkLoads, 0); assert.equal(data.generatedChunks, 0);
  const g = data.gameplay;
  assert.equal(g.version, 1); assert.equal(g.ticks, samples);
  assert.equal(g.invalidMoves, 0);
  assert.equal(g.entities, 16); assert.equal(g.entityTickDeltas.length, 16);
  assert(g.entityTickDeltas.every(n => Number.isInteger(n) && Math.abs(n - samples) <= 1));
  assert.equal(g.acceptedMoves.length, 32);
  assert(g.acceptedMoves.every(n => Number.isSafeInteger(n) && n >= Math.floor(samples / 20)), 'An actor stopped moving');
  if (data.label === 'stopwe') {
    assert.equal(g.editsEnabled, false);
    assert(Number.isInteger(g.editBatches) && g.editBatches >= 0 && g.editBatches < samples / 20);
  } else {
    assert.equal(g.editsEnabled, true); assert.equal(g.editBatches, samples / 20);
  }
  assert.equal(g.changedBlocks, g.editBatches * 512);
  for (const values of [data.sent, data.received, data.movementPackets]) {
    assert.equal(values.length, 32); assert(values.every(n => Number.isSafeInteger(n) && n >= 0));
  }
  assert(data.movementPackets.every((n, i) => n >= g.acceptedMoves[i]));
  assert(data.sent.slice(28).every(n => n === 0)); assert(data.received.slice(28).every(n => n === 0));
  assert.equal(data.ordinaryReplyLatencies.count, data.received[0]);
  assert(Number.isFinite(data.ordinaryReplyLatencies.maxMillis) && data.ordinaryReplyLatencies.maxMillis <= 2000);
  if (!['idle', 'stopwe'].includes(data.label)) {
    assert(data.sent[0] >= Math.floor(samples / 40), 'Ordinary actor sent no sustained workload');
    assert(data.received[0] >= Math.floor(samples / 40), 'Ordinary actor lost progress');
    // One abandoned request at graceful close, plus measurement-boundary tolerance.
    assert(data.received[0] >= Math.floor(data.sent[0] * .9) - 1, 'Ordinary actor lost too many replies');
    assert(data.received.slice(0, 28).every(n => n > 0), 'A consumer lost all progress');
  }
  assert(data.bridgeBefore, 'Missing pre-window core evidence');
  if (data.label === 'stopwe') assert.equal(data.bridgeAfter, null);
  else assert(data.bridgeAfter, 'Missing post-window core evidence');
  for (const state of [data.bridgeBefore, data.bridgeAfter].filter(Boolean)) {
    assert.equal(state.connections, 28); assert.equal(state.expired, 0);
    assert(Number.isSafeInteger(state.pending) && state.pending >= 0 && state.pending <= 28);
    assert.equal(state.dispatchTasks, state.pending > 0 ? 1 : 0);
  }
  if (data.label === 'idle') {
    assert(data.sent.every(n => n === 0)); assert(data.received.every(n => n === 0));
    assert.equal(data.bridgeAfter.reads, data.bridgeBefore.reads);
    assert.equal(data.bridgeAfter.replies, data.bridgeBefore.replies);
    assert.equal(data.bridgeAfter.bytes, data.bridgeBefore.bytes);
    assert.equal(data.bridgeAfter.pending, 0); assert.equal(data.bridgeAfter.dispatchTasks, 0);
    assert.deepEqual(data.bridgeAfter, data.bridgeBefore, 'Idle caused background control work');
  }
}

export function validateActiveCycle(result, samples) {
  assert.equal(result.faults.length, 6); assert.equal(result.recoveries.length, 3); assert.equal(result.windows.length, 8);
  const labels = ['flood', 'revoke', 'disconnect', 'restart-1', 'restart-2', 'restart-3', 'idle', 'stopwe'];
  assert.deepEqual(result.windows.map(w => w.label), labels);
  for (const window of result.windows) validateLifecycleWindow(window, samples);
  const faultLabels = ['revoke', 'disconnect', 'restart-1', 'restart-2', 'restart-3', 'stopwe'];
  for (const [index, fault] of result.faults.entries()) {
    validateQueuedFault(fault);
    assert.equal(fault.action, index < 2 ? faultLabels[index] : index === 5 ? 'stopwe' : 'bridgeoff');
    const window = result.windows.find(w => w.label === faultLabels[index]);
    const at = Date.parse(fault.at);
    assert(Number.isFinite(at) && at >= Date.parse(window.startedAt) && at < Date.parse(window.endedAt),
      'Fault did not happen inside its measured gameplay window');
  }
  for (const [index, recovery] of result.recoveries.entries()) {
    assert.equal(recovery.cycle, index + 1); assert.equal(recovery.restartHints, 28); assert.equal(recovery.hintBytes, 616);
    assert(Number.isFinite(recovery.recoveryMillis) && recovery.recoveryMillis > 0 && recovery.recoveryMillis <= 15000);
  }
}
