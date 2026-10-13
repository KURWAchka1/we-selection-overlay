import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { existsSync, readFileSync, unlinkSync, writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { verifyEditLoadWindow } from './edit-load-window.mjs';

const javaHome = process.argv[2]; assert(javaHome, 'Explicit JDK home required');
const root = resolve(import.meta.dirname, '../..'), directory = join(root, 'build/bridge-edit-load-proof');
const output = join(directory, 'edit-load-analysis.json'); if (existsSync(output)) unlinkSync(output);
const proof = JSON.parse(readFileSync(join(directory, 'edit-load-proof.json')));
const hash = file => createHash('sha256').update(readFileSync(file)).digest('hex');
assert.equal(hash(join(directory, 'companion.jar')), proof.hashes.companion);
assert.equal(proof.runs.length, proof.repetitions); assert(proof.repetitions >= 1 && proof.repetitions <= 4);
const results = [];
function invoke(binary, args) {
  const p = spawnSync(binary, args, { windowsHide:true, encoding:'utf8', timeout:30000, maxBuffer:4*1024*1024 });
  assert(!p.error && p.status === 0, p.error?.message ?? p.stderr); return p.stdout;
}
for (const [index, run] of proof.runs.entries()) {
  assert.equal(run.round, index+1);
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
    applied:active.after.applied-active.before.applied, latencyP95:latencies[Math.ceil(latencies.length*.95)-1], latencyMax:latencies.at(-1), outliers:rows });
}
writeFileSync(output,JSON.stringify({checkedAt:new Date().toISOString(),hashes:proof.hashes,results},null,2));
console.log(`EDIT LOAD ANALYSIS PASS: ${results.length*1200} matched JFR ticks, exact raw summaries, no recorded data loss`);
