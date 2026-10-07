import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const base = path.join(root, 'tools/command-preview-test');
const fabric = await fs.readFile(path.join(base, 'java/org/marshal/wesotest/CommandGameTest.java'), 'utf8');
const neo = await fs.readFile(path.join(base, 'neo-java/org/marshal/wesotest/NeoCommandTest.java'), 'utf8');
const editing = await fs.readFile(path.join(base, 'selection-java/org/marshal/wesotest/SelectionEditingChecks.java'), 'utf8');
for (const loader of ['fabric', 'neo']) for (const family of ['1.21.11', '26.1', '26.2']) {
  let source = loader === 'fabric' ? fabric : neo;
  source = source.replaceAll('runtime.mc26_2', 'runtime.' + ({'1.21.11':'mc1_21_11', '26.1':'mc26_1', '26.2':'mc26_2'}[family]));
  if (family !== '26.2') source = source.replaceAll('client.gui.setScreen(', 'client.setScreen(').replaceAll('client.gui.screen()', 'client.screen');
  if (loader === 'neo' && family === '26.1') source = source.replaceAll('client.gameRenderer.mainRenderTarget()', 'client.getMainRenderTarget()')
    .replaceAll('server.getWorldData().setAllowCommands(true)', 'server.getPlayerList().setAllowCommandsForAllPlayers(true)');
  if (loader === 'fabric' && family === '26.1') source = source.replaceAll('world.getConnection()', 'world.getClientLevel()');
  if (loader === 'neo' && family === '1.21.11') source = source.replaceAll('NeoForgeAdapter.get().fromNativePlayer(', 'NeoForgeAdapter.adaptPlayer(')
    .replaceAll('client.gameRenderer.mainRenderTarget()', 'client.getMainRenderTarget()')
    .replaceAll('client.resizeGui()', 'client.resizeDisplay()')
    .replaceAll('server.getWorldData().setAllowCommands(true)', 'server.getPlayerList().setAllowCommandsForAllPlayers(true)');
  if (loader === 'fabric' && family === '1.21.11') source = source.replaceAll('net.minecraft.client.gui.screens.ChatScreen', 'net.minecraft.client.gui.screen.ChatScreen')
    .replaceAll('net.minecraft.client.input.KeyEvent', 'net.minecraft.client.input.KeyInput').replaceAll('KeyEvent(', 'KeyInput(')
    .replaceAll('client.screen', 'client.currentScreen').replaceAll('client.getSingleplayerServer()', 'client.getServer()')
    .replaceAll('client.options.renderDistance()', 'client.options.getViewDistance()').replaceAll('client.options.guiScale()', 'client.options.getGuiScale()')
    .replaceAll('client.options.framerateLimit()', 'client.options.getMaxFps()').replaceAll('client.resizeGui()', 'client.onResolutionChanged()')
    .replaceAll('settings.setAllowCommands(', 'settings.setCheatsEnabled(').replaceAll('FabricAdapter.get().fromNativePlayer(', 'FabricAdapter.adaptPlayer(')
    .replaceAll('server.getPlayerList().getPlayers()', 'server.getPlayerManager().getPlayerList()');
  if (loader === 'fabric' && family === '1.21.11') source = source.replaceAll('net.minecraft.client.KeyMapping.click', 'net.minecraft.client.option.KeyBinding.onKeyPressed')
    .replaceAll('com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate', 'net.minecraft.client.util.InputUtil.Type.KEYSYM.createFromCode')
    .replaceAll('field(client.currentScreen, "input")', 'field(client.currentScreen, "chatField")').replaceAll('"setValue"', '"setText"')
    .replaceAll('getViewDistance().set(', 'getViewDistance().setValue(').replaceAll('getGuiScale().set(', 'getGuiScale().setValue(')
    .replaceAll('getMaxFps().set(', 'getMaxFps().setValue(').replaceAll('world.getConnection()', 'world.getClientWorld()');
  if (loader === 'fabric' && family === '1.21.11') source = source.replaceAll('net.minecraft.client.gui.components.EditBox', 'net.minecraft.client.gui.widget.TextFieldWidget')
    .replaceAll('input.setValue(', 'input.setText(')
    .replaceAll('chatInput((ChatScreen) client.currentScreen).setValue(', 'chatInput((ChatScreen) client.currentScreen).setText(')
    .replaceAll('net.minecraft.world.level.block.Blocks.DIAMOND_BLOCK.defaultBlockState()', 'net.minecraft.block.Blocks.DIAMOND_BLOCK.getDefaultState()')
    .replaceAll('net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState()', 'net.minecraft.block.Blocks.DIRT.getDefaultState()')
    .replaceAll('net.minecraft.world.level.block.Blocks.GOLD_BLOCK.defaultBlockState()', 'net.minecraft.block.Blocks.GOLD_BLOCK.getDefaultState()');
  if (loader === 'fabric' && family === '1.21.11') source = source.replaceAll('client.level.getBlockState(', 'client.world.getBlockState(')
    .replaceAll('net.minecraft.world.level.block.Blocks.STONE', 'net.minecraft.block.Blocks.STONE')
    .replaceAll('client.level, net.minecraft.block.Blocks.STONE.defaultBlockState()', 'client.world, net.minecraft.block.Blocks.STONE.getDefaultState()')
    .replaceAll('net.minecraft.core.BlockPos', 'net.minecraft.util.math.BlockPos')
    .replaceAll('net.minecraft.core.Direction', 'net.minecraft.util.math.Direction')
    .replaceAll('inside.offset(-20, 0, 0)', 'inside.add(-20, 0, 0)');
  const dir = path.join(base, `generated/${loader}-${family}/org/marshal/wesotest`);
  await fs.mkdir(dir, { recursive: true });
  await fs.writeFile(path.join(dir, loader === 'fabric' ? 'CommandGameTest.java' : 'NeoCommandTest.java'), source);
  let edit = editing.replaceAll('runtime.mc26_2', 'runtime.' + ({'1.21.11':'mc1_21_11', '26.1':'mc26_1', '26.2':'mc26_2'}[family]));
  if (loader === 'neo') edit = edit.replaceAll('com.sk89q.worldedit.fabric.FabricAdapter', 'com.sk89q.worldedit.neoforge.NeoForgeAdapter').replaceAll('FabricAdapter', 'NeoForgeAdapter');
  if (family !== '26.2') edit = edit.replaceAll('client.gui.screen()', 'client.screen').replaceAll('client.gui.setScreen(', 'client.setScreen(')
    .replaceAll('client.gui.overlay()', 'client.getOverlay()')
    .replaceAll('client.gui.hud.getChat()', 'client.gui.getChat()');
  if (loader === 'neo' && family === '1.21.11') edit = edit.replaceAll('client.resizeGui()', 'client.resizeDisplay()');
  if (loader === 'neo' && family === '1.21.11') edit = edit.replaceAll('NeoForgeAdapter.get().fromNativePlayer(', 'NeoForgeAdapter.adaptPlayer(');
  if (loader === 'fabric' && family === '1.21.11') edit = edit
    .replaceAll('net.minecraft.client.Minecraft', 'net.minecraft.client.MinecraftClient').replace(/\bMinecraft\b/g, 'MinecraftClient')
    .replaceAll('net.minecraft.client.KeyMapping', 'net.minecraft.client.option.KeyBinding').replaceAll('KeyMapping', 'KeyBinding')
    .replaceAll('net.minecraft.client.input.KeyEvent', 'net.minecraft.client.input.KeyInput').replaceAll('KeyEvent', 'KeyInput')
    .replaceAll('net.minecraft.client.input.MouseButtonInfo', 'net.minecraft.client.input.MouseInput').replaceAll('MouseButtonInfo', 'MouseInput')
    .replace(/(?<![.\w])client\.screen\b/g, 'client.currentScreen').replaceAll('getSingleplayerServer()', 'getServer()').replaceAll('getPlayerList()', 'getPlayerManager()').replaceAll('getPlayers()', 'getPlayerList()')
    .replaceAll('FabricAdapter.get().fromNativePlayer(', 'FabricAdapter.adaptPlayer(')
    .replaceAll('client.mouseHandler', 'client.mouse').replaceAll('client.keyboardHandler', 'client.keyboard')
    .replaceAll('getWindow().handle()', 'getWindow().getHandle()').replaceAll('getScreenWidth()', 'getWidth()').replaceAll('getScreenHeight()', 'getHeight()')
    .replaceAll('.setDown(', '.setPressed(').replaceAll('.isDown()', '.isPressed()').replaceAll('isMouseGrabbed()', 'isCursorLocked()')
    .replaceAll('client.options.keyAttack', 'client.options.attackKey')
    .replaceAll('net.minecraft.client.gui.screens.ChatScreen', 'net.minecraft.client.gui.screen.ChatScreen')
    .replaceAll('net.minecraft.client.gui.screens.Screen', 'net.minecraft.client.gui.screen.Screen')
    .replaceAll('net.minecraft.client.gui.components.AbstractWidget', 'net.minecraft.client.gui.widget.ClickableWidget')
    .replaceAll('client.options.guiScale().set(', 'client.options.getGuiScale().setValue(').replaceAll('client.resizeGui()', 'client.onResolutionChanged()')
    .replaceAll('client.gui.getChat().clearMessages(', 'client.inGameHud.getChatHud().clear(')
    .replaceAll('getLanguageManager().setSelected(', 'getLanguageManager().setLanguage(').replaceAll('client.reloadResourcePacks()', 'client.reloadResources()')
    .replaceAll('getGuiScaledWidth()', 'getScaledWidth()')
    // Production Yarn uses intermediary names; reflection strings are not remapped by Loom.
    .replaceAll('"onScroll"', '"method_1598"').replaceAll('"onButton"', '"method_1601"').replaceAll('"onMove"', '"method_1600"').replaceAll('"keyPress"', '"method_1466"')
    .replace('server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),', 'server.getCommandManager().parseAndExecute(server.getCommandSource(),');
  await fs.writeFile(path.join(dir, 'SelectionEditingChecks.java'), edit);
}
