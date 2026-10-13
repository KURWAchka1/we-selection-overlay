import assert from 'node:assert/strict';

export const initialBounds = '10,64,20,15,70,27';
export function verifyGestureResult(result, baseline, observed, serverBounds) {
  assert(Number.isInteger(result.index) && result.index >= 0 && result.index < 28);
  assert.match(result.label, /^[a-zA-Z0-9_-]+$/);
  const applied = result.index < 22 || result.index === 27;
  assert.equal(result.status, applied ? 'APPLIED' : 'CANCELLED');
  assert.match(result.bounds, /^-?\d+(,-?\d+){5}$/);
  assert.equal(serverBounds, result.bounds, 'Actual WorldEdit bounds differ');
  if (!applied) assert.equal(result.bounds, initialBounds, 'Cancelled selection changed');
  else assert.notEqual(result.bounds, initialBounds, 'No-op claimed as an applied gesture');
  const values = result.bounds.split(',').map(Number);
  for (const value of values) assert(Number.isSafeInteger(value), 'Invalid coordinate');
  for (const axis of [0, 2]) assert(values[axis] >= -30000000 && values[axis + 3] <= 29999999);
  assert(values[1] >= -64 && values[4] <= 319);
  for (let axis = 0; axis < 3; axis++) assert(values[axis] <= values[axis + 3]);
  assert.equal(observed.commits - baseline.commits, applied ? 1 : 0, 'Hidden or duplicate host apply');
  assert.equal(observed.applied - baseline.applied, applied ? 1 : 0);
  assert.equal(observed.captures - baseline.captures, result.index === 26 ? 2 : 1, 'Missing/redundant capture');
  assert.equal(observed.pending, 0); assert.equal(observed.tasks, 0);
}
export function verifyGestureMatrix(cases) {
  assert.equal(cases.length, 28);
  assert.deepEqual(cases.map(item => item.index), Array.from({ length: 28 }, (_, i) => i));
  const distinct = (start, end, prefix) => {
    const labels = cases.slice(start, end).map(item => item.label);
    assert.equal(new Set(labels).size, end - start);
    for (const label of labels) assert(label.startsWith(prefix));
  };
  distinct(0, 6, 'face-'); distinct(6, 14, 'corner-g-'); distinct(14, 22, 'corner-free-');
  assert.deepEqual(new Set(cases.slice(0, 6).map(item => item.label.slice(5))), new Set(['WEST', 'EAST', 'DOWN', 'UP', 'NORTH', 'SOUTH']));
  const corners = ['WEST_DOWN_NORTH', 'EAST_DOWN_NORTH', 'WEST_UP_NORTH', 'EAST_UP_NORTH', 'WEST_DOWN_SOUTH', 'EAST_DOWN_SOUTH', 'WEST_UP_SOUTH', 'EAST_UP_SOUTH'];
  assert.deepEqual(new Set(cases.slice(6, 14).map(item => item.label.slice(9))), new Set(corners));
  assert.deepEqual(cases.slice(6, 14).map(item => item.label.slice(9)), cases.slice(14, 22).map(item => item.label.slice(12)));
  assert.deepEqual(cases.slice(22).map(item => item.label), ['escape-g', 'escape-direct', 'focus', 'chat', 'stale', 'radial-xyz-move']);
  for (const item of cases) verifyGestureResult(item, item.baseline, item.observed, item.serverBounds);
}
