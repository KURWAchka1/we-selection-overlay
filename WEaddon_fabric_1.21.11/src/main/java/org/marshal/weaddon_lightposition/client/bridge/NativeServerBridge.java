package org.marshal.weaddon_lightposition.client.bridge;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.MinecraftClient;
import org.marshal.weaddon_lightposition.bridge.client.BridgeClient;
import org.marshal.weaddon_lightposition.bridge.protocol.BridgeMessage.SelectionState;
import org.slf4j.LoggerFactory;

/** Optional read-only transport. Existing CUI/local sources remain independent. */
public final class NativeServerBridge {
    private static final BridgeClient CLIENT = new BridgeClient();
    private static final boolean PROBE = Boolean.getBoolean("weso.bridge.probe");
    private static Object connection, level;
    private static boolean probeRequested, probeReported;

    private NativeServerBridge() { }

    public static void initialize() {
        PayloadTypeRegistry.playS2C().register(BridgePayload.TYPE, BridgePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(BridgePayload.TYPE, BridgePayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(BridgePayload.TYPE, (payload, context) -> receive(context.client(), payload));
        ClientTickEvents.END_CLIENT_TICK.register(NativeServerBridge::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
    }

    public static BridgeClient.State status() { return CLIENT.state(); }
    public static SelectionState selection() { return CLIENT.selection(); }

    public static void requestSelection() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getNetworkHandler() != connection || client.world != level || !canSend(client)) return;
        send(CLIENT.requestSelection(System.nanoTime()));
    }

    private static void tick(MinecraftClient client) {
        Object current = client.getNetworkHandler();
        if (current == null || client.world == null || client.getServer() != null) {
            if (connection != null) reset();
            return;
        }
        long now = System.nanoTime();
        if (current != connection || client.world != level) {
            reset();
            connection = current; level = client.world;
            CLIENT.connect(now);
        }
        send(CLIENT.poll(canSend(client), now));
        if (PROBE && !probeRequested && CLIENT.state() == BridgeClient.State.READY) {
            probeRequested = true;
            requestSelection();
        }
        if (PROBE && !probeReported && CLIENT.selection() != null) {
            probeReported = true;
            LoggerFactory.getLogger("worldedit_selection_overlay/bridge").info("Read-only bridge probe: {}", CLIENT.selection());
        }
    }

    private static void receive(MinecraftClient client, BridgePayload payload) {
        if (client.getNetworkHandler() == connection && client.world == level) CLIENT.receive(payload.bytes());
    }

    private static boolean canSend(MinecraftClient client) {
        return client.getNetworkHandler() != null && ClientPlayNetworking.canSend(BridgePayload.TYPE);
    }

    private static void send(byte[] frame) {
        if (frame != null) ClientPlayNetworking.send(new BridgePayload(frame));
    }

    private static void reset() {
        CLIENT.disconnect(); connection = null; level = null;
        probeRequested = false; probeReported = false;
    }
}
