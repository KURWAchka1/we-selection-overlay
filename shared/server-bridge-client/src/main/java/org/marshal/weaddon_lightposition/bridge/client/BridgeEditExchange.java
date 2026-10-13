package org.marshal.weaddon_lightposition.bridge.client;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import static org.marshal.weaddon_lightposition.bridge.protocol.BridgeMessage.*;

/** One callback on the client thread; context teardown and timeouts complete it, never retry it. */
public final class BridgeEditExchange {
    private final BridgeClient client;
    private Consumer<EditSnapshot> reading;
    private Consumer<EditCommitReply> committing;

    public BridgeEditExchange(BridgeClient client) { this.client = Objects.requireNonNull(client); }

    public void read(long now, Consumer<byte[]> send, Consumer<EditSnapshot> result) {
        byte[] frame = reading == null && committing == null ? client.requestEdit(now) : null;
        if (frame == null) { result.accept(EditSnapshot.unavailable(SelectionStatus.FAILED)); return; }
        reading = result; transmit(frame, send);
    }
    public void commit(UUID revision, Bounds bounds, long now, Consumer<byte[]> send, Consumer<EditCommitReply> result) {
        byte[] frame = reading == null && committing == null ? client.commitEdit(revision, bounds, now) : null;
        if (frame == null) { result.accept(failed(CommitStatus.CONFLICT)); return; }
        committing = result; transmit(frame, send);
    }
    private void transmit(byte[] frame, Consumer<byte[]> send) {
        try { send.accept(frame); }
        catch (IllegalArgumentException | IllegalStateException ex) { client.disconnect(); complete(); }
    }
    public void complete() {
        if (client.pending()) return;
        var read = reading; var commit = committing;
        reading = null; committing = null;
        if (read != null) read.accept(client.editSelection() == null ? EditSnapshot.unavailable(SelectionStatus.FAILED) : client.editSelection());
        if (commit != null) commit.accept(client.editResult() == null ? failed(CommitStatus.UNKNOWN) : client.editResult());
    }
    private static EditCommitReply failed(CommitStatus status) {
        return new EditCommitReply(NO_EPOCH, 1, status, EditSnapshot.unavailable(SelectionStatus.FAILED));
    }
}
