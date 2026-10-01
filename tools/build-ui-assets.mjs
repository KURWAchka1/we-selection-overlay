import fs from 'node:fs/promises';
import path from 'node:path';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';

const require = createRequire(import.meta.url);
const sharp = require(require.resolve('sharp', { paths: [process.env.WESO_TOOL_NODE_MODULES ?? process.cwd()] }));
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const modules = ['WEaddon_fabric_1.21.11', 'WEaddon_lightposition', 'WEaddon_fabric_26.2',
  'WEaddon_neoforge_1.21.11', 'WEaddon_neoforge_26.1', 'WEaddon_neoforge_26.2'];
const assetPath = 'src/main/resources/assets/worldedit_selection_overlay';

async function download(url) {
  const response = await fetch(url);
  if (!response.ok) throw new Error(`${response.status}: ${url}`);
  return Buffer.from(await response.arrayBuffer());
}

const font = await download('https://raw.githubusercontent.com/google/fonts/main/ofl/manrope/Manrope%5Bwght%5D.ttf');
const fontLicense = await download('https://raw.githubusercontent.com/google/fonts/main/ofl/manrope/OFL.txt');
const iconLicense = await download('https://raw.githubusercontent.com/lucide-icons/lucide/main/LICENSE');
const icons = {};
for (const name of ['pencil', 'palette', 'check', 'search', 'list-filter', 'refresh-cw', 'x', 'sliders-horizontal', 'chevron-down']) {
  const svg = await download(`https://raw.githubusercontent.com/lucide-icons/lucide/main/icons/${name}.svg`);
  icons[name] = await sharp(Buffer.from(svg.toString().replaceAll('currentColor', '#ffffff')))
    .resize(48, 48).png().toBuffer();
}

// A small signed-distance tile: corners stay fixed while the nine-slice center stretches.
const size = 32;
const pixels = Buffer.alloc(size * size * 4);
for (let y = 0; y < size; y++) {
  for (let x = 0; x < size; x++) {
    const qx = Math.abs(x + 0.5 - 16) - 9;
    const qy = Math.abs(y + 0.5 - 16) - 9;
    const distance = Math.hypot(Math.max(qx, 0), Math.max(qy, 0)) + Math.min(Math.max(qx, qy), 0) - 6;
    const value = Math.round(255 * Math.max(0, Math.min(1, 0.5 - distance / 8)));
    const offset = (y * size + x) * 4;
    pixels[offset] = pixels[offset + 1] = pixels[offset + 2] = value;
    pixels[offset + 3] = 255;
  }
}
const sdf = await sharp(pixels, { raw: { width: size, height: size, channels: 4 } }).png().toBuffer();
for (const module of modules) {
  const base = path.join(root, module, assetPath);
  await fs.mkdir(path.join(base, 'textures/gui/ui'), { recursive: true });
  await fs.mkdir(path.join(base, 'font'), { recursive: true });
  await fs.writeFile(path.join(base, 'font/manrope.ttf'), font);
  await fs.writeFile(path.join(base, 'font/manrope-ofl.txt'), fontLicense);
  await fs.writeFile(path.join(base, 'textures/gui/ui/lucide-license.txt'), iconLicense);
  await fs.writeFile(path.join(base, 'textures/gui/ui/surface.png'), sdf);
  for (const [name, bitmap] of Object.entries(icons)) {
    await fs.writeFile(path.join(base, `textures/gui/ui/${name}.png`), bitmap);
  }
}
console.log(`Generated SDF surface, ${Object.keys(icons).length} Lucide icons, and Manrope resources in ${modules.length} runtimes.`);
