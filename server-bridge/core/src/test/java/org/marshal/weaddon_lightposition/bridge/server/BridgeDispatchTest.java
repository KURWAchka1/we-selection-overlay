package org.marshal.weaddon_lightposition.bridge.server;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.marshal.weaddon_lightposition.bridge.protocol.BridgeCodec;
import org.marshal.weaddon_lightposition.bridge.protocol.BridgeMessage;
import static org.junit.jupiter.api.Assertions.*;
import static org.marshal.weaddon_lightposition.bridge.protocol.BridgeMessage.*;

class BridgeDispatchTest {
    private final AtomicLong time = new AtomicLong(1), epochs = new AtomicLong();
    private final UUID player = new UUID(1, 1), second = new UUID(1, 2);
    private final Set<UUID> allowed = new HashSet<>(Set.of(player, second));
    private final List<BridgeMessage> sent = new ArrayList<>();
    private int reads;
    private Runnable duringRead = () -> { };
    private final BridgeServer server = new BridgeServer(new BridgeServer.Host() {
        public boolean mayReadSelection(UUID id) { return allowed.contains(id); }
        public SelectionState readSelection(UUID id) {
            reads++; duringRead.run();
            return new SelectionState(SelectionStatus.READY, new Bounds(0, 0, 0, 1, 1, 1), -64, 319, 7);
        }
    }, BridgeServer.Limits.defaults(), time::get, () -> new UUID(8, epochs.incrementAndGet()));

    private Hello hello(long nonce) { return new Hello(new UUID(3, nonce), MAJOR, MINOR, SUPPORTED_CAPABILITIES); }
    private void drain() { server.drain((id, frame) -> sent.add(BridgeCodec.decode(frame, BridgeCodec.Direction.TO_CLIENT))); }
    private UUID negotiate(UUID id) {
        assertTrue(server.enqueue(id, BridgeCodec.encode(hello(id.getLeastSignificantBits()))));
        drain();
        UUID epoch = ((Welcome) sent.removeLast()).epoch();
        assertNotEquals(NO_EPOCH, epoch);
        return epoch;
    }

    @Test void inputOwnsOneDefensiveFrameAndDoesNotAccessTheHost() {
        byte[] frame = BridgeCodec.encode(hello(1));
        assertTrue(server.enqueue(player, frame));
        java.util.Arrays.fill(frame, (byte) 0);
        assertEquals(0, server.metrics().admitted()); assertEquals(0, reads);
        assertEquals(1, server.pendingCount());
        drain();
        assertEquals(new UUID(3, 1), ((Welcome) sent.getFirst()).nonce());
        assertEquals(0, server.pendingCount());
    }

    @Test void busyPeerCannotReplaceOrMoveItsExistingSlot() {
        assertTrue(server.enqueue(player, BridgeCodec.encode(hello(1))));
        assertFalse(server.enqueue(player, BridgeCodec.encode(hello(99))));
        assertTrue(server.enqueue(second, BridgeCodec.encode(hello(2))));
        List<UUID> order = new ArrayList<>();
        server.drain((id, frame) -> order.add(id));
        assertEquals(List.of(player, second), order);
        assertEquals(2, server.queueMetrics().queued()); assertEquals(2, server.queueMetrics().peakPending());
    }

    @Test void perSliceCountIsBoundedAcrossManyPendingActors() {
        for (int i = 0; i < 64; i++) assertTrue(server.enqueue(new UUID(4, i), BridgeCodec.encode(hello(i))));
        assertEquals(8, server.drain((id, frame) -> { }));
        assertEquals(56, server.pendingCount()); assertEquals(8, server.metrics().admitted());
    }

    @Test void cooperativeTimeLimitStopsAfterAnExpensiveNonPreemptibleCall() {
        BridgeServer target = new BridgeServer(new BridgeServer.Host() {
            public boolean mayReadSelection(UUID id) { time.addAndGet(700_000); return false; }
            public SelectionState readSelection(UUID id) { fail("No read expected"); return null; }
        }, BridgeServer.Limits.defaults(), time::get, UUID::randomUUID);
        target.enqueue(player, BridgeCodec.encode(hello(1))); target.enqueue(second, BridgeCodec.encode(hello(2)));
        assertEquals(1, target.drain((id, frame) -> { }));
        assertEquals(1, target.pendingCount()); assertEquals(700_000, target.queueMetrics().maxSliceNanos());
    }

    @Test void sliceDoesNotStartAnotherVisitWithoutRoomForItsObservedCost() {
        BridgeServer target = new BridgeServer(new BridgeServer.Host() {
            public boolean mayReadSelection(UUID id) { time.addAndGet(200_000); return false; }
            public SelectionState readSelection(UUID id) { fail("No read expected"); return null; }
        }, new BridgeServer.Limits(3, 1, 3, 4, 8), time::get, UUID::randomUUID);
        var third = new UUID(1, 3);
        for (UUID id : List.of(player, second, third)) target.enqueue(id, BridgeCodec.encode(hello(1)));
        var order = new ArrayList<UUID>();
        assertEquals(2, target.drain((id, frame) -> order.add(id)));
        assertEquals(List.of(player, second), order);
        assertEquals(1, target.pendingCount()); assertEquals(400_000, target.queueMetrics().maxSliceNanos());
        assertEquals(2, target.metrics().admitted());
        assertEquals(1, target.drain((id, frame) -> order.add(id)));
        assertEquals(List.of(player, second, third), order); assertEquals(0, target.pendingCount());
        assertEquals(3, target.metrics().admitted()); assertEquals(0, target.metrics().throttled());
    }

    @Test void visitReservationIncludesPublicationCost() {
        for (int i = 0; i < 3; i++) server.enqueue(new UUID(4, i), BridgeCodec.encode(hello(i)));
        assertEquals(2, server.drain((id, frame) -> time.addAndGet(200_000)));
        assertEquals(1, server.pendingCount()); assertEquals(400_000, server.queueMetrics().maxSliceNanos());
    }

    @Test void cheapVisitsUseAvailableBudgetWithoutAnUnmeasuredFloor() {
        BridgeServer target = new BridgeServer(new BridgeServer.Host() {
            public boolean mayReadSelection(UUID id) { time.addAndGet(50_000); return false; }
            public SelectionState readSelection(UUID id) { fail("No read expected"); return null; }
        }, BridgeServer.Limits.defaults(), time::get, UUID::randomUUID);
        var order = new ArrayList<UUID>();
        for (int i = 0; i < 8; i++) target.enqueue(new UUID(5, i), BridgeCodec.encode(hello(i)));
        assertEquals(8, target.drain((id, frame) -> order.add(id)));
        assertEquals(400_000, target.queueMetrics().maxSliceNanos()); assertEquals(0, target.pendingCount());
        assertEquals(0, target.drain((id, frame) -> order.add(id)));
        assertEquals(0, target.pendingCount()); assertEquals(8, target.metrics().admitted());
        for (int i=0;i<8;i++) assertEquals(new UUID(5, i), order.get(i));
    }

    @Test void expensiveVisitCannotPoisonLaterSlicesOrStopOrdinaryProgress() {
        var calls = new AtomicLong();
        BridgeServer target = new BridgeServer(new BridgeServer.Host() {
            public boolean mayReadSelection(UUID id) { if (calls.getAndIncrement() == 0) time.addAndGet(700_000); return false; }
            public SelectionState readSelection(UUID id) { fail("No read expected"); return null; }
        }, BridgeServer.Limits.defaults(), time::get, UUID::randomUUID);
        for (int i = 0; i < 10; i++) target.enqueue(new UUID(4, i), BridgeCodec.encode(hello(i)));
        assertEquals(1, target.drain((id, frame) -> { }));
        assertEquals(8, target.drain((id, frame) -> { }));
        assertEquals(1, target.drain((id, frame) -> { })); assertEquals(0, target.pendingCount());
    }

    @Test void suddenlySlowAtomicVisitRemainsVisibleAndIsNeverInterruptedOrRetried() {
        var calls = new AtomicLong();
        BridgeServer target = new BridgeServer(new BridgeServer.Host() {
            public boolean mayReadSelection(UUID id) { time.addAndGet(calls.incrementAndGet() == 2 ? 900_000 : 100_000); return false; }
            public SelectionState readSelection(UUID id) { fail("No read expected"); return null; }
        }, BridgeServer.Limits.defaults(), time::get, UUID::randomUUID);
        for (int i = 0; i < 3; i++) target.enqueue(new UUID(4, i), BridgeCodec.encode(hello(i)));
        assertEquals(2, target.drain((id, frame) -> { })); assertEquals(1_000_000, target.queueMetrics().maxSliceNanos());
        assertEquals(1, target.pendingCount()); assertEquals(1, target.drain((id, frame) -> { }));
        assertEquals(3, calls.get()); assertEquals(3, target.metrics().admitted());
    }

    @Test void visitReservationUsesElapsedDifferencesAcrossNanoTimeWrap() {
        time.set(Long.MAX_VALUE - 300_000);
        BridgeServer target = new BridgeServer(new BridgeServer.Host() {
            public boolean mayReadSelection(UUID id) { time.addAndGet(200_000); return false; }
            public SelectionState readSelection(UUID id) { fail("No read expected"); return null; }
        }, BridgeServer.Limits.defaults(), time::get, UUID::randomUUID);
        for (int i = 0; i < 3; i++) target.enqueue(new UUID(4, i), BridgeCodec.encode(hello(i)));
        assertEquals(2, target.drain((id, frame) -> { })); assertEquals(400_000, target.queueMetrics().maxSliceNanos());
        assertEquals(1, target.drain((id, frame) -> { })); assertEquals(0, target.pendingCount());
    }

    @Test void idleDrainNeverDoesWorkAndDoesNotVisitTheClock() {
        var clockCalls = new AtomicLong();
        BridgeServer target = new BridgeServer(new BridgeServer.Host() {
            public boolean mayReadSelection(UUID id) { fail("Idle permission check"); return false; }
            public SelectionState readSelection(UUID id) { fail("Idle read"); return null; }
        }, BridgeServer.Limits.defaults(), () -> { clockCalls.incrementAndGet(); return 1; }, UUID::randomUUID);
        long initial = clockCalls.get();
        for (int i = 0; i < 100_000; i++) assertEquals(0, target.drain((id, frame) -> fail("Idle send")));
        assertEquals(initial, clockCalls.get());
    }

    @Test void queuedRequestRechecksRevocationWithoutReadingProtectedState() {
        UUID epoch = negotiate(player);
        server.enqueue(player, BridgeCodec.encode(new SelectionRequest(epoch, 1)));
        allowed.remove(player); drain();
        var reply = (SelectionReply) sent.getFirst();
        assertEquals(SelectionStatus.DENIED, reply.selection().status()); assertNull(reply.selection().bounds());
        assertEquals(0, reads);
    }

    @Test void revocationDuringReadPreventsProtectedPublicationAndCaching() {
        UUID epoch = negotiate(player);
        duringRead = () -> allowed.remove(player);
        server.enqueue(player, BridgeCodec.encode(new SelectionRequest(epoch, 1))); drain();
        var reply = (SelectionReply) sent.getFirst();
        assertEquals(SelectionStatus.DENIED, reply.selection().status()); assertNull(reply.selection().bounds());
        assertEquals(1, reads);
        allowed.add(player); sent.clear();
        server.enqueue(player, BridgeCodec.encode(new SelectionRequest(epoch, 1))); drain();
        assertTrue(sent.isEmpty()); assertEquals(1, reads);
    }

    @Test void contextChangeDuringReadCannotResurrectItsOldEpoch() {
        UUID epoch = negotiate(player);
        duringRead = () -> server.resetContext(player);
        server.enqueue(player, BridgeCodec.encode(new SelectionRequest(epoch, 1))); drain();
        assertTrue(sent.isEmpty()); assertEquals(1, reads);
        assertNotEquals(epoch, negotiate(player));
    }

    @Test void disconnectContextChangeAndCloseReleasePendingSlots() {
        UUID epoch = negotiate(player);
        server.enqueue(player, BridgeCodec.encode(new SelectionRequest(epoch, 1)));
        server.resetContext(player); drain(); assertTrue(sent.isEmpty()); assertEquals(0, reads);
        server.enqueue(player, BridgeCodec.encode(hello(2)));
        server.disconnect(player); assertEquals(0, server.pendingCount());
        server.enqueue(second, BridgeCodec.encode(hello(3)));
        server.close(); assertEquals(0, server.pendingCount()); assertEquals(0, server.metrics().connections());
        assertEquals(3, server.queueMetrics().cancelled());
    }

    @Test void shutdownCancelsPendingWorkAndOnlyNotifiesNegotiatedSessions() {
        UUID epoch = negotiate(player);
        server.enqueue(player, BridgeCodec.encode(new SelectionRequest(epoch, 1)));
        server.enqueue(second, BridgeCodec.encode(hello(2)));
        server.shutdown((id, frame) -> sent.add(BridgeCodec.decode(frame, BridgeCodec.Direction.TO_CLIENT)));
        assertEquals(List.of(new SessionClosed(new UUID(3, 1), epoch)), sent);
        assertEquals(0, server.pendingCount()); assertEquals(2, server.queueMetrics().cancelled());
        assertEquals(0, reads);
    }

    @Test void deadlineExpiresBeforeDecodeOrReadAndWithoutRefillingTheGlobalBucket() {
        BridgeServer target = new BridgeServer(new BridgeServer.Host() {
            public boolean mayReadSelection(UUID id) { return true; }
            public SelectionState readSelection(UUID id) { fail("Expired read"); return null; }
        }, new BridgeServer.Limits(2, 1, 1, 4, 8), time::get, UUID::randomUUID);
        target.enqueue(player, BridgeCodec.encode(hello(1)));
        AtomicReference<UUID> epoch = new AtomicReference<>();
        target.drain((id, frame) -> epoch.set(((Welcome) BridgeCodec.decode(frame, BridgeCodec.Direction.TO_CLIENT)).epoch()));
        target.enqueue(player, BridgeCodec.encode(new SelectionRequest(epoch.get(), 1)));
        assertEquals(0, target.drain((id, frame) -> fail("Aggregate bucket exhausted")));
        time.addAndGet(5_000_000_000L);
        assertEquals(1, target.drain((id, frame) -> fail("Expired publication")));
        assertEquals(1, target.queueMetrics().expired()); assertEquals(1, target.metrics().admitted());
        assertEquals(0, target.pendingCount());
    }

    @Test void maximumConnectionsBoundStorageAndExpiredWorkEventuallyDrains() {
        for (int i = 0; i < 1024; i++) assertTrue(server.enqueue(new UUID(5, i), new byte[256]));
        assertFalse(server.enqueue(new UUID(5, 1024), new byte[256]));
        assertEquals(1024, server.pendingCount()); assertEquals(1024, server.metrics().connections());
        for (int tick = 0; tick < 200 && server.pendingCount() > 0; tick++) {
            assertTrue(server.drain((id, frame) -> fail("Malformed frame replied")) <= 8);
            time.addAndGet(50_000_000L);
        }
        assertEquals(0, server.pendingCount()); assertTrue(server.queueMetrics().expired() > 0);
        assertTrue(server.metrics().admitted() < 600); assertEquals(0, reads);
        server.close(); assertEquals(0, server.metrics().connections());
    }

    @Test void cancellingSlotsCannotRefillTheConnectionBudget() {
        for (int i = 0; i < 8; i++) {
            assertTrue(server.enqueue(player, BridgeCodec.encode(hello(i)))); server.resetContext(player);
        }
        assertFalse(server.enqueue(player, BridgeCodec.encode(hello(9))));
        assertTrue(server.enqueue(second, BridgeCodec.encode(hello(10))));
        assertEquals(0, server.metrics().admitted());
    }

    @Test void failedSenderReleasesDrainGuardAndDoesNotDiscardOtherActors() {
        server.enqueue(player, BridgeCodec.encode(hello(1))); server.enqueue(second, BridgeCodec.encode(hello(2)));
        assertThrows(IllegalStateException.class, () -> server.drain((id, frame) -> { throw new IllegalStateException("send failure"); }));
        assertEquals(1, server.pendingCount()); drain();
        assertEquals(0, server.pendingCount()); assertEquals(1, sent.size());
    }

    @Test void recursiveAndOffThreadDrainsFailBeforeWork() throws InterruptedException {
        server.enqueue(player, BridgeCodec.encode(hello(1)));
        server.drain((id, frame) -> assertThrows(IllegalStateException.class, () -> server.drain((p, bytes) -> { })));
        var failure = new AtomicReference<Throwable>();
        Thread worker = new Thread(() -> {
            try { server.drain((id, frame) -> { }); } catch (Throwable ex) { failure.set(ex); }
        });
        worker.start(); worker.join(); assertInstanceOf(IllegalStateException.class, failure.get());
        assertEquals(0, server.pendingCount());
    }

    @Test void dispatchLimitsRejectUnboundedOrInvalidSettings() {
        assertThrows(IllegalArgumentException.class, () -> new BridgeServer.DispatchLimits(0, 500_000, 5_000_000_000L));
        assertThrows(IllegalArgumentException.class, () -> new BridgeServer.DispatchLimits(257, 500_000, 5_000_000_000L));
        assertThrows(IllegalArgumentException.class, () -> new BridgeServer.DispatchLimits(8, 0, 5_000_000_000L));
        assertThrows(IllegalArgumentException.class, () -> new BridgeServer.DispatchLimits(8, 500_000, Long.MAX_VALUE));
    }
}
