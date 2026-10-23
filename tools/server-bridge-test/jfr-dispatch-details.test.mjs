import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdirSync, mkdtempSync, rmSync } from 'node:fs';
import { join, relative, resolve, sep } from 'node:path';
import test from 'node:test';

const root=resolve(import.meta.dirname,'../..'),java=process.env.WESO_TEST_JAVA;
assert(java,'Explicit Java 25 WESO_TEST_JAVA required');
function invoke(args) {
  return spawnSync(java,['-Xmx256m','-XX:ActiveProcessorCount=2',...args],
    {encoding:'utf8',windowsHide:true,timeout:30000,maxBuffer:2*1024*1024});
}
function read(t,kind,mode='dispatch') {
  const build=join(root,'build');mkdirSync(build,{recursive:true});
  const directory=mkdtempSync(join(build,'dispatch-reader-'));
  t.after(()=>{const inside=relative(build,resolve(directory));assert(inside.startsWith('dispatch-reader-')&&!inside.includes(sep));rmSync(directory,{recursive:true,force:true});});
  const path=join(directory,'probe.jfr');
  const generated=invoke(['--class-path',join(root,'tools/bridge-slice-agent/build/libs/bridge-slice-bootstrap.jar'),
    join(import.meta.dirname,'JfrDispatchProbe.java'),path,kind]);
  assert.equal(generated.status,0,generated.stderr || generated.error?.message);
  return invoke([join(import.meta.dirname,'JfrDispatchDetails.java'),path,'probe','1',mode]);
}
test('real method and sleep events correlate with synthetic native counters by root identity',t=>{
  const result=read(t,'valid');assert.equal(result.status,0,result.stderr);
  const [totals,row]=result.stdout.trim().split(/\r?\n/).map(line=>JSON.parse(line));
  assert.equal(totals.roots,1);assert.equal(totals.nativeRecords,1);assert.equal(totals.methodTraces,1);
  assert.equal(row.nativeMatched,true);assert.equal(row.cycles,100);assert.equal(row.switches,1);assert.equal(row.reasonsHex,'40');
  assert.equal(row.methodTotals['JfrDispatchProbe::work'].count,1);
  assert(row.stallTotals['jdk.ThreadSleep'].sumNanos>0);
  assert.equal(row.longestMethods[0].kind,'JfrDispatchProbe::work');
});
test('native attribution fails closed for absent, duplicate, foreign and invalid counter evidence',t=>{
  for(const kind of ['missing','duplicate','wrong-id','extra','negative','failed','markers','loss']) {
    const result=read(t,kind);assert.notEqual(result.status,0,`${kind} accepted`);assert.equal(result.stdout,'');
  }
});
test('a plain observer cannot silently accept native or method-trace evidence',t=>{
  const result=read(t,'valid','drain');assert.notEqual(result.status,0);assert.equal(result.stdout,'');
});
test('plain root controls remain valid without native or method instrumentation',t=>{
  const result=read(t,'plain','drain');assert.equal(result.status,0,result.stderr);
  const [totals,row]=result.stdout.trim().split(/\r?\n/).map(line=>JSON.parse(line));
  assert.equal(totals.nativeRecords,0);assert.equal(totals.methodTraces,0);assert.equal(row.nativeMatched,false);
});
