package org.marshal.weaddon_lightposition.bridge.paper;

import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;

import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerUnregisterChannelEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.scheduler.BukkitTask;

import org.marshal.weaddon_lightposition.bridge.protocol.BridgeCodec;
import org.marshal.weaddon_lightposition.bridge.protocol.BridgeRestartHint;
import org.marshal.weaddon_lightposition.bridge.server.BridgeServer;
import org.marshal.weaddon_lightposition.selection.CuboidDraft;
import org.marshal.weaddon_lightposition.selection.SelectionEditSnapshot;
import org.marshal.weaddon_lightposition.selection.worldedit.SelectionEditTransaction;

import static org.marshal.weaddon_lightposition.bridge.protocol.BridgeMessage.*;

public final class WorldEditOverlayBridgePlugin extends JavaPlugin implements Listener, PluginMessageListener {
    private BridgeServer bridge;
    private BukkitTask drainTask;
    private long lastFailureLog;
    private boolean warnedThread;
    private long restartHints;
    private boolean selectionEditingAvailable = true;

    @Override public void onEnable() {
        var worldEdit = getServer().getPluginManager().getPlugin("WorldEdit");
        String worldEditVersion = worldEdit == null ? "" : worldEdit.getPluginMeta().getVersion();
        if (worldEdit == null || !PaperHostCompatibility.supports(Bukkit.getMinecraftVersion(),
                worldEdit.getName(), worldEditVersion, worldEdit.isEnabled())) {
            getLogger().severe("This experimental host requires an exact Paper/official WorldEdit tuple: 1.21.11/7.4.2, 26.1.2/7.4.3 or 26.2/7.4.5.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        saveDefaultConfig();
        BridgeServer.Limits limits;
        try {
            limits = new BridgeServer.Limits(getConfig().getInt("limits.max-connections", 1024),
                    getConfig().getInt("limits.global-requests-per-second", 100),
                    getConfig().getInt("limits.global-burst", 100),
                    getConfig().getInt("limits.player-requests-per-second", 4),
                    getConfig().getInt("limits.player-burst", 8));
        } catch (IllegalArgumentException ex) {
            getLogger().severe("Invalid bridge rate limits. Companion disabled; normal WorldEdit is unaffected.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        bridge = new BridgeServer(new SelectionHost(), limits, System::nanoTime, UUID::randomUUID);
        getServer().getMessenger().registerOutgoingPluginChannel(this, BridgeCodec.CHANNEL);
        getServer().getMessenger().registerOutgoingPluginChannel(this, BridgeRestartHint.CHANNEL);
        getServer().getMessenger().registerIncomingPluginChannel(this, BridgeCodec.CHANNEL, this);
        getServer().getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("wesobridge")).setExecutor(this);
        getLogger().info("Experimental read-only bridge enabled; optional cuboid editing requires a separate grant. Bounded fair dispatch; no idle tasks or block scans.");
        if (!selectionEditingAvailable) getLogger().warning("Cuboid editing requires a full server restart after companion disable; metadata/CUI fallback remains available.");
        announceRestart();
    }

    private void announceRestart() {
        String channel = BridgeRestartHint.CHANNEL;
        byte[] frame = BridgeRestartHint.encode(UUID.randomUUID());
        restartHints = 0;
        // Only lifecycle work: no retained players, permission probes, sessions or retry tasks.
        for (Player player : getServer().getOnlinePlayers()) {
            if (!player.getListeningPluginChannels().contains(channel)) continue;
            try {
                player.sendPluginMessage(this, channel, frame);
                restartHints++;
            } catch (IllegalArgumentException | IllegalStateException ex) {
                logFailure(ex);
            }
        }
    }

    @Override public void onDisable() {
        selectionEditingAvailable = false;
        stopDrain();
        getServer().getMessenger().unregisterIncomingPluginChannel(this);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);
        if (bridge != null) { bridge.close(); bridge = null; }
    }

    @Override public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (bridge == null || !BridgeCodec.CHANNEL.equals(channel)) return;
        if (!Bukkit.isPrimaryThread()) {
            if (!warnedThread) {
                warnedThread = true;
                getLogger().severe("Bridge callback is not on the owning thread; refusing session access.");
            }
            return;
        }
        if (!player.isOnline() || !player.getListeningPluginChannels().contains(BridgeCodec.CHANNEL)) return;
        if (bridge.enqueue(player.getUniqueId(), message) && drainTask == null)
            drainTask = getServer().getScheduler().runTaskTimer(this, this::drainRequests, 1L, 1L);
    }

    private void drainRequests() {
        try {
            if (bridge != null) bridge.drain(this::sendReply);
        } finally {
            stopDrainIfIdle();
        }
    }

    private void stopDrainIfIdle() {
        if (bridge == null || bridge.pendingCount() == 0) stopDrain();
    }

    private void stopDrain() {
        if (drainTask != null) { drainTask.cancel(); drainTask = null; }
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) {
        if (bridge != null) bridge.disconnect(event.getPlayer().getUniqueId());
        stopDrainIfIdle();
    }

    @EventHandler public void onWorldChange(PlayerChangedWorldEvent event) {
        if (bridge != null) bridge.resetContext(event.getPlayer().getUniqueId());
        stopDrainIfIdle();
    }

    @EventHandler public void onRespawn(PlayerRespawnEvent event) {
        if (bridge != null) bridge.resetContext(event.getPlayer().getUniqueId());
        stopDrainIfIdle();
    }

    @EventHandler public void onChannelUnregister(PlayerUnregisterChannelEvent event) {
        if (bridge != null && BridgeCodec.CHANNEL.equals(event.getChannel()))
            bridge.resetContext(event.getPlayer().getUniqueId());
        stopDrainIfIdle();
    }

    @EventHandler public void onDependencyDisable(PluginDisableEvent event) {
        if (event.getPlugin() == this && bridge != null) {
            stopDrain();
            bridge.shutdown(this::sendReply);
        }
        if (event.getPlugin().getName().equals("WorldEdit"))
            getServer().getPluginManager().disablePlugin(this);
    }

    private void sendReply(UUID id, byte[] frame) {
        Player player = getServer().getPlayer(id);
        if (player == null || !player.isOnline() || !player.getListeningPluginChannels().contains(BridgeCodec.CHANNEL)) return;
        try {
            player.sendPluginMessage(this, BridgeCodec.CHANNEL, frame);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            logFailure(ex);
        }
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("weso.bridge.admin")) return true;
        if (bridge == null) { sender.sendMessage("WESO Bridge: disabled."); return true; }
        BridgeServer.Metrics m = bridge.metrics();
        sender.sendMessage("WESO Bridge 0.1 (optional selection transport): connections=" + m.connections()
                + ", admitted=" + m.admitted() + ", throttled=" + m.throttled() + ", malformed=" + m.malformed());
        sender.sendMessage("Selection metadata reads=" + m.selectionReads() + ", replies=" + m.replies()
                + ", reply bytes=" + m.encodedReplyBytes() + ". No block scans/subscriptions; cuboid metadata edits require a separate grant.");
        sender.sendMessage("Restart hints=" + restartHints + ", hint bytes=" + restartHints * BridgeRestartHint.FRAME_BYTES + ". Lifecycle only.");
        var edits = bridge.editMetrics();
        sender.sendMessage("Cuboid captures=" + edits.captures() + ", commits=" + edits.commits() + ", applied=" + edits.applied());
        BridgeServer.QueueMetrics q = bridge.queueMetrics();
        sender.sendMessage("Queue pending=" + q.pending() + ", peak=" + q.peakPending() + ", queued=" + q.queued()
                + ", expired=" + q.expired() + ", cancelled=" + q.cancelled() + ", max wait nanos=" + q.maxWaitNanos()
                + ", max slice nanos=" + q.maxSliceNanos() + ", dispatch tasks=" + (drainTask == null ? 0 : 1));
        return true;
    }

    private final class SelectionHost implements BridgeServer.Host {
        @Override public boolean mayEditSelection(UUID id) {
            Player player = getServer().getPlayer(id);
            return selectionEditingAvailable && player != null && player.hasPermission("weso.bridge.selection.edit") && mayReadSelection(id);
        }

        private boolean editableContext(com.sk89q.worldedit.entity.Player actor) {
            LocalSession session = WorldEdit.getInstance().getSessionManager().getIfPresent(actor);
            return session != null && !session.hasWorldOverride() && actor.getWorld().equals(session.getSelectionWorld());
        }

        private BridgeServer.EditCapture captured(SelectionEditSnapshot snapshot) {
            if (!snapshot.ready()) return BridgeServer.EditCapture.unavailable(switch (snapshot.reason()) {
                case "permission" -> SelectionStatus.DENIED;
                case "shape", "session" -> SelectionStatus.UNSUPPORTED;
                case "selection", "stale" -> SelectionStatus.EMPTY;
                default -> SelectionStatus.FAILED;
            });
            var b = snapshot.bounds();
            int grants = (snapshot.expand() ? 1 : 0) | (snapshot.contract() ? 2 : 0) | (snapshot.shift() ? 4 : 0);
            return new BridgeServer.EditCapture(snapshot, new SelectionState(SelectionStatus.READY,
                    new Bounds(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()), snapshot.minY(), snapshot.maxY(), grants));
        }

        @Override public BridgeServer.EditCapture captureEdit(UUID id) {
            if (!mayEditSelection(id)) return BridgeServer.EditCapture.unavailable(SelectionStatus.DENIED);
            try {
                var actor = BukkitAdapter.adapt(Objects.requireNonNull(getServer().getPlayer(id)));
                if (!editableContext(actor)) return BridgeServer.EditCapture.unavailable(SelectionStatus.UNSUPPORTED);
                return captured(SelectionEditTransaction.read(actor));
            } catch (RuntimeException | LinkageError ex) {
                logFailure(ex); return BridgeServer.EditCapture.unavailable(SelectionStatus.FAILED);
            }
        }

        @Override public BridgeServer.EditOutcome applyEdit(UUID id, Object token, Bounds target) {
            if (!mayEditSelection(id)) return new BridgeServer.EditOutcome(CommitStatus.DENIED, BridgeServer.EditCapture.unavailable(SelectionStatus.DENIED));
            try {
                var actor = BukkitAdapter.adapt(Objects.requireNonNull(getServer().getPlayer(id)));
                if (!(token instanceof SelectionEditSnapshot original) || !editableContext(actor))
                    return new BridgeServer.EditOutcome(CommitStatus.CONFLICT, BridgeServer.EditCapture.unavailable(SelectionStatus.UNSUPPORTED));
                var draft = new CuboidDraft(target.minX(), target.minY(), target.minZ(), target.maxX(), target.maxY(), target.maxZ());
                var result = SelectionEditTransaction.commitBounds(actor, original, draft);
                CommitStatus status = result.ready() ? CommitStatus.APPLIED : switch (result.reason()) {
                    case "stale", "selection" -> CommitStatus.CONFLICT;
                    case "permission" -> CommitStatus.DENIED;
                    case "shape", "session" -> CommitStatus.UNSUPPORTED;
                    case "invalid" -> CommitStatus.INVALID;
                    default -> CommitStatus.UNKNOWN;
                };
                return new BridgeServer.EditOutcome(status, captured(result));
            } catch (RuntimeException | LinkageError ex) {
                // Failure may follow mutation/CUI delivery; do not claim rollback or retry.
                logFailure(ex);
                return new BridgeServer.EditOutcome(CommitStatus.UNKNOWN, BridgeServer.EditCapture.unavailable(SelectionStatus.FAILED));
            }
        }

        @Override public boolean mayReadSelection(UUID id) {
            Player player = getServer().getPlayer(id);
            if (player == null || !player.isOnline() || !player.hasPermission("weso.bridge.selection")) return false;
            try {
                var actor = BukkitAdapter.adapt(player);
                return actor.hasPermission("worldedit.selection.pos") || actor.hasPermission("worldedit.selection.expand")
                        || actor.hasPermission("worldedit.selection.contract") || actor.hasPermission("worldedit.selection.shift");
            } catch (RuntimeException | LinkageError ex) {
                logFailure(ex);
                return false;
            }
        }

        @Override public SelectionState readSelection(UUID id) {
            if (!mayReadSelection(id)) return SelectionState.unavailable(SelectionStatus.DENIED);
            Player player = getServer().getPlayer(id);
            if (player == null) return SelectionState.unavailable(SelectionStatus.EMPTY);
            try {
                var actor = BukkitAdapter.adapt(player);
                LocalSession session = WorldEdit.getInstance().getSessionManager().getIfPresent(actor);
                if (session == null || session.getSelectionWorld() == null)
                    return SelectionState.unavailable(SelectionStatus.EMPTY);
                if (session.hasWorldOverride() || !actor.getWorld().equals(session.getSelectionWorld()))
                    return SelectionState.unavailable(SelectionStatus.UNSUPPORTED);
                var region = session.getSelection(actor.getWorld());
                var a = region.getMinimumPoint();
                var b = region.getMaximumPoint();
                int permissions = (actor.hasPermission("worldedit.selection.expand") ? 1 : 0)
                        | (actor.hasPermission("worldedit.selection.contract") ? 2 : 0)
                        | (actor.hasPermission("worldedit.selection.shift") ? 4 : 0);
                return new SelectionState(SelectionStatus.READY, new Bounds(a.x(), a.y(), a.z(), b.x(), b.y(), b.z()),
                        actor.getWorld().getMinY(), actor.getWorld().getMaxY(), permissions);
            } catch (IncompleteRegionException ex) {
                return SelectionState.unavailable(SelectionStatus.EMPTY);
            } catch (RuntimeException | LinkageError ex) {
                logFailure(ex);
                return SelectionState.unavailable(SelectionStatus.FAILED);
            }
        }
    }

    private void logFailure(Throwable ex) {
        long now = System.nanoTime();
        if (lastFailureLog == 0 || now - lastFailureLog >= 30_000_000_000L) {
            lastFailureLog = now;
            getLogger().log(Level.WARNING, "WorldEdit bridge host operation failed.", ex);
        }
    }
}
