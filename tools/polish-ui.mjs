import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { execFileSync } from 'node:child_process';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const modules = ['WEaddon_fabric_1.21.11', 'WEaddon_lightposition', 'WEaddon_fabric_26.2', 'WEaddon_neoforge_1.21.11', 'WEaddon_neoforge_26.1', 'WEaddon_neoforge_26.2'];
const canonicalInspector = await fs.readFile(path.join(root, 'WEaddon_fabric_26.2/src/main/java/org/marshal/weaddon_lightposition/client/screen/SelectionBlocksScreen.java'), 'utf8');
const summaryStart = canonicalInspector.indexOf('    private void drawSelectionSummary(');
const summaryEnd = canonicalInspector.indexOf('    private void drawFooter(', summaryStart);
if (summaryStart < 0 || summaryEnd < 0) throw new Error('Missing canonical selection summary');
const selectionSummary = canonicalInspector.slice(summaryStart, summaryEnd);
for (const module of modules) {
  const base = path.join(root, module, 'src/main');
  for (const lang of ['en_us', 'ru_ru']) {
    const file = path.join(base, 'resources/assets/worldedit_selection_overlay/lang', lang + '.json');
    let json = await fs.readFile(file, 'utf8');
    json = json.replace(/("screen.worldedit_selection_overlay.settings.colors_short":\s*)"[^"]*"/, '$1"' + (lang === 'en_us' ? 'Overlay palette' : 'Палитра оверлея') + '"');
    const translations = JSON.parse(json.replace(/^\uFEFF/, ''));
    for (const [key, en, ru] of [['cells', 'Cells: %s', 'Ячеек: %s'], ['types', 'Types: %s', 'Типов: %s'], ['loaded', 'Loaded: %s', 'Загружено: %s'], ['unloaded', 'Unloaded: %s', 'Не загружено: %s']]) {
      translations['screen.worldedit_selection_overlay.blocks.summary.' + key] = lang === 'en_us' ? en : ru;
    }
    let formatSource = json;
    if (process.argv.includes('--restore-format')) {
      formatSource = execFileSync('git', ['show', 'HEAD:' + path.relative(root, file).replaceAll('\\', '/')], { cwd: root, encoding: 'utf8' });
    }
    const formats = new Map();
    for (const line of formatSource.split(/\r?\n/)) {
      const match = line.match(/^(\s*)("(?:[^"\\]|\\.)*")(\s*:\s*)/);
      if (match) formats.set(JSON.parse(match[2]), match[1] + match[2] + match[3]);
    }
    const entries = Object.entries(translations);
    const output = entries.map(([key, value], index) => (formats.get(key) ?? '  ' + JSON.stringify(key) + ': ') + JSON.stringify(value) + (index < entries.length - 1 ? ',' : ''));
    await fs.writeFile(file, (formatSource.startsWith('\uFEFF') ? '\uFEFF' : '') + '{\n' + output.join('\n') + '\n}\n');
  }
  for (const name of ['SelectionBlocksScreen', 'OverlayColorSettingsScreen']) {
    const file = path.join(base, 'java/org/marshal/weaddon_lightposition/client/screen', name + '.java');
    let code = await fs.readFile(file, 'utf8');
    const measure = module === 'WEaddon_fabric_1.21.11' ? 'getWidth' : 'width';
    const trim = module === 'WEaddon_fabric_1.21.11' ? 'trimToWidth' : 'plainSubstrByWidth';
    code = code.replaceAll(`font.${measure}(`, 'UiChrome.width(font, ').replaceAll(`font.${trim}(`, 'UiChrome.trim(font, ');
    if (module === 'WEaddon_fabric_1.21.11') {
      code = code.replaceAll('textRenderer.getWidth(', 'UiChrome.width(textRenderer, ').replaceAll('textRenderer.trimToWidth(', 'UiChrome.trim(textRenderer, ');
    }
    code = code.replace(/(public boolean mouseClicked\([^\n]+\) \{\n)(?!        if \(isClosing)/, '$1        if (isClosing()) return true;\n');
    code = code.replace(/(public boolean mouseScrolled\([^\n]+\) \{\n)(?!        if \(isClosing)/, '$1        if (isClosing()) return true;\n');
    if (name === 'SelectionBlocksScreen') {
      if (!code.includes('private static final int RAIL_WIDTH')) code = code.replace('    private static final int PANEL_MARGIN = 16;', '    private static final int PANEL_MARGIN = 16;\n    private static final int RAIL_WIDTH = 188;\n    private static final int RAIL_INSET = 24;');
      code = code.replaceAll('rail ? panelX + 12 :', 'rail ? RAIL_INSET :');
      code = code.replace('int searchX = panelX + (rail ? 12 : 10);', 'int searchX = rail ? RAIL_INSET : panelX + 10;');
      code = code.replace('panelX + 158, panelY + 12, panelX + 159,', 'RAIL_WIDTH, panelY + 12, RAIL_WIDTH + 1,');
      code = code.replace('title, panelX + 10, panelY + 8, style.text', 'title, hasRail() ? RAIL_INSET : panelX + 10, panelY + 8, style.text');
      code = code.replace('return panelX() + (hasRail() ? 168 : 10);', 'return hasRail() ? RAIL_WIDTH + 12 : panelX() + 10;');
      code = code.replace('return Math.max(1, panelWidth() - (hasRail() ? 178 : 20));', 'return Math.max(1, panelX() + panelWidth() - 10 - listX());');
      code = code.replace('        drawFooter(context, panelX + 10, panelY + panelHeight - FOOTER_HEIGHT + 6, panelWidth - 20);', '        if (style == UiStyle.LEGACY) drawFooter(context, panelX + 10, panelY + panelHeight - FOOTER_HEIGHT + 6, panelWidth - 20);\n        else drawSelectionSummary(context);');
      if (!code.includes('private void drawSelectionSummary(')) {
        let summary = selectionSummary;
        if (module === 'WEaddon_fabric_1.21.11') summary = summary.replaceAll('GuiGraphicsExtractor', 'DrawContext').replaceAll('Component', 'Text').replaceAll('context.pose()', 'context.getMatrices()');
        if (module === 'WEaddon_neoforge_1.21.11') summary = summary.replaceAll('GuiGraphicsExtractor', 'GuiGraphics');
        code = code.replace('    private void drawFooter(', summary + '    private void drawFooter(');
      }
      code = code.replace('return Math.max(1, panelHeight() - (listY() - panelY()) - FOOTER_HEIGHT);', 'return Math.max(1, panelHeight() - (listY() - panelY()) - (UiStyle.current() == UiStyle.LEGACY ? FOOTER_HEIGHT : 8));');
      code = code.replace('UiChrome.panel(context, x, y, width, height, UiStyle.current());', 'UiChrome.popup(context, x, y, width, height, UiStyle.current());');
      code = code.replace('UiChrome.fill(context, x, y, x + 12, y + 12, fillColor);\n        drawOutline(context, x, y, 12, 12, borderColor);', 'UiChrome.fill(context, x, y, x + 12, y + 12, borderColor);\n        UiChrome.fill(context, x + 1, y + 1, x + 11, y + 11, fillColor);');
      code = code.replace(/UiChrome.text\(context, (?:font|textRenderer), "\\u2713", x \+ 3, y \+ 1, 0xFFE9FFF0, false\);/, 'UiChrome.icon(context, "check", x + 1, y + 1, 10, 0xFFE9FFF0);');
      code = code.replace('        int fillColor = checked ? 0xFF254A35 : 0xFF161B22;\n        UiChrome.fill(context, x, y, x + 12, y + 12, borderColor);\n        UiChrome.fill(context, x + 1, y + 1, x + 11, y + 11, fillColor);\n        if (checked) {', '        UiChrome.squareOutline(context, x, y, 12, 12, borderColor);\n        if (checked) {\n            UiChrome.fill(context, x + 1, y + 1, x + 11, y + 11, 0x30254A35);');
      code = code.replace('filtersMenuOpen = open;\n        updateFilterMenuVisibility();', 'filtersMenuOpen = open;\n        searchField.setFocused(false);\n        searchField.active = !open;\n        sortButton.active = refreshButton.active = !open;\n        setFocused(open ? filterSearchField : searchField);\n        updateFilterMenuVisibility();');
      code = code.replace('        setFocused(open ? filterSearchField : searchField);\n        updateFilterMenuVisibility();', '        updateFilterMenuVisibility();\n        setFocused(open ? filterSearchField : searchField);');
      if (!code.includes('public boolean keyPressed(')) {
        const input = module === 'WEaddon_fabric_1.21.11' ? 'net.minecraft.client.input.KeyInput' : 'net.minecraft.client.input.KeyEvent';
        code = code.replace('    private void setFiltersMenuOpen(', `    @Override\n    public boolean keyPressed(${input} input) {\n        if (isClosing()) return true;\n        if (filtersMenuOpen && input.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {\n            setFiltersMenuOpen(false);\n            return true;\n        }\n        return super.keyPressed(input);\n    }\n\n    private void setFiltersMenuOpen(`);
      }
    } else {
      if (!code.includes('private static final int LINE_WIDTH_GROUP_GAP')) code = code.replace('    private static final int COLOR_GRADIENT_STEP = 4;', '    private static final int COLOR_GRADIENT_STEP = 4;\n    private static final int LINE_WIDTH_GROUP_GAP = 10;');
      code = code.replace('return redSliderY() + 96;', 'return redSliderY() + 96 + LINE_WIDTH_GROUP_GAP;');
      code = code.replace('int slidersBottom = spectrumBottom + 22 + 58 + 96 + 42;', 'int slidersBottom = spectrumBottom + 22 + 58 + 96 + LINE_WIDTH_GROUP_GAP + 42;');
      const fieldLabel = `UiChrome.text(context, ${module === 'WEaddon_fabric_1.21.11' ? 'textRenderer, Text' : 'font, Component'}.translatable("screen.worldedit_selection_overlay.colors.hex"), x, y + 5, 0xFFB4C0D3, false);`;
      if (!code.includes('if (hexField.visible) UiChrome.text')) code = code.replace(fieldLabel, 'if (hexField.visible) ' + fieldLabel + '\n        if (!redField.visible) return;');
      code = code.replace('        boolean fieldsVisible = !stackedLayout() || isWidgetVisibleInContent(fieldY, 48);\n        hexField.visible = fieldsVisible;\n        redField.visible = fieldsVisible;\n        greenField.visible = fieldsVisible;\n        blueField.visible = fieldsVisible;\n        alphaField.visible = fieldsVisible;', '        hexField.visible = !stackedLayout() || isWidgetVisibleInContent(fieldY, 18);\n        boolean channelsVisible = !stackedLayout() || isWidgetVisibleInContent(fieldY + 28, 18);\n        redField.visible = channelsVisible;\n        greenField.visible = channelsVisible;\n        blueField.visible = channelsVisible;\n        alphaField.visible = channelsVisible;');
      code = code.replace('        int markerY = y + Math.round(hue * (spectrumHeight - 1));\n        UiChrome.fill(context, x - 3, markerY - 1, x + hueWidth + 3, markerY + 2, 0xFFFFFFFF);\n        UiChrome.fill(context, x - 2, markerY, x + hueWidth + 2, markerY + 1, 0xFF0B0E14);', '        int markerY = y + 1 + Math.round(hue * (spectrumHeight - 3));\n        UiChrome.fill(context, x, markerY - 1, x + hueWidth, markerY + 2, 0xFFFFFFFF);\n        UiChrome.fill(context, x + 1, markerY, x + hueWidth - 1, markerY + 1, 0xFF0B0E14);');
      code = code.replace('        drawOutline(context, x, y, sliderWidth, ALPHA_HEIGHT, 0xFF0B0E14);\n        int markerX = x + Math.round((float) alpha(color) / 255.0f * (sliderWidth - 1));\n        UiChrome.fill(context, markerX - 1, y - 3, markerX + 2, y + ALPHA_HEIGHT + 3, 0xFFFFFFFF);\n        UiChrome.fill(context, markerX, y - 2, markerX + 1, y + ALPHA_HEIGHT + 2, 0xFF0B0E14);', '        int markerX = x + 1 + Math.round((float) alpha(color) / 255.0f * (sliderWidth - 3));\n        UiChrome.fill(context, markerX - 1, y, markerX + 2, y + ALPHA_HEIGHT, 0xFFFFFFFF);\n        UiChrome.fill(context, markerX, y + 1, markerX + 1, y + ALPHA_HEIGHT - 1, 0xFF0B0E14);');
      if (!code.includes('private final UiMotion.Hover[] slotHovers')) {
        code = code.replace(/(    private final ArrayList<[^\n]+ groupButtons = new ArrayList<>\(\);\n)/, '$1    private final UiMotion.Hover[] slotHovers = UiMotion.Hover.states(OverlayColorSlot.values().length);\n    private final UiMotion.Hover[] slotResetHovers = UiMotion.Hover.states(OverlayColorSlot.values().length);\n    private final UiMotion.Hover[] presetHovers = UiMotion.Hover.states(OverlayPreset.values().length);\n    private final UiMotion.Hover[] resetHovers = UiMotion.Hover.states(ResetAction.values().length);\n    private final UiMotion.Hover[] dialogHovers = UiMotion.Hover.states(2);\n');
      }
      code = code.replace('int groupX = MARGIN;', 'int groupX = stackedLayout() ? MARGIN : slotRowX();');
      code = code.replace('            } else {\n                x = MARGIN;', '            } else {\n                x = slotRowX();');
      code = code.replace('return leftWidth() - MARGIN;', 'return slotRowWidth();');
      code = code.replace('int rowColor = selected ? UiStyle.current().raised : hovered ? 0xAA35403F : 0x00000000;', 'float rowHover = slotHovers[slot.ordinal()].value(hovered);\n            int rowColor = selected ? UiStyle.current().raised : UiChrome.blend(0x0035403F, 0xAA35403F, rowHover);');
      code = code.replace('int resetFill = resetHovered ? 0xFF3A4845 : UiStyle.current().raised;', 'int resetFill = UiChrome.blend(UiStyle.current().raised, UiStyle.current().accent, slotResetHovers[slot.ordinal()].value(resetHovered) * 0.22f);');
      code = code.replace('UiChrome.fill(context, x + 1, y, x + width - 1, y + resetMenuItemHeight(), hovered ? 0xFF22324A : 0x00141B25);', 'UiChrome.fillRounded(context, x + 4, y + 1, width - 8, resetMenuItemHeight() - 2, UiChrome.blend(0x0035403F, 0xAA35403F, resetHovers[action.ordinal()].value(hovered)));');
      code = code.replace('UiChrome.fill(context, x + 1, y, x + width - 1, y + templateMenuItemHeight(), hovered ? 0xFF22324A : 0x00141B25);', 'UiChrome.fillRounded(context, x + 4, y + 1, width - 8, templateMenuItemHeight() - 2, UiChrome.blend(0x0035403F, 0xAA35403F, presetHovers[preset.ordinal()].value(hovered)));');
      code = code.replace('UiChrome.fillRounded(context, x, y, width, height, hovered ? UiStyle.current().raised : fill);\n        drawOutline(context, x, y, width, height, hovered ? 0xFF8AB4FF : 0xFF2B3443);', 'float progress = dialogHovers[x == confirmApplyX() ? 0 : 1].value(hovered);\n        UiChrome.fillRounded(context, x, y, width, height, UiChrome.blend(fill, UiStyle.current().accent, progress * 0.22f));');
      code = code.replace(/    private static final int SPECTRUM_WIDTH = \d+;\n/g, '');
      if (!code.includes('private static final int COLOR_GRADIENT_STEP')) code = code.replace('    private static final int ALPHA_HEIGHT = 14;', '    private static final int ALPHA_HEIGHT = 14;\n    private static final int COLOR_GRADIENT_STEP = 4;');
      code = code.replace('UiChrome.fill(context, swatchX, y, swatchX + 6, y + 16, profile.color(slots[i]));\n            drawOutline(context, swatchX, y, 6, 16, 0xFF0B0E14);', 'UiChrome.fillRounded(context, swatchX, y, 6, 16, profile.color(slots[i]));');
      if (!code.includes('int bodyMouseX =')) {
        code = code.replace(/(protected void drawScreen\([^\n]+\) \{\n)/, '$1        int bodyMouseX = popupBlocksWidgetHover() ? Integer.MIN_VALUE : mouseX;\n        int bodyMouseY = popupBlocksWidgetHover() ? Integer.MIN_VALUE : mouseY;\n');
        for (const draw of ['drawSlotList', 'drawChannelSliders', 'drawLineWidthSlider', 'drawGlobalLineWidthSlider']) code = code.replaceAll(`${draw}(context, mouseX, mouseY);`, `${draw}(context, bodyMouseX, bodyMouseY);`);
      }
      code = code.replace('return super.keyPressed(input);', 'return popupBlocksWidgetHover() || super.keyPressed(input);');
      code = code.replace(/(public boolean keyPressed\([^\n]+\) \{\n)(?!        if \(isClosing)/, '$1        if (isClosing()) return true;\n');
      code = code.replace(/(public boolean mouseScrolled\([^\n]+\) \{\n        if \(isClosing\(\)\) return true;\n)(?!        if \(popupBlocksWidgetHover)/, '$1        if (popupBlocksWidgetHover()) return true;\n');
      code = code.replace(/((?:template|reset)MenuOpen = !(?:template|reset)MenuOpen;\n(\s+)(?:reset|template)MenuOpen = false;\n)(?!\s*setFocused\(null\);)/g, '$1$2setFocused(null);\n');
      code = code.replace('            context.enableScissor(0, topBarHeight(), width, height);\n            try {\n            } finally {\n                context.disableScissor();\n            }\n', '');
      for (const height of ['resetMenuHeight()', 'templateMenuHeight()']) {
        code = code.replace(`UiChrome.fill(context, x, y, x + width, y + ${height}, UiStyle.current().surface);`, `UiChrome.popup(context, x, y, width, ${height}, UiStyle.current());`);
      }
      code = code.replace('UiChrome.fill(context, x, y, x + dialogWidth, y + dialogHeight, UiStyle.current().surface);', 'UiChrome.popup(context, x, y, dialogWidth, dialogHeight, UiStyle.current());');
      code = code.replace('UiChrome.fill(context, x, y, x + width, y + height, hovered ? 0xFF27364C : fill);', 'UiChrome.fillRounded(context, x, y, width, height, hovered ? UiStyle.current().raised : fill);');
    }
    await fs.writeFile(file, code.replace(/[\t ]+$/gm, ''));
  }
  const client = path.join(base, 'java/org/marshal/weaddon_lightposition/client/WorldEditPointOverlayClient.java');
  let code = await fs.readFile(client, 'utf8');
  code = code.replace(/(            reducedMotion = false;\r?\n)        uiBlur = false;\r?\n        UiStyle.setBlur\(false\);/, '$1            uiBlur = false;\n            UiStyle.setBlur(false);');
  await fs.writeFile(client, code);
  const resources = path.join(base, 'resources/assets/worldedit_selection_overlay');
  for (const [oldName, newName] of [['font/Manrope-OFL.txt', 'font/manrope-ofl.txt'], ['textures/gui/ui/Lucide-LICENSE.txt', 'textures/gui/ui/lucide-license.txt']]) {
    try { await fs.rename(path.join(resources, oldName), path.join(resources, newName)); }
    catch (error) { if (error.code !== 'ENOENT') throw error; }
  }
  const preview = path.join(base, 'java/org/marshal/weaddon_lightposition/client/screen/OverlaySelectionPreviewRenderer.java');
  let previewCode = await fs.readFile(preview, 'utf8');
  const from = previewCode.indexOf('        if (!PROJECTION.matches(');
  const to = previewCode.indexOf('        }', previewCode.indexOf('PROJECTION.update(', from));
  if (from >= 0 && to >= 0) {
    const block = previewCode.slice(from, to);
    if (/\n        Bounds bounds/.test(block)) previewCode = previewCode.slice(0, from) + block.split('\n').map((line, index) => index > 0 && line ? '    ' + line : line).join('\n') + previewCode.slice(to);
  }
  await fs.writeFile(preview, previewCode);
}
console.log('Popup opacity, accessible palette labels, and config formatting synchronized.');
