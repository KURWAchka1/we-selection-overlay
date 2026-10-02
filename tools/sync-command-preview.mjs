import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { execFileSync } from 'node:child_process';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const canonical = 'WEaddon_fabric_26.2';
const java = 'src/main/java/org/marshal/weaddon_lightposition';
const targets = [
  { name: canonical, version: '26.2', neo: false },
  { name: 'WEaddon_lightposition', version: '26.1', neo: false },
  { name: 'WEaddon_neoforge_26.2', version: '26.2', neo: true },
  { name: 'WEaddon_neoforge_26.1', version: '26.1', neo: true },
  { name: 'WEaddon_neoforge_1.21.11', version: '1.21.11', neo: true },
  { name: 'WEaddon_fabric_1.21.11', version: 'yarn', neo: false }
];
const files = ['client/preview/CommandPreviewSnapshot.java', 'client/preview/CommandPreviewSource.java',
  'client/preview/WorldEditCommandPreviewSource.java', 'client/preview/CommandPreviewController.java',
  'client/preview/CommandPreviewRenderer.java', 'mixin/ChatCommandPreviewMixin.java'];
const copies = new Map();
for (const file of files) copies.set(file, await fs.readFile(path.join(root, canonical, java, file), 'utf8'));

function yarn(code) {
  const mappings = [
    ['net.minecraft.client.Minecraft', 'net.minecraft.client.MinecraftClient'],
    ['net.minecraft.client.KeyMapping', 'net.minecraft.client.option.KeyBinding'],
    ['net.minecraft.client.gui.GuiGraphicsExtractor', 'net.minecraft.client.gui.DrawContext'],
    ['net.minecraft.client.gui.screens.ChatScreen', 'net.minecraft.client.gui.screen.ChatScreen'],
    ['net.minecraft.client.gui.components.EditBox', 'net.minecraft.client.gui.widget.TextFieldWidget'],
    ['net.minecraft.client.input.KeyEvent', 'net.minecraft.client.input.KeyInput'],
    ['net.minecraft.network.chat.Component', 'net.minecraft.text.Text'],
    ['net.minecraft.client.server.IntegratedServer', 'net.minecraft.server.integrated.IntegratedServer'],
    ['net.minecraft.server.level.ServerPlayer', 'net.minecraft.server.network.ServerPlayerEntity'],
    ['net.minecraft.util.FormattedCharSequence', 'net.minecraft.text.OrderedText'],
    ['net.minecraft.locale.Language', 'net.minecraft.util.Language'],
    ['net.minecraft.core.BlockPos', 'net.minecraft.util.math.BlockPos'],
    ['net.minecraft.core.Direction', 'net.minecraft.util.math.Direction'],
    ['net.minecraft.world.level.block.state.BlockState', 'net.minecraft.block.BlockState'],
    ['com.mojang.blaze3d.vertex.PoseStack', 'net.minecraft.client.util.math.MatrixStack'],
    ['com.mojang.blaze3d.vertex.VertexConsumer', 'net.minecraft.client.render.VertexConsumer'],
    ['net.minecraft.client.renderer.block.dispatch.BlockStateModelPart', 'net.minecraft.client.render.model.BlockModelPart'],
    ['net.minecraft.client.resources.model.geometry.BakedQuad', 'net.minecraft.client.render.model.BakedQuad'],
    ['net.minecraft.client.renderer.rendertype.RenderTypes', 'net.minecraft.client.render.RenderLayers'],
    ['net.minecraft.client.renderer.texture.TextureAtlas', 'net.minecraft.client.texture.SpriteAtlasTexture'],
    ['net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY', 'net.minecraft.client.render.OverlayTexture.DEFAULT_UV'],
    ['net.minecraft.client.model.geom.builders.UVPair.unpackU', 'net.minecraft.client.util.math.Vector2f.getX'],
    ['net.minecraft.client.model.geom.builders.UVPair.unpackV', 'net.minecraft.client.util.math.Vector2f.getY'],
    ['net.minecraft.gizmos.GizmoStyle', 'net.minecraft.client.render.DrawStyle'],
    ['net.minecraft.gizmos.Gizmos', 'net.minecraft.world.debug.gizmo.GizmoDrawing'],
    ['net.minecraft.util.RandomSource', 'net.minecraft.util.math.random.Random'],
    ['net.minecraft.world.phys.AABB', 'net.minecraft.util.math.Box'],
    ['net.minecraft.world.phys.Vec3', 'net.minecraft.util.math.Vec3d']
  ];
  for (const [a, b] of mappings) code = code.replaceAll(a, b);
  for (const [a, b] of [['Minecraft', 'MinecraftClient'], ['KeyMapping', 'KeyBinding'], ['GuiGraphicsExtractor', 'DrawContext'],
    ['Component', 'Text'], ['KeyEvent', 'KeyInput'], ['ServerPlayer', 'ServerPlayerEntity'], ['EditBox', 'TextFieldWidget'],
    ['PoseStack', 'MatrixStack'], ['BlockStateModelPart', 'BlockModelPart'], ['RenderTypes', 'RenderLayers'],
    ['TextureAtlas', 'SpriteAtlasTexture'], ['GizmoStyle', 'DrawStyle'], ['Gizmos', 'GizmoDrawing'], ['RandomSource', 'Random'],
    ['AABB', 'Box'], ['Vec3', 'Vec3d']]) code = code.replace(new RegExp(`\\b${a}\\b`, 'g'), b);
  return code.replaceAll('client.gui.screen()', 'client.currentScreen').replaceAll('client.level', 'client.world')
    .replaceAll('getSingleplayerServer()', 'getServer()').replaceAll('getUUID()', 'getUuid()')
    .replaceAll('getPlayerList()', 'getPlayerManager()').replaceAll('getTickCount()', 'getTicks()')
    .replaceAll('nativePlayer.level().hasChunk(', 'nativePlayer.getEntityWorld().getChunkManager().isChunkLoaded(')
    .replaceAll('FabricAdapter.get().fromNativePlayer(', 'FabricAdapter.adaptPlayer(')
    .replaceAll('FabricAdapter.get().toNativeBlockState(', 'FabricAdapter.adapt(')
    .replaceAll('direction.getStepX()', 'direction.getOffsetX()').replaceAll('direction.getStepY()', 'direction.getOffsetY()').replaceAll('direction.getStepZ()', 'direction.getOffsetZ()')
    .replaceAll('normal.getStepX()', 'normal.getOffsetX()').replaceAll('normal.getStepY()', 'normal.getOffsetY()').replaceAll('normal.getStepZ()', 'normal.getOffsetZ()')
    .replaceAll('client.getWindow().getGuiScaledWidth()', 'client.getWindow().getScaledWidth()')
    .replaceAll('client.getWindow().getGuiScaledHeight()', 'client.getWindow().getScaledHeight()')
    .replaceAll('client.font', 'client.textRenderer').replaceAll('.width(', '.getWidth(')
    .replaceAll('client.textRenderer.split(', 'client.textRenderer.wrapLines(')
    .replaceAll('graphics.text(', 'graphics.drawText(').replaceAll('.consumeClick()', '.wasPressed()')
    .replaceAll('toggleKey.matches(key)', 'toggleKey.matchesKey(key)').replaceAll('input.getValue()', 'input.getText()')
    .replaceAll('onEdited', 'onChatFieldUpdate').replaceAll('protected TextFieldWidget input;', 'protected TextFieldWidget chatField;')
    .replaceAll('input.getText()', 'chatField.getText()').replaceAll('extractRenderState', 'render');
}

function legacyRenderer(code, isYarn) {
  // Legacy engines submit directly to their native shared translucent buffer.
  const start = code.indexOf('        context.submitNodeCollector().submitCustomGeometry');
  const end = code.indexOf('\n    }', start);
  const submit = isYarn
    ? `        var consumer = context.consumers().getBuffer(RenderTypes.entityTranslucentEmissive(TextureAtlas.LOCATION_BLOCKS));\n        draw(context.matrices().peek(), consumer, camera, distanceSqr, alpha, batch);`
    : `        var buffers = client.renderBuffers().bufferSource();\n        draw(context.last(), buffers.getBuffer(RenderTypes.entityTranslucentEmissive(TextureAtlas.LOCATION_BLOCKS)), camera, distanceSqr, alpha, batch);\n        buffers.endBatch(RenderTypes.entityTranslucentEmissive(TextureAtlas.LOCATION_BLOCKS));`;
  code = code.slice(0, start) + submit + code.slice(end);
  code = code.replace('client.getModelManager().getBlockStateModelSet().get(state).collectParts(RandomSource.create(42), parts);',
    isYarn ? 'client.getBlockRenderManager().getModel(state).addParts(RandomSource.create(42), parts);'
      : 'client.getBlockRenderer().getBlockModel(state).collectParts(RandomSource.create(42), parts);');
  code = code.replaceAll('client.getModelManager().getBlockStateModelSet().get(state)',
    isYarn ? 'client.getBlockRenderManager().getModel(state)' : 'client.getBlockRenderer().getBlockModel(state)');
  const tintStart = code.indexOf('        if (quad.materialInfo().isTinted())');
  const tintEnd = code.indexOf('\n        return new Quad', tintStart);
  code = code.slice(0, tintStart) + (isYarn
    ? '        if (quad.hasTint()) tint = client.getBlockColors().getColor(state, null, null, quad.tintIndex());'
    : '        if (quad.isTinted()) tint = client.getBlockColors().getColor(state, null, null, quad.tintIndex());') + code.slice(tintEnd);
  if (!isYarn) code = code.replaceAll('renderer.block.dispatch.BlockStateModelPart', 'renderer.block.model.BlockModelPart')
    .replaceAll('BlockStateModelPart', 'BlockModelPart').replaceAll('resources.model.geometry.BakedQuad', 'renderer.block.model.BakedQuad')
    .replaceAll('renderer.rendertype.RenderTypes', 'renderer.rendertype.RenderTypes');
  code = code.replaceAll('client.gameRenderer.mainCamera()', isYarn ? 'client.gameRenderer.getCamera()' : 'client.gameRenderer.getMainCamera()')
    .replaceAll('.position();', isYarn ? '.getCameraPos();' : '.position();')
    .replaceAll('camera.x()', 'camera.x').replaceAll('camera.y()', 'camera.y').replaceAll('camera.z()', 'camera.z')
    .replace('GizmoStyle.strokeAndFill(0xD0000000 | marker.color, 1.0f, fill), true)', 'GizmoStyle.strokeAndFill(0xD0000000 | marker.color, 1.0f, fill))');
  if (isYarn) code = code.replaceAll('q.baked.direction()', 'q.baked.face()').replaceAll('q.baked.position(v)', 'q.baked.getPosition(v)')
    .replaceAll('q.baked.packedUV(v)', 'q.baked.getTexcoords(v)').replaceAll('PoseStack.Pose', 'PoseStack.Entry')
    .replaceAll('getModelManager()', 'getBakedModelManager()');
  return code;
}

for (const target of targets) {
  for (const file of files) {
    let code = copies.get(file);
    if (target.version !== '26.2') code = code.replaceAll('client.gui.screen()', 'client.screen');
    if (target.version === '26.1') code = code.replaceAll('client.gameRenderer.mainCamera()', 'client.gameRenderer.getMainCamera()');
    if (target.neo) {
      code = code.replaceAll('com.sk89q.worldedit.fabric.FabricAdapter', 'com.sk89q.worldedit.neoforge.NeoForgeAdapter').replaceAll('FabricAdapter', 'NeoForgeAdapter');
      if (target.version === '1.21.11') code = code.replaceAll('NeoForgeAdapter.get().fromNativePlayer(', 'NeoForgeAdapter.adaptPlayer(')
        .replaceAll('NeoForgeAdapter.get().toNativeBlockState(', 'NeoForgeAdapter.adapt(');
      code = code.replace('import net.fabricmc.loader.api.FabricLoader;', 'import net.neoforged.fml.ModList;')
        .replaceAll('FabricLoader.getInstance().isModLoaded(', 'ModList.get().isLoaded(');
      if (file.endsWith('Controller.java')) code = code.replace('import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;', 'import com.mojang.blaze3d.vertex.PoseStack;')
        .replaceAll('LevelRenderContext context', 'PoseStack context');
      if (file.endsWith('Renderer.java')) {
        code = code.replace('import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;', '')
          .replaceAll('LevelRenderContext context', 'PoseStack context');
        if (target.version.startsWith('26.')) {
          code = code.replace('context.submitNodeCollector().submitCustomGeometry(context.poseStack(),', 'collector(client).submitCustomGeometry(context,')
            .replace('        prepare(client, snapshot);', '        if (collector(client) == null) return;\n        prepare(client, snapshot);');
          code = code.replace('    void clear() {', `    private java.lang.reflect.Field collectorField;\n    private boolean collectorResolved;\n    private net.minecraft.client.renderer.SubmitNodeCollector collector(Minecraft client) {\n        if (!collectorResolved) {\n            collectorResolved = true;\n            try { collectorField = net.minecraft.client.renderer.LevelRenderer.class.getDeclaredField("submitNodeStorage"); collectorField.setAccessible(true); }\n            catch (ReflectiveOperationException | RuntimeException ignored) { return null; }\n        }\n        if (collectorField == null) return null;\n        try { return (net.minecraft.client.renderer.SubmitNodeCollector) collectorField.get(client.levelRenderer); }\n        catch (IllegalAccessException ignored) { return null; }\n    }\n\n    void clear() {`);
        }
      }
    }
    if ((target.version === 'yarn' || target.version === '1.21.11') && file.endsWith('Renderer.java')) code = legacyRenderer(code, target.version === 'yarn');
    if (target.version === '1.21.11') code = code.replaceAll('GuiGraphicsExtractor', 'GuiGraphics').replaceAll('extractRenderState', 'render').replaceAll('graphics.text(', 'graphics.drawString(');
    if (target.version === 'yarn') {
      code = code.replace('net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext', 'net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext').replaceAll('LevelRenderContext', 'WorldRenderContext');
      code = yarn(code).replaceAll('client.screen', 'client.currentScreen').replaceAll('SpriteAtlasTexture.LOCATION_BLOCKS', 'SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE')
        .replaceAll('consumer.addVertex(', 'consumer.vertex(').replaceAll('.setColor(', '.color(').replaceAll('.setUv(', '.texture(')
        .replaceAll('.setOverlay(', '.overlay(').replaceAll('.setLight(', '.light(').replaceAll('.setNormal(', '.normal(')
        .replaceAll('marker.box.inflate(', 'marker.box.expand(').replaceAll('distanceToSqr(', 'squaredDistanceTo(')
        .replaceAll('GizmoDrawing.cuboid(', 'GizmoDrawing.box(').replaceAll('DrawStyle.strokeAndFill(', 'DrawStyle.filledAndStroked(')
        .replaceAll('.setAlwaysOnTop()', '.ignoreOcclusion()').replaceAll('net.minecraft.world.level.block.RenderShape.MODEL', 'net.minecraft.block.BlockRenderType.MODEL')
        .replaceAll('getRenderShape()', 'getRenderType()');
    }
    if (target.name !== canonical) { const out = path.join(root, target.name, java, file); await fs.mkdir(path.dirname(out), { recursive: true }); await fs.writeFile(out, code); }
  }
  const gradlePath = path.join(root, target.name, 'build.gradle');
  let gradle = await fs.readFile(gradlePath, 'utf8');
  if (!gradle.includes('../shared/command-preview/src/main/java')) gradle += "\nsourceSets.main.java.srcDir('../shared/command-preview/src/main/java')\n";
  await fs.writeFile(gradlePath, gradle);
  if (target.neo) {
    if (!gradle.includes('../shared/neoforge-cui/src/main/java')) {
      gradle += "\nsourceSets.main.java.srcDir('../shared/neoforge-cui/src/main/java')\n";
      await fs.writeFile(gradlePath, gradle);
    }
    const entryPath = path.join(root, target.name, java, 'client/WorldEditSelectionOverlayClient.java');
    let entry = await fs.readFile(entryPath, 'utf8');
    if (!entry.includes('RuntimeCuiBridge.initialize')) entry = entry.replace('        OVERLAY_CLIENT.initialize();',
      '        RuntimeCuiBridge.initialize(modEventBus);\n        OVERLAY_CLIENT.initialize();');
    await fs.writeFile(entryPath, entry);
  }
  const mainPath = path.join(root, target.name, java, 'client/WorldEditPointOverlayClient.java');
  let main = await fs.readFile(mainPath, 'utf8');
  if (!main.includes('commandPreviewController =')) {
    main = main.replace('import org.marshal.weaddon_lightposition.client.clipboard.ClipboardPreviewController;', 'import org.marshal.weaddon_lightposition.client.clipboard.ClipboardPreviewController;\nimport org.marshal.weaddon_lightposition.client.preview.CommandPreviewController;')
      .replace('private final ClipboardPreviewController clipboardPreviewController = new ClipboardPreviewController();', 'private final ClipboardPreviewController clipboardPreviewController = new ClipboardPreviewController();\n    private final CommandPreviewController commandPreviewController = CommandPreviewController.instance();')
      .replace('private ' + (target.version === 'yarn' ? 'KeyBinding' : 'KeyMapping') + ' deselectKey;', 'private ' + (target.version === 'yarn' ? 'KeyBinding' : 'KeyMapping') + ' deselectKey;\n    private ' + (target.version === 'yarn' ? 'KeyBinding' : 'KeyMapping') + ' commandPreviewKey;')
      .replace('        initSettings();', '        initSettings();\n        commandPreviewController.initialize(settingsFile.getParent());');
    const key = main.match(/        deselectKey = [\s\S]*?KEY_CATEGORY\s*\)\)?;/)?.[0];
    if (!key) throw new Error(`Missing key template: ${target.name}`);
    main = main.replace(key, key + '\n' + key.replace('deselectKey', 'commandPreviewKey').replace('overlay.deselect', 'overlay.command_preview').replace('GLFW_KEY_N', 'GLFW_KEY_F8') + '\n        commandPreviewController.setToggleKey(commandPreviewKey);');
    main = main.replace('        clipboardPreviewController.tick(client);', '        clipboardPreviewController.tick(client);\n        commandPreviewController.tick(client);')
      .replace('        clipboardPreviewController.renderBlocks(', '        commandPreviewController.renderBlocks(' + (target.neo ? 'event.getPoseStack());\n        clipboardPreviewController.renderBlocks(' : 'context);\n        clipboardPreviewController.renderBlocks('))
      .replace('            clipboardPreviewController.renderBounds();', '            clipboardPreviewController.renderBounds();\n            commandPreviewController.renderMarkers();')
      .replace('            receivedSelectionPackets++;', '            receivedSelectionPackets++;\n            commandPreviewController.selectionChanged();')
      .replace('    private void resetState() {', '    private void resetState() {\n        commandPreviewController.clearDraft();\n        commandPreviewController.invalidate();')
      .replace('    private void handleOpenSettingsKey(', '    public CommandPreviewController commandPreviewController() { return commandPreviewController; }\n\n    private void handleOpenSettingsKey(');
    if (target.neo) main = main.replace('        event.register(deselectKey);', '        event.register(deselectKey);\n        event.register(commandPreviewKey);');
  }
  main = main.replace('            commandPreviewController.invalidate();', '            commandPreviewController.selectionChanged();');
  main = main.replace(/(?<!if \(!commandPreviewController\.ownsPreviewChannel\(\)\) )clipboardPreviewController\.renderBlocks\(/g,
    'if (!commandPreviewController.ownsPreviewChannel()) clipboardPreviewController.renderBlocks(')
    .replace(/(?<!if \(!commandPreviewController\.ownsPreviewChannel\(\)\) )clipboardPreviewController\.renderBounds\(\);/g,
      'if (!commandPreviewController.ownsPreviewChannel()) clipboardPreviewController.renderBounds();');
  main = main.replace(/(?<!if \(!commandPreviewController\.ownsPreviewChannel\(\)\) )clipboardPreviewController\.tick\(client\);/g,
    'if (!commandPreviewController.ownsPreviewChannel()) clipboardPreviewController.tick(client);');
  if (target.neo) main = main.replace('import org.enginehub.worldeditcui.protocol.CUIPacketHandler;\n', '')
    .replace('        CUIPacketHandler.instance().registerClientboundHandler(this::onCuiPacket);', '        RuntimeCuiBridge.register(this::handleCuiPacket);')
    .replace(/    private void onCuiPacket\(CUIPacket payload, CUIPacketHandler.PacketContext context\) \{[\s\S]*?\n    }\n\n/, '')
    .replace('ClientPacketDistributor.sendToServer(new CUIPacket("v", CUIPacket.protocolVersion()));', 'RuntimeCuiBridge.sendHello();');
  await fs.writeFile(mainPath, main);
  const mixinsPath = path.join(root, target.name, 'src/main/resources/worldedit_selection_overlay.mixins.json');
  let mixins = await fs.readFile(mixinsPath, 'utf8');
  if (!mixins.includes('ChatCommandPreviewMixin')) mixins = mixins.replace('"client": [', '"client": [\n    "ChatCommandPreviewMixin",');
  await fs.writeFile(mixinsPath, mixins);
  for (const locale of ['en_us', 'ru_ru']) {
    const langPath = path.join(root, target.name, 'src/main/resources/assets/worldedit_selection_overlay/lang', `${locale}.json`);
    const additions = JSON.parse(await fs.readFile(path.join(root, 'shared/command-preview/lang', `${locale}.json`), 'utf8'));
    let source = await fs.readFile(langPath, 'utf8');
    let values = JSON.parse(source.replace(/^\uFEFF/, ''));
    if (process.argv.includes('--restore-lang-format')) {
      const baseline = execFileSync('git', ['show', `HEAD:${path.relative(root, langPath).replaceAll('\\', '/')}`], { cwd: root, encoding: 'utf8' });
      const old = JSON.parse(baseline.replace(/^\uFEFF/, ''));
      if (Object.keys(old).some(key => values[key] !== old[key])) throw new Error(`Existing translations changed: ${langPath}`);
      source = baseline; values = old;
    }
    const missing = [];
    for (const [key, value] of Object.entries(additions)) {
      if (!(key in values)) missing.push(`  ${JSON.stringify(key)}: ${JSON.stringify(value)}`);
      else if (values[key] !== value) throw new Error(`Update existing translation with an explicit patch: ${key}`);
    }
    if (missing.length) source = source.trimEnd().replace(/\s*}$/, ',\n' + missing.join(',\n') + '\n}') + '\n';
    JSON.parse(source.replace(/^\uFEFF/, ''));
    await fs.writeFile(langPath, source);
  }
}
