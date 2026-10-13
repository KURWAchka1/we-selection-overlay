import assert from 'node:assert/strict';
import { setTimeout as delay } from 'node:timers/promises';
import { channel, request, editRead, editCommit } from './wire-codec.mjs';

export async function verifyEdits({ reconnected, second, one, consoleAction, negotiate, silent, record, waitFor }) {
  await consoleAction('editgrant', 'WesoAllowed');
  await consoleAction('allgrants', 'WesoAllowed');
  await consoleAction('seed', 'WesoAllowed');
  reconnected.client.write('custom_payload', { channel: 'minecraft:register', data: Buffer.from('worldedit:cui') });
  reconnected.client.write('custom_payload', { channel: 'worldedit:cui', data: Buffer.from('v|4') });
  await delay(1000);
  await consoleAction('edit-audit-start', 'WesoAllowed');
  let editSession = await negotiate(reconnected, 1, 2, 7);
  assert.equal(editSession.capabilities, 7); assert.equal(editSession.minor, 2);
  let snapshot = (await reconnected.exchange(editRead(editSession.epoch, 1))).value;
  assert.equal(snapshot.status, 0); assert.deepEqual(snapshot.bounds, one.value.bounds);
  const shifted = [11, 65, 20, 16, 71, 27], commit = editCommit(editSession.epoch, 2, snapshot.revision, shifted);
  const cuiStart = reconnected.cui.length;
  const applied = await reconnected.exchange(commit);
  assert.equal(applied.value.commitStatus, 0); assert.deepEqual(applied.value.bounds, shifted);
  await waitFor(() => reconnected.cui.slice(cuiStart).some(s => s.startsWith('p|0|11|65|20|')), 'canonical CUI after shift');
  const afterCui = reconnected.cui.length;
  assert.deepEqual((await reconnected.exchange(commit)).bytes, applied.bytes);
  assert.equal(reconnected.cui.length, afterCui, 'Duplicate mutation emitted another CUI sequence');
  record('minor-two cuboid edit applies once, acknowledges canonical bounds and synchronizes normal CUI');

  snapshot = (await reconnected.exchange(editRead(editSession.epoch, 3))).value;
  await consoleAction('edit-command', 'WesoAllowed');
  assert.equal((await reconnected.exchange(editCommit(editSession.epoch, 4, snapshot.revision, shifted))).value.commitStatus, 1);
  assert.deepEqual((await reconnected.exchange(request(editSession.epoch, 5))).value.bounds, [12, 65, 20, 17, 71, 27]);
  record('real WorldEdit shift during a draft rejects the stale commit without overwriting the command');

  for (const [mode, permission, target] of [
    ['expandonly', 1, [10, 64, 20, 16, 70, 27]],
    ['contractonly', 2, [10, 64, 20, 14, 70, 27]],
    ['shiftonly', 4, [11, 64, 20, 16, 70, 27]]
  ]) {
    await consoleAction('seed', 'WesoAllowed'); await consoleAction(mode, 'WesoAllowed');
    editSession = await negotiate(reconnected, 1, 2, 7);
    snapshot = (await reconnected.exchange(editRead(editSession.epoch, 1))).value;
    assert.equal(snapshot.permissions, permission);
    const success = (await reconnected.exchange(editCommit(editSession.epoch, 2, snapshot.revision, target))).value;
    assert.equal(success.commitStatus, 0); assert.deepEqual(success.bounds, target);
    const forbidden = permission === 1 ? one.value.bounds : permission === 2 ? [11, 64, 20, 15, 70, 27] : [11, 64, 20, 17, 70, 27];
    const refusal = (await reconnected.exchange(editCommit(editSession.epoch, 3, success.revision, forbidden))).value;
    assert.equal(refusal.commitStatus, 2); assert.equal(refusal.status, 3); assert(!refusal.bounds);
    record(`${mode} is enforced from the actual target delta, including whole-box shift`);
  }

  await consoleAction('allgrants', 'WesoAllowed'); await consoleAction('reverse', 'WesoAllowed');
  editSession = await negotiate(reconnected, 1, 2, 7);
  snapshot = (await reconnected.exchange(editRead(editSession.epoch, 1))).value;
  const reversedCui = reconnected.cui.length;
  const reversed = (await reconnected.exchange(editCommit(editSession.epoch, 2, snapshot.revision, [10, 64, 20, 17, 70, 27]))).value;
  assert.equal(reversed.commitStatus, 0);
  await waitFor(() => reconnected.cui.slice(reversedCui).some(s => s.startsWith('p|0|17|70|27|'))
    && reconnected.cui.slice(reversedCui).some(s => s.startsWith('p|1|10|64|20|')), 'reversed points preserved');
  await consoleAction('extend', 'WesoAllowed');
  snapshot = (await reconnected.exchange(editRead(editSession.epoch, 3))).value;
  assert.equal((await reconnected.exchange(editCommit(editSession.epoch, 4, snapshot.revision, [10, 64, 20, 18, 70, 27]))).value.commitStatus, 0);
  record('reversed point ordering and extending-cuboid selector survive adjustment');

  await consoleAction('unsupported', 'WesoAllowed');
  assert.equal((await reconnected.exchange(editRead(editSession.epoch, 5))).value.status, 2);
  await consoleAction('empty', 'WesoAllowed');
  assert.equal((await reconnected.exchange(editRead(editSession.epoch, 6))).value.status, 1);
  await consoleAction('seed', 'WesoAllowed');
  snapshot = (await reconnected.exchange(editRead(editSession.epoch, 7))).value;
  await consoleAction('seed', 'WesoAllowed');
  assert.equal((await reconnected.exchange(editCommit(editSession.epoch, 8, snapshot.revision, shifted))).value.commitStatus, 1);
  record('unsupported/incomplete selectors and a replaced selector with identical bounds fail closed');

  await consoleAction('editgrant', 'WesoSecond');
  const otherEdit = await negotiate(second, 1, 2, 7);
  await second.exchange(editRead(otherEdit.epoch, 1));
  snapshot = (await reconnected.exchange(editRead(editSession.epoch, 9))).value;
  assert.equal((await second.exchange(editCommit(otherEdit.epoch, 2, snapshot.revision, shifted))).value.commitStatus, 1);
  await silent(second, editCommit(editSession.epoch, 3, snapshot.revision, shifted));
  let editId = 9;
  for (let face = 0; face < 6; face++) {
    await consoleAction('seed', 'WesoAllowed');
    snapshot = (await reconnected.exchange(editRead(editSession.epoch, ++editId))).value;
    const target = [...one.value.bounds]; target[face] += face < 3 ? -1 : 1;
    const result = (await reconnected.exchange(editCommit(editSession.epoch, ++editId, snapshot.revision, target))).value;
    assert.equal(result.commitStatus, 0); assert.deepEqual(result.bounds, target);
  }
  for (let corner = 0; corner < 8; corner++) {
    await consoleAction('seed', 'WesoAllowed');
    snapshot = (await reconnected.exchange(editRead(editSession.epoch, ++editId))).value;
    const target = [...one.value.bounds];
    for (let axis = 0; axis < 3; axis++) { const positive = (corner & (1 << axis)) !== 0; target[axis + (positive ? 3 : 0)] += positive ? 1 : -1; }
    const result = (await reconnected.exchange(editCommit(editSession.epoch, ++editId, snapshot.revision, target))).value;
    assert.equal(result.commitStatus, 0); assert.deepEqual(result.bounds, target);
  }
  record('all six faces and eight corners apply the expected inclusive bounds through real WorldEdit');
  await consoleAction('seed', 'WesoAllowed');
  snapshot = (await reconnected.exchange(editRead(editSession.epoch, ++editId))).value;
  assert.equal((await reconnected.exchange(editCommit(editSession.epoch, ++editId, snapshot.revision, [10, 64, 20, 15, 320, 27]))).value.commitStatus, 3);
  snapshot = (await reconnected.exchange(editRead(editSession.epoch, ++editId))).value;
  await consoleAction('editrevoke', 'WesoAllowed');
  const refused = (await reconnected.exchange(editCommit(editSession.epoch, ++editId, snapshot.revision, shifted))).value;
  assert.equal(refused.commitStatus, 2); assert.equal(refused.status, 3); assert(!refused.bounds);
  await silent(reconnected, editCommit(editSession.epoch, editId, snapshot.revision, shifted));
  await consoleAction('edit-audit-end', 'WesoAllowed');
  record('foreign revisions/epochs and live edit revocation cannot mutate or disclose protected state');
  record('selection adjustments leave the loaded audit blocks and WorldEdit undo history unchanged');
  await negotiate(second, 1, 0, 1);

}
