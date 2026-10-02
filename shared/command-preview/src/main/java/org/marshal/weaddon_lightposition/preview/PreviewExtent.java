package org.marshal.weaddon_lightposition.preview;

import com.sk89q.worldedit.extent.NullExtent;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockStateHolder;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

/** Isolated memory only: no reference or delegate to a writable World/EditSession. */
public final class PreviewExtent extends NullExtent {
    private record Key(int x, int y, int z) {
        static Key at(BlockVector3 p) { return new Key(p.x() >> 4, p.y() >> 4, p.z() >> 4); }
    }
    private static final class Indices {
        private final int uniform;
        private byte[] bytes;
        private short[] shorts;
        private int[] ints;
        Indices() { this(0); }
        Indices(int uniform) { this.uniform = uniform; }
        int get(int index) {
            return ints != null ? ints[index] : shorts != null ? Short.toUnsignedInt(shorts[index])
                    : bytes != null ? Byte.toUnsignedInt(bytes[index]) : uniform;
        }
        void set(int index, int id) {
            if (bytes == null && shorts == null && ints == null) {
                if (id == uniform) return;
                int largest = Math.max(uniform, id);
                if (largest <= 255) { bytes = new byte[4096]; Arrays.fill(bytes, (byte) uniform); }
                else if (largest <= 65535) { shorts = new short[4096]; Arrays.fill(shorts, (short) uniform); }
                else { ints = new int[4096]; Arrays.fill(ints, uniform); }
            }
            if (id > 65535 && ints == null) {
                int[] upgraded = new int[4096];
                for (int i = 0; i < upgraded.length; i++) upgraded[i] = get(i);
                ints = upgraded;
                bytes = null; shorts = null;
            } else if (id > 255 && ints == null && shorts == null) {
                short[] upgraded = new short[4096];
                for (int i = 0; i < upgraded.length; i++) upgraded[i] = (short) get(i);
                shorts = upgraded;
                bytes = null;
            }
            if (ints != null) ints[index] = id;
            else if (shorts != null) shorts[index] = (short) id;
            else bytes[index] = (byte) id;
        }
    }
    private static final class Section {
        final Indices original = new Indices();
        Indices after;
        final BitSet captured = new BitSet(4096), changed = new BitSet(4096);
        final ArrayList<BaseBlock> palette = new ArrayList<>();
        final Map<BaseBlock, Integer> ids = new HashMap<>();
        int id(BaseBlock block) {
            Integer id = ids.get(block);
            if (id != null) return id;
            int next = palette.size(); palette.add(block); ids.put(block, next); return next;
        }
    }
    private final BlockVector3 min, max;
    private final Map<Key, Section> sections = new LinkedHashMap<>();
    private int capturedCount, changedCount;
    private final Map<BlockVector3, BaseBlock> originalView = new AbstractMap<>() {
        @Override public int size() { return capturedCount; }
        @Override public BaseBlock get(Object key) { return key instanceof BlockVector3 p ? originalAt(p) : null; }
        @Override public Set<Entry<BlockVector3, BaseBlock>> entrySet() { return entries(false); }
    };
    private final Map<BlockVector3, PreviewChange> changesView = new AbstractMap<>() {
        @Override public int size() { return changedCount; }
        @Override public PreviewChange get(Object key) { return key instanceof BlockVector3 p ? changeAt(p) : null; }
        @Override public Set<Entry<BlockVector3, PreviewChange>> entrySet() { return entries(true); }
    };

    public PreviewExtent(BlockVector3 min, BlockVector3 max) { this.min = min; this.max = max; }
    private static int index(BlockVector3 p) { return (p.y() & 15) << 8 | (p.z() & 15) << 4 | p.x() & 15; }
    public void capture(BlockVector3 p, BaseBlock block) {
        Section section = sections.computeIfAbsent(Key.at(p), k -> new Section());
        int index = index(p);
        if (!section.captured.get(index)) { section.captured.set(index); capturedCount++; }
        section.original.set(index, section.id(block));
    }
    public void recapture(BlockVector3 p, BaseBlock block) {
        Section section = sections.get(Key.at(p));
        if (section == null || !section.captured.get(index(p))) throw new IllegalArgumentException("outside_snapshot");
        if (section.changed.get(index(p))) { section.changed.clear(index(p)); changedCount--; }
        capture(p, block);
    }
    public Map<BlockVector3, BaseBlock> original() { return originalView; }
    public Map<BlockVector3, PreviewChange> changes() { return changesView; }
    public boolean captured(BlockVector3 p) { return originalAt(p) != null; }
    public BaseBlock changedBlock(BlockVector3 p) {
        Section s = sections.get(Key.at(p)); int i = index(p);
        return s == null || !s.changed.get(i) ? null : s.palette.get(s.after.get(i));
    }
    private BaseBlock originalAt(BlockVector3 p) {
        Section s = sections.get(Key.at(p)); int i = index(p);
        return s == null || !s.captured.get(i) ? null : s.palette.get(s.original.get(i));
    }
    private PreviewChange changeAt(BlockVector3 p) {
        Section s = sections.get(Key.at(p)); int i = index(p);
        if (s == null || !s.changed.get(i)) return null;
        BaseBlock before = s.palette.get(s.original.get(i)), after = s.palette.get(s.after.get(i));
        return new PreviewChange(p, before, after, PreviewChange.classify(before, after));
    }
    @Override public BlockVector3 getMinimumPoint() { return min; }
    @Override public BlockVector3 getMaximumPoint() { return max; }
    @Override public BlockState getBlock(BlockVector3 position) { return getFullBlock(position).toImmutableState(); }
    @Override public BaseBlock getFullBlock(BlockVector3 position) {
        Section s = sections.get(Key.at(position)); int i = index(position);
        if (s == null || !s.captured.get(i)) throw new IllegalArgumentException("outside_snapshot");
        return s.palette.get(s.changed.get(i) ? s.after.get(i) : s.original.get(i));
    }

    @Override public <B extends BlockStateHolder<B>> boolean setBlock(BlockVector3 position, B block) {
        Section s = sections.get(Key.at(position)); int i = index(position);
        if (s == null || !s.captured.get(i)) throw new IllegalArgumentException("outside_snapshot");
        BaseBlock after = block.toBaseBlock();
        boolean altered = !getFullBlock(position).equals(after);
        boolean changed = !s.palette.get(s.original.get(i)).equals(after);
        if (changed != s.changed.get(i)) changedCount += changed ? 1 : -1;
        s.changed.set(i, changed);
        if (changed) {
            int id = s.id(after);
            if (s.after == null) s.after = new Indices(id);
            s.after.set(i, id);
        }
        return altered;
    }

    private <V> Set<Map.Entry<BlockVector3, V>> entries(boolean changes) {
        return new AbstractSet<>() {
            @Override public int size() { return changes ? changedCount : capturedCount; }
            @Override public Iterator<Map.Entry<BlockVector3, V>> iterator() {
                Iterator<Map.Entry<Key, Section>> iterator = sections.entrySet().iterator();
                return new Iterator<>() {
                    Map.Entry<Key, Section> section;
                    int next = -1;
                    @Override public boolean hasNext() {
                        while (next < 0 && iterator.hasNext()) {
                            section = iterator.next(); next = bits().nextSetBit(0);
                        }
                        return next >= 0;
                    }
                    private BitSet bits() { return changes ? section.getValue().changed : section.getValue().captured; }
                    @Override @SuppressWarnings("unchecked") public Map.Entry<BlockVector3, V> next() {
                        if (!hasNext()) throw new NoSuchElementException();
                        int i = next; next = bits().nextSetBit(i + 1);
                        Key k = section.getKey();
                        BlockVector3 p = BlockVector3.at((k.x << 4) + (i & 15), (k.y << 4) + (i >> 8), (k.z << 4) + (i >> 4 & 15));
                        return Map.entry(p, (V) (changes ? changeAt(p) : originalAt(p)));
                    }
                };
            }
        };
    }
}
