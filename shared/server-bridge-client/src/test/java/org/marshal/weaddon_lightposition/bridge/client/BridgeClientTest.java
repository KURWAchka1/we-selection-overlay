package org.marshal.weaddon_lightposition.bridge.client;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.marshal.weaddon_lightposition.bridge.protocol.BridgeCodec;
import org.marshal.weaddon_lightposition.bridge.protocol.BridgeRestartHint;
import org.marshal.weaddon_lightposition.bridge.server.BridgeServer;

import static org.junit.jupiter.api.Assertions.*;
import static org.marshal.weaddon_lightposition.bridge.protocol.BridgeMessage.*;

class BridgeClientTest {
    private final UUID player = new UUID(1, 1), nonce = new UUID(2, 2), epoch = new UUID(3, 3);
    private final BridgeClient client = new BridgeClient(() -> nonce);

    private byte[] exchange(BridgeServer server, UUID id, byte[] frame) {
        var reply = new AtomicReference<byte[]>();
        server.enqueue(id, frame);
        server.drain((player, bytes) -> { assertEquals(id, player); reply.set(bytes); });
        return reply.get();
    }

    @Test void restartHintRenewsOnceAfterCloseAndCannotResurrectOldSession() {
        var serial = new AtomicLong();
        var local = new BridgeClient(() -> new UUID(2, serial.incrementAndGet()));
        local.connect(0);
        for (int cycle = 1; cycle <= 4; cycle++) {
            Hello hello = (Hello) BridgeCodec.decode(local.poll(true, cycle), BridgeCodec.Direction.TO_SERVER);
            UUID currentEpoch = new UUID(3, cycle);
            byte[] welcome = BridgeCodec.encode(new Welcome(hello.nonce(), currentEpoch, WelcomeStatus.READY,
                    MAJOR, MINOR, SUPPORTED_CAPABILITIES, 256));
            local.receive(welcome);
            local.requestSelection(cycle);
            byte[] reply = BridgeCodec.encode(new SelectionReply(currentEpoch, 1, SelectionState.unavailable(SelectionStatus.EMPTY)));
            if (cycle % 2 == 0) local.receive(reply);
            byte[] closed = BridgeCodec.encode(new SessionClosed(hello.nonce(), currentEpoch));
            local.receive(closed);
            local.receiveRestartHint(new byte[22], cycle);
            if (cycle > 1) local.receiveRestartHint(BridgeRestartHint.encode(new UUID(4, cycle - 1)), cycle);
            assertEquals(BridgeClient.State.UNAVAILABLE, local.state());
            assertNull(local.poll(true, cycle));
            byte[] hint = BridgeRestartHint.encode(new UUID(4, cycle));
            local.receiveRestartHint(hint, cycle);
            assertEquals(BridgeClient.State.DISCOVERING, local.state());
            assertNull(local.selection()); assertFalse(local.pending()); assertEquals(0, local.capabilities());
            local.receive(welcome); local.receive(reply); local.receive(closed);
            for (int i = 0; i < 10_000; i++) local.receiveRestartHint(hint, cycle);
            // Leave the fresh one-shot hello for the next cycle; no read is implied.
        }
        assertEquals(4, serial.get() - 1);
        local.disconnect();
        local.receiveRestartHint(BridgeRestartHint.encode(new UUID(4, 99)), 10);
        assertEquals(BridgeClient.State.DISCONNECTED, local.state());
    }

    @Test void restartHintDoesNotRetryDenialAbsenceTimeoutOrNonNegotiatedStop() {
        byte[] hint = BridgeRestartHint.encode(new UUID(4, 1));
        client.connect(0);
        client.receiveRestartHint(hint, 0);
        client.poll(true, 0);
        client.receiveRestartHint(hint, 0);
        assertNull(client.poll(true, BridgeClient.TIMEOUT_NANOS));
        for (int i = 0; i < 10_000; i++) {
            client.receiveRestartHint(BridgeRestartHint.encode(new UUID(4, i + 1)), i);
            assertNull(client.poll(true, i));
        }
        assertEquals(BridgeClient.State.UNAVAILABLE, client.state());
        client.connect(0); client.poll(true, 0);
        client.receive(BridgeCodec.encode(new Welcome(nonce, NO_EPOCH, WelcomeStatus.DENIED, 1, 1, 0, 256)));
        client.receiveRestartHint(hint, 1);
        assertEquals(BridgeClient.State.DENIED, client.state());
        ready(); client.receive(BridgeCodec.encode(new SessionClosed(nonce, epoch)));
        client.receiveRestartHint(hint, 1);
        assertEquals(BridgeClient.State.READY, client.state());
    }

    @Test void failedRestartConsumesArmUntilAnotherNegotiatedCloseAndContextLossClearsIt() {
        client.connect(0); client.poll(true, 0);
        client.receive(BridgeCodec.encode(new Welcome(nonce, epoch, WelcomeStatus.READY, 1, 1, 3, 256)));
        byte[] close = BridgeCodec.encode(new SessionClosed(nonce, epoch));
        client.receive(close);
        client.receiveRestartHint(BridgeRestartHint.encode(new UUID(4, 1)), 1);
        assertNotNull(client.poll(true, 1));
        client.receive(close);
        client.poll(true, BridgeClient.TIMEOUT_NANOS + 1);
        client.receiveRestartHint(BridgeRestartHint.encode(new UUID(4, 2)), BridgeClient.TIMEOUT_NANOS + 2);
        assertEquals(BridgeClient.State.UNAVAILABLE, client.state());
        assertNull(client.poll(true, BridgeClient.TIMEOUT_NANOS + 2));
        client.connect(0); client.poll(true, 0);
        client.receive(BridgeCodec.encode(new Welcome(nonce, epoch, WelcomeStatus.READY, 1, 1, 3, 256)));
        client.receive(close); client.poll(false, 1);
        client.receiveRestartHint(BridgeRestartHint.encode(new UUID(4, 3)), 2);
        assertEquals(BridgeClient.State.UNAVAILABLE, client.state());
        assertNotNull(client.poll(true, 3));
    }

    @Test void restartedServerRechecksPermissionAndDoesNotReuseOldGrantsOrMetadata() {
        var serial = new AtomicLong();
        var local = new BridgeClient(() -> new UUID(2, serial.incrementAndGet()));
        BridgeServer.Host allowed = new BridgeServer.Host() {
            public boolean mayReadSelection(UUID id) { return true; }
            public SelectionState readSelection(UUID id) { return SelectionState.unavailable(SelectionStatus.EMPTY); }
        };
        var old = new BridgeServer(allowed, BridgeServer.Limits.defaults(), () -> 0, () -> epoch);
        local.connect(0);
        byte[] oldWelcome = exchange(old, player, local.poll(true, 0));
        local.receive(oldWelcome);
        byte[] oldReply = exchange(old, player, local.requestSelection(0));
        local.receive(oldReply);
        old.shutdown((id, frame) -> local.receive(frame));
        var replacement = new BridgeServer(new BridgeServer.Host() {
            public boolean mayReadSelection(UUID id) { return false; }
            public SelectionState readSelection(UUID id) { fail("Denied restart read a session"); return null; }
        }, BridgeServer.Limits.defaults(), () -> 0, () -> new UUID(3, 4));
        byte[] hint = BridgeRestartHint.encode(new UUID(4, 1));
        local.receiveRestartHint(hint, 1);
        local.receive(oldWelcome); local.receive(oldReply);
        assertEquals(BridgeClient.State.DISCOVERING, local.state());
        local.receive(exchange(replacement, player, local.poll(true, 1)));
        assertEquals(BridgeClient.State.DENIED, local.state());
        assertNull(local.selection()); assertFalse(local.pending()); assertEquals(0, local.capabilities());
        for (int i = 0; i < 10_000; i++) {
            local.receiveRestartHint(hint, i); local.receive(oldWelcome); local.receive(oldReply);
            assertNull(local.poll(true, i)); assertNull(local.requestSelection(i));
        }
        assertEquals(1, replacement.metrics().admitted());
        assertEquals(0, replacement.metrics().selectionReads());
    }

    @Test void saturatedRestartFailsClosedWithoutBypassingLimitsOrRetryingAfterCapacityReturns() {
        client.connect(0); client.poll(true, 0);
        client.receive(BridgeCodec.encode(new Welcome(nonce, epoch, WelcomeStatus.READY, 1, 1, 3, 256)));
        client.receive(BridgeCodec.encode(new SessionClosed(nonce, epoch)));
        var replacement = new BridgeServer(new BridgeServer.Host() {
            public boolean mayReadSelection(UUID id) { return true; }
            public SelectionState readSelection(UUID id) { fail("Recovery must not read metadata"); return null; }
        }, new BridgeServer.Limits(1, 1, 1, 1, 1), () -> 0, () -> new UUID(3, 4));
        UUID other = new UUID(5, 1);
        assertNotNull(exchange(replacement, other, BridgeCodec.encode(new Hello(new UUID(5, 2), 1, 1, 3))));
        client.receiveRestartHint(BridgeRestartHint.encode(new UUID(4, 1)), 1);
        assertNull(exchange(replacement, player, client.poll(true, 1)), "Recovery bypassed bounded admission");
        replacement.disconnect(other);
        client.poll(true, BridgeClient.TIMEOUT_NANOS + 1);
        for (int i = 0; i < 10_000; i++) {
            client.receiveRestartHint(BridgeRestartHint.encode(new UUID(4, i + 2)), BridgeClient.TIMEOUT_NANOS + 2 + i);
            assertNull(client.poll(true, BridgeClient.TIMEOUT_NANOS + 2 + i));
        }
        assertEquals(BridgeClient.State.UNAVAILABLE, client.state());
        assertNull(client.selection()); assertEquals(0, client.capabilities());
        assertEquals(0, replacement.metrics().connections());
        assertEquals(1, replacement.metrics().admitted());
        assertEquals(0, replacement.metrics().selectionReads());
    }
    private void ready() {
        client.connect(0); assertNotNull(client.poll(true, 0));
        client.receive(BridgeCodec.encode(new Welcome(nonce, epoch, WelcomeStatus.READY, 1, 0, 1, 256)));
        assertEquals(BridgeClient.State.READY, client.state());
    }

    @Test void delayedQueueRepliesCompleteExistingClientWithoutRetriesOrProtocolChanges() {
        var time = new AtomicLong();
        var reads = new AtomicLong();
        var server = new BridgeServer(new BridgeServer.Host() {
            public boolean mayReadSelection(UUID id) { return true; }
            public SelectionState readSelection(UUID id) { reads.incrementAndGet(); return SelectionState.unavailable(SelectionStatus.EMPTY); }
        }, BridgeServer.Limits.defaults(), time::get, () -> epoch);
        client.connect(0);
        assertTrue(server.enqueue(player, client.poll(true, 0)));
        time.set(500_000_000L); assertNull(client.poll(true, time.get()));
        assertEquals(BridgeClient.State.NEGOTIATING, client.state());
        server.drain((id, frame) -> client.receive(frame));
        assertEquals(BridgeClient.State.READY, client.state()); assertEquals(0, reads.get());
        assertTrue(server.enqueue(player, client.requestSelection(time.get())));
        time.addAndGet(500_000_000L); assertNull(client.poll(true, time.get())); assertTrue(client.pending());
        server.drain((id, frame) -> client.receive(frame));
        assertFalse(client.pending()); assertEquals(SelectionStatus.EMPTY, client.selection().status());
        assertEquals(1, reads.get()); assertEquals(0, server.pendingCount());
    }

    @Test void expiredQueuedNegotiationUsesExistingTimeoutWithoutImplicitRetry() {
        var time = new AtomicLong();
        var server = new BridgeServer(new BridgeServer.Host() {
            public boolean mayReadSelection(UUID id) { fail("Expired hello authorization"); return false; }
            public SelectionState readSelection(UUID id) { fail("Expired read"); return null; }
        }, BridgeServer.Limits.defaults(), time::get, UUID::randomUUID);
        client.connect(0); assertTrue(server.enqueue(player, client.poll(true, 0)));
        time.set(5_000_000_000L); server.drain((id, frame) -> fail("Expired welcome"));
        assertEquals(1, server.queueMetrics().expired()); assertEquals(0, server.pendingCount());
        assertNull(client.poll(true, BridgeClient.TIMEOUT_NANOS));
        assertEquals(BridgeClient.State.UNAVAILABLE, client.state());
        for (int i = 0; i < 10_000; i++) assertNull(client.poll(true, BridgeClient.TIMEOUT_NANOS + i));
        assertEquals(0, server.metrics().admitted());
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

    @Test void reconnectClearsPendingMetadataAndRejectsPreviousEpoch() {
        ready();
        client.requestSelection(0);
        client.receive(BridgeCodec.encode(new SelectionReply(epoch, 1,
                new SelectionState(SelectionStatus.READY, new Bounds(1, 2, 3, 4, 5, 6), -64, 319, 7))));
        assertNotNull(client.selection());
        client.requestSelection(1);
        client.connect(2);
        assertNull(client.selection());
        assertFalse(client.pending());
        assertEquals(0, client.capabilities());
        client.poll(true, 2);
        UUID renewed = new UUID(3, 4);
        client.receive(BridgeCodec.encode(new Welcome(nonce, renewed, WelcomeStatus.READY, 1, 0, 1, 256)));
        client.requestSelection(3);
        client.receive(BridgeCodec.encode(new SelectionReply(epoch, 1, SelectionState.unavailable(SelectionStatus.EMPTY))));
        assertTrue(client.pending());
        assertNull(client.selection());
        client.receive(BridgeCodec.encode(new SelectionReply(renewed, 1, SelectionState.unavailable(SelectionStatus.EMPTY))));
        assertFalse(client.pending());
        assertEquals(SelectionStatus.EMPTY, client.selection().status());
    }

    @Test void renewedContextRejectsDelayedWelcomeAndReplyWithoutBackgroundReads() {
        var counter = new AtomicLong();
        var local = new BridgeClient(() -> new UUID(2, counter.incrementAndGet()));
        var reads = new AtomicLong();
        var epochs = new AtomicLong();
        var server = new BridgeServer(new BridgeServer.Host() {
            public boolean mayReadSelection(UUID id) { return true; }
            public SelectionState readSelection(UUID id) {
                reads.incrementAndGet();
                return SelectionState.unavailable(SelectionStatus.EMPTY);
            }
        }, BridgeServer.Limits.defaults(), System::nanoTime, () -> new UUID(3, epochs.incrementAndGet()));
        local.connect(0);
        byte[] oldWelcome = exchange(server, player, local.poll(true, 0));
        local.receive(oldWelcome);
        byte[] oldReply = exchange(server, player, local.requestSelection(1));
        server.resetContext(player);
        local.connect(2);
        assertFalse(local.pending()); assertNull(local.selection()); assertEquals(0, local.capabilities());
        byte[] hello = local.poll(true, 2);
        local.receive(oldWelcome); local.receive(oldReply);
        assertEquals(BridgeClient.State.NEGOTIATING, local.state());
        local.receive(exchange(server, player, hello));
        assertEquals(BridgeClient.State.READY, local.state());
        assertEquals(1, reads.get(), "Renewal must not read selection");
        byte[] request = local.requestSelection(3);
        local.receive(oldReply);
        assertTrue(local.pending()); assertNull(local.selection());
        local.receive(exchange(server, player, request));
        assertFalse(local.pending()); assertEquals(SelectionStatus.EMPTY, local.selection().status());
        for (int i = 0; i < 100_000; i++) assertNull(local.poll(true, i + 4));
        assertEquals(2, reads.get());
        assertEquals(4, server.metrics().admitted());
    }

    @Test void discoveryAndRequestTimeoutsWorkAcrossMonotonicClockSignWrap() {
        long start = Long.MAX_VALUE - 1_000_000_000L;
        client.connect(start);
        assertNotNull(client.poll(true, start));
        assertNull(client.poll(true, start + BridgeClient.TIMEOUT_NANOS - 1));
        assertEquals(BridgeClient.State.NEGOTIATING, client.state());
        assertNull(client.poll(true, start + BridgeClient.TIMEOUT_NANOS));
        assertEquals(BridgeClient.State.UNAVAILABLE, client.state());
        assertNull(client.poll(true, start + BridgeClient.TIMEOUT_NANOS + 1));
        ready();
        assertNotNull(client.requestSelection(start));
        client.poll(true, start + BridgeClient.TIMEOUT_NANOS - 1);
        assertTrue(client.pending());
        client.poll(true, start + BridgeClient.TIMEOUT_NANOS);
        assertFalse(client.pending());
        assertEquals(SelectionStatus.FAILED, client.selection().status());
        assertEquals(BridgeClient.State.READY, client.state());
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
        client.receive(exchange(server, player, client.poll(true, 0)));
        assertEquals(BridgeClient.State.READY, client.state());
        assertEquals(0, reads.get());
        client.receive(exchange(server, player, client.requestSelection(0)));

        assertEquals(new Bounds(-16, -64, 1, 31, 10, 33), client.selection().bounds());
        for (int i = 0; i < 100_000; i++) assertNull(client.poll(true, time.addAndGet(50_000_000L)));
        assertEquals(1, reads.get());
        assertEquals(2, server.metrics().admitted());
        assertEquals(1, server.metrics().selectionReads());
        assertEquals(2, server.metrics().replies());
    }

    @Test void channelLossClearsMetadataAndPendingWorkAndReadvertisementRenewsOnce() {
        var serial = new AtomicLong();
        var local = new BridgeClient(() -> new UUID(2, serial.incrementAndGet()));
        for (boolean pending : new boolean[]{false, true}) {
            local.connect(0);
            Hello oldHello = (Hello) BridgeCodec.decode(local.poll(true, 0), BridgeCodec.Direction.TO_SERVER);
            byte[] oldWelcome = BridgeCodec.encode(new Welcome(oldHello.nonce(), epoch, WelcomeStatus.READY, 1, 0, 1, 256));
            local.receive(oldWelcome);
            local.requestSelection(1);
            byte[] oldReply = BridgeCodec.encode(new SelectionReply(epoch, 1,
                    new SelectionState(SelectionStatus.READY, new Bounds(1, 2, 3, 4, 5, 6), -64, 319, 7)));
            if (!pending) local.receive(oldReply);
            assertNull(local.poll(false, 2));
            assertEquals(BridgeClient.State.UNAVAILABLE, local.state());
            assertNull(local.selection()); assertFalse(local.pending()); assertEquals(0, local.capabilities());
            for (int i = 3; i < 10_000; i++) {
                local.receive(oldWelcome); local.receive(oldReply);
                assertNull(local.poll(false, i)); assertNull(local.requestSelection(i));
            }
            Hello renewed = (Hello) BridgeCodec.decode(local.poll(true, 10_000), BridgeCodec.Direction.TO_SERVER);
            assertNotEquals(oldHello.nonce(), renewed.nonce());
            local.receive(oldWelcome); local.receive(oldReply);
            assertEquals(BridgeClient.State.NEGOTIATING, local.state());
            local.receive(BridgeCodec.encode(new Welcome(renewed.nonce(), new UUID(4, serial.get()),
                    WelcomeStatus.READY, 1, 0, 1, 256)));
            for (int i = 10_001; i < 100_000; i++) assertNull(local.poll(true, i));
            assertEquals(BridgeClient.State.READY, local.state());
            assertNull(local.selection()); assertFalse(local.pending());
        }
    }

    @Test void lateAdvertisementRecoversAbsenceButContinuousAdvertisementNeverRetriesDenialOrTimeout() {
        client.connect(0);
        assertNull(client.poll(false, BridgeClient.TIMEOUT_NANOS));
        assertEquals(BridgeClient.State.UNAVAILABLE, client.state());
        assertNotNull(client.poll(true, BridgeClient.TIMEOUT_NANOS + 1));
        client.receive(BridgeCodec.encode(new Welcome(nonce, NO_EPOCH, WelcomeStatus.DENIED, 1, 0, 0, 256)));
        for (int i = 0; i < 10_000; i++) assertNull(client.poll(true, BridgeClient.TIMEOUT_NANOS * 2 + i));
        assertEquals(BridgeClient.State.DENIED, client.state());
        client.poll(false, BridgeClient.TIMEOUT_NANOS * 3);
        assertEquals(BridgeClient.State.UNAVAILABLE, client.state());
        assertNotNull(client.poll(true, BridgeClient.TIMEOUT_NANOS * 3 + 1));
        assertNull(client.poll(true, BridgeClient.TIMEOUT_NANOS * 4 + 1));
        assertEquals(BridgeClient.State.UNAVAILABLE, client.state());
        for (int i = 0; i < 10_000; i++) assertNull(client.poll(true, BridgeClient.TIMEOUT_NANOS * 5 + i));
        assertNull(client.requestSelection(BridgeClient.TIMEOUT_NANOS * 6));
        client.disconnect();
        assertNull(client.poll(false, 0)); assertNull(client.poll(true, 1));
        assertEquals(BridgeClient.State.DISCONNECTED, client.state());
    }

    @Test void negotiatedShutdownClearsCachedOrPendingStateAndNeverLoopsOnStaleAdvertisement() {
        var serial = new AtomicLong();
        var local = new BridgeClient(() -> new UUID(2, serial.incrementAndGet()));
        for (boolean pending : new boolean[]{false, true}) {
            local.connect(0);
            Hello hello = (Hello) BridgeCodec.decode(local.poll(true, 0), BridgeCodec.Direction.TO_SERVER);
            byte[] welcome = BridgeCodec.encode(new Welcome(hello.nonce(), epoch, WelcomeStatus.READY,
                    MAJOR, MINOR, SUPPORTED_CAPABILITIES, 256));
            local.receive(welcome); local.requestSelection(1);
            byte[] reply = BridgeCodec.encode(new SelectionReply(epoch, 1,
                    new SelectionState(SelectionStatus.READY, new Bounds(1, 2, 3, 4, 5, 6), -64, 319, 7)));
            if (!pending) local.receive(reply);
            byte[] closed = BridgeCodec.encode(new SessionClosed(hello.nonce(), epoch));
            local.receive(closed);
            assertEquals(BridgeClient.State.UNAVAILABLE, local.state());
            assertNull(local.selection()); assertFalse(local.pending()); assertEquals(0, local.capabilities());
            for (int i = 2; i < 10_000; i++) {
                local.receive(welcome); local.receive(reply); local.receive(closed);
                assertNull(local.poll(true, i)); assertNull(local.requestSelection(i));
            }
            local.poll(false, 10_000);
            Hello renewed = (Hello) BridgeCodec.decode(local.poll(true, 10_001), BridgeCodec.Direction.TO_SERVER);
            assertNotEquals(hello.nonce(), renewed.nonce());
            local.receive(closed);
            assertEquals(BridgeClient.State.NEGOTIATING, local.state());
            local.receive(BridgeCodec.encode(new Welcome(renewed.nonce(), new UUID(3, serial.get() + 10),
                    WelcomeStatus.READY, MAJOR, MINOR, SUPPORTED_CAPABILITIES, 256)));
            local.receive(closed);
            assertEquals(BridgeClient.State.READY, local.state());
        }
    }

    @Test void unsolicitedWrongNonceWrongEpochAndNonNegotiatedShutdownAreIgnored() {
        byte[] closed = BridgeCodec.encode(new SessionClosed(nonce, epoch));
        client.receive(closed); assertEquals(BridgeClient.State.DISCONNECTED, client.state());
        client.connect(0); client.poll(true, 0); client.receive(closed);
        assertEquals(BridgeClient.State.NEGOTIATING, client.state());
        for (int minor : new int[]{0, 1}) {
            client.connect(0); client.poll(true, 0);
            client.receive(BridgeCodec.encode(new Welcome(nonce, epoch, WelcomeStatus.READY, MAJOR, minor, SELECTION_READ, 256)));
            client.receive(closed);
            assertEquals(BridgeClient.State.READY, client.state(), "Server never negotiated close notifications");
        }
        client.connect(0); client.poll(true, 0);
        client.receive(BridgeCodec.encode(new Welcome(nonce, epoch, WelcomeStatus.READY,
                MAJOR, MINOR, SUPPORTED_CAPABILITIES, 256)));
        client.requestSelection(1);
        client.receive(BridgeCodec.encode(new SessionClosed(UUID.randomUUID(), epoch)));
        client.receive(BridgeCodec.encode(new SessionClosed(nonce, UUID.randomUUID())));
        assertEquals(BridgeClient.State.READY, client.state()); assertTrue(client.pending());
        client.receive(closed);
        assertEquals(BridgeClient.State.UNAVAILABLE, client.state()); assertFalse(client.pending());
        client.disconnect(); client.receive(closed);
        assertEquals(BridgeClient.State.DISCONNECTED, client.state());
    }
}
