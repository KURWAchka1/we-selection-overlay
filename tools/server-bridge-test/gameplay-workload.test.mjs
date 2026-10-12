import assert from 'node:assert/strict';
import test from 'node:test';
import { validateLoadWindow } from './load-window.mjs';
import { bridgeConsumerCount, movement } from './gameplay-workload.mjs';

const options = { players: 4, samples: 100, installed: true, baselineOnly: false, gameplay: true, bridgePlayers: 3 };
function window(label = 'many', installed = true) {
  const active = installed && ['one', 'many', 'flood'].includes(label);
  return { label, samples: 100, players: 4, wallMillis: 5000, tickMillis: Array(100).fill(2),
    generatedChunks: 0, chunkLoads: 0, connections: installed && label !== 'silent' ? 3 : 0,
    sent: active ? [10, 10, 10, 0] : [0, 0, 0, 0], received: active ? [10, 10, 10, 0] : [0, 0, 0, 0],
    bridge: installed ? { admitted: active ? 30 : 0, reads: active ? 30 : 0, replies: active ? 30 : 0,
      bytes: active ? 1920 : 0, malformed: 0, throttled: label === 'flood' ? 200 : 0 } : null,
    queueAfter: installed ? { pending: 0, peak: 3, expired: 0, tasks: 0 } : undefined,
    ordinaryReplyLatencies: { count: active ? 10 : 0, maxMillis: active ? 100 : 0 }, movementPackets: [25, 25, 25, 25],
    gameplay: { version: 1, ticks: 100, editBatches: 5, changedBlocks: 2560, invalidMoves: 0,
      acceptedMoves: [25, 25, 25, 25], entities: 16, entityTickDeltas: Array(16).fill(100) } };
}

test('roles keep ordinary actors at every supported count and permit global pressure at 32', () => {
  for (let players = 4; players <= 32; players++) {
    const consumers = bridgeConsumerCount(players);
    assert(consumers > 0 && consumers < players && players - consumers >= players / 8);
  }
  assert.equal(bridgeConsumerCount(32), 28);
  assert((bridgeConsumerCount(32) - 1) * 4 + 2 > 100);
  for (const players of [2, 33, 4.5, NaN]) assert.throws(() => bridgeConsumerCount(players));
  assert.throws(() => validateLoadWindow(window(), { ...options, bridgePlayers: 4 }));
  assert.throws(() => validateLoadWindow(window('unknown', false), { ...options, installed: false }));
});

test('flight route remains bounded, finite and smooth for every actor', () => {
  for (let index = 0; index < 32; index++) {
    let previous;
    for (let step = 0; step < 1000; step++) {
      const p = movement(index, step);
      assert(p.x >= 10 && p.x <= 22 && p.z >= 10 && p.z <= 22);
      assert.equal(p.y, 80 + index % 2);
      assert.deepEqual(p.flags, { onGround: false, hasHorizontalCollision: false });
      if (previous) assert(Math.hypot(p.x - previous.x, p.z - previous.z) < .37);
      previous = p;
    }
  }
  for (const args of [[32, 0], [-1, 0], [0, -1], [0, Infinity]]) assert.throws(() => movement(...args));
});

test('matched absent/installed gameplay retains bridge gates and non-consumer isolation', () => {
  for (const installed of [false, true]) for (const label of ['silent', 'idle', 'one', 'many', 'flood', 'recovery'])
    validateLoadWindow(window(label, installed), { ...options, installed });
});

test('missing gameplay, stationary actors, silent entity ticks and wrong edit volumes fail', () => {
  const mutations = [d => delete d.gameplay, d => d.gameplay.ticks--, d => d.gameplay.editBatches--,
    d => d.gameplay.changedBlocks--, d => d.gameplay.entities--, d => d.gameplay.entityTickDeltas.pop(),
    d => d.gameplay.entityTickDeltas[0] = 0, d => d.gameplay.acceptedMoves[3] = 0,
    d => d.gameplay.acceptedMoves[0] = NaN, d => d.gameplay.invalidMoves++,
    d => d.movementPackets.pop(), d => d.movementPackets[0] = 0, d => d.chunkLoads++];
  for (const mutate of mutations) { const d = window(); mutate(d); assert.throws(() => validateLoadWindow(d, options)); }
});

test('ordinary players never become consumers and active consumers keep their progress gates', () => {
  for (const mutate of [d => d.sent[3]++, d => d.received[3]++, d => d.connections++,
    d => d.received[2] = 0, d => d.ordinaryReplyLatencies.count--,
    d => d.ordinaryReplyLatencies.maxMillis = 2001]) {
    const d = window(); mutate(d); assert.throws(() => validateLoadWindow(d, options));
  }
});
