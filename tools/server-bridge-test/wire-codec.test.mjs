import assert from 'node:assert/strict';
import test from 'node:test';
import { decode, hello, request } from './wire-codec.mjs';

test('wire helpers match the fixed-width Java protocol golden framing', () => {
  const uuid = Buffer.from('00000000000000010000000000000002', 'hex');
  assert.equal(hello(uuid).toString('hex'), '5745534f0101000000000000000100000000000000020001000000000001');
  assert.equal(request(uuid, 3).toString('hex'), '5745534f0103000000000000000100000000000000020000000000000003');
});

test('selection parser preserves signed coordinates, ownership and large request IDs', () => {
  const bytes = Buffer.alloc(64);
  Buffer.from('5745534f0104', 'hex').copy(bytes);
  bytes.writeBigInt64BE(0x7fff_ffff_ffff_ffffn, 22);
  const bounds = [-30, -64, -27, -25, 319, 20];
  bounds.forEach((n, i) => bytes.writeInt32BE(n, 31 + i * 4));
  bytes.writeInt32BE(-64, 55); bytes.writeInt32BE(319, 59); bytes[63] = 7;
  const value = decode(bytes);
  assert.deepEqual(value.bounds, bounds);
  assert.equal(value.id, 0x7fff_ffff_ffff_ffffn);
  assert.equal(value.minY, -64); assert.equal(value.maxY, 319); assert.equal(value.permissions, 7);
});

test('truncated, oversized and unknown reply envelopes fail the harness', () => {
  for (const size of [0, 5, 6, 30, 31, 63, 65, 257]) {
    const bytes = Buffer.alloc(size);
    Buffer.from('5745534f0104', 'hex').copy(bytes);
    assert.throws(() => decode(bytes));
  }
  assert.throws(() => decode(Buffer.from('5745534f01ff', 'hex')));
});

test('non-ready states do not imply a bounds payload', () => {
  const bytes = Buffer.alloc(31);
  Buffer.from('5745534f0104', 'hex').copy(bytes); bytes[30] = 3;
  const value = decode(bytes);
  assert.equal(value.status, 3); assert.equal(value.bounds, undefined);
});
