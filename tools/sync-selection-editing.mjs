import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { execFileSync } from 'node:child_process';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const java = 'src/main/java/org/marshal/weaddon_lightposition';
const canonical = 'WEaddon_fabric_26.2';
const targets = [
  [canonical, '26.2', false], ['WEaddon_lightposition', '26.1', false],
  ['WEaddon_neoforge_26.2', '26.2', true], ['WEaddon_neoforge_26.1', '26.1', true],
  ['WEaddon_neoforge_1.21.11', '1.21.11', true], ['WEaddon_fabric_1.21.11', 'yarn', false]
];
const files = ['client/selection/SelectionEditController.java', 'client/selection/SelectionEditRenderer.java',
  'client/selection/SelectionEditSource.java', 'client/selection/WorldEditSelectionEditSource.java',
  'client/screen/SelectionEditSettingsScreen.java', 'mixin/SelectionEditMouseMixin.java',
  'mixin/SelectionEditKeyboardMixin.java', 'mixin/SelectionEditMovementMixin.java', 'mixin/SelectionEditPlayerMixin.java'];
const copies = new Map();
for (const file of files) copies.set(file, await fs.readFile(path.join(root, canonical, java, file), 'utf8'));
const hud = `        net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.attachElementBefore(
                net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.CHAT,
                net.minecraft.resources.Identifier.fromNamespaceAndPath("worldedit_selection_overlay", "selection_edit"), (graphics, delta) -> renderHud(graphics));`;

function yarn(code) {
  for (const [a, b] of [
    ['net.minecraft.client.Minecraft', 'net.minecraft.client.MinecraftClient'], ['net.minecraft.client.KeyMapping', 'net.minecraft.client.option.KeyBinding'],
    ['net.minecraft.client.gui.GuiGraphicsExtractor', 'net.minecraft.client.gui.DrawContext'], ['net.minecraft.network.chat.Component', 'net.minecraft.text.Text'],
    ['net.minecraft.client.gui.components.Button', 'net.minecraft.client.gui.widget.ButtonWidget'], ['net.minecraft.client.gui.screens.Screen', 'net.minecraft.client.gui.screen.Screen'],
    ['net.minecraft.client.gui.components.AbstractSliderButton', 'net.minecraft.client.gui.widget.SliderWidget'],
    ['net.minecraft.client.input.KeyEvent', 'net.minecraft.client.input.KeyInput'], ['net.minecraft.client.input.MouseButtonInfo', 'net.minecraft.client.input.MouseInput'],
    ['net.minecraft.client.MouseHandler', 'net.minecraft.client.Mouse'], ['net.minecraft.client.KeyboardHandler', 'net.minecraft.client.Keyboard'],
    ['net.minecraft.client.player.KeyboardInput', 'net.minecraft.client.input.KeyboardInput'], ['net.minecraft.client.player.ClientInput', 'net.minecraft.client.input.Input'],
    ['net.minecraft.client.player.LocalPlayer', 'net.minecraft.client.network.ClientPlayerEntity'], ['net.minecraft.world.entity.MoverType', 'net.minecraft.entity.MovementType'],
    ['net.minecraft.world.entity.player.Input', 'net.minecraft.util.PlayerInput'], ['net.minecraft.world.phys.Vec2', 'net.minecraft.util.math.Vec2f'],
    ['net.minecraft.world.phys.Vec3', 'net.minecraft.util.math.Vec3d'], ['net.minecraft.world.phys.AABB', 'net.minecraft.util.math.Box'],
    ['net.minecraft.gizmos.Gizmos', 'net.minecraft.world.debug.gizmo.GizmoDrawing'], ['net.minecraft.gizmos.GizmoStyle', 'net.minecraft.client.render.DrawStyle'],
    ['net.minecraft.client.server.IntegratedServer', 'net.minecraft.server.integrated.IntegratedServer'],
    ['net.minecraft.resources.Identifier', 'net.minecraft.util.Identifier'], ['com.mojang.blaze3d.platform.InputConstants', 'net.minecraft.client.util.InputUtil']
  ]) code = code.replaceAll(a, b);
  for (const [a, b] of [['Minecraft', 'MinecraftClient'], ['KeyMapping', 'KeyBinding'], ['GuiGraphicsExtractor', 'DrawContext'], ['Component', 'Text'],
    ['Button', 'ButtonWidget'], ['KeyEvent', 'KeyInput'], ['MouseButtonInfo', 'MouseInput'], ['MouseHandler', 'Mouse'], ['KeyboardHandler', 'Keyboard'],
    ['ClientInput', 'Input'], ['InputConstants', 'InputUtil'], ['Vec3', 'Vec3d'], ['Vec2', 'Vec2f'], ['AABB', 'Box'], ['Gizmos', 'GizmoDrawing'], ['GizmoStyle', 'DrawStyle']]) {
    code = code.replace(new RegExp(`\\b${a}\\b`, 'g'), b);
  }
  code = code.replaceAll('LocalPlayer.class', 'ClientPlayerEntity.class').replaceAll('MoverType type', 'MovementType type').replaceAll('AbstractSliderButton', 'SliderWidget');
  return code.replaceAll('client.level', 'client.world').replaceAll('client.gui.screen()', 'client.currentScreen').replace(/(?<![.\w])client\.screen\b/g, 'client.currentScreen')
    .replaceAll('MinecraftClient.getInstance().gui.setOverlayMessage(', 'MinecraftClient.getInstance().inGameHud.setOverlayMessage(')
    .replaceAll('getSingleplayerServer()', 'getServer()').replaceAll('getUUID()', 'getUuid()').replaceAll('getPlayerList()', 'getPlayerManager()')
    .replaceAll('nativePlayer.level()', 'nativePlayer.getEntityWorld()').replaceAll('FabricAdapter.get().fromNativePlayer(', 'FabricAdapter.adaptPlayer(')
    .replaceAll('.isDown()', '.isPressed()').replaceAll('.setDown(', '.setPressed(').replaceAll('.consumeClick()', '.wasPressed()')
    .replaceAll('.getTranslatedKeyMessage()', '.getBoundKeyLocalizedText()').replaceAll('.setKey(', '.setBoundKey(').replaceAll('KeyBinding.resetMapping()', 'KeyBinding.updateKeysByCode()')
    .replaceAll('InputUtil.getKey(event)', 'InputUtil.fromKeyCode(event)').replaceAll('InputUtil.UNKNOWN', 'InputUtil.UNKNOWN_KEY')
    .replaceAll('client.options.save()', 'client.options.write()').replaceAll('MinecraftClient.getInstance().options.save()', 'MinecraftClient.getInstance().options.write()')
    .replaceAll('client.options.getCameraType()', 'client.options.getPerspective()')
    .replaceAll('client.mouseHandler', 'client.mouse').replaceAll('.releaseMouse()', '.unlockCursor()').replaceAll('.grabMouse()', '.lockCursor()')
    .replaceAll('client.options.keyAttack', 'client.options.attackKey').replaceAll('client.options.keyUse', 'client.options.useKey').replaceAll('client.options.keyPickItem', 'client.options.pickItemKey')
    .replaceAll('client.player.setDeltaMovement(', 'client.player.setVelocity(').replaceAll('client.player.displayClientMessage(', 'client.player.sendMessage(')
    .replaceAll('client.isWindowActive()', 'client.isWindowFocused()').replaceAll('getWindow().handle()', 'getWindow().getHandle()')
    .replaceAll('getWindow().getScreenWidth()', 'getWindow().getWidth()').replaceAll('getWindow().getScreenHeight()', 'getWindow().getHeight()')
    .replaceAll('getWindow().getGuiScaledWidth()', 'getWindow().getScaledWidth()').replaceAll('getWindow().getGuiScaledHeight()', 'getWindow().getScaledHeight()')
    .replaceAll('client.gameRenderer.mainCamera()', 'client.gameRenderer.getCamera()').replaceAll('camera.position()', 'camera.getCameraPos()').replaceAll('camera.rotation()', 'camera.getRotation()')
    .replaceAll('GizmoDrawing.cuboid(', 'GizmoDrawing.box(').replaceAll('DrawStyle.strokeAndFill(', 'DrawStyle.filledAndStroked(').replaceAll('.setAlwaysOnTop()', '.ignoreOcclusion()')
    .replaceAll('GizmoDrawing.rect(', 'GizmoDrawing.quad(')
    .replaceAll('DrawStyle.stroke(', 'DrawStyle.stroked(')
    .replaceAll('point(camera.getCameraPos())', 'point(camera.getCameraPos())').replaceAll('v.x()', 'v.x').replaceAll('v.y()', 'v.y').replaceAll('v.z()', 'v.z')
    .replaceAll('client.font', 'client.textRenderer').replaceAll('client.textRenderer.width(', 'client.textRenderer.getWidth(').replaceAll('client.textRenderer.split(', 'client.textRenderer.wrapLines(')
    .replaceAll('addRenderableWidget(', 'addDrawableChild(').replaceAll(').bounds(', ').dimensions(').replace(/^(\s*)\.bounds\(/gm, '$1.dimensions(')
    .replaceAll('minecraft != null', 'client != null').replaceAll('minecraft.setScreen(', 'client.setScreen(').replaceAll('minecraft.gui.setScreen(', 'client.setScreen(')
    .replaceAll('onClose()', 'close()').replaceAll('isPauseScreen()', 'shouldPause()')
    .replaceAll('extractWidgetRenderState', 'renderWidget')
    .replaceAll('method = "onButton"', 'method = "onMouseButton"').replaceAll('method = "onScroll"', 'method = "onMouseScroll"').replaceAll('method = "onMove"', 'method = "onCursorPos"')
    .replaceAll('method = "keyPress"', 'method = "onKey"').replaceAll('keyPresses = Input.EMPTY; moveVector = Vec2f.ZERO;', 'playerInput = PlayerInput.DEFAULT; movementVector = Vec2f.ZERO;')
    .replaceAll('Identifier.fromNamespaceAndPath(', 'Identifier.of(');
}

for (const [name, version, neo] of targets) {
  for (const file of files) {
    let code = copies.get(file);
    if (neo) code = code.replace(hud, '        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.RenderGuiEvent.Post event) -> renderHud(event.getGuiGraphics()));');
    if (version !== '26.2') code = code.replaceAll('client.gui.screen()', 'client.screen').replaceAll('minecraft.gui.setScreen(', 'minecraft.setScreen(')
      .replaceAll('Minecraft.getInstance().gui.hud.setOverlayMessage(', 'Minecraft.getInstance().gui.setOverlayMessage(')
      .replaceAll('client.gameRenderer.mainCamera()', 'client.gameRenderer.getMainCamera()');
    if (file.endsWith('Controller.java') && !code.includes('HudElementRegistry')) code = code.replace('        settings = directory.resolve(', (neo
      ? '        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.RenderGuiEvent.Post event) -> renderHud(event.getGuiGraphics()));'
      : hud) + '\n        settings = directory.resolve(');
    if (neo) code = code.replace('import net.fabricmc.loader.api.FabricLoader;', 'import net.neoforged.fml.ModList;')
      .replaceAll('FabricLoader.getInstance().isModLoaded(', 'ModList.get().isLoaded(')
      .replaceAll('com.sk89q.worldedit.fabric.FabricAdapter', 'com.sk89q.worldedit.neoforge.NeoForgeAdapter').replaceAll('FabricAdapter', 'NeoForgeAdapter');
    if (version === '1.21.11' || version === 'yarn') {
      code = code.replaceAll('camera.getFov()', '((org.marshal.weaddon_lightposition.mixin.SelectionEditFovAccessor) client.gameRenderer).weso$editFov(camera, 1.0f, true)')
        .replaceAll('GizmoStyle.stroke(tint(style.secondaryText, alpha * 0.28f), 1), true)', 'GizmoStyle.stroke(tint(style.secondaryText, alpha * 0.28f), 1))')
        .replaceAll('GizmoStyle.stroke(tint(style.secondaryText, alpha * 0.65f), 1), true)', 'GizmoStyle.stroke(tint(style.secondaryText, alpha * 0.65f), 1))');
      if (neo) code = code.replaceAll('NeoForgeAdapter.get().fromNativePlayer(', 'NeoForgeAdapter.adaptPlayer(');
    }
    if (version === '1.21.11') code = code.replaceAll('GuiGraphicsExtractor', 'GuiGraphics').replaceAll('extractWidgetRenderState', 'renderWidget');
    if (version === 'yarn') {
      code = yarn(code).replaceAll('client.gameRenderer.getMainCamera()', 'client.gameRenderer.getCamera()');
      // JOML vectors retain accessors; only native Vec3d has public coordinate fields.
      code = code.replace('point(Vector3f v) { return new Point(v.x, v.y, v.z); }', 'point(Vector3f v) { return new Point(v.x(), v.y(), v.z()); }');
    }
    const output = path.join(root, name, java, file);
    await fs.mkdir(path.dirname(output), { recursive: true }); await fs.writeFile(output, code);
  }
  const mixinsPath = path.join(root, name, 'src/main/resources/worldedit_selection_overlay.mixins.json');
  const mixins = JSON.parse((await fs.readFile(mixinsPath, 'utf8')).replace(/^\uFEFF/, ''));
  for (const type of ['Mouse', 'Keyboard', 'Movement', 'Player']) if (!mixins.client.includes(`SelectionEdit${type}Mixin`)) mixins.client.push(`SelectionEdit${type}Mixin`);
  if (version === '1.21.11' || version === 'yarn') {
    const accessor = `package org.marshal.weaddon_lightposition.mixin;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(GameRenderer.class)
public interface SelectionEditFovAccessor {
    @Invoker("getFov") float weso$editFov(Camera camera, float partialTick, boolean useSetting);
}
`;
    await fs.writeFile(path.join(root, name, java, 'mixin/SelectionEditFovAccessor.java'), version === 'yarn'
      ? accessor.replaceAll('net.minecraft.client.renderer.GameRenderer', 'net.minecraft.client.render.GameRenderer').replaceAll('net.minecraft.client.Camera', 'net.minecraft.client.render.Camera') : accessor);
    if (!mixins.client.includes('SelectionEditFovAccessor')) mixins.client.push('SelectionEditFovAccessor');
  }
  await fs.writeFile(mixinsPath, JSON.stringify(mixins, null, 2) + '\n');
  const gradlePath = path.join(root, name, 'build.gradle');
  let gradle = await fs.readFile(gradlePath, 'utf8');
  if (!gradle.includes('shared/selection-editing')) gradle += "\nsourceSets.main.java.srcDir('../shared/selection-editing/src/main/java')\n";
  await fs.writeFile(gradlePath, gradle);

  const mainPath = path.join(root, name, java, 'client/WorldEditPointOverlayClient.java');
  let main = await fs.readFile(mainPath, 'utf8');
  if (!main.includes('selectionEditController')) {
    const mc = version === 'yarn' ? 'MinecraftClient' : 'Minecraft', kb = version === 'yarn' ? 'KeyBinding' : 'KeyMapping';
    const inp = version === 'yarn' ? 'InputUtil' : 'InputConstants';
    main = main.replace('    private Path settingsFile;', `    private ${kb} selectionEditKey, selectionEditFastKey;
    private final org.marshal.weaddon_lightposition.client.selection.SelectionEditController selectionEditController = org.marshal.weaddon_lightposition.client.selection.SelectionEditController.instance();
    private Path settingsFile;`);
    main = main.replace('        commandPreviewController.initialize(settingsFile.getParent());',
      '        commandPreviewController.initialize(settingsFile.getParent());\n        selectionEditController.initialize(settingsFile.getParent());');
    const register = neo ? '' : version === 'yarn' ? 'KeyBindingHelper.registerKeyBinding(' : 'KeyMappingHelper.registerKeyMapping(';
    main = main.replace('        commandPreviewController.setToggleKey(commandPreviewKey);', `        commandPreviewController.setToggleKey(commandPreviewKey);
        selectionEditKey = ${register}new ${kb}("key.worldedit_selection_overlay.selection_edit", ${inp}.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, KEY_CATEGORY)${neo ? '' : ')'};
        selectionEditFastKey = ${register}new ${kb}("key.worldedit_selection_overlay.selection_edit_fast", ${inp}.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_SHIFT, KEY_CATEGORY)${neo ? '' : ')'};
        selectionEditController.keys(selectionEditKey, selectionEditFastKey);`);
    if (neo) main = main.replace('        event.register(commandPreviewKey);', '        event.register(commandPreviewKey);\n        event.register(selectionEditKey);\n        event.register(selectionEditFastKey);');
    main = main.replace('        commandPreviewController.tick(client);', '        selectionEditController.tick(client);\n        commandPreviewController.tick(client);');
    main = main.replaceAll('!commandPreviewController.ownsPreviewChannel()', '!commandPreviewController.ownsPreviewChannel() && !selectionEditController.active()');
    main = main.replace('        clearIsolatedBlocksIfSelectionChanged(client, region);\n        BlockIsolationController.updateRegion(client, region, false);', `        if (!selectionEditController.waitingForCui() || selectionEditController.acknowledgeIsolation(region)) {
            if (selectionEditController.deferIsolation()) return;
            clearIsolatedBlocksIfSelectionChanged(client, region);
            BlockIsolationController.updateRegion(client, region, false);
        }`);
    // The acknowledgement must update the stored region identity before the ordinary reset check.
    main = main.replace('if (!selectionEditController.waitingForCui() || selectionEditController.acknowledgeIsolation(region)) {',
      'boolean editedSelection = selectionEditController.acknowledgeIsolation(region);\n        if (editedSelection) updateIsolatedBlocksRegionSnapshot(region);\n        if (!selectionEditController.waitingForCui()) {');
    const scope = version === 'yarn' ? 'GizmoDrawing.CollectorScope' : 'Gizmos.TemporaryCollection';
    const collector = version === 'yarn' ? 'editClient.worldRenderer.startDrawingGizmos()' : version === '26.2'
      ? 'editClient.levelRenderer.collectPerFrameRenderThreadGizmos()' : 'editClient.levelRenderer.collectPerFrameGizmos()';
    main = main.replace(/(private void onRenderWorld\([^)]*\) \{)/, `$1
        ${mc} editClient = ${mc}.getInstance();
        if (selectionEditController.active() && editClient.${version === 'yarn' ? 'world' : 'level'} != null && editClient.player != null) {
            try (${scope} editScope = ${collector}) { selectionEditController.renderWorld(); }
        }`);
    main = main.replaceAll('if (isolationActive || (overlayEnabled && !selectionGridEnabled))',
      'if (selectionEditController.active()) {\n                    // The draft renderer owns the contour; saved overlay preferences remain untouched.\n                } else if (isolationActive || (overlayEnabled && !selectionGridEnabled))');
    main = main.replaceAll('boolean showDiagonals = !isolationActive &&', 'boolean showDiagonals = !selectionEditController.active() && !isolationActive &&');
    main = main.replace('    private void resetState() {', '    private void resetState() {\n        selectionEditController.cancel();');
  }
  if (version !== '26.2') main = main.replaceAll('editClient.levelRenderer.collectPerFrameRenderThreadGizmos()', 'editClient.levelRenderer.collectPerFrameGizmos()');
  main = main.replace('            if (selectionEditController.deferIsolation()) return;\n', '')
    .replace('                if (selectionEditController.active()) {\n                    // The draft renderer owns the contour; saved overlay preferences remain untouched.\n                } else if (isolationActive || (overlayEnabled && !selectionGridEnabled)) {',
      '                if (!selectionEditController.active() && (isolationActive || (overlayEnabled && !selectionGridEnabled))) {')
    .replace('                } else if (overlayEnabled) {\n                    region.render(renderMode);',
      '                } else if (!selectionEditController.active() && overlayEnabled) {\n                    region.render(renderMode);');
  await fs.writeFile(mainPath, main);
  for (const language of ['en_us', 'ru_ru']) {
    const additions = JSON.parse((await fs.readFile(path.join(root, 'shared/selection-editing/lang', language + '.json'), 'utf8')).replace(/^\uFEFF/, ''));
    const langPath = path.join(root, name, 'src/main/resources/assets/worldedit_selection_overlay/lang', language + '.json');
    const text = await fs.readFile(langPath, 'utf8');
    const existing = JSON.parse(text.replace(/^\uFEFF/, ''));
    const format = process.argv.includes('--restore-format')
      ? execFileSync('git', ['show', 'HEAD:' + path.relative(root, langPath).replaceAll('\\', '/')], { cwd: root, encoding: 'utf8' }) : text;
    const formats = new Map();
    for (const line of format.split(/\r?\n/)) {
      const match = line.match(/^(\s*)("(?:[^"\\]|\\.)*")(\s*:\s*)/);
      if (match) formats.set(JSON.parse(match[2]), match[1] + match[2] + match[3]);
    }
    const entries = Object.entries({ ...existing, ...additions });
    const lines = entries.map(([key, value], index) => (formats.get(key) ?? '  ' + JSON.stringify(key) + ': ')
      + JSON.stringify(value) + (index < entries.length - 1 ? ',' : ''));
    await fs.writeFile(langPath, (format.startsWith('\uFEFF') ? '\uFEFF' : '') + '{\n' + lines.join('\n') + '\n}\n');
  }
}
console.log('Selection editing adapters synchronized; compile and exercise every native runtime.');
