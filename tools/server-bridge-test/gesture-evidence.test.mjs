import { test } from 'node:test';
import assert from 'node:assert/strict';
import { verifyGestureResult, verifyGestureMatrix } from './gesture-evidence.mjs';

function sample(index = 0) {
  const applied = index < 22 || index === 27;
  return [{ index, label: 'face-EAST', status: applied ? 'APPLIED' : 'CANCELLED', bounds: applied ? '10,64,20,16,70,27' : '10,64,20,15,70,27' },
    { captures: 0, commits: 0, applied: 0 },
    { captures: index === 26 ? 2 : 1, commits: applied ? 1 : 0, applied: applied ? 1 : 0, pending: 0, tasks: 0 }];
}
test('accepts authoritative applied and all cancellation counts', () => {
  for (const index of [0, 14, 22, 23, 24, 25, 26, 27]) { const [r, b, o] = sample(index); verifyGestureResult(r, b, o, r.bounds); }
});
test('rejects a client-only success', () => { const [r, b, o] = sample(); assert.throws(() => verifyGestureResult(r, b, o, '10,64,20,15,70,27')); });
test('rejects duplicate apply and mouse-release mutation', () => {
  for (const index of [0, 22]) { const [r, b, o] = sample(index); o.commits++; assert.throws(() => verifyGestureResult(r, b, o, r.bounds)); }
});
test('rejects polling and extra recapture', () => { const [r, b, o] = sample(); o.captures++; assert.throws(() => verifyGestureResult(r, b, o, r.bounds)); });
test('rejects retained dispatch work', () => {
  for (const key of ['pending', 'tasks']) { const [r, b, o] = sample(); o[key] = 1; assert.throws(() => verifyGestureResult(r, b, o, r.bounds)); }
});
test('rejects cancelled bounds and invalid case identity', () => {
  const [r, b, o] = sample(22); r.bounds = '10,64,20,16,70,27'; assert.throws(() => verifyGestureResult(r, b, o, r.bounds));
  r.index = 28; assert.throws(() => verifyGestureResult(r, b, o, r.bounds));
});
function matrix() {
  const faces = ['EAST', 'WEST', 'UP', 'DOWN', 'NORTH', 'SOUTH'];
  const corners = ['WEST_DOWN_NORTH', 'EAST_DOWN_NORTH', 'WEST_UP_NORTH', 'EAST_UP_NORTH', 'WEST_DOWN_SOUTH', 'EAST_DOWN_SOUTH', 'WEST_UP_SOUTH', 'EAST_UP_SOUTH'];
  const labels = [...faces.map(f => `face-${f}`), ...corners.map(c => `corner-g-${c}`), ...corners.map(c => `corner-free-${c}`), 'escape-g', 'escape-direct', 'focus', 'chat', 'stale', 'radial-xyz-move'];
  return labels.map((label, i) => { const [r, baseline, observed] = sample(i); return { ...r, label, baseline, observed, serverBounds: r.bounds }; });
}
test('accepts a complete matrix and rejects missing or reordered cases', () => {
  const cases = matrix(); verifyGestureMatrix(cases); assert.throws(() => verifyGestureMatrix(cases.slice(1)));
  [cases[0], cases[1]] = [cases[1], cases[0]]; assert.throws(() => verifyGestureMatrix(cases));
});
test('rejects duplicated handles and mismatched free corner coverage', () => {
  const cases = matrix(); cases[1].label = cases[0].label; assert.throws(() => verifyGestureMatrix(cases));
  const changed = matrix(); changed[14].label = 'corner-free-invented'; assert.throws(() => verifyGestureMatrix(changed));
  const invented = matrix(); invented[0].label = 'face-invented'; assert.throws(() => verifyGestureMatrix(invented));
  const [r, b, o] = sample(); r.bounds = '10,64,20,9007199254740992,70,27'; assert.throws(() => verifyGestureResult(r, b, o, r.bounds));
});
