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
  'client/preview/CommandPreviewRenderer.java', 'client/preview/CommandPreviewGpuSection.java',
  'client/preview/CommandPreviewMask.java', 'client/preview/CommandPreviewTerrainState.java', 'mixin/ChatCommandPreviewMixin.java',
  'client/clipboard/ClipboardPreviewController.java', 'client/clipboard/ClipboardPreviewSource.java', 'client/clipboard/WorldEditClipboardPreviewSource.java'];
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
    ['net.minecraft.core.SectionPos', 'net.minecraft.util.math.ChunkSectionPos'],
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
    ['net.minecraft.world.phys.Vec3', 'net.minecraft.util.math.Vec3d'],
    ['net.minecraft.client.renderer.culling.Frustum', 'net.minecraft.client.render.Frustum']
  ];
  for (const [a, b] of mappings) code = code.replaceAll(a, b);
  for (const [a, b] of [['Minecraft', 'MinecraftClient'], ['KeyMapping', 'KeyBinding'], ['GuiGraphicsExtractor', 'DrawContext'],
    ['Component', 'Text'], ['KeyEvent', 'KeyInput'], ['ServerPlayer', 'ServerPlayerEntity'], ['EditBox', 'TextFieldWidget'],
    ['PoseStack', 'MatrixStack'], ['BlockStateModelPart', 'BlockModelPart'], ['RenderTypes', 'RenderLayers'],
    ['TextureAtlas', 'SpriteAtlasTexture'], ['GizmoStyle', 'DrawStyle'], ['Gizmos', 'GizmoDrawing'], ['RandomSource', 'Random'],
    ['AABB', 'Box'], ['Vec3', 'Vec3d'], ['SectionPos', 'ChunkSectionPos']]) code = code.replace(new RegExp(`\\b${a}\\b`, 'g'), b);
  return code.replaceAll('client.gui.screen()', 'client.currentScreen').replaceAll('client.level', 'client.world')
    .replaceAll('getSingleplayerServer()', 'getServer()').replaceAll('getUUID()', 'getUuid()')
    .replaceAll('getPlayerList()', 'getPlayerManager()').replaceAll('getTickCount()', 'getTicks()')
    .replaceAll('nativePlayer.level().hasChunk(', 'nativePlayer.getEntityWorld().getChunkManager().isChunkLoaded(')
    .replaceAll('FabricAdapter.get().fromNativePlayer(', 'FabricAdapter.adaptPlayer(')
    .replaceAll('FabricAdapter.get().toNativeBlockState(', 'FabricAdapter.adapt(')
    .replaceAll('direction.getStepX()', 'direction.getOffsetX()').replaceAll('direction.getStepY()', 'direction.getOffsetY()').replaceAll('direction.getStepZ()', 'direction.getOffsetZ()')
    .replaceAll('.canOcclude()', '.isOpaque()')
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
  code = code.replace('context.levelState().cameraRenderState.cullFrustum', isYarn ? 'CommandPreviewFrustum.current()' : 'frustum')
    .replaceAll('context.poseStack().last().pose()', isYarn ? 'context.matrices().peek().getPositionMatrix()' : 'context.last().pose()');
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
    .replaceAll('.getCamera().position()', isYarn ? '.getCamera().getCameraPos()' : '.getCamera().position()')
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
    if (target.version !== '26.2') code = code.replaceAll('client.levelExtractor', 'client.levelRenderer');
    if (target.version === '26.1') code = code.replaceAll('client.gameRenderer.mainCamera()', 'client.gameRenderer.getMainCamera()');
    if (target.neo) {
      if (file.endsWith('CommandPreviewController.java')) code = code.replace(
        'net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(client -> close());',
        'net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.GameShuttingDownEvent event) -> close());');
      if (file.endsWith('WorldEditCommandPreviewSource.java')) code = code.replace(
        /        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents.CHUNK_LOAD[\s\S]*?chunkChanged\(level, chunk.getPos\(\).x\(\), chunk.getPos\(\).z\(\), false\)\);/,
        `        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.level.ChunkEvent.Load event) -> {\n            if (event.getLevel() instanceof net.minecraft.client.multiplayer.ClientLevel)\n                chunkChanged(event.getLevel(), event.getChunk().getPos().x, event.getChunk().getPos().z, true);\n        });\n        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.level.ChunkEvent.Unload event) -> {\n            if (event.getLevel() instanceof net.minecraft.client.multiplayer.ClientLevel)\n                chunkChanged(event.getLevel(), event.getChunk().getPos().x, event.getChunk().getPos().z, false);\n        });`);
      code = code.replaceAll('com.sk89q.worldedit.fabric.FabricAdapter', 'com.sk89q.worldedit.neoforge.NeoForgeAdapter').replaceAll('FabricAdapter', 'NeoForgeAdapter');
      if (target.version === '1.21.11') code = code.replaceAll('NeoForgeAdapter.get().fromNativePlayer(', 'NeoForgeAdapter.adaptPlayer(')
        .replaceAll('NeoForgeAdapter.get().toNativeBlockState(', 'NeoForgeAdapter.adapt(');
      code = code.replace('import net.fabricmc.loader.api.FabricLoader;', 'import net.neoforged.fml.ModList;')
        .replaceAll('FabricLoader.getInstance().isModLoaded(', 'ModList.get().isLoaded(');
      if (file.endsWith('ClipboardPreviewController.java')) {
        code = code.replace('import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;', 'import com.mojang.blaze3d.vertex.PoseStack;')
          .replaceAll('LevelRenderContext context', 'PoseStack context');
        if (target.version.startsWith('26.')) code = code.replace('    public void renderBlocks(PoseStack context) {\n        if (!enabled) {\n            return;\n        }\n        renderer.render(context, source.snapshot());\n    }',
          `    private static final net.minecraft.util.context.ContextKey<ClipboardPreviewSnapshot> FRAME_KEY =
            new net.minecraft.util.context.ContextKey<>(net.minecraft.resources.Identifier.fromNamespaceAndPath("worldedit_selection_overlay", "clipboard_preview"));

    public void extractFrame(net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent event, boolean visible) {
        event.getRenderState().setRenderData(FRAME_KEY, enabled && visible ? source.snapshot() : null);
    }

    public void renderBlocks(net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent event) {
        ClipboardPreviewSnapshot snapshot = event.getLevelRenderState().getRenderData(FRAME_KEY);
        if (snapshot != null) renderer.render(event.getPoseStack(), snapshot, event.getSubmitNodeCollector(),
                event.getLevelRenderState().cameraRenderState.pos);
    }`);
      }
      else if (file.endsWith('Controller.java')) code = code.replace('import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;', 'import com.mojang.blaze3d.vertex.PoseStack;')
        .replaceAll('LevelRenderContext context', 'PoseStack context, net.minecraft.client.renderer.culling.Frustum frustum')
        .replace('renderer.render(context,', 'renderer.render(context, frustum,');
      if (file.endsWith('Renderer.java')) {
        code = code.replace('import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;', 'import com.mojang.blaze3d.vertex.PoseStack;')
          .replaceAll('LevelRenderContext context', 'PoseStack context, net.minecraft.client.renderer.culling.Frustum frustum')
          .replace(/^[ \t]*var frustum = context.levelState\(\).cameraRenderState.cullFrustum;\r?\n/m, '')
          .replaceAll('context.poseStack().last().pose()', 'context.last().pose()');
      }
    }
    if (file.endsWith('GpuSection.java') && target.version !== '26.2') {
      code = code.replaceAll('Optional.empty()', 'java.util.OptionalInt.empty()')
        .replaceAll('getPrimitiveTopology()', 'getVertexFormatMode()').replaceAll('getVertexFormatBinding(0)', 'getVertexFormat()')
        .replaceAll('client.gameRenderer.mainRenderTarget()', 'client.getMainRenderTarget()')
        .replaceAll('pass.setVertexBuffer(0, vertices.slice())', 'pass.setVertexBuffer(0, vertices)')
        .replaceAll('pass.drawIndexed(indexCount, 1, 0, 0, 0)', 'pass.drawIndexed(0, 0, indexCount, 1)')
        .replace(/var textures = SETUP.prepareTextures\([\s\S]*?;/, 'var textures = SETUP.getTextures();')
        .replace('for (var texture : textures) pass.bindTexture(texture.name(), texture.textureView(), texture.sampler());',
          'for (var texture : textures.entrySet()) pass.bindTexture(texture.getKey(), texture.getValue().textureView(), texture.getValue().sampler());');
    }
    if ((target.version === 'yarn' || target.version === '1.21.11') && file.endsWith('Renderer.java')) code = legacyRenderer(code, target.version === 'yarn');
    if (target.version === '1.21.11') code = code.replaceAll('GuiGraphicsExtractor', 'GuiGraphics').replaceAll('extractRenderState', 'render').replaceAll('graphics.text(', 'graphics.drawString(');
    if (target.version === '1.21.11' || target.version === 'yarn') code = code.replaceAll('getPos().x()', 'getPos().x').replaceAll('getPos().z()', 'getPos().z');
    else if (target.neo) code = code.replaceAll('getPos().x,', 'getPos().x(),').replaceAll('getPos().z,', 'getPos().z(),');
    if (target.version === 'yarn') {
      code = code.replace('net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext', 'net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext').replaceAll('LevelRenderContext', 'WorldRenderContext');
      code = yarn(code).replaceAll('client.screen', 'client.currentScreen').replaceAll('SpriteAtlasTexture.LOCATION_BLOCKS', 'SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE')
        .replaceAll('consumer.addVertex(', 'consumer.vertex(').replaceAll('.setColor(', '.color(').replaceAll('.setUv(', '.texture(')
        .replaceAll('.setOverlay(', '.overlay(').replaceAll('.setLight(', '.light(').replaceAll('.setNormal(', '.normal(')
        .replaceAll('marker.box.inflate(', 'marker.box.expand(').replaceAll('distanceToSqr(', 'squaredDistanceTo(')
        .replaceAll('GizmoDrawing.cuboid(', 'GizmoDrawing.box(').replaceAll('DrawStyle.strokeAndFill(', 'DrawStyle.filledAndStroked(')
        .replaceAll('.setAlwaysOnTop()', '.ignoreOcclusion()').replaceAll('net.minecraft.world.level.block.RenderShape.MODEL', 'net.minecraft.block.BlockRenderType.MODEL')
        .replaceAll('getRenderShape()', 'getRenderType()');
      code = code.replaceAll('client.levelRenderer', 'client.worldRenderer')
        .replaceAll('setSectionRangeDirty(', 'scheduleChunkRenders(')
        .replaceAll('ChunkSectionPos.x(', 'ChunkSectionPos.unpackX(')
        .replaceAll('ChunkSectionPos.y(', 'ChunkSectionPos.unpackY(')
        .replaceAll('ChunkSectionPos.z(', 'ChunkSectionPos.unpackZ(');
      code = code.replaceAll('Optional.empty()', 'java.util.OptionalInt.empty()')
        .replaceAll('client.player.blockPosition()', 'client.player.getBlockPos()')
        .replaceAll('client.options.getEffectiveRenderDistance()', 'client.options.getClampedViewDistance()')
        .replaceAll('client.world.getChunkSource()', 'client.world.getChunkManager()')
        .replaceAll('net.minecraft.world.level.chunk.status.ChunkStatus', 'net.minecraft.world.chunk.ChunkStatus')
        .replaceAll('chunk.getPos()', 'chunk.getPos()');
      if (file.endsWith('GpuSection.java')) {
        for (const [a, b] of [
          ['com.mojang.blaze3d.vertex.BufferBuilder', 'net.minecraft.client.render.BufferBuilder'],
          ['com.mojang.blaze3d.vertex.ByteBufferBuilder', 'net.minecraft.client.util.BufferAllocator'],
          ['com.mojang.blaze3d.vertex.MeshData', 'net.minecraft.client.render.BuiltBuffer'],
          ['com.mojang.blaze3d.vertex.VertexSorting', 'com.mojang.blaze3d.systems.VertexSorter'],
          ['net.minecraft.client.renderer.RenderPipelines', 'net.minecraft.client.gl.RenderPipelines'],
          ['net.minecraft.client.renderer.rendertype.RenderSetup', 'net.minecraft.client.render.RenderSetup'],
          ['ByteBufferBuilder', 'BufferAllocator'], ['MeshData', 'BuiltBuffer'], ['VertexSorting', 'VertexSorter'],
          ['.withTexture(', '.texture('], ['.createRenderSetup()', '.build()'], ['builder.build()', 'builder.endNullable()'],
          ['data.drawState()', 'data.getDrawParameters()'], ['data.vertexBuffer()', 'data.getBuffer()'],
          ['data.indexBuffer()', 'data.getSortedBuffer()'], ['.buildSortedIndexBuffer(', '.sortAndStore('],
          ['data.byteBuffer()', 'data.getBuffer()'], ['getVertexFormatMode()', 'getVertexFormatMode()'],
          ['getDynamicUniforms().writeTransform(', 'getDynamicUniforms().write('], ['getMainRenderTarget()', 'getFramebuffer()'],
          ['getColorTextureView()', 'getColorAttachmentView()'], ['getDepthTextureView()', 'getDepthAttachmentView()'],
          ['SETUP.getTextures()', 'SETUP.resolveTextures()'], ['camera.x()', 'camera.x'], ['camera.y()', 'camera.y'], ['camera.z()', 'camera.z']
        ]) code = code.replaceAll(a, b);
      }
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
    if (!entry.includes('OVERLAY_CLIENT::onRenderCommandPreview')) entry = entry.replace('        OVERLAY_CLIENT.initialize();',
      '        OVERLAY_CLIENT.initialize();\n        NeoForge.EVENT_BUS.addListener(OVERLAY_CLIENT::onRenderCommandPreview);');
    await fs.writeFile(entryPath, entry);
  }
  const mainPath = path.join(root, target.name, java, 'client/WorldEditPointOverlayClient.java');
  const isolationPath = path.join(root, target.name, java, 'client/BlockIsolationController.java');
  let isolation = await fs.readFile(isolationPath, 'utf8');
  {
    for (const method of ['shouldHideBlock', 'shouldHideFluid', 'shouldForceFullbright', 'shouldForceFullbrightFluid', 'shouldDrawFaceAgainstHiddenNeighbor']) {
      const start = isolation.indexOf(`public static boolean ${method}(`);
      const end = isolation.indexOf('\n    public static ', start + 20);
      if (isolation.slice(start, end >= 0 ? end : isolation.length).includes('CommandPreviewTerrainState')) continue;
      const insert = method === 'shouldDrawFaceAgainstHiddenNeighbor' && ['yarn', '1.21.11'].includes(target.version)
        ? isolation.indexOf('        BlockPos currentPos =', start)
        : isolation.indexOf('        IsolationSnapshot current = snapshot;', start);
      if (start < 0 || insert < 0) throw new Error(`Missing isolation integration: ${target.name} ${method}`);
      const neighbor = target.version === 'yarn' ? 'neighborPos.offset(direction.getOpposite())' : 'neighborPos.relative(direction.getOpposite())';
      const snippet = method === 'shouldDrawFaceAgainstHiddenNeighbor'
        ? `        if (org.marshal.weaddon_lightposition.client.preview.CommandPreviewTerrainState.hides(neighborPos))\n            return !shouldHideBlock(state, ${neighbor});\n\n`
        : `        if (org.marshal.weaddon_lightposition.client.preview.CommandPreviewTerrainState.hides(pos)) return ${method.startsWith('shouldForce') ? 'false' : 'true'};\n\n`;
      isolation = isolation.slice(0, insert) + snippet + isolation.slice(insert);
    }
    await fs.writeFile(isolationPath, isolation);
  }
  let main = await fs.readFile(mainPath, 'utf8');
  if (!main.includes('commandPreviewController.bindClipboardPreview')) main = main.replace('        clipboardPreviewController.initialize();',
    '        clipboardPreviewController.initialize();\n        commandPreviewController.bindClipboardPreview(clipboardPreviewController);');
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
  if (!main.includes('onRenderCommandPreview')) {
    main = main.replace(/        commandPreviewController.renderBlocks\([^\n]*\);\r?\n/, '');
    if (target.neo) {
      const stage = 'AfterTranslucentBlocks';
      main = main.replace('    public void onClientSystemMessage(', `    public void onRenderCommandPreview(RenderLevelStageEvent.${stage} event) {\n        commandPreviewController.renderBlocks(event.getPoseStack(), event.getFrustum());\n    }\n\n    public void onClientSystemMessage(`);
    } else {
      const events = target.version === 'yarn' ? 'WorldRenderEvents' : 'LevelRenderEvents';
      const stage = target.version === 'yarn' ? 'END_MAIN' : 'AFTER_TRANSLUCENT_TERRAIN';
      const context = target.version === 'yarn' ? 'WorldRenderContext' : 'LevelRenderContext';
      main = main.replace(`${events}.${target.version === 'yarn' ? 'AFTER_ENTITIES' : 'COLLECT_SUBMITS'}.register(this::onRenderClipboardPreview);`,
        `${events}.${target.version === 'yarn' ? 'AFTER_ENTITIES' : 'COLLECT_SUBMITS'}.register(this::onRenderClipboardPreview);\n        ${events}.${stage}.register(this::onRenderCommandPreview);`);
      main = main.replace('    private void onRenderClipboardPreview(', `    private void onRenderCommandPreview(${context} context) {\n        commandPreviewController.renderBlocks(context);\n    }\n\n    private void onRenderClipboardPreview(`);
    }
  }
  if (target.neo && target.version.startsWith('26.')) main = main.replace('event.getPoseStack(), event.getFrustum()', 'event.getPoseStack(), event.getLevelRenderState().cameraRenderState.cullFrustum');
  if (target.neo && target.version === '1.21.11') main = main.replace('event.getPoseStack(), event.getFrustum()', 'event.getPoseStack(), org.marshal.weaddon_lightposition.client.preview.CommandPreviewFrustum.current()');
  if (target.version === 'yarn') main = main.replace('WorldRenderEvents.AFTER_TRANSLUCENT.register(this::onRenderCommandPreview);', 'WorldRenderEvents.END_MAIN.register(this::onRenderCommandPreview);');
  main = main.replaceAll('if (!commandPreviewController.ownsPreviewChannel() && !selectionEditController.active()) if (!commandPreviewController.ownsPreviewChannel())',
    'if (!commandPreviewController.ownsPreviewChannel() && !selectionEditController.active())');
  main = main.replace(/^([ \t]*)clipboardPreviewController\.(renderBlocks|renderBounds|tick)\(/gm,
    '$1if (!commandPreviewController.ownsPreviewChannel()) clipboardPreviewController.$2(');
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
