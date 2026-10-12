import assert from 'node:assert/strict';

export const channel = 'worldedit_selection_overlay:bridge';
export function frame(opcode, body) {
  return Buffer.concat([Buffer.from([0x57, 0x45, 0x53, 0x4f, 1, opcode]), body]);
}
export function hello(nonce, major = 1, minor = 0, capabilities = 1) {
  const body = Buffer.alloc(24);
  assert.equal(nonce.length, 16);
  nonce.copy(body); body.writeUInt16BE(major, 16); body.writeUInt16BE(minor, 18); body.writeUInt32BE(capabilities, 20);
  return frame(1, body);
}
export function request(epoch, id) {
  const body = Buffer.alloc(24);
  assert.equal(epoch.length, 16);
  epoch.copy(body); body.writeBigInt64BE(BigInt(id), 16);
  return frame(3, body);
}
export function decode(bytes) {
  assert(bytes.length >= 6 && bytes.length <= 256);
  assert.equal(bytes.subarray(0, 5).toString('hex'), '5745534f01');
  if (bytes[5] === 2) {
    assert.equal(bytes.length, 49);
    return { opcode: 2, nonce: bytes.subarray(6, 22), epoch: bytes.subarray(22, 38),
      status: bytes[38], major: bytes.readUInt16BE(39), minor: bytes.readUInt16BE(41),
      capabilities: bytes.readUInt32BE(43), frameBytes: bytes.readUInt16BE(47) };
  }
  if (bytes[5] === 5) {
    assert.equal(bytes.length, 38);
    assert(!bytes.subarray(22).equals(Buffer.alloc(16)), 'Session close has no epoch');
    return { opcode: 5, nonce: bytes.subarray(6, 22), epoch: bytes.subarray(22, 38) };
  }
  assert.equal(bytes[5], 4);
  assert(bytes.length >= 31);
  const result = { opcode: 4, epoch: bytes.subarray(6, 22), id: bytes.readBigInt64BE(22), status: bytes[30] };
  assert.equal(bytes.length, result.status === 0 ? 64 : 31);
  if (result.status === 0) {
    result.bounds = Array.from({ length: 6 }, (_, i) => bytes.readInt32BE(31 + i * 4));
    result.minY = bytes.readInt32BE(55); result.maxY = bytes.readInt32BE(59); result.permissions = bytes[63];
  }
  return result;
}
