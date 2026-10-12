import assert from 'node:assert/strict';
import { existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, symlinkSync, writeFileSync } from 'node:fs';
import { join, relative, resolve, sep } from 'node:path';
import test from 'node:test';
import { FrozenFixtureWorld } from './frozen-fixture-world.mjs';

const build = resolve(import.meta.dirname, '../../build');
function fixture(t) {
  mkdirSync(build, { recursive: true });
  const scratch = mkdtempSync(join(build, 'frozen-helper-'));
  t.after(() => {
    const path = resolve(scratch), inside = relative(build, path);
    assert(inside.startsWith('frozen-helper-') && !inside.includes(sep));
    rmSync(path, { recursive: true, force: true });
  });
  const directory = join(scratch, 'bridge-baseline-proof');
  for (const name of ['bridge-fixture', 'bridge-fixture_nether', 'bridge-fixture_the_end']) {
    mkdirSync(join(directory, name, 'region'), { recursive: true });
    writeFileSync(join(directory, name, 'level.dat'), name);
    writeFileSync(join(directory, name, 'region/r.0.0.mca'), 'original region');
  }
  return { directory, frozen: new FrozenFixtureWorld(directory) };
}
test('snapshot restores exact bytes repeatedly, including deleted and new files', t => {
  const { directory, frozen } = fixture(t);
  const digest = frozen.capture();
  for (let i = 0; i < 3; i++) {
    writeFileSync(join(directory, 'bridge-fixture/level.dat'), `changed ${i}`);
    writeFileSync(join(directory, 'bridge-fixture/extra.dat'), 'new');
    rmSync(join(directory, 'bridge-fixture_nether/level.dat'));
    assert.equal(frozen.restore(), digest);
    assert.equal(readFileSync(join(directory, 'bridge-fixture/level.dat'), 'utf8'), 'bridge-fixture');
    assert(!existsSync(join(directory, 'bridge-fixture/extra.dat')));
  }
});
test('corrupted snapshot refuses restore before changing live fixture worlds', t => {
  const { directory, frozen } = fixture(t); frozen.capture();
  writeFileSync(join(directory, 'frozen-world/bridge-fixture/level.dat'), 'corrupt');
  writeFileSync(join(directory, 'bridge-fixture/level.dat'), 'leave me');
  assert.throws(() => frozen.restore(), /snapshot changed/);
  assert.equal(readFileSync(join(directory, 'bridge-fixture/level.dat'), 'utf8'), 'leave me');
});
test('existing snapshots and paths outside intended build fixture are rejected', t => {
  const { directory, frozen } = fixture(t); frozen.capture();
  assert.throws(() => frozen.capture(), /Never overwrite/);
  assert.throws(() => new FrozenFixtureWorld(resolve(build, '../bridge-baseline-proof')));
  assert.throws(() => new FrozenFixtureWorld(resolve(directory, '..')));
});
test('junction inside a target refuses recursive restore before deletion', t => {
  const { directory, frozen } = fixture(t); frozen.capture();
  symlinkSync(join(directory, 'frozen-world'), join(directory, 'bridge-fixture/link'), 'junction');
  assert.throws(() => frozen.restore(), /No links/);
  assert(existsSync(join(directory, 'bridge-fixture/level.dat')));
});
