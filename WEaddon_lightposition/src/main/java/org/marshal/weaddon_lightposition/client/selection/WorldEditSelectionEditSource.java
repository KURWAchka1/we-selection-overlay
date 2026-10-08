package org.marshal.weaddon_lightposition.client.selection;

import com.sk89q.worldedit.fabric.FabricAdapter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import org.marshal.weaddon_lightposition.selection.CuboidDraft;
import org.marshal.weaddon_lightposition.selection.SelectionEditSnapshot;
import org.marshal.weaddon_lightposition.selection.worldedit.SelectionEditTransaction;
import java.util.UUID;
import java.util.function.Consumer;

public final class WorldEditSelectionEditSource implements SelectionEditSource {
    private record Context(IntegratedServer server, Object level, UUID player, Object nativeLevel, SelectionEditSnapshot selection) { }
    // The active controller owns snapshots; this server-thread cache must not retain a closed world.
    private java.lang.ref.WeakReference<SelectionEditSnapshot> last = new java.lang.ref.WeakReference<>(null);

    @Override public void read(Minecraft client, Consumer<SelectionEditSnapshot> result) {
        IntegratedServer server = client.getSingleplayerServer();
        if (server == null || client.player == null || client.level == null) { result.accept(SelectionEditSnapshot.unavailable("local_only")); return; }
        Object level = client.level;
        UUID uuid = client.player.getUUID();
        server.execute(() -> {
            SelectionEditSnapshot value;
            try {
                var nativePlayer = server.getPlayerList().getPlayer(uuid);
                if (nativePlayer == null) value = SelectionEditSnapshot.unavailable("selection");
                else {
                    var current = SelectionEditTransaction.read(FabricAdapter.get().fromNativePlayer(nativePlayer));
                    SelectionEditSnapshot cached = last.get();
                    if (cached != null && cached.token() instanceof Context old && old.server == server && old.level == level
                            && old.player.equals(uuid) && old.nativeLevel == nativePlayer.level()
                            && SelectionEditTransaction.same(old.selection, current) && cached.expand() == current.expand() && cached.contract() == current.contract()) value = cached;
                    else value = current.ready() ? new SelectionEditSnapshot(new Context(server, level, uuid, nativePlayer.level(), current),
                            current.bounds(), current.minY(), current.maxY(), current.expand(), current.contract(), "") : current;
                }
            } catch (RuntimeException | LinkageError ex) { value = SelectionEditSnapshot.unavailable("failed"); }
            last = new java.lang.ref.WeakReference<>(value);
            publish(client, level, uuid, value, result);
        });
    }

    @Override public void commit(Minecraft client, SelectionEditSnapshot origin, CuboidDraft draft, CuboidDraft.Face face, CuboidDraft.Corner corner, Consumer<SelectionEditSnapshot> result) {
        if (!(origin.token() instanceof Context context) || client.getSingleplayerServer() != context.server
                || client.level != context.level || client.player == null || !client.player.getUUID().equals(context.player)) {
            result.accept(SelectionEditSnapshot.unavailable("stale")); return;
        }
        context.server.execute(() -> {
            SelectionEditSnapshot value;
            try {
                var nativePlayer = context.server.getPlayerList().getPlayer(context.player);
                value = nativePlayer == null || nativePlayer.level() != context.nativeLevel ? SelectionEditSnapshot.unavailable("stale")
                        : corner == null ? SelectionEditTransaction.commit(FabricAdapter.get().fromNativePlayer(nativePlayer), context.selection, draft, face)
                        : SelectionEditTransaction.commitCorner(FabricAdapter.get().fromNativePlayer(nativePlayer), context.selection, draft, corner);
            } catch (RuntimeException | LinkageError ex) {
                org.slf4j.LoggerFactory.getLogger("worldedit_selection_overlay/selection-edit").warn("Selection adjustment failed.", ex);
                value = SelectionEditSnapshot.unavailable("failed");
            }
            last.clear();
            publish(client, context.level, context.player, value, result);
        });
    }

    private static void publish(Minecraft client, Object level, UUID player, SelectionEditSnapshot snapshot, Consumer<SelectionEditSnapshot> result) {
        client.execute(() -> {
            if (client.level == level && client.player != null && client.player.getUUID().equals(player)) result.accept(snapshot);
            else result.accept(SelectionEditSnapshot.unavailable("stale"));
        });
    }
}
