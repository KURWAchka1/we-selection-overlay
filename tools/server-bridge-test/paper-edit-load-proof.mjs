import assert from 'node:assert/strict';
import { spawn, spawnSync } from 'node:child_process';
import { createHash, randomBytes } from 'node:crypto';
import { copyFileSync, cpSync, existsSync, mkdirSync, readFileSync, unlinkSync, writeFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { createServer } from 'node:net';
import { constants, getPriority, setPriority } from 'node:os';
import { join, relative, resolve } from 'node:path';
import { performance } from 'node:perf_hooks';
import { setTimeout as delay } from 'node:timers/promises';
import { channel, hello, request, editRead, editCommit, decode } from './wire-codec.mjs';
import { captureProcess } from './native-process.mjs';
import { observeOwnedServer, observedOwnedDepartures } from './owned-server.mjs';
import { verifyEditLoadWindow } from './edit-load-window.mjs';
import { gestureTuple } from './gesture-tuples.mjs';
import { startJavaLoadClients } from './java-load-clients.mjs';
import { FrozenFixtureWorld } from './frozen-fixture-world.mjs';
import { editDiagnosticPolicy, editRunPolicy, paperProfilerConfig, verifyProfilerLog } from './edit-diagnostic-policy.mjs';
import { movement } from './gameplay-workload.mjs';
import { movingEditKinds, movingEditTarget, verifyMovingEditWindow } from './moving-edit-window.mjs';

const [javaHome, directory, playerArgument, sampleArgument, repeatArgument, mode, version = '1.21.11', ...options] = process.argv.slice(2);
const study = editDiagnosticPolicy(options), {matchedSlices,sparkControl,movingGameplay,budgetControl,drainControl,revisionControl,worldControl,guardControl,hotPathControl,priorityControl,encodingControl,batchControl,kindControl,peerControl,authorityControl} = study;
const minimalImplementation = revisionControl || worldControl || guardControl || hotPathControl || priorityControl || encodingControl || batchControl || kindControl || peerControl || authorityControl;
const frozenStudy = matchedSlices || movingGameplay, profilerStudy = sparkControl || movingGameplay;
if (sparkControl) assert.equal(version,'26.2','Profiler-control configuration pins currently cover only exact 26.2');
if (movingGameplay) assert.equal(version,'26.2','Moving edit transport is pinned to the exact 26.2 host');
const tuple = gestureTuple(version);
const root = resolve(import.meta.dirname, '../..'), serverDir = resolve(directory), repetitions = Number(repeatArgument);
assert.equal(relative(root, serverDir), frozenStudy ? join('build', study.rootName, version)
  : join('build', 'bridge-edit-load-proof', ...(version === '1.21.11' ? [] : [version])));
assert.equal(Number(playerArgument), 32); assert.equal(Number(sampleArgument), 400);
assert(Number.isInteger(repetitions) && repetitions >= 1 && repetitions <= 4);
if (matchedSlices) assert.equal(repetitions, 4);
if (movingGameplay) assert.equal(repetitions,budgetControl || drainControl || priorityControl ? 4 : 2,'Moving edit proof has a fixed repetition count');
const properties = readFileSync(join(serverDir, 'server.properties'), 'utf8');
for (const line of ['server-ip=127.0.0.1', 'server-port=25579', 'online-mode=false', 'max-players=32']) assert(properties.split(/\r?\n/).includes(line));
assert.match(readFileSync(join(serverDir, 'eula.txt'), 'utf8'), /^eula=true\s*$/);
const require = createRequire(join(root, 'build/bridge-wire-deps/package.json'));
const minecraft = version === '26.2' ? null : require('minecraft-protocol');
if (minecraft) assert.equal(require('minecraft-protocol/package.json').version, '1.68.0');
const hash = file => createHash('sha256').update(readFileSync(file)).digest('hex');
const hashes = { paper: hash(join(serverDir, 'server.jar')), worldedit: hash(join(serverDir, 'plugins/worldedit.jar')),
  companion: hash(join(serverDir, 'companion.jar')), fixture: hash(join(serverDir, 'plugins/weso-paper-test-fixture.jar')) };
if (budgetControl) {
  hashes.previousCompanion=hash(join(serverDir,'previous-companion.jar'));
  assert.equal(hashes.previousCompanion,batchControl || kindControl || peerControl || authorityControl ? '2fc45532e48eb1208dab02cf1134835888f58525c7efb94db75f624ea877a03a' : guardControl || encodingControl ? 'aab25479af9b47d330e0fbfd7de495303ae23aec06617c103b5ee2404505d4d6' : worldControl ? '42528d3b606e6e2cba9142a482d5ba6e95a589ec16fc3b693a57dfe1af9186cd' : revisionControl || hotPathControl ? '78ff34e46ef58fa61012612d799ac943815adfad33e0f1bf7c44544ea4107ed1' : '7e40f70b3fab486358a929da73db4132d31bcd3d10e5bc90b99392dce3c4d956');
  assert.notEqual(hashes.previousCompanion,hashes.companion,'Budget comparison needs a real candidate');
}
if(hotPathControl || priorityControl) assert.equal(hashes.companion,'aab25479af9b47d330e0fbfd7de495303ae23aec06617c103b5ee2404505d4d6');
if(batchControl) assert.equal(hashes.companion,study.batchVisits===7 ? '2f643ed9950c9b69a3ed524a5c6a67c69ca2ddfa63170938cce3703581dbd686' : 'f6b0e0829cce2f5d0bafba5246d1f3554edd8f3bd9aa2a87b1e3d1f42aa120e2');
if(kindControl) assert.equal(hashes.companion,'9cbf75252fa6a88cfb5c32d68ccaec8091ae2d8307df558089efabbb0404735b');
if(peerControl) assert.equal(hashes.companion,'6815f2b33d3da765b18385f3fa2bbcb26f4bf4334eafafdd2801196bd458fcdd');
if(authorityControl) {
  assert.equal(hashes.companion,'5518acd05bb06ea9072482b9263c5a3f58368617a0d44e169a2a6c9bc9af860d');
  assert.equal(hashes.fixture,'8e6e6f344f55ef890e0dfbd9c49f24b17a56d358e684d75f1502c148d6237995');
}
const agent = join(root, 'tools/bridge-slice-agent/build/libs/bridge-slice-agent.jar');
const drainAgent = join(root,'tools/bridge-slice-agent/build/libs/bridge-drain-agent.jar');
if (drainControl) {
  assert.equal(hashes.companion,'78ff34e46ef58fa61012612d799ac943815adfad33e0f1bf7c44544ea4107ed1','Observer control must keep the tested companion fixed');
}
if (drainControl || minimalImplementation) hashes.drainAgent=hash(drainAgent);
const recordingSettings = frozenStudy ? join(serverDir, 'edit-diagnostics.jfc') : 'default';
if (frozenStudy) {
  if (matchedSlices) {
    hashes.agent = hash(agent);
    hashes.bootstrap = hash(join(root, 'tools/bridge-slice-agent/build/libs/bridge-slice-bootstrap.jar'));
  }
  assert(!existsSync(join(serverDir, 'edit-load-proof.json')), 'Archive a previous diagnostic cycle before rerunning');
  for (const file of ['edit-load-failure.json','edit-0.log','edit-1.log'])
    assert(!existsSync(join(serverDir,file)), 'Archive previous diagnostic attempts before rerunning');
  const configured = spawnSync(join(javaHome,'bin/jfr.exe'), ['configure','--input','default','--output',recordingSettings,
    'jdk.ExecuteVMOperation#enabled=true','jdk.ExecuteVMOperation#threshold=0ms',
    'jdk.SafepointBegin#enabled=true','jdk.SafepointBegin#threshold=0ms'], {windowsHide:true,encoding:'utf8',timeout:30000});
  assert.equal(configured.status,0,configured.stderr || configured.error?.message);
  hashes.jfrSettings = hash(recordingSettings);
}
const frozen = frozenStudy ? new FrozenFixtureWorld(serverDir) : null;
let frozenDigest;
assert.equal(hashes.paper, tuple.paperSha256);
assert.equal(createHash(tuple.worldeditAlgorithm).update(readFileSync(join(serverDir, 'plugins/worldedit.jar'))).digest('hex'), tuple.worldeditHash);
const revision = spawnSync('git', ['rev-parse', 'HEAD'], { cwd: root, windowsHide: true, encoding: 'utf8' });
assert.equal(revision.status, 0); const baseline = revision.stdout.trim(); assert.match(baseline, /^[0-9a-f]{40}$/);
const proof = join(serverDir, 'edit-load-proof.json'), failure = join(serverDir, 'edit-load-failure.json');
for (const file of [proof, failure]) if (existsSync(file)) unlinkSync(file);
let server, transport, cancelled = false, stopping = true, peers = [], workers = [], fault, movementTimer;
const runs = [];
process.on('SIGINT', () => { cancelled = true; }); process.on('SIGTERM', () => { cancelled = true; });
async function waitFor(predicate, label, timeout = 15000) {
  const end = Date.now() + timeout;
  while (!predicate()) {
    assert(!server?.lines.some(s=>/Command exception: \/wesofixture|Gameplay workload edit failed|Unpaired fixture tick|Cannot write fixture|generated an exception whilst handling plugin message/.test(s)),'Fixture failed');
    assert(!fault && !transport?.fault && !cancelled && !server?.exited && Date.now() < end, `${label}: ${fault ?? transport?.fault ?? 'timeout/stop'}`); await delay(10);
  }
}
async function metrics() {
  const start = server.lines.length; server.child.stdin.write('wesobridge\n');
  await waitFor(() => server.lines.slice(start).some(s => /Queue pending=/.test(s)), 'metrics');
  const lines = server.lines.slice(start), find = text => lines.find(s => s.includes(text));
  const n = (text, key) => Number(find(text).match(new RegExp(`${key}=(\\d+)`))[1]);
  return { connections: n('connections=', 'connections'), admitted: n('connections=', 'admitted'), throttled: n('connections=', 'throttled'),
    reads: n('Selection metadata reads=', 'reads'), replies: n('Selection metadata reads=', 'replies'),
    captures: n('Cuboid captures=', 'captures'), commits: n('Cuboid captures=', 'commits'), applied: n('Cuboid captures=', 'applied'),
    pending: n('Queue pending=', 'pending'), peak: n('Queue pending=', 'peak'), tasks: n('Queue pending=', 'tasks'),
    ...(movingGameplay ? {bytes:n('Selection metadata reads=','reply bytes'),expired:n('Queue pending=','expired')} : {}) };
}
async function action(action, name) {
  const start = server.lines.length; server.child.stdin.write(`wesofixture ${action} ${name}\n`);
  await waitFor(() => server.lines.slice(start).some(s => s.includes(`WESO_FIXTURE_OK ${action} ${name}`)), action);
}
async function connect(index) {
  const name = `WesoLoad${String(index).padStart(2, '0')}`;
  const client = transport.createClient({ host: '127.0.0.1', port: 25579, username: name, auth: 'offline', version, profilesFolder: false, disableChatSigning: true, clientSettings: { viewDistance: 2 } });
  const p = { name, client, index, role: index < 8 ? 'editor' : index < 16 ? 'reader' : index < 28 ? 'flood' : 'silent', frames: [], cui: [], play: false, ended: false,
    id: 0, sent: 0, applied: 0, reads: 0, latencies: [], movementPackets:0, editKinds:Object.fromEntries(movingEditKinds.map(k=>[k,0])),
    bounds: [100 + index * 16, 64, 20, 105 + index * 16, 70, 27] };
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
    applied: p.applied, reads: p.reads, latencies: p.latencies, bounds: p.bounds,
    ...(movingGameplay ? {editKinds:p.editKinds} : {}) }));
}
async function measure(label, active) {
  if (movingGameplay) await delay(5000);
  const before = await metrics(), start = server.lines.length, started = performance.now();
  const movementBefore=peers.map(p=>p.movementPackets),teleportsBefore=peers.map(p=>p.client.teleports),framesBefore=peers.map(p=>p.frames.length);
  for (const p of peers) { p.sent = p.applied = p.reads = 0; p.latencies = []; p.editKinds=Object.fromEntries(movingEditKinds.map(k=>[k,0])); }
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
        const planned=movingGameplay ? movingEditTarget(p.index,round) : null;
        const target = planned?.bounds ?? [x, 64, 20, x + 5, 70, 27], frame = editCommit(p.epoch, ++p.id, p.revision, target);
        const response = await p.exchange(frame), reply = response.value;
        assert.equal(reply.commitStatus, 0); assert.deepEqual(reply.bounds, target);
        p.revision = reply.revision; p.bounds = target; p.applied++; p.latencies.push(performance.now() - at);
        if (planned) p.editKinds[planned.kind]++;
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
  const window = { active, before, after, actors: actorSummary(), durationMillis: performance.now() - started, ticks,
    ...(movingGameplay ? {moving:{movementPackets:peers.map((p,i)=>p.movementPackets-movementBefore[i]),
      teleports:peers.map((p,i)=>p.client.teleports-teleportsBefore[i]),bridgeFrames:peers.map((p,i)=>p.frames.length-framesBefore[i])}} : {}) };
  const evidence=join(serverDir,`${label}-window.json`);
  if (movingGameplay) writeFileSync(evidence,JSON.stringify({validated:false,window},null,2));
  (movingGameplay ? verifyMovingEditWindow : verifyEditLoadWindow)(window);
  if (movingGameplay) writeFileSync(evidence,JSON.stringify({validated:true,window},null,2));
  return window;
}
async function run(round) {
  peers = []; fault = null; stopping = true; transport = null;
  const probe = createServer(); await new Promise((resolve, reject) => { probe.once('error', reject); probe.listen(25579, '127.0.0.1', resolve); }); await new Promise(resolve => probe.close(resolve));
  const policy=editRunPolicy(study,round),{instrumented,sparkEnabled,variant,diagnosticProfile,processPriority}=policy;
  const companion=join(serverDir,budgetControl && variant==='previous' ? 'previous-companion.jar' : 'companion.jar');
  copyFileSync(companion, join(serverDir, 'plugins/worldedit_selection_overlay-bridge-paper-0.1.0-dev.jar'));
  const config = join(serverDir, 'plugins/WorldEditSelectionOverlayBridge'); mkdirSync(config, { recursive: true });
  writeFileSync(join(config, 'config.yml'), 'limits:\n  max-connections: 1024\n  global-requests-per-second: 100\n  global-burst: 100\n  player-requests-per-second: 4\n  player-burst: 8\n');
  let profilerConfigSha256;
  if (profilerStudy) {
    const config = join(serverDir,'config'); mkdirSync(config,{recursive:true});
    const input = join(serverDir,`edit-${round}-paper-global.input.json`);
    writeFileSync(input,JSON.stringify(paperProfilerConfig(sparkEnabled),null,2));
    copyFileSync(input,join(config,'paper-global.yml'));
    profilerConfigSha256 = hash(input);
  }
  const child = spawn(join(javaHome, 'bin/java.exe'), ['-Xms256m', '-Xmx1024m', '-XX:ActiveProcessorCount=2', '-Dweso.bridge.fixture=true', '-Dweso.bridge.editFixture=true', '-Dweso.bridge.measureTicks=400',
    ...(instrumented ? ['-Dweso.bridge.sliceDiagnostics=true',
      ...(diagnosticProfile==='drain' ? ['-Dweso.bridge.drainDiagnostics=true'] : []),
      `-javaagent:${diagnosticProfile==='drain' ? drainAgent : agent}`] : []),
    `-XX:StartFlightRecording=filename=edit-${round}.jfr,settings=${recordingSettings},dumponexit=true,maxsize=32m`, '-jar', 'server.jar', '--nogui'], { cwd: serverDir, windowsHide: true, stdio: ['pipe','pipe','pipe'] });
  server = captureProcess(child, join(serverDir, `edit-${round}.log`)); server.lifecycle = observeOwnedServer(child);
  let actualProcessPriority;
  const priorityReadings=[];
  const verifyPriority=()=>{
    const value=getPriority(child.pid);
    assert.equal(value,processPriority==='normal' ? constants.priority.PRIORITY_NORMAL : constants.priority.PRIORITY_BELOW_NORMAL);
    priorityReadings.push(value); return value;
  };
  try {
    if (child.pid) {
      setPriority(child.pid,processPriority==='normal' ? constants.priority.PRIORITY_NORMAL : constants.priority.PRIORITY_BELOW_NORMAL);
      if(priorityControl) {
        actualProcessPriority=verifyPriority();
      }
    }
    await waitFor(() => server.lines.some(s => /Done \(/.test(s)), 'Paper startup', 180000);
    assert(!server.lines.some(s => /Error occurred while enabling/.test(s)));
    assert(!server.lines.some(s => s.includes('[WorldEditSelectionOverlayBridge]') && /Disabling|ERROR/.test(s)), 'Bridge failed closed at startup');
    if (profilerStudy) {
      await waitFor(()=>server.lines.some(s=>s.includes(sparkEnabled ? 'built-in Java engine will be used instead.'
        : 'spark profiler will not be enabled because it is currently disabled')),'explicit profiler startup policy');
      verifyProfilerLog(server.lines.join('\n'),sparkEnabled);
    }
    if (round === 0) { await action('control', 'baseline'); return; }
    if (version === '26.2') {
      transport = startJavaLoadClients(javaHome, join(serverDir, `edit-${round}-clients.log`));
      await waitFor(() => transport.ready, 'exact 26.2 load codec', 15000);
      if (movingGameplay) {
        const archive=join(serverDir,'load-client-artifacts');
        if (!existsSync(archive)) cpSync(join(root,'tools/bridge-load-clients/build/install/weso-bridge-load-clients/lib'),archive,{recursive:true,force:false,errorOnExist:true});
        for (const artifact of transport.evidence.artifacts) assert.equal(hash(join(archive,artifact.name)),artifact.sha256);
      }
    } else transport = { createClient: options => minecraft.createClient(options), evidence: {
      implementation: 'minecraft-protocol', version: '1.68.0', protocol: require('minecraft-data')(version).version.version } };
    await Promise.all(Array.from({length:32}, (_,i) => connect(i)));
    await action('control', 'baseline');
    if (movingGameplay) {
      const positions=peers.map(p=>p.client.teleports);
      await action('control','gameplay');
      await waitFor(()=>peers.every((p,i)=>p.client.teleports>positions[i]),'all gameplay position acknowledgments');
      let step=0;
      movementTimer=setInterval(()=>{
        for (const p of peers) {
          try { p.client.write('position',movement(p.index,step)); p.movementPackets++; }
          catch (error) { fault=error; }
        }
        step++;
      },200);
      await delay(15000);
    } else await delay(5000);
    const editors = peers.filter(p => p.role === 'editor');
    if (movingGameplay) for (const p of peers) await action('edit-audit-start',p.name);
    for (const p of editors) { if (!movingGameplay) await action('edit-audit-start', p.name); await capture(p); }
    await action('edit-command', editors[0].name);
    const stale = (await editors[0].exchange(editCommit(editors[0].epoch, ++editors[0].id, editors[0].revision, editors[0].bounds))).value;
    assert.equal(stale.commitStatus, 1); editors[0].bounds = editors[0].bounds.map((v,i) => i === 0 || i === 3 ? v+1 : v); await capture(editors[0]);
    const foreign = (await editors[1].exchange(editCommit(editors[1].epoch, ++editors[1].id, editors[0].revision, editors[0].bounds))).value;
    assert.equal(foreign.commitStatus, 1); await capture(editors[1]);
    if(priorityControl) verifyPriority();
    const idle = await measure(`edit-${round}-idle`, false), active = await measure(`edit-${round}-active`, true);
    if(priorityControl) verifyPriority();
    const duplicate = editors[3], beforeDuplicate = await metrics(), cui = duplicate.cui.length;
    assert.deepEqual((await duplicate.exchange(duplicate.lastFrame)).bytes, duplicate.lastAck);
    assert.equal((await metrics()).commits, beforeDuplicate.commits); assert.equal(duplicate.cui.length, cui);
    const victim = editors[2]; await action('editrevoke', victim.name);
    const denied = (await victim.exchange(editCommit(victim.epoch, ++victim.id, victim.revision, victim.bounds))).value;
    assert.equal(denied.commitStatus, 2); assert.equal(denied.status, 3); assert.equal(denied.bounds, undefined);
    const canonical=[];
    for (const p of (movingGameplay ? peers : editors)) {
      if (movingGameplay) {
        const start=server.lines.length; await action('edit-bounds',p.name);
        const line=server.lines.slice(start).find(s=>s.includes(`WESO_FIXTURE_BOUNDS ${p.name} `)); assert(line,'No independent canonical bounds');
        const bounds=line.slice(line.indexOf(`${p.name} `)+p.name.length+1).split(',').map(Number);
        assert.deepEqual(bounds,p.bounds,'Server canonical state differs from acknowledgment'); canonical.push({name:p.name,bounds});
      }
      await action('edit-audit-end', p.name);
    }
    const recovered = await measure(`edit-${round}-recovered`, false);
    if(priorityControl) verifyPriority();
    if (movementTimer) { clearInterval(movementTimer); movementTimer=null; }
    const disconnectStart=server.lines.length;
    for (const p of peers) {
      assert(p.cui.filter(s => s.startsWith('p|0|')).every(s => { const x=Number(s.split('|')[2]); return x >= 100+p.index*16 && x <= 102+p.index*16; }), 'Foreign CUI selection');
      p.closing = true; p.client.end('Verification complete');
    }
    await waitFor(() => peers.every(p => p.ended), 'all client disconnects');
    await waitFor(() => observedOwnedDepartures(server.lines.slice(disconnectStart),peers.map(p=>p.name)),
      'all owning-server player quits',5000);
    const final = await metrics(); assert.equal(final.connections, 0); assert.equal(final.pending, 0); assert.equal(final.tasks, 0);
    const result = { round, ...(matchedSlices ? {instrumented, frozenDigest} : {}),
      ...(budgetControl ? {variant,companionHash:hash(companion)} : {}),
      ...(priorityControl ? {variant,processPriority,actualProcessPriority,priorityReadings} : {}),
      ...(drainControl || minimalImplementation ? {diagnosticProfile,companionHash:hash(companion)} : {}),
      ...(movingGameplay ? {movingGameplay,frozenDigest,canonical,actors:peers.map(p=>({name:p.name,frames:p.frames.length,cui:p.cui.length,
        teleports:p.client.teleports,movementPackets:p.movementPackets}))} : {}),
      ...(profilerStudy ? {sparkEnabled,profilerConfigSha256} : {}),
      transport: transport.evidence, idle, active, recovered, final, staleConflict: true, foreignConflict: true, duplicateAppliedOnce: true, revokedDenied: true };
    writeFileSync(join(serverDir, `edit-${round}-result.json`), JSON.stringify(result, null, 2));
    console.log(`EDIT LOAD PASS: run ${round}, ${active.after.applied-active.before.applied} commits, 32 actors`); return result;
  } finally {
    if (movementTimer) { clearInterval(movementTimer); movementTimer=null; }
    stopping = true; await Promise.allSettled(workers); workers = [];
    try {
      for (const p of peers) if (!p.ended) { p.closing = true; p.client.end('Fixture cleanup'); }
      await transport?.close?.();
    } finally {
      try { await server.lifecycle.stop({graceMillis:20000}); } finally {
        if (server.exited) {
          await server.drained;
          if (profilerStudy && existsSync(join(serverDir,'config/paper-global.yml')))
            copyFileSync(join(serverDir,'config/paper-global.yml'),join(serverDir,`edit-${round}-paper-global.effective.yml`));
        }
      }
    }
  }
}
try {
  if (frozen) {
    if (!frozen.exists()) { await run(0); frozen.capture(); }
    frozenDigest = frozen.digest();
  }
  for (let round=1; round<=repetitions; round++) {
    if (frozen) assert.equal(frozen.restore(), frozenDigest);
    runs.push(await run(round));
  }
  assert.equal(hash(join(serverDir, 'companion.jar')), hashes.companion);
  writeFileSync(proof, JSON.stringify({ checkedAt:new Date().toISOString(), baseline, minecraft:version, hashes, actors:32, repetitions,
    ...(matchedSlices ? {matchedSlices, frozenDigest, order:study.order,
      ...(sparkControl ? {sparkControl} : {}),
      profiler:`Paper ${sparkControl ? 'spark on/off/off/on' : 'default spark'} + JFR default with zero-threshold ExecuteVMOperation/SafepointBegin`} : {}),
    ...(movingGameplay ? {movingGameplay,frozenDigest,order:study.order,profiler:`Paper spark disabled + identical JFR; ${minimalImplementation ? 'identical drain-only agent' : drainControl ? 'nested/drain/drain/nested observers' : budgetControl ? 'identical method agent' : 'no method agent'}`} : {}),
    cleanupBarrier:'server-quit-log',
    ...(budgetControl ? {budgetControl} : {}),...(drainControl || minimalImplementation ? {joinPreparation:{batch:32,gapMillis:0}} : {}),
    ...(drainControl ? {drainControl} : {}),...(revisionControl ? {revisionControl} : {}),...(worldControl ? {worldControl} : {}),...(guardControl ? {guardControl} : {}),...(hotPathControl ? {hotPathControl} : {}),...(priorityControl ? {priorityControl} : {}),...(encodingControl ? {encodingControl} : {}),...(batchControl ? {batchControl,batchVisits:study.batchVisits} : {}),...(kindControl ? {kindControl} : {}),...(peerControl ? {peerControl} : {}),...(authorityControl ? {authorityControl} : {}),runs }, null, 2));
  console.log('PAPER EDIT LOAD PROOF PASS; owned servers stopped');
} catch (error) { writeFileSync(failure, JSON.stringify({ checkedAt:new Date().toISOString(), hashes, error:String(error), runs }, null, 2)); console.error(error); process.exitCode=1; }
