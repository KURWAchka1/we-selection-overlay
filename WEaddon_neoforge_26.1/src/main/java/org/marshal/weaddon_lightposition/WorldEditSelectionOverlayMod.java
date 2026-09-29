package org.marshal.weaddon_lightposition;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import org.marshal.weaddon_lightposition.client.WorldEditSelectionOverlayClient;

@Mod(WorldEditSelectionOverlayMod.MOD_ID)
public final class WorldEditSelectionOverlayMod {
    public static final String MOD_ID = "worldedit_selection_overlay";

    public WorldEditSelectionOverlayMod(IEventBus modEventBus) {
        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            WorldEditSelectionOverlayClient.initialize(modEventBus);
        }
    }
}
