package org.marshal.weaddon_lightposition.bridge.client;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.marshal.weaddon_lightposition.bridge.protocol.BridgeCodec;
import static org.junit.jupiter.api.Assertions.*;
import static org.marshal.weaddon_lightposition.bridge.protocol.BridgeMessage.*;

class BridgeEditClientTest {
    private final UUID epoch = new UUID(2, 2), revision = new UUID(3, 3), nonce = new UUID(1, 1);
    private final BridgeClient client = new BridgeClient(() -> nonce, SUPPORTED_CAPABILITIES);
    private final BridgeEditExchange exchange = new BridgeEditExchange(client);
    private final Bounds bounds = new Bounds(0, 0, 0, 5, 5, 5);
    private final EditSnapshot snapshot = new EditSnapshot(revision, new SelectionState(SelectionStatus.READY, bounds, -64, 319, 7));
    private void ready(int minor, int capabilities) {
        client.connect(0); client.poll(true, 0);
        client.receive(BridgeCodec.encode(new Welcome(nonce, epoch, WelcomeStatus.READY, 1, minor, capabilities, 256)));
    }
    @Test void resultsRequireMatchingOperationEpochAndRequest() {
        ready(2, 7); client.requestEdit(1);
        client.receive(BridgeCodec.encode(new SelectionReply(epoch, 1, snapshot.selection())));
        client.receive(BridgeCodec.encode(new EditReadReply(epoch, 2, snapshot)));
        client.receive(BridgeCodec.encode(new EditReadReply(nonce, 1, snapshot)));
        assertTrue(client.pending()); assertNull(client.editSelection());
        client.receive(BridgeCodec.encode(new EditReadReply(epoch, 1, snapshot)));
        assertFalse(client.pending()); assertEquals(snapshot, client.editSelection());
        client.commitEdit(revision, bounds, 2);
        client.receive(BridgeCodec.encode(new EditReadReply(epoch, 2, snapshot)));
        assertTrue(client.pending());
        client.receive(BridgeCodec.encode(new EditCommitReply(epoch, 2, CommitStatus.APPLIED, snapshot)));
        assertFalse(client.pending()); assertEquals(CommitStatus.APPLIED, client.editResult().status());
    }
    @Test void timeoutCompletesUnknownOnceWithoutRetryAndLateAckCannotResurrectIt() {
        ready(2, 7); var count = new AtomicInteger(); var result = new AtomicReference<EditCommitReply>();
        exchange.commit(revision, bounds, 0, bytes -> { }, reply -> { count.incrementAndGet(); result.set(reply); });
        assertNull(client.poll(true, BridgeClient.TIMEOUT_NANOS)); exchange.complete();
        assertEquals(CommitStatus.UNKNOWN, result.get().status()); assertEquals(1, count.get());
        client.receive(BridgeCodec.encode(new EditCommitReply(epoch, 1, CommitStatus.APPLIED, snapshot)));
        for (int i = 0; i < 100_000; i++) { assertNull(client.poll(true, BridgeClient.TIMEOUT_NANOS + i)); exchange.complete(); }
        assertEquals(1, count.get()); assertEquals(CommitStatus.UNKNOWN, client.editResult().status());
    }
    @Test void teardownAndSendFailureCompleteCallbacksAndOldRepliesAreIgnored() {
        ready(2, 7); var count = new AtomicInteger();
        exchange.commit(revision, bounds, 0, bytes -> { throw new IllegalStateException("closed channel"); },
                reply -> { assertEquals(CommitStatus.UNKNOWN, reply.status()); count.incrementAndGet(); });
        assertEquals(1, count.get()); assertEquals(BridgeClient.State.DISCONNECTED, client.state());
        ready(2, 7);
        exchange.read(0, bytes -> { }, reply -> { assertFalse(reply.ready()); count.incrementAndGet(); });
        client.disconnect(); exchange.complete(); exchange.complete(); assertEquals(2, count.get());
        client.receive(BridgeCodec.encode(new EditReadReply(epoch, 1, snapshot)));
        assertNull(client.editSelection()); assertEquals(BridgeClient.State.DISCONNECTED, client.state());
    }
    @Test void olderServersAndIdlePollsNeverStartEditTraffic() {
        ready(1, 3); assertNull(client.requestEdit(0)); assertNull(client.commitEdit(revision, bounds, 0));
        ready(2, 7);
        for (int i = 0; i < 100_000; i++) assertNull(client.poll(true, i));
        assertFalse(client.pending()); assertNull(client.editSelection());
    }
    @Test void unintegratedAdaptersDoNotRequestOrAcceptEditingEvenIfServerOffersIt() {
        var metadataOnly = new BridgeClient(() -> nonce);
        metadataOnly.connect(0);
        var hello = (Hello) BridgeCodec.decode(metadataOnly.poll(true, 0), BridgeCodec.Direction.TO_SERVER);
        assertEquals(SELECTION_READ | SESSION_CLOSE, hello.capabilities());
        metadataOnly.receive(BridgeCodec.encode(new Welcome(nonce, epoch, WelcomeStatus.READY, 1, 2, 7, 256)));
        assertEquals(3, metadataOnly.capabilities()); assertNull(metadataOnly.requestEdit(1));
        assertThrows(IllegalArgumentException.class, () -> new BridgeClient(8));
    }
    @Test void overlappingOperationCannotReplacePendingCallback() {
        ready(2, 7); var reads = new AtomicInteger(); var commits = new AtomicInteger();
        exchange.read(0, bytes -> { }, reply -> { assertTrue(reply.ready()); reads.incrementAndGet(); });
        exchange.commit(revision, bounds, 1, bytes -> fail("Overlapping frame"), reply -> {
            assertEquals(CommitStatus.CONFLICT, reply.status()); commits.incrementAndGet();
        });
        assertTrue(client.pending()); assertEquals(0, reads.get()); assertEquals(1, commits.get());
        client.receive(BridgeCodec.encode(new EditReadReply(epoch, 1, snapshot)));
        exchange.complete(); exchange.complete(); assertEquals(1, reads.get());
    }
}
