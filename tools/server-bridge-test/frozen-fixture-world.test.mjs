import assert from 'node:assert/strict';
import { existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, symlinkSync, writeFileSync } from 'node:fs';
import { join, relative, resolve, sep } from 'node:path';
import test from 'node:test';
import { FrozenFixtureWorld } from './frozen-fixture-world.mjs';

const build = resolve(import.meta.dirname, '../../build');
function fixture(t, name = 'bridge-baseline-proof') {
  mkdirSync(build, { recursive: true });
  const scratch = mkdtempSync(join(build, 'frozen-helper-'));
  t.after(() => {
    const path = resolve(scratch), inside = relative(build, path);
    assert(inside.startsWith('frozen-helper-') && !inside.includes(sep));
    rmSync(path, { recursive: true, force: true });
  });
  const directory = join(scratch, name);
  const modern = name === 'bridge-dispatch-diagnostics/26.2' || ['bridge-edit-diagnostics/26.2','bridge-edit-diagnostics/26.1.2',
    'bridge-edit-profiler-control/26.2','bridge-edit-profiler-control/26.1.2','bridge-gameplay-control-proof/26.2','bridge-moving-edit-proof/26.2','bridge-dispatch-budget-proof/26.2','bridge-drain-observer-proof/26.2','bridge-revision-proof/26.2','bridge-world-read-proof/26.2','bridge-guard-band-proof/26.2','bridge-hotpath-proof/26.2','bridge-priority-proof/26.2','bridge-encoding-proof/26.2','bridge-batch-proof/26.2','bridge-batch-seven-proof/26.2','bridge-kind-admission-proof/26.2','bridge-peer-admission-proof/26.2','bridge-authority-proof/26.2'].includes(name);
  for (const name of modern ? ['bridge-fixture'] : ['bridge-fixture', 'bridge-fixture_nether', 'bridge-fixture_the_end']) {
    mkdirSync(join(directory, name, 'region'), { recursive: true });
    writeFileSync(join(directory, name, 'level.dat'), name);
    writeFileSync(join(directory, name, 'region/r.0.0.mca'), 'original region');
  }
  if (modern) for (const dimension of ['overworld','the_nether','the_end']) {
    const path = join(directory,'bridge-fixture/dimensions/minecraft',dimension,'region');
    mkdirSync(path,{recursive:true}); writeFileSync(join(path,'r.0.0.mca'),dimension);
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

test('dispatch diagnostics use a separate modern world and preserve exact restoration guards', t => {
  const {directory, frozen} = fixture(t, 'bridge-dispatch-diagnostics/26.2');
  const digest = frozen.capture();
  writeFileSync(join(directory, 'bridge-fixture/level.dat'), 'changed');
  assert.equal(frozen.restore(), digest);
  assert.equal(readFileSync(join(directory, 'bridge-fixture/level.dat'), 'utf8'), 'bridge-fixture');
});

test('the separate tick-timeline fixture preserves the same snapshot guards', t => {
  const { directory, frozen } = fixture(t, 'bridge-tick-timeline-proof');
  const digest = frozen.capture();
  writeFileSync(join(directory, 'bridge-fixture/level.dat'), 'changed');
  assert.equal(frozen.restore(), digest);
  assert.equal(readFileSync(join(directory, 'bridge-fixture/level.dat'), 'utf8'), 'bridge-fixture');
  assert.throws(() => new FrozenFixtureWorld(resolve(directory, '../bridge-load-proof')), /Not a named/);
});
test('corrupted snapshot refuses restore before changing live fixture worlds', t => {
  const { directory, frozen } = fixture(t); frozen.capture();
  writeFileSync(join(directory, 'frozen-world/bridge-fixture/level.dat'), 'corrupt');
  writeFileSync(join(directory, 'bridge-fixture/level.dat'), 'leave me');
  assert.throws(() => frozen.restore(), /snapshot changed/);
  assert.equal(readFileSync(join(directory, 'bridge-fixture/level.dat'), 'utf8'), 'leave me');
});
test('gameplay has its own guarded frozen world, not a user server', t => {
  const { directory, frozen } = fixture(t, 'bridge-gameplay-proof');
  const digest = frozen.capture();
  writeFileSync(join(directory, 'bridge-fixture/level.dat'), 'gameplay changed world');
  assert.equal(frozen.restore(), digest);
  assert.throws(() => new FrozenFixtureWorld(resolve(directory, '../minecraft')), /Not a named/);
});

test('profiler-control worlds are separately guarded and restore exact bytes', t => {
  const { directory, frozen } = fixture(t, 'bridge-gameplay-control-proof');
  const digest = frozen.capture();
  writeFileSync(join(directory, 'bridge-fixture/level.dat'), 'control changed world');
  assert.equal(frozen.restore(), digest);
  assert.throws(() => frozen.capture(), /Never overwrite/);
});
test('existing snapshots and paths outside intended build fixture are rejected', t => {
  const { directory, frozen } = fixture(t); frozen.capture();
  assert.throws(() => frozen.capture(), /Never overwrite/);
  assert.throws(() => new FrozenFixtureWorld(resolve(build, '../bridge-baseline-proof')));
  assert.throws(() => new FrozenFixtureWorld(resolve(directory, '..')));
});

test('exact-version edit diagnostics restore bytes and reject arbitrary version directories', t => {
  const { directory, frozen } = fixture(t, 'bridge-edit-diagnostics/26.2');
  const digest = frozen.capture();
  writeFileSync(join(directory, 'bridge-fixture/level.dat'), 'edited');
  writeFileSync(join(directory,'bridge-fixture/dimensions/minecraft/the_nether/region/r.0.0.mca'),'changed nether');
  writeFileSync(join(directory,'bridge-fixture/dimensions/minecraft/the_end/region/r.0.0.mca'),'changed end');
  assert.equal(frozen.restore(), digest);
  for (const dimension of ['overworld','the_nether','the_end'])
    assert.equal(readFileSync(join(directory,'bridge-fixture/dimensions/minecraft',dimension,'region/r.0.0.mca'),'utf8'),dimension);
  for (const name of ['26.3', 'latest', '26.1', 'minecraft'])
    assert.throws(() => new FrozenFixtureWorld(resolve(directory, '..', name)), /Not a named/);
});

test('modern snapshot layout cannot be substituted', t => {
  const { directory, frozen } = fixture(t,'bridge-edit-diagnostics/26.1.2'); frozen.capture();
  const file = join(directory,'frozen-world/manifest.json'), manifest = JSON.parse(readFileSync(file));
  delete manifest.layout; writeFileSync(file,JSON.stringify(manifest));
  assert.throws(()=>frozen.restore(),/Snapshot layout differs/);
  assert.equal(readFileSync(join(directory,'bridge-fixture/level.dat'),'utf8'),'bridge-fixture');
});
test('a missing modern snapshot dimension fails before live worlds are touched', t => {
  const { directory, frozen } = fixture(t,'bridge-edit-diagnostics/26.2'); frozen.capture();
  const target = resolve(directory,'frozen-world/bridge-fixture/dimensions/minecraft/the_end');
  assert.equal(relative(directory,target),join('frozen-world/bridge-fixture/dimensions/minecraft/the_end'));
  rmSync(target,{recursive:true});
  assert.throws(()=>frozen.restore(),/ENOENT/);
  assert.equal(readFileSync(join(directory,'bridge-fixture/level.dat'),'utf8'),'bridge-fixture');
});
test('mutable helper properties cannot redirect a recursive restore outside named worlds', t => {
  const { directory, frozen } = fixture(t,'bridge-edit-diagnostics/26.2'); frozen.capture();
  frozen.worlds = ['..'];
  assert.throws(()=>frozen.restore(),/Invalid fixture world targets/);
  assert.equal(readFileSync(join(directory,'bridge-fixture/level.dat'),'utf8'),'bridge-fixture');
});
test('profiler-control roots preserve exact modern and legacy layouts without broadening version support', t => {
  for (const version of ['1.21.11','26.1.2','26.2']) {
    const {directory,frozen} = fixture(t,`bridge-edit-profiler-control/${version}`), digest = frozen.capture();
    writeFileSync(join(directory,'bridge-fixture/level.dat'),'modified');
    assert.equal(frozen.restore(),digest);
    assert.equal(readFileSync(join(directory,'bridge-fixture/level.dat'),'utf8'),'bridge-fixture');
    assert.throws(()=>new FrozenFixtureWorld(resolve(directory,'../26.3')),/Not a named/);
    assert.throws(()=>new FrozenFixtureWorld(resolve(directory,'../latest')),/Not a named/);
  }
});
test('junction inside a target refuses recursive restore before deletion', t => {
  const { directory, frozen } = fixture(t); frozen.capture();
  symlinkSync(join(directory, 'frozen-world'), join(directory, 'bridge-fixture/link'), 'junction');
  assert.throws(() => frozen.restore(), /No links/);
  assert(existsSync(join(directory, 'bridge-fixture/level.dat')));
});

test('exact 26.2 moving gameplay restores modern dimensions without accepting untested hosts', t => {
  const {directory,frozen}=fixture(t,'bridge-gameplay-control-proof/26.2'),digest=frozen.capture();
  writeFileSync(join(directory,'bridge-fixture/dimensions/minecraft/overworld/region/r.0.0.mca'),'changed');
  assert.equal(frozen.restore(),digest);
  for (const name of ['26.1.2','26.3','latest']) assert.throws(()=>new FrozenFixtureWorld(resolve(directory,'..',name)),/Not a named/);
});

test('moving edit study keeps a separate modern snapshot and refuses unknown host roots', t => {
  const {directory,frozen}=fixture(t,'bridge-moving-edit-proof/26.2'),digest=frozen.capture();
  writeFileSync(join(directory,'bridge-fixture/dimensions/minecraft/overworld/region/r.0.0.mca'),'changed');
  assert.equal(frozen.restore(),digest);
  for (const name of ['1.21.11','26.1.2','26.3','latest']) assert.throws(()=>new FrozenFixtureWorld(resolve(directory,'..',name)),/Not a named/);
});

test('budget comparison cannot restore another version or use a legacy world layout', t => {
  const {directory,frozen}=fixture(t,'bridge-dispatch-budget-proof/26.2'),digest=frozen.capture();
  writeFileSync(join(directory,'bridge-fixture/dimensions/minecraft/overworld/region/r.0.0.mca'),'changed');
  assert.equal(frozen.restore(),digest);
  for (const name of ['1.21.11','26.1.2','26.3','latest']) assert.throws(()=>new FrozenFixtureWorld(resolve(directory,'..',name)),/Not a named/);
});

test('drain observer study cannot reuse another evidence root or host version', t => {
  const {directory,frozen}=fixture(t,'bridge-drain-observer-proof/26.2'),digest=frozen.capture();
  writeFileSync(join(directory,'bridge-fixture/dimensions/minecraft/overworld/region/r.0.0.mca'),'changed');
  assert.equal(frozen.restore(),digest);
  for (const name of ['1.21.11','26.1.2','26.3','latest']) assert.throws(()=>new FrozenFixtureWorld(resolve(directory,'..',name)),/Not a named/);
});

test('revision comparison restores only its named exact-host world', t => {
  const {directory,frozen}=fixture(t,'bridge-revision-proof/26.2'),digest=frozen.capture();
  writeFileSync(join(directory,'bridge-fixture/dimensions/minecraft/overworld/region/r.0.0.mca'),'changed');
  assert.equal(frozen.restore(),digest);
  for (const name of ['1.21.11','26.1.2','26.3','latest']) assert.throws(()=>new FrozenFixtureWorld(resolve(directory,'..',name)),/Not a named/);
});

test('world-read comparison cannot restore an unrelated tuple', t => {
  const {directory,frozen}=fixture(t,'bridge-world-read-proof/26.2'),digest=frozen.capture();
  writeFileSync(join(directory,'bridge-fixture/dimensions/minecraft/overworld/region/r.0.0.mca'),'changed');
  assert.equal(frozen.restore(),digest);
  for (const name of ['1.21.11','26.1.2','26.3','latest']) assert.throws(()=>new FrozenFixtureWorld(resolve(directory,'..',name)),/Not a named/);
});

test('guard-band comparison refuses other hosts and preserves exact frozen bytes', t => {
  const {directory,frozen}=fixture(t,'bridge-guard-band-proof/26.2'),digest=frozen.capture();
  writeFileSync(join(directory,'bridge-fixture/dimensions/minecraft/overworld/region/r.0.0.mca'),'changed');
  assert.equal(frozen.restore(),digest);
  for (const name of ['1.21.11','26.1.2','26.3','latest']) assert.throws(()=>new FrozenFixtureWorld(resolve(directory,'..',name)),/Not a named/);
});

test('combined hot-path trial cannot restore another evidence root or host tuple',t=>{
  const {directory,frozen}=fixture(t,'bridge-hotpath-proof/26.2'),digest=frozen.capture();
  writeFileSync(join(directory,'bridge-fixture/dimensions/minecraft/overworld/region/r.0.0.mca'),'changed');
  assert.equal(frozen.restore(),digest);
  for (const name of ['1.21.11','26.1.2','26.3','latest']) assert.throws(()=>new FrozenFixtureWorld(resolve(directory,'..',name)),/Not a named/);
});

test('priority control restores only its separately named 26.2 fixture',t=>{
  const {directory,frozen}=fixture(t,'bridge-priority-proof/26.2'),digest=frozen.capture();
  writeFileSync(join(directory,'bridge-fixture/dimensions/minecraft/overworld/region/r.0.0.mca'),'changed');
  assert.equal(frozen.restore(),digest);
  for (const name of ['1.21.11','26.1.2','26.3','latest']) assert.throws(()=>new FrozenFixtureWorld(resolve(directory,'..',name)),/Not a named/);
});

test('encoding comparison restores only its separately named 26.2 fixture',t=>{
  const {directory,frozen}=fixture(t,'bridge-encoding-proof/26.2'),digest=frozen.capture();
  writeFileSync(join(directory,'bridge-fixture/dimensions/minecraft/overworld/region/r.0.0.mca'),'changed');
  assert.equal(frozen.restore(),digest);
  for (const name of ['1.21.11','26.1.2','26.3','latest']) assert.throws(()=>new FrozenFixtureWorld(resolve(directory,'..',name)),/Not a named/);
});

test('batch comparison restores exact bytes only in its named 26.2 fixture',t=>{
  const {directory,frozen}=fixture(t,'bridge-batch-proof/26.2'),digest=frozen.capture();
  writeFileSync(join(directory,'bridge-fixture/dimensions/minecraft/overworld/region/r.0.0.mca'),'changed');
  assert.equal(frozen.restore(),digest);
  for (const name of ['1.21.11','26.1.2','26.3','latest']) assert.throws(()=>new FrozenFixtureWorld(resolve(directory,'..',name)),/Not a named/);
});

test('seven-visit trial cannot restore another host and preserves its exact snapshot',t=>{
  const {directory,frozen}=fixture(t,'bridge-batch-seven-proof/26.2'),digest=frozen.capture();
  writeFileSync(join(directory,'bridge-fixture/dimensions/minecraft/overworld/region/r.0.0.mca'),'changed');
  assert.equal(frozen.restore(),digest);
  for (const name of ['1.21.11','26.1.2','26.3','latest']) assert.throws(()=>new FrozenFixtureWorld(resolve(directory,'..',name)),/Not a named/);
});

test('request-kind trial restores only its own exact-host modern world',t=>{
  const {directory,frozen}=fixture(t,'bridge-kind-admission-proof/26.2'),digest=frozen.capture();
  writeFileSync(join(directory,'bridge-fixture/dimensions/minecraft/overworld/region/r.0.0.mca'),'changed');
  assert.equal(frozen.restore(),digest);
  for (const name of ['1.21.11','26.1.2','26.3','latest']) assert.throws(()=>new FrozenFixtureWorld(resolve(directory,'..',name)),/Not a named/);
});

test('peer-local trial keeps exact frozen dimensions and rejects every other host',t=>{
  const {directory,frozen}=fixture(t,'bridge-peer-admission-proof/26.2'),digest=frozen.capture();
  writeFileSync(join(directory,'bridge-fixture/dimensions/minecraft/overworld/region/r.0.0.mca'),'changed');
  assert.equal(frozen.restore(),digest);
  for (const name of ['1.21.11','26.1.2','26.3','latest']) assert.throws(()=>new FrozenFixtureWorld(resolve(directory,'..',name)),/Not a named/);
});

test('combined-authority comparison cannot restore an old study or another host', t => {
  const {directory, frozen} = fixture(t, 'bridge-authority-proof/26.2'), digest = frozen.capture();
  writeFileSync(join(directory, 'bridge-fixture/dimensions/minecraft/overworld/region/r.0.0.mca'), 'changed');
  assert.equal(frozen.restore(), digest);
  for (const name of ['1.21.11', '26.1.2', '26.3', 'latest']) assert.throws(() => new FrozenFixtureWorld(resolve(directory, '..', name)), /Not a named/);
});
