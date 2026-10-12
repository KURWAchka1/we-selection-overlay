import assert from 'node:assert/strict';
import { spawn, spawnSync } from 'node:child_process';
import { createHash, randomBytes } from 'node:crypto';
import { copyFileSync, createWriteStream, mkdirSync, readFileSync, readdirSync, rmSync, writeFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { createServer } from 'node:net';
import { constants, cpus, setPriority } from 'node:os';
import { join, relative, resolve } from 'node:path';
import { setTimeout as delay } from 'node:timers/promises';
import { queuedRequest, validateQueuedFault, validateLifecycleWindow, validateActiveCycle, sendPlayMovement } from './active-lifecycle.mjs';
import { movement } from './gameplay-workload.mjs';
import { observeOwnedServer } from './owned-server.mjs';
import { channel, hello, request, decode } from './wire-codec.mjs';

const [javaHome, directory, playerArgument, sampleArgument, repeatArgument] = process.argv.slice(2);
const root = resolve(import.meta.dirname, '../..'), serverDir = resolve(directory);
assert.equal(relative(root, serverDir), join('build', 'bridge-active-lifecycle-proof'), 'Never operate a user server');
assert.equal(Number(playerArgument), 32);
const samples = Number(sampleArgument), repetitions = Number(repeatArgument);
assert(Number.isInteger(samples) && samples >= 100 && samples <= 2400 && samples % 20 === 0);
assert(Number.isInteger(repetitions) && repetitions >= 1 && repetitions <= 4);
assert.match(readFileSync(join(serverDir, 'eula.txt'), 'utf8'), /^eula=true\s*$/);
for (const line of ['server-ip=127.0.0.1', 'server-port=25579', 'online-mode=false', 'max-players=32'])
  assert(readFileSync(join(serverDir, 'server.properties'), 'utf8').split(/\r?\n/).includes(line));
const require = createRequire(join(root, 'build/bridge-wire-deps/package.json'));
const minecraft = require('minecraft-protocol');
assert.equal(require('minecraft-protocol/package.json').version, '1.68.0');
assert.equal(require('minecraft-data/package.json').version, '3.117.0');
const hash = path => createHash('sha256').update(readFileSync(path)).digest('hex');
const hashes = { paper: hash(join(serverDir, 'server.jar')), worldEdit: hash(join(serverDir, 'plugins/worldedit.jar')),
  companion: hash(join(serverDir, 'companion.jar')), fixture: hash(join(serverDir, 'plugins/weso-paper-test-fixture.jar')) };
assert.equal(hashes.paper, '5ffef465eeeb5f2a3c23a24419d97c51afd7dbb4923ff42df9a3f58bba1ccfba');
assert.equal(hashes.worldEdit, '0ee152b1be5dfb51500505e2bf5a8c9d66f09c7fa484bf9aea384d7e7b459b06');
const companionName = 'worldedit_selection_overlay-bridge-paper-0.1.0-dev.jar';
const allowed = new Set(['worldedit.jar', 'weso-paper-test-fixture.jar', companionName]);
assert(readdirSync(join(serverDir, 'plugins')).filter(n => n.endsWith('.jar')).every(n => allowed.has(n)));
const restartChannel = 'worldedit_selection_overlay:restart', cuiChannel = 'worldedit:cui';
let cancelled = false;
process.on('SIGINT', () => { cancelled = true; }); process.on('SIGTERM', () => { cancelled = true; });
const results = [];
rmSync(join(serverDir, 'lifecycle-proof.json'), { force: true });
rmSync(join(serverDir, 'lifecycle-failure.json'), { force: true });

async function freePort() {
  const probe = createServer();
  await new Promise((accept, reject) => { probe.once('error', reject); probe.listen(25579, '127.0.0.1', accept); });
  await new Promise((accept, reject) => probe.close(error => error ? reject(error) : accept()));
}

async function run(round) {
  await freePort();
  copyFileSync(join(serverDir, 'companion.jar'), join(serverDir, 'plugins', companionName));
  const config = join(serverDir, 'plugins/WorldEditSelectionOverlayBridge'); mkdirSync(config, { recursive: true });
  writeFileSync(join(config, 'config.yml'), 'limits:\n  max-connections: 1024\n  global-requests-per-second: 100\n  global-burst: 100\n  player-requests-per-second: 4\n  player-burst: 8\n');
  const result = { round, faults: [], recoveries: [], windows: [], validated: false };
  results.push(result);
  const measurements = join(serverDir, 'measurements', `round-${round}`); mkdirSync(measurements, { recursive: true });
  const output = createWriteStream(join(serverDir, `round-${round}.log`));
  const lines = [], partial = { out: '', err: '' }, peers = [], allPeers = [];
  const sent = Array(32).fill(0), received = Array(32).fill(0), movements = Array(32).fill(0), latencies = [];
  let exited = false, traffic = false, trafficTimer, movementTimer, closing = false, faultCount = 0;
  const jfrName = `round-${round}.jfr`; rmSync(join(serverDir, jfrName), { force: true });
  const java = spawn(join(javaHome, 'bin/java.exe'), ['-Xms256m', '-Xmx1024m', '-XX:ActiveProcessorCount=2',
    `-XX:StartFlightRecording=filename=${jfrName},settings=profile,dumponexit=true,maxsize=32m`,
    '-Dweso.bridge.fixture=true', `-Dweso.bridge.measureTicks=${samples}`, '-jar', 'server.jar', '--nogui'],
  { cwd: serverDir, windowsHide: true, stdio: ['pipe', 'pipe', 'pipe'] });
  const server = observeOwnedServer(java);
  java.on('close', () => { exited = true; });
  java.on('error', error => lines.push(`LAUNCH_ERROR ${error.message}`));
  function capture(source, bytes) {
    output.write(bytes); partial[source] += bytes.toString();
    let end;
    while ((end = partial[source].indexOf('\n')) >= 0) {
      lines.push(partial[source].slice(0, end).replace(/\r$/, '')); partial[source] = partial[source].slice(end + 1);
    }
  }
  java.stdout.on('data', bytes => capture('out', bytes)); java.stderr.on('data', bytes => capture('err', bytes));
  async function waitFor(predicate, label, timeout = 15000) {
    const deadline = Date.now() + timeout;
    while (!predicate()) {
      if (cancelled || exited) throw new Error(`Cancelled/server closed during ${label}: ${lines.slice(-10).join('\n')}`);
      assert(!lines.some(s => /Queued lifecycle fixture failed|Gameplay workload edit failed|Unpaired fixture tick|Cannot write fixture/.test(s)), 'Fixture failed');
      const problem = allPeers.find(p => p.errors.length || p.ended && !p.expectedEnd && !closing);
      if (problem) throw new Error(`${label}: ${problem.name}: ${problem.errors.join(', ')} ended=${problem.ended}, reason=${JSON.stringify(problem.disconnectReason)}`);
      if (Date.now() >= deadline) throw new Error(`Timeout: ${label}`);
      await delay(25);
    }
  }
  async function action(action, name = 'WesoLoad00') {
    const start = lines.length; java.stdin.write(`wesofixture ${action} ${name}\n`);
    await waitFor(() => lines.slice(start).some(s => s.includes(`WESO_FIXTURE_OK ${action} ${name}`)), action);
  }
  async function counters() {
    const start = lines.length; java.stdin.write('wesobridge\n');
    await waitFor(() => lines.slice(start).some(s => s.includes('Queue pending=')), 'counters');
    const number = key => {
      const expression = new RegExp(`(?:^|[ =,])${key}=(\\d+)`);
      const match = lines.slice(start).map(s => s.match(expression)).find(Boolean);
      assert(match, `Missing counter ${key}`); return Number(match[1]);
    };
    return { connections: number('connections'), admitted: number('admitted'), throttled: number('throttled'),
      reads: number('reads'), replies: number('replies'), bytes: number('reply bytes'), pending: number('pending'),
      expired: number('expired'), cancelled: number('cancelled'), restartHints: number('hints'), hintBytes: number('hint bytes'),
      maxWaitNanos: number('max wait nanos'), dispatchTasks: number('dispatch tasks') };
  }
  function negotiate(peer, automatic = false) {
    assert(!peer.welcome && !peer.expectedNonce, 'Overlapping hello');
    peer.expectedNonce = randomBytes(16); peer.id = 0; peer.lastReplyId = 0n;
    if (automatic) peer.restartHellos++;
    peer.client.write('custom_payload', { channel, data: hello(peer.expectedNonce, 1, 1, 3) });
  }
  function sendRequest(peer, epoch = peer.welcome.epoch) {
    const id = ++peer.id; peer.client.write('custom_payload', { channel, data: request(epoch, id) }); sent[peer.index]++;
    if (peer.index === 0) { peer.sentTimes.set(String(id), performance.now()); assert(peer.sentTimes.size <= 32); }
    return BigInt(id);
  }
  async function connect(index) {
    const name = `WesoLoad${String(index).padStart(2, '0')}`, start = lines.length;
    const client = minecraft.createClient({ host: '127.0.0.1', port: 25579, username: name, auth: 'offline',
      version: '1.21.11', profilesFolder: false, clientSettings: { viewDistance: 2 }, disableChatSigning: true });
    const peer = { name, index, client, errors: [], ended: false, playReady: false, expectedEnd: false, welcome: null, expectedNonce: null,
      paused: false, id: 0, lastReplyId: 0n, frames: [], closes: 0, hints: [], restartArmed: false, restartHellos: 0,
      next: 0, sentTimes: new Map(), cui: [] };
    peers[index] = peer; allPeers.push(peer);
    client.on('error', error => peer.errors.push(error.message)); client.on('end', () => { peer.ended = true; });
    client.on('login', () => { peer.playReady = true; });
    for (const event of ['disconnect', 'kick_disconnect']) client.on(event, packet => { peer.disconnectReason = packet; });
    client.on('position', packet => client.write('teleport_confirm', { teleportId: packet.teleportId }));
    client.on('custom_payload', packet => {
      try {
        if (packet.channel === cuiChannel) { peer.cui.push(packet.data.toString()); assert(peer.cui.length <= 1024); return; }
        if (packet.channel === restartChannel) {
          assert(index < 28 && peer.restartArmed, 'Unsolicited/duplicate restart hint');
          const bytes = Buffer.from(packet.data); assert.equal(bytes.length, 22);
          assert.equal(bytes.subarray(0, 6).toString('hex'), '574553520101');
          assert(!bytes.subarray(6).equals(Buffer.alloc(16))); assert(!peer.hints.includes(bytes.toString('hex')));
          peer.hints.push(bytes.toString('hex')); peer.restartArmed = false; negotiate(peer, true); return;
        }
        if (packet.channel !== channel) return;
        assert(index < 28, 'Non-consumer received bridge data');
        const value = decode(Buffer.from(packet.data)); peer.frames.push(value); assert(peer.frames.length <= 16384);
        if (value.opcode === 2) {
          assert.deepEqual(value.nonce, peer.expectedNonce); assert.equal(value.status, 0); assert.equal(value.capabilities, 3);
          peer.expectedNonce = null; peer.welcome = value;
        } else if (value.opcode === 5) {
          assert(peer.welcome, 'Close without session');
          assert.deepEqual(value.nonce, peer.welcome.nonce); assert.deepEqual(value.epoch, peer.welcome.epoch);
          peer.welcome = null; peer.closes++; peer.restartArmed = true; peer.sentTimes.clear();
        } else {
          assert(peer.welcome, 'Reply after terminal state'); assert.deepEqual(value.epoch, peer.welcome.epoch);
          assert(value.id > peer.lastReplyId); peer.lastReplyId = value.id;
          if (value.status === 3) { assert(!value.bounds); peer.welcome = null; peer.paused = true; return; }
          assert.equal(value.status, 0);
          const x = 100 + index * 16;
          assert.deepEqual(value.bounds, [x, 64, 20, x + 5, 70, 27], 'Cross-player disclosure');
          assert.equal(value.minY, -64); assert.equal(value.maxY, 319); assert.equal(value.permissions, 7);
          received[index]++;
          if (index === 0) {
            assert(peer.sentTimes.has(value.id.toString()), 'Unsolicited ordinary data');
            latencies.push(performance.now() - peer.sentTimes.get(value.id.toString())); assert(latencies.length <= 8192);
            for (const id of peer.sentTimes.keys()) if (BigInt(id) <= value.id) peer.sentTimes.delete(id);
          }
        }
      } catch (error) { peer.errors.push(error.stack); }
    });
    await waitFor(() => peer.playReady && lines.slice(start).some(s => s.includes(`WESO_FIXTURE_JOIN ${name}`)), `join ${name}`);
    client.write('custom_payload', { channel: 'minecraft:register', data: Buffer.from(index < 28 ? `${channel}\0${restartChannel}` : cuiChannel) });
    return peer;
  }
  async function cuiProof() {
    const peer = peers[28], before = peer.cui.length;
    peer.client.write('custom_payload', { channel: cuiChannel, data: Buffer.from('v|4') });
    await waitFor(() => peer.cui.slice(before).some(s => s.startsWith('s|cuboid'))
      && peer.cui.slice(before).some(s => s.startsWith('p|0|')) && peer.cui.slice(before).some(s => s.startsWith('p|1|')), 'independent CUI');
  }
  async function fault(actionName, index) {
    const label = `fault-${++faultCount}-${actionName}`;
    const path = join(serverDir, 'plugins/WesoBridgeTestFixture', `${label}.json`); rmSync(path, { force: true });
    await action(`queued-${actionName}`, peers[index].name);
    await waitFor(() => lines.some(s => s.includes(`WESO_QUEUED_FAULT_DONE ${label}`)), label);
    const data = JSON.parse(readFileSync(path, 'utf8'));
    writeFileSync(join(measurements, `${label}.json`), JSON.stringify({ ...data, validated: false }, null, 2));
    result.faults.push(data); validateQueuedFault(data);
    writeFileSync(join(measurements, `${label}.json`), JSON.stringify({ ...data, validated: true }, null, 2));
    return data;
  }
  async function measure(label, operation = async () => {}, bridgeActive = true) {
    const before = { sent: [...sent], received: [...received], movements: [...movements], latency: latencies.length };
    const bridgeBefore = bridgeActive ? await counters() : null;
    const path = join(serverDir, 'plugins/WesoBridgeTestFixture', `load-${label}.json`); rmSync(path, { force: true });
    const start = lines.length; java.stdin.write(`wesofixture measure ${label}\n`);
    await waitFor(() => lines.slice(start).some(s => s.includes(`WESO_MEASURE_START ${label}`)), `start ${label}`);
    await delay(100); await operation();
    await waitFor(() => lines.slice(start).some(s => s.includes(`WESO_MEASURE_DONE ${label}`)), label, samples * 100 + 30000);
    const data = JSON.parse(readFileSync(path, 'utf8'));
    const after = label === 'stopwe' ? null : await counters();
    const times = latencies.slice(before.latency);
    Object.assign(data, { sent: sent.map((n, i) => n - before.sent[i]), received: received.map((n, i) => n - before.received[i]),
      movementPackets: movements.map((n, i) => n - before.movements[i]), bridgeBefore, bridgeAfter: after,
      ordinaryReplyLatencies: { count: times.length, maxMillis: Math.max(0, ...times) } });
    result.windows.push(data);
    writeFileSync(join(measurements, `${label}.json`), JSON.stringify({ ...data, validated: false }, null, 2));
    validateLifecycleWindow(data, samples);
    writeFileSync(join(measurements, `${label}.json`), JSON.stringify({ ...data, validated: true }, null, 2));
    console.log(`PASS round-${round}/${label}: ${samples} moving-gameplay ticks, p99=${data.p99.toFixed(3)}ms`);
  }
  async function staleProbe(peer, epoch) {
    peer.paused = true; await delay(1000);
    const count = peer.frames.length; sendRequest(peer, epoch); await delay(1000);
    assert.equal(peer.frames.length, count, 'Previous epoch disclosed data');
    const id = sendRequest(peer); await waitFor(() => peer.lastReplyId === id, 'explicit fresh-epoch read');
    peer.paused = false;
  }
  try {
    if (java.pid) setPriority(java.pid, constants.priority.PRIORITY_BELOW_NORMAL);
    await waitFor(() => lines.some(s => /Done \(/.test(s)), 'startup', 180000);
    assert(lines.some(s => s.includes('Experimental read-only bridge enabled')));
    for (let index = 0; index < 32; index++) await connect(index);
    await action('control', 'baseline'); await action('control', 'gameplay');
    let step = 0;
    movementTimer = setInterval(() => {
      for (const p of peers) {
        try { if (sendPlayMovement(p, movement(p.index, step))) movements[p.index]++; }
        catch (error) { p.errors.push(error.stack); }
      }
      step++;
    }, 200);
    for (const peer of peers.slice(0, 28)) { negotiate(peer); await waitFor(() => peer.welcome, `hello ${peer.name}`); }
    assert.equal((await counters()).reads, 0); await cuiProof();
    trafficTimer = setInterval(() => {
      if (!traffic) return;
      const now = performance.now();
      for (const peer of peers.slice(0, 28)) {
        if (peer.ended || peer.paused || !peer.welcome || now < peer.next) continue;
        peer.next = now + (peer.index === 0 ? 500 : 50);
        try { for (let n = 0; n < (peer.index === 0 ? 1 : 4); n++) sendRequest(peer); }
        catch (error) { peer.errors.push(error.stack); }
      }
    }, 25);
    traffic = true; await delay(15000); await measure('flood');
    await measure('revoke', async () => {
      const peer = peers[1], old = peer.welcome;
      const data = await fault('revoke', 1), queued = queuedRequest(data);
      await waitFor(() => peer.frames.some(f => f.opcode === 4 && f.id === queued.id && f.status === 3), 'queued denial');
      assert(!peer.frames.some(f => f.opcode === 4 && f.epoch.equals(queued.epoch) && f.id >= queued.id && f.status === 0));
      await delay(1000); await action('grant', peer.name); negotiate(peer);
      await waitFor(() => peer.welcome, 'renewed permission'); assert(!peer.welcome.epoch.equals(old.epoch));
      await staleProbe(peer, old.epoch);
    });
    await measure('disconnect', async () => {
      const old = peers[2]; old.expectedEnd = true;
      const epoch = Buffer.from(old.welcome.epoch); await fault('disconnect', 2);
      await waitFor(() => old.ended, 'real disconnect'); await delay(300);
      const renewed = await connect(2); renewed.paused = true; negotiate(renewed);
      await waitFor(() => renewed.welcome, 'same-identity reconnect'); assert(!renewed.welcome.epoch.equals(epoch));
      await staleProbe(renewed, epoch);
    });
    for (let cycle = 1; cycle <= 3; cycle++) await measure(`restart-${cycle}`, async () => {
      const old = peers.slice(0, 28).map(p => p.welcome), closes = peers.slice(0, 28).map(p => p.closes);
      await fault('bridgeoff', 3); await action('channels');
      await waitFor(() => peers.slice(0, 28).every((p, i) => p.closes === closes[i] + 1 && !p.welcome), 'all active closes');
      await cuiProof(); await delay(300);
      const start = performance.now(); await action('bridgeon'); await action('bridgeactive');
      await waitFor(() => peers.slice(0, 28).every(p => p.welcome), 'one-shot active restart');
      const recoveryMillis = performance.now() - start;
      for (let index = 0; index < 28; index++) {
        const p = peers[index]; assert(!p.welcome.epoch.equals(old[index].epoch)); assert(!p.welcome.nonce.equals(old[index].nonce));
        assert.equal(p.restartHellos, cycle); assert.equal(p.hints.length, cycle);
      }
      const c = await counters(); assert.equal(c.restartHints, 28); assert.equal(c.hintBytes, 28 * 22);
      await staleProbe(peers[3], old[3].epoch);
      result.recoveries.push({ cycle, recoveryMillis, restartHints: c.restartHints, hintBytes: c.hintBytes });
    });
    traffic = false; await delay(1500); await measure('idle');
    traffic = true; await delay(3000);
    await measure('stopwe', async () => {
      const closes = peers.slice(0, 28).map(p => p.closes); await fault('stopwe', 4); await action('channels');
      await waitFor(() => peers.slice(0, 28).every((p, i) => p.closes === closes[i] + 1 && !p.welcome), 'dependency closes');
      const hints = peers.slice(0, 28).map(p => p.hints.length);
      await action('bridgeon'); await action('channels'); await delay(500);
      assert(peers.slice(0, 28).every((p, i) => p.hints.length === hints[i]));
    });
    traffic = false; assert(allPeers.every(p => p.errors.length === 0));
    assert.equal(result.faults.length, 6); assert.equal(result.recoveries.length, 3); assert.equal(result.windows.length, 8);
    assert.equal(hash(join(serverDir, 'companion.jar')), hashes.companion);
    assert.equal(hash(join(serverDir, 'plugins/weso-paper-test-fixture.jar')), hashes.fixture);
    clearInterval(movementTimer); movementTimer = null;
    closing = true; for (const peer of peers) peer.client.end('lifecycle verification complete');
    await waitFor(() => peers.every(p => p.ended), 'final disconnect'); await action('channels');
    assert(allPeers.every(p => p.errors.length === 0));
  } finally {
    if (trafficTimer) clearInterval(trafficTimer); if (movementTimer) clearInterval(movementTimer);
    closing = true; for (const peer of allPeers) if (!peer.ended) peer.client.end('fixture shutdown');
    try { await server.stop(); } finally { await new Promise(resolve => output.end(resolve)); }
  }
  const summary = spawnSync(join(javaHome, 'bin/jfr.exe'), ['summary', join(serverDir, jfrName)], { encoding: 'utf8', windowsHide: true, timeout: 30000 });
  assert.equal(summary.status, 0, summary.stderr); writeFileSync(join(serverDir, `round-${round}-jfr-summary.txt`), summary.stdout);
  validateActiveCycle(result, samples); result.validated = true;
}

try {
  for (let round = 1; round <= repetitions; round++) await run(round);
  await freePort();
  writeFileSync(join(serverDir, 'lifecycle-proof.json'), JSON.stringify({ checkedAt: new Date().toISOString(),
    minecraft: '1.21.11', players: 32, bridgePlayers: 28, samples, repetitions, javaHome, node: process.version,
    processorModel: cpus()[0]?.model, logicalProcessors: cpus().length, hashes, results,
    limitations: 'Functional active-load proof, not a paired performance baseline. Actual queued wire requests observed with test-only reflection before public lifecycle actions. Moving headless TCP clients, 16 AI cows and fixed WorldEdit batches; batches pause when WorldEdit disables. Epoch replays observe no disclosure but do not prove every replay passed rate admission. No native GPU, ungraceful restart, proxy switch or heavy transfer claim.' }, null, 2));
  console.log('PAPER ACTIVE LIFECYCLE PROOF PASS; all owned processes stopped');
} catch (error) {
  writeFileSync(join(serverDir, 'lifecycle-failure.json'), JSON.stringify({ checkedAt: new Date().toISOString(), hashes,
    error: error.stack, results }, (_, value) => typeof value === 'bigint' ? value.toString() : value, 2));
  console.error(error); process.exitCode = 1;
}
