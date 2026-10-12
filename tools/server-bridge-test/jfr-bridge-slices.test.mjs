import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdirSync, mkdtempSync, rmSync } from 'node:fs';
import { join, relative, resolve, sep } from 'node:path';
import test from 'node:test';

const root = resolve(import.meta.dirname, '../..');
const java = process.env.WESO_TEST_JAVA;
assert(java, 'WESO_TEST_JAVA must name the explicit test JVM');
const recording = join(root, 'tools/bridge-slice-agent/build/probe.jfr');
function read(mode = 'probe', path = recording) {
  return spawnSync(java, ['-Xmx256m', '-XX:ActiveProcessorCount=2',
    join(import.meta.dirname, 'JfrBridgeSlices.java'), path, mode],
  { encoding: 'utf8', windowsHide: true, timeout: 30000, maxBuffer: 1024 * 1024 });
}

test('real diagnostic recording preserves nested calls, failures and pause overlap', () => {
  const result = read(); assert.equal(result.status, 0, result.stderr || result.error?.message);
  const [totals, ...intervals] = result.stdout.trim().split(/\r?\n/).map(line => JSON.parse(line));
  assert.equal(totals.calls, 6); assert.equal(intervals.length, 6);
  assert.equal(totals.groups.reduce((n, g) => n + g.failed, 0), 1);
  assert.equal(totals.groups.find(g => g.operation === 'value').count, 2);
  const nested = intervals.find(i => i.operation === 'nested');
  assert.equal(nested.children.length, 1); assert.equal(nested.children[0].parentId, nested.id);
  const pause = intervals.find(i => i.operation === 'pause');
  assert(pause.parkOverlapMillis > 0 && pause.gcOverlapMillis > 0);
  assert(pause.parkOverlapMillis <= pause.wallMillis + .1);
  assert(pause.gcOverlapMillis <= pause.wallMillis + .1);
  // GC and VM-operation intervals can overlap; they must not be added as disjoint costs.
  assert(totals.groups.every(g => g.count > 0 && g.p95Millis <= g.maxMillis && g.p99Millis <= g.maxMillis));
});

test('probe-only, missing and invalid recordings cannot publish production evidence', () => {
  for (const result of [read('production'), read('wrong'), read('probe', recording + '.missing')]) {
    assert.notEqual(result.status, 0); assert.equal(result.stdout, '');
  }
});

test('corrupt recorded fields fail closed before any summary is printed', t => {
  const build = join(root, 'build'); mkdirSync(build, { recursive: true });
  const directory = mkdtempSync(join(build, 'slice-probe-'));
  t.after(() => {
    const inside = relative(build, resolve(directory));
    assert(inside.startsWith('slice-probe-') && !inside.includes(sep));
    rmSync(directory, { recursive: true, force: true });
  });
  for (const kind of ['operation', 'id', 'parent', 'wall', 'cpu', 'allocation', 'mark', 'backwards']) {
    const path = join(directory, `${kind}.jfr`);
    const generated = spawnSync(java, ['-Xmx256m', '-XX:ActiveProcessorCount=2', '--class-path',
      join(root, 'tools/bridge-slice-agent/build/libs/bridge-slice-bootstrap.jar'),
      join(import.meta.dirname, 'JfrSliceCorruptionProbe.java'), path, kind],
    { encoding: 'utf8', windowsHide: true, timeout: 30000 });
    assert.equal(generated.status, 0, generated.stderr || generated.error?.message);
    const result = read('probe', path);
    assert.notEqual(result.status, 0, `${kind} accepted`); assert.equal(result.stdout, '');
    assert.match(result.stderr, /Invalid diagnostic (call|high-water mark)/);
  }
});
