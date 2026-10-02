package org.marshal.weaddon_lightposition.client.preview;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;

/** Only render views use this mask; native client/server block data stays untouched. */
public final class CommandPreviewTerrainState {
    private static volatile CommandPreviewMask mask = CommandPreviewMask.empty();
    private static Object level;
    private static final LongOpenHashSet dirty = new LongOpenHashSet();

    private CommandPreviewTerrainState() { }
    public static boolean hides(BlockPos pos) { return pos != null && mask.contains(pos); }

    static void update(MinecraftClient client, CommandPreviewMask next) {
        if (level != client.world) { level = client.world; mask = CommandPreviewMask.empty(); dirty.clear(); }
        CommandPreviewMask previous = mask;
        if (previous == next) return;
        mask = next;
        if (client.world == null) return;
        next.changedSections(previous, key -> {
            int x = ChunkSectionPos.unpackX(key), y = ChunkSectionPos.unpackY(key), z = ChunkSectionPos.unpackZ(key);
            // Adjacent sections need their newly exposed faces and AO rebuilt too.
            for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++)
                dirty.add(ChunkSectionPos.asLong(x + dx, y + dy, z + dz));
        });
    }

    static void flush(MinecraftClient client) {
        if (client.world == null || level != client.world) { update(client, CommandPreviewMask.empty()); return; }
        long deadline = System.nanoTime() + 1_000_000;
        var iterator = dirty.iterator();
        while (iterator.hasNext() && System.nanoTime() < deadline) {
            long key = iterator.nextLong(); iterator.remove();
            int x = ChunkSectionPos.unpackX(key), y = ChunkSectionPos.unpackY(key), z = ChunkSectionPos.unpackZ(key);
            if (client.world.getChunkManager().getChunk(x, z, net.minecraft.world.chunk.ChunkStatus.FULL, false) != null)
                client.worldRenderer.scheduleChunkRenders(x, y, z, x, y, z);
        }
    }
}
