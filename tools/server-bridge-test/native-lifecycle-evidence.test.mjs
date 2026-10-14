import assert from 'node:assert/strict';
import test from 'node:test';
import { lifecycleContexts, verifyLifecycleEntry, verifyLifecycleMatrix } from './native-lifecycle-evidence.mjs';

const zero = () => ({ connections: 0, reads: 0, replies: 0, restartHints: 0, hintBytes: 0,
  editCaptures: 0, editCommits: 0, editsApplied: 0, pending: 0, tasks: 0 });
function entry(scenario = 'allowed', loader = 'fabric') {
  return { loader, scenario, baseline: scenario === 'absent' ? null : zero(),
    beforeShutdown: scenario === 'absent' ? undefined : { ...zero(), connections: 1, replies: scenario === 'allowed' ? 18 : 1, reads: scenario === 'allowed' ? 8 : 0,
      editCaptures: scenario === 'allowed' ? 1 : 0, editCommits: scenario === 'allowed' ? 2 : 0, editsApplied: scenario === 'allowed' ? 2 : 0 },
    final: scenario === 'absent' ? null : { ...zero(), reads: scenario === 'allowed' ? 1 : 0,
      replies: scenario === 'allowed' ? 2 : 0, restartHints: 1, hintBytes: 22 },
    contexts: scenario === 'allowed' ? [...lifecycleContexts] : scenario === 'denied' ? ['denied silent restart stays quiet'] : [],
    phases: scenario === 'allowed' ? ['initial', 'editsetup', 'editquiet', 'editcancel', 'respawn', 'nether', 'overworld',
      'channeloff', 'channelon', 'empty', 'seed', 'revoke', 'grant', 'reconnect', 'rejoin', 'rejoined', 'bridgeoff', 'bridgeon', 'restarted', 'disconnect']
      : scenario === 'denied' ? ['quiet', 'bridgeoff', 'bridgeon', 'disconnect'] : ['quiet', 'disconnect'] };
}
test('accepts complete native negative/lifecycle matrix', () => {
  verifyLifecycleMatrix(['fabric', 'neoforge'].flatMap(loader => ['allowed', 'denied', 'absent'].map(s => entry(s, loader))));
});
test('rejects missing or duplicated lifecycle contexts and phases', () => {
  for (const key of ['contexts', 'phases']) {
    const e = entry(); e[key].pop(); assert.throws(() => verifyLifecycleEntry(e));
    const duplicate = entry(); duplicate[key][1] = duplicate[key][0]; assert.throws(() => verifyLifecycleEntry(duplicate));
  }
});
test('rejects unauthorized metadata or edit work', () => {
  for (const key of ['reads', 'editCaptures', 'editCommits', 'editsApplied']) {
    const e = entry('denied'); e.beforeShutdown[key] = 1; assert.throws(() => verifyLifecycleEntry(e));
  }
});
test('rejects mutation retries, recapture and missing canonical applies', () => {
  for (const key of ['editCaptures', 'editCommits', 'editsApplied']) {
    for (const delta of [-1, 1]) { const e = entry(); e.beforeShutdown[key] += delta; assert.throws(() => verifyLifecycleEntry(e)); }
  }
});
test('rejects retained connections/tasks and unsolicited restart reads', () => {
  for (const key of ['connections', 'pending', 'tasks', 'replies']) {
    const e = entry(); e.beforeShutdown[key]++; assert.throws(() => verifyLifecycleEntry(e));
  }
  for (const key of ['connections', 'pending', 'tasks', 'reads', 'replies', 'restartHints', 'hintBytes', 'editCaptures', 'editCommits', 'editsApplied']) {
    const e = entry(); e.final[key]++; assert.throws(() => verifyLifecycleEntry(e));
  }
});
test('rejects nonfinite, absent or fractional counters', () => {
  for (const value of [NaN, Infinity, -1, undefined, 0.5]) {
    const e = entry(); e.final.pending = value; assert.throws(() => verifyLifecycleEntry(e));
  }
});
test('rejects incomplete matrix, invented loader or duplicate scenario', () => {
  assert.throws(() => verifyLifecycleMatrix([entry()]));
  assert.throws(() => verifyLifecycleMatrix(Array.from({ length: 6 }, () => entry())));
  const e = entry(); e.loader = 'unknown'; assert.throws(() => verifyLifecycleEntry(e));
});
