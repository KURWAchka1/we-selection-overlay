import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const modules = ['WEaddon_fabric_1.21.11', 'WEaddon_lightposition', 'WEaddon_fabric_26.2',
  'WEaddon_neoforge_1.21.11', 'WEaddon_neoforge_26.1', 'WEaddon_neoforge_26.2'];
const description = await fs.readFile(path.join(root, 'WEaddon_lightposition/MOD_DESCRIPTION.md'), 'utf8');
const imageLinks = text => [...text.matchAll(/!\[[^\]]*\]\(([^)]+)\)/g)].map(match => match[1]);
const release = await fs.readFile(path.join(root, 'changelog-1.8.0.md'), 'utf8');
const section = '## 1.8.0\n\n' + release.replace(/^# [^\n]+\n+/, '').replace(/^## /gm, '### ').trim() + '\n\n';
for (const module of modules) {
  const descPath = path.join(root, module, 'MOD_DESCRIPTION.md');
  let old = null;
  try { old = await fs.readFile(descPath, 'utf8'); }
  catch (error) { if (error.code !== 'ENOENT') throw error; }
  if (old && JSON.stringify(imageLinks(old)) !== JSON.stringify(imageLinks(description)))
    throw new Error(`Image URLs differ; refusing to overwrite ${descPath}`);
  await fs.writeFile(descPath, description);
  const logPath = path.join(root, module, 'CHANGELOG.md');
  const log = await fs.readFile(logPath, 'utf8');
  const prior = log.replace(/^## 1\.8\.0\s*\n[\s\S]*?(?=^## |$(?![\s\S]))/m, '');
  await fs.writeFile(logPath, prior.replace(/^# Changelog\s*\n+/, '# Changelog\n\n' + section));
}
