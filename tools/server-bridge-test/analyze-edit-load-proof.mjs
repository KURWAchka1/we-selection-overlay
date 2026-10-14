import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { existsSync, readFileSync, readdirSync, unlinkSync, writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { verifyEditLoadWindow } from './edit-load-window.mjs';
import { gestureTuple } from './gesture-tuples.mjs';
import { javaLoadVersion, javaLoadSha256 } from './java-load-clients.mjs';
import { channel } from './wire-codec.mjs';

const javaHome = process.argv[2]; assert(javaHome, 'Explicit JDK home required');
const version = process.argv[3] ?? '1.21.11', tuple = gestureTuple(version);
const root = resolve(import.meta.dirname, '../..'), directory = join(root, 'build/bridge-edit-load-proof', ...(version === '1.21.11' ? [] : [version]));
const output = join(directory, 'edit-load-analysis.json'); if (existsSync(output)) unlinkSync(output);
const proof = JSON.parse(readFileSync(join(directory, 'edit-load-proof.json')));
assert.equal(proof.minecraft, version); assert.equal(proof.actors, 32);
const hash = file => createHash('sha256').update(readFileSync(file)).digest('hex');
assert.equal(hash(join(directory, 'companion.jar')), proof.hashes.companion);
assert.equal(hash(join(directory, 'plugins/weso-paper-test-fixture.jar')), proof.hashes.fixture);
assert.equal(hash(join(directory, 'server.jar')), tuple.paperSha256);
assert.equal(createHash(tuple.worldeditAlgorithm).update(readFileSync(join(directory, 'plugins/worldedit.jar'))).digest('hex'), tuple.worldeditHash);
assert.equal(proof.runs.length, proof.repetitions); assert(proof.repetitions >= 1 && proof.repetitions <= 4);
const results = [];
function invoke(binary, args) {
  const p = spawnSync(binary, args, { windowsHide:true, encoding:'utf8', timeout:30000, maxBuffer:4*1024*1024 });
  assert(!p.error && p.status === 0, p.error?.message ?? p.stderr); return p.stdout;
}
for (const [index, run] of proof.runs.entries()) {
  assert.equal(run.round, index+1);
  assert.deepEqual(JSON.parse(readFileSync(join(directory, `edit-${run.round}-result.json`))), run, 'Per-run evidence differs from final proof');
  const serverLog = readFileSync(join(directory, `edit-${run.round}.log`), 'utf8');
  assert(serverLog.includes('Stopping server'), 'Missing owned server stop');
  assert(!serverLog.includes('Error occurred while enabling') && !serverLog.includes('Command exception: /wesofixture'), 'Fixture/server failure hidden by summary');
  assert.equal([...serverLog.matchAll(/WESO_FIXTURE_OK edit-audit-end WesoLoad\d\d/g)].length, 8, 'Missing world/undo audits');
  const dispatch = [...serverLog.matchAll(/max wait nanos=(\d+), max slice nanos=(\d+), dispatch tasks=(\d+)/g)].at(-1);
  assert(dispatch, 'Missing raw dispatcher metrics'); assert.equal(Number(dispatch[3]), 0);
  if (version === '26.2') {
    assert.equal(run.transport.implementation, 'MCProtocolLib'); assert.equal(run.transport.version, javaLoadVersion);
    assert.equal(run.transport.protocol, 776);
    assert.deepEqual(run.transport.artifacts.map(a => a.name).sort(), readdirSync(join(root, 'tools/bridge-load-clients/build/install/weso-bridge-load-clients/lib')).filter(name => name.endsWith('.jar')).sort());
    assert.equal(run.transport.artifacts.find(a => a.name === `protocol-${javaLoadVersion}.jar`)?.sha256, javaLoadSha256);
    for (const artifact of run.transport.artifacts) {
      assert.match(artifact.name, /^[A-Za-z0-9._+-]+\.jar$/);
      assert.equal(hash(join(root, 'tools/bridge-load-clients/build/install/weso-bridge-load-clients/lib', artifact.name)), artifact.sha256);
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
  } else if (run.transport) {
    assert.equal(run.transport.implementation, 'minecraft-protocol'); assert.equal(run.transport.version, '1.68.0');
    assert.equal(run.transport.protocol, version === '1.21.11' ? 774 : 775);
  }
  for (const key of ['staleConflict', 'foreignConflict', 'duplicateAppliedOnce', 'revokedDenied']) assert.equal(run[key], true);
  assert.equal(run.final.connections, 0); assert.equal(run.final.pending, 0); assert.equal(run.final.tasks, 0);
  const recording = join(directory, `edit-${run.round}.jfr`), args = [recording];
  const windows = ['idle','active','recovered'].map(phase => run[phase]);
  for (const [i, w] of windows.entries()) {
    verifyEditLoadWindow(w);
    const label = `edit-${run.round}-${['idle','active','recovered'][i]}`;
    const raw = JSON.parse(readFileSync(join(directory, `plugins/WesoBridgeTestFixture/load-${label}.json`)));
    assert.deepEqual(w.ticks, raw, 'Copied ticks differ from actual fixture data');
    assert.equal(raw.label,label);
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
  results.push({ round:run.round, markers:1200, recordedDataLoss:0,
    applied:active.after.applied-active.before.applied, latencyP95:latencies[Math.ceil(latencies.length*.95)-1], latencyMax:latencies.at(-1),
    dispatcherMaxWaitNanos:Number(dispatch[1]), dispatcherMaxSliceNanos:Number(dispatch[2]), outliers:rows });
}
writeFileSync(output,JSON.stringify({checkedAt:new Date().toISOString(),minecraft:version,hashes:proof.hashes,results},null,2));
console.log(`EDIT LOAD ANALYSIS PASS: ${results.length*1200} matched JFR ticks, exact raw summaries, no recorded data loss`);
