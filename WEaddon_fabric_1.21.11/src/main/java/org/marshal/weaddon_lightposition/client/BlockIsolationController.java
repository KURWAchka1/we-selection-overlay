package org.marshal.weaddon_lightposition.client;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockRenderView;
import org.marshal.weaddon_lightposition.client.overlay.SelectionIsolationMask;
import org.marshal.weaddon_lightposition.client.overlay.SelectionRegion;
import org.marshal.weaddon_lightposition.client.overlay.VoxelBounds;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class BlockIsolationController {
    private static volatile IsolationSnapshot snapshot = new IsolationSnapshot(Set.of(), null);
    private static SelectionRegion sourceRegion;
    private static long sourceRegionVersion = Long.MIN_VALUE;

    private BlockIsolationController() {
    }

    public static Set<String> selectedBlockIds() {
        return snapshot.selectedBlockIds();
    }

    public static boolean active() {
        IsolationSnapshot current = snapshot;
        return current.mask() != null && !current.selectedBlockIds().isEmpty();
    }

    public static boolean setSelectedBlockIds(Set<String> blockIds) {
        Set<String> normalized = normalizeBlockIds(blockIds);
        IsolationSnapshot current = snapshot;
        if (current.selectedBlockIds().equals(normalized)) {
            return false;
        }
        snapshot = new IsolationSnapshot(normalized, current.mask());
        return true;
    }

    public static void updateRegion(MinecraftClient client, SelectionRegion region, boolean force) {
        IsolationSnapshot current = snapshot;
        long regionVersion = region == null ? Long.MIN_VALUE : region.version();
        if (!force && region == sourceRegion && regionVersion == sourceRegionVersion) {
            return;
        }

        SelectionIsolationMask oldMask = current.mask();
        SelectionIsolationMask newMask = current.selectedBlockIds().isEmpty() || region == null
                ? null
                : region.toIsolationMask();

        sourceRegion = region;
        sourceRegionVersion = regionVersion;
        snapshot = new IsolationSnapshot(current.selectedBlockIds(), newMask);

        scheduleRerender(client, oldMask);
        if (newMask != oldMask) {
            scheduleRerender(client, newMask);
        }
    }

    public static void clearRegion(MinecraftClient client) {
        SelectionIsolationMask oldMask = snapshot.mask();
        sourceRegion = null;
        sourceRegionVersion = Long.MIN_VALUE;
        snapshot = new IsolationSnapshot(snapshot.selectedBlockIds(), null);
        scheduleRerender(client, oldMask);
    }

    public static boolean shouldHideBlock(BlockState state, BlockPos pos) {
        if (state == null || pos == null) {
            return false;
        }

        if (org.marshal.weaddon_lightposition.client.preview.CommandPreviewTerrainState.hides(pos)) return true;

        IsolationSnapshot current = snapshot;
        SelectionIsolationMask mask = current.mask();
        return mask != null
                && !current.selectedBlockIds().isEmpty()
                && mask.containsBlock(pos)
                && !current.selectedBlockIds().contains(blockId(state));
    }

    public static float blockRenderAlpha(BlockState state, BlockPos pos) {
        return 1.0F;
    }

    public static boolean shouldHideFluid(FluidState fluidState, BlockPos pos) {
        if (fluidState == null || fluidState.isEmpty() || pos == null) {
            return false;
        }

        if (org.marshal.weaddon_lightposition.client.preview.CommandPreviewTerrainState.hides(pos)) return true;

        IsolationSnapshot current = snapshot;
        SelectionIsolationMask mask = current.mask();
        return mask != null
                && !current.selectedBlockIds().isEmpty()
                && mask.containsBlock(pos)
                && !current.selectedBlockIds().contains(blockId(fluidState.getBlockState()));
    }

    public static boolean shouldHideFluid(BlockState blockState, FluidState fluidState, BlockPos pos) {
        return shouldHideFluid(fluidState, pos) && !shouldForceFullbright(blockState, pos);
    }

    public static float fluidRenderAlpha(BlockState blockState, FluidState fluidState, BlockPos pos) {
        return 1.0F;
    }

    public static boolean shouldForceFullbright(BlockState state, BlockPos pos) {
        if (state == null || pos == null) {
            return false;
        }

        if (org.marshal.weaddon_lightposition.client.preview.CommandPreviewTerrainState.hides(pos)) return false;

        IsolationSnapshot current = snapshot;
        SelectionIsolationMask mask = current.mask();
        if (mask == null || current.selectedBlockIds().isEmpty() || !mask.containsBlock(pos)) {
            return false;
        }

        return current.selectedBlockIds().contains(blockId(state));
    }

    public static boolean shouldForceFullbright(BlockState blockState, FluidState fluidState, BlockPos pos) {
        return shouldForceFullbright(blockState, pos) || shouldForceFullbrightFluid(fluidState, pos);
    }

    public static boolean shouldForceFullbrightFluid(FluidState fluidState, BlockPos pos) {
        if (fluidState == null || fluidState.isEmpty() || pos == null) {
            return false;
        }

        if (org.marshal.weaddon_lightposition.client.preview.CommandPreviewTerrainState.hides(pos)) return false;

        IsolationSnapshot current = snapshot;
        SelectionIsolationMask mask = current.mask();
        if (mask == null || current.selectedBlockIds().isEmpty() || !mask.containsBlock(pos)) {
            return false;
        }

        return current.selectedBlockIds().contains(blockId(fluidState.getBlockState()));
    }

    public static boolean shouldDrawFaceAgainstHiddenNeighbor(
            BlockRenderView world,
            BlockState state,
            Direction direction,
            BlockPos neighborPos
    ) {
        if (world == null || state == null || direction == null || neighborPos == null) {
            return false;
        }

        if (org.marshal.weaddon_lightposition.client.preview.CommandPreviewTerrainState.hides(neighborPos))
            return !shouldHideBlock(state, neighborPos.offset(direction.getOpposite()));

        BlockPos currentPos = neighborPos.offset(direction.getOpposite());
        return shouldForceFullbright(state, currentPos)
                && shouldHideBlock(world.getBlockState(neighborPos), neighborPos);
    }

    private static void scheduleRerender(MinecraftClient client, SelectionIsolationMask mask) {
        if (client == null || client.worldRenderer == null || mask == null || mask.bounds() == null) {
            return;
        }

        VoxelBounds bounds = mask.bounds();
        int minSectionX = ChunkSectionPos.getSectionCoord(bounds.minX() - 1);
        int minSectionY = ChunkSectionPos.getSectionCoord(bounds.minY() - 1);
        int minSectionZ = ChunkSectionPos.getSectionCoord(bounds.minZ() - 1);
        int maxSectionX = ChunkSectionPos.getSectionCoord(bounds.maxX() + 1);
        int maxSectionY = ChunkSectionPos.getSectionCoord(bounds.maxY() + 1);
        int maxSectionZ = ChunkSectionPos.getSectionCoord(bounds.maxZ() + 1);

        WorldRenderer renderer = client.worldRenderer;
        renderer.scheduleChunkRenders(minSectionX, minSectionY, minSectionZ, maxSectionX, maxSectionY, maxSectionZ);
    }

    private static Set<String> normalizeBlockIds(Set<String> blockIds) {
        if (blockIds == null || blockIds.isEmpty()) {
            return Set.of();
        }

        HashSet<String> normalized = new HashSet<>();
        for (String blockId : blockIds) {
            if (blockId == null) {
                continue;
            }
            String trimmed = blockId.trim();
            if (trimmed.isEmpty() || isAirBlockId(trimmed) || Identifier.tryParse(trimmed) == null) {
                continue;
            }
            normalized.add(trimmed);
        }
        if (normalized.isEmpty()) {
            return Set.of();
        }

        ArrayList<String> sorted = new ArrayList<>(normalized);
        Collections.sort(sorted);
        return Set.copyOf(sorted);
    }

    private static String blockId(BlockState state) {
        Identifier id = Registries.BLOCK.getId(state.getBlock());
        return id == null ? "minecraft:air" : id.toString();
    }

    private static boolean isAirBlockId(String blockId) {
        return "minecraft:air".equals(blockId)
                || "minecraft:cave_air".equals(blockId)
                || "minecraft:void_air".equals(blockId);
    }

    private static BlockState actualBlockState(BlockPos pos) {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.world == null ? null : client.world.getBlockState(pos);
    }

    private record IsolationSnapshot(Set<String> selectedBlockIds, SelectionIsolationMask mask) {
        private IsolationSnapshot {
            selectedBlockIds = selectedBlockIds == null ? Set.of() : Set.copyOf(selectedBlockIds);
        }
    }
}
