package org.marshal.weaddon_lightposition.client.clipboard;

import java.util.List;
import net.minecraft.core.BlockPos;

public record ClipboardPreviewSnapshot(
        long revision,
        BlockPos placement,
        List<ClipboardPreviewBlock> blocks,
        ClipboardPreviewBounds bounds,
        long scannedCells,
        long totalCells,
        boolean scanning,
        boolean limited
) {
    public static ClipboardPreviewSnapshot empty() {
        return new ClipboardPreviewSnapshot(
                0L,
                new BlockPos(0, 0, 0),
                List.of(),
                null,
                0L,
                0L,
                false,
                false
        );
    }

    public boolean active() {
        return bounds != null && !blocks.isEmpty();
    }
}
