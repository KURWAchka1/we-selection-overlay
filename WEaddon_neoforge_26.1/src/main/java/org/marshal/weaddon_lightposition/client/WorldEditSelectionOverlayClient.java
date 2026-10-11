package org.marshal.weaddon_lightposition.client;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;

public final class WorldEditSelectionOverlayClient {
    private static final WorldEditPointOverlayClient OVERLAY_CLIENT = new WorldEditPointOverlayClient();

    private WorldEditSelectionOverlayClient() {
    }

    public static void initialize(IEventBus modEventBus) {
        RuntimeCuiBridge.initialize(modEventBus);
        OVERLAY_CLIENT.initialize();
        org.marshal.weaddon_lightposition.client.bridge.NativeServerBridge.initialize(modEventBus);
        NeoForge.EVENT_BUS.addListener(OVERLAY_CLIENT::onRenderCommandPreview);
        modEventBus.addListener(OVERLAY_CLIENT::onRegisterKeyMappings);
        NeoForge.EVENT_BUS.addListener(OVERLAY_CLIENT::onClientTick);
        NeoForge.EVENT_BUS.addListener(OVERLAY_CLIENT::onClientLogin);
        NeoForge.EVENT_BUS.addListener(OVERLAY_CLIENT::onClientLogout);
        NeoForge.EVENT_BUS.addListener(OVERLAY_CLIENT::onClientSystemMessage);
        NeoForge.EVENT_BUS.addListener(OVERLAY_CLIENT::onRenderAfterEntities);
        NeoForge.EVENT_BUS.addListener(OVERLAY_CLIENT::onExtractClipboard);
        NeoForge.EVENT_BUS.addListener(OVERLAY_CLIENT::onSubmitClipboard);
    }
}
