package org.marshal.weaddon_lightposition.client.clipboard;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

public record ClipboardPreviewBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    public AABB toWorldAabb(BlockPos placement) {
        return new AABB(
                placement.getX() + minX,
                placement.getY() + minY,
                placement.getZ() + minZ,
                placement.getX() + maxX + 1.0,
                placement.getY() + maxY + 1.0,
                placement.getZ() + maxZ + 1.0
        );
    }
}
