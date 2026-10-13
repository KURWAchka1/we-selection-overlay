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
    private final int requestedCapabilities;
    private State state = State.DISCONNECTED;
    private UUID nonce, epoch;
    private long started, requestId, pendingId, pendingSince;
    private int capabilities;
    private SelectionState selection;
    private EditSnapshot editSelection;
    private EditCommitReply editResult;
    private int pendingKind;
    private boolean channelWasAvailable;
    private boolean restartArmed;
    private UUID lastRestartGeneration;

    public BridgeClient() { this(UUID::randomUUID, SELECTION_READ | SESSION_CLOSE); }
    public BridgeClient(Supplier<UUID> nonces) { this(nonces, SELECTION_READ | SESSION_CLOSE); }
    public BridgeClient(int capabilities) { this(UUID::randomUUID, capabilities); }
    public BridgeClient(Supplier<UUID> nonces, int capabilities) {
        this.nonces = Objects.requireNonNull(nonces);
        if ((capabilities & ~SUPPORTED_CAPABILITIES) != 0) throw new IllegalArgumentException("Unknown requested capability");
        requestedCapabilities = capabilities;
    }

    public void connect(long now) {
        disconnect();
        nonce = Objects.requireNonNull(nonces.get());
        state = State.DISCOVERING;
        started = now;
    }

    public void disconnect() {
        state = State.DISCONNECTED;
        nonce = null; epoch = null; selection = null;
        editSelection = null; editResult = null; pendingKind = 0;
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
                return BridgeCodec.encode(new Hello(nonce, MAJOR, MINOR, requestedCapabilities));
            }
        }
        if (pendingId != 0 && now - pendingSince >= TIMEOUT_NANOS) {
            if (pendingKind == 2) editSelection = EditSnapshot.unavailable(SelectionStatus.FAILED);
            if (pendingKind == 3) editResult = new EditCommitReply(epoch, pendingId, CommitStatus.UNKNOWN, EditSnapshot.unavailable(SelectionStatus.FAILED));
            pendingId = 0;
            pendingKind = 0;
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
            capabilities = welcome.capabilities() & requestedCapabilities;
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
                && reply.epoch().equals(epoch) && pendingId != 0 && pendingKind == 1 && reply.requestId() == pendingId) {
            pendingId = 0;
            pendingKind = 0;
            selection = reply.selection();
            if (selection.status() == SelectionStatus.DENIED) {
                state = State.DENIED;
                capabilities = 0;
            }
        } else if (message instanceof EditReadReply reply && matchesEdit(reply.epoch(), reply.requestId(), 2)) {
            pendingId = 0; pendingKind = 0; editSelection = reply.snapshot();
            if (editSelection.selection().status() == SelectionStatus.DENIED) { state = State.DENIED; capabilities = 0; }
        } else if (message instanceof EditCommitReply reply && matchesEdit(reply.epoch(), reply.requestId(), 3)) {
            pendingId = 0; pendingKind = 0; editResult = reply; editSelection = reply.snapshot();
            if (editSelection.selection().status() == SelectionStatus.DENIED) { state = State.DENIED; capabilities = 0; }
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
        pendingKind = 1;
        pendingSince = now;
        selection = null;
        return BridgeCodec.encode(new SelectionRequest(epoch, pendingId));
    }

    private boolean matchesEdit(UUID receivedEpoch, long id, int kind) {
        return state == State.READY && (capabilities & SELECTION_EDIT) != 0 && receivedEpoch.equals(epoch)
                && pendingId != 0 && id == pendingId && pendingKind == kind;
    }
    private boolean beginEdit(long now, int kind) {
        if (state != State.READY || (capabilities & SELECTION_EDIT) == 0 || pendingId != 0 || requestId == Long.MAX_VALUE) return false;
        pendingId = ++requestId; pendingSince = now; pendingKind = kind;
        editSelection = null; editResult = null; return true;
    }
    public byte[] requestEdit(long now) {
        return beginEdit(now, 2) ? BridgeCodec.encode(new EditReadRequest(epoch, pendingId)) : null;
    }
    public byte[] commitEdit(UUID revision, Bounds target, long now) {
        // Validate before reserving an in-flight request.
        var request = new EditCommitRequest(epoch == null ? NO_EPOCH : epoch, 1, revision, target);
        return beginEdit(now, 3) ? BridgeCodec.encode(new EditCommitRequest(epoch, pendingId, request.revision(), request.bounds())) : null;
    }

    public UUID epoch() { return epoch; }
    public EditSnapshot editSelection() { return editSelection; }
    public EditCommitReply editResult() { return editResult; }

    public State state() { return state; }
    public int capabilities() { return capabilities; }
    public SelectionState selection() { return selection; }
    public boolean pending() { return pendingId != 0; }
}
