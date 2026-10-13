package org.marshal.weaddon_lightposition.client.bridge;

import net.minecraft.client.Minecraft;
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

    public static void initialize(net.neoforged.bus.api.IEventBus modBus) {
        modBus.addListener((net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event) ->
                event.registrar("weso-bridge-1").optional().playBidirectional(BridgePayload.TYPE, BridgePayload.CODEC,
                        (payload, context) -> { }, (payload, context) -> receive(Minecraft.getInstance(), payload)));
        modBus.addListener((net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event) ->
                event.registrar("weso-bridge-restart-1").optional().playToClient(BridgeRestartPayload.TYPE, BridgeRestartPayload.CODEC,
                        (payload, context) -> receiveRestartHint(Minecraft.getInstance(), payload)));
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.client.event.ClientTickEvent.Post event) -> tick(Minecraft.getInstance()));
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) -> reset());
    }

    public static BridgeClient.State status() { return CLIENT.state(); }
    public static SelectionState selection() { return CLIENT.selection(); }
    public static java.util.UUID editContext() {
        var client = Minecraft.getInstance();
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
        Minecraft client = Minecraft.getInstance();
        if (!sameContext(client) || !canSend(client)) return;
        send(CLIENT.requestSelection(System.nanoTime()));
    }

    private static void tick(Minecraft client) {
        Object current = client.getConnection();
        if (current == null || client.level == null || client.player == null || client.getSingleplayerServer() != null) {
            if (connection != null) reset();
            return;
        }
        long now = System.nanoTime();
        if (!sameContext(client)) {
            reset();
            connection = current; level = client.level; player = client.player;
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

    private static void receive(Minecraft client, BridgePayload payload) {
        if (sameContext(client) && canSend(client)) CLIENT.receive(payload.bytes());
        EDIT.complete();
    }

    private static void receiveRestartHint(Minecraft client, BridgeRestartPayload payload) {
        if (sameContext(client) && canSend(client)) CLIENT.receiveRestartHint(payload.bytes(), System.nanoTime());
    }

    private static boolean sameContext(Minecraft client) {
        return connection != null && client.getConnection() == connection && client.level == level && client.player == player;
    }

    private static boolean canSend(Minecraft client) {
        return client.getConnection() != null && client.getConnection().hasChannel(BridgePayload.TYPE);
    }

    private static void send(byte[] frame) {
        if (frame != null) net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(new BridgePayload(frame));
    }

    private static void reset() {
        CLIENT.disconnect(); connection = null; level = null; player = null;
        editGeneration++; EDIT.complete();
        probeRequested = false; probeReported = false;
    }
}
