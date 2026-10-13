import assert from 'node:assert/strict';
import test from 'node:test';
import { decode, hello, request, editRead, editCommit } from './wire-codec.mjs';

test('wire helpers match the fixed-width Java protocol golden framing', () => {
  const uuid = Buffer.from('00000000000000010000000000000002', 'hex');
  assert.equal(hello(uuid).toString('hex'), '5745534f0101000000000000000100000000000000020001000000000001');
  assert.equal(request(uuid, 3).toString('hex'), '5745534f0103000000000000000100000000000000020000000000000003');
});

test('minor-two edit frames match Java golden bytes and canonical offsets', () => {
  const epoch = Buffer.from('00000000000000020000000000000003', 'hex'), revision = Buffer.from('00000000000000000000000000000004', 'hex');
  assert.equal(editRead(epoch, 1).length, 30);
  assert.equal(editCommit(epoch, 1, revision, [-2, -64, 0, 5, 319, 7]).toString('hex'),
    '5745534f010800000000000000020000000000000003000000000000000100000000000000000000000000000004fffffffeffffffc000000000000000050000013f00000007');
  const reply = Buffer.alloc(81); Buffer.from('5745534f0109', 'hex').copy(reply); epoch.copy(reply, 6);
  reply.writeBigInt64BE(1n, 22); revision.copy(reply, 31);
  [-2, -64, 0, 5, 319, 7].forEach((n, i) => reply.writeInt32BE(n, 48 + i * 4));
  reply.writeInt32BE(-64, 72); reply.writeInt32BE(319, 76); reply[80] = 7;
  assert.deepEqual(decode(reply).bounds, [-2, -64, 0, 5, 319, 7]);
  assert.equal(decode(reply).permissions, 7); assert.equal(decode(reply).commitStatus, 0);
  for (let size = 0; size < reply.length; size++) assert.throws(() => decode(reply.subarray(0, size)));
  assert.throws(() => decode(Buffer.concat([reply, Buffer.alloc(1)])));
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

test('minor-one opt-in and session close match Java golden bytes without altering legacy hello', () => {
  const nonce = Buffer.from('00000000000000000000000000000001', 'hex');
  const epoch = Buffer.from('00000000000000020000000000000003', 'hex');
  assert.equal(hello(nonce, 1, 1, 3).toString('hex'),
    '5745534f0101000000000000000000000000000000010001000100000003');
  const bytes = Buffer.from('5745534f01050000000000000000000000000000000100000000000000020000000000000003', 'hex');
  assert.deepEqual(decode(bytes), { opcode: 5, nonce, epoch });
  for (let length = 0; length < bytes.length; length++) assert.throws(() => decode(bytes.subarray(0, length)));
  assert.throws(() => decode(Buffer.concat([bytes, Buffer.alloc(1)])));
  const zeroEpoch = Buffer.from(bytes); zeroEpoch.fill(0, 22);
  assert.throws(() => decode(zeroEpoch));
});
