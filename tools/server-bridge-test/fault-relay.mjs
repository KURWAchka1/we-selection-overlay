import assert from 'node:assert/strict';
import { createConnection, createServer } from 'node:net';
import { performance } from 'node:perf_hooks';

const MAX_FRAME = 2 * 1024 * 1024 - 1, MAX_BUFFER = 8 * 1024 * 1024;

// This fixture accepts unencrypted, uncompressed loopback traffic only.
export class FrameReader {
  buffer = Buffer.alloc(0);
  push(bytes) {
    assert(this.buffer.length + bytes.length <= MAX_BUFFER, 'Relay input bound exceeded');
    this.buffer = this.buffer.length ? Buffer.concat([this.buffer, bytes]) : bytes;
    const frames = [];
    while (this.buffer.length) {
      let length = 0, header = 0;
      for (; header < 3 && header < this.buffer.length; header++) {
        const b = this.buffer[header]; length |= (b & 127) << (header * 7);
        if (!(b & 128)) { header++; break; }
      }
      if (header && (this.buffer[header - 1] & 128)) {
        assert(header < 3, 'Overlong relay packet length'); break;
      }
      assert(length > 0 && length <= MAX_FRAME, 'Invalid relay packet length');
      if (this.buffer.length < header + length) break;
      frames.push({ bytes: Buffer.from(this.buffer.subarray(0, header + length)), payload: Buffer.from(this.buffer.subarray(header, header + length)) });
      this.buffer = this.buffer.subarray(header + length);
    }
    return frames;
  }
}

export class FaultRelay {
  constructor(prefixes, { frontPort = 25579, backPort = 25580 } = {}) {
    assert(Number.isInteger(frontPort) && frontPort >= 0 && frontPort <= 65535);
    assert(Number.isInteger(backPort) && backPort > 0 && backPort <= 65535);
    this.frontPort = frontPort; this.backPort = backPort;
    this.prefixes = prefixes; this.pairs = new Set(); this.mode = null;
    this.held = null; this.failures = []; this.stats = { commits: 0, replies: 0, held: 0, released: 0, peakBytes: 0, delayedFrames: 0 };
  }
  arm(mode) {
    assert(['slow', 'late', 'revoked'].includes(mode));
    assert.equal(this.mode, null); assert.equal(this.held, null); assert.equal(this.pairs.size, 1);
    this.mode = mode;
  }
  release() {
    assert(this.held, 'No intercepted frame');
    const held = this.held; this.held = null; this.stats.released++;
    held.direction.enqueue(held.frame, 0);
  }
  async listen() {
    this.server = createServer(front => {
      if (this.pairs.size) { this.failures.push('Unexpected second relay connection'); front.destroy(); return; }
      const back = createConnection({ host: '127.0.0.1', port: this.backPort });
      const pair = { front, back, directions: [], closed: false };
      this.pairs.add(pair);
      const close = error => {
        if (pair.closed) return;
        pair.closed = true;
        if (error) this.failures.push(String(error));
        for (const d of pair.directions) { clearTimeout(d.timer); d.queue.length = 0; }
        front.destroy(); back.destroy(); this.pairs.delete(pair);
      };
      for (const socket of [front, back]) { socket.setNoDelay(true); socket.on('error', close); socket.on('close', () => close()); }
      for (const [from, to, name] of [[front, back, 'toServer'], [back, front, 'toClient']]) {
        const reader = new FrameReader();
        const direction = { queue: [], bytes: 0, blocked: false, timer: null };
        pair.directions.push(direction);
        const pump = () => {
          clearTimeout(direction.timer);
          while (!pair.closed && !direction.blocked && direction.queue.length) {
            const first = direction.queue[0], wait = first.due - performance.now();
            if (wait > 0) { direction.timer = setTimeout(pump, wait); return; }
            direction.queue.shift(); direction.bytes -= first.bytes.length;
            if (!to.write(first.bytes)) { direction.blocked = true; from.pause(); }
          }
        };
        direction.enqueue = (frame, delayMillis) => {
          assert(direction.bytes + to.writableLength + frame.bytes.length <= MAX_BUFFER, 'Relay output bound exceeded');
          direction.bytes += frame.bytes.length;
          this.stats.peakBytes = Math.max(this.stats.peakBytes, direction.bytes + to.writableLength);
          direction.queue.push({ bytes: frame.bytes, due: performance.now() + delayMillis });
          pump();
        };
        to.on('drain', () => { direction.blocked = false; from.resume(); pump(); });
        from.on('data', bytes => {
          try {
            for (const frame of reader.push(bytes)) {
              let opcode;
              const prefix = this.prefixes[name];
              // Armed only after native PLAY, selection capture and CUI are verified.
              if (this.mode && frame.payload.subarray(0, prefix.length).equals(prefix)) {
                const data = frame.payload.subarray(prefix.length);
                assert(data.length >= 6 && data.length <= 256 && data.readUInt32BE() === 0x5745534f && data[4] === 1, 'Invalid bridge frame in relay');
                opcode = data[5];
                if (opcode === 8) this.stats.commits++;
                if (opcode === 9) this.stats.replies++;
              }
              if (!this.stats.held && ((this.mode === 'late' && name === 'toClient' && opcode === 9)
                  || (this.mode === 'revoked' && name === 'toServer' && opcode === 8))) {
                this.held = { frame, direction }; this.stats.held++; continue;
              }
              const latency = this.mode === 'slow' ? 400 : 0;
              if (latency) this.stats.delayedFrames++;
              direction.enqueue(frame, latency);
            }
          } catch (error) { close(error); }
        });
      }
    });
    await new Promise((resolve, reject) => { this.server.once('error', reject); this.server.listen(this.frontPort, '127.0.0.1', resolve); });
  }
  async close() {
    for (const pair of this.pairs) { for (const d of pair.directions) clearTimeout(d.timer); pair.front.destroy(); pair.back.destroy(); }
    this.held = null;
    if (this.server?.listening) await new Promise((resolve, reject) => this.server.close(error => error ? reject(error) : resolve()));
  }
}
