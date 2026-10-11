package org.marshal.weaddon_lightposition.client;

import net.fabricmc.api.ClientModInitializer;

public class WorldEditSelectionOverlayClient implements ClientModInitializer {
    private final WorldEditPointOverlayClient overlayClient = new WorldEditPointOverlayClient();

    @Override
    public void onInitializeClient() {
        overlayClient.initialize();
        org.marshal.weaddon_lightposition.client.bridge.NativeServerBridge.initialize();
    }
}
