import assert from 'node:assert/strict';

export function bridgeConsumerCount(players) {
  assert(Number.isInteger(players) && players >= 4 && players <= 32);
  return players - Math.ceil(players / 8);
}

export function movement(index, step) {
  assert(Number.isInteger(index) && index >= 0 && index < 32);
  assert(Number.isSafeInteger(step) && step >= 0);
  const angle = step * .06 + index * Math.PI * 2 / 32;
  return { x: 16 + 6 * Math.cos(angle), y: 80 + index % 2, z: 16 + 6 * Math.sin(angle),
    flags: { onGround: false, hasHorizontalCollision: false } };
}

export function validateGameplayActivity(data, { players, samples, movementPackets }) {
  const g = data.gameplay;
  assert.equal(g?.version, 1, 'Gameplay instrumentation missing');
  assert.equal(g.ticks, samples);
  assert.equal(g.editBatches, samples / 20, 'WorldEdit workload stopped or changed');
  assert.equal(g.changedBlocks, samples / 20 * 512, 'WorldEdit edit volume differs');
  assert.equal(g.entities, 16);
  assert.equal(g.entityTickDeltas.length, 16);
  assert(g.entityTickDeltas.every(n => Number.isInteger(n) && Math.abs(n - samples) <= 1), 'Entities did not tick');
  assert.equal(g.invalidMoves, 0, 'Movement escaped the fixture route');
  assert.equal(g.acceptedMoves.length, players);
  assert(g.acceptedMoves.every(n => Number.isSafeInteger(n) && n >= Math.floor(samples / 20)), 'A gameplay actor stopped moving');
  assert.equal(movementPackets.length, players);
  assert(movementPackets.every((n, i) => Number.isSafeInteger(n) && n >= g.acceptedMoves[i]), 'Movement evidence mismatch');
  assert.equal(data.chunkLoads, 0, 'Gameplay left the preloaded area');
}

export function validateGameplayWindow(data, { players, samples, installed, bridgePlayers }) {
  assert.equal(bridgePlayers, bridgeConsumerCount(players), 'Gameplay roles differ from the declared workload');
  assert(['silent', 'idle', 'one', 'many', 'flood', 'recovery'].includes(data.label), 'Unknown gameplay phase');
  validateGameplayActivity(data,{players,samples,movementPackets:data.movementPackets});
  if ('teleports' in data || 'bridgeFrames' in data) {
    for (const values of [data.teleports,data.bridgeFrames]) {
      assert.equal(values?.length,players);
      assert(values.every(n=>Number.isSafeInteger(n) && n>=0),'Invalid moving transport counters');
    }
    assert(data.teleports.every(n=>n===0),'Server corrected/teleported a measured moving actor');
    assert(data.bridgeFrames.slice(bridgePlayers).every(n=>n===0),'Unnegotiated player received a bridge frame');
    if (!installed || ['silent','idle','recovery'].includes(data.label))
      assert(data.bridgeFrames.every(n=>n===0),'Unsolicited idle/absent bridge frames');
  }
  assert(data.sent.slice(bridgePlayers).every(n => n === 0), 'Ordinary player sent application requests');
  assert(data.received.slice(bridgePlayers).every(n => n === 0), 'Unnegotiated player received application data');
  assert.equal(data.connections, installed && data.label !== 'silent' ? bridgePlayers : 0);
  if (installed && ['one', 'many', 'flood'].includes(data.label)) {
    assert.equal(data.ordinaryReplyLatencies.count, data.received[0]);
    assert(Number.isFinite(data.ordinaryReplyLatencies.maxMillis) && data.ordinaryReplyLatencies.maxMillis <= 2000,
      'Gameplay ordinary reply exceeded the 2s fixture gate');
  }
}
