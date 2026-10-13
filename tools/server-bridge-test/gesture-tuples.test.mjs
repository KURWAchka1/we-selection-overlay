import assert from 'node:assert/strict';
import test from 'node:test';
import { gestureTuple } from './gesture-tuples.mjs';

for (const [version, family, worldedit] of [['1.21.11', '1.21.11', '7.4.2'], ['26.1.2', '26.1', '7.4.3'], ['26.2', '26.2', '7.4.5']]) {
  test('Exact native tuple: ' + version, () => {
    const value = gestureTuple(version);
    assert.equal(value.family, family); assert.equal(value.worldedit, worldedit);
    assert.match(value.paperSha256, /^[a-f0-9]{64}$/);
    assert.match(value.worldeditHash, value.worldeditAlgorithm === 'sha512' ? /^[a-f0-9]{128}$/ : /^[a-f0-9]{64}$/);
    assert(Object.isFrozen(value));
  });
}
test('Unknown versions do not inherit a tested family', () => {
  for (const value of ['26.1', '26.1.1', '26.3', 'latest', '../26.2', '__proto__', undefined]) assert.throws(() => gestureTuple(value));
});
