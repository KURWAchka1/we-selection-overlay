import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { readFileSync, readdirSync, writeFileSync } from 'node:fs';
import { delimiter, join, resolve } from 'node:path';
import { verifyLoadEvidence } from './load-evidence.mjs';
import { loadHost } from './gameplay-host.mjs';
import { javaLoadVersion, javaLoadSha256 } from './java-load-clients.mjs';
import { paperProfilerConfig, verifyProfilerLog } from './edit-diagnostic-policy.mjs';
import { profilerConfigLibraries, verifyEffectiveProfilerConfigurations } from './paper-profiler-config.mjs';
import { FrozenFixtureWorld } from './frozen-fixture-world.mjs';

const root = resolve(import.meta.dirname, '../..');
const mode = process.argv[2] ?? 'load';
assert(['load', 'baseline', 'restart', 'timeline', 'fair', 'gameplay'].includes(mode));
const host=loadHost(mode,process.argv.slice(3)),{version,withoutSpark,tuple}=host;
const directory = join(root,host.directory);
const proof = JSON.parse(readFileSync(join(directory, 'load-proof.json'), 'utf8'));
assert.equal(proof.minecraft,version);
assert.equal(proof.mode ?? 'load', mode);
assert.equal(!!proof.withoutSpark, withoutSpark, 'Profiler modes do not match');
const runs = [...new Set(proof.results.map(result => result.run))];
const hash=file=>createHash('sha256').update(readFileSync(file)).digest('hex');
let effectiveConfigurationDigest;
if (version === '26.2') {
  assert.equal(proof.players,32); assert.equal(proof.samples,400); assert.equal(proof.repetitions,2);
  assert.equal(new FrozenFixtureWorld(directory).digest(),proof.worldDigest);
  assert.equal(hash(join(directory,'server.jar')),tuple.paperSha256); assert.equal(proof.serverHash,tuple.paperSha256);
  assert.equal(createHash(tuple.worldeditAlgorithm).update(readFileSync(join(directory,'plugins/worldedit.jar'))).digest('hex'),tuple.worldeditHash);
  assert.equal(hash(join(directory,'plugins/worldedit.jar')),proof.worldEditHash);
  assert.equal(hash(join(directory,'companion.jar')),proof.companionHash);
  assert.equal(hash(join(directory,'plugins/weso-paper-test-fixture.jar')),proof.fixtureHash);
  assert.deepEqual(proof.runEvidence.map(r=>r.run),runs);
  for (const run of proof.runEvidence) {
    assert.equal(run.worldDigest,proof.worldDigest);
    assert.equal(run.recordingSettingsSha256,hash(join(directory,'gameplay.jfc')));
    const input=join(directory,`${run.run}-paper-global.input.json`);
    assert.equal(run.profilerInputSha256,hash(input));
    assert.deepEqual(JSON.parse(readFileSync(input)),paperProfilerConfig(false));
    const serverLog=readFileSync(join(directory,`${run.run}.log`),'utf8'),installed=run.run.endsWith('-installed');
    verifyProfilerLog(serverLog,false);
    assert.equal(serverLog.includes('Experimental read-only bridge enabled'),installed);
    assert(serverLog.includes('Loading Paper 26.2-133-ver/26.2@58e6e45'));
    const transport=run.transport,lib=join(directory,'load-client-artifacts');
    assert.equal(transport.implementation,'MCProtocolLib'); assert.equal(transport.version,javaLoadVersion); assert.equal(transport.protocol,776);
    assert.deepEqual(transport.artifacts.map(a=>a.name).sort(),readdirSync(lib).filter(n=>n.endsWith('.jar')).sort());
    assert.equal(transport.artifacts.find(a=>a.name===`protocol-${javaLoadVersion}.jar`)?.sha256,javaLoadSha256);
    for (const artifact of transport.artifacts) {
      assert.match(artifact.name,/^[A-Za-z0-9._+-]+\.jar$/); assert.equal(hash(join(lib,artifact.name)),artifact.sha256);
    }
    const events=readFileSync(join(directory,`${run.run}-clients.log`),'utf8').split(/\r?\n/).filter(line=>line.startsWith('{')).map(JSON.parse);
    assert.equal(events.filter(e=>e.type==='ready' && e.detail===javaLoadVersion).length,1);
    assert(!events.some(e=>e.type==='fault'),'Hidden moving client codec failure');
    assert.equal(run.actors.length,32);
    for (let i=0;i<32;i++) {
      const actor=run.actors[i]; assert.equal(actor.name,`WesoLoad${String(i).padStart(2,'0')}`);
      const own=events.filter(e=>e.name===actor.name);
      for (const type of ['joined','ended']) assert.equal(own.filter(e=>e.type===type).length,1);
      assert.equal(own.filter(e=>e.type==='teleport').length,actor.teleports); assert(actor.teleports>=2);
      assert.equal(own.filter(e=>e.type==='payload' && e.channel==='worldedit:cui').length,actor.cui); assert(actor.cui>0);
      assert.equal(own.filter(e=>e.type==='payload' && e.channel==='worldedit_selection_overlay:bridge').length,actor.bridgeFrames);
      if (i>=proof.bridgePlayers || run.run.endsWith('-absent')) assert.equal(actor.bridgeFrames,0);
    }
    if (installed) {
      assert(run.disconnected);
      for (const key of ['connections','pending','dispatchTasks','expired']) assert.equal(run.disconnected[key],0);
      const replies=events.filter(e=>e.type==='payload' && e.channel==='worldedit_selection_overlay:bridge');
      assert.equal(replies.length,run.disconnected.replies,'Lifetime host/wire reply counts differ');
      assert.equal(replies.reduce((n,e)=>n+Buffer.from(e.data,'base64').length,0),run.disconnected.bytes,'Lifetime host/wire bytes differ');
      assert.equal(run.disconnected.reads+proof.bridgePlayers,run.disconnected.replies,'Unexpected non-metadata reply');
      assert.equal(run.disconnected.admitted,run.disconnected.replies,'Admitted fixture request lost');
    } else assert.equal(run.disconnected,null);
  }
  const libraries=profilerConfigLibraries(directory);
  const parsed=spawnSync(join(proof.javaHome,'bin/java.exe'),['-Xmx128m','-XX:ActiveProcessorCount=2','-cp',libraries.map(a=>a.file).join(delimiter),
    join(import.meta.dirname,'PaperProfilerConfiguration.java'),...runs.map(run=>join(directory,`${run}-paper-global.effective.yml`))],
    {windowsHide:true,encoding:'utf8',timeout:60000,maxBuffer:1024*1024});
  assert.equal(parsed.status,0,parsed.stderr || parsed.error?.message);
  effectiveConfigurationDigest=verifyEffectiveProfilerConfigurations(JSON.parse(parsed.stdout),[false,false,false,false]);
}
if (mode === 'gameplay') {
  assert.deepEqual(runs, Array.from({ length: proof.repetitions }, (_, i) =>
    (i % 2 ? ['installed', 'absent'] : ['absent', 'installed']).map(kind => `${i + 1}-${kind}`)).flat());
  for (const run of runs) {
    const windows = proof.results.filter(row => row.run === run);
    assert.deepEqual(windows.map(w => w.label), ['silent', 'idle', 'one', 'many', 'flood', 'recovery']);
    for (const window of windows) {
      assert.equal(window.worldDigest, proof.worldDigest, 'Starting worlds differ');
      const raw = JSON.parse(readFileSync(join(directory, 'measurements', run, `${window.label}.json`), 'utf8'));
      verifyLoadEvidence(raw, window, { players: proof.players, samples: proof.samples,
        installed: run.endsWith('-installed'), baselineOnly: false, gameplay: true, bridgePlayers: proof.bridgePlayers });
      if (version === '26.2') {
        assert.deepEqual(raw.teleports,window.teleports); assert.equal(raw.teleports.length,32); assert(raw.teleports.every(n=>n===0));
        assert.deepEqual(raw.bridgeFrames,window.bridgeFrames); assert.equal(raw.bridgeFrames.length,32);
        if (['silent','idle','recovery'].includes(window.label)) assert(raw.bridgeFrames.every(n=>n===0));
      }
    }
  }
}
const profiles = [];
const tickOutliers = [];
for (const run of runs) {
  const windows = proof.results.filter(result => result.run === run);
  const args = ['-Xmx256m', '-XX:ActiveProcessorCount=2', join(import.meta.dirname, 'JfrLoadSummary.java'),
    join(directory, `${run}.jfr`), ...windows.flatMap(w => [w.startedAt, w.endedAt])];
  const result = spawnSync(join(proof.javaHome, 'bin/java.exe'), args,
    { encoding: 'utf8', windowsHide: true, timeout: 120000, maxBuffer: 1024 * 1024 });
  assert.equal(result.status, 0, result.stderr || result.error?.message);
  const data = result.stdout.trim().split(/\r?\n/).map(line => JSON.parse(line));
  assert.equal(data.length, windows.length);
  for (const window of data) {
    assert(Number.isInteger(window.window) && window.window >= 0 && window.window < windows.length);
    if (withoutSpark) assert.equal(window.recordingSparkThreadDumps, 0, 'Control recording contains spark sampling');
    profiles.push({ run, label: windows[window.window].label, ...window });
  }
  if (mode === 'timeline' || mode === 'fair' || mode === 'gameplay') {
    assert(windows.every(w => w.tickTimelineVersion === 1));
    const result = spawnSync(join(proof.javaHome, 'bin/java.exe'), ['-Xmx256m', '-XX:ActiveProcessorCount=2',
      join(import.meta.dirname, 'JfrTickTimeline.java'), join(directory, `${run}.jfr`),
      ...windows.flatMap(w => [w.label, String(w.samples), w.startedAt, w.endedAt])],
    { encoding: 'utf8', windowsHide: true, timeout: 120000, maxBuffer: 1024 * 1024 });
    assert.equal(result.status, 0, result.stderr || result.error?.message);
    const ticks = result.stdout.trim().split(/\r?\n/).map(line => JSON.parse(line));
    assert.equal(ticks.length, windows.reduce((n, w) => n + Math.min(5, w.samples), 0));
    for (const tick of ticks) {
      assert(Number.isInteger(tick.window) && tick.window >= 0 && tick.window < windows.length);
      assert.equal(tick.markerCount, windows[tick.window].samples);
      tickOutliers.push({ run, label: windows[tick.window].label, ...tick });
    }
  }
}
const rows = proof.results.map(row => ({ run: row.run, phase: row.label,
  p50: row.p50, p95: row.p95, p99: row.p99, max: row.max,
  ownerCpuMillisPerSecond: row.ownerCpuMillis / (row.wallMillis / 1000),
  ownerMiBPerSecond: row.ownerAllocatedBytes / 1024 / 1024 / (row.wallMillis / 1000),
  reads: row.bridge?.reads ?? 0, throttled: row.bridge?.throttled ?? 0, replyBytes: row.bridge?.bytes ?? 0,
  chunkLoads: row.chunkLoads, generatedChunks: row.generatedChunks,
  gcCount: row.gcCount, gcMillis: row.gcMillis,
  ...(row.gameplay ? { gameplay: row.gameplay } : {}),
  ...(row.queueAfter ? { queueAfter: row.queueAfter } : {}),
  ...(row.ordinaryReplyLatencies ? { ordinaryReplyLatencies: row.ordinaryReplyLatencies } : {}) }));
const matchedComparisons = [];
if (mode === 'gameplay') {
  for (const row of rows) {
    const raw = JSON.parse(readFileSync(join(directory, 'measurements', row.run, `${row.phase}.json`), 'utf8'));
    assert.equal(raw.validated, true); assert.equal(raw.samples, proof.samples);
    row.ticksOver50Millis = raw.tickMillis.filter(n => n > 50).length;
  }
  for (let round = 1; round <= proof.repetitions; round++) {
    const absent = rows.filter(row => row.run === `${round}-absent`);
    const installed = rows.filter(row => row.run === `${round}-installed`);
    assert.equal(absent.length, 6); assert.equal(installed.length, 6);
    for (const base of absent) {
      const candidate = installed.find(row => row.phase === base.phase);
      assert(candidate, 'Missing matched gameplay phase');
      for (const key of ['ticks', 'editBatches', 'changedBlocks', 'entities'])
        assert.equal(candidate.gameplay[key], base.gameplay[key], `Unequal world workload: ${key}`);
      matchedComparisons.push({ round, phase: base.phase,
        deltaP95Millis: candidate.p95 - base.p95, deltaP99Millis: candidate.p99 - base.p99,
        deltaOwnerCpuMillisPerSecond: candidate.ownerCpuMillisPerSecond - base.ownerCpuMillisPerSecond,
        deltaOwnerMiBPerSecond: candidate.ownerMiBPerSecond - base.ownerMiBPerSecond,
        absentTicksOver50Millis: base.ticksOver50Millis, installedTicksOver50Millis: candidate.ticksOver50Millis });
    }
  }
}
const report = { checkedAt: proof.checkedAt, minecraft:version, mode, withoutSpark, effectiveConfigurationDigest, worldDigest: proof.worldDigest, rows, profiles, tickOutliers,
  matchedComparisons,
  warning: 'Local bounded headless workloads on a shared laptop. Sampled allocation weights are estimates, not exact call costs. Frozen starting worlds reduce confounds but do not establish production performance acceptance or native-client lifecycle behavior.' };
writeFileSync(join(directory, 'load-analysis.json'), JSON.stringify(report, null, 2));
console.table(rows);
console.table(profiles.map(({ run, label, executionSamples, ownerSamples, bridgeSamples, bridgeAllocationSamples }) =>
  ({ run, label, executionSamples, ownerSamples, bridgeSamples, bridgeAllocationSamples })));
if (tickOutliers.length) console.table(tickOutliers.map(({ run, sample, paperMillis, bracketMillis,
  paperMinusBracketMillis, bracketCpuMillis, inferredGcPauseMillis, inferredBridgeSamples }) =>
  ({ run, sample, paperMillis, bracketMillis, paperMinusBracketMillis, bracketCpuMillis, inferredGcPauseMillis, inferredBridgeSamples })));
