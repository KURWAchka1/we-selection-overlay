package org.marshal.weaddon_lightposition.client.preview;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;

/** Only render views use this mask; native client/server block data stays untouched. */
public final class CommandPreviewTerrainState {
    private static volatile CommandPreviewMask mask = CommandPreviewMask.empty();
    private static Object level;
    private static final LongOpenHashSet dirty = new LongOpenHashSet();

    private CommandPreviewTerrainState() { }
    public static boolean hides(BlockPos pos) { return pos != null && mask.contains(pos); }

    static void update(Minecraft client, CommandPreviewMask next) {
        if (level != client.level) { level = client.level; mask = CommandPreviewMask.empty(); dirty.clear(); }
        CommandPreviewMask previous = mask;
        if (previous == next) return;
        mask = next;
        if (client.level == null) return;
        next.changedSections(previous, key -> {
            int x = SectionPos.x(key), y = SectionPos.y(key), z = SectionPos.z(key);
            // Adjacent sections need their newly exposed faces and AO rebuilt too.
            for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++)
                dirty.add(SectionPos.asLong(x + dx, y + dy, z + dz));
        });
    }

    static void flush(Minecraft client) {
        if (client.level == null || level != client.level) { update(client, CommandPreviewMask.empty()); return; }
        long deadline = System.nanoTime() + 1_000_000;
        var iterator = dirty.iterator();
        while (iterator.hasNext() && System.nanoTime() < deadline) {
            long key = iterator.nextLong(); iterator.remove();
            int x = SectionPos.x(key), y = SectionPos.y(key), z = SectionPos.z(key);
            if (client.level.getChunkSource().getChunk(x, z, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false) != null)
                client.levelRenderer.setSectionRangeDirty(x, y, z, x, y, z);
        }
    }
}
