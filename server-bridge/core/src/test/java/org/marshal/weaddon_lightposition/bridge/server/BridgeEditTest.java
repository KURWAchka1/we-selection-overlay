package org.marshal.weaddon_lightposition.bridge.server;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.marshal.weaddon_lightposition.bridge.protocol.BridgeCodec;
import org.marshal.weaddon_lightposition.bridge.protocol.BridgeMessage;
import static org.junit.jupiter.api.Assertions.*;
import static org.marshal.weaddon_lightposition.bridge.protocol.BridgeMessage.*;

class BridgeEditTest {
    private final UUID player = new UUID(1, 1), other = new UUID(1, 2), nonce = new UUID(2, 2);
    private final AtomicLong now = new AtomicLong();
    private final Bounds initial = new Bounds(0, 0, 0, 5, 5, 5), shifted = new Bounds(10, 0, 0, 15, 5, 5);
    private boolean authorized = true, editing = true, revokeDuringApply, failDuringApply;
    private int captures, applies;
    private int authorizationChecks, resetAtCheck = -1;
    private record Origin(UUID player, Bounds bounds) { }
    private Bounds actual = initial;
    private final BridgeServer server = new BridgeServer(new BridgeServer.Host() {
        public boolean mayReadSelection(UUID id) { return authorized; }
        public SelectionState readSelection(UUID id) { return state(); }
        public boolean mayEditSelection(UUID id) {
            if (++authorizationChecks == resetAtCheck) server.resetContext(id);
            return editing;
        }
        public BridgeServer.EditCapture captureEdit(UUID id) { captures++; return new BridgeServer.EditCapture(new Origin(id, actual), state()); }
        public BridgeServer.EditOutcome applyEdit(UUID id, Object token, Bounds target) {
            applies++;
            Origin origin = (Origin) token;
            assertEquals(id, origin.player);
            if (!origin.bounds.equals(actual)) return new BridgeServer.EditOutcome(CommitStatus.CONFLICT, BridgeServer.EditCapture.unavailable(SelectionStatus.EMPTY));
            actual = target;
            if (revokeDuringApply) editing = false;
            if (failDuringApply) throw new IllegalStateException("uncertain host exit");
            return new BridgeServer.EditOutcome(CommitStatus.APPLIED, new BridgeServer.EditCapture(new Origin(id, actual), state()));
        }
    }, new BridgeServer.Limits(10, 1000, 1000, 100, 100), now::get, UUID::randomUUID);
    private SelectionState state() { return new SelectionState(SelectionStatus.READY, actual, -64, 319, 7); }
    private BridgeMessage exchange(UUID id, BridgeMessage message) {
        var result = new AtomicReference<BridgeMessage>();
        assertTrue(server.enqueue(id, BridgeCodec.encode(message)));
        server.drain((actor, bytes) -> { assertEquals(id, actor); result.set(BridgeCodec.decode(bytes, BridgeCodec.Direction.TO_CLIENT)); });
        return result.get();
    }
    private Welcome hello(UUID id) { return (Welcome) exchange(id, new Hello(nonce, 1, 2, 7)); }
    private EditSnapshot capture(UUID id, UUID epoch, long request) { return ((EditReadReply) exchange(id, new EditReadRequest(epoch, request))).snapshot(); }

    @Test void editingIsSeparatelyAuthorizedAndNeverAvailableToLegacyPeers() {
        editing = false; assertEquals(3, hello(player).capabilities()); assertEquals(0, captures);
        editing = true;
        assertEquals(3, ((Welcome) exchange(player, new Hello(nonce, 1, 1, 7))).capabilities());
        var welcome = hello(player); assertEquals(7, welcome.capabilities()); assertEquals(0, captures);
        var snapshot = capture(player, welcome.epoch(), 1); assertTrue(snapshot.ready()); assertEquals(initial, snapshot.selection().bounds());
    }
    @Test void duplicateCommitAppliesOnceAndDifferentPayloadWithSameIdIsDropped() {
        var epoch = hello(player).epoch(); var snapshot = capture(player, epoch, 1);
        var request = new EditCommitRequest(epoch, 2, snapshot.revision(), shifted);
        var first = (EditCommitReply) exchange(player, request);
        assertEquals(CommitStatus.APPLIED, first.status()); assertEquals(shifted, first.snapshot().selection().bounds());
        assertEquals(first, exchange(player, request)); assertEquals(1, applies);
        assertNull(exchange(player, new EditCommitRequest(epoch, 2, snapshot.revision(), initial)));
        assertNull(exchange(player, new EditReadRequest(epoch, 1)));
        assertEquals(CommitStatus.CONFLICT, ((EditCommitReply) exchange(player, new EditCommitRequest(epoch, 3, snapshot.revision(), initial))).status());
        assertEquals(1, applies); assertEquals(1, server.editMetrics().applied());
    }
    @Test void connectionIdentityAndRevisionCannotTargetAnotherPlayersCapture() {
        var a = hello(player).epoch(); var b = hello(other).epoch();
        var original = capture(player, a, 1); capture(other, b, 1);
        assertNull(exchange(other, new EditCommitRequest(a, 2, original.revision(), shifted)));
        assertEquals(CommitStatus.CONFLICT, ((EditCommitReply) exchange(other, new EditCommitRequest(b, 2, original.revision(), shifted))).status());
        assertEquals(0, applies); assertEquals(initial, actual);
    }
    @Test void currentHostStateWinsOverAStaleCapturedBounds() {
        var epoch = hello(player).epoch(); var original = capture(player, epoch, 1); actual = shifted;
        assertEquals(CommitStatus.CONFLICT, ((EditCommitReply) exchange(player, new EditCommitRequest(epoch, 2, original.revision(), initial))).status());
        assertEquals(shifted, actual);
    }
    @Test void revocationBeforeApplyOrCachedReplyDoesNotDiscloseOrApply() {
        var epoch = hello(player).epoch(); var original = capture(player, epoch, 1); editing = false;
        var denied = (EditReadReply) exchange(player, new EditReadRequest(epoch, 1));
        assertEquals(SelectionStatus.DENIED, denied.snapshot().selection().status()); assertFalse(denied.snapshot().ready());
        assertNull(exchange(player, new EditCommitRequest(epoch, 2, original.revision(), shifted))); assertEquals(0, applies);
    }
    @Test void revocationAfterHostApplyIsUnknownNotAFictionalRollback() {
        var epoch = hello(player).epoch(); var original = capture(player, epoch, 1); revokeDuringApply = true;
        var reply = (EditCommitReply) exchange(player, new EditCommitRequest(epoch, 2, original.revision(), shifted));
        assertEquals(CommitStatus.UNKNOWN, reply.status()); assertFalse(reply.snapshot().ready()); assertEquals(shifted, actual);
        assertNull(exchange(player, new EditCommitRequest(epoch, 2, original.revision(), shifted))); assertEquals(1, applies);
    }
    @Test void heightRejectionAndContextResetNeverApply() {
        var epoch = hello(player).epoch(); var original = capture(player, epoch, 1);
        assertEquals(CommitStatus.INVALID, ((EditCommitReply) exchange(player,
                new EditCommitRequest(epoch, 2, original.revision(), new Bounds(0, -65, 0, 1, 0, 1)))).status());
        assertEquals(0, applies);
        server.resetContext(player);
        assertNull(exchange(player, new EditCommitRequest(epoch, 3, original.revision(), shifted)));
        var next = hello(player).epoch();
        assertEquals(CommitStatus.CONFLICT, ((EditCommitReply) exchange(player, new EditCommitRequest(next, 1, original.revision(), shifted))).status());
        assertEquals(0, applies);
    }
    @Test void anExceptionalHostExitConsumesTheTokenBeforeAnyReplay() {
        var epoch = hello(player).epoch(); var original = capture(player, epoch, 1); failDuringApply = true;
        var request = new EditCommitRequest(epoch, 2, original.revision(), shifted);
        assertThrows(IllegalStateException.class, () -> exchange(player, request));
        assertEquals(CommitStatus.CONFLICT, ((EditCommitReply) exchange(player, request)).status());
        assertEquals(1, applies); assertEquals(shifted, actual);
    }
    @Test void queuedCommitRechecksPermissionAtServiceNotIngress() {
        var epoch = hello(player).epoch(); var original = capture(player, epoch, 1);
        assertTrue(server.enqueue(player, BridgeCodec.encode(new EditCommitRequest(epoch, 2, original.revision(), shifted))));
        editing = false;
        var reply = new AtomicReference<EditCommitReply>();
        server.drain((id, bytes) -> reply.set((EditCommitReply) BridgeCodec.decode(bytes, BridgeCodec.Direction.TO_CLIENT)));
        assertEquals(CommitStatus.DENIED, reply.get().status()); assertFalse(reply.get().snapshot().ready());
        assertEquals(0, applies); assertEquals(initial, actual);
    }
    @Test void queuedCommitIsDiscardedOnContextResetAndTerminalClose() {
        var epoch = hello(player).epoch(); var original = capture(player, epoch, 1);
        var frame = BridgeCodec.encode(new EditCommitRequest(epoch, 2, original.revision(), shifted));
        assertTrue(server.enqueue(player, frame)); server.resetContext(player);
        server.drain((id, bytes) -> fail("Old context reply"));
        epoch = hello(player).epoch(); original = capture(player, epoch, 1);
        assertTrue(server.enqueue(player, BridgeCodec.encode(new EditCommitRequest(epoch, 2, original.revision(), shifted))));
        server.close(); server.drain((id, bytes) -> fail("Closed host reply"));
        assertEquals(0, applies); assertEquals(initial, actual); assertFalse(server.enqueue(player, frame));
    }
    @Test void expiredQueuedCommitCannotApplyAfterClientWouldHaveMovedOn() {
        var epoch = hello(player).epoch(); var original = capture(player, epoch, 1);
        assertTrue(server.enqueue(player, BridgeCodec.encode(new EditCommitRequest(epoch, 2, original.revision(), shifted))));
        now.set(6_000_000_000L);
        server.drain((id, bytes) -> fail("Expired operation reply"));
        assertEquals(0, applies); assertEquals(initial, actual);
    }
    @Test void revokedPermissionCannotRecoverAnAppliedCachedCommitReply() {
        var epoch = hello(player).epoch(); var original = capture(player, epoch, 1);
        var request = new EditCommitRequest(epoch, 2, original.revision(), shifted);
        assertEquals(CommitStatus.APPLIED, ((EditCommitReply) exchange(player, request)).status());
        editing = false;
        var reply = (EditCommitReply) exchange(player, request);
        assertEquals(CommitStatus.DENIED, reply.status()); assertFalse(reply.snapshot().ready());
        assertEquals(1, applies); assertEquals(shifted, actual);
    }
    @Test void reentrantFinalAuthorizationCannotPublishCaptureIntoResetContext() {
        var epoch = hello(player).epoch(); resetAtCheck = authorizationChecks + 2;
        assertNull(exchange(player, new EditReadRequest(epoch, 1)));
        assertEquals(1, captures); assertEquals(0, applies);
        assertNull(exchange(player, new EditCommitRequest(epoch, 2, nonce, shifted)));
    }
    @Test void reentrantFinalAuthorizationCannotPublishAppliedAckIntoResetContext() {
        var epoch = hello(player).epoch(); var original = capture(player, epoch, 1);
        resetAtCheck = authorizationChecks + 2;
        var request = new EditCommitRequest(epoch, 2, original.revision(), shifted);
        assertNull(exchange(player, request)); assertEquals(1, applies); assertEquals(shifted, actual);
        assertNull(exchange(player, request)); assertEquals(1, applies);
    }
}
