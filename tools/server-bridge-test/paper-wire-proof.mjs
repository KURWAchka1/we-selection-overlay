import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { createHash, randomBytes } from 'node:crypto';
import { createWriteStream, readFileSync, readdirSync, rmSync, writeFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { setPriority, constants } from 'node:os';
import { resolve, relative, join } from 'node:path';
import { setTimeout as delay } from 'node:timers/promises';
import { createServer } from 'node:net';
import { channel, frame, hello, request, decode } from './wire-codec.mjs';
import { observeOwnedServer } from './owned-server.mjs';

const [javaHome, serverDirectory, dependencyDirectory] = process.argv.slice(2);
assert(javaHome && serverDirectory && dependencyDirectory, 'Expected JDK home, isolated server directory, dependency directory');
const root = resolve(import.meta.dirname, '../..');
const serverDir = resolve(serverDirectory);
assert.equal(relative(root, serverDir), join('build', 'bridge-paper-proof'), 'Never operate a user server');
assert.match(readFileSync(join(serverDir, 'eula.txt'), 'utf8'), /^eula=true\s*$/);
const properties = readFileSync(join(serverDir, 'server.properties'), 'utf8');
assert.match(properties, /^server-ip=127\.0\.0\.1$/m);
assert.match(properties, /^server-port=25579$/m);
assert.match(properties, /^online-mode=false$/m);
const require = createRequire(join(resolve(dependencyDirectory), 'package.json'));
const minecraft = require('minecraft-protocol');
assert.equal(require('minecraft-protocol/package.json').version, '1.68.0');
const portProbe = createServer();
await new Promise((accept, reject) => { portProbe.once('error', reject); portProbe.listen(25579, '127.0.0.1', accept); });
await new Promise((accept, reject) => portProbe.close(error => error ? reject(error) : accept()));
rmSync(join(serverDir, 'wire-proof.json'), { force: true });
const clients = [];
const restartChannel = 'worldedit_selection_overlay:restart';
const checks = [];
const record = name => { checks.push(name); console.log(`PASS: ${name}`); };
const output = createWriteStream(join(serverDir, 'wire-server.log'));
const lines = [];
const partial = { out: '', err: '' };
let exited = false, cancelled = false, successReport;
process.on('SIGINT', () => { cancelled = true; });
process.on('SIGTERM', () => { cancelled = true; });
const java = spawn(join(javaHome, 'bin/java.exe'), ['-Xms256m', '-Xmx1024m', '-XX:ActiveProcessorCount=2',
  '-Dweso.bridge.fixture=true', '-jar', 'server.jar', '--nogui'], {
  cwd: serverDir, windowsHide: true, stdio: ['pipe', 'pipe', 'pipe']
});
const server = observeOwnedServer(java);
java.on('close', () => { exited = true; });
java.on('error', error => { lines.push(`LAUNCH_ERROR ${error.message}`); });
function capture(source, bytes) {
  output.write(bytes);
  partial[source] += bytes.toString();
  let end;
  while ((end = partial[source].indexOf('\n')) >= 0) {
    lines.push(partial[source].slice(0, end).replace(/\r$/, ''));
    partial[source] = partial[source].slice(end + 1);
  }
}
java.stdout.on('data', bytes => capture('out', bytes));
java.stderr.on('data', bytes => capture('err', bytes));
async function waitFor(predicate, label, timeout = 15000) {
  const deadline = Date.now() + timeout;
  while (!predicate()) {
    if (cancelled) throw new Error(`Cancelled while waiting for ${label}`);
    if (exited) throw new Error(`Server exited while waiting for ${label}: ${lines.slice(-15).join('\n')}`);
    if (Date.now() >= deadline) throw new Error(`Timeout: ${label}`);
    await delay(25);
  }
}
async function consoleAction(action, player) {
  const start = lines.length;
  java.stdin.write(`wesofixture ${action} ${player}\n`);
  await waitFor(() => lines.slice(start).some(s => s.includes(`WESO_FIXTURE_OK ${action} ${player}`)), action);
}
async function metrics() {
  const start = lines.length;
  java.stdin.write('wesobridge\n');
  await waitFor(() => lines.slice(start).some(s => /Restart hints=/.test(s)), 'diagnostic counters');
  const a = lines.slice(start).find(s => /connections=/.test(s));
  const b = lines.slice(start).find(s => /Selection metadata reads=/.test(s));
  const c = lines.slice(start).find(s => /Restart hints=/.test(s));
  const number = (text, key) => Number(text.match(new RegExp(`${key}=(\\d+)`))[1]);
  return { connections: number(a, 'connections'), admitted: number(a, 'admitted'),
    throttled: number(a, 'throttled'), malformed: number(a, 'malformed'),
    reads: number(b, 'reads'), replies: number(b, 'replies'), restartHints: number(c, 'hints'), hintBytes: number(c, 'bytes') };
}
async function connect(name, advertise = true) {
  const joinStart = lines.length;
  const client = minecraft.createClient({ host: '127.0.0.1', port: 25579, username: name,
    auth: 'offline', version: '1.21.11', profilesFolder: false,
    clientSettings: { viewDistance: 2 }, disableChatSigning: true });
  const peer = { client, frames: [], restartHints: [], cui: [], advertised: false, serverChannels: new Set(), errors: [], ended: false };
  clients.push(peer);
  client.on('error', error => peer.errors.push(error.message));
  client.on('end', () => { peer.ended = true; });
  client.on('custom_payload', packet => {
    if (packet.channel === 'minecraft:register' || packet.channel === 'minecraft:unregister') {
      const channels = Array.isArray(packet.data) ? packet.data : packet.data.toString().split('\0');
      for (const name of channels) {
        if (packet.channel === 'minecraft:register') peer.serverChannels.add(name);
        else peer.serverChannels.delete(name);
      }
      peer.advertised ||= peer.serverChannels.has(channel);
    }
    if (packet.channel === channel) peer.frames.push(Buffer.from(packet.data));
    if (packet.channel === restartChannel) peer.restartHints.push(Buffer.from(packet.data));
    if (packet.channel === 'worldedit:cui') peer.cui.push(packet.data.toString());
  });
  await waitFor(() => lines.slice(joinStart).some(s => s.includes(`WESO_FIXTURE_JOIN ${name}`)), `join ${name}`);
  if (advertise) client.write('custom_payload', { channel: 'minecraft:register', data: Buffer.from(channel) });
  await waitFor(() => peer.advertised, 'Paper advertised channel');
  peer.send = bytes => { assert(!peer.ended, peer.errors.join(', ')); client.write('custom_payload', { channel, data: bytes }); };
  peer.exchange = async bytes => {
    await delay(300);
    const index = peer.frames.length; peer.send(bytes);
    await waitFor(() => peer.frames.length > index || peer.ended, `reply to opcode ${bytes[5]}`);
    assert(!peer.ended, peer.errors.join(', '));
    return { bytes: peer.frames[index], value: decode(peer.frames[index]) };
  };
  return peer;
}
async function negotiate(peer, major = 1, minor = 0, capabilities = 1) {
  const nonce = randomBytes(16), response = await peer.exchange(hello(nonce, major, minor, capabilities));
  assert.equal(response.value.opcode, 2); assert.deepEqual(response.value.nonce, nonce);
  assert.equal(response.value.frameBytes, 256);
  return response.value;
}
async function silent(peer, bytes) {
  await delay(300);
  const count = peer.frames.length; peer.send(bytes); await delay(500);
  assert.equal(peer.frames.length, count, 'Unexpected reply to stale/invalid request');
  assert(!peer.ended, peer.errors.join(', '));
}
try {
  if (java.pid) setPriority(java.pid, constants.priority.PRIORITY_BELOW_NORMAL);
  await waitFor(() => lines.some(s => /Done \(/.test(s)), 'Paper startup', 180000);
  assert(lines.some(s => s.includes('Experimental read-only bridge enabled')));
  assert(!lines.some(s => s.includes('Error occurred while enabling')));
  record('Paper 1.21.11 and official WorldEdit 7.4.2 enable');
  const ordinary = await connect('WesoVanilla', false);
  const denied = await connect('WesoDenied');
  assert.equal((await negotiate(denied)).status, 1);
  const first = await connect('WesoAllowed');
  const second = await connect('WesoSecond');
  for (const peer of [first, denied]) {
    peer.client.write('custom_payload', { channel: 'minecraft:register', data: Buffer.from('worldedit:cui') });
    peer.client.write('custom_payload', { channel: 'worldedit:cui', data: Buffer.from('v|4') });
    await waitFor(() => peer.cui.some(s => s.startsWith('s|cuboid'))
      && peer.cui.some(s => s.startsWith('p|0|')) && peer.cui.some(s => s.startsWith('p|1|')), 'ordinary CUI selection');
  }
  record('normal CUI works independently for granted and bridge-denied clients');
  const a = await negotiate(first), b = await negotiate(second);
  assert.equal(a.status, 0); assert.equal(b.status, 0); assert.equal(a.capabilities, 1);
  assert.equal(a.minor, 0); assert.equal(b.minor, 0);
  assert.notDeepEqual(a.epoch, b.epoch);
  await delay(1500);
  assert.equal((await metrics()).reads, 0);
  assert.equal(ordinary.frames.length, 0);
  record('ordinary client joins; grants opt-in; hello and idle perform zero selection reads');
  const one = await first.exchange(request(a.epoch, 1));
  assert.equal(one.value.status, 0);
  assert.deepEqual(one.value.bounds, [10, 64, 20, 15, 70, 27]);
  assert.equal(one.value.minY, -64); assert.equal(one.value.maxY, 319); assert.equal(one.value.permissions, 7);
  const two = await second.exchange(request(b.epoch, 1));
  assert.deepEqual(two.value.bounds, [-30, 64, 20, -25, 70, 27]);
  assert.equal((await metrics()).reads, 2);
  record('real connection-owned WorldEdit metadata and inclusive height round trip');
  const duplicate = await first.exchange(request(a.epoch, 1));
  assert.deepEqual(duplicate.bytes, one.bytes); assert.equal((await metrics()).reads, 2);
  await silent(first, request(b.epoch, 2));
  assert.equal((await metrics()).reads, 2);
  record('duplicate cached; another player epoch rejected before WorldEdit read');
  await consoleAction('revoke', 'WesoAllowed');
  assert.equal((await first.exchange(request(a.epoch, 1))).value.status, 3);
  await silent(first, request(a.epoch, 2));
  assert.equal((await metrics()).reads, 2);
  record('live revocation prevents cached disclosure and invalidates epoch');
  await consoleAction('grant', 'WesoAllowed');
  const renewed = await negotiate(first); assert.notDeepEqual(renewed.epoch, a.epoch);
  await consoleAction('empty', 'WesoAllowed');
  assert.equal((await first.exchange(request(renewed.epoch, 1))).value.status, 1);
  await consoleAction('seed', 'WesoAllowed');
  assert.deepEqual((await first.exchange(request(renewed.epoch, 2))).value.bounds, one.value.bounds);
  const baseline = await metrics();
  await silent(first, Buffer.alloc(257));
  await silent(first, frame(255, Buffer.alloc(0)));
  assert((await metrics()).malformed >= baseline.malformed + 2);
  assert.equal((await metrics()).reads, baseline.reads);
  assert.equal((await negotiate(denied, 2)).status, 2);
  record('empty/recreated selection, oversized/unknown payload and incompatible major fail safely');
  const stable = await metrics(); await delay(2000); const idle = await metrics();
  assert.equal(idle.reads, stable.reads); assert.equal(idle.replies, stable.replies);

  const beforeUnregister = await metrics();
  first.client.write('custom_payload', { channel: 'minecraft:unregister', data: Buffer.from('worldedit:cui') });
  await delay(300);
  assert.equal((await metrics()).connections, beforeUnregister.connections, 'Unrelated channel must not clear the bridge');
  first.client.write('custom_payload', { channel: 'minecraft:unregister', data: Buffer.from(channel) });
  await delay(300);
  assert.equal((await metrics()).connections, beforeUnregister.connections, 'Open connection must retain its admission budget');
  first.client.write('custom_payload', { channel: 'minecraft:register', data: Buffer.from(channel) });
  await silent(first, request(renewed.epoch, 3));
  const afterRegister = await negotiate(first);
  assert.notDeepEqual(afterRegister.epoch, renewed.epoch);
  record('channel unregister drops cached state/epoch, preserves admission budget; unrelated channels do not reset context');

  const respawnStart = lines.length;
  await consoleAction('kill', 'WesoAllowed');
  first.client.write('client_command', { actionId: 0 });
  await waitFor(() => lines.slice(respawnStart).some(s => s.includes('WESO_FIXTURE_RESPAWN WesoAllowed')), 'actual same-world respawn');
  await silent(first, request(afterRegister.epoch, 1));
  assert.equal((await metrics()).reads, idle.reads);
  const afterRespawn = await negotiate(first);
  assert.notDeepEqual(afterRespawn.epoch, afterRegister.epoch);
  assert.equal((await first.exchange(request(afterRespawn.epoch, 1))).value.status, 0);
  record('real same-world death/respawn invalidates epoch before metadata disclosure');

  first.client.end('same-process reconnect test');
  await waitFor(() => first.ended, 'first connection closes');
  const reconnected = await connect('WesoAllowed');
  await silent(reconnected, request(afterRespawn.epoch, 2));
  const newConnection = await negotiate(reconnected);
  assert.notDeepEqual(newConnection.epoch, afterRespawn.epoch);
  assert.equal((await reconnected.exchange(request(newConnection.epoch, 1))).value.status, 0);
  record('same offline identity reconnects in this server process without retaining old epoch');

  const closeOnly = await negotiate(reconnected, 1, 1, 2);
  assert.equal(closeOnly.status, 1); assert.equal(closeOnly.capabilities, 0);
  const downgrade = await negotiate(reconnected, 1, 0, 3);
  assert.equal(downgrade.status, 0); assert.equal(downgrade.minor, 0); assert.equal(downgrade.capabilities, 1);
  let current = await negotiate(reconnected, 1, 1, 3);
  assert.equal(current.status, 0); assert.equal(current.minor, 1); assert.equal(current.capabilities, 3);
  record('minor-zero remains compatible; close notification alone grants no access; minor-one requires explicit opt-in');
  for (const peer of [reconnected, denied])
    peer.client.write('custom_payload', { channel: 'minecraft:register', data: Buffer.from(restartChannel) });
  await delay(300);
  let currentEpoch = current.epoch;
  const lifecycle = [];
  for (let cycle = 1; cycle <= 3; cycle++) {
    const legacyMinorOne = cycle === 3 ? await negotiate(second, 1, 1, 3) : null;
    await consoleAction('bridgeactive', 'WesoAllowed');
    const start = reconnected.frames.length;
    const legacyFrames = second.frames.length, deniedFrames = denied.frames.length;
    await consoleAction('bridgeoff', 'WesoAllowed');
    await waitFor(() => reconnected.frames.length > start, 'negotiated close before channel cleanup');
    assert.equal(reconnected.frames.length, start + 1, 'Repeated close notification');
    const closed = decode(reconnected.frames[start]);
    assert.equal(closed.opcode, 5); assert.deepEqual(closed.nonce, current.nonce); assert.deepEqual(closed.epoch, current.epoch);
    await delay(100);
    const expectedLegacyFrames = legacyFrames + (legacyMinorOne ? 1 : 0);
    assert.equal(second.frames.length, expectedLegacyFrames, 'Legacy lifecycle contract changed');
    if (legacyMinorOne) {
      const legacyClosed = decode(second.frames[legacyFrames]);
      assert.equal(legacyClosed.opcode, 5); assert.deepEqual(legacyClosed.nonce, legacyMinorOne.nonce);
      assert.deepEqual(legacyClosed.epoch, legacyMinorOne.epoch);
    }
    assert.equal(denied.frames.length, deniedFrames, 'Denied client received a session notification');
    assert.equal(ordinary.frames.length, 0, 'Ordinary client received a bridge payload');
    await consoleAction('channels', 'WesoAllowed');
    await silent(reconnected, request(currentEpoch, 2));
    assert.equal(reconnected.frames.length, start + 1, 'Late duplicate close notification');
    assert.equal(second.frames.length, expectedLegacyFrames, 'Legacy peer received a late lifecycle notification');
    assert.equal(denied.frames.length, deniedFrames, 'Denied peer received a late lifecycle notification');
    const remoteStillAdvertised = reconnected.serverChannels.has(channel);
    const hintIndex = reconnected.restartHints.length, deniedHintIndex = denied.restartHints.length;
    await consoleAction('bridgeon', 'WesoAllowed');
    await consoleAction('bridgeactive', 'WesoAllowed');
    const fresh = await metrics();
    assert.equal(fresh.connections, 0); assert.equal(fresh.reads, 0); assert.equal(fresh.replies, 0);
    assert.equal(fresh.restartHints, 2); assert.equal(fresh.hintBytes, 44);
    await waitFor(() => reconnected.restartHints.length > hintIndex && denied.restartHints.length > deniedHintIndex, 'opt-in restart hint');
    assert.equal(reconnected.restartHints.length, hintIndex + 1);
    assert.equal(denied.restartHints.length, deniedHintIndex + 1);
    assert.equal(second.restartHints.length, 0, 'Legacy client received an unsupported channel');
    assert.equal(ordinary.restartHints.length, 0, 'Ordinary client received an unsupported channel');
    const hint = reconnected.restartHints[hintIndex];
    assert.equal(hint.length, 22); assert.equal(hint.subarray(0, 6).toString('hex'), '574553520101');
    assert(!hint.subarray(6).equals(Buffer.alloc(16)), 'Zero startup generation');
    assert.deepEqual(denied.restartHints[deniedHintIndex], hint, 'Startup created per-player authority tokens');
    if (hintIndex) assert.notDeepEqual(reconnected.restartHints[hintIndex - 1], hint, 'Restart reused generation');
    await silent(reconnected, request(currentEpoch, 2));
    assert.equal((await metrics()).reads, 0, 'Restart accepted a previous core epoch');
    const welcome = await negotiate(reconnected, 1, 1, 3);
    assert.notDeepEqual(welcome.epoch, currentEpoch);
    const read = await reconnected.exchange(request(welcome.epoch, 1));
    assert.deepEqual(read.value.bounds, one.value.bounds);
    assert.deepEqual((await reconnected.exchange(request(welcome.epoch, 1))).bytes, read.bytes);
    const beforeQuiet = await metrics();
    assert.equal(beforeQuiet.reads, 1); assert.equal(beforeQuiet.replies, 3, 'Duplicate callback/reply after re-enable');
    await delay(1000);
    assert.deepEqual(await metrics(), beforeQuiet, 'Idle restarted companion performed unsolicited work');
    assert.equal(reconnected.restartHints.length, hintIndex + 1, 'Periodic startup hints');
    lifecycle.push({ cycle, remoteStillAdvertised, sessionClosed: true, restartHint: hint.toString('hex'),
      legacyMinor: legacyMinorOne ? 1 : 0, legacyCloseNotified: !!legacyMinorOne, legacyRestartHintNotified: false, metrics: beforeQuiet });
    currentEpoch = welcome.epoch;
    current = welcome;
  }
  record('three same-process plugin disable/enable cycles release resources, reject old epochs and do not duplicate work');
  record('restart sends one 22-byte hint per opt-in listener, no legacy/ordinary payload, reads or permission bypass');
  record('previous minor-one client still receives negotiated close only, without an unsupported restart channel');

  for (const peer of clients) if (peer !== first && peer !== reconnected) {
    assert(!peer.ended, peer.errors.join(', ')); peer.client.end('verification complete');
  }
  await waitFor(() => clients.filter(p => p !== reconnected).every(p => p.ended), 'other clients disconnect');
  await delay(200);
  assert.equal((await metrics()).connections, 1);
  const dependencyStop = reconnected.frames.length;
  await consoleAction('stopwe', 'WesoAllowed');
  await waitFor(() => reconnected.frames.length > dependencyStop, 'WorldEdit dependency stop notification');
  assert.equal(reconnected.frames.length, dependencyStop + 1);
  const closed = decode(reconnected.frames[dependencyStop]);
  assert.equal(closed.opcode, 5); assert.deepEqual(closed.nonce, current.nonce); assert.deepEqual(closed.epoch, currentEpoch);
  await consoleAction('channels', 'WesoAllowed');
  await silent(reconnected, request(currentEpoch, 2));
  record('WorldEdit disable also disables companion and unregisters both channel directions');
  await consoleAction('bridgeon', 'WesoAllowed');
  await consoleAction('channels', 'WesoAllowed');
  await silent(reconnected, hello(randomBytes(16)));
  assert.equal(reconnected.restartHints.length, 3, 'Failed dependency startup sent a restart hint');
  record('re-enable with disabled WorldEdit fails closed without channel/listener/task ownership');
  reconnected.client.end('verification complete');
  await waitFor(() => clients.every(p => p.ended), 'client disconnect');
  await delay(200);
  record('no idle source work; remaining clients leave cleanly after dependency shutdown');
  const hashes = Object.fromEntries(readdirSync(join(serverDir, 'plugins')).filter(f => f.endsWith('.jar'))
    .map(f => [f, createHash('sha256').update(readFileSync(join(serverDir, 'plugins', f))).digest('hex')]));
  successReport = { checkedAt: new Date().toISOString(),
    minecraft: '1.21.11', minecraftProtocol: '1.68.0', server: lines.find(s => /This server is running Paper/.test(s)),
    hashes, checks, lifecycle, metricsBeforeLifecycle: idle,
    nativeClients: 'not launched; separate native tests required' };
} catch (error) {
  console.error(error);
  process.exitCode = 1;
} finally {
  for (const peer of clients) if (!peer.ended) peer.client.end('fixture shutdown');
  try { await server.stop({ graceMillis: 20000 }); }
  finally { await new Promise(r => output.end(r)); }
}
assert(exited, `Owned Paper PID ${java.pid} did not stop`);
if (successReport && !process.exitCode) {
  writeFileSync(join(serverDir, 'wire-proof.json'), JSON.stringify(successReport, null, 2));
  console.log('PAPER BRIDGE WIRE PROOF PASS; owned server stopped');
}
