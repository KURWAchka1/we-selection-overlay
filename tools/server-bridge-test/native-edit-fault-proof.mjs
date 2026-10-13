import assert from 'node:assert/strict';
import { spawn, spawnSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { copyFileSync, existsSync, readFileSync, unlinkSync, writeFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { createServer } from 'node:net';
import { constants, setPriority } from 'node:os';
import { join, relative, resolve } from 'node:path';
import { setTimeout as delay } from 'node:timers/promises';
import { FaultRelay } from './fault-relay.mjs';
import { captureProcess } from './native-process.mjs';
import { observeOwnedServer } from './owned-server.mjs';
import { channel, decode } from './wire-codec.mjs';

const [javaHome, directory, modVersion, powershell] = process.argv.slice(2);
const root = resolve(import.meta.dirname, '../..'), serverDir = resolve(directory);
assert.equal(relative(root, serverDir), join('build', 'bridge-native-fault-proof'));
assert(process.env.WESO_TEST_DESKTOP?.startsWith('WesoTest-'));
assert.match(modVersion, /^[A-Za-z0-9][A-Za-z0-9._+-]{0,79}$/);
const properties = readFileSync(join(serverDir, 'server.properties'), 'utf8');
for (const line of ['server-ip=127.0.0.1', 'server-port=25580', 'online-mode=false', 'network-compression-threshold=-1']) assert(properties.split(/\r?\n/).includes(line));
assert.match(readFileSync(join(serverDir, 'eula.txt'), 'utf8'), /^eula=true\s*$/);
const require = createRequire(join(root, 'build/bridge-wire-deps/package.json'));
const minecraft = require('minecraft-protocol');
assert.equal(require('minecraft-protocol/package.json').version, '1.68.0');
const prefixes = Object.fromEntries([['toServer', false], ['toClient', true]].map(([key, isServer]) => [key,
  minecraft.createSerializer({ state: minecraft.states.PLAY, isServer, version: '1.21.11' })
    .createPacketBuffer({ name: 'custom_payload', params: { channel, data: Buffer.alloc(0) } })]));
const hash = file => createHash('sha256').update(readFileSync(file)).digest('hex');
assert.equal(hash(join(serverDir, 'server.jar')), '5ffef465eeeb5f2a3c23a24419d97c51afd7dbb4923ff42df9a3f58bba1ccfba');
assert.equal(hash(join(serverDir, 'plugins/worldedit.jar')), '0ee152b1be5dfb51500505e2bf5a8c9d66f09c7fa484bf9aea384d7e7b459b06');
const hashes = { companion: hash(join(serverDir, 'companion.jar')), fixture: hash(join(serverDir, 'plugins/weso-paper-test-fixture.jar')) };
const artifacts = Object.fromEntries(['fabric', 'neoforge'].map(loader => [loader,
  join(root, `WEaddon_${loader}_universal_1.21.11-26.2/build/libs/worldedit_selection_overlay-${modVersion}-${loader}-1.21.11-26.2.jar`)]));
for (const [loader, file] of Object.entries(artifacts)) hashes[loader] = hash(file);
const proof = join(serverDir, 'native-fault-proof.json'), failure = join(serverDir, 'native-fault-failure.json');
for (const file of [proof, failure]) if (existsSync(file)) unlinkSync(file);
let server, client, relay, cancelled = false;
const evidence = [];
process.on('SIGINT', () => { cancelled = true; }); process.on('SIGTERM', () => { cancelled = true; });
async function waitFor(predicate, label, timeout = 180000) {
  const end = Date.now() + timeout;
  while (!predicate()) { assert(!cancelled && !server?.exited && Date.now() < end, `Timeout/stop: ${label}`); await delay(25); }
}
async function freePort(port) {
  const probe = createServer();
  await new Promise((resolve, reject) => { probe.once('error', reject); probe.listen(port, '127.0.0.1', resolve); });
  await new Promise((resolve, reject) => probe.close(error => error ? reject(error) : resolve()));
}
async function startServer(loader) {
  await freePort(25580); await freePort(25579);
  copyFileSync(join(serverDir, 'companion.jar'), join(serverDir, 'plugins/worldedit_selection_overlay-bridge-paper-0.1.0-dev.jar'));
  const child = spawn(join(javaHome, 'bin/java.exe'), ['-Xms256m', '-Xmx1024m', '-XX:ActiveProcessorCount=2', '-Dweso.bridge.fixture=true', '-Dweso.bridge.editFixture=true', '-jar', 'server.jar', '--nogui'], { cwd: serverDir, windowsHide: true, stdio: ['pipe', 'pipe', 'pipe'] });
  server = captureProcess(child, join(serverDir, `${loader}-server.log`)); server.lifecycle = observeOwnedServer(child);
  if (child.pid) setPriority(child.pid, constants.priority.PRIORITY_BELOW_NORMAL);
  await waitFor(() => server.lines.some(s => /Done \(/.test(s)), 'Paper startup');
  assert(!server.lines.some(s => /Error occurred while enabling/.test(s)));
}
async function stopServer() { if (server) { try { await server.lifecycle.stop({ graceMillis: 20000 }); } finally { if (server.exited) await server.drained; } } }
async function action(name) {
  const start = server.lines.length; server.child.stdin.write(`wesofixture ${name} WesoAllowed\n`);
  await waitFor(() => server.lines.slice(start).some(s => s.includes(`WESO_FIXTURE_OK ${name} WesoAllowed`)), name, 10000);
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
async function runClient(loader, mode) {
  const scenario = `fault-${mode}`, control = join(serverDir, `${loader}-${scenario}-control.txt`);
  writeFileSync(control, '');
  const baseline = await metrics(); assert.equal(baseline.connections, 0);
  relay = new FaultRelay(prefixes); await relay.listen();
  const child = spawn(powershell, ['-NoProfile', '-NonInteractive', '-WindowStyle', 'Hidden', '-File', join(root, 'tools/test-command-matrix.ps1'), '-Java21', javaHome, '-Java25', javaHome, '-Only', `${loader}-1.21.11`, '-ModVersion', modVersion, '-NativeBridgeScenario', scenario, '-NativeBridgeControl', control, '-DesktopWorker'], { cwd: root, windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'] });
  client = captureProcess(child, join(serverDir, `${loader}-${scenario}.log`));
  let index = 0, revoked = false, pendingObserved = false, verified, heldReply;
  const end = Date.now() + 300000;
  while (!client.exited) {
    assert(Date.now() < end && !cancelled && !server.exited, 'Native fault timeout/stop'); assert.deepEqual(relay.failures, []);
    if (mode === 'revoked' && relay.held && !revoked) { await action('editrevoke'); revoked = true; relay.release(); }
    for (; index < client.lines.length; index++) {
      const line = client.lines[index]; assert(!line.includes('NATIVE PAPER BRIDGE FAIL:'), line);
      if (line.includes('NATIVE EDIT PENDING:')) pendingObserved = true;
      const phase = line.match(/NATIVE EDIT WAIT: (setup|arm|timeout|verified|disconnect)\s*$/)?.[1];
      if (!phase) continue;
      if (phase === 'setup') {
        assert.equal((await metrics()).captures, baseline.captures, 'Idle handshake captured selection');
        await action('allgrants'); await action('editgrant'); await action('seed-sync'); await action('edit-audit-start');
      } else if (phase === 'arm') {
        const m = await metrics(); assert.equal(m.captures - baseline.captures, 1); assert.equal(m.commits, baseline.commits);
        relay.arm(mode);
      } else if (phase === 'timeout') {
        assert.equal(mode, 'late'); assert(relay.held, 'No intercepted acknowledgment');
        const reply = decode(relay.held.frame.payload.subarray(prefixes.toClient.length));
        assert.equal(reply.commitStatus, 0);
        heldReply = { requestId: reply.id.toString(), commitStatus: reply.commitStatus, bounds: reply.bounds };
        relay.release();
      } else if (phase === 'verified' || phase === 'disconnect') {
        const m = await metrics(), expected = mode === 'revoked' ? 0 : 1;
        assert.equal(m.commits - baseline.commits, expected); assert.equal(m.applied - baseline.applied, expected);
        assert.equal(m.captures - baseline.captures, mode === 'late' ? 2 : 1, 'Unexpected recapture/retry');
        assert.equal(m.pending, 0); assert.equal(m.tasks, 0);
        if (phase === 'verified') verified = m;
        else { await action('edit-audit-end'); server.child.stdin.write('kick WesoAllowed Verification complete\n'); }
      }
      writeFileSync(control, phase);
    }
    await delay(25);
  }
  await client.drained; assert.equal(client.code, 0, client.lines.slice(-25).join('\n'));
  for (const marker of ['CURSOR ISOLATION GUARD PASS:', `NATIVE EDIT FAULT PASS: ${scenario}`, `NATIVE PAPER BRIDGE PASS: ${scenario}`]) assert(client.lines.some(s => s.includes(marker)), `Missing ${marker}`);
  assert(pendingObserved && verified); assert.equal(relay.stats.commits, 1); assert.equal(relay.stats.replies, 1);
  assert.equal(relay.stats.held, mode === 'slow' ? 0 : 1); assert.equal(relay.stats.released, mode === 'slow' ? 0 : 1);
  if (mode === 'slow') assert(relay.stats.delayedFrames > 0);
  const final = await metrics(); assert.equal(final.connections, 0); assert.equal(final.pending, 0); assert.equal(final.tasks, 0);
  assert.equal(hash(artifacts[loader]), hashes[loader]); assert.equal(hash(join(serverDir, 'companion.jar')), hashes.companion);
  evidence.push({ loader, scenario, baseline, verified, final, relay: { ...relay.stats }, pendingObserved, heldReply });
  await relay.close(); relay = null; client = null;
  console.log(`NATIVE EDIT FAULT PASS: ${loader} ${mode}`);
}
try {
  for (const loader of ['fabric', 'neoforge']) {
    await startServer(loader);
    for (const mode of ['slow', 'late', 'revoked']) await runClient(loader, mode);
    await stopServer();
  }
  writeFileSync(proof, JSON.stringify({ checkedAt: new Date().toISOString(), baseline: 'bdc373c', minecraft: '1.21.11', modVersion, hashes, oneWayDelayMillis: 400, evidence }, null, 2));
  console.log('NATIVE EDIT FAULT MATRIX PASS: six guarded real clients');
} catch (error) { writeFileSync(failure, JSON.stringify({ checkedAt: new Date().toISOString(), error: String(error), hashes, evidence, relay: relay?.stats }, null, 2)); console.error(error); process.exitCode = 1; }
finally {
  try {
    if (client && !client.exited && client.child.pid) {
      spawnSync('taskkill', ['/PID', String(client.child.pid), '/T', '/F'], { windowsHide: true });
      const end = Date.now() + 10000;
      while (!client.exited && Date.now() < end) await delay(25);
      assert(client.exited, 'Owned client did not terminate');
    }
  } finally { if (relay) await relay.close(); await stopServer(); }
}
