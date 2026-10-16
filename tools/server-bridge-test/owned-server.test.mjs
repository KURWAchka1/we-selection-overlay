import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { EventEmitter, once } from 'node:events';
import { PassThrough } from 'node:stream';
import test from 'node:test';
import { constants, getPriority, setPriority } from 'node:os';
import { observeOwnedServer, observedOwnedDepartures } from './owned-server.mjs';

test('client completion cannot replace all 32 owning-server quit observations',()=>{
  const names=Array.from({length:32},(_,i)=>`WesoLoad${String(i).padStart(2,'0')}`);
  const lines=names.map(n=>`[18:55:47 INFO]: ${n} left the game`);
  assert(!observedOwnedDepartures(lines.slice(0,30),names));
  assert(observedOwnedDepartures(lines,names));
  assert(observedOwnedDepartures(lines.map(line=>'> \r  \r'+line.replace(']: ',']: \u001b[93m')+'\u001b[0m'),names));
  assert(!observedOwnedDepartures(lines.map(s=>s.replace('left the game','lost connection: Disconnected')),names));
});

test('duplicate or foreign log messages cannot complete missing server quits',()=>{
  const names=['WesoLoad00','WesoLoad01'];
  assert(!observedOwnedDepartures(['[INFO]: WesoLoad00 left the game','[INFO]: WesoLoad00 left the game'],names));
  assert(!observedOwnedDepartures(['[INFO]: FakeWesoLoad00 left the game','[INFO]: WesoLoad01 left the game'],names));
  assert.throws(()=>observedOwnedDepartures([],[]));
  assert.throws(()=>observedOwnedDepartures([],['WesoLoad00','WesoLoad00']));
  assert.throws(()=>observedOwnedDepartures([],['WesoLoad00\n']));
});

function fake(onStop, onKill = child => child.emit('close', null, 'SIGTERM')) {
  const child = new EventEmitter();
  child.pid = 123;
  child.stdin = new PassThrough();
  child.stdin.on('data', bytes => onStop(child, bytes.toString()));
  child.kill = () => { onKill(child); return true; };
  return child;
}

function ownedNode(t, program) {
  const child = spawn(process.execPath, ['-e', program],
    { windowsHide: true, stdio: ['pipe', 'pipe', 'pipe'] });
  const server = observeOwnedServer(child);
  t.after(async () => {
    try { await server.stop({ graceMillis: 50, killMillis: 5000 }); }
    catch (error) { assert(child.exitCode !== null || child.signalCode !== null, error.message); }
  });
  return { child, server };
}

test('owned Windows process can verify below-normal and normal priorities without foreground control',
  {skip:process.platform!=='win32'},async t=>{
    const {child,server}=ownedNode(t,"process.stdin.once('data',()=>process.exit(0));process.stdout.write('ready');");
    await once(child.stdout,'data');
    for(const priority of [constants.priority.PRIORITY_BELOW_NORMAL,constants.priority.PRIORITY_NORMAL]) {
      setPriority(child.pid,priority);assert.equal(getPriority(child.pid),priority);
    }
    await server.stop({graceMillis:5000,killMillis:5000});
  });

test('normal shutdown waits for close, retains final output and is idempotent', async t => {
  const { child, server } = ownedNode(t,
    "process.stdin.once('data',()=>{process.stdout.write('final log');process.stdin.destroy();});");
  let output = '';
  child.stdout.on('data', bytes => { output += bytes; });
  const first = server.stop({ graceMillis: 5000, killMillis: 5000 });
  assert.strictEqual(server.stop(), first);
  assert.deepEqual(await first, { code: 0, signal: null, forced: false });
  assert.equal(output, 'final log');
});

test('nonzero exit and signal exits never pass', async () => {
  for (const [code, signal] of [[7, null], [null, 'SIGTERM']]) {
    const child = fake(child => queueMicrotask(() => child.emit('close', code, signal)));
    await assert.rejects(observeOwnedServer(child).stop(), /shutdown failed|ended by signal/);
  }
});

test('a successful-looking exit after forced termination still fails', async () => {
  const child = fake(() => {}, child => child.emit('close', 0, null));
  await assert.rejects(observeOwnedServer(child).stop({ graceMillis: 10, killMillis: 10 }), /forced termination/);
});

test('an owned process that never closes fails within the termination deadline', async () => {
  const child = fake(() => {}, () => {});
  await assert.rejects(observeOwnedServer(child).stop({ graceMillis: 10, killMillis: 10 }), /did not close/);
});

test('premature zero exit cannot validate a run', async () => {
  const child = fake(() => {}), server = observeOwnedServer(child);
  child.emit('close', 0, null);
  await assert.rejects(server.stop(), /closed before shutdown/);
});

test('spawn errors and stdin failures are retained, not replaced by a zero exit', async () => {
  for (const target of ['process', 'stdin']) {
    const child = fake(child => queueMicrotask(() => child.emit('close', 0, null)));
    const server = observeOwnedServer(child);
    if (target === 'process') {
      child.emit('error', new Error('spawn failed'));
      queueMicrotask(() => child.emit('close', null, null));
    } else child.stdin.on('data', () => child.stdin.emit('error', new Error('broken pipe')));
    await assert.rejects(server.stop(), /process or stdin failed/);
  }
});

test('real missing executable is rejected and its close is awaited', async () => {
  const child = spawn(`${process.execPath}.weso-does-not-exist`, [], { windowsHide: true });
  const closed = new Promise(resolve => child.once('close', resolve));
  const server = observeOwnedServer(child);
  await assert.rejects(server.stop(), /process or stdin failed/);
  await closed;
});

test('real unresponsive owned node is killed, awaited and cannot pass', async t => {
  const { child, server } = ownedNode(t,
    "process.stdin.resume();process.stdout.write('ready');setInterval(()=>{},1000);");
  let timer;
  try {
    await Promise.race([once(child.stdout, 'data'), new Promise((_, reject) => {
      timer = setTimeout(() => reject(new Error('Owned test process did not become ready')), 5000);
    })]);
  } finally { clearTimeout(timer); }
  await assert.rejects(server.stop({ graceMillis: 25, killMillis: 5000 }), /forced termination/);
  assert(child.exitCode !== null || child.signalCode !== null);
});
