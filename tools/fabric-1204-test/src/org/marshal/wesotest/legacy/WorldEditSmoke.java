package org.marshal.wesotest.legacy;

import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.fabric.FabricAdapter;
import net.minecraft.client.MinecraftClient;

final class WorldEditSmoke {
    private WorldEditSmoke() { }

    static boolean hasCui(MinecraftClient client) {
        var player = client.getServer().getPlayerManager().getPlayer(client.player.getUuid());
        return player != null && WorldEdit.getInstance().getSessionManager().get(FabricAdapter.adaptPlayer(player)).hasCUISupport();
    }
}
