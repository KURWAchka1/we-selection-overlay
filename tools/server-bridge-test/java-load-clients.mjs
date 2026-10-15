import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { createHash } from 'node:crypto';
import { EventEmitter } from 'node:events';
import { createWriteStream, readFileSync, readdirSync } from 'node:fs';
import { constants, setPriority } from 'node:os';
import { join, resolve } from 'node:path';
import { StringDecoder } from 'node:string_decoder';
import { setTimeout as delay } from 'node:timers/promises';
import { channel } from './wire-codec.mjs';

export const javaLoadVersion = '26.2-20261012.101744-21';
export const javaLoadSha256 = '5757ef29742d75b4af90fcdaf3d997e4b6dcf23928e601b4869487286854779a';

/** Narrow adapter to the existing load actor interface, not a Minecraft protocol implementation. */
export function observeLoadClients(child, log) {
  const peers = new Map(), decoder = new StringDecoder('utf8');
  let pending = '', bytes = 0, closed = false, exitCode, closing = false;
  const pool = { ready: false, fault: null };
  const fail = error => { pool.fault ??= error; };
  const drained = log ? new Promise((resolve, reject) => { log.once('finish', resolve); log.once('error', reject); }) : Promise.resolve();
  drained.catch(fail);
  const send = command => {
    assert(!pool.fault && !closed && child.stdin.writable, 'Load helper unavailable');
    assert(child.stdin.writableLength < 1024 * 1024, 'Load helper input backlog');
    const line = JSON.stringify(command); assert(Buffer.byteLength(line) <= 4096);
    child.stdin.write(line + '\n');
  };
  const record = chunk => {
    bytes += chunk.length; assert(bytes <= 32 * 1024 * 1024, 'Load helper log limit');
    assert(!log || log.writableLength <= 8 * 1024 * 1024, 'Load helper disk backlog');
    log?.write(chunk);
  };
  child.stdout.on('data', chunk => {
    try {
      record(chunk); pending += decoder.write(chunk);
      assert(Buffer.byteLength(pending) <= 128 * 1024, 'Load helper output backlog');
      let end;
      while ((end = pending.indexOf('\n')) !== -1) {
        const line = pending.slice(0, end); pending = pending.slice(end + 1); assert(line.length <= 8192);
        const event = JSON.parse(line);
        if (event.type === 'ready') {
          assert(!pool.ready); assert.equal(event.name, ''); assert.equal(event.detail, javaLoadVersion);
          pool.ready = true; continue;
        }
        assert(pool.ready, 'Actor event before codec verification');
        const peer = peers.get(event.name); assert(peer, 'Unknown helper actor');
        if (event.type === 'joined') { assert(!peer.joined && !peer.ended); peer.joined = true; peer.emit('playerJoin'); }
        else if (event.type === 'ended') { assert(!peer.ended); peer.ended = true; peer.emit('end'); }
        else if (event.type === 'teleport') {
          assert(peer.joined && !peer.ended, 'Teleport before play or after disconnect');
          peer.teleports++; peer.emit('positionReady');
        }
        else if (event.type === 'fault') fail(new Error(`Load helper ${event.name}: ${event.detail}`));
        else {
          assert.equal(event.type, 'payload'); assert(!peer.ended);
          assert([channel, 'worldedit:cui'].includes(event.channel));
          assert.equal(typeof event.data, 'string'); assert(event.data.length <= 5500);
          const data = Buffer.from(event.data, 'base64'); assert(data.length <= 4096); assert.equal(data.toString('base64'), event.data);
          peer.emit('custom_payload', { channel: event.channel, data });
        }
      }
      assert(pending.length <= 8192, 'Unterminated helper line');
    } catch (error) { fail(error); }
  });
  child.stderr.on('data', chunk => { try { record(chunk); } catch (error) { fail(error); } });
  child.on('error', fail); child.stdin.on('error', fail);
  child.once('close', code => {
    pending += decoder.end();
    closed = true; exitCode = code;
    if (!closing || code !== 0 || pending.length || !pool.ready) fail(new Error(`Load helper premature/invalid exit: ${code}`));
    log?.end();
  });
  pool.createClient = options => {
    assert(pool.ready && !pool.fault && !closing);
    assert.equal(options.host, '127.0.0.1'); assert.equal(options.port, 25579);
    assert.equal(options.version, '26.2'); assert.equal(options.auth, 'offline');
    assert.match(options.username, /^WesoLoad(?:[0-2][0-9]|3[01])$/);
    assert(!peers.has(options.username) && peers.size < 32);
    const peer = new EventEmitter(); peer.joined = peer.ended = false; peer.teleports = 0;
    peer.write = (type, packet) => {
      assert(peer.joined && !peer.ended);
      if (type === 'position') {
        assert(peer.teleports > 0, 'Movement before initial server position');
        const { x, y, z, flags } = packet, index = Number(options.username.slice(8));
        assert([x, y, z].every(Number.isFinite) && x >= 10 && x <= 22 && z >= 10 && z <= 22 && y === 80 + index % 2);
        assert.deepEqual(flags, { onGround: false, hasHorizontalCollision: false });
        send({ op: 'move', name: options.username, x, y, z }); return;
      }
      assert.equal(type, 'custom_payload');
      assert([channel, 'worldedit:cui', 'minecraft:register'].includes(packet.channel));
      assert(Buffer.isBuffer(packet.data) && packet.data.length <= 256);
      send({ op: 'send', name: options.username, channel: packet.channel, data: packet.data.toString('base64') });
    };
    peer.end = () => {
      if (!peer.ended && !closed && child.stdin.writable) {
        if (pool.fault) child.stdin.end();
        else send({ op: 'end', name: options.username });
      }
    };
    peers.set(options.username, peer); send({ op: 'connect', name: options.username }); return peer;
  };
  pool.close = async () => {
    closing = true;
    if (!closed && child.stdin.writable) child.stdin.end();
    const deadline = Date.now() + 10000;
    while (!closed && Date.now() < deadline) await delay(20);
    if (!closed) {
      child.kill(); const end = Date.now() + 5000;
      while (!closed && Date.now() < end) await delay(20);
      throw new Error('Owned load helper required forced termination');
    }
    await drained; assert.equal(exitCode, 0); assert(!pool.fault, String(pool.fault));
    assert([...peers.values()].every(peer => peer.ended), 'Load helper lost a live actor');
  };
  return pool;
}

export function startJavaLoadClients(javaHome, logPath) {
  const root = resolve(import.meta.dirname, '../..');
  const directory = join(root, 'tools/bridge-load-clients/build/install/weso-bridge-load-clients/lib');
  const artifacts = readdirSync(directory).filter(name => name.endsWith('.jar')).sort().map(name => ({ name,
    sha256: createHash('sha256').update(readFileSync(join(directory, name))).digest('hex') }));
  assert(artifacts.length > 1 && artifacts.length < 128);
  assert.equal(artifacts.find(a => a.name === `protocol-${javaLoadVersion}.jar`)?.sha256, javaLoadSha256, 'Wrong pinned protocol artifact');
  const child = spawn(join(javaHome, 'bin/java.exe'), ['-Xmx256m', '-XX:ActiveProcessorCount=2', '-Dio.netty.eventLoopThreads=2',
    '-Dorg.slf4j.simpleLogger.logFile=System.err', '-cp', join(directory, '*'), 'org.marshal.wesoload.LoadClients', '--owned-loopback-fixture'],
    { cwd: root, windowsHide: true, stdio: ['pipe', 'pipe', 'pipe'] });
  const pool = observeLoadClients(child, createWriteStream(logPath));
  try { if (child.pid) setPriority(child.pid, constants.priority.PRIORITY_BELOW_NORMAL); }
  catch (error) { pool.fault = error; child.stdin.end(); }
  pool.evidence = { implementation: 'MCProtocolLib', version: javaLoadVersion, protocol: 776, artifacts };
  return pool;
}
