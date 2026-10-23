import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { randomBytes } from 'node:crypto';
import { mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { dirname, join, relative, resolve, sep } from 'node:path';
import test from 'node:test';
import { ownedWireRunDirectory, requireUnusedWireEvidence } from './wire-run-directory.mjs';

const root = resolve(import.meta.dirname, '../..');

test('wire runs accept only the original fixture or a bounded unique run below it', () => {
  for (const parts of [[], ['20261023-120000-a12bc345']]) {
    const directory = join(root, 'build/bridge-paper-proof', ...parts);
    assert.equal(ownedWireRunDirectory(root, directory), directory);
  }
  for (const parts of [['build', 'other-server'], ['normal-world'], ['build', 'bridge-paper-proof-old'],
    ['build', 'bridge-paper-proof', '..'], ['build', 'bridge-paper-proof', 'unbounded'],
    ['build', 'bridge-paper-proof', '20261023-120000-a12bc345', 'world']])
    assert.throws(() => ownedWireRunDirectory(root, join(root, ...parts)));
  assert.throws(() => ownedWireRunDirectory(root, join(root, '..', 'build/bridge-paper-proof')));
});

test('successful and failed wire-run evidence is preserved byte for byte', t => {
  const build = join(root, 'build'); mkdirSync(build, { recursive: true });
  const directory = mkdtempSync(join(build, 'wire-directory-check-'));
  t.after(() => {
    const inside = relative(build, resolve(directory));
    assert(inside.startsWith('wire-directory-check-') && !inside.includes(sep));
    rmSync(directory, { recursive: true, force: true });
  });
  requireUnusedWireEvidence(directory);
  writeFileSync(join(directory, 'wire-server.log'), 'failed or successful prior run');
  assert.throws(() => requireUnusedWireEvidence(directory));
  assert.equal(readFileSync(join(directory, 'wire-server.log'), 'utf8'), 'failed or successful prior run');
  writeFileSync(join(directory, 'wire-proof.json'), '{"previous":true}');
  assert.throws(() => requireUnusedWireEvidence(directory));
  assert.equal(readFileSync(join(directory, 'wire-proof.json'), 'utf8'), '{"previous":true}');
});

test('the Windows wrapper rejects a reused run before building, copying or launching', t => {
  assert(process.env.WESO_TEST_JAVA, 'Explicit WESO_TEST_JAVA required');
  const runId = `20261023-000000-${randomBytes(4).toString('hex')}`;
  const directory = join(root, 'build/bridge-paper-proof', runId);
  mkdirSync(directory, { recursive: true });
  t.after(() => {
    assert.equal(ownedWireRunDirectory(root, directory), directory);
    rmSync(directory, { recursive: true, force: true });
  });
  const report = join(directory, 'wire-proof.json');
  writeFileSync(report, '{"previous":true}');
  const script = join(import.meta.dirname, '../test-paper-bridge.ps1');
  const result = spawnSync('powershell.exe', ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', script,
    '-Java21', dirname(dirname(process.env.WESO_TEST_JAVA)), '-PaperJar', script, '-WorldEditJar', script,
    '-NodeDependencies', directory, '-RunId', runId, '-AcceptEula', '-Offline'],
  { encoding: 'utf8', windowsHide: true, timeout: 30000, maxBuffer: 1024 * 1024 });
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /Never reuse an existing wire-run directory/);
  assert(!result.stdout.includes('Task :'));
  assert.equal(readFileSync(report, 'utf8'), '{"previous":true}');
});
