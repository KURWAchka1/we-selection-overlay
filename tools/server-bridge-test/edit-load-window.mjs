import assert from 'node:assert/strict';

export function verifyEditLoadWindow(window) {
  const { before, after, actors, durationMillis, ticks, active } = window;
  assert.equal(ticks.samples, 400); assert.equal(ticks.players, 32);
  assert.equal(ticks.tickMillis.length, 400);
  assert(ticks.tickMillis.every(n => Number.isFinite(n) && n >= 0));
  const sorted = [...ticks.tickMillis].sort((a,b) => a-b);
  for (const [key, index] of [['p50',199], ['p95',379], ['p99',395], ['max',399]]) assert.equal(ticks[key], sorted[index], `Corrupt ${key} summary`);
  assert.equal(ticks.generatedChunks, 0, 'Edit fixture generated chunks during measurement');
  for (const value of [durationMillis, ticks.ownerCpuMillis, ticks.ownerAllocatedBytes, ticks.usedHeapBytes]) assert(Number.isFinite(value) && value >= 0);
  assert(durationMillis >= 19000 && durationMillis < 60000);
  assert.equal(after.pending, 0); assert.equal(after.tasks, 0); assert(after.peak <= 28);
  for (const counters of [before,after]) for (const n of Object.values(counters)) assert(Number.isFinite(n) && n >= 0);
  assert.equal(actors.length, 32);
  assert.equal(new Set(actors.map(a => a.name)).size, 32);
  for (const [role, count] of [['editor',8], ['reader',8], ['flood',12], ['silent',4]]) assert.equal(actors.filter(a => a.role === role).length,count);
  for (const actor of actors.filter(a => a.role === 'silent')) assert.equal(actor.frames, 0, 'Non-consumer got bridge data');
  if (!active) {
    for (const key of ['reads', 'captures', 'commits', 'applied', 'replies']) assert.equal(after[key], before[key], `Idle ${key}`);
    for (const actor of actors) for (const key of ['sent','applied','reads']) assert.equal(actor[key],0,`Idle actor ${key}`);
    return;
  }
  const editors = actors.filter(a => a.role === 'editor'); assert.equal(editors.length, 8);
  let applied = 0;
  for (const actor of editors) {
    assert(actor.applied >= 30, 'Editor did not make ordinary progress');
    assert.equal(actor.latencies.length, actor.applied);
    assert(actor.latencies.every(n => Number.isFinite(n) && n >= 0 && n < 5000), 'Expired/stalled editor request');
    applied += actor.applied;
  }
  assert.equal(after.applied - before.applied, applied);
  assert.equal(after.commits - before.commits, applied, 'Mutation replay or hidden failure');
  assert.equal(after.captures, before.captures, 'Canonical acknowledgments caused recapture');
  for (const actor of actors.filter(a => a.role === 'reader')) assert(actor.reads >= 30 && actor.sent === actor.reads);
  for (const actor of actors.filter(a => a.role === 'flood')) assert(actor.sent > 0 && actor.frames > 0, 'Flood actor did not participate');
  assert(after.throttled > before.throttled, 'No actual admission pressure');
}
