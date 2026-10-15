import assert from 'node:assert/strict';
import test from 'node:test';
import {editDiagnosticPolicy,editRunPolicy,paperProfilerConfig,verifyProfilerLog,verifyProfilerSamples} from './edit-diagnostic-policy.mjs';

const on = '[spark] Starting background profiler...\nbuilt-in Java engine will be used instead.';
const off = 'spark profiler will not be enabled because it is currently disabled';
test('plain and historical instrumentation studies preserve their exact policies', () => {
  const plain = editDiagnosticPolicy([]), previous = editDiagnosticPolicy(['--matched-slices']);
  assert.equal(plain.rootName,'bridge-edit-load-proof'); assert.equal(plain.matchedSlices,false);
  assert.equal(previous.rootName,'bridge-edit-diagnostics'); assert.equal(previous.sparkControl,false);
  assert.deepEqual([1,2,3,4].map(round=>editRunPolicy(previous,round).instrumented),[false,true,true,false]);
  assert([0,1,2,3,4].every(round=>editRunPolicy(previous,round).sparkEnabled));
});
test('spark controls instrument every measured arm and reverse profiler order', () => {
  const study = editDiagnosticPolicy(['--spark-control']);
  assert.equal(study.rootName,'bridge-edit-profiler-control'); assert.equal(study.matchedSlices,true);
  assert.deepEqual(study.order,['spark-on','spark-off','spark-off','spark-on']);
  assert.deepEqual([1,2,3,4].map(round=>editRunPolicy(study,round)),[
    {instrumented:true,sparkEnabled:true},{instrumented:true,sparkEnabled:false},
    {instrumented:true,sparkEnabled:false},{instrumented:true,sparkEnabled:true}]);
  assert.deepEqual(editRunPolicy(study,0),{instrumented:false,sparkEnabled:true});
});
test('ambiguous flags, unknown studies, invalid rounds and accidental policy mutation fail', () => {
  for (const options of [['--spark-control','--matched-slices'],['--spark-control','--spark-control'],['--without-spark'],['26.3'],null])
    assert.throws(()=>editDiagnosticPolicy(options));
  const study = editDiagnosticPolicy(['--spark-control']);
  for (const round of [-1,5,1.5,NaN,'2']) assert.throws(()=>editRunPolicy(study,round));
  assert.throws(()=>{ study.order[0]='spark-off'; });
  assert.throws(()=>{ study.rootName='minecraft'; });
});
test('Paper input defaults are identical except for the profiler enable bit', () => {
  const enabled = paperProfilerConfig(true), disabled = paperProfilerConfig(false);
  assert.deepEqual(enabled,{_version:31,spark:{enabled:true,'enable-immediately':false}});
  disabled.spark.enabled = true; assert.deepEqual(disabled,enabled);
  for (const value of [null,1,'false',undefined]) assert.throws(()=>paperProfilerConfig(value));
});
test('startup logs require the exact chosen profiler policy and sampling engine', () => {
  verifyProfilerLog(on,true); verifyProfilerLog(off,false);
  for (const [log,enabled] of [[on,false],[off,true],['',true],['',false],[on+'\n'+off,true],[on+'\n'+off,false],['[spark] Starting background profiler...',true]])
    assert.throws(()=>verifyProfilerLog(log,enabled));
});
test('actual sampler evidence is required independently from the configuration and logs', () => {
  const totals = {threadDumpCallers:[{name:'spark-java-sampler-2-thread-1',count:3},{name:'Server thread',count:1}]};
  assert.equal(verifyProfilerSamples(totals,true),3); assert.throws(()=>verifyProfilerSamples(totals,false));
  assert.equal(verifyProfilerSamples({threadDumpCallers:[{name:'Server thread',count:4}]},false),0);
  assert.throws(()=>verifyProfilerSamples({threadDumpCallers:[]},true));
  for (const count of [-1,NaN,Infinity,1.1,'1'])
    assert.throws(()=>verifyProfilerSamples({threadDumpCallers:[{name:'spark-java-sampler-test',count}]},true));
  assert.throws(()=>verifyProfilerSamples({threadDumpCallers:[
    {name:'spark-java-sampler-a',count:Number.MAX_SAFE_INTEGER},{name:'spark-java-sampler-b',count:Number.MAX_SAFE_INTEGER}]},true),/overflow/);
});
