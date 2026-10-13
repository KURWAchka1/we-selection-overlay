import assert from 'node:assert/strict';
import { spawn, spawnSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { copyFileSync, existsSync, readFileSync, readdirSync, statSync, unlinkSync, writeFileSync } from 'node:fs';
import { createServer } from 'node:net';
import { constants, setPriority } from 'node:os';
import { join, relative, resolve } from 'node:path';
import { setTimeout as delay } from 'node:timers/promises';
import { captureProcess } from './native-process.mjs';
import { observeOwnedServer } from './owned-server.mjs';
import { initialBounds, verifyGestureResult, verifyGestureMatrix } from './gesture-evidence.mjs';
import { gestureTuple } from './gesture-tuples.mjs';

const [javaHome, directory, modVersion, powershell, minecraft = '1.21.11'] = process.argv.slice(2);
const tuple = gestureTuple(minecraft);
const root = resolve(import.meta.dirname, '../..'), serverDir = resolve(directory);
assert.equal(relative(root, serverDir), join('build', 'bridge-native-gesture-proof', ...(minecraft === '1.21.11' ? [] : [minecraft])));
assert(process.env.WESO_TEST_DESKTOP?.startsWith('WesoTest-'));
assert.match(modVersion, /^[A-Za-z0-9][A-Za-z0-9._+-]{0,79}$/);
const properties = readFileSync(join(serverDir, 'server.properties'), 'utf8');
for (const line of ['server-ip=127.0.0.1', 'server-port=25579', 'online-mode=false']) assert(properties.split(/\r?\n/).includes(line));
assert.match(readFileSync(join(serverDir, 'eula.txt'), 'utf8'), /^eula=true\s*$/);
const hash = file => createHash('sha256').update(readFileSync(file)).digest('hex');
const hashes = { paper: hash(join(serverDir, 'server.jar')), worldedit: hash(join(serverDir, 'plugins/worldedit.jar')),
  companion: hash(join(serverDir, 'companion.jar')), fixture: hash(join(serverDir, 'plugins/weso-paper-test-fixture.jar')) };
assert.equal(hashes.paper, tuple.paperSha256);
assert.equal(createHash(tuple.worldeditAlgorithm).update(readFileSync(join(serverDir, 'plugins/worldedit.jar'))).digest('hex'), tuple.worldeditHash);
const artifacts = Object.fromEntries(['fabric', 'neoforge'].map(loader => [loader,
  join(root, `WEaddon_${loader}_universal_1.21.11-26.2/build/libs/worldedit_selection_overlay-${modVersion}-${loader}-1.21.11-26.2.jar`)]));
for (const [loader, file] of Object.entries(artifacts)) hashes[loader] = hash(file);
const proof = join(serverDir, 'native-gesture-proof.json'), failure = join(serverDir, 'native-gesture-failure.json');
for (const file of [proof, failure]) if (existsSync(file)) unlinkSync(file);
let server, client, cancelled = false;
const evidence = [];
process.on('SIGINT', () => { cancelled = true; }); process.on('SIGTERM', () => { cancelled = true; });
async function waitFor(predicate, label, timeout = 180000) {
  const end = Date.now() + timeout;
  while (!predicate()) { assert(!cancelled && !server?.exited && Date.now() < end, `Timeout/stop: ${label}`); await delay(25); }
}
async function startServer(loader) {
  const probe = createServer();
  await new Promise((resolve, reject) => { probe.once('error', reject); probe.listen(25579, '127.0.0.1', resolve); });
  await new Promise((resolve, reject) => probe.close(error => error ? reject(error) : resolve()));
  copyFileSync(join(serverDir, 'companion.jar'), join(serverDir, 'plugins/worldedit_selection_overlay-bridge-paper-0.1.0-dev.jar'));
  const child = spawn(join(javaHome, 'bin/java.exe'), ['-Xms256m', '-Xmx1024m', '-XX:ActiveProcessorCount=2', '-Dweso.bridge.fixture=true', '-Dweso.bridge.editFixture=true', '-jar', 'server.jar', '--nogui'], { cwd: serverDir, windowsHide: true, stdio: ['pipe', 'pipe', 'pipe'] });
  server = captureProcess(child, join(serverDir, `${loader}-server.log`)); server.lifecycle = observeOwnedServer(child);
  if (child.pid) setPriority(child.pid, constants.priority.PRIORITY_BELOW_NORMAL);
  await waitFor(() => server.lines.some(s => /Done \(/.test(s)), 'Paper startup');
  assert(!server.lines.some(s => /Error occurred while enabling/.test(s)));
  assert(!server.lines.some(s => s.includes('[WorldEditSelectionOverlayBridge]') && /Disabling|ERROR/.test(s)), 'Bridge failed closed at startup');
}
async function stopServer() { if (server) { try { await server.lifecycle.stop({ graceMillis: 20000 }); } finally { if (server.exited) await server.drained; } } }
async function action(name) {
  const start = server.lines.length; server.child.stdin.write(`wesofixture ${name} WesoAllowed\n`);
  await waitFor(() => server.lines.slice(start).some(s => s.includes(`WESO_FIXTURE_OK ${name} WesoAllowed`)), name, 10000);
  return server.lines.slice(start);
}
async function metrics() {
  const start = server.lines.length; server.child.stdin.write('wesobridge\n');
  await waitFor(() => server.lines.slice(start).some(s => /Queue pending=/.test(s)), 'metrics', 10000);
  const lines = server.lines.slice(start), find = text => lines.find(s => s.includes(text));
  const number = (line, key) => Number(line.match(new RegExp(`${key}=(\\d+)`))[1]);
  return { connections: number(find('connections='), 'connections'), captures: number(find('Cuboid captures='), 'captures'),
    commits: number(find('Cuboid captures='), 'commits'), applied: number(find('Cuboid captures='), 'applied'),
    pending: number(find('Queue pending='), 'pending'), tasks: number(find('Queue pending='), 'tasks') };
}
async function runClient(loader) {
  const started = Date.now();
  const baseline = await metrics(); assert.equal(baseline.connections, 0);
  const control = join(serverDir, `${loader}-gestures-control.txt`); writeFileSync(control, '');
  const child = spawn(powershell, ['-NoProfile', '-NonInteractive', '-WindowStyle', 'Hidden', '-File', join(root, 'tools/test-command-matrix.ps1'), '-Java21', javaHome, '-Java25', javaHome, '-Only', `${loader}-${tuple.family}`, '-ModVersion', modVersion, '-NativeBridgeScenario', 'gestures', '-NativeBridgeControl', control, '-NativeBridgeMinecraft', minecraft, '-DesktopWorker'], { cwd: root, windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'] });
  client = captureProcess(child, join(serverDir, `${loader}-gestures.log`));
  let lineIndex = 0, caseBaseline = baseline, result;
  const cases = [], end = Date.now() + 420000;
  while (!client.exited) {
    assert(Date.now() < end && !cancelled && !server.exited, 'Native gesture timeout/stop');
    for (; lineIndex < client.lines.length; lineIndex++) {
      const line = client.lines[lineIndex]; assert(!line.includes('NATIVE PAPER BRIDGE FAIL:'), line);
      const match = line.match(/NATIVE GESTURE RESULT: (\d+) ([\w-]+) (APPLIED|CANCELLED) (-?\d+(?:,-?\d+){5})\s*$/);
      if (match) {
        assert.equal(Number(match[1]), cases.length, 'Missing/out-of-order gesture');
        result = { index: Number(match[1]), label: match[2], status: match[3], bounds: match[4] };
      }
      const phase = line.match(/NATIVE GESTURE WAIT: (setup|case-\d+|draft-\d+|released-\d+|result-\d+|stale|disconnect)\s*$/)?.[1];
      if (!phase) continue;
      if (phase === 'setup') {
        assert.equal((await metrics()).captures, baseline.captures);
        await action('allgrants'); await action('editgrant'); await action('edit-pose'); await action('seed-sync'); await action('edit-audit-start');
      } else if (phase.startsWith('case-')) {
        assert.equal(Number(phase.slice(5)), cases.length);
        if (cases.length > 0) await action('seed-sync');
      } else if (phase.startsWith('draft-') || phase.startsWith('released-')) {
        const m = await metrics(); assert.equal(m.commits, caseBaseline.commits, 'Unconfirmed mouse gesture committed');
        assert.equal(m.applied, caseBaseline.applied); assert.equal(m.captures - caseBaseline.captures, 1);
        const lines = await action('edit-bounds'); assert(lines.some(s => s.endsWith(`WESO_FIXTURE_BOUNDS WesoAllowed ${initialBounds}`)));
      } else if (phase === 'stale') await action('seed-sync');
      else if (phase.startsWith('result-')) {
        assert(result && phase === `result-${result.index}`);
        const lines = await action('edit-bounds');
        const actual = lines.find(s => s.includes('WESO_FIXTURE_BOUNDS WesoAllowed '))?.split('WesoAllowed ')[1];
        const observed = await metrics(); verifyGestureResult(result, caseBaseline, observed, actual);
        cases.push({ ...result, baseline: caseBaseline, observed, serverBounds: actual }); caseBaseline = observed; result = null;
        console.log(`NATIVE REMOTE GESTURE PASS: ${loader} ${cases.at(-1).label}`);
      } else if (phase === 'disconnect') {
        verifyGestureMatrix(cases); await action('edit-audit-end'); server.child.stdin.write('kick WesoAllowed Verification complete\n');
      }
      writeFileSync(control, phase);
    }
    await delay(25);
  }
  await client.drained; assert.equal(client.code, 0, client.lines.slice(-30).join('\n'));
  for (const marker of ['CURSOR ISOLATION GUARD PASS:', 'NATIVE PAPER CUI PASS: gestures', 'NATIVE EDIT GESTURES PASS:', 'NATIVE PAPER BRIDGE PASS: gestures']) assert(client.lines.some(s => s.includes(marker)), `Missing ${marker}`);
  verifyGestureMatrix(cases);
  const final = await metrics(); assert.equal(final.connections, 0); assert.equal(final.pending, 0); assert.equal(final.tasks, 0);
  assert.equal(final.commits - baseline.commits, 23); assert.equal(final.applied - baseline.applied, 23); assert.equal(final.captures - baseline.captures, 29);
  assert.equal(hash(artifacts[loader]), hashes[loader]); assert.equal(hash(join(serverDir, 'companion.jar')), hashes.companion);
  const fabricModule = tuple.family === '26.1' ? 'WEaddon_lightposition' : `WEaddon_fabric_${tuple.family}`;
  const runDirectory = 'run-native-bridge-gestures' + (minecraft === '1.21.11' ? '' : `-${tuple.family}`);
  const screenshotRoot = join(root, loader === 'fabric' ? fabricModule : 'WEaddon_neoforge_universal_1.21.11-26.2', runDirectory, 'screenshots');
  const screenshots = readdirSync(screenshotRoot).filter(name => name.endsWith('.png') && statSync(join(screenshotRoot, name)).mtimeMs >= started).sort().map(name => {
    const file = join(screenshotRoot, name), bytes = readFileSync(file);
    assert.equal(bytes.subarray(0, 8).toString('hex'), '89504e470d0a1a0a');
    assert.equal(bytes.readUInt32BE(16), 1280); assert.equal(bytes.readUInt32BE(20), 720);
    return { file: relative(root, file).replaceAll('\\', '/'), sha256: hash(file) };
  });
  assert.equal(screenshots.length, 2, 'Missing fresh corner/XYZ screenshots');
  evidence.push({ loader, baseline, final, cases, screenshots }); client = null;
}
try {
  for (const loader of ['fabric', 'neoforge']) { await startServer(loader); await runClient(loader); await stopServer(); }
  const revision = spawnSync('git', ['rev-parse', 'HEAD'], { cwd: root, encoding: 'utf8', windowsHide: true });
  assert.equal(revision.status, 0);
  writeFileSync(proof, JSON.stringify({ checkedAt: new Date().toISOString(), baseline: revision.stdout.trim(), minecraft, tuple, modVersion, hashes,
    input: 'In-JVM pinned Minecraft callbacks; private desktop and cursor agent; no system input', evidence }, null, 2));
  console.log(`NATIVE EDIT GESTURE MATRIX PASS: ${minecraft}, both guarded clients, 56 cases`);
} catch (error) { writeFileSync(failure, JSON.stringify({ checkedAt: new Date().toISOString(), error: String(error), hashes, evidence }, null, 2)); console.error(error); process.exitCode = 1; }
finally {
  try {
    if (client && !client.exited && client.child.pid) {
      spawnSync('taskkill', ['/PID', String(client.child.pid), '/T', '/F'], { windowsHide: true });
      const end = Date.now() + 10000; while (!client.exited && Date.now() < end) await delay(25);
      assert(client.exited, 'Owned client did not terminate');
    }
  } finally { await stopServer(); }
}
