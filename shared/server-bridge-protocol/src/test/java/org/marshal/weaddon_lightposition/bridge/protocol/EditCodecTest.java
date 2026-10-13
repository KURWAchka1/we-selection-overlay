package org.marshal.weaddon_lightposition.bridge.protocol;

import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.marshal.weaddon_lightposition.bridge.protocol.BridgeMessage.*;

class EditCodecTest {
    private final UUID epoch = new UUID(2, 3), revision = new UUID(0, 4);
    private final Bounds bounds = new Bounds(-2, -64, 0, 5, 319, 7);
    private final EditSnapshot ready = new EditSnapshot(revision, new SelectionState(SelectionStatus.READY, bounds, -64, 319, 7));

    @Test void editFramesHaveFixedGoldenBytesAndBoundedCanonicalSizes() {
        assertEquals("5745534f010800000000000000020000000000000003000000000000000100000000000000000000000000000004fffffffeffffffc000000000000000050000013f00000007",
                HexFormat.of().formatHex(BridgeCodec.encode(new EditCommitRequest(epoch, 1, revision, bounds))));
        assertEquals(30, BridgeCodec.encode(new EditReadRequest(epoch, 1)).length);
        assertEquals(70, BridgeCodec.encode(new EditCommitRequest(epoch, 1, revision, bounds)).length);
        assertEquals(80, BridgeCodec.encode(new EditReadReply(epoch, 1, ready)).length);
        assertEquals(81, BridgeCodec.encode(new EditCommitReply(epoch, 1, CommitStatus.APPLIED, ready)).length);
    }
    @Test void editsRejectTruncationTrailingDataWrongDirectionAndUnknownStatus() {
        var messages = List.of(new EditReadRequest(epoch, 1), new EditReadReply(epoch, 2, ready),
                new EditCommitRequest(epoch, 3, revision, bounds), new EditCommitReply(epoch, 4, CommitStatus.APPLIED, ready),
                new EditCommitReply(epoch, 5, CommitStatus.UNKNOWN, EditSnapshot.unavailable(SelectionStatus.FAILED)),
                new EditReadReply(epoch, 6, EditSnapshot.unavailable(SelectionStatus.UNSUPPORTED)));
        for (var message : messages) {
            var direction = message instanceof EditReadRequest || message instanceof EditCommitRequest ? BridgeCodec.Direction.TO_SERVER : BridgeCodec.Direction.TO_CLIENT;
            byte[] frame = BridgeCodec.encode(message);
            assertEquals(message, BridgeCodec.decode(frame, direction));
            assertThrows(IllegalArgumentException.class, () -> BridgeCodec.decode(frame, direction == BridgeCodec.Direction.TO_SERVER ? BridgeCodec.Direction.TO_CLIENT : BridgeCodec.Direction.TO_SERVER));
            for (int length = 0; length < frame.length; length++) {
                byte[] prefix = Arrays.copyOf(frame, length);
                assertThrows(IllegalArgumentException.class, () -> BridgeCodec.decode(prefix, direction));
            }
            assertThrows(IllegalArgumentException.class, () -> BridgeCodec.decode(Arrays.copyOf(frame, frame.length + 1), direction));
        }
        byte[] bad = BridgeCodec.encode(new EditCommitReply(epoch, 1, CommitStatus.APPLIED, ready)); bad[30] = -1;
        assertThrows(IllegalArgumentException.class, () -> BridgeCodec.decode(bad, BridgeCodec.Direction.TO_CLIENT));
    }
    @Test void revisionAndCapabilityDomainsFailBeforeEncoding() {
        assertThrows(IllegalArgumentException.class, () -> new EditSnapshot(NO_EPOCH, ready.selection()));
        assertThrows(IllegalArgumentException.class, () -> new EditSnapshot(revision, SelectionState.unavailable(SelectionStatus.EMPTY)));
        assertThrows(IllegalArgumentException.class, () -> new EditCommitRequest(epoch, 1, NO_EPOCH, bounds));
        assertThrows(IllegalArgumentException.class, () -> new EditCommitReply(epoch, 1, CommitStatus.APPLIED, EditSnapshot.unavailable(SelectionStatus.EMPTY)));
        assertThrows(IllegalArgumentException.class, () -> new Welcome(revision, epoch, WelcomeStatus.READY, 1, 1, 7, 256));
        assertThrows(IllegalArgumentException.class, () -> new Welcome(revision, epoch, WelcomeStatus.READY, 1, 2, 7, 80));
        assertThrows(IllegalArgumentException.class, () -> new Welcome(revision, epoch, WelcomeStatus.READY, 1, 2, 4, 256));
    }
}
