import assert from 'node:assert/strict';
import { join } from 'node:path';
import test from 'node:test';
import { loadHost } from './gameplay-host.mjs';

test('legacy workload paths and exact host remain unchanged', () => {
  assert.equal(loadHost('load').directory,join('build','bridge-load-proof'));
  assert.equal(loadHost('gameplay',['--without-spark']).directory,join('build','bridge-gameplay-control-proof'));
  assert.equal(loadHost('gameplay').version,'1.21.11');
});
test('26.2 gameplay requires explicit matched profiler policy and versioned evidence', () => {
  const host = loadHost('gameplay',['26.2','--without-spark']);
  assert.equal(host.directory,join('build','bridge-gameplay-control-proof','26.2'));
  assert.equal(host.tuple.paperBuild,133); assert.equal(host.tuple.worldedit,'7.4.5'); assert(Object.isFrozen(host));
  for (const [mode, options] of [['gameplay',['26.2']],['load',['26.2','--without-spark']],
    ['gameplay',['26.1.2']],['gameplay',['26.3']],['gameplay',['--without-spark','--without-spark']],['load',['--without-spark']]])
    assert.throws(() => loadHost(mode,options));
});
