import assert from 'node:assert/strict';
import { decode } from './wire-codec.mjs';

export const propertyBase = Object.freeze([10, 64, 20, 15, 70, 27]);
const targets = Object.freeze([
  ['disjoint-positive', [31, 76, 42, 33, 78, 44]],
  ['disjoint-negative', [-13, 51, -5, -10, 53, -2]],
  ['mixed-six-faces', [8, 66, 18, 14, 73, 25]],
  ['single-block', [12, 67, 23, 12, 67, 23]],
  ['noop', propertyBase],
  ['shift', [17, 69, 15, 22, 75, 22]],
].map(([name, bounds]) => Object.freeze({ name, bounds: Object.freeze(bounds) })));

// Independent integer-coordinate oracle, not the production draft/resize algorithm.
export function orderedPoints(bounds, orientation) {
  assert(Array.isArray(bounds) && bounds.length === 6 && bounds.every(Number.isInteger));
  assert(Number.isInteger(orientation) && orientation >= 0 && orientation < 8);
  for (let axis = 0; axis < 3; axis++) assert(bounds[axis] <= bounds[axis + 3]);
  const first = [], second = [];
  for (let axis = 0; axis < 3; axis++) {
    const highFirst = (orientation & (1 << axis)) !== 0;
    first.push(bounds[axis + (highFirst ? 3 : 0)]);
    second.push(bounds[axis + (highFirst ? 0 : 3)]);
  }
  return [...first, ...second];
}

export function selectionPropertyCases() {
  const result = [];
  for (const extending of [false, true]) for (let orientation = 0; orientation < 8; orientation++) {
    const seed = `orientation-${extending ? 'ext-' : ''}${orientation}`;
    const selector = extending ? 'ExtendingCuboidRegionSelector' : 'CuboidRegionSelector';
    // WorldEdit's public Extending copy constructor normalizes the two corners.
    const pointOrientation = extending ? 0 : orientation;
    for (const target of targets) result.push({ id: `${seed}/${target.name}`, seed, selector, orientation, pointOrientation,
      permission: 'allgrants', permissionBits: 7, bounds: target.bounds, status: 0,
      expectedPoints: orderedPoints(target.bounds, pointOrientation), cui: target.name !== 'noop', replay: true });
    for (const [permission, permissionBits] of [['expandonly', 1], ['contractonly', 2], ['shiftonly', 4]]) {
      result.push({ id: `${seed}/${permission}-mixed-denied`, seed, selector, orientation, pointOrientation, permission, permissionBits,
        bounds: targets[2].bounds, status: 2, expectedPoints: orderedPoints(propertyBase, pointOrientation), cui: false, replay: false });
    }
  }
  return result;
}

export function verifyPropertyRows(rows) {
  const cases = selectionPropertyCases();
  assert.equal(rows.length, cases.length, 'Incomplete property matrix');
  for (const [index, expected] of cases.entries()) {
    const row = rows[index];
    assert.equal(row.id, expected.id, 'Missing, duplicate or reordered property');
    assert.equal(row.commitStatus, expected.status, row.id);
    assert.equal(row.selector, expected.selector, row.id);
    assert.deepEqual(row.points, expected.expectedPoints, row.id);
    assert.equal(row.cui, expected.cui, row.id);
    assert.equal(row.replayed, expected.replay, row.id);
    assert.equal(row.permissionBits, expected.permissionBits, row.id);
    const fromHex = hex => {
      assert.equal(typeof hex, 'string'); assert.match(hex, /^[a-f0-9]{12,512}$/); assert.equal(hex.length % 2, 0);
      return decode(Buffer.from(hex, 'hex'));
    };
    const capture = fromHex(row.captureHex), ack = fromHex(row.ackHex);
    assert.equal(capture.opcode, 7); assert.equal(capture.status, 0); assert(capture.id > 0n);
    assert.deepEqual(capture.bounds, propertyBase); assert.equal(capture.permissions, expected.permissionBits);
    assert.equal(ack.opcode, 9); assert.equal(ack.id, capture.id + 1n); assert.deepEqual(ack.epoch, capture.epoch);
    assert.equal(ack.commitStatus, expected.status); assert.equal(ack.status, expected.status === 0 ? 0 : 3);
    if (expected.status === 0) {
      assert.deepEqual(ack.bounds, expected.bounds); assert.equal(ack.permissions, expected.permissionBits);
      assert.notDeepEqual(ack.revision, capture.revision, 'Successful capture was not renewed');
    } else assert.equal(ack.bounds, undefined);
    assert.equal(row.replayHex, expected.replay ? row.ackHex : null);
    assert(Array.isArray(row.cuiFrames) && row.cuiFrames.length <= 16 && row.cuiFrames.every(s => typeof s === 'string' && s.length < 512));
    if (expected.cui) {
      assert.equal(row.cuiFrames.filter(s => s.startsWith('p|')).length, 2);
      for (let point = 0; point < 2; point++)
        assert(row.cuiFrames.some(s => s.startsWith(`p|${point}|${expected.expectedPoints.slice(point * 3, point * 3 + 3).join('|')}|`)));
    } else assert.equal(row.cuiFrames.length, 0);
  }
  return { cases: rows.length, applied: cases.filter(c => c.status === 0).length,
    denied: cases.filter(c => c.status === 2).length, replays: cases.filter(c => c.replay).length };
}
