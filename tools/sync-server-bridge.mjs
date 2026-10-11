import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const java = 'src/main/java/org/marshal/weaddon_lightposition/client';
const canonical = 'WEaddon_fabric_26.2';
const targets = [
  [canonical, '26.2', false], ['WEaddon_lightposition', '26.1', false],
  ['WEaddon_fabric_1.21.11', 'yarn', false], ['WEaddon_neoforge_1.21.11', '1.21.11', true],
  ['WEaddon_neoforge_26.1', '26.1', true], ['WEaddon_neoforge_26.2', '26.2', true]
];
const payload = await fs.readFile(path.join(root, canonical, java, 'bridge/BridgePayload.java'), 'utf8');
const transport = await fs.readFile(path.join(root, canonical, java, 'bridge/NativeServerBridge.java'), 'utf8');
const fabricHooks = `        PayloadTypeRegistry.clientboundPlay().register(BridgePayload.TYPE, BridgePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(BridgePayload.TYPE, BridgePayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(BridgePayload.TYPE, (payload, context) -> receive(context.client(), payload));
        ClientTickEvents.END_CLIENT_TICK.register(NativeServerBridge::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());`;
const neoHooks = `        modBus.addListener((net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event) ->
                event.registrar("weso-bridge-1").optional().playBidirectional(BridgePayload.TYPE, BridgePayload.CODEC,
                        (payload, context) -> { }, (payload, context) -> receive(Minecraft.getInstance(), payload)));
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.client.event.ClientTickEvent.Post event) -> tick(Minecraft.getInstance()));
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) -> reset());`;

function replaceRequired(code, before, after, label) {
  if (!code.includes(before)) throw new Error(`Missing ${label} anchor`);
  return code.replace(before, after);
}

for (const [module, version, neo] of targets) {
  let native = transport;
  let frame = payload;
  if (neo) {
    native = native.replace(/^import net\.fabricmc\..*;\r?\n/gm, '')
      .replace('public static void initialize()', 'public static void initialize(net.neoforged.bus.api.IEventBus modBus)');
    native = replaceRequired(native, fabricHooks, neoHooks, `${module} lifecycle`)
      .replace('ClientPlayNetworking.canSend(BridgePayload.TYPE)', 'client.getConnection().hasChannel(BridgePayload.TYPE)')
      .replace('ClientPlayNetworking.send(new BridgePayload(frame))', 'net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(new BridgePayload(frame))');
  } else if (version === 'yarn') {
    native = native.replace('PayloadTypeRegistry.clientboundPlay()', 'PayloadTypeRegistry.playS2C()')
      .replace('PayloadTypeRegistry.serverboundPlay()', 'PayloadTypeRegistry.playC2S()');
  }
  if (version === 'yarn') {
    native = native.replaceAll('net.minecraft.client.Minecraft', 'net.minecraft.client.MinecraftClient')
      .replaceAll('Minecraft', 'MinecraftClient').replaceAll('MinecraftClientClient', 'MinecraftClient')
      .replaceAll('.getConnection()', '.getNetworkHandler()').replaceAll('.getSingleplayerServer()', '.getServer()')
      .replaceAll('client.level', 'client.world');
    frame = frame.replace('net.minecraft.network.codec.StreamCodec', 'net.minecraft.network.codec.PacketCodec')
      .replace('net.minecraft.network.protocol.common.custom.CustomPacketPayload', 'net.minecraft.network.packet.CustomPayload')
      .replace('net.minecraft.resources.Identifier', 'net.minecraft.util.Identifier')
      .replaceAll('CustomPacketPayload', 'CustomPayload').replaceAll('StreamCodec', 'PacketCodec')
      .replaceAll('Type<', 'Id<').replaceAll('new Type<>', 'new Id<>').replaceAll('type()', 'getId()')
      .replace('Identifier.fromNamespaceAndPath(', 'Identifier.of(').replace('PacketCodec.of(', 'PacketCodec.ofStatic(');
  }
  const destination = path.join(root, module, java, 'bridge');
  await fs.mkdir(destination, { recursive: true });
  await fs.writeFile(path.join(destination, 'BridgePayload.java'), frame);
  await fs.writeFile(path.join(destination, 'NativeServerBridge.java'), native);

  const gradlePath = path.join(root, module, 'build.gradle');
  let gradle = await fs.readFile(gradlePath, 'utf8');
  for (const shared of ['server-bridge-protocol', 'server-bridge-client']) {
    const line = `sourceSets.main.java.srcDir('../shared/${shared}/src/main/java')`;
    if (!gradle.includes(line)) gradle += `\n${line}\n`;
  }
  await fs.writeFile(gradlePath, gradle);

  const entryPath = path.join(root, module, java, 'WorldEditSelectionOverlayClient.java');
  let entry = await fs.readFile(entryPath, 'utf8');
  if (!entry.includes('bridge.NativeServerBridge.initialize')) {
    const anchor = neo ? '        OVERLAY_CLIENT.initialize();' : '        overlayClient.initialize();';
    entry = replaceRequired(entry, anchor, `${anchor}\n        org.marshal.weaddon_lightposition.client.bridge.NativeServerBridge.initialize(${neo ? 'modEventBus' : ''});`, `${module} entrypoint`);
  }
  await fs.writeFile(entryPath, entry);
}
console.log(`Synchronized optional bridge adapters for ${targets.length} client runtimes.`);
