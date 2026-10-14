import assert from 'node:assert/strict';
import test from 'node:test';
import { channel } from './wire-codec.mjs';
import { faultPrefixes, verifyFaultEntry, verifyFaultMatrix } from './native-fault-evidence.mjs';

const counters = (connections, captures, commits, applied) => ({ connections, captures, commits, applied, pending: 0, tasks: 0 });
function entry(loader = 'fabric', scenario = 'fault-slow') {
  const late = scenario === 'fault-late', revoked = scenario === 'fault-revoked';
  const base = counters(0, 3, 2, 2), done = counters(1, late ? 5 : 4, revoked ? 2 : 3, revoked ? 2 : 3);
  const bounds = [10, 64, 20, 15, revoked ? 70 : 71, 27];
  return { loader, scenario, baseline: base, verified: done, final: { ...done, connections: 0 },
    phases: ['setup', 'arm', ...(late ? ['timeout'] : []), 'verified', 'disconnect'],
    relay: { commits: 1, replies: 1, held: late || revoked ? 1 : 0, released: late || revoked ? 1 : 0,
      peakBytes: 1024, delayedFrames: late || revoked ? 0 : 10 },
    pendingObserved: true, auditPassed: true, serverBounds: bounds,
    heldReply: late ? { requestId: '9', commitStatus: 0, bounds: [...bounds] } : undefined,
    beforeDelivery: revoked ? counters(1, 4, 2, 2) : undefined, revokedBeforeDelivery: revoked };
}
const matrix = () => ['fabric', 'neoforge'].flatMap(loader => ['slow', 'late', 'revoked'].map(mode => entry(loader, `fault-${mode}`)));
const report = () => ({ minecraft: '26.2', toServer: Buffer.concat([Buffer.from([22, Buffer.byteLength(channel)]), Buffer.from(channel)]).toString('hex'),
  toClient: Buffer.concat([Buffer.from([24, Buffer.byteLength(channel)]), Buffer.from(channel)]).toString('hex') });

test('accepts complete exact six-case native fault evidence', () => verifyFaultMatrix(matrix()));
test('rejects missing, repeated or reordered scenarios and phases', () => {
  const m = matrix(); m[5] = structuredClone(m[0]); assert.throws(() => verifyFaultMatrix(m));
  assert.throws(() => verifyFaultMatrix(matrix().slice(1)));
  const e = entry(); e.phases.reverse(); assert.throws(() => verifyFaultEntry(e));
});
test('rejects retries, extra captures and leaked cleanup counters', () => {
  for (const key of ['commits', 'applied', 'captures', 'pending', 'tasks', 'connections']) {
    const e = entry(); e.final[key]++; assert.throws(() => verifyFaultEntry(e), key);
  }
});
test('rejects missing native pending, world audit and canonical bounds', () => {
  for (const key of ['pendingObserved', 'auditPassed']) { const e = entry(); e[key] = false; assert.throws(() => verifyFaultEntry(e)); }
  const e = entry(); e.serverBounds[4] = 70; assert.throws(() => verifyFaultEntry(e));
});
test('requires actual held successful ack and pre-delivery revocation evidence', () => {
  for (const mutate of [e => { e.heldReply.commitStatus = 1; }, e => { e.heldReply.bounds[4] = 70; }, e => { delete e.heldReply; }]) {
    const e = entry('fabric', 'fault-late'); mutate(e); assert.throws(() => verifyFaultEntry(e));
  }
  for (const mutate of [e => { e.beforeDelivery.commits++; }, e => { e.revokedBeforeDelivery = false; }]) {
    const e = entry('neoforge', 'fault-revoked'); mutate(e); assert.throws(() => verifyFaultEntry(e));
  }
});
test('rejects missing delay, incorrect held frames and relay overflow', () => {
  for (const key of ['commits', 'replies', 'held', 'released', 'delayedFrames', 'peakBytes']) {
    const e = entry(); e.relay[key] = key === 'peakBytes' ? 8 * 1024 * 1024 + 1 : 0;
    if (key === 'held' || key === 'released') e.relay[key] = 1;
    assert.throws(() => verifyFaultEntry(e), key);
  }
});
test('accepts host-discovered custom-payload prefixes, including multi-byte packet IDs', () => {
  assert.equal(faultPrefixes(report(), '26.2').toServer[0], 22);
  const r = report(); r.toServer = '8001' + r.toServer.slice(2); assert.equal(faultPrefixes(r, '26.2').toServer[1], 1);
});
test('rejects wrong host, channel and malformed packet prefixes', () => {
  assert.throws(() => faultPrefixes(report(), '26.1.2'));
  for (const hex of ['GG', 'f', '', '80', '8000', '16ff', '80ff', '80808000']) {
    const r = report(); r.toServer = hex + r.toServer.slice(2); assert.throws(() => faultPrefixes(r, '26.2'), hex);
  }
  const r = report(); r.toClient = r.toClient.slice(0, -2) + '00'; assert.throws(() => faultPrefixes(r, '26.2'));
});
