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
    private static final BridgeClient CLIENT = new BridgeClient(org.marshal.weaddon_lightposition.bridge.protocol.BridgeMessage.SUPPORTED_CAPABILITIES);
    private static final org.marshal.weaddon_lightposition.bridge.client.BridgeEditExchange EDIT = new org.marshal.weaddon_lightposition.bridge.client.BridgeEditExchange(CLIENT);
    private static long editGeneration;
    private static final boolean PROBE = Boolean.getBoolean("weso.bridge.probe");
    private static Object connection, level, player;
    private static boolean probeRequested, probeReported;

    private NativeServerBridge() { }

    public static void initialize() {
        PayloadTypeRegistry.playS2C().register(BridgePayload.TYPE, BridgePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(BridgePayload.TYPE, BridgePayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(BridgePayload.TYPE, (payload, context) -> receive(context.client(), payload));
        PayloadTypeRegistry.playS2C().register(BridgeRestartPayload.TYPE, BridgeRestartPayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(BridgeRestartPayload.TYPE,
                (payload, context) -> receiveRestartHint(context.client(), payload));
        ClientTickEvents.END_CLIENT_TICK.register(NativeServerBridge::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
    }

    public static BridgeClient.State status() { return CLIENT.state(); }
    public static SelectionState selection() { return CLIENT.selection(); }
    public static java.util.UUID editContext() {
        var client = MinecraftClient.getInstance();
        return sameContext(client) && canSend(client) && (CLIENT.capabilities() & org.marshal.weaddon_lightposition.bridge.protocol.BridgeMessage.SELECTION_EDIT) != 0 ? CLIENT.epoch() : null;
    }
    public static long editGeneration() { return editGeneration; }
    public static void cuiChanged() { editGeneration++; }
    public static void readEdit(java.util.function.Consumer<org.marshal.weaddon_lightposition.bridge.protocol.BridgeMessage.EditSnapshot> result) {
        EDIT.read(System.nanoTime(), NativeServerBridge::send, result);
    }
    public static void commitEdit(java.util.UUID revision, org.marshal.weaddon_lightposition.bridge.protocol.BridgeMessage.Bounds bounds,
            java.util.function.Consumer<org.marshal.weaddon_lightposition.bridge.protocol.BridgeMessage.EditCommitReply> result) {
        EDIT.commit(revision, bounds, System.nanoTime(), NativeServerBridge::send, result);
    }

    public static void requestSelection() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!sameContext(client) || !canSend(client)) return;
        send(CLIENT.requestSelection(System.nanoTime()));
    }

    private static void tick(MinecraftClient client) {
        Object current = client.getNetworkHandler();
        if (current == null || client.world == null || client.player == null || client.getServer() != null) {
            if (connection != null) reset();
            return;
        }
        long now = System.nanoTime();
        if (!sameContext(client)) {
            reset();
            connection = current; level = client.world; player = client.player;
            CLIENT.connect(now);
        }
        send(CLIENT.poll(canSend(client), now));
        EDIT.complete();
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
        if (sameContext(client) && canSend(client)) CLIENT.receive(payload.bytes());
        EDIT.complete();
    }

    private static void receiveRestartHint(MinecraftClient client, BridgeRestartPayload payload) {
        if (sameContext(client) && canSend(client)) CLIENT.receiveRestartHint(payload.bytes(), System.nanoTime());
    }

    private static boolean sameContext(MinecraftClient client) {
        return connection != null && client.getNetworkHandler() == connection && client.world == level && client.player == player;
    }

    private static boolean canSend(MinecraftClient client) {
        return client.getNetworkHandler() != null && ClientPlayNetworking.canSend(BridgePayload.TYPE);
    }

    private static void send(byte[] frame) {
        if (frame != null) ClientPlayNetworking.send(new BridgePayload(frame));
    }

    private static void reset() {
        CLIENT.disconnect(); connection = null; level = null; player = null;
        editGeneration++; EDIT.complete();
        probeRequested = false; probeReported = false;
    }
}
