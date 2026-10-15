import assert from 'node:assert/strict';
import { spawn, spawnSync } from 'node:child_process';
import { createHash, randomBytes } from 'node:crypto';
import { copyFileSync, cpSync, createWriteStream, existsSync, mkdirSync, readFileSync, readdirSync, rmSync, writeFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { createServer } from 'node:net';
import { constants, cpus, setPriority } from 'node:os';
import { join, relative, resolve } from 'node:path';
import { setTimeout as delay } from 'node:timers/promises';
import { channel, hello, request, decode } from './wire-codec.mjs';
import { FrozenFixtureWorld } from './frozen-fixture-world.mjs';
import { observeOwnedServer } from './owned-server.mjs';
import { recordLoadWindow } from './load-window.mjs';
import { bridgeConsumerCount, movement } from './gameplay-workload.mjs';
import { loadHost } from './gameplay-host.mjs';
import { startJavaLoadClients } from './java-load-clients.mjs';
import { paperProfilerConfig, verifyProfilerLog } from './edit-diagnostic-policy.mjs';

const [javaHome, directory, playerArgument, sampleArgument, repeatArgument, mode = 'load'] = process.argv.slice(2);
assert(['load', 'baseline', 'restart', 'timeline', 'fair', 'gameplay'].includes(mode));
const gameplay = mode === 'gameplay';
const host = loadHost(mode,process.argv.slice(8)), {version,withoutSpark,tuple} = host;
const baselineOnly = ['baseline', 'timeline'].includes(mode);
const restartLoad = mode === 'restart';
const root = resolve(import.meta.dirname, '../..'), serverDir = resolve(directory);
assert.equal(relative(root, serverDir), host.directory, 'Never operate a user server');
const frozen = baselineOnly || gameplay ? new FrozenFixtureWorld(serverDir) : null;
let worldDigest;
const players = Number(playerArgument), samples = Number(sampleArgument), repetitions = Number(repeatArgument);
assert(Number.isInteger(players) && players >= 2 && players <= 32);
assert(Number.isInteger(samples) && samples >= 100 && samples <= 2400);
assert(Number.isInteger(repetitions) && repetitions >= 1 && repetitions <= 4);
if (gameplay) assert(players >= 4 && samples % 20 === 0);
if (version === '26.2') assert(players === 32 && samples === 400 && repetitions === 2, 'Exact gameplay comparison requires 32 actors, 400 ticks and reversed-order pairs');
const bridgePlayers = gameplay ? bridgeConsumerCount(players) : players;
assert.match(readFileSync(join(serverDir, 'eula.txt'), 'utf8'), /^eula=true\s*$/);
const properties = readFileSync(join(serverDir, 'server.properties'), 'utf8');
for (const line of ['server-ip=127.0.0.1', 'server-port=25579', 'online-mode=false', 'max-players=32'])
  assert(properties.split(/\r?\n/).includes(line));
const require = createRequire(join(root, 'build/bridge-wire-deps/package.json'));
const minecraft = version === '26.2' ? null : require('minecraft-protocol');
if (minecraft) assert.equal(require('minecraft-protocol/package.json').version, '1.68.0');
if (gameplay && minecraft) assert.equal(require('minecraft-data/package.json').version, '3.117.0');
const hash = path => createHash('sha256').update(readFileSync(path)).digest('hex');
assert.equal(hash(join(serverDir, 'server.jar')), tuple.paperSha256);
assert.equal(createHash(tuple.worldeditAlgorithm).update(readFileSync(join(serverDir,'plugins/worldedit.jar'))).digest('hex'),tuple.worldeditHash);
const companionName = 'worldedit_selection_overlay-bridge-paper-0.1.0-dev.jar';
const allowedPlugins = new Set(['worldedit.jar', 'weso-paper-test-fixture.jar', companionName]);
assert(readdirSync(join(serverDir, 'plugins')).filter(n => n.endsWith('.jar')).every(n => allowedPlugins.has(n)));
const companionHash = hash(join(serverDir, 'companion.jar'));
const fixtureHash = hash(join(serverDir, 'plugins/weso-paper-test-fixture.jar'));
const results = [], checks = [];
const lifecycleResults = [];
const runEvidence = [];
const restartChannel = 'worldedit_selection_overlay:restart';
let cancelled = false;
process.on('SIGINT', () => { cancelled = true; });
process.on('SIGTERM', () => { cancelled = true; });
if (version === '26.2') for (const name of ['load-proof.json','load-failure.json','prepare.log','1-absent.log'])
  assert(!existsSync(join(serverDir,name)), 'Archive previous gameplay attempts before rerunning');
else {
  rmSync(join(serverDir, 'load-proof.json'), { force: true });
  rmSync(join(serverDir, 'load-failure.json'), { force: true });
}
const recordingSettings = version === '26.2' ? join(serverDir,'gameplay.jfc') : 'profile';
if (version === '26.2') {
  const configured = spawnSync(join(javaHome,'bin/jfr.exe'),['configure','--input','profile','--output',recordingSettings,
    'jdk.ExecuteVMOperation#enabled=true','jdk.ExecuteVMOperation#threshold=0ms',
    'jdk.SafepointBegin#enabled=true','jdk.SafepointBegin#threshold=0ms'],{windowsHide:true,encoding:'utf8',timeout:30000});
  assert.equal(configured.status,0,configured.stderr || configured.error?.message);
}

async function freePort() {
  const probe = createServer();
  await new Promise((accept, reject) => { probe.once('error', reject); probe.listen(25579, '127.0.0.1', accept); });
  await new Promise((accept, reject) => probe.close(error => error ? reject(error) : accept()));
}
function delta(after, before) {
  const gauges = new Set(['connections', 'pending', 'peakPending', 'maxWaitNanos', 'maxSliceNanos', 'dispatchTasks']);
  return Object.fromEntries(Object.keys(after).filter(k => !gauges.has(k)).map(k => [k, after[k] - before[k]]));
}
async function run(installed, round, prepare = false) {
  await freePort();
  const runName = prepare ? 'prepare' : `${round}-${installed ? 'installed' : 'absent'}`;
  if (withoutSpark) {
    const config = join(serverDir, 'config'); mkdirSync(config, { recursive: true });
    // Owned fixture only: the same pinned Paper profiler setting in both groups.
    const input=JSON.stringify(paperProfilerConfig(false),null,2);
    writeFileSync(join(config, 'paper-global.yml'),input);
    if (version === '26.2') writeFileSync(join(serverDir,`${runName}-paper-global.input.json`),input);
  }
  if (frozen && !prepare) worldDigest = frozen.restore();
  const plugin = join(serverDir, 'plugins', companionName);
  if (installed) copyFileSync(join(serverDir, 'companion.jar'), plugin);
  else rmSync(plugin, { force: true });
  // Fix the experimental policy explicitly; never reuse a prior hand-edited config.
  const config = join(serverDir, 'plugins/WorldEditSelectionOverlayBridge');
  mkdirSync(config, { recursive: true });
  writeFileSync(join(config, 'config.yml'), 'limits:\n  max-connections: 1024\n  global-requests-per-second: 100\n  global-burst: 100\n  player-requests-per-second: 4\n  player-burst: 8\n');
  const output = createWriteStream(join(serverDir, `${runName}.log`));
  const lines = [], peers = [], pending = { out: '', err: '' }, ordinaryLatencies = [];
  let exited = false, phase = 'none', timer, movementTimer, disconnecting = false, transport;
  const jfrName = `${runName}.jfr`;
  rmSync(join(serverDir, jfrName), { force: true });
  const java = spawn(join(javaHome, 'bin/java.exe'), ['-Xms256m', '-Xmx1024m', '-XX:ActiveProcessorCount=2',
    `-XX:StartFlightRecording=filename=${jfrName},settings=${recordingSettings},dumponexit=true,maxsize=32m`,
    '-Dweso.bridge.fixture=true', `-Dweso.bridge.measureTicks=${samples}`, '-jar', 'server.jar', '--nogui'],
  { cwd: serverDir, windowsHide: true, stdio: ['pipe', 'pipe', 'pipe'] });
  const server = observeOwnedServer(java);
  java.on('close', () => { exited = true; });
  java.on('error', error => { lines.push(`LAUNCH_ERROR ${error.message}`); });
  function capture(source, bytes) {
    output.write(bytes); pending[source] += bytes.toString();
    let end;
    while ((end = pending[source].indexOf('\n')) >= 0) {
      lines.push(pending[source].slice(0, end).replace(/\r$/, '')); pending[source] = pending[source].slice(end + 1);
    }
  }
  java.stdout.on('data', bytes => capture('out', bytes));
  java.stderr.on('data', bytes => capture('err', bytes));
  async function waitFor(predicate, label, timeout = 15000) {
    const deadline = Date.now() + timeout;
    while (!predicate()) {
      if (cancelled || exited) throw new Error(`Cancelled/server closed during ${label}: ${lines.slice(-10).join('\n')}`);
      assert(!transport?.fault, String(transport?.fault));
      assert(!lines.some(s => /Gameplay workload edit failed|Unpaired fixture tick|Cannot write fixture|generated an exception whilst handling plugin message|NoClassDefFoundError|VerifyError/.test(s)), 'Fixture failed');
      if (Date.now() >= deadline) throw new Error(`Timeout: ${label}`);
      const problem = peers.find(p => p.errors.length || p.ended);
      if (problem && !disconnecting) throw new Error(`${problem.name}: ${problem.errors.join(', ')}; ended=${problem.ended}`);
      await delay(25);
    }
  }
  async function counters() {
    if (!installed) return null;
    const start = lines.length;
    java.stdin.write('wesobridge\n');
    await waitFor(() => lines.slice(start).some(s => s.includes('Queue pending=')), 'bridge counters');
    const a = lines.slice(start).find(s => s.includes('connections='));
    const b = lines.slice(start).find(s => s.includes('Selection metadata reads='));
    const c = lines.slice(start).find(s => s.includes('Restart hints='));
    const q = lines.slice(start).find(s => s.includes('Queue pending='));
    const number = (line, key) => Number(line.match(new RegExp(`${key}=(\\d+)`))[1]);
    return { connections: number(a, 'connections'), admitted: number(a, 'admitted'), throttled: number(a, 'throttled'),
      malformed: number(a, 'malformed'), reads: number(b, 'reads'), replies: number(b, 'replies'), bytes: number(b, 'reply bytes'),
      restartHints: number(c, 'hints'), hintBytes: number(c, 'bytes'), pending: number(q, 'pending'),
      peakPending: number(q, 'peak'), queued: number(q, 'queued'), expired: number(q, 'expired'),
      cancelled: number(q, 'cancelled'), maxWaitNanos: number(q, 'max wait nanos'),
      maxSliceNanos: number(q, 'max slice nanos'), dispatchTasks: number(q, 'dispatch tasks') };
  }
  async function consoleAction(action) {
    const start = lines.length;
    java.stdin.write(`wesofixture ${action} WesoLoad00\n`);
    await waitFor(() => lines.slice(start).some(s => s.includes(`WESO_FIXTURE_OK ${action} WesoLoad00`)), action);
  }

  async function restartCycle(cycle) {
    const before = await counters();
    const old = peers.map(p => p.welcome);
    const oldHints = peers.map(p => p.hints.length);
    const oldClosed = peers.map(p => p.closed);
    await consoleAction('bridgeoff');
    await consoleAction('channels');
    await waitFor(() => peers.every((p, i) => p.closed === oldClosed[i] + 1 && p.restartArmed && p.welcome === null), 'all negotiated closes');
    await delay(1000);
    assert(peers.every((p, i) => p.closed === oldClosed[i] + 1 && p.hints.length === oldHints[i]), 'Stopped service sent more control traffic');
    const started = performance.now();
    await consoleAction('bridgeon');
    await consoleAction('bridgeactive');
    await waitFor(() => peers.every(p => p.welcome), '32-way fresh negotiation');
    const recoveryMillis = performance.now() - started;
    const negotiated = await counters();
    assert.equal(negotiated.connections, players);
    assert.equal(negotiated.admitted, players);
    assert.equal(negotiated.replies, players);
    assert.equal(negotiated.reads, 0, 'Recovery implicitly read metadata');
    assert.equal(negotiated.restartHints, players); assert.equal(negotiated.hintBytes, players * 22);
    assert.equal(negotiated.throttled, 0); assert.equal(negotiated.malformed, 0);
    for (let i = 0; i < peers.length; i++) {
      const p = peers[i];
      assert.equal(p.hints.length, oldHints[i] + 1);
      assert.notDeepEqual(p.welcome.epoch, old[i].epoch);
      assert.notDeepEqual(p.welcome.nonce, old[i].nonce);
      assert.equal(p.restartHellos, cycle);
      // Old epochs cannot read the replacement core even after a fresh hello.
      p.client.write('custom_payload', { channel, data: request(old[i].epoch, 1) });
    }
    await delay(500);
    assert.equal((await counters()).reads, 0, 'A previous core epoch disclosed metadata');
    for (const p of peers) {
      p.client.write('custom_payload', { channel, data: request(p.welcome.epoch, ++p.id) }); p.sent++;
    }
    await waitFor(() => peers.every(p => p.lastReplyId === 1n), 'explicit own selection after recovery');
    const explicit = await counters();
    assert.equal(explicit.reads, players); assert.equal(explicit.replies, players * 2);
    await delay(1000);
    assert.deepEqual(await counters(), explicit, 'Restarted service did background work');
    const record = { run: runName, cycle, players, recoveryMillis, before, negotiated, explicit };
    lifecycleResults.push(record);
    console.log(`PASS ${runName}/restart-${cycle}: ${players} fresh hellos, ${players * 22} hint bytes, recovery=${recoveryMillis.toFixed(1)}ms`);
  }
  async function measure(label) {
    await delay(5000);
    const before = await counters();
    const latencyBefore = ordinaryLatencies.length;
    const sentBefore = peers.map(p => p.sent), repliesBefore = peers.map(p => p.replies);
    const movementBefore = peers.map(p => p.movementPackets);
    const teleportsBefore = peers.map(p => p.client.teleports ?? 0), framesBefore = peers.map(p => p.bridgeFrames);
    const path = join(serverDir, 'plugins/WesoBridgeTestFixture', `load-${label}.json`);
    rmSync(path, { force: true });
    const start = lines.length;
    java.stdin.write(`wesofixture measure ${label}\n`);
    await waitFor(() => lines.slice(start).some(s => s.includes(`WESO_MEASURE_DONE ${label}`)), label, samples * 100 + 30000);
    const data = JSON.parse(readFileSync(path, 'utf8'));
    if (mode === 'timeline' || mode === 'fair' || gameplay) assert.equal(data.tickTimelineVersion, 1, 'Fixture has no per-tick markers');
    const after = await counters();
    data.bridge = installed ? delta(after, before) : null;
    data.connections = after?.connections ?? 0;
    if (installed) data.queueAfter = { pending: after.pending, peak: after.peakPending, expired: after.expired,
      maxWaitNanos: after.maxWaitNanos, maxSliceNanos: after.maxSliceNanos, tasks: after.dispatchTasks };
    const latencies = ordinaryLatencies.slice(latencyBefore).sort((a, b) => a - b);
    data.ordinaryReplyLatencies = { count: latencies.length, maxMillis: latencies.at(-1) ?? 0,
      p95Millis: latencies[Math.ceil(latencies.length * .95) - 1] ?? 0 };
    data.sent = peers.map((p, i) => p.sent - sentBefore[i]);
    data.received = peers.map((p, i) => p.replies - repliesBefore[i]);
    if (gameplay) data.movementPackets = peers.map((p, i) => p.movementPackets - movementBefore[i]);
    if (version === '26.2') {
      data.teleports = peers.map((p,i) => p.client.teleports - teleportsBefore[i]);
      data.bridgeFrames = peers.map((p,i) => p.bridgeFrames - framesBefore[i]);
    }
    const runDirectory = join(serverDir, 'measurements', runName);
    mkdirSync(runDirectory, { recursive: true });
    recordLoadWindow(join(runDirectory, `${label}.json`), data, { players, samples, installed, baselineOnly, gameplay, bridgePlayers });
    results.push({ run: runName, worldDigest, ...data, tickMillis: undefined });
    if (baselineOnly) checks.push(`${runName}: identical frozen world, zero chunk loads/generation and silent bridge work`);
    console.log(`PASS ${runName}/${label}: p95=${data.p95.toFixed(3)}ms p99=${data.p99.toFixed(3)}ms reads=${data.bridge?.reads ?? 0}`);
  }
  try {
    if (java.pid) setPriority(java.pid, constants.priority.PRIORITY_BELOW_NORMAL);
    await waitFor(() => lines.some(s => /Done \(/.test(s)), 'Paper startup', 180000);
    assert.equal(lines.some(s => s.includes('Experimental read-only bridge enabled')), installed);
    assert(!lines.some(s => /Error occurred while enabling/.test(s)));
    if (withoutSpark) {
      assert(lines.some(s => s.includes('spark profiler will not be enabled because it is currently disabled')));
      assert(!lines.some(s => s.includes('Starting background profiler')));
    }
    if (version === '26.2') {
      verifyProfilerLog(lines.join('\n'),false);
      transport = startJavaLoadClients(javaHome,join(serverDir,`${runName}-clients.log`));
      await waitFor(()=>transport.ready,'exact moving codec helper');
      const archive=join(serverDir,'load-client-artifacts');
      if (!existsSync(archive)) cpSync(join(root,'tools/bridge-load-clients/build/install/weso-bridge-load-clients/lib'),archive,{recursive:true,errorOnExist:true,force:false});
      for (const artifact of transport.evidence.artifacts) assert.equal(hash(join(archive,artifact.name)),artifact.sha256);
    }
    for (let index = 0; index < players; index++) {
      const name = `WesoLoad${String(index).padStart(2, '0')}`;
      const client = (transport ?? minecraft).createClient({ host: '127.0.0.1', port: 25579, username: name,
        auth: 'offline', version, profilesFolder: false, clientSettings: { viewDistance: 2 }, disableChatSigning: true });
      const peer = { client, name, index, errors: [], ended: false, playReady: false, sent: 0, replies: 0, welcome: null, id: 0, next: 0,
        hints: [], closed: 0, restartArmed: false, restartHellos: 0, expectedNonce: null, sentTimes: new Map(), movementPackets: 0, bridgeFrames:0, cui:0 };
      peers.push(peer);
      client.on('error', error => peer.errors.push(error.message));
      client.on('end', () => { peer.ended = true; });
      client.on('login', () => { peer.playReady = true; });
      client.on('playerJoin', () => { peer.playReady = true; });
      if (gameplay && !transport) client.on('position', packet => {
        // Acknowledge server teleports before sending the bounded synthetic flight route.
        client.write('teleport_confirm', { teleportId: packet.teleportId });
      });
      client.on('custom_payload', packet => {
        if (packet.channel === 'worldedit:cui') { peer.cui++; return; }
        if (packet.channel === restartChannel) {
          try {
            assert(restartLoad && installed, 'Unsupported restart hint');
            const bytes = Buffer.from(packet.data);
            assert.equal(bytes.length, 22); assert.equal(bytes.subarray(0, 6).toString('hex'), '574553520101');
            assert(!bytes.subarray(6).equals(Buffer.alloc(16)));
            const generation = bytes.toString('hex');
            assert(!peer.hints.includes(generation), 'Repeated startup generation');
            peer.hints.push(generation);
            if (peer.restartArmed) {
              peer.restartArmed = false; peer.restartHellos++;
              peer.expectedNonce = randomBytes(16);
              client.write('custom_payload', { channel, data: hello(peer.expectedNonce, 1, 1, 3) });
            }
          } catch (error) { peer.errors.push(error.stack); }
          return;
        }
        if (packet.channel !== channel) return;
        peer.bridgeFrames++;
        try {
          assert(installed, 'Absent companion sent bridge traffic');
          const value = decode(Buffer.from(packet.data));
          if (value.opcode === 2) {
            assert.equal(value.status, 0);
            if (restartLoad) { assert.deepEqual(value.nonce, peer.expectedNonce); assert.equal(value.capabilities, 3); }
            peer.welcome = value;
          } else if (value.opcode === 5) {
            assert(restartLoad && peer.welcome, 'Unexpected close');
            assert.deepEqual(value.nonce, peer.welcome.nonce); assert.deepEqual(value.epoch, peer.welcome.epoch);
            peer.closed++; peer.restartArmed = true; peer.welcome = null; peer.id = 0; peer.lastReplyId = 0n;
          }
          else {
            assert.equal(value.status, 0); assert.deepEqual(value.epoch, peer.welcome.epoch);
            const x = 100 + index * 16;
            assert.deepEqual(value.bounds, [x, 64, 20, x + 5, 70, 27], 'Cross-player data disclosure');
            assert.equal(value.minY, -64); assert.equal(value.maxY, 319); assert.equal(value.permissions, 7);
            assert(value.id > peer.lastReplyId); peer.lastReplyId = value.id; peer.replies++;
            if (peer.index === 0 && peer.sentTimes.has(value.id.toString())) {
              ordinaryLatencies.push(performance.now() - peer.sentTimes.get(value.id.toString()));
              assert(ordinaryLatencies.length <= 8192, 'Unbounded fixture latency samples');
              for (const id of peer.sentTimes.keys()) if (BigInt(id) <= value.id) peer.sentTimes.delete(id);
            }
          }
        } catch (error) { peer.errors.push(error.stack); }
      });
      await waitFor(() => peer.playReady && lines.some(s => s.includes(`WESO_FIXTURE_JOIN ${name}`)), name);
      if (index < bridgePlayers)
        client.write('custom_payload', { channel: 'minecraft:register', data: Buffer.from(restartLoad ? `${channel}\0${restartChannel}` : transport ? `${channel}\0worldedit:cui` : channel) });
      else if (transport) client.write('custom_payload',{channel:'minecraft:register',data:Buffer.from('worldedit:cui')});
      if (transport) {
        client.write('custom_payload',{channel:'worldedit:cui',data:Buffer.from('v|4')});
        await waitFor(()=>peer.cui>0 && client.teleports>0,`CUI and initial position ${name}`);
      }
    }
    if (baselineOnly || gameplay) {
      const start = lines.length;
      java.stdin.write('wesofixture control baseline\n');
      await waitFor(() => lines.slice(start).some(s => s.includes('WESO_FIXTURE_OK control baseline')), 'controlled baseline rules');
    }
    if (gameplay) {
      const start = lines.length;
      java.stdin.write('wesofixture control gameplay\n');
      await waitFor(() => lines.slice(start).some(s => s.includes('WESO_FIXTURE_OK control gameplay')), 'gameplay setup');
      await delay(1000);
      let step = 0;
      movementTimer = setInterval(() => {
        for (const peer of peers) {
          try { peer.client.write('position', movement(peer.index, step)); peer.movementPackets++; }
          catch (error) { peer.errors.push(error.stack); }
        }
        step++;
      }, 200);
    }
    await delay(15000);
    if (!prepare) await measure('silent');
    if (!prepare && (installed && !baselineOnly || gameplay)) {
      const consumers = peers.slice(0, bridgePlayers);
      if (installed) for (const peer of consumers) {
        peer.lastReplyId = 0n;
        peer.expectedNonce = randomBytes(16);
        peer.client.write('custom_payload', { channel, data: hello(peer.expectedNonce, 1, restartLoad ? 1 : 0, restartLoad ? 3 : 1) });
        await waitFor(() => peer.welcome, `${peer.name} hello`);
      }
      if (installed) assert.equal((await counters()).reads, 0);
      await measure('idle');
      timer = setInterval(() => {
        const now = performance.now();
        if (!installed) return;
        const order = phase === 'ordered-last' ? [...consumers.slice(1), consumers[0]] : consumers;
        for (const peer of order) {
          if (phase === 'none' || (phase === 'one' && peer.index !== 0) || now < peer.next) continue;
          const flood = phase === 'flood' && peer.index !== 0;
          const ordered = phase.startsWith('ordered-') && peer.index !== 0;
          peer.next = now + (flood ? 50 : ordered ? 250 : 500);
          for (let count = 0; count < (flood ? 4 : 1); count++) {
            try {
              peer.client.write('custom_payload', { channel, data: request(peer.welcome.epoch, ++peer.id) }); peer.sent++;
              if (peer.index === 0) {
                peer.sentTimes.set(String(peer.id), performance.now());
                assert(peer.sentTimes.size <= 32, 'Ordinary requests never completed');
              }
            } catch (error) { peer.errors.push(error.stack); phase = 'none'; }
          }
        }
      }, 25);
      for (const name of ['one', 'many', 'flood', ...(mode === 'fair' ? ['ordered-first', 'ordered-last'] : [])]) {
        phase = name; await measure(name);
      }
      phase = 'none';
      if (restartLoad) {
        await delay(1500);
        for (let cycle = 1; cycle <= 3; cycle++) await restartCycle(cycle);
      }
      await measure('recovery');
      clearInterval(timer); timer = null;
      if (movementTimer) { clearInterval(movementTimer); movementTimer = null; }
      disconnecting = true;
      for (const peer of peers) peer.client.end('load verification complete');
      const deadline = Date.now() + 15000;
      while (!peers.every(p => p.ended) && Date.now() < deadline) await delay(25);
      assert(peers.every(p => p.ended));
      await delay(300);
      const disconnected = await counters();
      if (installed) {
        assert.equal(disconnected.connections, 0);
        assert.equal(disconnected.pending, 0); assert.equal(disconnected.dispatchTasks, 0);
      }
      checks.push(`${runName}: ${players}-way ownership, bounded traffic, legitimate progress, idle recovery, disconnect cleanup`);
    }
    assert.equal(hash(join(serverDir, 'companion.jar')), companionHash, 'Candidate changed during test');
    if (version === '26.2' && !prepare) runEvidence.push({run:runName,worldDigest,transport:transport.evidence,
      profilerInputSha256:hash(join(serverDir,`${runName}-paper-global.input.json`)),
      recordingSettingsSha256:hash(recordingSettings),disconnected:installed ? await counters() : null,
      actors:peers.map(p=>({name:p.name,bridgeFrames:p.bridgeFrames,cui:p.cui,teleports:p.client.teleports,movementPackets:p.movementPackets}))});
  } finally {
    if (timer) clearInterval(timer);
    if (movementTimer) clearInterval(movementTimer);
    disconnecting = true;
    try {
      for (const peer of peers) if (!peer.ended) peer.client.end('fixture shutdown');
      if (transport) await transport.close();
    }
    finally { try { await server.stop(); }
      finally { await new Promise(resolve => output.end(resolve)); } }
  }
  if (version === '26.2') copyFileSync(join(serverDir,'config/paper-global.yml'),join(serverDir,`${runName}-paper-global.effective.yml`));
  const summary = spawnSync(join(javaHome, 'bin/jfr.exe'), ['summary', join(serverDir, jfrName)], { encoding: 'utf8', windowsHide: true, timeout: 30000 });
  assert.equal(summary.status, 0, summary.stderr);
  writeFileSync(join(serverDir, `${runName}-jfr-summary.txt`), summary.stdout);
  if (prepare) worldDigest = frozen.capture();
}

try {
  if (frozen && !frozen.exists()) await run(false, 0, true);
  for (let round = 1; round <= repetitions; round++) {
    // Reverse order on the repeat to expose gross warm-up/order bias.
    for (const installed of (round % 2 ? [false, true] : [true, false])) await run(installed, round);
  }
  await freePort();
  writeFileSync(join(serverDir, 'load-proof.json'), JSON.stringify({ checkedAt: new Date().toISOString(),
    minecraft: version, mode, withoutSpark, worldDigest, players, bridgePlayers, samples, repetitions, javaHome, node: process.version,
    processorModel: cpus()[0]?.model, logicalProcessors: cpus().length,
    serverHash: hash(join(serverDir, 'server.jar')), worldEditHash: hash(join(serverDir, 'plugins/worldedit.jar')),
    companionHash, fixtureHash, checks, results, lifecycleResults, runEvidence,
    limitations: gameplay ? 'Identical frozen starting worlds, bounded flight routes, 16 live AI cows and 512-block WorldEdit batches per 20 ticks; at least one eighth of clients never negotiate the bridge. All phases matched absent/installed. AI decisions and network timing are not deterministic. Preloaded area, shared laptop and headless TCP clients, not arbitrary production gameplay or native GPU rendering.' : baselineOnly ? 'Controlled silent baseline from identical frozen world bytes, with random ticks, mob spawning, daylight/weather cycles disabled. Shared laptop, short warmup and stationary headless clients; not busy gameplay or production performance acceptance.' : 'Stationary headless protocol clients on flat loopback fixture; not native GPU clients, busy production gameplay or streaming/simulation cost.' }, null, 2));
  console.log('PAPER BRIDGE LOAD PROOF PASS; all owned processes stopped');
} catch (error) {
  writeFileSync(join(serverDir, 'load-failure.json'), JSON.stringify({ checkedAt: new Date().toISOString(),
    mode, players, samples, repetitions, companionHash, error: error.stack, checks, results, lifecycleResults }, null, 2));
  console.error(error); process.exitCode = 1;
}
