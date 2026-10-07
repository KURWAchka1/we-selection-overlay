package org.marshal.wesomanual;

import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod("weso_manual_world")
public final class NeoManualWorld {
    public NeoManualWorld() {
        var bootstrap = new ManualWorldBootstrap(new ManualWorldHost());
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> bootstrap.tick());
    }
}
