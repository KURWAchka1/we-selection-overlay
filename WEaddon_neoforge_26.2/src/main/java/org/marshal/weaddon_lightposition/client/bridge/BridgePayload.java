package org.marshal.weaddon_lightposition.client.bridge;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.marshal.weaddon_lightposition.bridge.protocol.BridgeCodec;

public record BridgePayload(byte[] bytes) implements CustomPacketPayload {
    public static final Type<BridgePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("worldedit_selection_overlay", "bridge"));
    public static final StreamCodec<ByteBuf, BridgePayload> CODEC = StreamCodec.of(BridgePayload::write, BridgePayload::read);

    public BridgePayload {
        if (bytes.length > BridgeCodec.MAX_FRAME_BYTES) throw new IllegalArgumentException("Oversized bridge frame");
        bytes = bytes.clone();
    }

    @Override public byte[] bytes() { return bytes.clone(); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    private static void write(ByteBuf buffer, BridgePayload payload) { buffer.writeBytes(payload.bytes); }

    private static BridgePayload read(ByteBuf buffer) {
        int length = buffer.readableBytes();
        if (length < BridgeCodec.MIN_FRAME_BYTES || length > BridgeCodec.MAX_FRAME_BYTES) {
            buffer.skipBytes(length);
            return new BridgePayload(new byte[0]);
        }
        byte[] bytes = new byte[length];
        buffer.readBytes(bytes);
        return new BridgePayload(bytes);
    }
}
