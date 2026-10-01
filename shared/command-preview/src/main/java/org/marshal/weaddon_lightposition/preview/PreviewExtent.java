package org.marshal.weaddon_lightposition.preview;

import com.sk89q.worldedit.extent.NullExtent;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockStateHolder;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Isolated memory only: no reference or delegate to a writable World/EditSession. */
public final class PreviewExtent extends NullExtent {
    private final BlockVector3 min, max;
    private final Map<BlockVector3, BaseBlock> original = new LinkedHashMap<>();
    private final Map<BlockVector3, PreviewChange> changes = new LinkedHashMap<>();
    private final Map<BlockVector3, BaseBlock> originalView = Collections.unmodifiableMap(original);
    private final Map<BlockVector3, PreviewChange> changesView = Collections.unmodifiableMap(changes);

    public PreviewExtent(BlockVector3 min, BlockVector3 max) { this.min = min; this.max = max; }
    public void capture(BlockVector3 position, BaseBlock block) { original.put(position, block); }
    public Map<BlockVector3, BaseBlock> original() { return originalView; }
    public Map<BlockVector3, PreviewChange> changes() { return changesView; }
    @Override public BlockVector3 getMinimumPoint() { return min; }
    @Override public BlockVector3 getMaximumPoint() { return max; }
    @Override public BlockState getBlock(BlockVector3 position) { return getFullBlock(position).toImmutableState(); }
    @Override public BaseBlock getFullBlock(BlockVector3 position) {
        PreviewChange change = changes.get(position);
        if (change != null) return change.after();
        BaseBlock block = original.get(position);
        if (block == null) throw new IllegalArgumentException("outside_snapshot");
        return block;
    }

    @Override public <B extends BlockStateHolder<B>> boolean setBlock(BlockVector3 position, B block) {
        BaseBlock before = original.get(position);
        if (before == null) throw new IllegalArgumentException("outside_snapshot");
        BaseBlock after = block.toBaseBlock();
        boolean altered = !getFullBlock(position).equals(after);
        PreviewChangeKind kind = PreviewChange.classify(before, after);
        if (kind == null) changes.remove(position);
        else changes.put(position, new PreviewChange(position, before, after, kind));
        return altered;
    }
}
