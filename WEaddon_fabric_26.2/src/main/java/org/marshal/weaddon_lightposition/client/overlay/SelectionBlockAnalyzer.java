package org.marshal.weaddon_lightposition.client.overlay;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;

public final class SelectionBlockAnalyzer {
    private static final int BOUNDS_CELLS_PER_TICK = 30_000;
    private static final Identifier AIR_ID = Identifier.fromNamespaceAndPath("minecraft", "air");

    private final HashMap<BlockState, Long> counts = new HashMap<>();
    private final BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

    private SelectionRegion activeRegion;
    private long activeRegionVersion;
    private ClientLevel activeWorld;
    private VoxelBounds bounds;
    private long cursor;
    private long selectedCells;
    private long unloadedSelectedCells;
    private SelectionBlockSnapshot snapshot = SelectionBlockSnapshot.noSelection();

    public void reset() {
        counts.clear();
        activeRegion = null;
        activeRegionVersion = 0L;
        activeWorld = null;
        bounds = null;
        cursor = 0L;
        selectedCells = 0L;
        unloadedSelectedCells = 0L;
        snapshot = SelectionBlockSnapshot.noSelection();
    }

    public SelectionBlockSnapshot snapshot() {
        return snapshot;
    }

    public void refresh(ClientLevel world, SelectionRegion region) {
        if (world == null || region == null) {
            reset();
            return;
        }
        startScan(world, region);
    }

    public void tick(ClientLevel world, SelectionRegion region) {
        if (world == null || region == null) {
            if (snapshot.state() != SelectionBlockSnapshot.ScanState.NO_SELECTION) {
                reset();
            }
            return;
        }

        if (activeRegion != region || activeWorld != world || activeRegionVersion != region.version()) {
            startScan(world, region);
        }

        if (snapshot.state() != SelectionBlockSnapshot.ScanState.SCANNING || bounds == null) {
            return;
        }

        scanStep();
    }

    private void startScan(ClientLevel world, SelectionRegion region) {
        if (world == null || region == null) {
            reset();
            return;
        }

        activeRegion = region;
        activeWorld = world;
        activeRegionVersion = region.version();
        bounds = region.boundsForAnalysis();
        cursor = 0L;
        selectedCells = 0L;
        unloadedSelectedCells = 0L;
        counts.clear();

        if (bounds == null || bounds.volume() <= 0) {
            snapshot = SelectionBlockSnapshot.incomplete(region.shape());
            return;
        }

        snapshot = SelectionBlockSnapshot.scanning(
                region.shape(),
                0L,
                bounds.volume(),
                0L,
                0L,
                List.of()
        );
    }

    private void scanStep() {
        if (activeWorld == null || activeRegion == null || bounds == null) {
            return;
        }

        int budget = BOUNDS_CELLS_PER_TICK;
        int sizeX = bounds.sizeX();
        int sizeY = bounds.sizeY();
        long total = bounds.volume();

        while (budget-- > 0 && cursor < total) {
            long index = cursor++;

            int relX = (int) (index % sizeX);
            long yz = index / sizeX;
            int relY = (int) (yz % sizeY);
            int relZ = (int) (yz / sizeY);

            int x = bounds.minX() + relX;
            int y = bounds.minY() + relY;
            int z = bounds.minZ() + relZ;
            if (!activeRegion.containsBlock(x, y, z)) {
                continue;
            }

            selectedCells++;
            mutablePos.set(x, y, z);
            if (!isChunkLoaded(activeWorld, mutablePos)) {
                unloadedSelectedCells++;
                continue;
            }

            BlockState state = activeWorld.getBlockState(mutablePos);
            counts.merge(state, 1L, Long::sum);
        }

        if (cursor >= total) {
            snapshot = SelectionBlockSnapshot.complete(
                    activeRegion.shape(),
                    total,
                    selectedCells,
                    unloadedSelectedCells,
                    buildEntries()
            );
            return;
        }

        snapshot = SelectionBlockSnapshot.scanning(
                activeRegion.shape(),
                cursor,
                total,
                selectedCells,
                unloadedSelectedCells,
                buildEntries()
        );
    }

    private List<SelectionBlockSnapshot.BlockEntry> buildEntries() {
        if (counts.isEmpty()) {
            return List.of();
        }

        ArrayList<SelectionBlockSnapshot.BlockEntry> entries = new ArrayList<>(counts.size());
        for (Map.Entry<BlockState, Long> entry : counts.entrySet()) {
            BlockState state = entry.getKey();
            long count = entry.getValue();
            Identifier blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            if (blockId == null) {
                blockId = AIR_ID;
            }

            entries.add(new SelectionBlockSnapshot.BlockEntry(
                    state.toString(),
                    blockId.toString(),
                    blockId.getNamespace(),
                    blockId.getPath(),
                    state.getBlock().getName().getString(),
                    state.isAir(),
                    !state.getFluidState().isEmpty(),
                    state.canOcclude(),
                    count
            ));
        }
        return entries;
    }

    private static boolean isChunkLoaded(ClientLevel world, BlockPos pos) {
        return world.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4);
    }
}
