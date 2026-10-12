package org.marshal.weaddon_lightposition.client.bridge;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import org.marshal.weaddon_lightposition.bridge.protocol.BridgeRestartHint;

public record BridgeRestartPayload(byte[] bytes) implements CustomPayload {
    public static final Id<BridgeRestartPayload> TYPE = new Id<>(Identifier.of("worldedit_selection_overlay", "restart"));
    public static final PacketCodec<ByteBuf, BridgeRestartPayload> CODEC = PacketCodec.ofStatic(BridgeRestartPayload::write, BridgeRestartPayload::read);

    public BridgeRestartPayload {
        if (bytes.length != 0 && bytes.length != BridgeRestartHint.FRAME_BYTES)
            throw new IllegalArgumentException("Invalid restart hint length");
        bytes = bytes.clone();
    }

    @Override public byte[] bytes() { return bytes.clone(); }
    @Override public Id<? extends CustomPayload> getId() { return TYPE; }

    private static void write(ByteBuf buffer, BridgeRestartPayload payload) { buffer.writeBytes(payload.bytes); }

    private static BridgeRestartPayload read(ByteBuf buffer) {
        int length = buffer.readableBytes();
        if (length != BridgeRestartHint.FRAME_BYTES) {
            buffer.skipBytes(length);
            return new BridgeRestartPayload(new byte[0]);
        }
        byte[] bytes = new byte[length];
        buffer.readBytes(bytes);
        return new BridgeRestartPayload(bytes);
    }
}
