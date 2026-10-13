import assert from 'node:assert/strict';
import { once } from 'node:events';
import { createConnection, createServer } from 'node:net';
import { setTimeout as delay } from 'node:timers/promises';
import test from 'node:test';
import { FaultRelay, FrameReader } from './fault-relay.mjs';

function packet(payload) {
  const header = []; let n = payload.length;
  do { header.push((n & 127) | (n > 127 ? 128 : 0)); n >>>= 7; } while (n);
  return Buffer.concat([Buffer.from(header), payload]);
}
const prefix = Buffer.from([0x18, 1, 65]);
const bridge = opcode => packet(Buffer.concat([prefix, Buffer.from([0x57, 0x45, 0x53, 0x4f, 1, opcode])]));

test('uncompressed frame reader preserves arbitrary fragmentation and coalescing', () => {
  const packets = [packet(Buffer.from([1])), packet(Buffer.alloc(300, 7)), bridge(9)];
  const input = Buffer.concat(packets), reader = new FrameReader(), actual = [];
  for (const byte of input) actual.push(...reader.push(Buffer.from([byte])).map(f => f.bytes));
  assert.deepEqual(actual, packets); assert.equal(reader.buffer.length, 0);
  assert.deepEqual(new FrameReader().push(input).map(f => f.bytes), packets);
});
test('invalid length encodings and input bounds fail closed', () => {
  for (const bytes of [[0], [128,128,128], [255,255,255]]) assert.throws(() => new FrameReader().push(Buffer.from(bytes)));
  assert.throws(() => new FrameReader().push(Buffer.alloc(8 * 1024 * 1024 + 1)));
});

async function fixture(mode, run) {
  let backend; const received = [];
  const server = createServer(socket => { backend = socket; socket.on('data', bytes => received.push(Buffer.from(bytes))); });
  server.listen(0, '127.0.0.1'); await once(server, 'listening');
  const relay = new FaultRelay({ toClient: prefix, toServer: prefix }, { frontPort: 0, backPort: server.address().port });
  let front;
  try {
    await relay.listen(); front = createConnection({ host: '127.0.0.1', port: relay.server.address().port });
    front.on('error', () => {}); await once(front, 'connect');
    for (let i=0; !backend && i<100; i++) await delay(5);
    assert(backend); relay.arm(mode);
    await run({ front, backend, relay, received }); assert.deepEqual(relay.failures, []);
  } finally { front?.destroy(); backend?.destroy(); await relay.close(); await new Promise(resolve => server.close(resolve)); }
}
test('late acknowledgment is held once and released byte-for-byte', async () => {
  await fixture('late', async ({ front, backend, relay }) => {
    const received = []; front.on('data', bytes => received.push(bytes));
    backend.write(bridge(9)); await delay(40); assert.equal(received.length, 0); assert(relay.held);
    relay.release(); await delay(40); assert.deepEqual(Buffer.concat(received), bridge(9));
    backend.write(bridge(9)); await delay(40);
    assert.deepEqual(Buffer.concat(received), Buffer.concat([bridge(9), bridge(9)]));
    assert.equal(relay.stats.held, 1); assert.equal(relay.stats.replies, 2);
  });
});
test('delayed request has not reached the backend before explicit release', async () => {
  await fixture('revoked', async ({ front, relay, received }) => {
    front.write(bridge(8)); await delay(40); assert.equal(received.length, 0); assert(relay.held);
    relay.release(); await delay(40); assert.deepEqual(Buffer.concat(received), bridge(8));
    assert.equal(relay.stats.commits, 1); assert.throws(() => relay.release());
  });
});
test('latency preserves packet order rather than reordering the underlying stream', async () => {
  await fixture('slow', async ({ front, received, relay }) => {
    const data = Buffer.concat([packet(Buffer.from([3])), bridge(8), packet(Buffer.from([4]))]);
    const start = performance.now(); front.write(data); await delay(150); assert.equal(received.length, 0);
    await delay(350); assert.deepEqual(Buffer.concat(received), data); assert(performance.now() - start >= 400);
    assert.equal(relay.stats.delayedFrames, 3); assert(relay.stats.peakBytes <= 8 * 1024 * 1024);
  });
});
test('relay rejects a second live connection without allocating another backend', async () => {
  await fixture('late', async ({ relay }) => {
    const second = createConnection({ host: '127.0.0.1', port: relay.server.address().port });
    second.on('error', () => {}); await once(second, 'close');
    assert.deepEqual(relay.failures, ['Unexpected second relay connection']); assert.equal(relay.pairs.size, 1);
    relay.failures.length = 0;
    assert.throws(() => relay.arm('slow'));
  });
});
test('output queue cannot bypass the explicit memory bound', async () => {
  await fixture('slow', async ({ relay }) => {
    const direction = [...relay.pairs][0].directions[0];
    assert.throws(() => direction.enqueue({ bytes: Buffer.alloc(8 * 1024 * 1024 + 1) }, 400));
    assert.equal(direction.bytes, 0); assert.equal(direction.queue.length, 0);
  });
});
