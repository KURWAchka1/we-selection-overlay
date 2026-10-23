import assert from 'node:assert/strict';

// Reasons describe the enclosing native window, not timed portions of Java execution.
export function summarizeDispatchEvidence(rows) {
  assert(Array.isArray(rows) && rows.length > 0 && rows.length <= 2400, 'Invalid root count');
  const identities = new Set();
  for (const row of rows) {
    assert(row.nativeMatched === true, 'Missing native match');
    for (const name of ['id', 'thread', 'wallNanos', 'cycles', 'switches', 'beforeReadNanos', 'afterReadNanos', 'classLoads'])
      assert(Number.isSafeInteger(row[name]) && row[name] >= 0, `Invalid ${name}`);
    assert(row.switches <= 0xffff_ffff, 'Invalid switch delta');
    assert(typeof row.reasonsHex === 'string' && /^[0-9a-f]{1,16}$/.test(row.reasonsHex), 'Invalid reason bitmap');
    assert(row.stallTotals && typeof row.stallTotals === 'object' && !Array.isArray(row.stallTotals), 'Invalid stalls');
    const key = `${row.thread}:${row.id}`;
    assert(!identities.has(key), 'Duplicate root identity');
    identities.add(key);
  }
  const has = (row, bit) => (BigInt(`0x${row.reasonsHex}`) & (1n << BigInt(bit))) !== 0n;
  return [0, 500_000, 1_000_000, 2_000_000].map(thresholdNanos => {
    const subset = rows.filter(row => row.wallNanos > thresholdNanos);
    const count = predicate => subset.filter(predicate).length;
    const bitmaps = {};
    for (const row of subset) bitmaps[row.reasonsHex] = (bitmaps[row.reasonsHex] ?? 0) + 1;
    return {
      aboveNanosExclusive: thresholdNanos, roots: subset.length,
      zeroSwitch: count(row => row.switches === 0),
      preemptedWithinNativeWindow: count(row => has(row, 32)),
      suspendedWithinNativeWindow: count(row => has(row, 5) || has(row, 12)),
      withClassLoadOverlap: count(row => row.classLoads > 0),
      withRecordedStallOverlap: count(row => Object.keys(row.stallTotals).length > 0),
      bitmaps,
    };
  });
}
