import assert from 'node:assert/strict';
import { join } from 'node:path';
import { gestureTuple } from './gesture-tuples.mjs';

export function loadHost(mode, options = []) {
  assert(['load','baseline','restart','timeline','fair','gameplay'].includes(mode));
  assert(new Set(options).size === options.length, 'Duplicate load option');
  assert(options.every(option => ['--without-spark','26.2'].includes(option)), 'Unsupported load option/host');
  const version = options.includes('26.2') ? '26.2' : '1.21.11';
  const withoutSpark = options.includes('--without-spark');
  assert(!withoutSpark || mode === 'gameplay', 'Profiler control requires matched gameplay');
  assert(version !== '26.2' || mode === 'gameplay' && withoutSpark, 'Exact 26.2 requires matched no-spark gameplay');
  const name = withoutSpark ? 'bridge-gameplay-control-proof' : { load:'bridge-load-proof', baseline:'bridge-baseline-proof',
    restart:'bridge-restart-load-proof', timeline:'bridge-tick-timeline-proof', fair:'bridge-fair-dispatch-proof', gameplay:'bridge-gameplay-proof' }[mode];
  return Object.freeze({ version, withoutSpark, directory: join('build',name,...(version === '26.2' ? [version] : [])),
    tuple: gestureTuple(version) });
}
