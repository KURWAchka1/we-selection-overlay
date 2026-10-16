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

test('moving edit study is isolated, repeated without method instrumentation and fixed spark policy', () => {
  const study=editDiagnosticPolicy(['--moving-gameplay']);
  assert.equal(study.rootName,'bridge-moving-edit-proof'); assert.equal(study.movingGameplay,true);
  assert.equal(study.matchedSlices,false); assert.equal(study.sparkControl,false);
  assert.deepEqual(study.order,['moving','moving']);
  for (const round of [0,1,2]) assert.deepEqual(editRunPolicy(study,round),{instrumented:false,sparkEnabled:false});
  for (const flag of ['--spark-control','--matched-slices','--without-spark','--moving-gameplay'])
    assert.throws(()=>editDiagnosticPolicy(['--moving-gameplay',flag]));
});

test('budget comparison changes only the pinned companion in previous/candidate/candidate/previous order', () => {
  const study=editDiagnosticPolicy(['--budget-control']);
  assert.equal(study.rootName,'bridge-dispatch-budget-proof'); assert.equal(study.budgetControl,true);
  assert.equal(study.matchedSlices,true); assert.equal(study.movingGameplay,true); assert.equal(study.sparkControl,false);
  assert.deepEqual(study.order,['previous','candidate','candidate','previous']);
  assert.deepEqual([1,2,3,4].map(round=>editRunPolicy(study,round)),[
    {instrumented:true,sparkEnabled:false,variant:'previous'},{instrumented:true,sparkEnabled:false,variant:'candidate'},
    {instrumented:true,sparkEnabled:false,variant:'candidate'},{instrumented:true,sparkEnabled:false,variant:'previous'}]);
  assert.deepEqual(editRunPolicy(study,0),{instrumented:false,sparkEnabled:false,variant:'previous'});
  for (const flag of ['--spark-control','--matched-slices','--moving-gameplay','--budget-control'])
    assert.throws(()=>editDiagnosticPolicy(['--budget-control',flag]));
});
test('ambiguous flags, unknown studies, invalid rounds and accidental policy mutation fail', () => {
  for (const options of [['--spark-control','--matched-slices'],['--spark-control','--spark-control'],['--without-spark'],['26.3'],null])
    assert.throws(()=>editDiagnosticPolicy(options));
  const study = editDiagnosticPolicy(['--spark-control']);
  for (const round of [-1,5,1.5,NaN,'2']) assert.throws(()=>editRunPolicy(study,round));
  assert.throws(()=>{ study.order[0]='spark-off'; });
  assert.throws(()=>{ study.rootName='minecraft'; });
});

test('drain observer control keeps the companion fixed and changes only nested/root instrumentation', () => {
  const study=editDiagnosticPolicy(['--drain-control']);
  assert.equal(study.rootName,'bridge-drain-observer-proof'); assert.equal(study.drainControl,true);
  assert.equal(study.movingGameplay,true); assert.equal(study.matchedSlices,true); assert.equal(study.budgetControl,false);
  assert.deepEqual(study.order,['nested','drain','drain','nested']);
  for (const round of [1,2,3,4]) assert.deepEqual(editRunPolicy(study,round),
    {instrumented:true,sparkEnabled:false,diagnosticProfile:round===2 || round===3 ? 'drain' : 'nested'});
  assert.deepEqual(editRunPolicy(study,0),{instrumented:false,sparkEnabled:false,diagnosticProfile:'nested'});
  for (const flag of ['--matched-slices','--spark-control','--moving-gameplay','--budget-control','--drain-control'])
    assert.throws(()=>editDiagnosticPolicy(['--drain-control',flag]));
});

test('revision comparison uses the same minimal observer in every old/candidate arm', () => {
  const study=editDiagnosticPolicy(['--revision-control']);
  assert.equal(study.rootName,'bridge-revision-proof'); assert.equal(study.revisionControl,true);
  assert.equal(study.budgetControl,true); assert.equal(study.drainControl,false);
  assert.deepEqual(study.order,['previous','candidate','candidate','previous']);
  for(const round of [1,2,3,4]) assert.deepEqual(editRunPolicy(study,round),
    {instrumented:true,sparkEnabled:false,diagnosticProfile:'drain',variant:round===2 || round===3 ? 'candidate' : 'previous'});
  for(const flag of ['--matched-slices','--spark-control','--moving-gameplay','--budget-control','--drain-control','--revision-control'])
    assert.throws(()=>editDiagnosticPolicy(['--revision-control',flag]));
});

test('request-local world comparison changes only its companion with identical minimal observers', () => {
  const study=editDiagnosticPolicy(['--world-control']);
  assert.equal(study.rootName,'bridge-world-read-proof'); assert.equal(study.worldControl,true);
  assert.equal(study.budgetControl,true); assert.equal(study.drainControl,false); assert.equal(study.revisionControl,false);
  for(const round of [1,2,3,4]) assert.deepEqual(editRunPolicy(study,round),
    {instrumented:true,sparkEnabled:false,diagnosticProfile:'drain',variant:round===2 || round===3 ? 'candidate' : 'previous'});
  for(const flag of ['--matched-slices','--spark-control','--moving-gameplay','--budget-control','--drain-control','--revision-control','--world-control'])
    assert.throws(()=>editDiagnosticPolicy(['--world-control',flag]));
});

test('guard-band study keeps its own baseline and identical minimal observers', () => {
  const study=editDiagnosticPolicy(['--guard-control']);
  assert.equal(study.rootName,'bridge-guard-band-proof'); assert.equal(study.guardControl,true); assert.equal(study.budgetControl,true);
  for(const round of [1,2,3,4]) assert.deepEqual(editRunPolicy(study,round),
    {instrumented:true,sparkEnabled:false,diagnosticProfile:'drain',variant:round===2 || round===3 ? 'candidate' : 'previous'});
  for(const flag of ['--matched-slices','--spark-control','--moving-gameplay','--budget-control','--drain-control','--revision-control','--world-control','--guard-control'])
    assert.throws(()=>editDiagnosticPolicy(['--guard-control',flag]));
});
test('combined hot-path comparison pins its own root and unchanged minimal-observer policy',()=>{
  const study=editDiagnosticPolicy(['--hotpath-control']);
  assert.equal(study.rootName,'bridge-hotpath-proof'); assert.equal(study.hotPathControl,true);
  assert.equal(study.guardControl,false); assert.equal(study.budgetControl,true);
  assert.deepEqual(study.order,['previous','candidate','candidate','previous']);
  for(const round of [1,2,3,4]) assert.deepEqual(editRunPolicy(study,round),
    {instrumented:true,sparkEnabled:false,diagnosticProfile:'drain',variant:round===2 || round===3 ? 'candidate' : 'previous'});
  for(const flag of ['--matched-slices','--spark-control','--moving-gameplay','--budget-control','--drain-control','--revision-control','--world-control','--guard-control','--hotpath-control'])
    assert.throws(()=>editDiagnosticPolicy(['--hotpath-control',flag]));
});

test('priority control changes only normal versus below-normal with one fixed companion',()=>{
  const study=editDiagnosticPolicy(['--priority-control']);
  assert.equal(study.rootName,'bridge-priority-proof'); assert.equal(study.priorityControl,true);
  assert.equal(study.budgetControl,false); assert.equal(study.hotPathControl,false);assert.equal(study.drainControl,false);
  assert.deepEqual(study.order,['below-normal','normal','normal','below-normal']);
  for(const round of [1,2,3,4]) assert.deepEqual(editRunPolicy(study,round),
    {instrumented:true,sparkEnabled:false,diagnosticProfile:'drain',variant:round===2 || round===3 ? 'candidate' : 'previous',
      processPriority:round===2 || round===3 ? 'normal' : 'below-normal'});
  for(const flag of ['--matched-slices','--spark-control','--moving-gameplay','--budget-control','--drain-control','--revision-control','--world-control','--guard-control','--hotpath-control','--priority-control'])
    assert.throws(()=>editDiagnosticPolicy(['--priority-control',flag]));
});

test('encoding comparison fixes the minimal observer and changes only the companion',()=>{
  const study=editDiagnosticPolicy(['--encoding-control']);
  assert.equal(study.rootName,'bridge-encoding-proof'); assert.equal(study.encodingControl,true);
  assert.equal(study.priorityControl,false); assert.equal(study.budgetControl,true);
  assert.deepEqual(study.order,['previous','candidate','candidate','previous']);
  for(const round of [1,2,3,4]) assert.deepEqual(editRunPolicy(study,round),
    {instrumented:true,sparkEnabled:false,diagnosticProfile:'drain',variant:round===2 || round===3 ? 'candidate' : 'previous'});
  for(const flag of ['--matched-slices','--spark-control','--moving-gameplay','--budget-control','--drain-control','--revision-control','--world-control','--guard-control','--hotpath-control','--priority-control','--encoding-control'])
    assert.throws(()=>editDiagnosticPolicy(['--encoding-control',flag]));
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
