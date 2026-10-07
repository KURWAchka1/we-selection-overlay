package org.marshal.wesomanual;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public final class FabricManualWorld implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        var bootstrap = new ManualWorldBootstrap(new ManualWorldHost());
        ClientTickEvents.END_CLIENT_TICK.register(client -> bootstrap.tick());
    }
}
