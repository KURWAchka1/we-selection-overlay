package org.marshal.weaddon_lightposition.client;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
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

    private static void scheduleRerender(MinecraftClient client, SelectionIsolationMask mask) {
        if (client == null || client.world == null || client.worldRenderer == null
                || client.getCameraEntity() == null || mask == null || mask.bounds() == null) {
            return;
        }

        VoxelBounds bounds = mask.bounds();
        BlockPos center = client.getCameraEntity().getBlockPos();
        int distance = client.options.getClampedViewDistance() + 2;
        int minX = Math.max((bounds.minX() - 1) >> 4, (center.getX() >> 4) - distance);
        int maxX = Math.min((bounds.maxX() + 1) >> 4, (center.getX() >> 4) + distance);
        int minZ = Math.max((bounds.minZ() - 1) >> 4, (center.getZ() >> 4) - distance);
        int maxZ = Math.min((bounds.maxZ() + 1) >> 4, (center.getZ() >> 4) + distance);
        int minY = Math.max((bounds.minY() - 1) >> 4, client.world.getBottomY() >> 4);
        int maxY = Math.min((bounds.maxY() + 1) >> 4, (client.world.getTopY() - 1) >> 4);
        // The six-coordinate vanilla overload visits every block, not every section.
        for (int z = minZ; z <= maxZ; z++) for (int x = minX; x <= maxX; x++) {
            if (!client.world.getChunkManager().isChunkLoaded(x, z)) continue;
            for (int y = minY; y <= maxY; y++) client.worldRenderer.scheduleBlockRender(x, y, z);
        }
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

    private record IsolationSnapshot(Set<String> selectedBlockIds, SelectionIsolationMask mask) {
        private IsolationSnapshot {
            selectedBlockIds = selectedBlockIds == null ? Set.of() : Set.copyOf(selectedBlockIds);
        }
    }
}
