import assert from 'node:assert/strict';
import { EventEmitter } from 'node:events';
import { PassThrough } from 'node:stream';
import test from 'node:test';
import { observeLoadClients, javaLoadVersion } from './java-load-clients.mjs';
import { channel } from './wire-codec.mjs';

function fixture() {
  const child = new EventEmitter(); child.stdin = new PassThrough(); child.stdout = new PassThrough(); child.stderr = new PassThrough();
  const pool = observeLoadClients(child), event = value => child.stdout.write(JSON.stringify(value) + '\n');
  return { child, pool, event, ready: () => event({ type: 'ready', name: '', detail: javaLoadVersion }) };
}
const options = name => ({ host: '127.0.0.1', port: 25579, version: '26.2', auth: 'offline', username: name });

test('accepts exact codec ready and ordered own-actor events', async () => {
  const f = fixture(); f.ready(); const peer = f.pool.createClient(options('WesoLoad00'));
  let joined = 0, ended = 0, payload;
  peer.on('playerJoin', () => joined++); peer.on('end', () => ended++); peer.on('custom_payload', value => { payload = value; });
  f.event({ type: 'joined', name: 'WesoLoad00' });
  f.event({ type: 'payload', name: 'WesoLoad00', channel, data: 'AA==' });
  assert.equal(joined, 1); assert.equal(payload.channel, channel); assert.deepEqual(payload.data, Buffer.from([0]));
  f.event({ type: 'ended', name: 'WesoLoad00' });
  f.child.stdin.once('finish', () => f.child.emit('close', 0));
  await f.pool.close(); assert.equal(ended, 1);
});
test('refuses wrong codec, repeated ready and actor events before readiness', () => {
  for (const change of [f => f.event({ type: 'ready', name: '', detail: '26.3' }),
    f => { f.ready(); f.ready(); }, f => f.event({ type: 'joined', name: 'WesoLoad00' })]) {
    const f = fixture(); change(f); assert(f.pool.fault);
  }
});
test('refuses external destinations, real identities and duplicate actors', () => {
  const f = fixture(); assert.throws(() => f.pool.createClient(options('WesoLoad00'))); f.ready();
  for (const option of [{ ...options('WesoLoad00'), host: 'example.com' }, options('Player'), options('WesoLoad32'), { ...options('WesoLoad00'), version: '26.1.2' }])
    assert.throws(() => f.pool.createClient(option));
  f.pool.createClient(options('WesoLoad00')); assert.throws(() => f.pool.createClient(options('WesoLoad00')));
});
test('refuses unknown, repeated and post-disconnect actor events', () => {
  for (const events of [[{ type: 'joined', name: 'unknown' }],
    [{ type: 'joined' }, { type: 'joined' }], [{ type: 'ended' }, { type: 'payload', channel, data: 'AA==' }]]) {
    const f = fixture(); f.ready(); f.pool.createClient(options('WesoLoad00'));
    for (const event of events) f.event({ name: 'WesoLoad00', ...event }); assert(f.pool.fault);
  }
});
test('accepts fragmented UTF-8 JSON but rejects malformed or oversized IPC', () => {
  const f = fixture(), ready = Buffer.from(JSON.stringify({ type: 'ready', name: '', detail: javaLoadVersion }) + '\n');
  for (const byte of ready) f.child.stdout.write(Buffer.from([byte])); assert(f.pool.ready); assert.equal(f.pool.fault, null);
  for (const bytes of [Buffer.from('{bad}\n'), Buffer.alloc(8193, 65), Buffer.alloc(128 * 1024 + 1, 65)]) {
    const bad = fixture(); bad.child.stdout.write(bytes); assert(bad.pool.fault);
  }
});
test('refuses wrong channels, noncanonical base64 and oversized outgoing payloads', () => {
  for (const value of [{ channel: 'other:channel', data: 'AA==' }, { channel, data: '!bad' }, { channel, data: 'A'.repeat(5501) }]) {
    const f = fixture(); f.ready(); f.pool.createClient(options('WesoLoad00'));
    f.event({ type: 'payload', name: 'WesoLoad00', ...value }); assert(f.pool.fault);
  }
  const f = fixture(); f.ready(); const peer = f.pool.createClient(options('WesoLoad00')); f.event({ type: 'joined', name: 'WesoLoad00' });
  assert.throws(() => peer.write('custom_payload', { channel, data: Buffer.alloc(257) }));
  assert.throws(() => peer.write('chat', { channel, data: Buffer.alloc(1) }));
});
test('does not hide library parser faults or premature successful-looking exits', async () => {
  const f = fixture(); f.ready(); const peer = f.pool.createClient(options('WesoLoad00'));
  f.event({ type: 'fault', name: 'WesoLoad00', detail: 'packet parser failed' }); assert(f.pool.fault);
  assert.doesNotThrow(() => peer.end()); f.child.emit('close', 1); await assert.rejects(f.pool.close());
  const early = fixture(); early.ready(); early.child.emit('close', 0); assert(early.pool.fault);
});
test('requires all owned actors ended and rejects truncated helper output', async () => {
  const f = fixture(); f.ready(); f.pool.createClient(options('WesoLoad00'));
  f.child.stdin.once('finish', () => f.child.emit('close', 0)); await assert.rejects(f.pool.close(), /live actor/);
  const cut = fixture(); cut.ready(); cut.child.stdout.write('{');
  cut.child.stdin.once('finish', () => cut.child.emit('close', 0)); await assert.rejects(cut.pool.close());
});
