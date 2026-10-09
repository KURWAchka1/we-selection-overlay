import fs from 'node:fs/promises';
import path from 'node:path';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';

const require = createRequire(import.meta.url);
const sharp = require(require.resolve('sharp', { paths: [process.env.WESO_TOOL_NODE_MODULES ?? process.cwd()] }));
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const modules = ['WEaddon_fabric_1.21.11', 'WEaddon_lightposition', 'WEaddon_fabric_26.2',
  'WEaddon_neoforge_1.21.11', 'WEaddon_neoforge_26.1', 'WEaddon_neoforge_26.2'];
const bitmaps = new Map();
for (const name of ['move', 'scaling']) {
  const response = await fetch(`https://raw.githubusercontent.com/lucide-icons/lucide/main/icons/${name}.svg`);
  if (!response.ok) throw new Error(`Lucide ${name}: ${response.status}`);
  const svg = (await response.text()).replaceAll('currentColor', '#ffffff');
  bitmaps.set(name, await sharp(Buffer.from(svg)).resize(48, 48).png().toBuffer());
}
// Static anti-aliased masks: two batches, no generated geometry or off-screen passes per frame.
for (const [name, side] of [['radial-left', -1], ['radial-right', 1], ['radial-dot', 0]]) {
  const size = side ? 512 : 48, pixels = Buffer.alloc(size * size * 4);
  for (let y = 0; y < size; y++) for (let x = 0; x < size; x++) {
    const dx = (x + 0.5 - size / 2) / (size / 2), dy = (y + 0.5 - size / 2) / (size / 2);
    const distance = Math.hypot(dx, dy);
    const border = side ? Math.min(1 - distance, distance - 0.36, side * dx - 0.035) : 0.86 - distance;
    const coverage = Math.max(0, Math.min(1, border * size / 2 + 0.5));
    const index = (y * size + x) * 4;
    pixels[index] = pixels[index + 1] = pixels[index + 2] = 255;
    pixels[index + 3] = Math.round(coverage * 255);
  }
  bitmaps.set(name, await sharp(pixels, { raw: { width: size, height: size, channels: 4 } }).png().toBuffer());
}
for (const module of modules) {
  const directory = path.join(root, module, 'src/main/resources/assets/worldedit_selection_overlay/textures/gui/ui');
  for (const [name, bitmap] of bitmaps) await fs.writeFile(path.join(directory, name + '.png'), bitmap);
}
console.log('Generated licensed selection icons and static radial masks in six runtimes.');
