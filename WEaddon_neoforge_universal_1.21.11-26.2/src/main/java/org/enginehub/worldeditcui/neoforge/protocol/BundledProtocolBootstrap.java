package org.enginehub.worldeditcui.neoforge.protocol;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.enginehub.worldeditcui.protocol.CUIPacket;

public final class BundledProtocolBootstrap {
    private BundledProtocolBootstrap() {
    }

    public static void initialize(IEventBus modEventBus) {
        modEventBus.addListener(BundledProtocolBootstrap::registerPacket);
    }

    private static void registerPacket(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(CUIPacket.protocolVersion()).optional();
        registrar.playBidirectional(
                CUIPacket.TYPE,
                CUIPacket.CODEC,
                NeoForgeCUIPacketHandler.asHandler(),
                NeoForgeCUIPacketHandler.asHandler()
        );
    }
}
