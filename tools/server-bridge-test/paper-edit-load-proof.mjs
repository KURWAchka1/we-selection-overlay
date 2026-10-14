import assert from 'node:assert/strict';
import { spawn, spawnSync } from 'node:child_process';
import { createHash, randomBytes } from 'node:crypto';
import { copyFileSync, existsSync, mkdirSync, readFileSync, unlinkSync, writeFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { createServer } from 'node:net';
import { constants, setPriority } from 'node:os';
import { join, relative, resolve } from 'node:path';
import { performance } from 'node:perf_hooks';
import { setTimeout as delay } from 'node:timers/promises';
import { channel, hello, request, editRead, editCommit, decode } from './wire-codec.mjs';
import { captureProcess } from './native-process.mjs';
import { observeOwnedServer } from './owned-server.mjs';
import { verifyEditLoadWindow } from './edit-load-window.mjs';
import { gestureTuple } from './gesture-tuples.mjs';
import { startJavaLoadClients } from './java-load-clients.mjs';

const [javaHome, directory, playerArgument, sampleArgument, repeatArgument, mode, version = '1.21.11'] = process.argv.slice(2);
const tuple = gestureTuple(version);
const root = resolve(import.meta.dirname, '../..'), serverDir = resolve(directory), repetitions = Number(repeatArgument);
assert.equal(relative(root, serverDir), join('build', 'bridge-edit-load-proof', ...(version === '1.21.11' ? [] : [version])));
assert.equal(Number(playerArgument), 32); assert.equal(Number(sampleArgument), 400);
assert(Number.isInteger(repetitions) && repetitions >= 1 && repetitions <= 4);
const properties = readFileSync(join(serverDir, 'server.properties'), 'utf8');
for (const line of ['server-ip=127.0.0.1', 'server-port=25579', 'online-mode=false', 'max-players=32']) assert(properties.split(/\r?\n/).includes(line));
assert.match(readFileSync(join(serverDir, 'eula.txt'), 'utf8'), /^eula=true\s*$/);
const require = createRequire(join(root, 'build/bridge-wire-deps/package.json'));
const minecraft = version === '26.2' ? null : require('minecraft-protocol');
if (minecraft) assert.equal(require('minecraft-protocol/package.json').version, '1.68.0');
const hash = file => createHash('sha256').update(readFileSync(file)).digest('hex');
const hashes = { paper: hash(join(serverDir, 'server.jar')), worldedit: hash(join(serverDir, 'plugins/worldedit.jar')),
  companion: hash(join(serverDir, 'companion.jar')), fixture: hash(join(serverDir, 'plugins/weso-paper-test-fixture.jar')) };
assert.equal(hashes.paper, tuple.paperSha256);
assert.equal(createHash(tuple.worldeditAlgorithm).update(readFileSync(join(serverDir, 'plugins/worldedit.jar'))).digest('hex'), tuple.worldeditHash);
const revision = spawnSync('git', ['rev-parse', 'HEAD'], { cwd: root, windowsHide: true, encoding: 'utf8' });
assert.equal(revision.status, 0); const baseline = revision.stdout.trim(); assert.match(baseline, /^[0-9a-f]{40}$/);
const proof = join(serverDir, 'edit-load-proof.json'), failure = join(serverDir, 'edit-load-failure.json');
for (const file of [proof, failure]) if (existsSync(file)) unlinkSync(file);
let server, transport, cancelled = false, stopping = true, peers = [], workers = [], fault;
const runs = [];
process.on('SIGINT', () => { cancelled = true; }); process.on('SIGTERM', () => { cancelled = true; });
async function waitFor(predicate, label, timeout = 15000) {
  const end = Date.now() + timeout;
  while (!predicate()) { assert(!fault && !transport?.fault && !cancelled && !server?.exited && Date.now() < end, `${label}: ${fault ?? transport?.fault ?? 'timeout/stop'}`); await delay(10); }
}
async function metrics() {
  const start = server.lines.length; server.child.stdin.write('wesobridge\n');
  await waitFor(() => server.lines.slice(start).some(s => /Queue pending=/.test(s)), 'metrics');
  const lines = server.lines.slice(start), find = text => lines.find(s => s.includes(text));
  const n = (text, key) => Number(find(text).match(new RegExp(`${key}=(\\d+)`))[1]);
  return { connections: n('connections=', 'connections'), admitted: n('connections=', 'admitted'), throttled: n('connections=', 'throttled'),
    reads: n('Selection metadata reads=', 'reads'), replies: n('Selection metadata reads=', 'replies'),
    captures: n('Cuboid captures=', 'captures'), commits: n('Cuboid captures=', 'commits'), applied: n('Cuboid captures=', 'applied'),
    pending: n('Queue pending=', 'pending'), peak: n('Queue pending=', 'peak'), tasks: n('Queue pending=', 'tasks') };
}
async function action(action, name) {
  const start = server.lines.length; server.child.stdin.write(`wesofixture ${action} ${name}\n`);
  await waitFor(() => server.lines.slice(start).some(s => s.includes(`WESO_FIXTURE_OK ${action} ${name}`)), action);
}
async function connect(index) {
  const name = `WesoLoad${String(index).padStart(2, '0')}`;
  const client = transport.createClient({ host: '127.0.0.1', port: 25579, username: name, auth: 'offline', version, profilesFolder: false, disableChatSigning: true, clientSettings: { viewDistance: 2 } });
  const p = { name, client, index, role: index < 8 ? 'editor' : index < 16 ? 'reader' : index < 28 ? 'flood' : 'silent', frames: [], cui: [], play: false, ended: false,
    id: 0, sent: 0, applied: 0, reads: 0, latencies: [], bounds: [100 + index * 16, 64, 20, 105 + index * 16, 70, 27] };
  peers.push(p);
  client.on('playerJoin', () => { p.play = true; });
  client.on('error', error => { if (!p.closing) fault = error; });
  client.on('end', () => { p.ended = true; if (!p.closing) fault = new Error(`Unexpected disconnect ${name}`); });
  client.on('custom_payload', packet => {
    try {
      if (packet.channel === channel) { assert(p.frames.length < 8192); p.frames.push(Buffer.from(packet.data)); }
      if (packet.channel === 'worldedit:cui') { assert(p.cui.length < 8192); p.cui.push(packet.data.toString()); }
    } catch (error) { fault = error; }
  });
  await waitFor(() => p.play && server.lines.some(s => s.includes(`WESO_FIXTURE_JOIN ${name}`)), `join ${name}`, 60000);
  client.write('custom_payload', { channel: 'minecraft:register', data: Buffer.from(p.role === 'silent' ? 'worldedit:cui' : `${channel}\0worldedit:cui`) });
  client.write('custom_payload', { channel: 'worldedit:cui', data: Buffer.from('v|4') });
  await waitFor(() => p.cui.some(s => s.startsWith('p|1|')), `CUI ${name}`);
  p.send = frame => { assert(!p.ended, `Disconnected ${name}`); p.sent++; client.write('custom_payload', { channel, data: frame }); };
  p.exchange = async frame => {
    const start = p.frames.length; p.send(frame);
    await waitFor(() => p.frames.length > start || p.ended, `reply ${name}/${frame[5]}`, 10000);
    assert(!p.ended, `${name} disconnected`); const bytes = p.frames[start]; return { bytes, value: decode(bytes) };
  };
  if (p.role !== 'silent') {
    const nonce = randomBytes(16), result = (await p.exchange(hello(nonce, 1, 2, p.role === 'editor' ? 7 : 3))).value;
    assert.equal(result.status, 0); assert.deepEqual(result.nonce, nonce); p.epoch = result.epoch;
  }
  return p;
}
async function capture(p) {
  const reply = (await p.exchange(editRead(p.epoch, ++p.id))).value;
  assert.equal(reply.status, 0); assert.deepEqual(reply.bounds, p.bounds); p.revision = reply.revision;
}
function actorSummary() {
  return peers.sort((a,b) => a.index - b.index).map(p => ({ name: p.name, role: p.role, sent: p.sent, frames: p.frames.length,
    applied: p.applied, reads: p.reads, latencies: p.latencies, bounds: p.bounds }));
}
async function measure(label, active) {
  const before = await metrics(), start = server.lines.length, started = performance.now();
  for (const p of peers) { p.sent = p.applied = p.reads = 0; p.latencies = []; }
  server.child.stdin.write(`wesofixture measure ${label}\n`);
  await waitFor(() => server.lines.slice(start).some(s => s.includes(`WESO_MEASURE_START ${label}`)), 'measurement start');
  stopping = !active;
  if (active) workers = peers.filter(p => p.role !== 'silent').map(p => (async () => {
    let round = 0;
    while (!stopping) {
      const at = performance.now();
      if (p.role === 'flood') p.send(request(p.epoch, ++p.id));
      else if (p.role === 'reader') { const reply = (await p.exchange(request(p.epoch, ++p.id))).value; assert.deepEqual(reply.bounds, p.bounds); p.reads++; }
      else {
        const x = 100 + p.index * 16 + (p.index === 0 ? 1 : 0) + (++round % 2);
        const target = [x, 64, 20, x + 5, 70, 27], frame = editCommit(p.epoch, ++p.id, p.revision, target);
        const response = await p.exchange(frame), reply = response.value;
        assert.equal(reply.commitStatus, 0); assert.deepEqual(reply.bounds, target);
        p.revision = reply.revision; p.bounds = target; p.applied++; p.latencies.push(performance.now() - at);
        p.lastFrame = frame; p.lastAck = response.bytes;
      }
      await delay(Math.max(0, (p.role === 'flood' ? 80 : 250) - (performance.now() - at)));
    }
  })().catch(error => { fault = error; throw error; }));
  for (const worker of workers) worker.catch(() => {});
  await waitFor(() => server.lines.slice(start).some(s => s.includes(`WESO_MEASURE_DONE ${label}`)), 'measurement completion', 60000);
  stopping = true; await Promise.all(workers); workers = [];
  await delay(500);
  const after = await metrics(), ticks = JSON.parse(readFileSync(join(serverDir, `plugins/WesoBridgeTestFixture/load-${label}.json`)));
  const window = { active, before, after, actors: actorSummary(), durationMillis: performance.now() - started, ticks };
  verifyEditLoadWindow(window); return window;
}
async function run(round) {
  peers = []; fault = null; stopping = true; transport = null;
  const probe = createServer(); await new Promise((resolve, reject) => { probe.once('error', reject); probe.listen(25579, '127.0.0.1', resolve); }); await new Promise(resolve => probe.close(resolve));
  copyFileSync(join(serverDir, 'companion.jar'), join(serverDir, 'plugins/worldedit_selection_overlay-bridge-paper-0.1.0-dev.jar'));
  const config = join(serverDir, 'plugins/WorldEditSelectionOverlayBridge'); mkdirSync(config, { recursive: true });
  writeFileSync(join(config, 'config.yml'), 'limits:\n  max-connections: 1024\n  global-requests-per-second: 100\n  global-burst: 100\n  player-requests-per-second: 4\n  player-burst: 8\n');
  const child = spawn(join(javaHome, 'bin/java.exe'), ['-Xms256m', '-Xmx1024m', '-XX:ActiveProcessorCount=2', '-Dweso.bridge.fixture=true', '-Dweso.bridge.editFixture=true', '-Dweso.bridge.measureTicks=400',
    `-XX:StartFlightRecording=filename=edit-${round}.jfr,settings=default,dumponexit=true,maxsize=32m`, '-jar', 'server.jar', '--nogui'], { cwd: serverDir, windowsHide: true, stdio: ['pipe','pipe','pipe'] });
  server = captureProcess(child, join(serverDir, `edit-${round}.log`)); server.lifecycle = observeOwnedServer(child);
  if (child.pid) setPriority(child.pid, constants.priority.PRIORITY_BELOW_NORMAL);
  try {
    await waitFor(() => server.lines.some(s => /Done \(/.test(s)), 'Paper startup', 180000);
    assert(!server.lines.some(s => /Error occurred while enabling/.test(s)));
    assert(!server.lines.some(s => s.includes('[WorldEditSelectionOverlayBridge]') && /Disabling|ERROR/.test(s)), 'Bridge failed closed at startup');
    if (version === '26.2') {
      transport = startJavaLoadClients(javaHome, join(serverDir, `edit-${round}-clients.log`));
      await waitFor(() => transport.ready, 'exact 26.2 load codec', 15000);
    } else transport = { createClient: options => minecraft.createClient(options), evidence: {
      implementation: 'minecraft-protocol', version: '1.68.0', protocol: require('minecraft-data')(version).version.version } };
    await Promise.all(Array.from({length:32}, (_,i) => connect(i)));
    await action('control', 'baseline'); await delay(5000);
    const editors = peers.filter(p => p.role === 'editor');
    for (const p of editors) { await action('edit-audit-start', p.name); await capture(p); }
    await action('edit-command', editors[0].name);
    const stale = (await editors[0].exchange(editCommit(editors[0].epoch, ++editors[0].id, editors[0].revision, editors[0].bounds))).value;
    assert.equal(stale.commitStatus, 1); editors[0].bounds = editors[0].bounds.map((v,i) => i === 0 || i === 3 ? v+1 : v); await capture(editors[0]);
    const foreign = (await editors[1].exchange(editCommit(editors[1].epoch, ++editors[1].id, editors[0].revision, editors[0].bounds))).value;
    assert.equal(foreign.commitStatus, 1); await capture(editors[1]);
    const idle = await measure(`edit-${round}-idle`, false), active = await measure(`edit-${round}-active`, true);
    const duplicate = editors[3], beforeDuplicate = await metrics(), cui = duplicate.cui.length;
    assert.deepEqual((await duplicate.exchange(duplicate.lastFrame)).bytes, duplicate.lastAck);
    assert.equal((await metrics()).commits, beforeDuplicate.commits); assert.equal(duplicate.cui.length, cui);
    const victim = editors[2]; await action('editrevoke', victim.name);
    const denied = (await victim.exchange(editCommit(victim.epoch, ++victim.id, victim.revision, victim.bounds))).value;
    assert.equal(denied.commitStatus, 2); assert.equal(denied.status, 3); assert.equal(denied.bounds, undefined);
    for (const p of editors) await action('edit-audit-end', p.name);
    const recovered = await measure(`edit-${round}-recovered`, false);
    for (const p of peers) {
      assert(p.cui.filter(s => s.startsWith('p|0|')).every(s => { const x=Number(s.split('|')[2]); return x >= 100+p.index*16 && x <= 102+p.index*16; }), 'Foreign CUI selection');
      p.closing = true; p.client.end('Verification complete');
    }
    await waitFor(() => peers.every(p => p.ended), 'all disconnects'); await delay(250);
    const final = await metrics(); assert.equal(final.connections, 0); assert.equal(final.pending, 0); assert.equal(final.tasks, 0);
    const result = { round, transport: transport.evidence, idle, active, recovered, final, staleConflict: true, foreignConflict: true, duplicateAppliedOnce: true, revokedDenied: true };
    writeFileSync(join(serverDir, `edit-${round}-result.json`), JSON.stringify(result, null, 2));
    console.log(`EDIT LOAD PASS: run ${round}, ${active.after.applied-active.before.applied} commits, 32 actors`); return result;
  } finally {
    stopping = true; await Promise.allSettled(workers); workers = [];
    try {
      for (const p of peers) if (!p.ended) { p.closing = true; p.client.end('Fixture cleanup'); }
      await transport?.close?.();
    } finally {
      try { await server.lifecycle.stop({graceMillis:20000}); } finally { if (server.exited) await server.drained; }
    }
  }
}
try {
  for (let round=1; round<=repetitions; round++) runs.push(await run(round));
  assert.equal(hash(join(serverDir, 'companion.jar')), hashes.companion);
  writeFileSync(proof, JSON.stringify({ checkedAt:new Date().toISOString(), baseline, minecraft:version, hashes, actors:32, repetitions, runs }, null, 2));
  console.log('PAPER EDIT LOAD PROOF PASS; owned servers stopped');
} catch (error) { writeFileSync(failure, JSON.stringify({ checkedAt:new Date().toISOString(), hashes, error:String(error), runs }, null, 2)); console.error(error); process.exitCode=1; }
