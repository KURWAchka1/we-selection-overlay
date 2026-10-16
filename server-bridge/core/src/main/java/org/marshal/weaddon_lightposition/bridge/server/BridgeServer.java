package org.marshal.weaddon_lightposition.bridge.server;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import org.marshal.weaddon_lightposition.bridge.protocol.BridgeCodec;
import org.marshal.weaddon_lightposition.bridge.protocol.BridgeMessage;

import static org.marshal.weaddon_lightposition.bridge.protocol.BridgeMessage.*;

/** Owning-thread metadata service. Host drains bounded slices only while work exists. */
public final class BridgeServer {
    public interface Host {
        boolean mayReadSelection(UUID connectionPlayer);
        SelectionState readSelection(UUID connectionPlayer);
        default boolean mayEditSelection(UUID player) { return false; }
        default EditCapture captureEdit(UUID player) { return EditCapture.unavailable(SelectionStatus.UNSUPPORTED); }
        default EditOutcome applyEdit(UUID player, Object token, Bounds target) {
            return new EditOutcome(CommitStatus.UNSUPPORTED, EditCapture.unavailable(SelectionStatus.UNSUPPORTED));
        }
    }

    /** Opaque host token never enters the codec or a client-owned object. */
    public record EditCapture(Object token, SelectionState selection) {
        public EditCapture {
            Objects.requireNonNull(selection);
            if ((token != null) != (selection.status() == SelectionStatus.READY)) throw new IllegalArgumentException("Invalid host capture");
        }
        public static EditCapture unavailable(SelectionStatus status) { return new EditCapture(null, SelectionState.unavailable(status)); }
    }
    public record EditOutcome(CommitStatus status, EditCapture capture) {
        public EditOutcome { Objects.requireNonNull(status); Objects.requireNonNull(capture); }
    }

    public record Limits(int maxConnections, int globalPerSecond, int globalBurst,
                         int playerPerSecond, int playerBurst) {
        public Limits {
            if (maxConnections < 1 || maxConnections > 100_000 || globalPerSecond < 1 || globalPerSecond > 10_000
                    || globalBurst < 1 || globalBurst > 10_000 || playerPerSecond < 1 || playerPerSecond > 100
                    || playerBurst < 1 || playerBurst > 100) throw new IllegalArgumentException("Invalid bridge limits");
        }
        public static Limits defaults() { return new Limits(1024, 100, 100, 4, 8); }
    }

    public record Metrics(int connections, long admitted, long throttled, long malformed,
                          long selectionReads, long replies, long encodedReplyBytes) { }
    public record EditMetrics(long captures, long commits, long applied) { }

    public record DispatchLimits(int maxPerSlice, long sliceNanos, long maxPendingNanos) {
        public DispatchLimits {
            if (maxPerSlice < 1 || maxPerSlice > 256 || sliceNanos < 50_000 || sliceNanos > 10_000_000
                    || maxPendingNanos < 1_000_000 || maxPendingNanos > 9_000_000_000L)
                throw new IllegalArgumentException("Invalid dispatch limits");
        }
        public static DispatchLimits defaults() { return new DispatchLimits(8, 500_000, 5_000_000_000L); }
    }

    public record QueueMetrics(int pending, int peakPending, long queued, long expired,
                               long cancelled, long maxWaitNanos, long maxSliceNanos) { }

    private static final class Peer {
        final TokenBucket rate;
        UUID nonce, epoch;
        int capabilities;
        long lastRequest;
        BridgeMessage lastReply, lastMessage;
        EditCapture edit;
        UUID editRevision;
        byte[] pending;
        long queuedAt;
        Peer(TokenBucket rate) { this.rate = rate; }
    }

    private final Host host;
    private final Thread owner = Thread.currentThread();
    private final Limits limits;
    private final DispatchLimits dispatchLimits;
    private final LongSupplier clock;
    private final Supplier<UUID> epochs;
    private final TokenBucket aggregate;
    private final Map<UUID, Peer> peers = new HashMap<>();
    private final LinkedHashMap<UUID, Peer> pending = new LinkedHashMap<>();
    private long admitted, throttled, malformed, selectionReads, replies, encodedReplyBytes;
    private long editCaptures, editCommits, editsApplied;
    private long queued, expired, cancelled, maxWaitNanos, maxSliceNanos;
    private int peakPending;
    private boolean draining;
    private boolean closed;

    public BridgeServer(Host host, Limits limits, LongSupplier clock, Supplier<UUID> epochs) {
        this(host, limits, DispatchLimits.defaults(), clock, epochs);
    }

    public BridgeServer(Host host, Limits limits, DispatchLimits dispatchLimits,
                        LongSupplier clock, Supplier<UUID> epochs) {
        this.host = Objects.requireNonNull(host);
        this.limits = Objects.requireNonNull(limits);
        this.dispatchLimits = Objects.requireNonNull(dispatchLimits);
        this.clock = Objects.requireNonNull(clock);
        this.epochs = Objects.requireNonNull(epochs);
        aggregate = new TokenBucket(limits.globalPerSecond(), limits.globalBurst(), clock.getAsLong());
    }

    /** Caller supplies identity from the actual connection, never from a payload. */
    public boolean enqueue(UUID player, byte[] frame) {
        checkThread();
        Objects.requireNonNull(player);
        if (closed) return false;
        if (frame == null || frame.length < BridgeCodec.MIN_FRAME_BYTES || frame.length > BridgeCodec.MAX_FRAME_BYTES) {
            malformed++;
            return false;
        }
        long now = clock.getAsLong();
        Peer peer = peers.get(player);
        if (peer == null) {
            if (peers.size() >= limits.maxConnections()) { throttled++; return false; }
            peer = new Peer(new TokenBucket(limits.playerPerSecond(), limits.playerBurst(), now));
            peers.put(player, peer);
        }
        // Input stays cheap: no decoding/session access, one defensive frame copy per peer.
        if (!peer.rate.take(now) || peer.pending != null) { throttled++; return false; }
        peer.pending = frame.clone();
        peer.queuedAt = now;
        pending.put(player, peer);
        queued++;
        peakPending = Math.max(peakPending, pending.size());
        return true;
    }

    /** Host invokes once per tick at most; slice limits cannot preempt an individual host call. */
    public int drain(BiConsumer<UUID, byte[]> send) {
        checkThread();
        Objects.requireNonNull(send);
        if (closed) return 0;
        if (draining) throw new IllegalStateException("Recursive bridge drain");
        if (pending.isEmpty()) return 0;
        draining = true;
        long started = clock.getAsLong();
        long previousVisit = started, visitReserve = 0;
        int visited = 0;
        try {
            while (!closed && !pending.isEmpty() && visited < dispatchLimits.maxPerSlice()) {
                long now = clock.getAsLong();
                if (visited > 0) {
                    // Reserve one observed visit; never carry a cold/paused call into later slices.
                    visitReserve = Math.max(visitReserve, Math.max(0, now - previousVisit));
                    long elapsed = Math.max(0, now - started);
                    if (elapsed >= dispatchLimits.sliceNanos() || visitReserve >= dispatchLimits.sliceNanos() - elapsed) break;
                }
                previousVisit = now;
                var entry = pending.entrySet().iterator().next();
                UUID player = entry.getKey();
                Peer peer = entry.getValue();
                long wait = Math.max(0, now - peer.queuedAt);
                boolean stale = wait >= dispatchLimits.maxPendingNanos();
                if (!stale && !aggregate.take(now)) break;
                pending.remove(player);
                byte[] frame = peer.pending;
                peer.pending = null;
                visited++;
                maxWaitNanos = Math.max(maxWaitNanos, wait);
                if (stale) { expired++; continue; }
                admitted++;
                byte[] reply = process(player, peer, frame);
                if (reply != null && !closed && peers.get(player) == peer) send.accept(player, reply);
            }
            return visited;
        } finally {
            maxSliceNanos = Math.max(maxSliceNanos, Math.max(0, clock.getAsLong() - started));
            draining = false;
        }
    }

    private byte[] process(UUID player, Peer peer, byte[] frame) {
        BridgeMessage message;
        try { message = BridgeCodec.decode(frame, BridgeCodec.Direction.TO_SERVER); }
        catch (IllegalArgumentException ex) { malformed++; return null; }
        BridgeMessage response = switch (message) {
            case Hello hello -> hello(player, peer, hello);
            case SelectionRequest request -> selection(player, peer, request);
            case EditReadRequest request -> editRead(player, peer, request);
            case EditCommitRequest request -> editCommit(player, peer, request);
            default -> null;
        };
        if (response == null) return null;
        byte[] encoded = BridgeCodec.encode(response);
        replies++;
        encodedReplyBytes += encoded.length;
        return encoded;
    }

    private Welcome hello(UUID player, Peer peer, Hello hello) {
        int minor = Math.min(MINOR, hello.minor());
        if (hello.major() != MAJOR) {
            invalidate(peer);
            return new Welcome(hello.nonce(), NO_EPOCH, WelcomeStatus.INCOMPATIBLE, MAJOR, minor, 0, BridgeCodec.MAX_FRAME_BYTES);
        }
        boolean authorized = host.mayReadSelection(player);
        if (closed || peers.get(player) != peer) return null;
        int allowed = authorized ? hello.capabilities() & SUPPORTED_CAPABILITIES : 0;
        if (minor < 1) allowed &= ~SESSION_CLOSE;
        if (minor < 2 || (allowed & SELECTION_EDIT) == 0 || !host.mayEditSelection(player)) allowed &= ~SELECTION_EDIT;
        if (closed || peers.get(player) != peer) return null;
        if ((allowed & SELECTION_READ) == 0) {
            invalidate(peer);
            return new Welcome(hello.nonce(), NO_EPOCH, WelcomeStatus.DENIED, MAJOR, minor, 0, BridgeCodec.MAX_FRAME_BYTES);
        }
        if (!hello.nonce().equals(peer.nonce) || peer.epoch == null || peer.capabilities == 0) {
            peer.nonce = hello.nonce();
            peer.epoch = Objects.requireNonNull(epochs.get());
            peer.lastRequest = 0;
            peer.lastReply = null;
            peer.lastMessage = null; peer.edit = null; peer.editRevision = null;
        }
        peer.capabilities = allowed;
        if ((allowed & SELECTION_EDIT) == 0) { peer.edit = null; peer.editRevision = null; }
        return new Welcome(hello.nonce(), peer.epoch, WelcomeStatus.READY, MAJOR,
                minor, allowed, BridgeCodec.MAX_FRAME_BYTES);
    }

    private SelectionReply selection(UUID player, Peer peer, SelectionRequest request) {
        if (peer.epoch == null || !peer.epoch.equals(request.epoch()) || (peer.capabilities & SELECTION_READ) == 0)
            return null;
        boolean authorized = host.mayReadSelection(player);
        if (closed || peers.get(player) != peer || !request.epoch().equals(peer.epoch)) return null;
        if (!authorized) {
            SelectionReply denied = new SelectionReply(peer.epoch, request.requestId(), SelectionState.unavailable(SelectionStatus.DENIED));
            invalidate(peer);
            return denied;
        }
        if (request.requestId() < peer.lastRequest) return null;
        if (request.requestId() == peer.lastRequest) return request.equals(peer.lastMessage) && peer.lastReply instanceof SelectionReply reply ? reply : null;
        selectionReads++;
        SelectionState state = Objects.requireNonNull(host.readSelection(player));
        if (closed || peers.get(player) != peer || !request.epoch().equals(peer.epoch)) return null;
        // A host callback may revoke permission or invalidate context during the read.
        authorized = host.mayReadSelection(player);
        if (closed || peers.get(player) != peer || !request.epoch().equals(peer.epoch)) return null;
        if (!authorized) state = SelectionState.unavailable(SelectionStatus.DENIED);
        SelectionReply reply = new SelectionReply(peer.epoch, request.requestId(), state);
        if (state.status() == SelectionStatus.DENIED) {
            invalidate(peer);
            return reply;
        }
        peer.lastRequest = request.requestId();
        peer.lastMessage = request;
        peer.lastReply = reply;
        return reply;
    }

    private boolean editSession(Peer peer, UUID epoch) {
        return epoch.equals(peer.epoch) && (peer.capabilities & SELECTION_EDIT) != 0;
    }

    private EditSnapshot retainCapture(Peer peer, EditCapture capture) {
        peer.edit = capture.token() == null ? null : capture;
        peer.editRevision = peer.edit == null ? NO_EPOCH : UUID.randomUUID();
        return new EditSnapshot(peer.editRevision, capture.selection());
    }

    private BridgeMessage editRead(UUID player, Peer peer, EditReadRequest request) {
        if (!editSession(peer, request.epoch())) return null;
        boolean allowed = host.mayReadSelection(player) && host.mayEditSelection(player);
        if (closed || peers.get(player) != peer || !editSession(peer, request.epoch())) return null;
        if (!allowed) {
            var reply = new EditReadReply(request.epoch(), request.requestId(), EditSnapshot.unavailable(SelectionStatus.DENIED));
            invalidate(peer); return reply;
        }
        if (request.requestId() < peer.lastRequest) return null;
        if (request.requestId() == peer.lastRequest) return request.equals(peer.lastMessage) ? peer.lastReply : null;
        editCaptures++;
        EditCapture capture = Objects.requireNonNull(host.captureEdit(player));
        if (closed || peers.get(player) != peer || !editSession(peer, request.epoch())) return null;
        allowed = host.mayReadSelection(player) && host.mayEditSelection(player);
        if (closed || peers.get(player) != peer || !editSession(peer, request.epoch())) return null;
        if (!allowed || capture.selection().status() == SelectionStatus.DENIED) {
            var reply = new EditReadReply(request.epoch(), request.requestId(), EditSnapshot.unavailable(SelectionStatus.DENIED));
            invalidate(peer); return reply;
        }
        var reply = new EditReadReply(request.epoch(), request.requestId(), retainCapture(peer, capture));
        cache(peer, request.requestId(), request, reply); return reply;
    }

    private BridgeMessage editCommit(UUID player, Peer peer, EditCommitRequest request) {
        if (!editSession(peer, request.epoch())) return null;
        boolean allowed = host.mayReadSelection(player) && host.mayEditSelection(player);
        if (closed || peers.get(player) != peer || !editSession(peer, request.epoch())) return null;
        if (!allowed) {
            var reply = new EditCommitReply(request.epoch(), request.requestId(), CommitStatus.DENIED, EditSnapshot.unavailable(SelectionStatus.DENIED));
            invalidate(peer); return reply;
        }
        if (request.requestId() < peer.lastRequest) return null;
        if (request.requestId() == peer.lastRequest) return request.equals(peer.lastMessage) ? peer.lastReply : null;
        EditCapture original = peer.edit;
        UUID revision = peer.editRevision;
        // Consume before crossing the host boundary. An uncertain outcome must never be retried as a new apply.
        peer.edit = null; peer.editRevision = null;
        EditOutcome outcome;
        if (original == null || !request.revision().equals(revision))
            outcome = new EditOutcome(CommitStatus.CONFLICT, EditCapture.unavailable(SelectionStatus.EMPTY));
        else if (request.bounds().minY() < original.selection().minY() || request.bounds().maxY() > original.selection().maxY())
            outcome = new EditOutcome(CommitStatus.INVALID, EditCapture.unavailable(SelectionStatus.FAILED));
        else {
            editCommits++;
            outcome = Objects.requireNonNull(host.applyEdit(player, original.token(), request.bounds()));
            if (outcome.status() == CommitStatus.APPLIED) editsApplied++;
        }
        if (closed || peers.get(player) != peer || !editSession(peer, request.epoch())) return null;
        allowed = host.mayReadSelection(player) && host.mayEditSelection(player);
        if (closed || peers.get(player) != peer || !editSession(peer, request.epoch())) return null;
        if (!allowed) {
            var reply = new EditCommitReply(request.epoch(), request.requestId(), CommitStatus.UNKNOWN, EditSnapshot.unavailable(SelectionStatus.DENIED));
            invalidate(peer); return reply;
        }
        var reply = new EditCommitReply(request.epoch(), request.requestId(), outcome.status(), retainCapture(peer, outcome.capture()));
        if (outcome.capture().selection().status() == SelectionStatus.DENIED) { invalidate(peer); return reply; }
        cache(peer, request.requestId(), request, reply); return reply;
    }

    private static void cache(Peer peer, long id, BridgeMessage request, BridgeMessage reply) {
        peer.lastRequest = id; peer.lastMessage = request; peer.lastReply = reply;
    }

    private static void invalidate(Peer peer) {
        peer.nonce = null; peer.epoch = null; peer.capabilities = 0;
        peer.lastRequest = 0; peer.lastReply = null; peer.lastMessage = null; peer.edit = null; peer.editRevision = null;
    }

    private void checkThread() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("Bridge server used outside its owning thread");
    }

    private void cancelPending(UUID player) {
        Peer peer = pending.remove(player);
        if (peer != null) { peer.pending = null; cancelled++; }
    }

    public void disconnect(UUID player) { checkThread(); cancelPending(player); peers.remove(player); }
    /** Drop session data without resetting the budget of a still-open connection. */
    public void resetContext(UUID player) {
        checkThread();
        cancelPending(player);
        Peer peer = peers.get(player);
        if (peer != null) invalidate(peer);
    }
    /** One bounded notification per opted-in live session, before the host loses send authority. */
    public void shutdown(BiConsumer<UUID, byte[]> send) {
        checkThread();
        Objects.requireNonNull(send);
        if (closed) return;
        closed = true;
        try {
            for (var entry : peers.entrySet()) {
                Peer peer = entry.getValue();
                if ((peer.capabilities & SESSION_CLOSE) == 0) continue;
                byte[] frame = BridgeCodec.encode(new SessionClosed(peer.nonce, peer.epoch));
                send.accept(entry.getKey(), frame);
                replies++;
                encodedReplyBytes += frame.length;
            }
        } finally {
            clearPending();
            peers.clear();
        }
    }

    private void clearPending() {
        cancelled += pending.size();
        for (Peer peer : pending.values()) peer.pending = null;
        pending.clear();
    }

    public void close() { checkThread(); closed = true; clearPending(); peers.clear(); }
    public Metrics metrics() {
        checkThread();
        return new Metrics(peers.size(), admitted, throttled, malformed, selectionReads, replies, encodedReplyBytes);
    }
    public int pendingCount() { checkThread(); return pending.size(); }
    public EditMetrics editMetrics() { checkThread(); return new EditMetrics(editCaptures, editCommits, editsApplied); }
    public QueueMetrics queueMetrics() {
        checkThread();
        return new QueueMetrics(pending.size(), peakPending, queued, expired, cancelled, maxWaitNanos, maxSliceNanos);
    }
}
