import assert from 'node:assert/strict';

/** Observe immediately after spawn; only an orderly zero-exit shutdown can pass. */
export function observeOwnedServer(child) {
  let closed = false, code, signal, failure, shutdown;
  const completion = new Promise(resolve => child.once('close', (exitCode, exitSignal) => {
    closed = true; code = exitCode; signal = exitSignal; resolve();
  }));
  child.on('error', error => { failure ??= error; });
  child.stdin?.on('error', error => { failure ??= error; });
  async function waitClosed(millis) {
    let timer;
    try {
      await Promise.race([completion, new Promise(resolve => { timer = setTimeout(resolve, millis); })]);
      return closed;
    } finally { clearTimeout(timer); }
  }
  async function stop(graceMillis, killMillis) {
    assert(Number.isInteger(graceMillis) && graceMillis > 0 && graceMillis <= 300_000);
    assert(Number.isInteger(killMillis) && killMillis > 0 && killMillis <= 30_000);
    if (closed) throw new Error(`Owned server closed before shutdown: code=${code}, signal=${signal}`, { cause: failure });
    if (!failure) {
      try {
        assert(child.stdin?.writable, 'Owned server stdin is unavailable');
        child.stdin.write('stop\n', error => { if (error) failure ??= error; });
      } catch (error) { failure ??= error; }
    }
    let forced = false;
    if (!await waitClosed(graceMillis)) {
      forced = true;
      child.kill();
      if (!await waitClosed(killMillis)) throw new Error(`Owned server PID ${child.pid} did not close after termination`);
    }
    if (failure) throw new Error('Owned server process or stdin failed', { cause: failure });
    if (forced) throw new Error('Owned server required forced termination; run cannot pass');
    assert.equal(signal, null, 'Owned server ended by signal');
    assert.equal(code, 0, 'Owned server shutdown failed');
    return { code, signal, forced };
  }
  return {
    stop({ graceMillis = 25_000, killMillis = 10_000 } = {}) {
      shutdown ??= stop(graceMillis, killMillis);
      return shutdown;
    }
  };
}
