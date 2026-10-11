package org.marshal.weaddon_lightposition.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.enginehub.worldeditcui.protocol.CUIPacket;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Uses the shared EngineHub protocol when available; otherwise a private wire-compatible copy. */
public final class RuntimeCuiBridge {
    private static Consumer<CUIPacket> receiver;
    private static Constructor<?> sharedPacket;

    private RuntimeCuiBridge() {
    }

    public static void initialize(IEventBus modBus) {
        try {
            // Keep the public API name out of Shadow's relocation of the private fallback.
            String api = String.join(".", "org", "enginehub", "worldeditcui", "protocol");
            Class<?> handler = Class.forName(api + ".CUIPacketHandler");
            Class<?> packet = Class.forName(api + ".CUIPacket");
            Method multi = packet.getMethod("multi");
            Method eventType = packet.getMethod("eventType");
            Method args = packet.getMethod("args");
            sharedPacket = packet.getConstructor(String.class, String[].class);
            BiConsumer<Object, Object> callback = (payload, context) -> {
                try {
                    @SuppressWarnings("unchecked") List<String> values = (List<String>) args.invoke(payload);
                    deliver(new CUIPacket((boolean) multi.invoke(payload), (String) eventType.invoke(payload), values));
                } catch (ReflectiveOperationException exception) {
                    throw new IllegalStateException("Unable to receive the shared WorldEdit CUI packet", exception);
                }
            };
            Object instance = handler.getMethod("instance").invoke(null);
            handler.getMethod("registerClientboundHandler", BiConsumer.class).invoke(instance, callback);
        } catch (ClassNotFoundException absent) {
            modBus.addListener(RuntimeCuiBridge::registerFallback);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Incompatible shared WorldEdit CUI protocol", exception);
        }
    }

    public static void register(Consumer<CUIPacket> callback) {
        receiver = callback;
    }

    public static void sendHello() {
        CustomPacketPayload payload;
        if (sharedPacket == null) {
            payload = new CUIPacket("v", CUIPacket.protocolVersion());
        } else {
            try {
                payload = (CustomPacketPayload) sharedPacket.newInstance("v", new String[]{CUIPacket.protocolVersion()});
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Unable to send the shared WorldEdit CUI handshake", exception);
            }
        }
        ClientPacketDistributor.sendToServer(payload);
    }

    private static void registerFallback(RegisterPayloadHandlersEvent event) {
        event.registrar(CUIPacket.protocolVersion()).optional().playBidirectional(
                CUIPacket.TYPE, CUIPacket.CODEC,
                (payload, context) -> { },
                (payload, context) -> deliver(payload));
    }

    private static void deliver(CUIPacket payload) {
        Minecraft.getInstance().execute(() -> {
            if (receiver != null) receiver.accept(payload);
        });
    }
}
