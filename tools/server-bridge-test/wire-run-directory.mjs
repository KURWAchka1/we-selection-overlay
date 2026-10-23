import assert from 'node:assert/strict';
import { existsSync } from 'node:fs';
import { join, relative, resolve, sep } from 'node:path';

export function ownedWireRunDirectory(root, candidate) {
  const directory = resolve(candidate), parts = relative(resolve(root), directory).split(sep);
  assert(parts[0] === 'build' && parts[1] === 'bridge-paper-proof' &&
    (parts.length === 2 || parts.length === 3 && /^\d{8}-\d{6}-[0-9a-f]{8}$/.test(parts[2])), 'Never operate a user server');
  return directory;
}

export function requireUnusedWireEvidence(directory) {
  for (const file of ['wire-proof.json', 'wire-server.log'])
    assert(!existsSync(join(directory, file)), `Never overwrite existing wire evidence: ${file}`);
}
