package org.marshal.weaddon_lightposition.client.bridge;

import net.minecraft.client.Minecraft;
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

    public static void initialize(net.neoforged.bus.api.IEventBus modBus) {
        modBus.addListener((net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event) ->
                event.registrar("weso-bridge-1").optional().playBidirectional(BridgePayload.TYPE, BridgePayload.CODEC,
                        (payload, context) -> { }, (payload, context) -> receive(Minecraft.getInstance(), payload)));
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.client.event.ClientTickEvent.Post event) -> tick(Minecraft.getInstance()));
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) -> reset());
    }

    public static BridgeClient.State status() { return CLIENT.state(); }
    public static SelectionState selection() { return CLIENT.selection(); }

    public static void requestSelection() {
        Minecraft client = Minecraft.getInstance();
        if (client.getConnection() != connection || client.level != level || !canSend(client)) return;
        send(CLIENT.requestSelection(System.nanoTime()));
    }

    private static void tick(Minecraft client) {
        Object current = client.getConnection();
        if (current == null || client.level == null || client.getSingleplayerServer() != null) {
            if (connection != null) reset();
            return;
        }
        long now = System.nanoTime();
        if (current != connection || client.level != level) {
            reset();
            connection = current; level = client.level;
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

    private static void receive(Minecraft client, BridgePayload payload) {
        if (client.getConnection() == connection && client.level == level) CLIENT.receive(payload.bytes());
    }

    private static boolean canSend(Minecraft client) {
        return client.getConnection() != null && client.getConnection().hasChannel(BridgePayload.TYPE);
    }

    private static void send(byte[] frame) {
        if (frame != null) net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(new BridgePayload(frame));
    }

    private static void reset() {
        CLIENT.disconnect(); connection = null; level = null;
        probeRequested = false; probeReported = false;
    }
}
