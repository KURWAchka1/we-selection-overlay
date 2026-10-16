package org.marshal.weaddon_lightposition.bridge.server;

import java.util.UUID;
import java.util.function.BiConsumer;

/** Signature fixture only; never placed in an agent or production JAR. */
public final class BridgeServer {
    private long maxSliceNanos;
    public RuntimeException failure;

    public int drain(BiConsumer<UUID, byte[]> send) {
        readSelection();
        if (failure != null) throw failure;
        send.accept(new UUID(0, 1), new byte[]{1});
        maxSliceNanos++;
        return 17;
    }

    public int readSelection() { return 3; }
}
