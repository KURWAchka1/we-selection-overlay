import assert from 'node:assert/strict';
import {spawnSync} from 'node:child_process';
import {mkdirSync,mkdtempSync,rmSync,writeFileSync} from 'node:fs';
import {delimiter,join,relative,resolve,sep} from 'node:path';
import test from 'node:test';
import {profilerConfigLibraries,verifyEffectiveProfilerConfigurations} from './paper-profiler-config.mjs';

const root = resolve(import.meta.dirname,'../..'), java = process.env.WESO_TEST_JAVA;
assert(java,'Explicit WESO_TEST_JAVA required');
const libraries = profilerConfigLibraries(join(root,'build/bridge-edit-diagnostics/26.2'));
function invoke(file) {
  return spawnSync(java,['-Xmx128m','-XX:ActiveProcessorCount=2','--class-path',libraries.map(x=>x.file).join(delimiter),
    join(import.meta.dirname,'PaperProfilerConfiguration.java'),file],{windowsHide:true,encoding:'utf8',timeout:30000});
}
test('exact YAML parser rejects malformed, duplicate, custom-tag and oversized configurations', t => {
  const build = join(root,'build'); mkdirSync(build,{recursive:true});
  const directory = mkdtempSync(join(build,'profiler-config-probe-'));
  t.after(()=>{ const inside=relative(build,resolve(directory)); assert(inside.startsWith('profiler-config-probe-')&&!inside.includes(sep)); rmSync(directory,{recursive:true,force:true}); });
  const file=join(directory,'paper-global.yml'), valid='_version: 31\nspark:\n  enabled: true\n  enable-immediately: false\n';
  writeFileSync(file,valid+'nested:\n  empty: null\n'); let result=invoke(file); assert.equal(result.status,0,result.stderr);
  assert.deepEqual(JSON.parse(result.stdout),[{_version:31,spark:{enabled:true,'enable-immediately':false},nested:{empty:null}}]);
  for (const text of [valid+'spark: {}\n',valid.replace('enabled: true','enabled: "true"'),
    valid.replace('_version: 31','_version: 32'),valid.replace('enable-immediately: false','enable-immediately: true'),
    '!!java.net.URL [https://example.com]',valid+'bad: [',valid+'1: numeric-key\n',
    valid+'date: 2026-10-15\n',valid+'number: .inf\n',' '.repeat(1_048_577)]) {
    writeFileSync(file,text); result=invoke(file); assert.notEqual(result.status,0); assert.equal(result.stdout,'');
  }
});
test('matched effective configuration rejects changes outside the one profiler bit', () => {
  const enabled=[true,false,false,true], configs=enabled.map(value=>({_version:31,spark:{enabled:value,'enable-immediately':false},misc:{'max-joins-per-tick':5}}));
  assert.match(verifyEffectiveProfilerConfigurations(configs,enabled),/^[0-9a-f]{64}$/);
  configs[2].misc['max-joins-per-tick']=6;
  assert.throws(()=>verifyEffectiveProfilerConfigurations(configs,enabled),/Non-profiler/);
  configs[2].misc['max-joins-per-tick']=5; configs[1].spark.enabled=true;
  assert.throws(()=>verifyEffectiveProfilerConfigurations(configs,enabled));
  assert.throws(()=>verifyEffectiveProfilerConfigurations(configs.slice(1),enabled));
  configs[1].spark.enabled=false; configs[2].misc.extra=null;
  assert.throws(()=>verifyEffectiveProfilerConfigurations(configs,enabled),/Non-profiler/);
});
