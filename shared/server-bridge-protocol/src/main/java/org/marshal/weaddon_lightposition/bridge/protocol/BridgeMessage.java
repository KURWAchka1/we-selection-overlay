package org.marshal.weaddon_lightposition.bridge.protocol;

import java.util.Objects;
import java.util.UUID;

public sealed interface BridgeMessage {
    int MAJOR = 1;
    int MINOR = 1;
    int SELECTION_READ = 1;
    int SESSION_CLOSE = 2;
    int SUPPORTED_CAPABILITIES = SELECTION_READ | SESSION_CLOSE;
    UUID NO_EPOCH = new UUID(0, 0);

    enum WelcomeStatus { READY, DENIED, INCOMPATIBLE }
    enum SelectionStatus { READY, EMPTY, UNSUPPORTED, DENIED, FAILED }

    record Hello(UUID nonce, int major, int minor, int capabilities) implements BridgeMessage {
        public Hello {
            Objects.requireNonNull(nonce);
            unsignedShort(major);
            unsignedShort(minor);
        }
    }

    record Welcome(UUID nonce, UUID epoch, WelcomeStatus status, int major, int minor,
                   int capabilities, int maxFrameBytes) implements BridgeMessage {
        public Welcome {
            Objects.requireNonNull(nonce);
            Objects.requireNonNull(epoch);
            Objects.requireNonNull(status);
            unsignedShort(major);
            unsignedShort(minor);
            if (maxFrameBytes < BridgeCodec.MIN_FRAME_BYTES || maxFrameBytes > BridgeCodec.MAX_FRAME_BYTES
                    || (capabilities & ~SUPPORTED_CAPABILITIES) != 0
                    || ((capabilities & SESSION_CLOSE) != 0 && (minor < 1 || (capabilities & SELECTION_READ) == 0))
                    || (status != WelcomeStatus.READY && (capabilities != 0 || !epoch.equals(NO_EPOCH)))
                    || (status == WelcomeStatus.READY && (epoch.equals(NO_EPOCH) || capabilities == 0))) {
                throw new IllegalArgumentException("Invalid welcome");
            }
        }
    }

    record SelectionRequest(UUID epoch, long requestId) implements BridgeMessage {
        public SelectionRequest { Objects.requireNonNull(epoch); positive(requestId); }
    }

    record SelectionReply(UUID epoch, long requestId, SelectionState selection) implements BridgeMessage {
        public SelectionReply {
            Objects.requireNonNull(epoch);
            Objects.requireNonNull(selection);
            positive(requestId);
        }
    }

    /** Negotiated minor-1 notification; never a command or a request for data. */
    record SessionClosed(UUID nonce, UUID epoch) implements BridgeMessage {
        public SessionClosed {
            Objects.requireNonNull(nonce);
            Objects.requireNonNull(epoch);
            if (epoch.equals(NO_EPOCH)) throw new IllegalArgumentException("Invalid closed session epoch");
        }
    }

    record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public Bounds {
            if (minX > maxX || minY > maxY || minZ > maxZ
                    || minX < -30_000_000 || maxX > 30_000_000
                    || minZ < -30_000_000 || maxZ > 30_000_000) {
                throw new IllegalArgumentException("Invalid selection bounds");
            }
        }
    }

    record SelectionState(SelectionStatus status, Bounds bounds, int minY, int maxY, int permissions) {
        public SelectionState {
            Objects.requireNonNull(status);
            if ((permissions & ~7) != 0 || status == SelectionStatus.READY
                    && (bounds == null || minY > maxY || bounds.minY() < minY || bounds.maxY() > maxY)
                    || status != SelectionStatus.READY
                    && (bounds != null || minY != 0 || maxY != 0 || permissions != 0)) {
                throw new IllegalArgumentException("Invalid selection state");
            }
        }

        public static SelectionState unavailable(SelectionStatus status) {
            return new SelectionState(status, null, 0, 0, 0);
        }
    }

    private static void unsignedShort(int value) {
        if (value < 0 || value > 65535) throw new IllegalArgumentException("Invalid version");
    }

    private static void positive(long value) {
        if (value <= 0) throw new IllegalArgumentException("Invalid request ID");
    }
}
