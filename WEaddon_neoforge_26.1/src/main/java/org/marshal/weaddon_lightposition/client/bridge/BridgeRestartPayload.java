package org.marshal.weaddon_lightposition.client.bridge;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.marshal.weaddon_lightposition.bridge.protocol.BridgeRestartHint;

public record BridgeRestartPayload(byte[] bytes) implements CustomPacketPayload {
    public static final Type<BridgeRestartPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("worldedit_selection_overlay", "restart"));
    public static final StreamCodec<ByteBuf, BridgeRestartPayload> CODEC = StreamCodec.of(BridgeRestartPayload::write, BridgeRestartPayload::read);

    public BridgeRestartPayload {
        if (bytes.length != 0 && bytes.length != BridgeRestartHint.FRAME_BYTES)
            throw new IllegalArgumentException("Invalid restart hint length");
        bytes = bytes.clone();
    }

    @Override public byte[] bytes() { return bytes.clone(); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

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
