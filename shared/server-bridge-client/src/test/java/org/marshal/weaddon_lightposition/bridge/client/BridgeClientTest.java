package org.marshal.weaddon_lightposition.bridge.client;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.marshal.weaddon_lightposition.bridge.protocol.BridgeCodec;
import org.marshal.weaddon_lightposition.bridge.server.BridgeServer;

import static org.junit.jupiter.api.Assertions.*;
import static org.marshal.weaddon_lightposition.bridge.protocol.BridgeMessage.*;

class BridgeClientTest {
    private final UUID player = new UUID(1, 1), nonce = new UUID(2, 2), epoch = new UUID(3, 3);
    private final BridgeClient client = new BridgeClient(() -> nonce);
    private void ready() {
        client.connect(0); assertNotNull(client.poll(true, 0));
        client.receive(BridgeCodec.encode(new Welcome(nonce, epoch, WelcomeStatus.READY, 1, 0, 1, 256)));
        assertEquals(BridgeClient.State.READY, client.state());
    }

    @Test void absenceTimeoutAndDisconnectSendNoRepeatedHello() {
        client.connect(0);
        assertNull(client.poll(false, 0));
        assertNotNull(client.poll(true, 1));
        for (int i = 2; i < 1000; i++) assertNull(client.poll(true, i));
        assertNull(client.poll(true, BridgeClient.TIMEOUT_NANOS));
        assertEquals(BridgeClient.State.UNAVAILABLE, client.state());
        assertNull(client.requestSelection(0));
        client.disconnect();
        assertNull(client.poll(true, Long.MAX_VALUE));
        assertEquals(BridgeClient.State.DISCONNECTED, client.state());
    }

    @Test void deniedIncompatibleAndWrongNonceAreSafeFallbacks() {
        client.connect(0); client.poll(true, 0);
        client.receive(BridgeCodec.encode(new Welcome(UUID.randomUUID(), epoch, WelcomeStatus.READY, 1, 0, 1, 256)));
        assertEquals(BridgeClient.State.NEGOTIATING, client.state());
        client.receive(BridgeCodec.encode(new Welcome(nonce, NO_EPOCH, WelcomeStatus.DENIED, 1, 0, 0, 256)));
        assertEquals(BridgeClient.State.DENIED, client.state());
        assertNull(client.requestSelection(1));
        client.connect(0); client.poll(true, 0);
        client.receive(BridgeCodec.encode(new Welcome(nonce, NO_EPOCH, WelcomeStatus.INCOMPATIBLE, 2, 0, 0, 256)));
        assertEquals(BridgeClient.State.INCOMPATIBLE, client.state());
    }

    @Test void onlyMatchingResponseCompletesPendingRequest() {
        ready();
        assertNotNull(client.requestSelection(10));
        assertNull(client.requestSelection(11));
        client.receive(BridgeCodec.encode(new SelectionReply(UUID.randomUUID(), 1, SelectionState.unavailable(SelectionStatus.EMPTY))));
        client.receive(BridgeCodec.encode(new SelectionReply(epoch, 2, SelectionState.unavailable(SelectionStatus.EMPTY))));
        assertTrue(client.pending());
        client.receive(BridgeCodec.encode(new SelectionReply(epoch, 1, SelectionState.unavailable(SelectionStatus.EMPTY))));
        assertFalse(client.pending());
        assertEquals(SelectionStatus.EMPTY, client.selection().status());
        client.disconnect();
        assertNull(client.selection());
        assertEquals(0, client.capabilities());
    }

    @Test void requestTimeoutDoesNotRetryAndLateResponseCannotOverwriteNewerState() {
        ready(); client.requestSelection(0);
        client.poll(true, BridgeClient.TIMEOUT_NANOS);
        assertFalse(client.pending());
        assertEquals(SelectionStatus.FAILED, client.selection().status());
        client.requestSelection(BridgeClient.TIMEOUT_NANOS + 1);
        client.receive(BridgeCodec.encode(new SelectionReply(epoch, 1, SelectionState.unavailable(SelectionStatus.EMPTY))));
        assertTrue(client.pending());
        client.receive(BridgeCodec.encode(new SelectionReply(epoch, 2, SelectionState.unavailable(SelectionStatus.DENIED))));
        assertEquals(BridgeClient.State.DENIED, client.state());
        assertNull(client.requestSelection(BridgeClient.TIMEOUT_NANOS + 2));
    }

    @Test void malformedOrUnsolicitedFramesDoNotDisconnectOrExposeState() {
        ready();
        client.receive(new byte[257]); client.receive(new byte[1]);
        client.receive(BridgeCodec.encode(new SelectionReply(epoch, 1, SelectionState.unavailable(SelectionStatus.EMPTY))));
        assertEquals(BridgeClient.State.READY, client.state());
        assertNull(client.selection());
        assertNull(client.poll(true, Long.MAX_VALUE));
    }

    @Test void insufficientFrameBudgetDoesNotNegotiateAnUnsendableCapability() {
        client.connect(0); client.poll(true, 0);
        client.receive(BridgeCodec.encode(new Welcome(nonce, epoch, WelcomeStatus.READY, 1, 0, 1, 6)));
        assertEquals(BridgeClient.State.INCOMPATIBLE, client.state());
        assertNull(client.requestSelection(1));
    }

    @Test void fullEncodedRoundTripThenIdleDoesNoSourceWork() {
        AtomicLong time = new AtomicLong();
        AtomicLong reads = new AtomicLong();
        BridgeServer server = new BridgeServer(new BridgeServer.Host() {
            public boolean mayReadSelection(UUID id) { return id.equals(player); }
            public SelectionState readSelection(UUID id) {
                reads.incrementAndGet();
                return new SelectionState(SelectionStatus.READY, new Bounds(-16, -64, 1, 31, 10, 33), -64, 319, 5);
            }
        }, BridgeServer.Limits.defaults(), time::get, () -> epoch);
        client.connect(0);
        client.receive(server.receive(player, client.poll(true, 0)));
        assertEquals(BridgeClient.State.READY, client.state());
        assertEquals(0, reads.get());
        client.receive(server.receive(player, client.requestSelection(0)));
        assertEquals(new Bounds(-16, -64, 1, 31, 10, 33), client.selection().bounds());
        for (int i = 0; i < 100_000; i++) assertNull(client.poll(true, time.addAndGet(50_000_000L)));
        assertEquals(1, reads.get());
        assertEquals(2, server.metrics().admitted());
        assertEquals(1, server.metrics().selectionReads());
        assertEquals(2, server.metrics().replies());
    }
}
