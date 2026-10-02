package org.marshal.weaddon_lightposition.client.preview;

import java.util.List;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.BlockState;
import org.marshal.weaddon_lightposition.preview.PreviewChangeKind;
import org.marshal.weaddon_lightposition.preview.PreviewStatistics;

public record CommandPreviewSnapshot(long revision, State state, String reason, String detail,
                                     long scanned, long total, boolean random, boolean limited,
                                     List<Cell> cells, PreviewStatistics statistics, CommandPreviewMask terrain) {
    public enum State { IDLE, COMPUTING, READY, UNAVAILABLE, ERROR, LIMIT }
    public record Cell(BlockPos position, BlockState after, PreviewChangeKind kind, int faces) { }
    public static CommandPreviewSnapshot empty() { return status(State.IDLE, "", ""); }
    public static CommandPreviewSnapshot status(State state, String reason, String detail) {
        return new CommandPreviewSnapshot(0, state, reason, detail, 0, 0, false, false, List.of(), PreviewStatistics.empty(), CommandPreviewMask.empty());
    }
}
