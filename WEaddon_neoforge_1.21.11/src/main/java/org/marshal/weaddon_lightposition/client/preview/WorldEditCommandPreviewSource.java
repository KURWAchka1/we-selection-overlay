package org.marshal.weaddon_lightposition.client.preview;

import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.extension.input.ParserContext;
import com.sk89q.worldedit.neoforge.NeoForgeAdapter;
import com.sk89q.worldedit.function.mask.Mask;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.world.block.BaseBlock;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import org.marshal.weaddon_lightposition.preview.PreviewChange;
import org.marshal.weaddon_lightposition.preview.PreviewChangeKind;
import org.marshal.weaddon_lightposition.preview.PreviewCommand;
import org.marshal.weaddon_lightposition.preview.PreviewExtent;
import org.marshal.weaddon_lightposition.preview.PreviewOptions;
import org.marshal.weaddon_lightposition.preview.PreviewStatistics;
import org.marshal.weaddon_lightposition.preview.WorldEditPreviewProgram;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class WorldEditCommandPreviewSource implements CommandPreviewSource {
    private static final Logger LOGGER = LoggerFactory.getLogger("worldedit_selection_overlay/command-preview");
    private static final int WORK_STEPS = 1_024;
    private static final long WORK_NANOS = 2_000_000;
    private static final Direction[] DIRECTIONS = Direction.values();
    private final AtomicLong epoch = new AtomicLong();
    private final AtomicBoolean queued = new AtomicBoolean();
    private final AtomicBoolean selectionDirty = new AtomicBoolean();
    private record Request(long token, String draft, PreviewOptions options, UUID player, Object level, IntegratedServer server,
                           List<Chunk> chunks) { }
    private record Chunk(int x, int z) { }
    private List<Chunk> loaded = List.of();
    private final java.util.Set<Chunk> trackedChunks = new java.util.HashSet<>();
    private Object trackedLevel;
    private boolean chunksDirty = true;
    private record Coverage(int minX, int minZ, int maxX, int maxZ) {
        boolean contains(Chunk c) { return c.x >= minX && c.x <= maxX && c.z >= minZ && c.z <= maxZ; }
    }
    private volatile Coverage coverage;

    public WorldEditCommandPreviewSource() {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.level.ChunkEvent.Load event) -> {
            if (event.getLevel() instanceof net.minecraft.client.multiplayer.ClientLevel)
                chunkChanged(event.getLevel(), event.getChunk().getPos().x, event.getChunk().getPos().z, true);
        });
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.level.ChunkEvent.Unload event) -> {
            if (event.getLevel() instanceof net.minecraft.client.multiplayer.ClientLevel)
                chunkChanged(event.getLevel(), event.getChunk().getPos().x, event.getChunk().getPos().z, false);
        });
    }

    private void chunkChanged(Object level, int x, int z, boolean present) {
        if (trackedLevel != level) { trackedLevel = level; trackedChunks.clear(); }
        Chunk chunk = new Chunk(x, z);
        if (present ? trackedChunks.add(chunk) : trackedChunks.remove(chunk)) chunksDirty = true;
    }
    private volatile Request requested;
    private volatile CommandPreviewSnapshot snapshot = CommandPreviewSnapshot.empty();
    // Only the integrated server thread owns these fields.
    private long jobEpoch = -1;
    private Job job;
    private boolean failed;
    private boolean terminalFailure;
    private long revision;
    private volatile long idleEpoch = -1;
    private int retryAt;
    private int workTick = -1;

    @Override public void tick(Minecraft client, String draft, PreviewOptions options) {
        long token = epoch.get();
        if (draft.isEmpty() && idleEpoch == token) return;
        IntegratedServer server = client.getSingleplayerServer();
        if (server == null || client.player == null || client.level == null) {
            if (!draft.isEmpty()) snapshot = CommandPreviewSnapshot.status(CommandPreviewSnapshot.State.UNAVAILABLE, "local_only", "");
            return;
        }
        if (trackedLevel != client.level) {
            trackedLevel = client.level; trackedChunks.clear(); trackedChunks.addAll(loadedChunks(client)); chunksDirty = true;
        }
        if (chunksDirty) {
            chunksDirty = false;
            List<Chunk> current = trackedChunks.stream().sorted(java.util.Comparator.comparingInt(Chunk::z).thenComparingInt(Chunk::x)).toList();
            Coverage bounds = coverage;
            boolean changed = bounds != null && !current.stream().filter(bounds::contains).toList().equals(loaded.stream().filter(bounds::contains).toList());
            loaded = current;
            if (!draft.isEmpty() && changed) { clear(); token = epoch.get(); }
        }
        requested = new Request(token, draft, options, client.player.getUUID(), client.level, server, loaded);
        if (!queued.compareAndSet(false, true)) return;
        server.execute(() -> {
            Request request = requested;
            long currentToken = request.token;
            try {
                if (epoch.get() != currentToken || request.server != server) return;
                if (jobEpoch != currentToken) { jobEpoch = currentToken; job = null; failed = false; terminalFailure = false; }
                if (request.draft.isEmpty()) { job = null; idleEpoch = currentToken; return; }
                ServerPlayer nativePlayer = server.getPlayerList().getPlayer(request.player);
                if (nativePlayer == null) return;
                Player actor = NeoForgeAdapter.adaptPlayer(nativePlayer);
                LocalSession session = WorldEdit.getInstance().getSessionManager().get(actor);
                if (job != null && !actor.hasPermission(job.permission)) throw new IllegalArgumentException("permission");
                if (job != null && (job.globalMask != session.getMask() || job.changeLimit != session.getBlockChangeLimit()
                        || session.hasWorldOverride() || session.isUsingInventory())) {
                    clear();
                    job = null;
                    return;
                }
                if (failed && (terminalFailure || server.getTickCount() < retryAt)) return;
                // Rapid input replaces the queued request; it never queues one task per character.
                if (workTick == server.getTickCount()) return;
                workTick = server.getTickCount();
                long deadline = System.nanoTime() + WORK_NANOS;
                failed = false;
                if (job == null) {
                    job = createJob(actor, session, request.draft, request.chunks);
                    coverage = new Coverage(job.memory.getMinimumPoint().x() >> 4, job.memory.getMinimumPoint().z() >> 4,
                            job.memory.getMaximumPoint().x() >> 4, job.memory.getMaximumPoint().z() >> 4);
                }
                if (job.published) {
                    if (selectionDirty.getAndSet(false) || server.getTickCount() % 10 == 0 || job.verifying != null)
                        verify(nativePlayer, actor, session, job, deadline);
                    return;
                }
                // Finished phases fall through immediately, sharing one budget rather than one tick each.
                while (epoch.get() == currentToken && !job.published && System.nanoTime() < deadline) {
                    if (!job.captured) {
                        capture(nativePlayer, actor, job, deadline);
                    } else if (!job.program.complete()) {
                        job.program.advance(WORK_STEPS, deadline);
                    } else if (job.needsValidation) {
                        verify(nativePlayer, actor, session, job, deadline);
                    } else {
                        finish(job, client, request.level, currentToken, request.options, deadline);
                    }
                }
                if (epoch.get() == currentToken && !job.published && job.progressScanned != job.scanned) {
                    job.progressScanned = job.scanned;
                    publish(client, request.level, currentToken, new CommandPreviewSnapshot(++revision,
                        CommandPreviewSnapshot.State.COMPUTING, "", "", job.scanned, job.total,
                        job.program != null && job.program.random(), false, List.of(), PreviewStatistics.empty(), CommandPreviewMask.empty()));
                }
            } catch (IncompleteRegionException ex) {
                fail(client, request.level, currentToken, CommandPreviewSnapshot.State.UNAVAILABLE, "selection", "");
            } catch (WorldEditException ex) {
                fail(client, request.level, currentToken, CommandPreviewSnapshot.State.ERROR, "invalid", shortDetail(ex.getMessage()));
            } catch (IllegalArgumentException ex) {
                String reason = ex.getMessage();
                boolean limit = "too_large".equals(reason) || "worldedit_limit".equals(reason);
                fail(client, request.level, currentToken, limit ? CommandPreviewSnapshot.State.LIMIT : CommandPreviewSnapshot.State.ERROR,
                        reason == null ? "invalid" : reason, "");
            } catch (RuntimeException | LinkageError ex) {
                terminalFailure = true;
                LOGGER.warn("Command preview failed safely; no editing session was created.", ex);
                fail(client, request.level, currentToken, CommandPreviewSnapshot.State.ERROR, "failed", "");
            } finally {
                if (failed && retryAt <= server.getTickCount()) retryAt = server.getTickCount() + 20;
                queued.set(false);
            }
        });
    }

    @Override public CommandPreviewSnapshot snapshot() { return snapshot; }
    @Override public void clear() { epoch.incrementAndGet(); snapshot = CommandPreviewSnapshot.empty(); }
    @Override public void selectionChanged() { selectionDirty.set(true); }

    private static List<Chunk> loadedChunks(Minecraft client) {
        ArrayList<Chunk> chunks = new ArrayList<>();
        int x = client.player.blockPosition().getX() >> 4, z = client.player.blockPosition().getZ() >> 4;
        int radius = Math.max(2, client.options.getEffectiveRenderDistance()) + 3;
        for (int cz = z - radius; cz <= z + radius; cz++) for (int cx = x - radius; cx <= x + radius; cx++) {
            if (client.level.getChunkSource().getChunk(cx, cz, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false) != null)
                chunks.add(new Chunk(cx, cz));
        }
        return List.copyOf(chunks);
    }

    private static Job createJob(Player actor, LocalSession session, String draft, List<Chunk> chunks) throws WorldEditException {
        PreviewCommand command = PreviewCommand.parse(draft);
        if (session.hasWorldOverride() || session.isUsingInventory()) throw new IllegalArgumentException("session_state");
        String permission = "worldedit.region." + command.kind().name().toLowerCase(java.util.Locale.ROOT);
        if (!actor.hasPermission(permission)) throw new IllegalArgumentException("permission");
        Region region = session.getSelection(actor.getWorld()).clone();
        BlockVector3 min = region.getMinimumPoint(), max = region.getMaximumPoint();
        if (command.kind() == PreviewCommand.Kind.OVERLAY || command.kind() == PreviewCommand.Kind.FLORA || command.kind() == PreviewCommand.Kind.NATURALIZE) max = max.add(0, 1, 0);
        if (command.kind() == PreviewCommand.Kind.LINE || command.kind() == PreviewCommand.Kind.CURVE) {
            WorldEdit.getInstance().checkMaxRadius(command.thickness());
            int radius = (int) Math.ceil(command.thickness());
            BlockVector3 padding = BlockVector3.at(radius, radius, radius);
            if (command.kind() == PreviewCommand.Kind.CURVE) {
                // Zero-tension Catmull-Rom tangents can overshoot control points.
                // A quarter of each axis span is a conservative capture bound.
                var span = max.subtract(min);
                padding = padding.add(span.x() / 4 + 1, span.y() / 4 + 1, span.z() / 4 + 1);
            }
            min = min.subtract(padding); max = max.add(padding);
            min = min.withY(Math.max(min.y(), actor.getWorld().getMinimumPoint().y()));
            max = max.withY(Math.min(max.y(), actor.getWorld().getMaximumPoint().y()));
        }
        if (command.kind() == PreviewCommand.Kind.HOLLOW) {
            min = min.subtract(1, 1, 1); max = max.add(1, 1, 1);
        }
        if (min.y() < actor.getWorld().getMinimumPoint().y() || max.y() > actor.getWorld().getMaximumPoint().y())
            throw new IllegalArgumentException("height");
        ArrayList<CuboidRegion> boxes = new ArrayList<>();
        long volume = 0;
        for (Chunk chunk : chunks) {
            int minX = Math.max(min.x(), chunk.x * 16), maxX = Math.min(max.x(), chunk.x * 16 + 15);
            int minZ = Math.max(min.z(), chunk.z * 16), maxZ = Math.min(max.z(), chunk.z * 16 + 15);
            if (minX > maxX || minZ > maxZ) continue;
            boxes.add(new CuboidRegion(BlockVector3.at(minX, min.y(), minZ), BlockVector3.at(maxX, max.y(), maxZ)));
            volume += (long) (maxX - minX + 1) * (maxZ - minZ + 1) * (max.y() - min.y() + 1);
        }
        if (boxes.isEmpty()) throw new IllegalArgumentException("unloaded");
        PreviewExtent memory = new PreviewExtent(min, max);
        ParserContext parser = new ParserContext();
        parser.setActor(actor);
        parser.setSession(session);
        parser.setWorld(actor.getWorld());
        parser.setRestricted(true);
        parser.setTryLegacy(true);
        WorldEditPreviewProgram program = new WorldEditPreviewProgram(command, region, memory, parser, session.getMask(),
                () -> boxes.stream().flatMap(box -> java.util.stream.StreamSupport.stream(box.spliterator(), false)).iterator(),
                () -> boxes.stream().flatMap(box -> java.util.stream.StreamSupport.stream(box.asFlatRegion().spliterator(), false)).iterator());
        return new Job(region, memory, boxes.stream().flatMap(box -> java.util.stream.StreamSupport.stream(box.spliterator(), false)).iterator(), volume, program,
                session.getMask(), session.getBlockChangeLimit(), permission);
    }

    private static void capture(ServerPlayer nativePlayer, Player actor, Job job, long deadline) {
        int count = 0;
        while (job.capture.hasNext() && count++ < WORK_STEPS && System.nanoTime() < deadline) {
            BlockVector3 point = job.capture.next();
            if (!nativePlayer.level().hasChunk(point.x() >> 4, point.z() >> 4)) throw new IllegalArgumentException("unloaded");
            job.memory.capture(point, actor.getWorld().getFullBlock(point));
            job.scanned++;
        }
        job.captured = !job.capture.hasNext();
    }

    private void finish(Job job, Minecraft client, Object level, long token, PreviewOptions options, long deadline) {
        if (job.changeLimit >= 0 && job.memory.changes().size() > job.changeLimit) throw new IllegalArgumentException("worldedit_limit");
        if (job.output == null) job.output = job.memory.changes().values().iterator();
        int count = 0;
        while (job.output.hasNext() && count++ < WORK_STEPS && System.nanoTime() < deadline) {
            PreviewChange change = job.output.next();
            if (!change.before().getBlockType().getMaterial().isAir())
                job.terrain.add(change.position().x(), change.position().y(), change.position().z());
            job.statistics.accept(change.kind(), job.name(change.before()), job.name(change.after()));
            int faces = surfaceFaces(change, job.memory);
            if (faces == 0) continue;
            job.cells.add(new CommandPreviewSnapshot.Cell(new BlockPos(change.position().x(), change.position().y(), change.position().z()),
                    NeoForgeAdapter.adapt(change.after().toImmutableState()), change.kind(), faces));
        }
        if (job.output.hasNext()) return;
        // Validate the captured source before publishing, not only after displaying it.
        if (!job.validated) { job.needsValidation = true; return; }
        PreviewStatistics statistics = job.statistics.finish();
        job.published = true;
        publish(client, level, token, new CommandPreviewSnapshot(++revision, CommandPreviewSnapshot.State.READY,
                "", "", job.scanned, job.total, job.program.random(), job.limited, List.copyOf(job.cells), statistics, job.terrain.build()));
    }

    private void verify(ServerPlayer nativePlayer, Player actor, LocalSession session, Job job, long deadline) throws IncompleteRegionException {
        Region current = session.getSelection(actor.getWorld());
        if (current.getClass() != job.region.getClass() || !current.getMinimumPoint().equals(job.region.getMinimumPoint())
                || !current.getMaximumPoint().equals(job.region.getMaximumPoint())) { clear(); return; }
        if (current instanceof com.sk89q.worldedit.regions.ConvexPolyhedralRegion convex
                && job.region instanceof com.sk89q.worldedit.regions.ConvexPolyhedralRegion previous
                && !List.copyOf(convex.getVertices()).equals(List.copyOf(previous.getVertices()))) { clear(); return; }
        if (job.verifying == null) job.verifying = job.memory.original().entrySet().iterator();
        int count = 0;
        while (job.verifying.hasNext() && count++ < WORK_STEPS && System.nanoTime() < deadline) {
            Map.Entry<BlockVector3, BaseBlock> entry = job.verifying.next();
            BlockVector3 p = entry.getKey();
            if (!nativePlayer.level().hasChunk(p.x() >> 4, p.z() >> 4)
                    || current.contains(p) != job.region.contains(p)
                    || !entry.getValue().equals(actor.getWorld().getFullBlock(p))) {
                clear();
                return;
            }
        }
        if (!job.verifying.hasNext()) { job.verifying = null; job.validated = true; job.needsValidation = false; }
    }

    private static int surfaceFaces(PreviewChange c, PreviewExtent memory) {
        int mask = 0;
        for (Direction direction : DIRECTIONS) {
            BaseBlock neighbor = memory.changedBlock(c.position().add(direction.getStepX(), direction.getStepY(), direction.getStepZ()));
            boolean interior = neighbor != null && (c.kind() == PreviewChangeKind.REMOVED
                    ? neighbor.getBlockType().getMaterial().isAir()
                    : c.after().getBlockType().getMaterial().isOpaque() && neighbor.getBlockType().getMaterial().isOpaque());
            if (!interior) mask |= 1 << direction.ordinal();
        }
        return mask;
    }

    private void fail(Minecraft client, Object level, long token, CommandPreviewSnapshot.State state, String reason, String detail) {
        job = null;
        failed = true;
        publish(client, level, token, CommandPreviewSnapshot.status(state, reason, detail));
    }

    private void publish(Minecraft client, Object level, long token, CommandPreviewSnapshot result) {
        client.execute(() -> { if (epoch.get() == token && client.level == level) snapshot = result; });
    }

    private static String shortDetail(String message) {
        if (message == null) return "";
        StringBuilder clean = new StringBuilder();
        for (int i = 0; i < Math.min(message.length(), 120); i++) {
            char c = message.charAt(i);
            clean.append(Character.isISOControl(c) ? ' ' : c);
        }
        return clean.toString();
    }

    private static final class Job {
        final Region region;
        final PreviewExtent memory;
        final Iterator<BlockVector3> capture;
        final long total;
        final WorldEditPreviewProgram program;
        final Mask globalMask;
        final int changeLimit;
        final String permission;
        final ArrayList<CommandPreviewSnapshot.Cell> cells = new ArrayList<>();
        final CommandPreviewMask.Builder terrain = new CommandPreviewMask.Builder();
        final PreviewStatistics.Accumulator statistics = new PreviewStatistics.Accumulator();
        final Map<com.sk89q.worldedit.world.block.BlockState, String> names = new java.util.HashMap<>();
        String name(BaseBlock block) { return names.computeIfAbsent(block.toImmutableState(), s -> s.getAsString()); }
        Iterator<PreviewChange> output;
        Iterator<Map.Entry<BlockVector3, BaseBlock>> verifying;
        long scanned;
        long progressScanned = -1;
        boolean captured, published, limited, validated, needsValidation;
        Job(Region region, PreviewExtent memory, Iterator<BlockVector3> capture, long total,
            WorldEditPreviewProgram program, Mask globalMask, int changeLimit, String permission) {
            this.region = region; this.memory = memory; this.capture = capture; this.total = total;
            this.program = program; this.globalMask = globalMask; this.changeLimit = changeLimit;
            this.permission = permission;
        }
    }
}
