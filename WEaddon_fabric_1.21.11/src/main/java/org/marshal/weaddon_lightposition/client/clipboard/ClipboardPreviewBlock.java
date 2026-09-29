package org.marshal.weaddon_lightposition.client.clipboard;

import net.minecraft.block.BlockState;

public record ClipboardPreviewBlock(int x, int y, int z, BlockState state) {
}
