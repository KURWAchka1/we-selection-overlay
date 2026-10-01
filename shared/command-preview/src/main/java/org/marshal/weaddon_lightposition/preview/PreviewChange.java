package org.marshal.weaddon_lightposition.preview;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BaseBlock;

public record PreviewChange(BlockVector3 position, BaseBlock before, BaseBlock after, PreviewChangeKind kind) {

    public static PreviewChangeKind classify(BaseBlock before, BaseBlock after) {
        if (before.equals(after)) return null;
        if (after.getBlockType().getMaterial().isAir()) return PreviewChangeKind.REMOVED;
        if (before.getBlockType().getMaterial().isAir()) return PreviewChangeKind.ADDED;
        return PreviewChangeKind.REPLACED;
    }
}
