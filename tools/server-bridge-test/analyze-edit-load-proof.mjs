import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { existsSync, readFileSync, readdirSync, unlinkSync, writeFileSync } from 'node:fs';
import { delimiter, join, resolve } from 'node:path';
import { verifyEditLoadWindow } from './edit-load-window.mjs';
import { gestureTuple } from './gesture-tuples.mjs';
import { javaLoadVersion, javaLoadSha256 } from './java-load-clients.mjs';
import { channel, decode } from './wire-codec.mjs';
import { FrozenFixtureWorld } from './frozen-fixture-world.mjs';
import { editDiagnosticPolicy, editRunPolicy, paperProfilerConfig, verifyProfilerLog, verifyProfilerSamples } from './edit-diagnostic-policy.mjs';
import { profilerConfigLibraries, verifyEffectiveProfilerConfigurations } from './paper-profiler-config.mjs';
import { verifyMovingEditWindow, verifyMovingEditTargets, reconcileMovingEditReplies, movingEditHostCounters } from './moving-edit-window.mjs';

const javaHome = process.argv[2]; assert(javaHome, 'Explicit JDK home required');
const version = process.argv[3] ?? '1.21.11', tuple = gestureTuple(version);
const study = editDiagnosticPolicy(process.argv.slice(4)), {matchedSlices,sparkControl,movingGameplay,budgetControl} = study;
const frozenStudy=matchedSlices || movingGameplay,profilerStudy=sparkControl || movingGameplay;
if (movingGameplay) assert.equal(version,'26.2');
const root = resolve(import.meta.dirname, '../..'), directory = frozenStudy ? join(root, 'build',study.rootName, version)
  : join(root, 'build/bridge-edit-load-proof', ...(version === '1.21.11' ? [] : [version]));
const output = join(directory, 'edit-load-analysis.json'); if (existsSync(output)) unlinkSync(output);
const proof = JSON.parse(readFileSync(join(directory, 'edit-load-proof.json')));
assert.equal(proof.minecraft, version); assert.equal(proof.actors, 32);
const hash = file => createHash('sha256').update(readFileSync(file)).digest('hex');
assert.equal(!!proof.matchedSlices, matchedSlices);
assert.equal(!!proof.sparkControl,sparkControl);
assert.equal(!!proof.movingGameplay,movingGameplay);
assert.equal(!!proof.budgetControl,budgetControl);
if (budgetControl) {
  assert.equal(hash(join(directory,'previous-companion.jar')),proof.hashes.previousCompanion);
  assert.equal(proof.hashes.previousCompanion,'7e40f70b3fab486358a929da73db4132d31bcd3d10e5bc90b99392dce3c4d956');
  assert.notEqual(proof.hashes.companion,proof.hashes.previousCompanion);
}
if (frozenStudy) {
  assert.equal(proof.repetitions, movingGameplay && !budgetControl ? 2 : 4); assert.deepEqual(proof.order, study.order);
  assert.match(proof.frozenDigest, /^[0-9a-f]{64}$/);
  assert.equal(new FrozenFixtureWorld(directory).digest(), proof.frozenDigest);
  assert.equal(hash(join(directory,'edit-diagnostics.jfc')), proof.hashes.jfrSettings);
  if (matchedSlices) for (const [key, name] of [['agent','bridge-slice-agent.jar'],['bootstrap','bridge-slice-bootstrap.jar']])
    assert.equal(hash(join(root, 'tools/bridge-slice-agent/build/libs', name)), proof.hashes[key]);
}
assert.equal(hash(join(directory, 'companion.jar')), proof.hashes.companion);
assert.equal(hash(join(directory, 'plugins/weso-paper-test-fixture.jar')), proof.hashes.fixture);
assert.equal(hash(join(directory, 'server.jar')), tuple.paperSha256);
assert.equal(createHash(tuple.worldeditAlgorithm).update(readFileSync(join(directory, 'plugins/worldedit.jar'))).digest('hex'), tuple.worldeditHash);
assert.equal(proof.runs.length, proof.repetitions); assert(proof.repetitions >= 1 && proof.repetitions <= 4);
const results = [];
function invoke(binary, args) {
  const p = spawnSync(binary, args, { windowsHide:true, encoding:'utf8', timeout:120000, maxBuffer:4*1024*1024 });
  assert(!p.error && p.status === 0, p.error?.message ?? p.stderr); return p.stdout;
}
let effectiveConfigurationDigest, configurationReaderLibraries;
if (profilerStudy) {
  assert.equal(version,'26.2','Profiler configuration reader is pinned to the exact 26.2 study');
  const libraries = profilerConfigLibraries(directory);
  configurationReaderLibraries = libraries.map(({path,sha256})=>({path,sha256}));
  const configurations = JSON.parse(invoke(join(javaHome,'bin/java.exe'),['-Xmx128m','-XX:ActiveProcessorCount=2',
    '--class-path',libraries.map(x=>x.file).join(delimiter),join(root,'tools/server-bridge-test/PaperProfilerConfiguration.java'),
    ...proof.runs.map(run=>join(directory,`edit-${run.round}-paper-global.effective.yml`))]));
  effectiveConfigurationDigest = verifyEffectiveProfilerConfigurations(configurations,movingGameplay ? proof.runs.map(()=>false) : [true,false,false,true]);
}
for (const [index, run] of proof.runs.entries()) {
  assert.equal(run.round, index+1);
  assert.deepEqual(JSON.parse(readFileSync(join(directory, `edit-${run.round}-result.json`))), run, 'Per-run evidence differs from final proof');
  const serverLog = readFileSync(join(directory, `edit-${run.round}.log`), 'utf8');
  assert(serverLog.includes('Stopping server'), 'Missing owned server stop');
  assert(!serverLog.includes('Error occurred while enabling') && !serverLog.includes('Command exception: /wesofixture'), 'Fixture/server failure hidden by summary');
  assert.equal([...serverLog.matchAll(/WESO_FIXTURE_OK edit-audit-end WesoLoad\d\d/g)].length, movingGameplay ? 32 : 8, 'Missing world/undo audits');
  const dispatch = [...serverLog.matchAll(/max wait nanos=(\d+), max slice nanos=(\d+), dispatch tasks=(\d+)/g)].at(-1);
  assert(dispatch, 'Missing raw dispatcher metrics'); assert.equal(Number(dispatch[3]), 0);
  if (frozenStudy) {
    const policy = editRunPolicy(study,run.round);
    verifyProfilerLog(serverLog,policy.sparkEnabled);
    if (matchedSlices) assert.equal(run.instrumented, policy.instrumented);
    if (movingGameplay) { assert.equal(run.movingGameplay,true); if (!budgetControl) assert.equal(run.instrumented,undefined); }
    if (budgetControl) {
      assert.equal(run.variant,policy.variant);
      assert.equal(run.companionHash,policy.variant==='previous' ? proof.hashes.previousCompanion : proof.hashes.companion);
    }
    if (profilerStudy) {
      assert.equal(run.sparkEnabled,policy.sparkEnabled);
      const input = join(directory,`edit-${run.round}-paper-global.input.json`);
      assert.equal(hash(input),run.profilerConfigSha256);
      assert.deepEqual(JSON.parse(readFileSync(input)),paperProfilerConfig(policy.sparkEnabled));
      assert(existsSync(join(directory,`edit-${run.round}-paper-global.effective.yml`)));
    }
    assert.equal(run.frozenDigest, proof.frozenDigest);
    assert.equal((serverLog.match(/WESO_SLICE_INSTRUMENTED /g) ?? []).length, run.instrumented ? 3 : 0);
    if (run.instrumented) for (const text of ['BridgeServer methods=3', 'WorldEditOverlayBridgePlugin methods=2', 'WorldEditOverlayBridgePlugin$SelectionHost methods=5'])
      assert(serverLog.includes(text), 'Exact wrapper plan not loaded');
  }
  if (version === '26.2') {
    assert.equal(run.transport.implementation, 'MCProtocolLib'); assert.equal(run.transport.version, javaLoadVersion);
    assert.equal(run.transport.protocol, 776);
    if (movingGameplay) assert(existsSync(join(directory,'load-client-artifacts')),'Moving study requires retained original helper');
    const artifactDirectory = existsSync(join(directory,'load-client-artifacts')) ? join(directory,'load-client-artifacts')
      : join(root, 'tools/bridge-load-clients/build/install/weso-bridge-load-clients/lib');
    assert.deepEqual(run.transport.artifacts.map(a => a.name).sort(), readdirSync(artifactDirectory).filter(name => name.endsWith('.jar')).sort());
    assert.equal(run.transport.artifacts.find(a => a.name === `protocol-${javaLoadVersion}.jar`)?.sha256, javaLoadSha256);
    for (const artifact of run.transport.artifacts) {
      assert.match(artifact.name, /^[A-Za-z0-9._+-]+\.jar$/);
      assert.equal(hash(join(artifactDirectory, artifact.name)), artifact.sha256);
    }
    const helperEvents = readFileSync(join(directory, `edit-${run.round}-clients.log`), 'utf8').split(/\r?\n/)
      .filter(line => line.startsWith('{')).map(line => JSON.parse(line));
    assert.equal(helperEvents.filter(e => e.type === 'ready' && e.detail === javaLoadVersion).length, 1);
    assert(!helperEvents.some(e => e.type === 'fault'), 'Hidden Java packet-decoder fault');
    for (const actor of run.recovered.actors) {
      assert.equal(helperEvents.filter(e => e.name === actor.name && e.type === 'joined').length, 1);
      assert.equal(helperEvents.filter(e => e.name === actor.name && e.type === 'ended').length, 1);
      assert.equal(helperEvents.filter(e => e.name === actor.name && e.type === 'payload' && e.channel === channel).length, actor.frames,
        'Raw Java payload count differs from actor evidence');
    }
    if (movingGameplay) {
      assert.deepEqual(movingEditHostCounters(serverLog),run.final,'Final proof differs from raw host console metrics');
      reconcileMovingEditReplies(helperEvents,run.final);
      assert.equal(run.actors.length,32); assert.equal(run.canonical.length,32); assert.equal(run.final.expired,0);
      assert.equal(run.final.applied,run.active.after.applied-run.active.before.applied);
      for (let i=0;i<32;i++) {
        const actor=run.actors[i],summary=run.recovered.actors[i],canonical=run.canonical[i],name=`WesoLoad${String(i).padStart(2,'0')}`;
        assert.equal(actor.name,name); assert.equal(canonical.name,name); assert.deepEqual(canonical.bounds,summary.bounds);
        const own=helperEvents.filter(e=>e.name===name),payloads=own.filter(e=>e.type==='payload');
        assert.equal(actor.frames,summary.frames);
        assert.equal(own.filter(e=>e.type==='teleport').length,actor.teleports); assert(actor.teleports>=3);
        const cui=payloads.filter(e=>e.channel==='worldedit:cui').map(e=>Buffer.from(e.data,'base64').toString());
        assert.equal(cui.length,actor.cui); assert(cui.length>0);
        const point=n=>cui.findLast(s=>s.startsWith(`p|${n}|`))?.split('|').slice(2,5).map(Number);
        assert.deepEqual([...point(0),...point(1)],canonical.bounds,'CUI and independent WorldEdit bounds differ');
        const rawBounds=[...serverLog.matchAll(new RegExp(`WESO_FIXTURE_BOUNDS ${name} ([^\\r\\n]+)`,'g'))];
        assert.equal(rawBounds.length,1); assert.deepEqual(rawBounds[0][1].split(',').map(Number),canonical.bounds);
        assert.equal([...serverLog.matchAll(new RegExp(`WESO_FIXTURE_OK edit-audit-start ${name}`,'g'))].length,1);
        assert.equal([...serverLog.matchAll(new RegExp(`WESO_FIXTURE_OK edit-audit-end ${name}`,'g'))].length,1);
        const replies=payloads.filter(e=>e.channel===channel).map(e=>({bytes:Buffer.from(e.data,'base64'),value:decode(Buffer.from(e.data,'base64'))}));
        if (i>=28) assert.equal(replies.length,0);
        else {
          const welcome=replies[0].value; assert.equal(welcome.opcode,2); assert.equal(welcome.minor,2);
          for (const reply of replies.slice(1)) assert.deepEqual(reply.value.epoch,welcome.epoch,'Foreign/stale reply epoch');
          if (i<8) {
            const success=replies.filter(r=>r.value.opcode===9 && r.value.commitStatus===0);
            assert.equal(success.length,run.active.actors[i].applied+(i===3?1:0));
            assert.deepEqual(success.at(-1).value.bounds,canonical.bounds);
            const ids=new Set(success.map(r=>r.value.id.toString()));
            assert.equal(success.length-ids.size,i===3?1:0,'Unexpected mutation replay');
            if (i===3) assert.deepEqual(success.at(-1).bytes,success.at(-2).bytes,'Cached replay not byte-identical');
            verifyMovingEditTargets(i,success.slice(0,run.active.actors[i].applied).map(r=>r.value.bounds),run.active.actors[i].editKinds);
          } else for (const reply of replies.slice(1)) assert.deepEqual(reply.value.bounds,canonical.bounds,'Foreign reader metadata');
        }
      }
    }
  } else if (run.transport) {
    assert.equal(run.transport.implementation, 'minecraft-protocol'); assert.equal(run.transport.version, '1.68.0');
    assert.equal(run.transport.protocol, version === '1.21.11' ? 774 : 775);
  }
  for (const key of ['staleConflict', 'foreignConflict', 'duplicateAppliedOnce', 'revokedDenied']) assert.equal(run[key], true);
  assert.equal(run.final.connections, 0); assert.equal(run.final.pending, 0); assert.equal(run.final.tasks, 0);
  const recording = join(directory, `edit-${run.round}.jfr`), args = [recording];
  const windows = ['idle','active','recovered'].map(phase => run[phase]);
  for (const [i, w] of windows.entries()) {
    (movingGameplay ? verifyMovingEditWindow : verifyEditLoadWindow)(w);
    const label = `edit-${run.round}-${['idle','active','recovered'][i]}`;
    const raw = JSON.parse(readFileSync(join(directory, `plugins/WesoBridgeTestFixture/load-${label}.json`)));
    assert.deepEqual(w.ticks, raw, 'Copied ticks differ from actual fixture data');
    assert.equal(raw.label,label);
    if (movingGameplay) assert.deepEqual(JSON.parse(readFileSync(join(directory,`${label}-window.json`))),{validated:true,window:w},'Retained enriched window differs');
    args.push(label, '400', raw.startedAt, raw.endedAt);
  }
  const loss = JSON.parse(invoke(join(javaHome,'bin/jfr.exe'), ['print','--json','--events','jdk.DataLoss',recording]));
  assert.equal(loss.recording.events.length,0,'Recorded JFR data loss');
  const rows = invoke(join(javaHome,'bin/java.exe'), ['-Xmx256m','-XX:ActiveProcessorCount=2',join(root,'tools/server-bridge-test/JfrTickTimeline.java'),...args])
    .trim().split(/\r?\n/).map(line=>JSON.parse(line));
  assert.equal(rows.length,15);
  for (const row of rows) {
    assert.equal(row.markerCount,400); assert(row.window>=0 && row.window<3);
    assert(Math.abs(row.paperMillis-windows[row.window].ticks.tickMillis[row.sample])<=0.000001,'JFR and Paper tick disagree');
  }
  const active = run.active, latencies = active.actors.filter(a=>a.role==='editor').flatMap(a=>a.latencies).sort((a,b)=>a-b);
  let gameplayProfile;
  if (movingGameplay) {
    const profiles=invoke(join(javaHome,'bin/java.exe'),['-Xmx256m','-XX:ActiveProcessorCount=2',join(root,'tools/server-bridge-test/JfrLoadSummary.java'),
      recording,...windows.flatMap(w=>[w.ticks.startedAt,w.ticks.endedAt])]).trim().split(/\r?\n/).map(JSON.parse);
    assert.equal(profiles.length,3); assert(profiles.every(p=>p.recordingSparkThreadDumps===0),'Unexpected sampler in moving edit study');
    gameplayProfile=profiles;
  }
  let slices;
  if (run.instrumented) {
    const readSlices = extra => invoke(join(javaHome,'bin/java.exe'), ['-Xmx256m','-XX:ActiveProcessorCount=2',
      join(root,'tools/server-bridge-test/JfrBridgeSlices.java'),recording,'edit',...extra]).trim().split(/\r?\n/).map(line=>JSON.parse(line));
    const [totals, ...intervals] = readSlices([]);
    const sparkDumps = verifyProfilerSamples(totals,editRunPolicy(study,run.round).sparkEnabled);
    const groups = Object.fromEntries(totals.groups.map(group => [group.operation, group]));
    assert.equal(groups.process.count, run.final.admitted);
    assert.equal(groups.read.count, run.final.reads);
    assert.equal(groups.capture.count, run.final.captures);
    assert.equal(groups.apply.count, run.final.commits);
    assert.equal(groups.send.count, run.final.replies);
    const queued = [...serverLog.matchAll(/Queue pending=\d+, peak=\d+, queued=(\d+), expired=(\d+), cancelled=(\d+)/g)].at(-1);
    assert(queued,'Missing raw ingress counters'); assert.equal(Number(queued[2]),0); assert.equal(Number(queued[3]),0);
    assert.equal(groups.enqueue.count,Number(queued[1])+run.final.throttled);
    assert.equal(groups.ingress.count,groups.enqueue.count);
    assert(Math.abs(totals.coreMaxMillis - Number(dispatch[2])/1e6) < .000001);
    const phases = windows.map((window,i) => {
      const [totals, ...intervals] = readSlices([`edit-${run.round}-${['idle','active','recovered'][i]}`,'400']);
      if (i !== 1) assert.equal(totals.calls,0,'Diagnostic calls occurred in a quiet measured window');
      return {phase:['idle','active','recovered'][i],totals,intervals};
    });
    slices = {totals,intervals,phases,...(sparkControl ? {sparkDumps} : {})};
  }
  results.push({ round:run.round, markers:1200, recordedDataLoss:0,
    ...(matchedSlices ? {instrumented:run.instrumented,slices} : {}),
    ...(budgetControl ? {variant:run.variant,companionHash:run.companionHash} : {}),
    applied:active.after.applied-active.before.applied, latencyP95:latencies[Math.ceil(latencies.length*.95)-1], latencyMax:latencies.at(-1),
    ...(movingGameplay ? {movingGameplay,gameplayProfile,windows:windows.map((w,i)=>({phase:['idle','active','recovered'][i],
      p95:w.ticks.p95,p99:w.ticks.p99,max:w.ticks.max,ticksOver50:w.ticks.tickMillis.filter(n=>n>50).length,
      acceptedMoves:w.ticks.gameplay.acceptedMoves,editBatches:w.ticks.gameplay.editBatches,changedBlocks:w.ticks.gameplay.changedBlocks}))} : {}),
    dispatcherMaxWaitNanos:Number(dispatch[1]), dispatcherMaxSliceNanos:Number(dispatch[2]), outliers:rows });
}
let budgetComparison;
if (budgetControl) {
  const arms=results.map(result=>{
    const drain=result.slices.phases.find(p=>p.phase==='active').totals.groups.find(g=>g.operation==='drain');
    assert(drain.count>0);
    const actors=proof.runs[result.round-1].active.actors;
    return {round:result.round,variant:result.variant,applied:result.applied,
      editorMinimum:Math.min(...actors.filter(a=>a.role==='editor').map(a=>a.applied)),
      readerMinimum:Math.min(...actors.filter(a=>a.role==='reader').map(a=>a.reads)),
      drain,overTargetFraction:drain.overSoftTarget/drain.count,
      latencyP95:result.latencyP95,latencyMax:result.latencyMax,maxWaitMillis:result.dispatcherMaxWaitNanos/1e6};
  });
  budgetComparison={arms,pairs:[[0,1],[3,2]].map(([old,next])=>({previousRound:arms[old].round,candidateRound:arms[next].round,
    appliedRatio:arms[next].applied/arms[old].applied,
    overTargetFractionDelta:arms[next].overTargetFraction-arms[old].overTargetFraction,
    drainP95DeltaMillis:arms[next].drain.p95Millis-arms[old].drain.p95Millis,
    latencyP95DeltaMillis:arms[next].latencyP95-arms[old].latencyP95}))};
}
writeFileSync(output,JSON.stringify({checkedAt:new Date().toISOString(),minecraft:version,hashes:proof.hashes,
  ...(budgetControl ? {budgetComparison} : {}),
  ...(frozenStudy ? {matchedSlices,frozenDigest:proof.frozenDigest,order:proof.order,
    ...(profilerStudy ? {sparkControl,movingGameplay,budgetControl,effectiveConfigurationDigest,configurationReaderLibraries} : {})} : {}),results},null,2));
console.log(`EDIT LOAD ANALYSIS PASS: ${results.length*1200} matched JFR ticks, exact raw summaries, no recorded data loss`);
