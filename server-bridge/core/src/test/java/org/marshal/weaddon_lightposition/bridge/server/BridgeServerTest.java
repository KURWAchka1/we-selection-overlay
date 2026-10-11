package org.marshal.weaddon_lightposition.bridge.server;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.marshal.weaddon_lightposition.bridge.protocol.BridgeCodec;
import org.marshal.weaddon_lightposition.bridge.protocol.BridgeMessage;

import static org.junit.jupiter.api.Assertions.*;
import static org.marshal.weaddon_lightposition.bridge.protocol.BridgeMessage.*;

class BridgeServerTest {
    private final AtomicLong time = new AtomicLong(1), epochIds = new AtomicLong();
    private final Set<UUID> allowed = new HashSet<>();
    private final UUID player = new UUID(1, 1), second = new UUID(1, 2), nonce = new UUID(3, 1);
    private int reads;
    private UUID lastReader;
    private final BridgeServer.Host host = new BridgeServer.Host() {
        public boolean mayReadSelection(UUID id) { return allowed.contains(id); }
        public SelectionState readSelection(UUID id) {
            reads++; lastReader = id;
            return new SelectionState(SelectionStatus.READY, new Bounds(0, 0, 0, 10, 10, 10), -64, 319, 7);
        }
    };
    private final BridgeServer server = server(BridgeServer.Limits.defaults());

    private BridgeServer server(BridgeServer.Limits limits) {
        return new BridgeServer(host, limits, time::get, () -> new UUID(8, epochIds.incrementAndGet()));
    }
    private BridgeMessage exchange(BridgeServer target, UUID player, BridgeMessage message) {
        byte[] reply = target.receive(player, BridgeCodec.encode(message));
        return reply == null ? null : BridgeCodec.decode(reply, BridgeCodec.Direction.TO_CLIENT);
    }
    private Welcome hello(UUID player) {
        return (Welcome) exchange(server, player, new Hello(nonce, 1, 0, 1));
    }

    @Test void handshakeDoesNotReadWorldEditAndMissingPermissionDoesNotLeak() {
        assertEquals(WelcomeStatus.DENIED, hello(player).status());
        allowed.add(player);
        assertEquals(WelcomeStatus.READY, hello(player).status());
        assertEquals(0, reads);
        assertEquals(0, server.metrics().selectionReads());
        assertNull(exchange(server, second, new SelectionRequest(new UUID(8, 1), 1)));
    }

    @Test void readsOnlyConnectionOwnerAndRechecksRevocationBeforeCachedReply() {
        allowed.add(player); allowed.add(second);
        Welcome a = hello(player), b = hello(second);
        assertNotEquals(a.epoch(), b.epoch());
        assertNull(exchange(server, second, new SelectionRequest(a.epoch(), 1)));
        SelectionReply reply = (SelectionReply) exchange(server, player, new SelectionRequest(a.epoch(), 1));
        assertEquals(player, lastReader);
        assertEquals(SelectionStatus.READY, reply.selection().status());
        assertEquals(reply, exchange(server, player, new SelectionRequest(a.epoch(), 1)));
        assertEquals(1, reads);
        allowed.remove(player);
        SelectionReply denied = (SelectionReply) exchange(server, player, new SelectionRequest(a.epoch(), 1));
        assertEquals(SelectionStatus.DENIED, denied.selection().status());
        assertNull(denied.selection().bounds());
        assertEquals(1, reads);
    }

    @Test void contextResetAndNewHelloInvalidateOldEpochs() {
        allowed.add(player);
        Welcome original = hello(player);
        assertEquals(original.epoch(), hello(player).epoch());
        Welcome changed = (Welcome) exchange(server, player, new Hello(new UUID(3, 2), 1, 0, 1));
        assertNotEquals(original.epoch(), changed.epoch());
        assertNull(exchange(server, player, new SelectionRequest(original.epoch(), 1)));
        server.disconnect(player);
        assertEquals(0, server.metrics().connections());
        assertNull(exchange(server, player, new SelectionRequest(changed.epoch(), 1)));
        server.close();
        assertEquals(0, server.metrics().connections());
    }

    @Test void unsupportedVersionOrCapabilityCannotEnableOperations() {
        allowed.add(player);
        Welcome wrong = (Welcome) exchange(server, player, new Hello(nonce, 2, 0, 1));
        assertEquals(WelcomeStatus.INCOMPATIBLE, wrong.status());
        assertEquals(0, wrong.capabilities());
        Welcome unknown = (Welcome) exchange(server, player, new Hello(nonce, 1, 0, 2));
        assertEquals(WelcomeStatus.DENIED, unknown.status());
        Welcome future = (Welcome) exchange(server, player, new Hello(nonce, 1, 65535, -1));
        assertEquals(0, future.minor());
        assertEquals(SELECTION_READ, future.capabilities());
    }

    @Test void aggregateRateIsNotMultipliedByPlayerCount() {
        BridgeServer limited = server(new BridgeServer.Limits(10, 2, 2, 4, 4));
        allowed.add(player); allowed.add(second);
        assertNotNull(exchange(limited, player, new Hello(nonce, 1, 0, 1)));
        assertNotNull(exchange(limited, second, new Hello(nonce, 1, 0, 1)));
        assertNull(exchange(limited, player, new Hello(nonce, 1, 0, 1)));
        assertEquals(2, limited.metrics().admitted());
        assertEquals(1, limited.metrics().throttled());
        time.addAndGet(500_000_000L);
        assertNotNull(exchange(limited, player, new Hello(nonce, 1, 0, 1)));
        assertEquals(0, reads);
    }

    @Test void spamIsBoundedBeforeParsingAndDoesNotUseOtherPlayersEntireBudget() {
        allowed.add(player); allowed.add(second);
        for (int i = 0; i < 10_000; i++) server.receive(player, new byte[30]);
        assertEquals(8, server.metrics().admitted());
        assertEquals(8, server.metrics().malformed());
        assertEquals(9992, server.metrics().throttled());
        assertNotNull(hello(second));
        assertEquals(0, reads);
    }

    @Test void peersAndInvalidFramesHaveBoundedStorage() {
        BridgeServer limited = server(new BridgeServer.Limits(1, 10, 10, 4, 4));
        limited.receive(player, new byte[256]);
        limited.receive(second, new byte[256]);
        assertEquals(1, limited.metrics().connections());
        limited.receive(second, new byte[257]);
        assertEquals(1, limited.metrics().connections());
        assertEquals(0, limited.metrics().selectionReads());
        limited.disconnect(player);
        limited.receive(second, new byte[256]);
        assertEquals(1, limited.metrics().connections());
    }

    @Test void oldRequestIdsAreNotReexecuted() {
        allowed.add(player); Welcome welcome = hello(player);
        assertNotNull(exchange(server, player, new SelectionRequest(welcome.epoch(), 2)));
        assertNull(exchange(server, player, new SelectionRequest(welcome.epoch(), 1)));
        assertEquals(1, reads);
    }

    @Test void denialDuringHostReadInvalidatesEpochEvenAfterPermissionReturns() {
        AtomicReference<SelectionStatus> status = new AtomicReference<>(SelectionStatus.DENIED);
        BridgeServer target = new BridgeServer(new BridgeServer.Host() {
            public boolean mayReadSelection(UUID id) { return true; }
            public SelectionState readSelection(UUID id) { return SelectionState.unavailable(status.get()); }
        }, BridgeServer.Limits.defaults(), time::get, () -> new UUID(8, epochIds.incrementAndGet()));
        Welcome original = (Welcome) exchange(target, player, new Hello(nonce, 1, 0, 1));
        SelectionReply denied = (SelectionReply) exchange(target, player, new SelectionRequest(original.epoch(), 1));
        assertEquals(SelectionStatus.DENIED, denied.selection().status());
        status.set(SelectionStatus.EMPTY);
        assertNull(exchange(target, player, new SelectionRequest(original.epoch(), 2)));
        assertEquals(1, target.metrics().selectionReads());
        Welcome renewed = (Welcome) exchange(target, player, new Hello(nonce, 1, 0, 1));
        assertNotEquals(original.epoch(), renewed.epoch());
        SelectionReply empty = (SelectionReply) exchange(target, player, new SelectionRequest(renewed.epoch(), 1));
        assertEquals(SelectionStatus.EMPTY, empty.selection().status());
    }

    @Test void manyPlayersAndMalformedTrafficRespectAggregateAndStorageBounds() {
        BridgeServer target = server(BridgeServer.Limits.defaults());
        byte[] invalid = new byte[30];
        for (int id = 1; id <= 2048; id++) {
            UUID connection = new UUID(9, id);
            for (int packet = 0; packet < 16; packet++) assertNull(target.receive(connection, invalid));
        }
        assertEquals(1024, target.metrics().connections());
        assertEquals(100, target.metrics().admitted());
        assertEquals(100, target.metrics().malformed());
        assertEquals(32_768 - 100, target.metrics().throttled());
        assertEquals(0, reads);
        target.close();
        assertEquals(0, target.metrics().connections());
        time.addAndGet(1_000_000_000L);
        allowed.add(player);
        assertNotNull(exchange(target, player, new Hello(nonce, 1, 0, 1)));
        assertEquals(1, target.metrics().connections());
        assertEquals(101, target.metrics().admitted());
        assertEquals(0, target.metrics().selectionReads());
    }

    @Test void contextResetDropsCachedSelectionAndEpochButRetainsConnectionAdmission() {
        allowed.add(player);
        Welcome original = hello(player);
        assertNotNull(exchange(server, player, new SelectionRequest(original.epoch(), 1)));
        server.resetContext(player);
        assertNull(exchange(server, player, new SelectionRequest(original.epoch(), 1)));
        assertEquals(1, reads);
        Welcome renewed = hello(player);
        assertNotEquals(original.epoch(), renewed.epoch());
        assertNotNull(exchange(server, player, new SelectionRequest(renewed.epoch(), 1)));
        assertEquals(2, reads);
        server.resetContext(player);
        server.resetContext(second);
        assertEquals(1, server.metrics().connections());
        server.disconnect(player);
        assertEquals(0, server.metrics().connections());
    }

    @Test void repeatedContextResetsCannotRefillPlayerBudgetOrStarveAnotherPlayer() {
        BridgeServer target = server(new BridgeServer.Limits(10, 100, 100, 4, 4));
        allowed.add(player); allowed.add(second);
        for (int attempt = 0; attempt < 1000; attempt++) {
            target.resetContext(player);
            var reply = exchange(target, player, new Hello(nonce, 1, 0, 1));
            if (attempt < 4) assertNotNull(reply); else assertNull(reply);
        }
        assertEquals(4, target.metrics().admitted());
        assertEquals(996, target.metrics().throttled());
        assertNotNull(exchange(target, second, new Hello(nonce, 1, 0, 1)));
        assertEquals(0, reads);
        time.addAndGet(250_000_000L);
        assertNotNull(exchange(target, player, new Hello(nonce, 1, 0, 1)));
    }

    @Test void offThreadCallsFailBeforeSessionOrStorageAccess() throws InterruptedException {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try { hello(player); }
            catch (Throwable ex) { failure.set(ex); }
        });
        worker.start(); worker.join();
        assertInstanceOf(IllegalStateException.class, failure.get());
        assertEquals(0, server.metrics().connections());
        assertEquals(0, reads);
    }
}
