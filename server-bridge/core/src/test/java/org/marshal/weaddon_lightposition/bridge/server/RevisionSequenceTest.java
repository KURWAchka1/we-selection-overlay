package org.marshal.weaddon_lightposition.bridge.server;

import java.util.HashSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.marshal.weaddon_lightposition.bridge.protocol.BridgeCodec;
import static org.marshal.weaddon_lightposition.bridge.protocol.BridgeMessage.*;
import static org.junit.jupiter.api.Assertions.*;

class RevisionSequenceTest {
    @Test void oneNamespaceNeverReusesAHandleAcrossOrdinaryAdvances() {
        var seed = new UUID(4, 5); var sequence = new RevisionSequence(seed); var seen = new HashSet<UUID>();
        for (int i = 1; i <= 100_000; i++) {
            UUID next = sequence.next(); assertEquals(new UUID(4, 5 + i), next);
            assertNotEquals(seed, next); assertTrue(seen.add(next));
        }
    }

    @Test void unsignedLowWordWrapCarriesIntoHighWord() {
        var sequence = new RevisionSequence(new UUID(7, -1));
        assertEquals(new UUID(8, 0), sequence.next()); assertEquals(new UUID(8, 1), sequence.next());
    }

    @Test void signedBoundaryDoesNotStopTheUnsignedSequence() {
        var sequence = new RevisionSequence(new UUID(7, Long.MAX_VALUE));
        assertEquals(new UUID(7, Long.MIN_VALUE), sequence.next());
        assertEquals(new UUID(7, Long.MIN_VALUE + 1), sequence.next());
    }

    @Test void zeroIsSkippedEvenWhenBothWordsWrap() {
        var sequence = new RevisionSequence(new UUID(-1, -1));
        assertEquals(new UUID(0, 1), sequence.next()); assertEquals(new UUID(0, 2), sequence.next());
    }

    @Test void zeroSeedStillProducesOnlyUsableHandles() {
        var sequence = new RevisionSequence(new UUID(0, 0));
        assertEquals(new UUID(0, 1), sequence.next());
    }

    @Test void aCompleteCycleFailsInsteadOfReissuingTheSeed() throws Exception {
        var sequence = new RevisionSequence(new UUID(4, 5));
        var high = RevisionSequence.class.getDeclaredField("high"); high.setAccessible(true); high.setLong(sequence, 4);
        var low = RevisionSequence.class.getDeclaredField("low"); low.setAccessible(true); low.setLong(sequence, 4);
        assertThrows(IllegalStateException.class, sequence::next);
        assertThrows(IllegalStateException.class, sequence::next);
    }

    @Test void missingNamespaceIsRejected() { assertThrows(NullPointerException.class, () -> new RevisionSequence(null)); }

    @Test void nonVersionFourHandlesUseTheExistingWireWithoutChangingItsSchema() {
        var sequence = new RevisionSequence(new UUID(-1, -1));
        var state = new SelectionState(SelectionStatus.READY, new Bounds(0, 0, 0, 1, 1, 1), -64, 319, 7);
        for (int i = 1; i <= 3; i++) {
            var reply = new EditReadReply(new UUID(1, 2), i, new EditSnapshot(sequence.next(), state));
            assertEquals(reply, BridgeCodec.decode(BridgeCodec.encode(reply), BridgeCodec.Direction.TO_CLIENT));
        }
    }
}
