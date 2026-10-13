import assert from 'node:assert/strict';
import test from 'node:test';
import { verifyEditLoadWindow } from './edit-load-window.mjs';

function fixture(active = true) {
  const actors = Array.from({ length: 32 }, (_, i) => ({ name: `WesoLoad${i}`, role: i < 8 ? 'editor' : i < 16 ? 'reader' : i < 28 ? 'flood' : 'silent',
    applied: active && i < 8 ? 40 : 0, latencies: active && i < 8 ? Array(40).fill(150) : [], reads: active && i >= 8 && i < 16 ? 40 : 0,
    sent: active && i < 28 ? 40 : 0, frames: i >= 28 ? 0 : 40 }));
  const before = { reads: 0, captures: 8, commits: 0, applied: 0, replies: 0, throttled: 0 };
  return { active, before, after: { ...before, reads: active ? 320 : 0, commits: active ? 320 : 0, applied: active ? 320 : 0,
    replies: active ? 640 : 0, throttled: active ? 50 : 0, pending: 0, tasks: 0, peak: 28 }, actors, durationMillis: 20000,
    ticks: { samples: 400, players: 32, tickMillis: Array(400).fill(2), p50:2, p95:2, p99:2, max:2, generatedChunks: 0, ownerCpuMillis: 500, ownerAllocatedBytes: 500000, usedHeapBytes: 10000000 } };
}
test('valid parallel editing and idle counters pass independently', () => { verifyEditLoadWindow(fixture()); verifyEditLoadWindow(fixture(false)); });
test('duplicate mutation, recapture and hidden server failure are rejected', () => {
  for (const key of ['commits', 'applied', 'captures']) { const w = fixture(); w.after[key]++; assert.throws(() => verifyEditLoadWindow(w)); }
});
test('starvation, late replies and non-consumer disclosure cannot pass', () => {
  for (const change of [w => w.actors[0].applied = 0, w => w.actors[0].latencies[0] = 5000,
    w => w.actors[8].reads = 0, w => w.actors[31].frames = 1]) { const w = fixture(); change(w); assert.throws(() => verifyEditLoadWindow(w)); }
});
test('idle work, chunk generation, missing samples and leaking tasks fail', () => {
  for (const change of [w => w.after.captures++, w => w.ticks.generatedChunks++, w => w.ticks.tickMillis.pop(), w => w.after.tasks++]) {
    const w = fixture(false); change(w); assert.throws(() => verifyEditLoadWindow(w));
  }
});
test('statistics are recomputed from raw ticks and invalid counters are rejected', () => {
  for (const change of [w => w.ticks.p95=3, w => w.ticks.max=0, w => w.after.commits=NaN, w => w.after.peak=-1]) {
    const w=fixture(); change(w); assert.throws(() => verifyEditLoadWindow(w));
  }
});
test('missing roles, identity collisions, fake flood and idle traffic are rejected', () => {
  for (const change of [w => w.actors[20].role='other', w => w.actors[1].name=w.actors[0].name, w => w.actors[20].sent=0]) {
    const w=fixture(); change(w); assert.throws(() => verifyEditLoadWindow(w));
  }
  const w=fixture(false); w.actors[8].sent=1; assert.throws(() => verifyEditLoadWindow(w));
});
