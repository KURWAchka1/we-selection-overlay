import assert from 'node:assert/strict';
import test from 'node:test';
import { orderedPoints, propertyBase, selectionPropertyCases, verifyPropertyRows } from './selection-property-cases.mjs';

const cases = selectionPropertyCases();
function reply(opcode, c) {
  const commit = opcode === 9, ready = !commit || c.status === 0, offset = commit ? 31 : 30;
  const bytes = Buffer.alloc(offset + 17 + (ready ? 33 : 0));
  Buffer.from([0x57, 0x45, 0x53, 0x4f, 1, opcode]).copy(bytes);
  bytes.fill(0x11, 6, 22); bytes.writeBigInt64BE(commit ? 2n : 1n, 22);
  if (commit) bytes[30] = c.status;
  if (ready) bytes.fill(commit ? 0x33 : 0x22, offset, offset + 16);
  bytes[offset + 16] = ready ? 0 : 3;
  if (ready) {
    (commit ? c.bounds : propertyBase).forEach((n, i) => bytes.writeInt32BE(n, offset + 17 + i * 4));
    bytes.writeInt32BE(-64, offset + 41); bytes.writeInt32BE(319, offset + 45); bytes[offset + 49] = c.permissionBits;
  }
  return bytes.toString('hex');
}
const rows = () => cases.map(c => ({ id: c.id, commitStatus: c.status, selector: c.selector,
  points: [...c.expectedPoints], cui: c.cui, replayed: c.replay, permissionBits: c.permissionBits,
  captureHex: reply(7, c), ackHex: reply(9, c), replayHex: c.replay ? reply(9, c) : null,
  cuiFrames: c.cui ? [`p|0|${c.expectedPoints.slice(0, 3).join('|')}|1`, `p|1|${c.expectedPoints.slice(3).join('|')}|1`] : [] }));

test('matrix covers all point orders, selector types, mixed operations and denied grants', () => {
  assert.equal(cases.length, 144); assert.equal(new Set(cases.map(c => c.id)).size, 144);
  for (const selector of ['CuboidRegionSelector', 'ExtendingCuboidRegionSelector']) for (let orientation = 0; orientation < 8; orientation++) {
    const group = cases.filter(c => c.selector === selector && c.orientation === orientation);
    assert.equal(group.length, 9); assert.equal(group.filter(c => c.status === 0).length, 6);
    assert.deepEqual(group.filter(c => c.status === 2).map(c => c.permissionBits), [1, 2, 4]);
  }
  assert.deepEqual(verifyPropertyRows(rows()), { cases: 144, applied: 96, denied: 48, replays: 96 });
});
test('point oracle preserves independent per-axis ordering and handles collapse', () => {
  assert.deepEqual(orderedPoints(propertyBase, 0), propertyBase);
  assert.deepEqual(orderedPoints(propertyBase, 7), [15, 70, 27, 10, 64, 20]);
  assert.deepEqual(orderedPoints(propertyBase, 5), [15, 64, 27, 10, 70, 20]);
  for (let orientation = 0; orientation < 8; orientation++)
    assert.deepEqual(orderedPoints([12, 67, 23, 12, 67, 23], orientation), [12, 67, 23, 12, 67, 23]);
});
test('oracle rejects malformed bounds or orientation instead of guessing', () => {
  for (const orientation of [-1, 8, NaN, 1.5]) assert.throws(() => orderedPoints(propertyBase, orientation));
  for (const bounds of [[], [1, 2, 3, 0, 2, 3], [0, 0, 0, 1, 1, NaN]]) assert.throws(() => orderedPoints(bounds, 0));
});
for (const [name, mutate] of [
  ['missing row', r => r.pop()], ['duplicate row', r => { r[1] = r[0]; }],
  ['wrong point order', r => { r[0].points.reverse(); }], ['partial denied mutation', r => { r[6].points[0]++; }],
  ['wrong selector', r => { r[0].selector = 'CuboidRegionSelectorSubclass'; }],
  ['unexpected CUI', r => { r[4].cui = true; }], ['missed CUI', r => { r[0].cui = false; }],
  ['missed replay', r => { r[0].replayed = false; }], ['wrong grant', r => { r[6].permissionBits = 7; }],
  ['unexpected success', r => { r[6].commitStatus = 0; }],
  ['missing raw capture', r => { delete r[0].captureHex; }], ['corrupted wire status', r => { r[0].ackHex = r[6].ackHex; }],
  ['wrong acknowledgment epoch', r => { r[0].ackHex = r[0].ackHex.slice(0, 12) + 'ff'.repeat(16) + r[0].ackHex.slice(44); }],
  ['fabricated replay flag', r => { r[0].replayHex = null; }], ['incorrect CUI points', r => { r[0].cuiFrames[0] = 'p|0|0|0|0|1'; }],
  ['extra CUI point', r => { r[0].cuiFrames.push(r[0].cuiFrames[0]); }],
]) test(`evidence rejects ${name}`, () => { const r = rows(); mutate(r); assert.throws(() => verifyPropertyRows(r)); });
