package org.marshal.weaddon_lightposition.bridge.client;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

import org.marshal.weaddon_lightposition.bridge.protocol.BridgeCodec;
import org.marshal.weaddon_lightposition.bridge.protocol.BridgeRestartHint;
import org.marshal.weaddon_lightposition.bridge.protocol.BridgeMessage;

import static org.marshal.weaddon_lightposition.bridge.protocol.BridgeMessage.*;

/** Single client-thread state machine; poll only discovers/times out, never scans. */
public final class BridgeClient {
    public enum State { DISCONNECTED, DISCOVERING, NEGOTIATING, READY, DENIED, INCOMPATIBLE, UNAVAILABLE }
    public static final long TIMEOUT_NANOS = 10_000_000_000L;
    private final Supplier<UUID> nonces;
    private State state = State.DISCONNECTED;
    private UUID nonce, epoch;
    private long started, requestId, pendingId, pendingSince;
    private int capabilities;
    private SelectionState selection;
    private boolean channelWasAvailable;
    private boolean restartArmed;
    private UUID lastRestartGeneration;

    public BridgeClient() { this(UUID::randomUUID); }
    public BridgeClient(Supplier<UUID> nonces) { this.nonces = Objects.requireNonNull(nonces); }

    public void connect(long now) {
        disconnect();
        nonce = Objects.requireNonNull(nonces.get());
        state = State.DISCOVERING;
        started = now;
    }

    public void disconnect() {
        state = State.DISCONNECTED;
        nonce = null; epoch = null; selection = null;
        requestId = 0; pendingId = 0; capabilities = 0;
        channelWasAvailable = false;
        restartArmed = false;
        lastRestartGeneration = null;
    }

    /** One hello per context/advertisement edge, no retries/heartbeats. */
    public byte[] poll(boolean channelAvailable, long now) {
        if (state == State.DISCONNECTED) return null;
        if (channelWasAvailable && !channelAvailable) {
            disconnect();
            state = State.UNAVAILABLE;
        } else if (!channelWasAvailable && channelAvailable && state == State.UNAVAILABLE) {
            connect(now);
        }
        channelWasAvailable = channelAvailable;
        if (state == State.DISCOVERING || state == State.NEGOTIATING) {
            if (now - started >= TIMEOUT_NANOS) { state = State.UNAVAILABLE; return null; }
            if (state == State.DISCOVERING && channelAvailable) {
                state = State.NEGOTIATING;
                return BridgeCodec.encode(new Hello(nonce, MAJOR, MINOR, SUPPORTED_CAPABILITIES));
            }
        }
        if (pendingId != 0 && now - pendingSince >= TIMEOUT_NANOS) {
            pendingId = 0;
            selection = SelectionState.unavailable(SelectionStatus.FAILED);
        }
        return null;
    }

    public void receive(byte[] frame) {
        if (state == State.DISCONNECTED) return;
        BridgeMessage message;
        try { message = BridgeCodec.decode(frame, BridgeCodec.Direction.TO_CLIENT); }
        catch (IllegalArgumentException ex) { return; }
        if (message instanceof Welcome welcome) {
            if (state != State.NEGOTIATING || !welcome.nonce().equals(nonce)) return;
            if (welcome.major() != MAJOR || welcome.minor() > MINOR || welcome.status() == WelcomeStatus.INCOMPATIBLE
                    || welcome.maxFrameBytes() < BridgeCodec.MIN_NEGOTIATED_FRAME_BYTES) {
                state = State.INCOMPATIBLE;
                return;
            }
            if (welcome.status() == WelcomeStatus.DENIED) { state = State.DENIED; return; }
            epoch = welcome.epoch();
            capabilities = welcome.capabilities() & SUPPORTED_CAPABILITIES;
            state = capabilities == 0 ? State.UNAVAILABLE : State.READY;
        } else if (message instanceof SessionClosed closed && state == State.READY
                && (capabilities & SESSION_CLOSE) != 0 && closed.nonce().equals(nonce) && closed.epoch().equals(epoch)) {
            // A still-advertised channel is not a restart edge. Do not retry into a stopped service.
            boolean advertised = channelWasAvailable;
            UUID generation = lastRestartGeneration;
            disconnect();
            channelWasAvailable = advertised;
            lastRestartGeneration = generation;
            restartArmed = true;
            state = State.UNAVAILABLE;
        } else if (message instanceof SelectionReply reply && state == State.READY
                && reply.epoch().equals(epoch) && pendingId != 0 && reply.requestId() == pendingId) {
            pendingId = 0;
            selection = reply.selection();
            if (selection.status() == SelectionStatus.DENIED) {
                state = State.DENIED;
                capabilities = 0;
            }
        }
    }

    /** One fresh handshake after an opted-in graceful close; hints grant nothing. */
    public void receiveRestartHint(byte[] frame, long now) {
        if (state != State.UNAVAILABLE || !restartArmed) return;
        UUID generation;
        try { generation = BridgeRestartHint.decode(frame); }
        catch (IllegalArgumentException ex) { return; }
        if (generation.equals(lastRestartGeneration)) return;
        boolean advertised = channelWasAvailable;
        connect(now);
        channelWasAvailable = advertised;
        lastRestartGeneration = generation;
    }

    /** On-demand metadata only; enabling a connection does not start a subscription. */
    public byte[] requestSelection(long now) {
        if (state != State.READY || (capabilities & SELECTION_READ) == 0 || pendingId != 0 || requestId == Long.MAX_VALUE)
            return null;
        pendingId = ++requestId;
        pendingSince = now;
        selection = null;
        return BridgeCodec.encode(new SelectionRequest(epoch, pendingId));
    }

    public State state() { return state; }
    public int capabilities() { return capabilities; }
    public SelectionState selection() { return selection; }
    public boolean pending() { return pendingId != 0; }
}
