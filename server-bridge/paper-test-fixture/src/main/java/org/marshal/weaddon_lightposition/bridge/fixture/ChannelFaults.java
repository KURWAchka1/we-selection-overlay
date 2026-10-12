package org.marshal.weaddon_lightposition.bridge.fixture;

import java.nio.charset.StandardCharsets;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Test-only wire fault injection; Bukkit deliberately reserves REGISTER/UNREGISTER. */
final class ChannelFaults {
    private ChannelFaults() { }

    static void advertise(Player player, boolean available) {
        if (!Boolean.getBoolean("weso.bridge.fixture") || !Bukkit.getMinecraftVersion().equals("1.21.11"))
            throw new IllegalStateException("Pinned loopback fixture only");
        try {
            Class<?> id = Class.forName("net.minecraft.resources.Identifier");
            Class<?> payload = Class.forName("net.minecraft.network.protocol.common.custom.CustomPacketPayload");
            Object channel = id.getMethod("parse", String.class).invoke(null,
                    available ? "minecraft:register" : "minecraft:unregister");
            Object data = Class.forName("net.minecraft.network.protocol.common.custom.DiscardedPayload")
                    .getConstructor(id, byte[].class).newInstance(channel,
                            "worldedit_selection_overlay:bridge".getBytes(StandardCharsets.UTF_8));
            Object packet = Class.forName("net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket")
                    .getConstructor(payload).newInstance(data);
            Object handle = player.getClass().getMethod("getHandle").invoke(player);
            Object connection = handle.getClass().getField("connection").get(handle);
            connection.getClass().getMethod("send", Class.forName("net.minecraft.network.protocol.Packet"))
                    .invoke(connection, packet);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Pinned Paper packet fault injector no longer matches", ex);
        }
    }
}
