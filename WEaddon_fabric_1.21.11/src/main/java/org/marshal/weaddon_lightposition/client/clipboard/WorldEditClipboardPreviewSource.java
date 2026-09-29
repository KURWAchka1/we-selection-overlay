package org.marshal.weaddon_lightposition.client.clipboard;

import com.sk89q.worldedit.EmptyClipboardException;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.transform.BlockTransformExtent;
import com.sk89q.worldedit.fabric.FabricAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.math.transform.Transform;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.block.BlockType;
import com.sk89q.worldedit.world.block.BlockTypes;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class WorldEditClipboardPreviewSource implements ClipboardPreviewSource {
    private static final Logger LOGGER = LoggerFactory.getLogger("worldedit_selection_overlay/clipboard");
    private static final int POLL_INTERVAL_TICKS = 5;
    private static final int SCAN_BUDGET_PER_TICK = 12_000;
    private static final long MAX_SCAN_CELLS = 524_288L;
    private static final int MAX_SOURCE_BLOCKS = 150_000;
    private static final int MAX_RENDER_BLOCKS = 18_000;
    private static final Direction[] DIRECTIONS = Direction.values();

    private volatile ClipboardPreviewSnapshot currentSnapshot = ClipboardPreviewSnapshot.empty();
    private volatile boolean serverTaskQueued;
    private volatile long generation;

    private int tickCounter;
    private long revision;
    private int cachedClipboardIdentity;
    private String cachedTransformKey = "";
    private List<SourceBlock> cachedSourceBlocks = List.of();
    private BuildResult cachedBuild = BuildResult.empty();
    private boolean cachedSourceLimited;
    private SnapshotJob job;

    @Override
    public void tick(MinecraftClient client) {
        if (client == null || client.player == null || client.world == null) {
            clear();
            return;
        }

        IntegratedServer server = client.getServer();
        if (server == null) {
            clear();
            return;
        }

        tickCounter++;
        if (job == null && tickCounter % POLL_INTERVAL_TICKS != 0) {
            return;
        }
        if (serverTaskQueued) {
            return;
        }

        UUID playerId = client.player.getUuid();
        long scheduledGeneration = generation;
        serverTaskQueued = true;
        server.execute(() -> {
            try {
                if (scheduledGeneration == generation) {
                    pollOnServer(server, playerId, client, scheduledGeneration);
                }
            } catch (Throwable ex) {
                LOGGER.warn("Could not update WorldEdit clipboard preview.", ex);
                publish(client, ClipboardPreviewSnapshot.empty(), scheduledGeneration);
                resetServerState();
            } finally {
                serverTaskQueued = false;
            }
        });
    }

    @Override
    public ClipboardPreviewSnapshot snapshot() {
        return currentSnapshot;
    }

    @Override
    public void clear() {
        generation++;
        currentSnapshot = ClipboardPreviewSnapshot.empty();
        resetServerState();
    }

    private void pollOnServer(IntegratedServer server, UUID playerId, MinecraftClient client, long scheduledGeneration) {
        ServerPlayerEntity nativePlayer = server.getPlayerManager().getPlayer(playerId);
        if (nativePlayer == null) {
            clearOnClient(client, scheduledGeneration);
            return;
        }

        Player actor = FabricAdapter.adaptPlayer(nativePlayer);
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(actor);
        ClipboardHolder holder;
        try {
            holder = session.getClipboard();
        } catch (EmptyClipboardException ignored) {
            clearOnClient(client, scheduledGeneration);
            return;
        }

        Clipboard clipboard = holder.getClipboard();
        Transform transform = holder.getTransform();
        BlockVector3 placement = placementPosition(session, actor, clipboard);
        int clipboardIdentity = System.identityHashCode(clipboard);
        String transformKey = transformKey(transform);

        if (job != null && job.clipboard != clipboard) {
            job = null;
        }

        if (cachedClipboardIdentity != clipboardIdentity && job == null) {
            cachedSourceBlocks = List.of();
            cachedBuild = BuildResult.empty();
            cachedTransformKey = "";
            job = new SnapshotJob(clipboard, clipboardIdentity);
        }

        if (job != null) {
            processJob(job);
            publishProgress(client, placement, job, scheduledGeneration);
            if (!job.complete()) {
                return;
            }

            cachedClipboardIdentity = job.clipboardIdentity;
            cachedSourceBlocks = List.copyOf(job.blocks);
            cachedSourceLimited = job.limited;
            cachedBuild = BuildResult.empty();
            cachedTransformKey = "";
            job = null;
        }

        if (cachedBuild.isEmpty() || !cachedTransformKey.equals(transformKey)) {
            cachedBuild = buildRenderBlocks(cachedSourceBlocks, transform);
            cachedTransformKey = transformKey;
        }

        publish(
                client,
                new ClipboardPreviewSnapshot(
                        ++revision,
                        toNativeBlockPos(placement),
                        cachedBuild.blocks(),
                        cachedBuild.bounds(),
                        cachedSourceBlocks.size(),
                        cachedSourceBlocks.size(),
                        false,
                        cachedSourceLimited || cachedBuild.limited()
                ),
                scheduledGeneration
        );
    }

    private void processJob(SnapshotJob job) {
        int processed = 0;
        while (job.iterator.hasNext()
                && processed < SCAN_BUDGET_PER_TICK
                && job.scannedCells < MAX_SCAN_CELLS
                && job.blocks.size() < MAX_SOURCE_BLOCKS) {
            BlockVector3 position = job.iterator.next();
            processed++;
            job.scannedCells++;

            com.sk89q.worldedit.world.block.BlockState worldEditState = job.clipboard.getBlock(position).toImmutableState();
            if (isAir(worldEditState.getBlockType())) {
                continue;
            }

            BlockVector3 relative = position.subtract(job.origin);
            job.blocks.add(new SourceBlock(relative.x(), relative.y(), relative.z(), worldEditState));
        }

        if (!job.iterator.hasNext() || job.scannedCells >= MAX_SCAN_CELLS || job.blocks.size() >= MAX_SOURCE_BLOCKS) {
            job.finished = true;
            job.limited = job.scannedCells < job.totalCells || job.blocks.size() >= MAX_SOURCE_BLOCKS;
        }
    }

    private void publishProgress(MinecraftClient client, BlockVector3 placement, SnapshotJob job, long scheduledGeneration) {
        publish(
                client,
                new ClipboardPreviewSnapshot(
                        ++revision,
                        toNativeBlockPos(placement),
                        List.of(),
                        null,
                        job.scannedCells,
                        job.totalCells,
                        true,
                        job.limited
                ),
                scheduledGeneration
        );
    }

    private static BuildResult buildRenderBlocks(List<SourceBlock> sourceBlocks, Transform transform) {
        if (sourceBlocks.isEmpty()) {
            return BuildResult.empty();
        }

        Long2ObjectOpenHashMap<ClipboardPreviewBlock> byPosition = new Long2ObjectOpenHashMap<>(sourceBlocks.size());
        boolean limited = false;

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (SourceBlock sourceBlock : sourceBlocks) {
            BlockVector3 transformed = transform
                    .apply(Vector3.at(sourceBlock.x(), sourceBlock.y(), sourceBlock.z()))
                    .toBlockPoint();
            int x = transformed.x();
            int y = transformed.y();
            int z = transformed.z();
            com.sk89q.worldedit.world.block.BlockState transformedState =
                    BlockTransformExtent.transform(sourceBlock.state(), transform).toImmutableState();
            BlockState nativeState = FabricAdapter.adapt(transformedState);
            if (nativeState == null || nativeState.isAir()) {
                continue;
            }

            ClipboardPreviewBlock block = new ClipboardPreviewBlock(x, y, z, nativeState);
            byPosition.put(BlockPos.asLong(x, y, z), block);
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
        }

        if (byPosition.isEmpty()) {
            return BuildResult.empty();
        }

        ArrayList<ClipboardPreviewBlock> visibleBlocks = new ArrayList<>(Math.min(byPosition.size(), MAX_RENDER_BLOCKS));
        for (ClipboardPreviewBlock block : byPosition.values()) {
            if (isHiddenInsideClipboard(block, byPosition)) {
                continue;
            }
            if (visibleBlocks.size() >= MAX_RENDER_BLOCKS) {
                limited = true;
                continue;
            }
            visibleBlocks.add(block);
        }

        if (visibleBlocks.isEmpty()) {
            visibleBlocks.add(byPosition.values().iterator().next());
        }

        return new BuildResult(
                List.copyOf(visibleBlocks),
                new ClipboardPreviewBounds(minX, minY, minZ, maxX, maxY, maxZ),
                limited
        );
    }

    private static boolean isHiddenInsideClipboard(
            ClipboardPreviewBlock block,
            Long2ObjectOpenHashMap<ClipboardPreviewBlock> byPosition
    ) {
        if (!block.state().isOpaque()) {
            return false;
        }

        for (Direction direction : DIRECTIONS) {
            ClipboardPreviewBlock neighbor = byPosition.get(BlockPos.asLong(
                    block.x() + direction.getOffsetX(),
                    block.y() + direction.getOffsetY(),
                    block.z() + direction.getOffsetZ()
            ));
            if (neighbor == null || !neighbor.state().isOpaque()) {
                return false;
            }
        }
        return true;
    }

    private static BlockVector3 placementPosition(LocalSession session, Player actor, Clipboard clipboard) {
        try {
            return session.getPlacementPosition(actor);
        } catch (IncompleteRegionException ignored) {
            try {
                return actor.getBlockIn().toVector().toBlockPoint();
            } catch (RuntimeException ex) {
                return clipboard.getOrigin();
            }
        }
    }

    private static String transformKey(Transform transform) {
        return vectorKey(transform.apply(Vector3.ZERO))
                + '|'
                + vectorKey(transform.apply(Vector3.UNIT_X))
                + '|'
                + vectorKey(transform.apply(Vector3.UNIT_Y))
                + '|'
                + vectorKey(transform.apply(Vector3.UNIT_Z));
    }

    private static String vectorKey(Vector3 vector) {
        return quantize(vector.x()) + "," + quantize(vector.y()) + "," + quantize(vector.z());
    }

    private static long quantize(double value) {
        return Math.round(value * 1_000_000.0);
    }

    private static boolean isAir(BlockType type) {
        return type == null || type == BlockTypes.AIR || type == BlockTypes.CAVE_AIR || type == BlockTypes.VOID_AIR;
    }

    private static BlockPos toNativeBlockPos(BlockVector3 vector) {
        return new BlockPos(vector.x(), vector.y(), vector.z());
    }

    private void publish(MinecraftClient client, ClipboardPreviewSnapshot snapshot, long scheduledGeneration) {
        client.execute(() -> {
            if (scheduledGeneration == generation) {
                currentSnapshot = snapshot;
            }
        });
    }

    private void clearOnClient(MinecraftClient client, long scheduledGeneration) {
        resetServerState();
        publish(client, ClipboardPreviewSnapshot.empty(), scheduledGeneration);
    }

    private void resetServerState() {
        cachedClipboardIdentity = 0;
        cachedTransformKey = "";
        cachedSourceBlocks = List.of();
        cachedBuild = BuildResult.empty();
        cachedSourceLimited = false;
        job = null;
        serverTaskQueued = false;
        tickCounter = 0;
    }

    private record SourceBlock(int x, int y, int z, com.sk89q.worldedit.world.block.BlockState state) {
    }

    private record BuildResult(List<ClipboardPreviewBlock> blocks, ClipboardPreviewBounds bounds, boolean limited) {
        static BuildResult empty() {
            return new BuildResult(List.of(), null, false);
        }

        boolean isEmpty() {
            return bounds == null || blocks.isEmpty();
        }
    }

    private static final class SnapshotJob {
        private final Clipboard clipboard;
        private final int clipboardIdentity;
        private final BlockVector3 origin;
        private final Iterator<BlockVector3> iterator;
        private final ArrayList<SourceBlock> blocks = new ArrayList<>();
        private final long totalCells;

        private long scannedCells;
        private boolean finished;
        private boolean limited;

        private SnapshotJob(Clipboard clipboard, int clipboardIdentity) {
            this.clipboard = clipboard;
            this.clipboardIdentity = clipboardIdentity;
            this.origin = clipboard.getOrigin();
            Region region = clipboard.getRegion();
            this.iterator = region.iterator();
            this.totalCells = Math.min(region.getVolume(), MAX_SCAN_CELLS);
        }

        private boolean complete() {
            return finished;
        }
    }
}
