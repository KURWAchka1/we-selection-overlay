import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { readFileSync, writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { validateActiveCycle } from './active-lifecycle.mjs';

assert(process.argv.slice(2).length <= 2 && process.argv.slice(2).every(arg => ['--slice-diagnostics', '--without-spark'].includes(arg)));
const sliceDiagnostics = process.argv.includes('--slice-diagnostics');
const withoutSpark = process.argv.includes('--without-spark');
assert(!withoutSpark || sliceDiagnostics);
const directory = resolve(import.meta.dirname, '../../build', withoutSpark ? 'bridge-slice-diagnostics-no-spark' : sliceDiagnostics ? 'bridge-slice-diagnostics' : 'bridge-active-lifecycle-proof');
const proof = JSON.parse(readFileSync(join(directory, 'lifecycle-proof.json'), 'utf8'));
assert.equal(!!proof.sliceDiagnostics, sliceDiagnostics);
assert.equal(!!proof.withoutSpark, withoutSpark);
assert.equal(proof.results.length, proof.repetitions);
const rows = [], profiles = [], tickOutliers = [];
const sliceProfiles = [];
for (const result of proof.results) {
  assert.equal(result.validated, true); validateActiveCycle(result, proof.samples);
  const windows = result.windows, run = `round-${result.round}`;
  for (const window of windows) {
    const saved = JSON.parse(readFileSync(join(directory, 'measurements', run, `${window.label}.json`), 'utf8'));
    assert.equal(saved.validated, true);
    const { validated, ...raw } = saved;
    assert.deepEqual(raw, window, 'Proof differs from retained measurement');
    rows.push({ run, label: window.label, p50: window.p50, p95: window.p95, p99: window.p99, max: window.max,
      ticksOver50Millis: window.tickMillis.filter(n => n > 50).length,
      ordinaryReplies: window.received[0], ordinaryMaxMillis: window.ordinaryReplyLatencies.maxMillis,
      moves: window.gameplay.acceptedMoves.reduce((a, b) => a + b, 0),
      changedBlocks: window.gameplay.changedBlocks, chunkLoads: window.chunkLoads,
      ownerCpuMillisPerSecond: window.ownerCpuMillis / (window.wallMillis / 1000),
      ownerMiBPerSecond: window.ownerAllocatedBytes / 1024 / 1024 / (window.wallMillis / 1000) });
  }
  for (const [reader, args, target] of [
    ['JfrLoadSummary.java', windows.flatMap(w => [w.startedAt, w.endedAt]), profiles],
    ['JfrTickTimeline.java', windows.flatMap(w => [w.label, String(w.samples), w.startedAt, w.endedAt]), tickOutliers]
  ]) {
    const execution = spawnSync(join(proof.javaHome, 'bin/java.exe'), ['-Xmx256m', '-XX:ActiveProcessorCount=2',
      join(import.meta.dirname, reader), join(directory, `${run}.jfr`), ...args],
    { encoding: 'utf8', windowsHide: true, timeout: 120000, maxBuffer: 1024 * 1024 });
    assert.equal(execution.status, 0, execution.stderr || execution.error?.message);
    const data = execution.stdout.trim().split(/\r?\n/).map(line => JSON.parse(line));
    assert.equal(data.length, windows.length * (reader === 'JfrTickTimeline.java' ? 5 : 1));
    for (const item of data) {
      assert(Number.isInteger(item.window) && item.window >= 0 && item.window < windows.length);
      if (reader === 'JfrTickTimeline.java') assert.equal(item.markerCount, proof.samples);
      target.push({ run, label: windows[item.window].label, ...item });
    }
  }
  if (sliceDiagnostics) {
    const execution = spawnSync(join(proof.javaHome, 'bin/java.exe'), ['-Xmx256m', '-XX:ActiveProcessorCount=2',
      join(import.meta.dirname, 'JfrBridgeSlices.java'), join(directory, `${run}.jfr`), 'production'],
    { encoding: 'utf8', windowsHide: true, timeout: 120000, maxBuffer: 1024 * 1024 });
    assert.equal(execution.status, 0, execution.stderr || execution.error?.message);
    const [totals, ...intervals] = execution.stdout.trim().split(/\r?\n/).map(line => JSON.parse(line));
    const sparkDumps = totals.threadDumpCallers.filter(c => c.name.startsWith('spark-java-sampler-')).reduce((n, c) => n + c.count, 0);
    if (withoutSpark) assert.equal(sparkDumps, 0, 'Spark still sampled the control JVM');
    else assert(sparkDumps > 0, 'Default-profiler diagnostic lacks observed Java sampler work');
    const byOperation = Object.fromEntries(totals.groups.map(g => [g.operation, g]));
    // Each graceful stop snapshots the complete lifetime counters of its core.
    // No source work is admitted between that same-thread snapshot and shutdown.
    const closedCores = result.faults.filter(f => ['bridgeoff', 'stopwe'].includes(f.action));
    assert.equal(byOperation.process.count, closedCores.reduce((n, f) => n + f.before.metrics.admitted, 0), 'Lost or extra recorded dispatch work');
    assert.equal(byOperation.read.count, closedCores.reduce((n, f) => n + f.before.metrics.selectionReads, 0), 'Lost or extra recorded source reads');
    assert.equal(byOperation.send.count, closedCores.reduce((n, f) => n + f.after.metrics.replies, 0), 'Lost or extra recorded sends');
    assert.equal(byOperation.enqueue.count, closedCores.reduce((n, f) => n + f.before.queue.queued + f.before.metrics.throttled, 0), 'Lost or extra recorded ingress');
    assert(Math.abs(totals.coreMaxMillis - Math.max(...closedCores.map(f => f.before.queue.maxSliceNanos)) / 1e6) < .000001,
      'Cumulative dispatcher maximum lacks a diagnostic interval');
    for (const interval of intervals) {
      interval.window = result.windows.find(w => Date.parse(interval.start) >= Date.parse(w.startedAt)
        && Date.parse(interval.end) <= Date.parse(w.endedAt))?.label ?? 'outside-measured-windows';
    }
    sliceProfiles.push({ run, totals, intervals });
  }
}
const report = { checkedAt: proof.checkedAt, hashes: proof.hashes, rows, profiles, tickOutliers, sliceProfiles,
  measuredTicks: proof.samples * rows.length, changedBlocks: rows.reduce((n, row) => n + row.changedBlocks, 0),
  acceptedMoves: rows.reduce((n, row) => n + row.moves, 0),
  recoveries: proof.results.flatMap(result => result.recoveries.map(recovery => ({ round: result.round, ...recovery }))),
  faults: proof.results.flatMap(result => result.faults.map(fault => ({ round: result.round, action: fault.action,
    queuedBefore: fault.before.queue.pending, cancelledByFault: fault.after.queue.cancelled - fault.before.queue.cancelled,
    maxWaitNanos: fault.before.queue.maxWaitNanos, maxSliceNanos: fault.before.queue.maxSliceNanos }))),
  warning: proof.limitations };
writeFileSync(join(directory, 'lifecycle-analysis.json'), JSON.stringify(report, null, 2));
console.table(rows);
console.table(report.recoveries);
console.table(report.faults);
for (const profile of sliceProfiles) {
  console.log(profile.run); console.table(profile.totals.groups);
  console.table(profile.intervals.map(({ id, window, wallMillis, coreMaxAfterMillis, gcOverlapMillis, vmOverlapMillis, safepointBeginOverlapMillis }) =>
    ({ id, window, wallMillis, coreMaxAfterMillis, gcOverlapMillis, vmOverlapMillis, safepointBeginOverlapMillis })));
}
console.log(`Validated ${report.measuredTicks} tick markers, ${report.changedBlocks} changed cells and ${report.acceptedMoves} accepted movements.`);
