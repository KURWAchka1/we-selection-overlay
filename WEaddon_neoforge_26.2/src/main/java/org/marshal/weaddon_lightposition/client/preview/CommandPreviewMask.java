package org.marshal.weaddon_lightposition.client.preview;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.BitSet;
import java.util.Objects;
import java.util.function.LongConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;

/** Immutable replaced-source membership, independent of exposed ghost faces. */
public final class CommandPreviewMask {
    private static final CommandPreviewMask EMPTY = new CommandPreviewMask(Long2ObjectMaps.emptyMap());
    private final Long2ObjectMap<BitSet> sections;

    private CommandPreviewMask(Long2ObjectMap<BitSet> sections) { this.sections = sections; }
    public static CommandPreviewMask empty() { return EMPTY; }

    public boolean contains(BlockPos pos) {
        BitSet cells = sections.get(SectionPos.asLong(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4));
        return cells != null && cells.get(index(pos.getX(), pos.getY(), pos.getZ()));
    }

    void changedSections(CommandPreviewMask previous, LongConsumer consumer) {
        sections.forEach((key, cells) -> { if (!cells.equals(previous.sections.get(key))) consumer.accept(key); });
        previous.sections.forEach((key, cells) -> { if (!Objects.equals(cells, sections.get(key))) consumer.accept(key); });
    }

    private static int index(int x, int y, int z) { return (y & 15) << 8 | (z & 15) << 4 | x & 15; }

    public static final class Builder {
        private final Long2ObjectMap<BitSet> sections = new Long2ObjectOpenHashMap<>();
        private boolean built;

        public void add(int x, int y, int z) {
            if (built) throw new IllegalStateException("Preview mask already published");
            sections.computeIfAbsent(SectionPos.asLong(x >> 4, y >> 4, z >> 4), key -> new BitSet(4096)).set(index(x, y, z));
        }

        public CommandPreviewMask build() {
            if (built) throw new IllegalStateException("Preview mask already published");
            built = true;
            return sections.isEmpty() ? EMPTY : new CommandPreviewMask(sections);
        }
    }
}
