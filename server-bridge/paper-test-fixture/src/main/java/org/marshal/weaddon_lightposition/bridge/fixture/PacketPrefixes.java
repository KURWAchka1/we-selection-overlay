package org.marshal.weaddon_lightposition.bridge.fixture;

import java.lang.reflect.Proxy;
import java.util.HexFormat;
import java.util.Set;
import org.bukkit.Bukkit;

/** Pinned test-only PLAY packet discovery; no protocol IDs inferred from another release. */
final class PacketPrefixes {
    private PacketPrefixes() { }

    static void report() {
        if (!Boolean.getBoolean("weso.bridge.fixture") || !Boolean.getBoolean("weso.bridge.faultFixture")
                || Bukkit.getOnlineMode() || !Bukkit.getIp().equals("127.0.0.1") || Bukkit.getPort() != 25580
                || !Set.of("1.21.11", "26.1.2", "26.2").contains(Bukkit.getMinecraftVersion()))
            throw new IllegalStateException("Pinned offline loopback fault fixture only");
        try {
            System.out.println("WESO_FAULT_PREFIXES: {\"minecraft\":\"" + Bukkit.getMinecraftVersion()
                    + "\",\"toServer\":\"" + prefix("SERVERBOUND") + "\",\"toClient\":\"" + prefix("CLIENTBOUND") + "\"}");
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Pinned Paper protocol discovery no longer matches", ex);
        }
    }

    private static String prefix(String flow) throws ReflectiveOperationException {
        Object template = Class.forName("net.minecraft.network.protocol.game.GameProtocols").getField(flow + "_TEMPLATE").get(null);
        Object details = Class.forName("net.minecraft.network.ProtocolInfo$DetailsProvider").getMethod("details").invoke(template);
        Object payloadType = Class.forName("net.minecraft.network.protocol.common.CommonPacketTypes").getField(flow + "_CUSTOM_PAYLOAD").get(null);
        Class<?> visitor = Class.forName("net.minecraft.network.ProtocolInfo$Details$PacketVisitor");
        int[] found = {-1};
        Object callback = Proxy.newProxyInstance(visitor.getClassLoader(), new Class<?>[]{visitor}, (proxy, method, args) -> {
            if (!method.getName().equals("accept")) throw new IllegalStateException("Unexpected visitor method");
            if (payloadType.equals(args[0])) {
                if (found[0] != -1) throw new IllegalStateException("Duplicate custom payload packet");
                found[0] = (int) args[1];
            }
            return null;
        });
        Class.forName("net.minecraft.network.ProtocolInfo$Details").getMethod("listPackets", visitor).invoke(details, callback);
        if (found[0] < 0) throw new IllegalStateException("Custom payload packet not found");
        Class<?> byteBuf = Class.forName("io.netty.buffer.ByteBuf");
        Object storage = Class.forName("io.netty.buffer.Unpooled").getMethod("buffer", int.class).invoke(null, 64);
        try {
            Class<?> friendly = Class.forName("net.minecraft.network.FriendlyByteBuf");
            Object buffer = friendly.getConstructor(byteBuf).newInstance(storage);
            friendly.getMethod("writeVarInt", int.class).invoke(buffer, found[0]);
            friendly.getMethod("writeUtf", String.class).invoke(buffer, "worldedit_selection_overlay:bridge");
            byte[] bytes = new byte[(int) byteBuf.getMethod("readableBytes").invoke(storage)];
            byteBuf.getMethod("readBytes", byte[].class).invoke(storage, (Object) bytes);
            return HexFormat.of().formatHex(bytes);
        } finally { Class.forName("io.netty.util.ReferenceCounted").getMethod("release").invoke(storage); }
    }
}
