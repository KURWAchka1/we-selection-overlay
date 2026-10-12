import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { readFileSync, writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';

const root = resolve(import.meta.dirname, '../..');
const mode = process.argv[2] ?? 'load';
assert(['load', 'baseline'].includes(mode));
const directory = join(root, 'build', mode === 'baseline' ? 'bridge-baseline-proof' : 'bridge-load-proof');
const proof = JSON.parse(readFileSync(join(directory, 'load-proof.json'), 'utf8'));
assert.equal(proof.mode ?? 'load', mode);
const runs = [...new Set(proof.results.map(result => result.run))];
const profiles = [];
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
}
const rows = proof.results.map(row => ({ run: row.run, phase: row.label,
  p50: row.p50, p95: row.p95, p99: row.p99, max: row.max,
  ownerCpuMillisPerSecond: row.ownerCpuMillis / (row.wallMillis / 1000),
  ownerMiBPerSecond: row.ownerAllocatedBytes / 1024 / 1024 / (row.wallMillis / 1000),
  reads: row.bridge?.reads ?? 0, throttled: row.bridge?.throttled ?? 0, replyBytes: row.bridge?.bytes ?? 0,
  chunkLoads: row.chunkLoads, generatedChunks: row.generatedChunks,
  gcCount: row.gcCount, gcMillis: row.gcMillis }));
const report = { checkedAt: proof.checkedAt, mode, worldDigest: proof.worldDigest, rows, profiles,
  warning: 'Local stationary headless workloads on a shared laptop. Sampled allocation weights are estimates, not exact call costs. Frozen baseline worlds reduce confounds but do not establish production performance acceptance or native-client lifecycle behavior.' };
writeFileSync(join(directory, 'load-analysis.json'), JSON.stringify(report, null, 2));
console.table(rows);
console.table(profiles.map(({ run, label, executionSamples, ownerSamples, bridgeSamples, bridgeAllocationSamples }) =>
  ({ run, label, executionSamples, ownerSamples, bridgeSamples, bridgeAllocationSamples })));
