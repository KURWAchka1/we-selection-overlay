package org.marshal.weaddon_lightposition.client.clipboard;

import com.sk89q.worldedit.EmptyClipboardException;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.UnknownDirectionException;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.transform.BlockTransformExtent;
import com.sk89q.worldedit.fabric.FabricAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.math.transform.Transform;
import com.sk89q.worldedit.math.transform.AffineTransform;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.block.BlockType;
import com.sk89q.worldedit.world.block.BlockTypes;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.block.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.marshal.weaddon_lightposition.preview.ClipboardTransformDraft;
import org.marshal.weaddon_lightposition.preview.ClipboardTransformStatus;

public final class WorldEditClipboardPreviewSource implements ClipboardPreviewSource {
    private static final Logger LOGGER = LoggerFactory.getLogger("worldedit_selection_overlay/clipboard");
    private static final int POLL_INTERVAL_TICKS = 5;
    private static final int SCAN_BUDGET_PER_TICK = 12_000;
    private static final long MAX_SCAN_CELLS = 524_288L;
    private static final int MAX_SOURCE_BLOCKS = 150_000;
    private static final int MAX_RENDER_BLOCKS = 18_000;
    private static final long WORK_BUDGET_NANOS = 2_000_000L;
    private static final Direction[] DIRECTIONS = Direction.values();

    private volatile ClipboardPreviewSnapshot currentSnapshot = ClipboardPreviewSnapshot.empty();
    private ClipboardPreviewSnapshot baselineSnapshot = ClipboardPreviewSnapshot.empty();
    private volatile ClipboardTransformStatus transformStatus = ClipboardTransformStatus.IDLE;
    private volatile boolean serverTaskQueued;
    private volatile long generation;

    private int tickCounter;
    private long revision;
    private long lastScheduledGeneration = -1;
    private volatile ClipboardTransformDraft transformDraft = ClipboardTransformDraft.none();
    private volatile ServerState serverState = new ServerState();

    @Override
    public void setTransformDraft(ClipboardTransformDraft draft) {
        if (transformDraft.equals(draft)) return;
        transformDraft = draft;
        generation++;
        currentSnapshot = baselineSnapshot;
        transformStatus = !draft.recognized() ? ClipboardTransformStatus.IDLE : draft.valid()
                ? ClipboardTransformStatus.UPDATING : ClipboardTransformStatus.status(draft.reason());
    }

    @Override
    public ClipboardTransformStatus transformStatus() { return transformStatus; }

    @Override
    public void tick(MinecraftClient client) {
        if (client == null || client.player == null || client.world == null) {
            clear();
            return;
        }

        IntegratedServer server = client.getServer();
        if (server == null) {
            currentSnapshot = baselineSnapshot = ClipboardPreviewSnapshot.empty();
            transformStatus = ClipboardTransformStatus.status("local_only");
            return;
        }

        tickCounter++;
        ServerState state = serverState;
        if (!state.working && lastScheduledGeneration == generation && tickCounter % POLL_INTERVAL_TICKS != 0) {
            return;
        }
        if (serverTaskQueued) {
            return;
        }

        UUID playerId = client.player.getUuid();
        long scheduledGeneration = generation;
        ClipboardTransformDraft draft = transformDraft;
        lastScheduledGeneration = scheduledGeneration;
        serverTaskQueued = true;
        server.execute(() -> {
            try {
                if (scheduledGeneration == generation) {
                    pollOnServer(server, playerId, client, scheduledGeneration, draft, state);
                }
            } catch (Throwable ex) {
                LOGGER.warn("Could not update WorldEdit clipboard preview.", ex);
                publish(client, ClipboardPreviewSnapshot.empty(), ClipboardPreviewSnapshot.empty(),
                        ClipboardTransformStatus.status("failed"), scheduledGeneration);
                client.execute(() -> { if (scheduledGeneration == generation) serverState = new ServerState(); });
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
        currentSnapshot = baselineSnapshot = ClipboardPreviewSnapshot.empty();
        transformDraft = ClipboardTransformDraft.none();
        transformStatus = ClipboardTransformStatus.IDLE;
        serverState = new ServerState();
        tickCounter = 0;
    }

    private void pollOnServer(IntegratedServer server, UUID playerId, MinecraftClient client, long scheduledGeneration,
                              ClipboardTransformDraft draft, ServerState state) {
        ServerPlayerEntity nativePlayer = server.getPlayerManager().getPlayer(playerId);
        if (nativePlayer == null) {
            clearOnClient(client, scheduledGeneration, state, "local_only");
            return;
        }

        Player actor = FabricAdapter.adaptPlayer(nativePlayer);
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(actor);
        ClipboardHolder holder;
        try {
            holder = session.getClipboard();
        } catch (EmptyClipboardException ignored) {
            clearOnClient(client, scheduledGeneration, state, "clipboard_empty");
            return;
        }

        Clipboard clipboard = holder.getClipboard();
        Transform baseTransform = holder.getTransform();
        BlockVector3 placement = placementPosition(session, actor, clipboard);
        long deadline = System.nanoTime() + WORK_BUDGET_NANOS;
        if (state.clipboard != clipboard || !clipboard.getOrigin().equals(state.origin)) {
            state.reset();
            state.clipboard = clipboard;
            state.origin = clipboard.getOrigin();
            state.job = new SnapshotJob(clipboard);
        }
        state.working = true;
        if (state.job != null) {
            processJob(state.job, deadline);
            if (!state.job.complete()) {
                publishProgress(client, placement, state.job, scheduledGeneration, draft);
                return;
            }
            state.sourceBlocks = List.copyOf(state.job.blocks);
            state.limited = state.job.limited;
            state.scanned = state.job.scannedCells;
            state.total = state.job.totalCells;
            state.job = null;
        }
        String baseKey = transformKey(baseTransform);
        if (!baseKey.equals(state.baseKey)) {
            BuildResult committed = baseKey.equals(state.draftKey) ? state.draft : null;
            state.baseKey = baseKey;
            state.base = committed;
            state.draftKey = "";
            state.draft = null;
            state.builder = null;
        }
        if (state.base == null) state.base = build(state, baseKey, baseTransform, deadline, scheduledGeneration);
        if (state.base == null) {
            publishProgress(client, placement, state, scheduledGeneration, draft);
            return;
        }
        ClipboardPreviewSnapshot baseline = snapshot(placement, state.base, state);
        ClipboardTransformStatus status = ClipboardTransformStatus.IDLE;
        Transform effective = baseTransform;
        if (draft.recognized()) {
            if (!draft.valid()) status = ClipboardTransformStatus.status(draft.reason());
            else if (!actor.hasPermission(draft.kind() == ClipboardTransformDraft.Kind.ROTATE
                    ? "worldedit.clipboard.rotate" : "worldedit.clipboard.flip")) status = ClipboardTransformStatus.status("permission");
            else {
                try {
                    AffineTransform transform = new AffineTransform();
                    if (draft.kind() == ClipboardTransformDraft.Kind.ROTATE) {
                        transform = transform.rotateY(-draft.y()).rotateX(-draft.x()).rotateZ(-draft.z());
                    } else {
                        BlockVector3 direction = WorldEdit.getInstance().getDirection(actor, draft.direction());
                        transform = transform.scale(direction.abs().multiply(-2).add(1, 1, 1).toVector3());
                    }
                    // Match ClipboardCommands without modifying the real ClipboardHolder.
                    effective = baseTransform.combine(transform);
                    status = new ClipboardTransformStatus("clipboard_ready", draft.approximate());
                } catch (UnknownDirectionException ignored) { status = ClipboardTransformStatus.status("clipboard_direction"); }
            }
        }
        BuildResult result = state.base;
        String effectiveKey = transformKey(effective);
        if (!effectiveKey.equals(baseKey)) {
            if (!effectiveKey.equals(state.draftKey)) {
                state.draftKey = effectiveKey;
                state.draft = null;
            }
            if (state.draft == null) state.draft = build(state, effectiveKey, effective, deadline, scheduledGeneration);
            if (state.draft == null) {
                publish(client, baseline, baseline, ClipboardTransformStatus.UPDATING, scheduledGeneration);
                return;
            }
            result = state.draft;
        } else {
            state.builder = null;
        }
        state.working = false;
        publish(client, snapshot(placement, result, state), baseline, status, scheduledGeneration);
    }

    private BuildResult build(ServerState state, String key, Transform transform, long deadline, long scheduledGeneration) {
        if (state.builder == null || !state.builder.key.equals(key)) state.builder = new BuildJob(key, state.sourceBlocks, transform);
        BuildResult result = state.builder.advance(deadline, () -> scheduledGeneration != generation);
        if (result != null) state.builder = null;
        return result;
    }

    private ClipboardPreviewSnapshot snapshot(BlockVector3 placement, BuildResult result, ServerState state) {
        return new ClipboardPreviewSnapshot(++revision, toNativeBlockPos(placement), result.blocks(), result.bounds(),
                state.scanned, state.total, false, state.limited || result.limited());
    }

    private void processJob(SnapshotJob job, long deadline) {
        int processed = 0;
        while (job.iterator.hasNext()
                && processed < SCAN_BUDGET_PER_TICK
                && job.scannedCells < MAX_SCAN_CELLS
                && job.blocks.size() < MAX_SOURCE_BLOCKS) {
            if ((processed & 63) == 0 && System.nanoTime() >= deadline) return;
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

    private void publishProgress(MinecraftClient client, BlockVector3 placement, SnapshotJob job, long scheduledGeneration,
                                 ClipboardTransformDraft draft) {
        publishProgress(client, placement, job.scannedCells, job.totalCells, job.limited, scheduledGeneration, draft);
    }

    private void publishProgress(MinecraftClient client, BlockVector3 placement, ServerState state, long scheduledGeneration,
                                 ClipboardTransformDraft draft) {
        publishProgress(client, placement, state.scanned, state.total, state.limited, scheduledGeneration, draft);
    }

    private void publishProgress(MinecraftClient client, BlockVector3 placement, long scanned, long total, boolean limited,
                                 long scheduledGeneration, ClipboardTransformDraft draft) {
        publish(
                client,
                new ClipboardPreviewSnapshot(
                        ++revision,
                        toNativeBlockPos(placement),
                        List.of(),
                        null,
                        scanned,
                        total,
                        true,
                        limited
                ),
                ClipboardPreviewSnapshot.empty(),
                !draft.recognized() ? ClipboardTransformStatus.IDLE : draft.valid()
                        ? ClipboardTransformStatus.UPDATING : ClipboardTransformStatus.status(draft.reason()),
                scheduledGeneration
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
        return coordinateBits(vector.x()) + "," + coordinateBits(vector.y()) + "," + coordinateBits(vector.z());
    }

    private static long coordinateBits(double value) {
        return Double.doubleToLongBits(value == 0 ? 0 : value);
    }

    private static boolean isAir(BlockType type) {
        return type == null || type == BlockTypes.AIR || type == BlockTypes.CAVE_AIR || type == BlockTypes.VOID_AIR;
    }

    private static BlockPos toNativeBlockPos(BlockVector3 vector) {
        return new BlockPos(vector.x(), vector.y(), vector.z());
    }

    private void publish(MinecraftClient client, ClipboardPreviewSnapshot snapshot, ClipboardPreviewSnapshot baseline,
                         ClipboardTransformStatus status, long scheduledGeneration) {
        client.execute(() -> {
            if (scheduledGeneration == generation) {
                currentSnapshot = snapshot;
                baselineSnapshot = baseline;
                if (!transformStatus.equals(status)) transformStatus = status;
            }
        });
    }

    private void clearOnClient(MinecraftClient client, long scheduledGeneration, ServerState state, String reason) {
        state.reset();
        publish(client, ClipboardPreviewSnapshot.empty(), ClipboardPreviewSnapshot.empty(),
                ClipboardTransformStatus.status(reason), scheduledGeneration);
    }

    private static final class ServerState {
        private Clipboard clipboard;
        private BlockVector3 origin;
        private SnapshotJob job;
        private List<SourceBlock> sourceBlocks = List.of();
        private BuildResult base, draft;
        private String baseKey = "", draftKey = "";
        private BuildJob builder;
        private boolean limited;
        private long scanned, total;
        private volatile boolean working;

        void reset() {
            clipboard = null; origin = null; job = null; sourceBlocks = List.of();
            base = draft = null; baseKey = draftKey = ""; builder = null;
            limited = false; scanned = total = 0; working = false;
        }
    }

    private record SourceBlock(int x, int y, int z, com.sk89q.worldedit.world.block.BlockState state) {
    }

    private record BuildResult(List<ClipboardPreviewBlock> blocks, ClipboardPreviewBounds bounds, boolean limited) {
        static BuildResult empty() {
            return new BuildResult(List.of(), null, false);
        }
    }

    private static final class BuildJob {
        private final String key;
        private final List<SourceBlock> source;
        private final Transform transform;
        private final Long2ObjectOpenHashMap<ClipboardPreviewBlock> byPosition;
        private final IdentityHashMap<com.sk89q.worldedit.world.block.BlockState, BlockState> states = new IdentityHashMap<>();
        private final ArrayList<ClipboardPreviewBlock> visible = new ArrayList<>();
        private Iterator<ClipboardPreviewBlock> exposed;
        private int index;
        private int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        private int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        private boolean limited;

        BuildJob(String key, List<SourceBlock> source, Transform transform) {
            this.key = key; this.source = source; this.transform = transform;
            byPosition = new Long2ObjectOpenHashMap<>(source.size());
        }

        BuildResult advance(long deadline, BooleanSupplier obsolete) {
            int processed = 0;
            while (index < source.size()) {
                if ((processed & 63) == 0 && (obsolete.getAsBoolean() || System.nanoTime() >= deadline || processed >= 4096)) return null;
                SourceBlock block = source.get(index++);
                processed++;
                BlockState state = states.get(block.state());
                if (state == null) {
                    state = FabricAdapter.adapt(BlockTransformExtent.transform(block.state(), transform).toImmutableState());
                    states.put(block.state(), state);
                }
                if (state == null || state.isAir()) continue;
                BlockVector3 point = transform.apply(Vector3.at(block.x(), block.y(), block.z())).toBlockPoint();
                int x = point.x(), y = point.y(), z = point.z();
                byPosition.put(BlockPos.asLong(x, y, z), new ClipboardPreviewBlock(x, y, z, state));
                minX = Math.min(minX, x); minY = Math.min(minY, y); minZ = Math.min(minZ, z);
                maxX = Math.max(maxX, x); maxY = Math.max(maxY, y); maxZ = Math.max(maxZ, z);
            }
            if (byPosition.isEmpty()) return BuildResult.empty();
            if (exposed == null) exposed = byPosition.values().iterator();
            while (exposed.hasNext()) {
                if ((processed & 63) == 0 && (obsolete.getAsBoolean() || System.nanoTime() >= deadline || processed >= 4096)) return null;
                ClipboardPreviewBlock block = exposed.next();
                processed++;
                if (isHiddenInsideClipboard(block, byPosition)) continue;
                if (visible.size() >= MAX_RENDER_BLOCKS) { limited = true; continue; }
                visible.add(block);
            }
            return new BuildResult(List.copyOf(visible), new ClipboardPreviewBounds(minX, minY, minZ, maxX, maxY, maxZ), limited);
        }
    }

    private static final class SnapshotJob {
        private final Clipboard clipboard;
        private final BlockVector3 origin;
        private final Iterator<BlockVector3> iterator;
        private final ArrayList<SourceBlock> blocks = new ArrayList<>();
        private final long totalCells;

        private long scannedCells;
        private boolean finished;
        private boolean limited;

        private SnapshotJob(Clipboard clipboard) {
            this.clipboard = clipboard;
            this.origin = clipboard.getOrigin();
            Region region = clipboard.getRegion();
            this.iterator = region.iterator();
            this.totalCells = region.getVolume();
        }

        private boolean complete() {
            return finished;
        }
    }
}
