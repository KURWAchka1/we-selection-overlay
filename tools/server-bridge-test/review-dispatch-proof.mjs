import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { summarizeDispatchEvidence } from './dispatch-evidence.mjs';

const root = resolve(import.meta.dirname, '../..'), javaHome = process.argv[2], name = process.argv[3];
assert(javaHome && /^dispatch-review-\d{8}\.json$/.test(name) && process.argv.length === 4, 'Expected explicit JDK25 home and new dispatch-review-YYYYMMDD.json');
const directory = join(root, 'build/bridge-dispatch-sampling-proof/26.2');
const output = join(directory, name);
assert(!existsSync(output), 'Never overwrite completed review');
const hash = file => createHash('sha256').update(readFileSync(file)).digest('hex');
const original = join(directory, 'dispatch-cause-analysis.json');
const evidence = JSON.parse(readFileSync(original));
assert.equal(hash(join(directory, 'edit-load-proof.json')), evidence.proofHash);
assert.equal(hash(join(directory, 'edit-load-analysis.json')), evidence.analysisHash);
assert.deepEqual(evidence.results.map(r => r.round), [1, 2, 3, 4]);
const results = [];
for (const previous of evidence.results) {
  const path = join(directory, `edit-${previous.round}.jfr`);
  assert.equal(hash(path), previous.recordingHash);
  const invocation = spawnSync(join(javaHome, 'bin/java.exe'), ['-Xmx256m', '-XX:ActiveProcessorCount=2',
    join(import.meta.dirname, 'JfrDispatchDetails.java'), path, `edit-${previous.round}-active`, '400', 'dispatch'],
  { encoding: 'utf8', windowsHide: true, timeout: 60000, maxBuffer: 16 * 1024 * 1024 });
  assert.equal(invocation.status, 0, invocation.stderr || invocation.error?.message);
  const [totals, ...rows] = invocation.stdout.trim().split(/\r?\n/).map(line => JSON.parse(line));
  assert.deepEqual(totals, previous.totals, 'Original totals changed');
  assert.deepEqual(rows, previous.intervals, 'Original interval evidence changed');
  assert.equal(hash(path), previous.recordingHash);
  results.push({ round: previous.round, drain: previous.drain, totals, cohorts: summarizeDispatchEvidence(rows) });
}
writeFileSync(output, JSON.stringify({ checkedAt: new Date().toISOString(), originalAnalysisHash: hash(original),
  readerHash: hash(join(import.meta.dirname, 'JfrDispatchDetails.java')),
  summarizerHash: hash(join(import.meta.dirname, 'dispatch-evidence.mjs')), results }, null, 2) + '\n', { flag: 'wx' });
console.log(`DISPATCH REVIEW PASS: all ${results.reduce((sum, r) => sum + r.totals.roots, 0)} native roots equal original evidence; no recording modified`);
for (const result of results) console.log(JSON.stringify(result));
