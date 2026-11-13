package org.marshal.weaddon_lightposition.client.overlay;

import java.util.List;

public record SelectionBlockSnapshot(
        ScanState state,
        RegionShape shape,
        long scannedBoundsCells,
        long totalBoundsCells,
        long selectedCells,
        long unloadedSelectedCells,
        List<BlockEntry> entries
) {
    public enum ScanState {
        NO_SELECTION,
        INCOMPLETE_SELECTION,
        SCANNING,
        COMPLETE
    }

    public record BlockEntry(
            String stateId,
            String blockId,
            String namespace,
            String path,
            String displayName,
            boolean air,
            boolean fluid,
            boolean opaque,
            long count
    ) {
    }

    public SelectionBlockSnapshot {
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    public static SelectionBlockSnapshot noSelection() {
        return new SelectionBlockSnapshot(ScanState.NO_SELECTION, null, 0L, 0L, 0L, 0L, List.of());
    }

    public static SelectionBlockSnapshot incomplete(RegionShape shape) {
        return new SelectionBlockSnapshot(ScanState.INCOMPLETE_SELECTION, shape, 0L, 0L, 0L, 0L, List.of());
    }

    public static SelectionBlockSnapshot scanning(
            RegionShape shape,
            long scannedBoundsCells,
            long totalBoundsCells,
            long selectedCells,
            long unloadedSelectedCells,
            List<BlockEntry> entries
    ) {
        return new SelectionBlockSnapshot(
                ScanState.SCANNING,
                shape,
                scannedBoundsCells,
                totalBoundsCells,
                selectedCells,
                unloadedSelectedCells,
                entries
        );
    }

    public static SelectionBlockSnapshot complete(
            RegionShape shape,
            long totalBoundsCells,
            long selectedCells,
            long unloadedSelectedCells,
            List<BlockEntry> entries
    ) {
        return new SelectionBlockSnapshot(
                ScanState.COMPLETE,
                shape,
                totalBoundsCells,
                totalBoundsCells,
                selectedCells,
                unloadedSelectedCells,
                entries
        );
    }

    public long loadedSelectedCells() {
        return Math.max(0L, selectedCells - unloadedSelectedCells);
    }

    public double progress() {
        if (totalBoundsCells <= 0) {
            return 0.0;
        }
        return Math.min(1.0, (double) scannedBoundsCells / (double) totalBoundsCells);
    }
}
