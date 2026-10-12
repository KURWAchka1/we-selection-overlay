package org.marshal.weaddon_lightposition.bridge.protocol;

import java.nio.ByteBuffer;
import java.util.UUID;

/** Optional lifecycle hint, never an authorization or selection response. */
public final class BridgeRestartHint {
    public static final String CHANNEL = "worldedit_selection_overlay:restart";
    public static final int FRAME_BYTES = 22;
    private static final int MAGIC = 0x57455352;

    private BridgeRestartHint() { }

    public static byte[] encode(UUID generation) {
        validate(generation);
        return ByteBuffer.allocate(FRAME_BYTES).putInt(MAGIC).put((byte) 1).put((byte) 1)
                .putLong(generation.getMostSignificantBits()).putLong(generation.getLeastSignificantBits()).array();
    }

    public static UUID decode(byte[] frame) {
        if (frame == null || frame.length != FRAME_BYTES) throw new IllegalArgumentException("Invalid restart hint length");
        ByteBuffer buffer = ByteBuffer.wrap(frame);
        if (buffer.getInt() != MAGIC || buffer.get() != 1 || buffer.get() != 1)
            throw new IllegalArgumentException("Invalid restart hint header");
        UUID generation = new UUID(buffer.getLong(), buffer.getLong());
        validate(generation);
        return generation;
    }

    private static void validate(UUID generation) {
        if (generation == null || generation.equals(new UUID(0, 0)))
            throw new IllegalArgumentException("Missing service generation");
    }
}
