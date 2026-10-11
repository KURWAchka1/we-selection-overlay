import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { createHash, randomBytes } from 'node:crypto';
import { createWriteStream, readFileSync, readdirSync, writeFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { setPriority, constants } from 'node:os';
import { resolve, relative, join } from 'node:path';
import { setTimeout as delay } from 'node:timers/promises';

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
const channel = 'worldedit_selection_overlay:bridge';
const clients = [];
const checks = [];
const record = name => { checks.push(name); console.log(`PASS: ${name}`); };
const output = createWriteStream(join(serverDir, 'wire-server.log'));
const lines = [];
let partial = '', exited = false, cancelled = false;
process.on('SIGINT', () => { cancelled = true; });
process.on('SIGTERM', () => { cancelled = true; });
const java = spawn(join(javaHome, 'bin/java.exe'), ['-Xms256m', '-Xmx1024m', '-XX:ActiveProcessorCount=2',
  '-Dweso.bridge.fixture=true', '-jar', 'server.jar', '--nogui'], {
  cwd: serverDir, windowsHide: true, stdio: ['pipe', 'pipe', 'pipe']
});
java.on('exit', () => { exited = true; });
java.on('error', error => { lines.push(`LAUNCH_ERROR ${error.message}`); exited = true; });
function capture(bytes) {
  output.write(bytes);
  partial += bytes.toString();
  let end;
  while ((end = partial.indexOf('\n')) >= 0) {
    lines.push(partial.slice(0, end).replace(/\r$/, ''));
    partial = partial.slice(end + 1);
  }
}
java.stdout.on('data', capture);
java.stderr.on('data', capture);
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
  await waitFor(() => lines.slice(start).some(s => /Selection metadata reads=/.test(s)), 'diagnostic counters');
  const a = lines.slice(start).find(s => /connections=/.test(s));
  const b = lines.slice(start).find(s => /Selection metadata reads=/.test(s));
  const number = (text, key) => Number(text.match(new RegExp(`${key}=(\\d+)`))[1]);
  return { connections: number(a, 'connections'), admitted: number(a, 'admitted'),
    throttled: number(a, 'throttled'), malformed: number(a, 'malformed'),
    reads: number(b, 'reads'), replies: number(b, 'replies') };
}
function frame(opcode, body) { return Buffer.concat([Buffer.from('5745534f01' + opcode.toString(16).padStart(2, '0'), 'hex'), body]); }
function hello(nonce, major = 1) {
  const body = Buffer.alloc(24);
  nonce.copy(body); body.writeUInt16BE(major, 16); body.writeUInt32BE(1, 20);
  return frame(1, body);
}
function request(epoch, id) {
  const body = Buffer.alloc(24); epoch.copy(body); body.writeBigInt64BE(BigInt(id), 16);
  return frame(3, body);
}
function decode(bytes) {
  assert(bytes.length >= 6 && bytes.length <= 256);
  assert.equal(bytes.subarray(0, 5).toString('hex'), '5745534f01');
  if (bytes[5] === 2) {
    assert.equal(bytes.length, 49);
    return { opcode: 2, nonce: bytes.subarray(6, 22), epoch: bytes.subarray(22, 38),
      status: bytes[38], major: bytes.readUInt16BE(39), minor: bytes.readUInt16BE(41),
      capabilities: bytes.readUInt32BE(43), frameBytes: bytes.readUInt16BE(47) };
  }
  assert.equal(bytes[5], 4);
  const result = { opcode: 4, epoch: bytes.subarray(6, 22), id: bytes.readBigInt64BE(22), status: bytes[30] };
  assert.equal(bytes.length, result.status === 0 ? 64 : 31);
  if (result.status === 0) {
    result.bounds = Array.from({ length: 6 }, (_, i) => bytes.readInt32BE(31 + i * 4));
    result.minY = bytes.readInt32BE(55); result.maxY = bytes.readInt32BE(59); result.permissions = bytes[63];
  }
  return result;
}
async function connect(name, advertise = true) {
  const client = minecraft.createClient({ host: '127.0.0.1', port: 25579, username: name,
    auth: 'offline', version: '1.21.11', profilesFolder: false,
    clientSettings: { viewDistance: 2 }, disableChatSigning: true });
  const peer = { client, frames: [], cui: [], advertised: false, errors: [], ended: false };
  clients.push(peer);
  client.on('error', error => peer.errors.push(error.message));
  client.on('end', () => { peer.ended = true; });
  client.on('custom_payload', packet => {
    if (packet.channel === 'minecraft:register') {
      const channels = Array.isArray(packet.data) ? packet.data : packet.data.toString().split('\0');
      peer.advertised ||= channels.includes(channel);
    }
    if (packet.channel === channel) peer.frames.push(Buffer.from(packet.data));
    if (packet.channel === 'worldedit:cui') peer.cui.push(packet.data.toString());
  });
  await waitFor(() => lines.some(s => s.includes(`WESO_FIXTURE_JOIN ${name}`)), `join ${name}`);
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
async function negotiate(peer, major = 1) {
  const nonce = randomBytes(16), response = await peer.exchange(hello(nonce, major));
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
  for (const peer of clients) { assert(!peer.ended, peer.errors.join(', ')); peer.client.end('verification complete'); }
  await waitFor(() => clients.every(p => p.ended), 'client disconnect');
  await delay(200);
  const finalMetrics = await metrics(); assert.equal(finalMetrics.connections, 0);
  record('no idle source work and all connection state released on disconnect');
  const hashes = Object.fromEntries(readdirSync(join(serverDir, 'plugins')).filter(f => f.endsWith('.jar'))
    .map(f => [f, createHash('sha256').update(readFileSync(join(serverDir, 'plugins', f))).digest('hex')]));
  writeFileSync(join(serverDir, 'wire-proof.json'), JSON.stringify({ checkedAt: new Date().toISOString(),
    minecraft: '1.21.11', minecraftProtocol: '1.68.0', server: lines.find(s => /This server is running Paper/.test(s)),
    hashes, checks, metrics: idle, finalMetrics,
    nativeClients: 'not launched; separate native codec tests required' }, null, 2));
  console.log('PAPER BRIDGE WIRE PROOF PASS');
} catch (error) {
  console.error(error);
  process.exitCode = 1;
} finally {
  for (const peer of clients) if (!peer.ended) peer.client.end('fixture shutdown');
  if (!exited) {
    java.stdin.write('stop\n');
    const deadline = Date.now() + 20000;
    while (!exited && Date.now() < deadline) await delay(50);
    if (!exited) { java.kill(); await new Promise(r => java.once('exit', r)); }
  }
  await new Promise(r => output.end(r));
}
