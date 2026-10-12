import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdirSync, mkdtempSync, rmSync } from 'node:fs';
import { join, relative, resolve, sep } from 'node:path';
import test from 'node:test';

const root = resolve(import.meta.dirname, '../..');
const java = process.env.WESO_TEST_JAVA;
assert(java, 'WESO_TEST_JAVA must name the explicit java.exe under test');
const fixture = join(root, 'server-bridge/paper-test-fixture/build/libs/weso-paper-test-fixture.jar');
function invoke(args) {
  const result = spawnSync(java, ['-Xmx256m', '-XX:ActiveProcessorCount=2', ...args],
    { encoding: 'utf8', windowsHide: true, timeout: 30000, maxBuffer: 1024 * 1024 });
  assert(!result.error, result.error?.message);
  return result;
}
function recording(t, duplicate = false) {
  const build = join(root, 'build'); mkdirSync(build, { recursive: true });
  const directory = mkdtempSync(join(build, 'tick-probe-'));
  t.after(() => {
    const inside = relative(build, resolve(directory));
    assert(inside.startsWith('tick-probe-') && !inside.includes(sep));
    rmSync(directory, { recursive: true, force: true });
  });
  const path = join(directory, 'probe.jfr');
  const result = invoke(['--class-path', fixture, join(import.meta.dirname, 'JfrTickProbe.java'), path,
    ...(duplicate ? ['duplicate'] : [])]);
  assert.equal(result.status, 0, result.stderr);
  const [start, end] = result.stdout.trim().split(/\r?\n/);
  assert(!Number.isNaN(Date.parse(start)) && !Number.isNaN(Date.parse(end)));
  return { path, start, end };
}
function read({ path, start, end }, phase = 'probe', count = 2) {
  return invoke([join(import.meta.dirname, 'JfrTickTimeline.java'), path, phase, String(count), start, end]);
}

test('actual fixture event records survive round trip and GC overlap is not concurrent collection duration', t => {
  const result = read(recording(t)); assert.equal(result.status, 0, result.stderr);
  const ticks = result.stdout.trim().split(/\r?\n/).map(line => JSON.parse(line));
  assert.equal(ticks.length, 2);
  for (const tick of ticks) {
    assert.equal(tick.markerCount, 2); assert.equal(tick.window, 0);
    assert(tick.paperMinusBracketMillis >= 39 && tick.paperMinusBracketMillis < 60);
    assert(tick.bracketCpuMillis >= 0 && tick.bracketAllocatedBytes >= 0);
    assert(tick.inferredGcPauseMillis >= 0 && tick.inferredGcPauseMillis <= tick.paperMillis);
  }
  assert(ticks.some(tick => tick.inferredGcPauseMillis > 0), 'The explicit full-GC pause was not correlated');
});

test('missing markers or an incorrect phase cannot publish a complete timeline', t => {
  const source = recording(t);
  for (const [phase, count] of [['probe', 3], ['wrong', 2]]) {
    const result = read(source, phase, count);
    assert.notEqual(result.status, 0); assert.match(result.stderr, /Missing tick markers/);
    assert.equal(result.stdout, '');
  }
});

test('duplicate sample markers fail before reporting outliers', t => {
  const result = read(recording(t, true));
  assert.notEqual(result.status, 0); assert.match(result.stderr, /Invalid or duplicate tick marker/);
  assert.equal(result.stdout, '');
});

test('phase/sample identity is preserved when wall-clock boundaries clip a marker', t => {
  const source = recording(t);
  source.end = new Date(Date.parse(source.start) + 1).toISOString();
  const result = read(source); assert.equal(result.status, 0, result.stderr);
  const ticks = result.stdout.trim().split(/\r?\n/).map(line => JSON.parse(line));
  assert.equal(ticks.length, 2);
  assert(ticks.every(tick => tick.markerCount === 2 && tick.phaseWallEndSkewMillis > 0));
});
