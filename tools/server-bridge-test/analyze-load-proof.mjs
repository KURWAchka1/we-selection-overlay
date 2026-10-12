import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { readFileSync, writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';

const root = resolve(import.meta.dirname, '../..');
const mode = process.argv[2] ?? 'load';
assert(['load', 'baseline', 'restart', 'timeline', 'fair', 'gameplay'].includes(mode));
const directory = join(root, 'build', { load: 'bridge-load-proof', baseline: 'bridge-baseline-proof', restart: 'bridge-restart-load-proof', timeline: 'bridge-tick-timeline-proof', fair: 'bridge-fair-dispatch-proof', gameplay: 'bridge-gameplay-proof' }[mode]);
const proof = JSON.parse(readFileSync(join(directory, 'load-proof.json'), 'utf8'));
assert.equal(proof.mode ?? 'load', mode);
const runs = [...new Set(proof.results.map(result => result.run))];
const profiles = [];
const tickOutliers = [];
for (const run of runs) {
  const windows = proof.results.filter(result => result.run === run);
  const args = ['-Xmx256m', '-XX:ActiveProcessorCount=2', join(import.meta.dirname, 'JfrLoadSummary.java'),
    join(directory, `${run}.jfr`), ...windows.flatMap(w => [w.startedAt, w.endedAt])];
  const result = spawnSync(join(proof.javaHome, 'bin/java.exe'), args,
    { encoding: 'utf8', windowsHide: true, timeout: 120000, maxBuffer: 1024 * 1024 });
  assert.equal(result.status, 0, result.stderr || result.error?.message);
  const data = result.stdout.trim().split(/\r?\n/).map(line => JSON.parse(line));
  assert.equal(data.length, windows.length);
  for (const window of data) {
    assert(Number.isInteger(window.window) && window.window >= 0 && window.window < windows.length);
    profiles.push({ run, label: windows[window.window].label, ...window });
  }
  if (mode === 'timeline' || mode === 'fair' || mode === 'gameplay') {
    assert(windows.every(w => w.tickTimelineVersion === 1));
    const result = spawnSync(join(proof.javaHome, 'bin/java.exe'), ['-Xmx256m', '-XX:ActiveProcessorCount=2',
      join(import.meta.dirname, 'JfrTickTimeline.java'), join(directory, `${run}.jfr`),
      ...windows.flatMap(w => [w.label, String(w.samples), w.startedAt, w.endedAt])],
    { encoding: 'utf8', windowsHide: true, timeout: 120000, maxBuffer: 1024 * 1024 });
    assert.equal(result.status, 0, result.stderr || result.error?.message);
    const ticks = result.stdout.trim().split(/\r?\n/).map(line => JSON.parse(line));
    assert.equal(ticks.length, windows.reduce((n, w) => n + Math.min(5, w.samples), 0));
    for (const tick of ticks) {
      assert(Number.isInteger(tick.window) && tick.window >= 0 && tick.window < windows.length);
      assert.equal(tick.markerCount, windows[tick.window].samples);
      tickOutliers.push({ run, label: windows[tick.window].label, ...tick });
    }
  }
}
const rows = proof.results.map(row => ({ run: row.run, phase: row.label,
  p50: row.p50, p95: row.p95, p99: row.p99, max: row.max,
  ownerCpuMillisPerSecond: row.ownerCpuMillis / (row.wallMillis / 1000),
  ownerMiBPerSecond: row.ownerAllocatedBytes / 1024 / 1024 / (row.wallMillis / 1000),
  reads: row.bridge?.reads ?? 0, throttled: row.bridge?.throttled ?? 0, replyBytes: row.bridge?.bytes ?? 0,
  chunkLoads: row.chunkLoads, generatedChunks: row.generatedChunks,
  gcCount: row.gcCount, gcMillis: row.gcMillis,
  ...(row.gameplay ? { gameplay: row.gameplay } : {}),
  ...(row.queueAfter ? { queueAfter: row.queueAfter } : {}),
  ...(row.ordinaryReplyLatencies ? { ordinaryReplyLatencies: row.ordinaryReplyLatencies } : {}) }));
const matchedComparisons = [];
if (mode === 'gameplay') {
  for (const row of rows) {
    const raw = JSON.parse(readFileSync(join(directory, 'measurements', row.run, `${row.phase}.json`), 'utf8'));
    assert.equal(raw.validated, true); assert.equal(raw.samples, proof.samples);
    row.ticksOver50Millis = raw.tickMillis.filter(n => n > 50).length;
  }
  for (let round = 1; round <= proof.repetitions; round++) {
    const absent = rows.filter(row => row.run === `${round}-absent`);
    const installed = rows.filter(row => row.run === `${round}-installed`);
    assert.equal(absent.length, 6); assert.equal(installed.length, 6);
    for (const base of absent) {
      const candidate = installed.find(row => row.phase === base.phase);
      assert(candidate, 'Missing matched gameplay phase');
      for (const key of ['ticks', 'editBatches', 'changedBlocks', 'entities'])
        assert.equal(candidate.gameplay[key], base.gameplay[key], `Unequal world workload: ${key}`);
      matchedComparisons.push({ round, phase: base.phase,
        deltaP95Millis: candidate.p95 - base.p95, deltaP99Millis: candidate.p99 - base.p99,
        deltaOwnerCpuMillisPerSecond: candidate.ownerCpuMillisPerSecond - base.ownerCpuMillisPerSecond,
        deltaOwnerMiBPerSecond: candidate.ownerMiBPerSecond - base.ownerMiBPerSecond,
        absentTicksOver50Millis: base.ticksOver50Millis, installedTicksOver50Millis: candidate.ticksOver50Millis });
    }
  }
}
const report = { checkedAt: proof.checkedAt, mode, worldDigest: proof.worldDigest, rows, profiles, tickOutliers,
  matchedComparisons,
  warning: 'Local bounded headless workloads on a shared laptop. Sampled allocation weights are estimates, not exact call costs. Frozen starting worlds reduce confounds but do not establish production performance acceptance or native-client lifecycle behavior.' };
writeFileSync(join(directory, 'load-analysis.json'), JSON.stringify(report, null, 2));
console.table(rows);
console.table(profiles.map(({ run, label, executionSamples, ownerSamples, bridgeSamples, bridgeAllocationSamples }) =>
  ({ run, label, executionSamples, ownerSamples, bridgeSamples, bridgeAllocationSamples })));
if (tickOutliers.length) console.table(tickOutliers.map(({ run, sample, paperMillis, bracketMillis,
  paperMinusBracketMillis, bracketCpuMillis, inferredGcPauseMillis, inferredBridgeSamples }) =>
  ({ run, sample, paperMillis, bracketMillis, paperMinusBracketMillis, bracketCpuMillis, inferredGcPauseMillis, inferredBridgeSamples })));
