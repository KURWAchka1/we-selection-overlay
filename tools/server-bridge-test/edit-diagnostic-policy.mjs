import assert from 'node:assert/strict';

export function editDiagnosticPolicy(options) {
  assert(Array.isArray(options) && options.length <= 1
    && options.every(option => ['--matched-slices','--spark-control','--moving-gameplay'].includes(option)), 'Choose one exact edit diagnostic study');
  const sparkControl = options[0] === '--spark-control', movingGameplay = options[0] === '--moving-gameplay';
  const matchedSlices = options.length === 1 && !movingGameplay;
  return Object.freeze({matchedSlices,sparkControl,movingGameplay,
    rootName:movingGameplay ? 'bridge-moving-edit-proof' : sparkControl ? 'bridge-edit-profiler-control' : matchedSlices ? 'bridge-edit-diagnostics' : 'bridge-edit-load-proof',
    order:Object.freeze(movingGameplay ? ['moving','moving'] : sparkControl ? ['spark-on','spark-off','spark-off','spark-on'] : ['control','instrumented','instrumented','control'])});
}

export function editRunPolicy(policy, round) {
  assert(Number.isInteger(round) && round >= 0 && round <= 4, 'Invalid edit study round');
  const middle = round === 2 || round === 3;
  return Object.freeze({instrumented:policy.matchedSlices && round > 0 && (policy.sparkControl || middle),
    sparkEnabled:!policy.movingGameplay && (!policy.sparkControl || !middle)});
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
