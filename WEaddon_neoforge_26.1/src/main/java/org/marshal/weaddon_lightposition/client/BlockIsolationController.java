package org.marshal.weaddon_lightposition.client;

import org.marshal.weaddon_lightposition.client.overlay.SelectionIsolationMask;
import org.marshal.weaddon_lightposition.client.overlay.SelectionRegion;
import org.marshal.weaddon_lightposition.client.overlay.VoxelBounds;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

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

    public static void updateRegion(Minecraft client, SelectionRegion region, boolean force) {
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

    public static void clearRegion(Minecraft client) {
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
        if (mask == null || current.selectedBlockIds().isEmpty() || !mask.containsBlock(pos)) {
            return false;
        }

        return !current.selectedBlockIds().contains(blockId(state));
    }

    public static boolean shouldHideFluid(FluidState fluidState, BlockPos pos) {
        if (fluidState == null || fluidState.isEmpty() || pos == null) {
            return false;
        }

        if (org.marshal.weaddon_lightposition.client.preview.CommandPreviewTerrainState.hides(pos)) return true;

        IsolationSnapshot current = snapshot;
        SelectionIsolationMask mask = current.mask();
        if (mask == null || current.selectedBlockIds().isEmpty() || !mask.containsBlock(pos)) {
            return false;
        }

        return !current.selectedBlockIds().contains(blockId(fluidState.createLegacyBlock()));
    }

    public static boolean shouldHideFluid(BlockState blockState, FluidState fluidState, BlockPos pos) {
        if (shouldHideFluid(fluidState, pos)) {
            return true;
        }

        if (fluidState == null || fluidState.isEmpty()) {
            return false;
        }

        return shouldHideBlock(blockState, pos);
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

        return current.selectedBlockIds().contains(blockId(fluidState.createLegacyBlock()));
    }

    public static boolean shouldDrawFaceAgainstHiddenNeighbor(
            BlockAndTintGetter world,
            BlockState state,
            Direction direction,
            BlockPos neighborPos
    ) {
        if (world == null || state == null || direction == null || neighborPos == null) {
            return false;
        }

        if (org.marshal.weaddon_lightposition.client.preview.CommandPreviewTerrainState.hides(neighborPos))
            return !shouldHideBlock(state, neighborPos.relative(direction.getOpposite()));

        IsolationSnapshot current = snapshot;
        SelectionIsolationMask mask = current.mask();
        if (mask == null || current.selectedBlockIds().isEmpty()) {
            return false;
        }

        BlockPos currentPos = neighborPos.relative(direction.getOpposite());
        if (!shouldForceFullbright(state, currentPos)) {
            return false;
        }

        BlockState neighborState = world.getBlockState(neighborPos);
        return shouldHideBlock(neighborState, neighborPos);
    }

    private static void scheduleRerender(Minecraft client, SelectionIsolationMask mask) {
        if (client == null || client.levelRenderer == null || mask == null || mask.bounds() == null) {
            return;
        }

        VoxelBounds bounds = mask.bounds();
        int minSectionX = SectionPos.blockToSectionCoord(bounds.minX() - 1);
        int minSectionY = SectionPos.blockToSectionCoord(bounds.minY() - 1);
        int minSectionZ = SectionPos.blockToSectionCoord(bounds.minZ() - 1);
        int maxSectionX = SectionPos.blockToSectionCoord(bounds.maxX() + 1);
        int maxSectionY = SectionPos.blockToSectionCoord(bounds.maxY() + 1);
        int maxSectionZ = SectionPos.blockToSectionCoord(bounds.maxZ() + 1);

        LevelRenderer renderer = client.levelRenderer;
        renderer.setSectionRangeDirty(minSectionX, minSectionY, minSectionZ, maxSectionX, maxSectionY, maxSectionZ);
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
            if (trimmed.isEmpty() || Identifier.tryParse(trimmed) == null) {
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
        Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return id == null ? "minecraft:air" : id.toString();
    }

    private record IsolationSnapshot(Set<String> selectedBlockIds, SelectionIsolationMask mask) {
        private IsolationSnapshot {
            selectedBlockIds = selectedBlockIds == null ? Set.of() : Set.copyOf(selectedBlockIds);
        }
    }
}
