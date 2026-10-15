import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { cpSync, existsSync, lstatSync, mkdirSync, readFileSync, readdirSync, realpathSync, rmSync, writeFileSync } from 'node:fs';
import { basename, dirname, isAbsolute, join, relative, resolve, sep } from 'node:path';

const build = resolve(import.meta.dirname, '../../build');
const worlds = ['bridge-fixture', 'bridge-fixture_nether', 'bridge-fixture_the_end'];

function inspectTree(path, label, result = []) {
  const stat = lstatSync(path);
  assert(!stat.isSymbolicLink(), `No links/junctions in fixture worlds: ${path}`);
  if (stat.isDirectory()) {
    for (const name of readdirSync(path).sort()) inspectTree(join(path, name), `${label}/${name}`, result);
  } else {
    assert(stat.isFile(), `Not a regular world file: ${path}`);
    result.push({ path: label, bytes: stat.size, sha256: createHash('sha256').update(readFileSync(path)).digest('hex') });
  }
  return result;
}

/** All operations are restricted to named synthetic worlds under the repo's build directory. */
export class FrozenFixtureWorld {
  constructor(directory) {
    this.root = resolve(directory);
    const inside = relative(build, this.root);
    assert(inside && !isAbsolute(inside) && inside !== '..' && !inside.startsWith(`..${sep}`));
    const editDiagnostics = ['bridge-edit-diagnostics','bridge-edit-profiler-control'].includes(basename(dirname(this.root)))
      && ['1.21.11', '26.1.2', '26.2'].includes(basename(this.root));
    const modernGameplay = basename(dirname(this.root)) === 'bridge-gameplay-control-proof' && basename(this.root) === '26.2';
    assert(editDiagnostics || modernGameplay || ['bridge-baseline-proof', 'bridge-tick-timeline-proof', 'bridge-gameplay-proof', 'bridge-gameplay-control-proof'].includes(basename(this.root)), 'Not a named frozen fixture');
    this.modern = modernGameplay || editDiagnostics && basename(this.root) !== '1.21.11';
    this.worlds = Object.freeze(this.modern ? ['bridge-fixture'] : [...worlds]);
    assert.equal(realpathSync(this.root).toLowerCase(), this.root.toLowerCase(), 'Fixture root must not resolve through links');
    this.snapshot = join(this.root, 'frozen-world');
    this.manifest = join(this.snapshot, 'manifest.json');
  }
  exists() { return existsSync(this.manifest); }
  files(directory) {
    assert.deepEqual(this.worlds,this.modern ? ['bridge-fixture'] : worlds,'Invalid fixture world targets');
    if (this.modern) for (const dimension of ['overworld','the_nether','the_end']) {
      const path = join(directory,'bridge-fixture/dimensions/minecraft',dimension);
      assert(lstatSync(path).isDirectory() && !lstatSync(path).isSymbolicLink(), 'Missing/linked modern fixture dimension');
    }
    return this.worlds.flatMap(name => inspectTree(join(directory, name), name));
  }
  capture() {
    assert.equal(realpathSync(this.root).toLowerCase(), this.root.toLowerCase());
    assert(!existsSync(this.snapshot), 'Never overwrite an existing frozen snapshot');
    const files = this.files(this.root);
    mkdirSync(this.snapshot);
    for (const name of this.worlds) cpSync(join(this.root, name), join(this.snapshot, name), { recursive: true, errorOnExist: true, force: false });
    assert.deepEqual(this.files(this.snapshot), files, 'Snapshot copy differs');
    writeFileSync(this.manifest, JSON.stringify({ schema: 1, ...(this.modern ? {layout:'paper-26x'} : {}), files }, null, 2));
    return this.digest();
  }
  digest() {
    assert(!lstatSync(this.snapshot).isSymbolicLink(), 'Snapshot must not be a link/junction');
    assert(!lstatSync(this.manifest).isSymbolicLink(), 'Manifest must not be a link');
    const manifest = JSON.parse(readFileSync(this.manifest, 'utf8'));
    assert.equal(manifest.schema, 1);
    assert.equal(manifest.layout, this.modern ? 'paper-26x' : undefined, 'Snapshot layout differs from exact fixture tuple');
    assert.deepEqual(this.files(this.snapshot), manifest.files, 'Frozen snapshot changed');
    return createHash('sha256').update(JSON.stringify(manifest.files)).digest('hex');
  }
  restore() {
    const digest = this.digest();
    assert.equal(realpathSync(this.root).toLowerCase(), this.root.toLowerCase());
    // Resolve and inspect every target before any recursive removal.
    for (const name of this.worlds) {
      const target = resolve(this.root, name);
      assert.equal(relative(this.root, target), name);
      if (existsSync(target)) inspectTree(target, name);
    }
    for (const name of this.worlds) {
      const target = resolve(this.root, name);
      rmSync(target, { recursive: true, force: true });
      cpSync(join(this.snapshot, name), target, { recursive: true, errorOnExist: true, force: false });
    }
    const restored = createHash('sha256').update(JSON.stringify(this.files(this.root))).digest('hex');
    assert.equal(restored, digest, 'Restored world bytes differ');
    return digest;
  }
}
