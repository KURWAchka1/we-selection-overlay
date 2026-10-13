import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdirSync, mkdtempSync, rmSync } from 'node:fs';
import { join, relative, resolve, sep } from 'node:path';
import test from 'node:test';

const java = process.env.WESO_TEST_JAVA;
assert(java, 'WESO_TEST_JAVA must name the explicit test JVM');
const build = resolve(import.meta.dirname, '../../build');
function inspect(t, loss = false) {
  mkdirSync(build, { recursive: true });
  const directory = mkdtempSync(join(build, 'summary-probe-'));
  t.after(() => {
    const inside = relative(build, resolve(directory));
    assert(inside.startsWith('summary-probe-') && !inside.includes(sep));
    rmSync(directory, { recursive: true, force: true });
  });
  const path = join(directory, 'probe.jfr');
  const invoke = args => spawnSync(java, ['-Xmx256m', '-XX:ActiveProcessorCount=2', ...args],
    { encoding: 'utf8', windowsHide: true, timeout: 30000 });
  const generated = invoke([join(import.meta.dirname, 'JfrProfilerControlProbe.java'), path, ...(loss ? ['loss'] : [])]);
  assert.equal(generated.status, 0, generated.stderr || generated.error?.message);
  return invoke([join(import.meta.dirname, 'JfrLoadSummary.java'), path, ...generated.stdout.trim().split(/\r?\n/)]);
}
test('actual thread-dump operation is detected instead of trusting profiler configuration', t => {
  const result = inspect(t); assert.equal(result.status, 0, result.stderr || result.error?.message);
  const data = JSON.parse(result.stdout);
  assert.equal(data.window, 0); assert(data.recordingSparkThreadDumps > 0);
});
test('recorded lost data prevents any cost summary publication', t => {
  const result = inspect(t, true);
  assert.notEqual(result.status, 0); assert.equal(result.stdout, '');
  assert.match(result.stderr, /lost JFR data/);
});
