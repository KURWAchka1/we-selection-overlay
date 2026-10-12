import assert from 'node:assert/strict';
import { mkdtempSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import test from 'node:test';
import { recordLoadWindow, validateLoadWindow } from './load-window.mjs';

const options = { players: 2, samples: 100, installed: true, baselineOnly: false };
function window(label = 'flood') {
  return { label, samples: 100, players: 2, wallMillis: 5000, tickMillis: Array(100).fill(2),
    generatedChunks: 0, chunkLoads: 0, sent: [100, 1000], received: [95, 100],
    bridge: { admitted: 195, reads: 195, bytes: 12480, replies: 195, malformed: 0, throttled: 905 } };
}

test('valid active and silent windows retain the existing admission/progress gates', () => {
  for (const label of ['one', 'many', 'flood']) validateLoadWindow(window(label), options);
  for (const label of ['silent', 'idle', 'recovery']) {
    const data = window(label);
    data.sent.fill(0); data.received.fill(0);
    data.bridge = { admitted: 0, reads: 0, bytes: 0, replies: 0, malformed: 0 };
    validateLoadWindow(data, options);
    validateLoadWindow(data, { ...options, baselineOnly: true });
    validateLoadWindow({ ...data, bridge: null }, { ...options, installed: false });
  }
});

test('missing workload and starvation never validate', () => {
  for (const [received, sent, message] of [[89, 100, /starved/], [0, 0, /no workload/]]) {
    const data = window(); data.received[0] = received; data.sent[0] = sent;
    assert.throws(() => validateLoadWindow(data, options), message);
  }
  const data = window('many'); data.received[1] = 0;
  assert.throws(() => validateLoadWindow(data, options), /no reply/);
});

test('idle transfers, missing flood pressure and excess admissions fail', () => {
  assert.throws(() => validateLoadWindow(window('idle'), options));
  for (const [key, value] of [['admitted', 651], ['malformed', 1], ['throttled', 0]]) {
    const data = window(); data.bridge[key] = value;
    assert.throws(() => validateLoadWindow(data, options));
  }
  assert.throws(() => validateLoadWindow(window('unknown'), options), /Unknown active/);
});

test('wrong counts, non-finite durations, generation and baseline chunk loads fail', () => {
  const mutations = [data => data.samples++, data => data.players++, data => data.tickMillis.pop(),
    data => data.tickMillis[0] = NaN, data => data.tickMillis[0] = -1, data => data.wallMillis = Infinity,
    data => data.sent.pop(), data => data.received[0] = .5, data => data.generatedChunks = 1];
  for (const mutate of mutations) {
    const data = window(); mutate(data);
    assert.throws(() => validateLoadWindow(data, options));
  }
  const data = window(); data.chunkLoads = 1;
  assert.throws(() => validateLoadWindow(data, { ...options, baselineOnly: true }), /baseline loaded chunks/);
});

test('failed window retains raw measurements and is not marked accepted', t => {
  const directory = mkdtempSync(join(tmpdir(), 'weso-load-window-'));
  t.after(() => rmSync(directory, { recursive: true, force: true }));
  const path = join(directory, 'window.json'), data = window();
  data.received[0] = 0;
  assert.throws(() => recordLoadWindow(path, data, options), /starved/);
  assert.deepEqual(JSON.parse(readFileSync(path, 'utf8')), { ...data, validated: false });
  data.received[0] = 95;
  recordLoadWindow(path, data, options);
  assert.deepEqual(JSON.parse(readFileSync(path, 'utf8')), { ...data, validated: true });
});
