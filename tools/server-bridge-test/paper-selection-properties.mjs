import assert from 'node:assert/strict';
import { spawn, spawnSync } from 'node:child_process';
import { createHash, randomBytes } from 'node:crypto';
import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { createServer } from 'node:net';
import { constants, getPriority, setPriority } from 'node:os';
import { join, relative, resolve } from 'node:path';
import { setTimeout as delay } from 'node:timers/promises';
import { channel, hello, editRead, editCommit, decode } from './wire-codec.mjs';
import { gestureTuple } from './gesture-tuples.mjs';
import { captureProcess } from './native-process.mjs';
import { observeOwnedServer, observedOwnedDepartures } from './owned-server.mjs';
import { startJavaLoadClients } from './java-load-clients.mjs';
import { orderedPoints, propertyBase, selectionPropertyCases, verifyPropertyRows } from './selection-property-cases.mjs';

const [javaHome, directory, version] = process.argv.slice(2);
const tuple = gestureTuple(version), root = resolve(import.meta.dirname, '../..'), serverDir = resolve(directory);
assert.equal(relative(root, serverDir), join('build', 'bridge-selection-properties', version), 'Only the isolated property fixture is allowed');
for (const file of ['property-proof.json', 'property-failure.json', 'property-server.log', 'property-helper.log'])
  assert(!existsSync(join(serverDir, file)), 'Preserve previous property attempts before running');
const properties = readFileSync(join(serverDir, 'server.properties'), 'utf8').split(/\r?\n/);
for (const value of ['server-ip=127.0.0.1', 'server-port=25579', 'online-mode=false', 'max-players=4']) assert(properties.includes(value));
assert.match(readFileSync(join(serverDir, 'eula.txt'), 'utf8'), /^eula=true\s*$/);
const hash = (file, algorithm = 'sha256') => createHash(algorithm).update(readFileSync(file)).digest('hex');
const artifacts = { paper: hash(join(serverDir, 'server.jar')), worldedit: hash(join(serverDir, 'plugins/worldedit.jar')),
  companion: hash(join(serverDir, 'plugins/worldedit_selection_overlay-bridge-paper-0.1.0-dev.jar')),
  fixture: hash(join(serverDir, 'plugins/weso-paper-test-fixture.jar')) };
assert.equal(artifacts.paper, tuple.paperSha256);
assert.equal(hash(join(serverDir, 'plugins/worldedit.jar'), tuple.worldeditAlgorithm), tuple.worldeditHash);
const revision = spawnSync('git', ['rev-parse', 'HEAD'], { cwd: root, windowsHide: true, encoding: 'utf8' });
assert.equal(revision.status, 0); assert.match(revision.stdout.trim(), /^[a-f0-9]{40}$/);
const javaVersion = spawnSync(join(javaHome, 'bin/java.exe'), ['-version'], { windowsHide: true, encoding: 'utf8' });
assert.equal(javaVersion.status, 0);
assert.match(javaVersion.stderr, new RegExp(`version "${version === '1.21.11' ? '21' : '25'}\\.`));
const harness = Object.fromEntries(['paper-selection-properties.mjs', 'selection-property-cases.mjs', 'wire-codec.mjs',
  'owned-server.mjs', 'native-process.mjs', 'java-load-clients.mjs', 'gesture-tuples.mjs'].map(file => [file, hash(join(import.meta.dirname, file))]));
const manifest = { minecraft: version, worldedit: tuple.worldedit, paperBuild: tuple.paperBuild, artifacts, harness,
  revision: revision.stdout.trim(), java: javaVersion.stderr.trim(), nativeGameplay: false, performanceStudy: false };
const port = createServer();
await new Promise((accept, reject) => { port.once('error', reject); port.listen(25579, '127.0.0.1', accept); });
await new Promise((accept, reject) => port.close(error => error ? reject(error) : accept()));

const child = spawn(join(javaHome, 'bin/java.exe'), ['-Xms256m', '-Xmx1024m', '-XX:ActiveProcessorCount=2',
  '-Dweso.bridge.fixture=true', '-Dweso.bridge.editFixture=true', '-jar', 'server.jar', '--nogui'],
{ cwd: serverDir, windowsHide: true, stdio: ['pipe', 'pipe', 'pipe'] });
const server = captureProcess(child, join(serverDir, 'property-server.log')), lifecycle = observeOwnedServer(child);
const rows = [], frames = [], cui = [];
let transport, client, fault, closing = false, ended = false, cancelled = false, epoch, requestId = 0, lastSend = 0, result;
process.on('SIGINT', () => { cancelled = true; }); process.on('SIGTERM', () => { cancelled = true; });
async function waitFor(predicate, label, timeout = 15000) {
  const deadline = Date.now() + timeout;
  while (!predicate()) {
    if (cancelled || fault || transport?.fault || server.exited) throw new Error(`Fixture stopped during ${label}`, { cause: fault ?? transport?.fault });
    assert(Date.now() < deadline, `Timeout: ${label}`);
    await delay(20);
  }
}
async function action(operation) {
  assert.match(operation, /^(editgrant|allgrants|expandonly|contractonly|shiftonly|edit-state|edit-pose|edit-audit-start|edit-audit-end|orientation-(ext-)?[0-7])$/);
  const start = server.lines.length;
  child.stdin.write(`wesofixture ${operation} WesoLoad00\n`);
  await waitFor(() => server.lines.slice(start).some(s => s.includes(`WESO_FIXTURE_OK ${operation} WesoLoad00`)), operation);
  return server.lines.slice(start);
}
async function canonical() {
  const lines = await action('edit-state');
  const match = lines.map(s => s.match(/WESO_FIXTURE_EDIT_STATE WesoLoad00 (\w+) (-?\d+(?:,-?\d+){5})/)).find(Boolean);
  assert(match, 'Missing canonical server points');
  return { selector: match[1], points: match[2].split(',').map(Number) };
}
async function exchange(bytes) {
  await delay(Math.max(0, 300 - (Date.now() - lastSend)));
  const start = frames.length; lastSend = Date.now();
  client.write('custom_payload', { channel, data: bytes });
  await waitFor(() => frames.length > start || ended, `reply opcode ${bytes[5]}`);
  assert(!ended, 'Client disconnected'); assert.equal(frames.length, start + 1, 'Unsolicited/duplicate reply');
  return { bytes: frames[start], value: decode(frames[start]) };
}
async function metrics() {
  const start = server.lines.length; child.stdin.write('wesobridge\n');
  await waitFor(() => server.lines.slice(start).some(s => /Queue pending=/.test(s)), 'resource counters');
  const lines = server.lines.slice(start).join('\n');
  const number = key => { const match = lines.match(new RegExp(`\\b${key}=(\\d+)`)); assert(match, `Missing counter ${key}`); return Number(match[1]); };
  return Object.fromEntries(['connections', 'reads', 'captures', 'commits', 'applied', 'pending', 'tasks', 'throttled', 'malformed', 'replies'].map(key => [key, number(key)]));
}
try {
  setPriority(child.pid, constants.priority.PRIORITY_BELOW_NORMAL);
  assert.equal(getPriority(child.pid), constants.priority.PRIORITY_BELOW_NORMAL);
  await waitFor(() => server.lines.some(s => /Done \(/.test(s)), 'Paper startup', 120000);
  assert(server.lines.some(s => s.includes('WorldEditSelectionOverlayBridge') && s.includes('Enabling')), 'Companion not enabled');
  if (version === '26.2') {
    transport = startJavaLoadClients(javaHome, join(serverDir, 'property-helper.log'));
    await waitFor(() => transport.ready, 'pinned 26.2 transport', 30000);
  } else {
    const require = createRequire(join(root, 'build/bridge-wire-deps/package.json'));
    assert.equal(require('minecraft-protocol/package.json').version, '1.68.0'); transport = require('minecraft-protocol');
  }
  client = transport.createClient({ host: '127.0.0.1', port: 25579, username: 'WesoLoad00', auth: 'offline', version,
    profilesFolder: false, disableChatSigning: true, clientSettings: { viewDistance: 2 } });
  let joined = false;
  client.on('playerJoin', () => { joined = true; });
  client.on('error', error => { if (!closing) fault = error; });
  client.on('end', () => { ended = true; if (!closing) fault = new Error('Unexpected property client disconnect'); });
  client.on('custom_payload', packet => {
    try {
      if (packet.channel === channel) { assert(frames.length < 2048); frames.push(Buffer.from(packet.data)); }
      if (packet.channel === 'worldedit:cui') { assert(cui.length < 4096); cui.push(packet.data.toString()); }
    } catch (error) { fault = error; }
  });
  await waitFor(() => joined && server.lines.some(s => s.includes('WESO_FIXTURE_JOIN WesoLoad00')), 'own player join', 60000);
  client.write('custom_payload', { channel: 'minecraft:register', data: Buffer.from(`${channel}\0worldedit:cui`) });
  client.write('custom_payload', { channel: 'worldedit:cui', data: Buffer.from('v|4') });
  await waitFor(() => cui.some(s => s.startsWith('p|1|')), 'ordinary CUI');
  await action('editgrant'); await action('edit-pose'); await delay(2500); await action('edit-audit-start');
  const before = await metrics();
  for (const test of selectionPropertyCases()) {
    await action(test.seed); await action(test.permission);
    assert.deepEqual(await canonical(), { selector: test.selector, points: orderedPoints(propertyBase, test.pointOrientation) });
    if (!epoch) {
      const nonce = randomBytes(16), welcome = (await exchange(hello(nonce, 1, 2, 7))).value;
      assert.equal(welcome.opcode, 2); assert.equal(welcome.status, 0); assert.equal(welcome.capabilities, 7);
      assert.deepEqual(welcome.nonce, nonce); epoch = welcome.epoch; requestId = 0;
    }
    const captureReply = await exchange(editRead(epoch, ++requestId)), captured = captureReply.value;
    assert.equal(captured.opcode, 7); assert.equal(captured.id, BigInt(requestId)); assert.deepEqual(captured.epoch, epoch);
    assert.equal(captured.status, 0); assert.equal(captured.permissions, test.permissionBits); assert.deepEqual(captured.bounds, propertyBase);
    const startCui = cui.length, command = editCommit(epoch, ++requestId, captured.revision, test.bounds);
    const ack = await exchange(command), committed = ack.value;
    assert.equal(committed.opcode, 9); assert.equal(committed.id, BigInt(requestId)); assert.deepEqual(committed.epoch, epoch);
    assert.equal(committed.commitStatus, test.status, test.id);
    assert.equal(committed.status, test.status === 0 ? 0 : 3, test.id);
    if (test.status === 0) assert.deepEqual(committed.bounds, test.bounds);
    else { assert.equal(committed.bounds, undefined); epoch = null; }
    const state = await canonical();
    assert.deepEqual(state, { selector: test.selector, points: test.expectedPoints }, test.id);
    const updates = cui.slice(startCui);
    if (test.cui) for (const [i, point] of [[0, test.expectedPoints.slice(0, 3)], [1, test.expectedPoints.slice(3)]])
      assert(updates.some(s => s.startsWith(`p|${i}|${point.join('|')}|`)), `Missing canonical CUI ${test.id}/${i}`);
    else assert.equal(updates.length, 0, `Unchanged/denied case emitted CUI: ${test.id}`);
    let replayHex = null;
    if (test.replay) {
      const afterCui = cui.length;
      const replay = await exchange(command); replayHex = replay.bytes.toString('hex');
      assert.deepEqual(replay.bytes, ack.bytes, `Replay acknowledgment changed: ${test.id}`);
      assert.equal(cui.length, afterCui, `Replay mutated the selector: ${test.id}`);
    }
    rows.push({ id: test.id, bounds: test.bounds, commitStatus: committed.commitStatus, ...state,
      permissionBits: captured.permissions, cui: updates.length > 0, replayed: test.replay,
      captureHex: captureReply.bytes.toString('hex'), ackHex: ack.bytes.toString('hex'), replayHex, cuiFrames: updates });
    if (rows.length % 18 === 0) console.log(`PROPERTY ${version}: ${rows.length}/144 passed`);
  }
  const counts = verifyPropertyRows(rows);
  await action('edit-audit-end');
  const quiet = await metrics(); await delay(1200); assert.deepEqual(await metrics(), quiet, 'Idle bridge work');
  assert.equal(quiet.applied - before.applied, 96); assert.equal(quiet.commits - before.commits, 144);
  assert.equal(quiet.captures - before.captures, 144); assert.equal(quiet.throttled, before.throttled); assert.equal(quiet.malformed, before.malformed);
  const quitStart = server.lines.length; closing = true; client.end('Properties complete');
  await waitFor(() => ended && observedOwnedDepartures(server.lines.slice(quitStart), ['WesoLoad00']), 'owning server quit barrier');
  const final = await metrics();
  assert.equal(final.connections, 0); assert.equal(final.pending, 0); assert.equal(final.tasks, 0);
  result = { ...manifest, counts, rows, before, quiet, final, worldAudit: true, noUndoEntry: true, idleStable: true,
    transport: transport.evidence ?? { implementation: 'minecraft-protocol', version: '1.68.0' } };
} catch (error) {
  process.exitCode = 1; console.error(error);
  writeFileSync(join(serverDir, 'property-failure.json'), JSON.stringify({ ...manifest, checkedAt: new Date().toISOString(), error: String(error), rows }, null, 2));
} finally {
  closing = true;
  try { if (client && !ended) client.end('Property fixture cleanup'); await transport?.close?.(); }
  finally {
    try { const stop = await lifecycle.stop({ graceMillis: 25000 }); if (result) result.shutdown = stop; }
    finally { await server.drained; }
  }
}
if (result && !process.exitCode) {
  writeFileSync(join(serverDir, 'property-proof.json'), JSON.stringify({ checkedAt: new Date().toISOString(), ...result }, null, 2));
  console.log(`SELECTION PROPERTIES PASS: ${version}, ${result.counts.cases} cases; owned server stopped`);
}
