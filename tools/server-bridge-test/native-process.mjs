import { createWriteStream } from 'node:fs';

export function captureProcess(child, path) {
  const log = createWriteStream(path), lines = [];
  const drained = new Promise((resolve, reject) => { log.once('finish', resolve); log.once('error', reject); });
  drained.catch(() => {});
  const state = { child, lines, exited: false, code: null, log, drained };
  for (const stream of [child.stdout, child.stderr]) {
    let pending = '';
    stream.on('data', bytes => {
      log.write(bytes); pending += bytes.toString();
      let end;
      while ((end = pending.indexOf('\n')) >= 0) { lines.push(pending.slice(0, end).replace(/\r$/, '')); pending = pending.slice(end + 1); }
    });
    stream.on('end', () => { if (pending) lines.push(pending); });
  }
  child.on('error', ex => { lines.push(`LAUNCH ERROR: ${ex}`); state.exited = true; state.code = -1; });
  child.on('close', code => { state.exited = true; state.code = code; log.end(); });
  return state;
}
