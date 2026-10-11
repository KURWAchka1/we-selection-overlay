package org.marshal.weaddon_lightposition.bridge.server;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import org.marshal.weaddon_lightposition.bridge.protocol.BridgeCodec;
import org.marshal.weaddon_lightposition.bridge.protocol.BridgeMessage;

import static org.marshal.weaddon_lightposition.bridge.protocol.BridgeMessage.*;

/** Owned by the platform's game thread. No tasks, workers or world/block reads. */
public final class BridgeServer {
    public interface Host {
        boolean mayReadSelection(UUID connectionPlayer);
        SelectionState readSelection(UUID connectionPlayer);
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

    private static final class Peer {
        final TokenBucket rate;
        UUID nonce, epoch;
        int capabilities;
        long lastRequest;
        SelectionReply lastReply;
        Peer(TokenBucket rate) { this.rate = rate; }
    }

    private final Host host;
    private final Thread owner = Thread.currentThread();
    private final Limits limits;
    private final LongSupplier clock;
    private final Supplier<UUID> epochs;
    private final TokenBucket aggregate;
    private final Map<UUID, Peer> peers = new HashMap<>();
    private long admitted, throttled, malformed, selectionReads, replies, encodedReplyBytes;

    public BridgeServer(Host host, Limits limits, LongSupplier clock, Supplier<UUID> epochs) {
        this.host = Objects.requireNonNull(host);
        this.limits = Objects.requireNonNull(limits);
        this.clock = Objects.requireNonNull(clock);
        this.epochs = Objects.requireNonNull(epochs);
        aggregate = new TokenBucket(limits.globalPerSecond(), limits.globalBurst(), clock.getAsLong());
    }

    /** Caller supplies identity from the actual connection, never from a payload. */
    public byte[] receive(UUID player, byte[] frame) {
        checkThread();
        Objects.requireNonNull(player);
        if (frame == null || frame.length < BridgeCodec.MIN_FRAME_BYTES || frame.length > BridgeCodec.MAX_FRAME_BYTES) {
            malformed++;
            return null;
        }
        long now = clock.getAsLong();
        Peer peer = peers.get(player);
        if (peer == null) {
            if (peers.size() >= limits.maxConnections()) { throttled++; return null; }
            peer = new Peer(new TokenBucket(limits.playerPerSecond(), limits.playerBurst(), now));
            peers.put(player, peer);
        }
        if (!peer.rate.take(now) || !aggregate.take(now)) { throttled++; return null; }
        admitted++;
        BridgeMessage message;
        try { message = BridgeCodec.decode(frame, BridgeCodec.Direction.TO_SERVER); }
        catch (IllegalArgumentException ex) { malformed++; return null; }
        BridgeMessage response = switch (message) {
            case Hello hello -> hello(player, peer, hello);
            case SelectionRequest request -> selection(player, peer, request);
            default -> null;
        };
        if (response == null) return null;
        byte[] encoded = BridgeCodec.encode(response);
        replies++;
        encodedReplyBytes += encoded.length;
        return encoded;
    }

    private Welcome hello(UUID player, Peer peer, Hello hello) {
        if (hello.major() != MAJOR) {
            invalidate(peer);
            return new Welcome(hello.nonce(), NO_EPOCH, WelcomeStatus.INCOMPATIBLE, MAJOR, MINOR, 0, BridgeCodec.MAX_FRAME_BYTES);
        }
        int allowed = host.mayReadSelection(player) ? hello.capabilities() & SUPPORTED_CAPABILITIES : 0;
        if (allowed == 0) {
            invalidate(peer);
            return new Welcome(hello.nonce(), NO_EPOCH, WelcomeStatus.DENIED, MAJOR, MINOR, 0, BridgeCodec.MAX_FRAME_BYTES);
        }
        if (!hello.nonce().equals(peer.nonce) || peer.epoch == null || peer.capabilities == 0) {
            peer.nonce = hello.nonce();
            peer.epoch = Objects.requireNonNull(epochs.get());
            peer.lastRequest = 0;
            peer.lastReply = null;
        }
        peer.capabilities = allowed;
        return new Welcome(hello.nonce(), peer.epoch, WelcomeStatus.READY, MAJOR,
                Math.min(MINOR, hello.minor()), allowed, BridgeCodec.MAX_FRAME_BYTES);
    }

    private SelectionReply selection(UUID player, Peer peer, SelectionRequest request) {
        if (peer.epoch == null || !peer.epoch.equals(request.epoch()) || (peer.capabilities & SELECTION_READ) == 0)
            return null;
        if (!host.mayReadSelection(player)) {
            SelectionReply denied = new SelectionReply(peer.epoch, request.requestId(), SelectionState.unavailable(SelectionStatus.DENIED));
            invalidate(peer);
            return denied;
        }
        if (request.requestId() < peer.lastRequest) return null;
        if (request.requestId() == peer.lastRequest) return peer.lastReply;
        selectionReads++;
        SelectionState state = Objects.requireNonNull(host.readSelection(player));
        SelectionReply reply = new SelectionReply(peer.epoch, request.requestId(), state);
        if (state.status() == SelectionStatus.DENIED) {
            invalidate(peer);
            return reply;
        }
        peer.lastRequest = request.requestId();
        peer.lastReply = reply;
        return peer.lastReply;
    }

    private static void invalidate(Peer peer) {
        peer.nonce = null; peer.epoch = null; peer.capabilities = 0;
        peer.lastRequest = 0; peer.lastReply = null;
    }

    private void checkThread() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("Bridge server used outside its owning thread");
    }

    public void disconnect(UUID player) { checkThread(); peers.remove(player); }
    public void close() { checkThread(); peers.clear(); }
    public Metrics metrics() {
        checkThread();
        return new Metrics(peers.size(), admitted, throttled, malformed, selectionReads, replies, encodedReplyBytes);
    }
}
