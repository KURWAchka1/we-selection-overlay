package org.marshal.weaddon_lightposition.client.selection;

import net.minecraft.client.Minecraft;
import java.util.UUID;
import java.util.function.Consumer;
import org.marshal.weaddon_lightposition.client.bridge.NativeServerBridge;
import org.marshal.weaddon_lightposition.selection.CuboidDraft;
import org.marshal.weaddon_lightposition.selection.SelectionEditSnapshot;
import static org.marshal.weaddon_lightposition.bridge.protocol.BridgeMessage.*;

/** CUI invalidates the cache; repeated controller reads never poll the server. */
public final class RemoteSelectionEditSource implements SelectionEditSource {
    private record Token(UUID context, EditSnapshot snapshot) { }
    private UUID context;
    private long generation;
    private SelectionEditSnapshot cached;

    @Override public void read(Minecraft client, Consumer<SelectionEditSnapshot> result) {
        UUID current = NativeServerBridge.editContext();
        long nextGeneration = NativeServerBridge.editGeneration();
        if (current == null) { cached = null; context = null; result.accept(SelectionEditSnapshot.unavailable("local_only")); return; }
        if (current.equals(context) && generation == nextGeneration && cached != null) { result.accept(cached); return; }
        context = current; generation = nextGeneration;
        NativeServerBridge.readEdit(snapshot -> {
            if (!current.equals(NativeServerBridge.editContext()) || nextGeneration != NativeServerBridge.editGeneration()) {
                cached = null; result.accept(SelectionEditSnapshot.unavailable("stale")); return;
            }
            cached = convert(current, snapshot); result.accept(cached);
        });
    }

    @Override public void commit(Minecraft client, SelectionEditSnapshot origin, CuboidDraft draft,
            CuboidDraft.Face face, CuboidDraft.Corner corner, Consumer<SelectionEditSnapshot> result) {
        if (!(origin.token() instanceof Token token) || !token.context.equals(NativeServerBridge.editContext())) {
            result.accept(SelectionEditSnapshot.unavailable("stale")); return;
        }
        cached = null;
        NativeServerBridge.commitEdit(token.snapshot.revision(), new Bounds(draft.minX(), draft.minY(), draft.minZ(), draft.maxX(), draft.maxY(), draft.maxZ()), reply -> {
            context = NativeServerBridge.editContext(); generation = NativeServerBridge.editGeneration();
            SelectionEditSnapshot value = reply.status() == CommitStatus.APPLIED && token.context.equals(context) ? convert(context, reply.snapshot())
                    : SelectionEditSnapshot.unavailable(switch (reply.status()) {
                        case CONFLICT -> "stale";
                        case DENIED -> "permission";
                        case INVALID -> "invalid";
                        case UNSUPPORTED -> "shape";
                        default -> "unknown";
                    });
            cached = value.ready() ? value : null;
            result.accept(value);
        });
    }

    private static SelectionEditSnapshot convert(UUID context, EditSnapshot snapshot) {
        if (!snapshot.ready()) return SelectionEditSnapshot.unavailable(switch (snapshot.selection().status()) {
            case EMPTY -> "selection";
            case UNSUPPORTED -> "shape";
            case DENIED -> "permission";
            default -> "failed";
        });
        var s = snapshot.selection(); var b = s.bounds();
        return new SelectionEditSnapshot(new Token(context, snapshot), new CuboidDraft(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()),
                s.minY(), s.maxY(), (s.permissions() & 1) != 0, (s.permissions() & 2) != 0, (s.permissions() & 4) != 0, "");
    }
}
