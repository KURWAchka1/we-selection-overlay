import assert from 'node:assert/strict';
import { spawn, spawnSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { copyFileSync, existsSync, readFileSync, unlinkSync, writeFileSync } from 'node:fs';
import { createConnection } from 'node:net';
import { setPriority, constants } from 'node:os';
import { resolve, relative, join } from 'node:path';
import { setTimeout as delay } from 'node:timers/promises';
import { observeOwnedServer } from './owned-server.mjs';
import { captureProcess as capture } from './native-process.mjs';

const [javaHome, serverDirectory, modVersion, powershell] = process.argv.slice(2);
const root = resolve(import.meta.dirname, '../..'), serverDir = resolve(serverDirectory);
assert.equal(relative(root, serverDir), join('build', 'bridge-native-proof'));
assert.match(modVersion, /^[A-Za-z0-9][A-Za-z0-9._+-]{0,79}$/);
assert(process.env.WESO_TEST_DESKTOP?.startsWith('WesoTest-'), 'Guarded private desktop required');
assert.match(readFileSync(join(serverDir, 'server.properties'), 'utf8'), /^server-ip=127\.0\.0\.1$/m);
assert.match(readFileSync(join(serverDir, 'server.properties'), 'utf8'), /^server-port=25579$/m);
assert.match(readFileSync(join(serverDir, 'server.properties'), 'utf8'), /^online-mode=false$/m);
assert.match(readFileSync(join(serverDir, 'eula.txt'), 'utf8'), /^eula=true\s*$/);
const evidence = [], checks = [];
let server, client, cancelled = false;
process.on('SIGINT', () => { cancelled = true; });
process.on('SIGTERM', () => { cancelled = true; });
const hash = path => createHash('sha256').update(readFileSync(path)).digest('hex');
const paperHash = '5ffef465eeeb5f2a3c23a24419d97c51afd7dbb4923ff42df9a3f58bba1ccfba';
assert.equal(hash(join(serverDir, 'server.jar')), paperHash);
assert.equal(hash(join(serverDir, 'plugins/worldedit.jar')), '0ee152b1be5dfb51500505e2bf5a8c9d66f09c7fa484bf9aea384d7e7b459b06');
const artifactPaths = Object.fromEntries(['fabric', 'neoforge'].map(loader => [loader,
  join(root, `WEaddon_${loader}_universal_1.21.11-26.2/build/libs/worldedit_selection_overlay-${modVersion}-${loader}-1.21.11-26.2.jar`)]));
const clientHashes = Object.fromEntries(Object.entries(artifactPaths).map(([loader, path]) => [loader, hash(path)]));
const companionHash = hash(join(serverDir, 'companion.jar'));
const fixtureHash = hash(join(serverDir, 'plugins/weso-paper-test-fixture.jar'));
const proofPath = join(serverDir, 'native-proof.json');
if (existsSync(proofPath)) unlinkSync(proofPath);

async function waitFor(predicate, label, timeout = 180000) {
  const end = Date.now() + timeout;
  while (!predicate()) {
    assert(!cancelled, `Cancelled: ${label}`);
    assert(!server?.exited, `Paper exited: ${label}`);
    assert(Date.now() < end, `Timeout: ${label}`);
    await delay(50);
  }
}
async function startServer(companion, label = 'absent') {
  assert.equal(hash(join(serverDir, 'companion.jar')), companionHash);
  assert.equal(hash(join(serverDir, 'plugins/weso-paper-test-fixture.jar')), fixtureHash);
  const destination = join(serverDir, 'plugins/worldedit_selection_overlay-bridge-paper-0.1.0-dev.jar');
  if (companion) copyFileSync(join(serverDir, 'companion.jar'), destination);
  else if (existsSync(destination)) unlinkSync(destination);
  const child = spawn(join(javaHome, 'bin/java.exe'), ['-Xms256m', '-Xmx1024m', '-XX:ActiveProcessorCount=2', '-Dweso.bridge.fixture=true', '-Dweso.bridge.editFixture=true', '-jar', 'server.jar', '--nogui'], { cwd: serverDir, windowsHide: true, stdio: ['pipe', 'pipe', 'pipe'] });
  server = capture(child, join(serverDir, companion ? `${label}-native-server.log` : 'absent-server.log'));
  server.lifecycle = observeOwnedServer(child);
  if (child.pid) setPriority(child.pid, constants.priority.PRIORITY_BELOW_NORMAL);
  await waitFor(() => server.lines.some(s => /Done \(/.test(s)), 'Paper startup');
  assert(!server.lines.some(s => /Error occurred while enabling/.test(s)), server.lines.slice(-25).join('\n'));
  assert.equal(server.lines.some(s => s.includes('Experimental read-only bridge enabled')), companion);
}
async function stopServer() {
  if (server) {
    try { await server.lifecycle.stop({ graceMillis: 20000 }); }
    finally { if (server.exited) await server.drained; }
  }
}
async function consoleAction(action, name) {
  const start = server.lines.length;
  server.child.stdin.write(`wesofixture ${action} ${name}\n`);
  await waitFor(() => server.lines.slice(start).some(s => s.includes(`WESO_FIXTURE_OK ${action} ${name}`)), action, 10000);
}
async function metrics() {
  const start = server.lines.length;
  server.child.stdin.write('wesobridge\n');
  await waitFor(() => server.lines.slice(start).some(s => /Queue pending=/.test(s)), 'metrics', 10000);
  const a = server.lines.slice(start).find(s => /connections=/.test(s));
  const b = server.lines.slice(start).find(s => /Selection metadata reads=/.test(s));
  const c = server.lines.slice(start).find(s => /Restart hints=/.test(s));
  const e = server.lines.slice(start).find(s => /Cuboid captures=/.test(s));
  const number = (line, key) => Number(line.match(new RegExp(`${key}=(\\d+)`))[1]);
  return { connections: number(a, 'connections'), reads: number(b, 'reads'), replies: number(b, 'replies'),
    restartHints: number(c, 'hints'), hintBytes: number(c, 'bytes'), editCaptures: number(e, 'captures'), editCommits: number(e, 'commits'), editsApplied: number(e, 'applied') };
}
async function runClient(loader, scenario) {
  assert.equal(hash(artifactPaths[loader]), clientHashes[loader], 'Client artifact changed during the matrix');
  const baseline = scenario === 'absent' ? null : await metrics();
  const control = join(serverDir, `${loader}-${scenario}-control.txt`);
  writeFileSync(control, '');
  const name = scenario === 'denied' ? 'WesoDenied' : 'WesoAllowed';
  const child = spawn(powershell, ['-NoProfile', '-NonInteractive', '-WindowStyle', 'Hidden', '-File', join(root, 'tools/test-command-matrix.ps1'), '-Java21', javaHome, '-Java25', javaHome, '-Only', `${loader}-1.21.11`, '-ModVersion', modVersion, '-NativeBridgeScenario', scenario, '-NativeBridgeControl', control, '-DesktopWorker'], { cwd: root, windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'] });
  client = capture(child, join(serverDir, `${loader}-${scenario}.log`));
  let index = 0;
  let channelReads, beforeShutdown;
  const end = Date.now() + 300000;
  while (!client.exited) {
    assert(Date.now() < end && !cancelled, 'Native client timeout/cancellation');
    assert(!server.exited, 'Server exited during native client run');
    for (; index < client.lines.length; index++) {
      const line = client.lines[index];
      assert(!line.includes('NATIVE PAPER BRIDGE FAIL:'), line);
      const phase = line.match(/NATIVE PAPER WAIT: (initial|quiet|editsetup|editquiet|editcancel|respawn|nether|overworld|channeloff|channelon|empty|seed|revoke|grant|reconnect|rejoin|rejoined|bridgeoff|bridgeon|restarted|disconnect)\s*$/)?.[1];
      if (!phase) continue;
      if (phase === 'initial' || phase === 'quiet') {
        if (baseline) assert.equal((await metrics()).reads, baseline.reads, 'Hello/idle scanned selection');
      } else if (phase === 'editsetup') {
        await consoleAction('editgrant', name); await consoleAction('allgrants', name); await consoleAction('seed-sync', name);
        await consoleAction('edit-audit-start', name);
      } else if (phase === 'editquiet' || phase === 'editcancel') {
        const m = await metrics();
        assert.equal(m.editCaptures, 1, 'Editor performed redundant capture'); assert.equal(m.editCommits, 2); assert.equal(m.editsApplied, 2);
        if (phase === 'editcancel') { await consoleAction('edit-audit-end', name); await consoleAction('seed-sync', name); }
      } else if (phase === 'respawn') {
        await consoleAction('kill', name);
      } else if (phase === 'channeloff') {
        channelReads = (await metrics()).reads;
        await consoleAction(phase, name);
      } else if (phase === 'channelon') {
        assert.equal((await metrics()).reads, channelReads, 'Unavailable channel performed reads');
        await consoleAction(phase, name);
      } else if (phase === 'rejoin') {
        assert.equal((await metrics()).connections, 0, 'First disconnect leaked peers');
      } else if (phase === 'rejoined') {
        assert.equal((await metrics()).reads - baseline.reads, 7, 'Reconnect performed unsolicited read');
      } else if (phase === 'bridgeoff') {
        beforeShutdown = await metrics();
        assert.equal(beforeShutdown.reads - baseline.reads, scenario === 'allowed' ? 8 : 0, 'Unexpected source work before shutdown');
        await consoleAction('bridgeoff', name);
        await consoleAction('channels', name);
      } else if (phase === 'bridgeon') {
        await consoleAction('bridgeon', name);
        await consoleAction('bridgeactive', name);
        const restarted = await metrics();
        assert.equal(restarted.restartHints, 1); assert.equal(restarted.hintBytes, 22);
        assert.equal(restarted.reads, 0, 'Restart performed unsolicited selection read');
        assert(restarted.connections <= (scenario === 'allowed' ? 1 : 0));
        assert(restarted.replies <= (scenario === 'allowed' ? 1 : 0));
      } else if (phase === 'restarted') {
        const restarted = await metrics();
        assert.equal(restarted.connections, 1); assert.equal(restarted.reads, 0); assert.equal(restarted.replies, 1);
      } else if (phase === 'disconnect' || phase === 'reconnect') {
        server.child.stdin.write(`kick ${name} Verification complete\n`);
      } else await consoleAction(phase, name);
      writeFileSync(control, phase);
    }
    await delay(50);
  }
  await client.drained;
  assert.equal(client.code, 0, client.lines.slice(-30).join('\n'));
  for (const marker of ['CURSOR ISOLATION GUARD PASS:', 'NATIVE PAPER CUI PASS:', `NATIVE PAPER BRIDGE PASS: ${scenario}`]) assert(client.lines.some(s => s.includes(marker)), `Missing ${marker}`);
  if (scenario === 'allowed') for (const context of ['same-world respawn', 'Nether transition', 'Overworld return', 'channel re-advertisement', 'same-process reconnect', 'negotiated plugin stop', 'silent plugin restart'])
    assert(client.lines.some(s => s.includes(`NATIVE PAPER CONTEXT PASS: ${context}`)), `Missing context recovery: ${context}`);
  if (scenario === 'allowed') assert(client.lines.some(s => s.includes('NATIVE PAPER EDIT PASS:')), 'Missing real native editing proof');
  if (scenario === 'denied') assert(client.lines.some(s => s.includes('NATIVE PAPER CONTEXT PASS: denied silent restart stays quiet')));
  assert(!client.lines.some(s => s.includes('NATIVE PAPER BRIDGE FAIL:')));
  assert.equal(hash(artifactPaths[loader]), clientHashes[loader], 'Client artifact changed during the run');
  await delay(250);
  if (scenario === 'allowed') {
    assert(beforeShutdown, 'Shutdown phase was not observed');
  }
  const final = baseline ? await metrics() : null;
  if (final) {
    assert.equal(final.connections, 0, 'Disconnect leaked peers');
    if (scenario === 'allowed') {
      assert.equal(final.reads, 1, 'Restarted source was not explicitly read exactly once');
      assert.equal(final.replies, 2, 'Restart duplicated negotiation or read');
    } else { assert.equal(final.reads, 0, 'Unexpected denied metadata reads'); assert.equal(final.replies, 0, 'Denied hint retried hello'); }
  }
  evidence.push({ loader, scenario, baseline, beforeShutdown, final });
  checks.push(`${loader} native 1.21.11 -> Paper: ${scenario}, CUI and disconnect cleanup`);
  console.log(`PASS: ${checks.at(-1)}`);
  client = null;
}
try {
  const inUse = await new Promise(resolve => {
    const socket = createConnection({ host: '127.0.0.1', port: 25579 });
    socket.once('connect', () => { socket.destroy(); resolve(true); });
    socket.once('error', () => resolve(false));
  });
  assert(!inUse, 'Port is already owned; do not touch another server');
  for (const loader of ['fabric', 'neoforge']) {
    await startServer(true, loader);
    for (const scenario of ['allowed', 'denied']) await runClient(loader, scenario);
    await stopServer();
  }
  await startServer(false);
  for (const loader of ['fabric', 'neoforge']) await runClient(loader, 'absent');
  await stopServer();
  writeFileSync(proofPath, JSON.stringify({ checkedAt: new Date().toISOString(), minecraft: '1.21.11', paper: server.lines.find(s => /This server is running Paper/.test(s)), paperHash, modVersion, companionHash, fixtureHash, clientWorldEdit: 'not installed; server-side WorldEdit 7.4.2 only', hashes: clientHashes, checks, evidence }, null, 2));
  console.log('NATIVE PAPER MATRIX PASS: six real Minecraft runs');
} catch (ex) { console.error(ex); process.exitCode = 1; }
finally {
  if (client && !client.exited && client.child.pid) {
    spawnSync('taskkill', ['/PID', String(client.child.pid), '/T', '/F'], { windowsHide: true });
    const end = Date.now() + 10000;
    while (!client.exited && Date.now() < end) await delay(50);
  }
  await stopServer();
}
