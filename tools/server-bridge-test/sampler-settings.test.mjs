import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { join, relative, resolve, sep } from 'node:path';
import test from 'node:test';

const root = resolve(import.meta.dirname, '../..');
const event = (name, enabled) => `<event name="${name}"><setting name="enabled">${enabled}</setting><setting name="period">20 ms</setting></event>`;
const configuration = enabled => `<configuration>${event('jdk.ExecutionSample', enabled)}${event('jdk.NativeMethodSample', enabled)}<event name="jdk.ThreadPark"><setting name="threshold">0 ns</setting></event></configuration>`;

function check(t, on, off) {
  const build = join(root, 'build');
  mkdirSync(build, { recursive: true });
  const directory = mkdtempSync(join(build, 'sampler-settings-'));
  t.after(() => {
    const inside = relative(build, resolve(directory));
    assert(inside.startsWith('sampler-settings-') && !inside.includes(sep));
    rmSync(directory, { recursive: true, force: true });
  });
  writeFileSync(join(directory, 'on.jfc'), on);
  writeFileSync(join(directory, 'off.jfc'), off);
  return spawnSync('powershell.exe', ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-File',
    join(import.meta.dirname, 'Assert-SamplerSettings.ps1'), '-On', join(directory, 'on.jfc'), '-Off', join(directory, 'off.jfc')],
  { encoding: 'utf8', windowsHide: true, timeout: 30000, maxBuffer: 1024 * 1024 });
}

test('sampler control permits exactly the two enabled-bit differences', t => {
  const result = check(t, configuration('true'), configuration('false'));
  assert.equal(result.status, 0, result.stderr || result.error?.message);
  assert.deepEqual(JSON.parse(result.stdout), { equalExceptTwoSamplerBits: true });
});

test('sampler control rejects missing, duplicate and wrong event or enabled settings', t => {
  const on = configuration('true'), off = configuration('false');
  const cases = [
    [off, off], [on, on], [on.replace('true', 'TRUE'), off],
    [on.replace(event('jdk.ExecutionSample', 'true'), ''), off],
    [on.replace('</configuration>', `${event('jdk.ExecutionSample', 'true')}</configuration>`), off],
    [on.replace('</configuration>', '<event name="jdk.ExecutionSample"/></configuration>'), off],
    [on.replace('</event>', '<setting name="enabled">true</setting></event>'), off],
    [on, off.replace('</event>', '<setting name="enabled">false</setting></event>')],
    [on, off.replace('</configuration>', '<event name="jdk.NativeMethodSample"/></configuration>')],
  ];
  for (const [a, b] of cases) {
    const result = check(t, a, b);
    assert.notEqual(result.status, 0, `Invalid settings accepted: ${a} / ${b}`);
    assert.equal(result.stdout, '');
  }
});

test('sampler control rejects unrelated changes, oversized input and unsafe or malformed XML', t => {
  const on = configuration('true'), off = configuration('false');
  for (const [a, b] of [
    [on, off.replace('0 ns', '20 ms')], [on, off.replace('20 ms', '1 ms')],
    ['<!DOCTYPE configuration [<!ENTITY probe "true">]>' + on, off],
    [on, '<configuration>'], ['<wrong/>', '<wrong/>'],
    [on.replace('<configuration>', '<configuration xmlns="unexpected">'), off],
    [on + ' '.repeat(512 * 1024), off], [on, off + ' '.repeat(512 * 1024)],
  ]) {
    const result = check(t, a, b);
    assert.notEqual(result.status, 0, 'Uncontrolled settings accepted');
    assert.equal(result.stdout, '');
  }
});
