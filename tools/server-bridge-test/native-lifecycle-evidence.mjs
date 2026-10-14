import assert from 'node:assert/strict';

export const lifecycleContexts = Object.freeze(['same-world respawn', 'Nether transition', 'Overworld return',
  'channel re-advertisement', 'same-process reconnect', 'negotiated plugin stop', 'silent plugin restart']);
const phases = Object.freeze({
  allowed: ['initial', 'editsetup', 'editquiet', 'editcancel', 'respawn', 'nether', 'overworld', 'channeloff',
    'channelon', 'empty', 'seed', 'revoke', 'grant', 'reconnect', 'rejoin', 'rejoined', 'bridgeoff', 'bridgeon', 'restarted', 'disconnect'],
  denied: ['quiet', 'bridgeoff', 'bridgeon', 'disconnect'],
  absent: ['quiet', 'disconnect']
});
function counters(value) {
  assert(value && typeof value === 'object');
  for (const key of ['connections', 'reads', 'replies', 'restartHints', 'hintBytes', 'editCaptures', 'editCommits', 'editsApplied', 'pending', 'tasks'])
    assert(Number.isSafeInteger(value[key]) && value[key] >= 0, `Invalid counter: ${key}`);
}
export function verifyLifecycleEntry(entry) {
  assert(['fabric', 'neoforge'].includes(entry.loader));
  assert(Object.hasOwn(phases, entry.scenario));
  assert.deepEqual(entry.phases, phases[entry.scenario], 'Incomplete, reordered or duplicated native phases');
  assert.deepEqual(entry.contexts, entry.scenario === 'allowed' ? lifecycleContexts
    : entry.scenario === 'denied' ? ['denied silent restart stays quiet'] : []);
  if (entry.scenario === 'absent') {
    assert.equal(entry.baseline, null); assert.equal(entry.final, null); assert.equal(entry.beforeShutdown, undefined);
    return;
  }
  for (const value of [entry.baseline, entry.beforeShutdown, entry.final]) counters(value);
  assert.equal(entry.baseline.connections, 0);
  assert.equal(entry.baseline.pending, 0); assert.equal(entry.baseline.tasks, 0);
  assert.equal(entry.beforeShutdown.connections, 1);
  assert.equal(entry.beforeShutdown.pending, 0); assert.equal(entry.beforeShutdown.tasks, 0);
  assert.equal(entry.beforeShutdown.replies - entry.baseline.replies, entry.scenario === 'allowed' ? 18 : 1);
  assert.equal(entry.final.connections, 0); assert.equal(entry.final.pending, 0); assert.equal(entry.final.tasks, 0);
  assert.equal(entry.beforeShutdown.reads - entry.baseline.reads, entry.scenario === 'allowed' ? 8 : 0);
  assert.equal(entry.beforeShutdown.editCaptures, entry.scenario === 'allowed' ? 1 : 0);
  assert.equal(entry.beforeShutdown.editCommits, entry.scenario === 'allowed' ? 2 : 0);
  assert.equal(entry.beforeShutdown.editsApplied, entry.scenario === 'allowed' ? 2 : 0);
  assert.equal(entry.final.reads, entry.scenario === 'allowed' ? 1 : 0);
  assert.equal(entry.final.replies, entry.scenario === 'allowed' ? 2 : 0);
  assert.equal(entry.final.restartHints, 1); assert.equal(entry.final.hintBytes, 22);
  assert.equal(entry.final.editCaptures, 0); assert.equal(entry.final.editCommits, 0); assert.equal(entry.final.editsApplied, 0);
}
export function verifyLifecycleMatrix(entries) {
  assert.equal(entries.length, 6);
  for (const entry of entries) verifyLifecycleEntry(entry);
  assert.deepEqual(entries.map(e => `${e.loader}/${e.scenario}`).sort(),
    ['fabric/allowed', 'fabric/denied', 'fabric/absent', 'neoforge/allowed', 'neoforge/denied', 'neoforge/absent'].sort());
}
