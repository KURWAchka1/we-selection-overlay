package org.marshal.weaddon_lightposition.bridge.protocol;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.UUID;

import static org.marshal.weaddon_lightposition.bridge.protocol.BridgeMessage.*;

public final class BridgeCodec {
    public static final String CHANNEL = "worldedit_selection_overlay:bridge";
    public static final int MIN_FRAME_BYTES = 6;
    public static final int MAX_FRAME_BYTES = 256;
    public static final int MIN_NEGOTIATED_FRAME_BYTES = 64;
    private static final int MAGIC = 0x5745534f;
    private static final int ENVELOPE_VERSION = 1;

    public enum Direction { TO_SERVER, TO_CLIENT }

    private BridgeCodec() { }

    public static byte[] encode(BridgeMessage message) {
        ByteBuffer out = ByteBuffer.allocate(MAX_FRAME_BYTES);
        out.putInt(MAGIC).put((byte) ENVELOPE_VERSION);
        switch (message) {
            case Hello value -> {
                out.put((byte) 1); uuid(out, value.nonce());
                out.putShort((short) value.major()).putShort((short) value.minor()).putInt(value.capabilities());
            }
            case Welcome value -> {
                out.put((byte) 2); uuid(out, value.nonce()); uuid(out, value.epoch());
                out.put((byte) value.status().ordinal()).putShort((short) value.major()).putShort((short) value.minor())
                        .putInt(value.capabilities()).putShort((short) value.maxFrameBytes());
            }
            case SelectionRequest value -> {
                out.put((byte) 3); uuid(out, value.epoch()); out.putLong(value.requestId());
            }
            case SelectionReply value -> {
                out.put((byte) 4); uuid(out, value.epoch()); out.putLong(value.requestId());
                SelectionState state = value.selection();
                out.put((byte) state.status().ordinal());
                if (state.status() == SelectionStatus.READY) {
                    Bounds b = state.bounds();
                    out.putInt(b.minX()).putInt(b.minY()).putInt(b.minZ())
                            .putInt(b.maxX()).putInt(b.maxY()).putInt(b.maxZ())
                            .putInt(state.minY()).putInt(state.maxY()).put((byte) state.permissions());
                }
            }
        }
        return Arrays.copyOf(out.array(), out.position());
    }

    public static BridgeMessage decode(byte[] frame, Direction direction) {
        if (frame == null || frame.length < MIN_FRAME_BYTES || frame.length > MAX_FRAME_BYTES || direction == null)
            throw new IllegalArgumentException("Invalid bridge frame size/direction");
        ByteBuffer in = ByteBuffer.wrap(frame);
        try {
            if (in.getInt() != MAGIC || Byte.toUnsignedInt(in.get()) != ENVELOPE_VERSION)
                throw new IllegalArgumentException("Unknown bridge envelope");
            int opcode = Byte.toUnsignedInt(in.get());
            if (direction == Direction.TO_SERVER ? opcode != 1 && opcode != 3 : opcode != 2 && opcode != 4)
                throw new IllegalArgumentException("Unknown or wrong-direction bridge opcode");
            BridgeMessage message = switch (opcode) {
                case 1 -> new Hello(uuid(in), u16(in), u16(in), in.getInt());
                case 2 -> new Welcome(uuid(in), uuid(in), value(WelcomeStatus.values(), in.get()),
                        u16(in), u16(in), in.getInt(), u16(in));
                case 3 -> new SelectionRequest(uuid(in), in.getLong());
                case 4 -> new SelectionReply(uuid(in), in.getLong(), selection(in));
                default -> throw new IllegalArgumentException("Unknown bridge opcode");
            };
            if (in.hasRemaining()) throw new IllegalArgumentException("Trailing bridge data");
            return message;
        } catch (BufferUnderflowException ex) {
            throw new IllegalArgumentException("Truncated bridge frame", ex);
        }
    }

    private static SelectionState selection(ByteBuffer in) {
        SelectionStatus status = value(SelectionStatus.values(), in.get());
        if (status != SelectionStatus.READY) return SelectionState.unavailable(status);
        return new SelectionState(status, new Bounds(in.getInt(), in.getInt(), in.getInt(),
                in.getInt(), in.getInt(), in.getInt()), in.getInt(), in.getInt(), Byte.toUnsignedInt(in.get()));
    }

    private static <T> T value(T[] values, byte encoded) {
        int index = Byte.toUnsignedInt(encoded);
        if (index >= values.length) throw new IllegalArgumentException("Unknown bridge status");
        return values[index];
    }

    private static int u16(ByteBuffer in) { return Short.toUnsignedInt(in.getShort()); }
    private static UUID uuid(ByteBuffer in) { return new UUID(in.getLong(), in.getLong()); }
    private static void uuid(ByteBuffer out, UUID value) {
        out.putLong(value.getMostSignificantBits()).putLong(value.getLeastSignificantBits());
    }
}
