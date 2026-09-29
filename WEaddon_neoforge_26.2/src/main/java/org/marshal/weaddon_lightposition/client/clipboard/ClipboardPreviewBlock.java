package org.marshal.weaddon_lightposition.client.clipboard;

import net.minecraft.world.level.block.state.BlockState;

public record ClipboardPreviewBlock(int x, int y, int z, BlockState state) {
}
