import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { dispatchTraceTargets, dispatchStallEvents, paperSafeDispatchTraceTargets } from './dispatch-diagnostics.mjs';

const root=resolve(import.meta.dirname,'../..'), javaHome=process.argv[2]; assert(javaHome,'Explicit JDK25 home required');
const samplingControl=process.argv[3]==='--sampling-control';
assert(process.argv[3]===undefined || samplingControl,'Unknown study');
const directory=join(root,'build',samplingControl ? 'bridge-dispatch-sampling-proof' : 'bridge-dispatch-diagnostics','26.2'), output=join(directory,'dispatch-cause-analysis.json');
assert(!existsSync(output),'Never overwrite completed attribution');
const analysis=JSON.parse(readFileSync(join(directory,'edit-load-analysis.json')));
const proof=JSON.parse(readFileSync(join(directory,'edit-load-proof.json')));
assert.equal(analysis.dispatchControl,true); assert.equal(proof.dispatchControl,true);
assert.deepEqual(proof.traceTargets,samplingControl ? paperSafeDispatchTraceTargets : dispatchTraceTargets);assert.deepEqual(proof.stallEvents,dispatchStallEvents);
const hash=file=>createHash('sha256').update(readFileSync(file)).digest('hex');
const results=[];
for(const result of analysis.results) {
  const profile=proof.runs[result.round-1].diagnosticProfile;
  const recording=join(directory,`edit-${result.round}.jfr`), recordingHash=hash(recording);
  const invocation=spawnSync(join(javaHome,'bin/java.exe'),['-Xmx256m','-XX:ActiveProcessorCount=2',
    join(import.meta.dirname,'JfrDispatchDetails.java'),recording,`edit-${result.round}-active`,'400',profile],
  {encoding:'utf8',windowsHide:true,timeout:60000,maxBuffer:16*1024*1024});
  assert.equal(invocation.status,0,invocation.stderr || invocation.error?.message);
  const [totals,...intervals]=invocation.stdout.trim().split(/\r?\n/).map(line=>JSON.parse(line));
  const previous=result.slices.phases.find(p=>p.phase==='active');
  const group=previous.totals.groups.find(g=>g.operation==='drain');
  assert.equal(intervals.length,group.count);assert.equal(totals.roots,group.count);
  assert(Math.abs(intervals.reduce((n,i)=>n+i.wallNanos,0)/1e6-group.sumMillis)<.000001);
  assert.equal(intervals.filter(i=>i.wallNanos>500000).length,group.overSoftTarget);
  for(const old of previous.intervals) {
    const row=intervals.find(i=>i.id===old.id && i.thread===old.thread);assert(row);
    assert.equal(row.start,old.start); assert.equal(row.end,old.end);
    assert(Math.abs(row.wallNanos/1e6-old.wallMillis)<.000001);
    assert.equal(row.ownerSamples,old.ownerSamples);
  }
  if(profile==='dispatch') {
    assert.deepEqual([...totals.methods].sort(),[...proof.traceTargets].sort(),'Some requested method filters did not record');
    const log=readFileSync(join(directory,`edit-${result.round}.log`),'utf8');
    assert.equal(Number(log.match(/WESO_DISPATCH_CLOSED completed=(\d+)/)?.[1]),result.slices.totals.calls);
  }
  assert.equal(hash(recording),recordingHash);
  writeFileSync(join(directory,`dispatch-${result.round}.ndjson`),invocation.stdout,{flag:'wx'});
  results.push({round:result.round,profile,recordingHash,drain:group,totals,intervals});
}
writeFileSync(output,JSON.stringify({checkedAt:new Date().toISOString(),proofHash:hash(join(directory,'edit-load-proof.json')),
  analysisHash:hash(join(directory,'edit-load-analysis.json')),readerHash:hash(join(import.meta.dirname,'JfrDispatchDetails.java')),results},null,2)+'\n',{flag:'wx'});
for(const r of results) console.log(JSON.stringify({round:r.round,profile:r.profile,drain:r.drain,
  top:r.intervals.slice(0,8).map(i=>({id:i.id,wallMs:i.wallNanos/1e6,cpuMs:i.cpuNanos/1e6,cycles:i.cycles,
    switches:i.switches,reasons:i.reasonsHex,readUs:(i.beforeReadNanos+i.afterReadNanos)/1000,
    samples:i.ownerSamples,classLoads:i.classLoads,stalls:i.stallTotals,methods:i.longestMethods.slice(0,3)}))}));
