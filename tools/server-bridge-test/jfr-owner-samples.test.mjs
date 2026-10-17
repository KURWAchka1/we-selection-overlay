import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdirSync, mkdtempSync, rmSync } from 'node:fs';
import { join, relative, resolve, sep } from 'node:path';
import test from 'node:test';

const root = resolve(import.meta.dirname, '../..'), java = process.env.WESO_TEST_JAVA;
assert(java, 'Explicit WESO_TEST_JAVA required');
function invoke(args) {
  return spawnSync(java, ['-Xmx256m', '-XX:ActiveProcessorCount=2', ...args],
    { encoding: 'utf8', windowsHide: true, timeout: 30000, maxBuffer: 1024 * 1024 });
}
function read(t, kind) {
  const build = join(root, 'build'); mkdirSync(build, { recursive: true });
  const directory = mkdtempSync(join(build, 'owner-sample-probe-'));
  t.after(() => {
    const inside = relative(build, resolve(directory));
    assert(inside.startsWith('owner-sample-probe-') && !inside.includes(sep));
    rmSync(directory, { recursive: true, force: true });
  });
  const path = join(directory, 'probe.jfr');
  const generated = invoke(['-XX:FlightRecorderOptions=stackdepth=32', '--class-path',
    join(root, 'tools/bridge-slice-agent/build/libs/bridge-slice-bootstrap.jar'),
    join(import.meta.dirname, 'JfrOwnerSampleProbe.java'), path, kind]);
  assert.equal(generated.status, 0, generated.stderr || generated.error?.message);
  return invoke([join(import.meta.dirname, 'JfrBridgeSlices.java'), path, 'probe']);
}
function interval(result) {
  assert.equal(result.status, 0, result.stderr || result.error?.message);
  const [totals, ...intervals] = result.stdout.trim().split(/\r?\n/).map(line => JSON.parse(line));
  assert.equal(totals.calls, 1); assert.equal(intervals.length, 1);
  return intervals[0];
}

test('owner samples use sampledThread, not event committer, and exclude times outside the root', t => {
  const row = interval(read(t, 'identity'));
  assert.equal(row.ownerSamples, 3);
  assert.equal(row.ownerSampleDetailsClipped, false);
  assert.deepEqual(row.ownerSampleDetails.map(s => s.state),
    ['STATE_RUNNABLE', 'STATE_FROM_OTHER_COMMITTER', 'STATE_"\\\n\t\u0001']);
  for (const sample of row.ownerSampleDetails) {
    assert(sample.time >= row.start && sample.time < row.end);
  }
  const present = row.ownerSampleDetails.filter(s => s.stackPresent);
  assert.equal(present.length, 2);
  assert(present.every(s => !s.framesClipped && !s.recordingTruncated && !s.stateClipped));
  assert(present.every(s => s.frames.some(f => f.location.includes('JfrOwnerSampleProbe.sample'))));
  const missing = row.ownerSampleDetails.find(s => !s.stackPresent);
  assert.deepEqual(missing.frames, []);
  assert.equal(missing.recordedFrameCount, 0);
  assert.equal(missing.recordingTruncated, false);
});

test('sample and frame bounds stay explicit without losing total owner-sample counts', t => {
  const row = interval(read(t, 'many'));
  assert.equal(row.ownerSamples, 12);
  assert.equal(row.ownerSampleDetails.length, 8);
  assert.equal(row.ownerSampleDetailsClipped, true);
  assert.deepEqual(row.ownerSampleDetails.map(s => s.state), Array.from({ length: 8 }, (_, i) => `sample-${i}`));
  for (const sample of row.ownerSampleDetails) {
    assert.equal(sample.stackPresent, true);
    assert.equal(sample.recordingTruncated, true);
    assert.equal(sample.recordedFrameCount, 32);
    assert.equal(sample.frames.length, 24);
    assert.equal(sample.framesClipped, true);
    assert(sample.frames.every(f => typeof f.location === 'string' && Number.isInteger(f.line)
      && typeof f.type === 'string' && f.locationClipped === false));
  }
});

test('earliest bounded points remain chronological across different committer buffers', t => {
  const row = interval(read(t, 'buffers'));
  assert.equal(row.ownerSamples, 12);
  assert.equal(row.ownerSampleDetailsClipped, true);
  assert.deepEqual(row.ownerSampleDetails.map(s => s.state),
    ['early-0', 'early-1', 'early-2', 'early-3', 'late-0', 'late-1', 'late-2', 'late-3']);
});

test('long or absent state labels remain bounded and unambiguous JSON', t => {
  const row = interval(read(t, 'labels'));
  assert.equal(row.ownerSamples, 2);
  assert.equal(row.ownerSampleDetails[0].state, 'x'.repeat(128));
  assert.equal(row.ownerSampleDetails[0].stateClipped, true);
  assert.equal(row.ownerSampleDetails[1].state, null);
  assert.equal(row.ownerSampleDetails[1].stateClipped, false);
});

test('absence of an owner sample is explicit, not evidence of no work', t => {
  const row = interval(read(t, 'empty'));
  assert.equal(row.ownerSamples, 0);
  assert.deepEqual(row.ownerSampleDetails, []);
  assert.equal(row.ownerSampleDetailsClipped, false);
});

test('recorded data loss still prevents publication of even a partial summary', t => {
  const result = read(t, 'loss');
  assert.notEqual(result.status, 0);
  assert.equal(result.stdout, '');
  assert.match(result.stderr, /lost JFR data/);
});
