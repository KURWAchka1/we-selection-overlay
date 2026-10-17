import assert from 'node:assert/strict';

export function editDiagnosticPolicy(options) {
  assert(Array.isArray(options) && options.length <= 1
    && options.every(option => ['--matched-slices','--spark-control','--moving-gameplay','--budget-control','--drain-control','--revision-control','--world-control','--guard-control','--hotpath-control','--priority-control','--encoding-control','--batch-control','--batch-seven-control','--kind-control','--peer-control','--authority-control'].includes(option)), 'Choose one exact edit diagnostic study');
  const revisionControl = options[0] === '--revision-control';
  const worldControl = options[0] === '--world-control';
  const guardControl = options[0] === '--guard-control';
  const hotPathControl = options[0] === '--hotpath-control';
  const priorityControl = options[0] === '--priority-control';
  const encodingControl = options[0] === '--encoding-control';
  const batchControl = ['--batch-control','--batch-seven-control'].includes(options[0]);
  const batchVisits = options[0] === '--batch-seven-control' ? 7 : 6;
  const kindControl = options[0] === '--kind-control';
  const peerControl = options[0] === '--peer-control';
  const authorityControl = options[0] === '--authority-control';
  const sparkControl = options[0] === '--spark-control', budgetControl = revisionControl || worldControl || guardControl || hotPathControl || encodingControl || batchControl || kindControl || peerControl || authorityControl || options[0] === '--budget-control';
  const drainControl = options[0] === '--drain-control';
  const movingGameplay = budgetControl || drainControl || priorityControl || options[0] === '--moving-gameplay';
  const matchedSlices = budgetControl || drainControl || priorityControl || options.length === 1 && !movingGameplay;
  return Object.freeze({matchedSlices,sparkControl,movingGameplay,budgetControl,drainControl,revisionControl,worldControl,guardControl,hotPathControl,priorityControl,encodingControl,batchControl,
    ...(kindControl ? {kindControl} : {}),
    ...(peerControl ? {peerControl} : {}),
    ...(authorityControl ? {authorityControl} : {}),
    ...(batchControl ? {batchVisits} : {}),
    rootName:authorityControl ? 'bridge-authority-proof' : peerControl ? 'bridge-peer-admission-proof' : kindControl ? 'bridge-kind-admission-proof' : batchControl ? (batchVisits===7 ? 'bridge-batch-seven-proof' : 'bridge-batch-proof') : encodingControl ? 'bridge-encoding-proof' : priorityControl ? 'bridge-priority-proof' : hotPathControl ? 'bridge-hotpath-proof' : guardControl ? 'bridge-guard-band-proof' : worldControl ? 'bridge-world-read-proof' : revisionControl ? 'bridge-revision-proof' : drainControl ? 'bridge-drain-observer-proof' : budgetControl ? 'bridge-dispatch-budget-proof' : movingGameplay ? 'bridge-moving-edit-proof' : sparkControl ? 'bridge-edit-profiler-control' : matchedSlices ? 'bridge-edit-diagnostics' : 'bridge-edit-load-proof',
    order:Object.freeze(priorityControl ? ['below-normal','normal','normal','below-normal'] : drainControl ? ['nested','drain','drain','nested'] : budgetControl ? ['previous','candidate','candidate','previous'] : movingGameplay ? ['moving','moving'] : sparkControl ? ['spark-on','spark-off','spark-off','spark-on'] : ['control','instrumented','instrumented','control'])});
}

export function editRunPolicy(policy, round) {
  assert(Number.isInteger(round) && round >= 0 && round <= 4, 'Invalid edit study round');
  const middle = round === 2 || round === 3;
  return Object.freeze({instrumented:policy.matchedSlices && round > 0 && (policy.sparkControl || policy.budgetControl || policy.drainControl || policy.priorityControl || middle),
    sparkEnabled:!policy.movingGameplay && (!policy.sparkControl || !middle),
    ...(policy.revisionControl || policy.worldControl || policy.guardControl || policy.hotPathControl || policy.priorityControl || policy.encodingControl || policy.batchControl || policy.kindControl || policy.peerControl || policy.authorityControl ? {diagnosticProfile:'drain'} : policy.drainControl ? {diagnosticProfile:middle ? 'drain' : 'nested'} : {}),
    ...(policy.budgetControl || policy.priorityControl ? {variant:middle ? 'candidate' : 'previous'} : {}),
    ...(policy.priorityControl ? {processPriority:middle ? 'normal' : 'below-normal'} : {})});
}

export function paperProfilerConfig(enabled) {
  assert.equal(typeof enabled,'boolean');
  return {_version:31,spark:{enabled,'enable-immediately':false}};
}

export function verifyProfilerLog(log, enabled) {
  const started = log.includes('[spark] Starting background profiler...');
  const disabled = log.includes('spark profiler will not be enabled because it is currently disabled');
  assert.equal(started,enabled,'Spark startup policy differs from measured arm');
  assert.equal(disabled,!enabled,'Spark disable policy differs from measured arm');
  if (enabled) assert(log.includes('built-in Java engine will be used instead.'),'Expected Windows Java sampling engine');
}

export function verifyProfilerSamples(totals, enabled) {
  assert(Array.isArray(totals.threadDumpCallers));
  let count = 0;
  for (const caller of totals.threadDumpCallers) {
    assert.equal(typeof caller.name,'string');
    assert(Number.isSafeInteger(caller.count) && caller.count >= 0,'Invalid sampler count');
    if (caller.name.startsWith('spark-java-sampler-')) count += caller.count;
  }
  assert(Number.isSafeInteger(count),'Sampler total overflow');
  assert(enabled ? count > 0 : count === 0,'Recorded spark sampling differs from configured arm');
  return count;
}
