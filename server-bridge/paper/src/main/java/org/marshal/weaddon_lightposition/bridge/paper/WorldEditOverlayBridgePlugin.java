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

import org.marshal.weaddon_lightposition.bridge.protocol.BridgeCodec;
import org.marshal.weaddon_lightposition.bridge.server.BridgeServer;

import static org.marshal.weaddon_lightposition.bridge.protocol.BridgeMessage.*;

public final class WorldEditOverlayBridgePlugin extends JavaPlugin implements Listener, PluginMessageListener {
    private BridgeServer bridge;
    private long lastFailureLog;
    private boolean warnedThread;

    @Override public void onEnable() {
        var worldEdit = getServer().getPluginManager().getPlugin("WorldEdit");
        String worldEditVersion = worldEdit == null ? "" : worldEdit.getPluginMeta().getVersion();
        if (!Bukkit.getMinecraftVersion().equals("1.21.11") || worldEdit == null || !worldEdit.isEnabled()
                || !worldEdit.getName().equals("WorldEdit")
                || !(worldEditVersion.equals("7.4.2") || worldEditVersion.startsWith("7.4.2+"))) {
            getLogger().severe("This experimental host targets Paper 1.21.11 with official WorldEdit 7.4.2 only.");
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
        getServer().getMessenger().registerIncomingPluginChannel(this, BridgeCodec.CHANNEL, this);
        getServer().getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("wesobridge")).setExecutor(this);
        getLogger().info("Experimental read-only bridge enabled. No block scanning or background tasks.");
    }

    @Override public void onDisable() {
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
        byte[] reply = bridge.receive(player.getUniqueId(), message);
        if (reply != null) player.sendPluginMessage(this, BridgeCodec.CHANNEL, reply);
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) {
        if (bridge != null) bridge.disconnect(event.getPlayer().getUniqueId());
    }

    @EventHandler public void onWorldChange(PlayerChangedWorldEvent event) {
        if (bridge != null) bridge.resetContext(event.getPlayer().getUniqueId());
    }

    @EventHandler public void onRespawn(PlayerRespawnEvent event) {
        if (bridge != null) bridge.resetContext(event.getPlayer().getUniqueId());
    }

    @EventHandler public void onChannelUnregister(PlayerUnregisterChannelEvent event) {
        if (bridge != null && BridgeCodec.CHANNEL.equals(event.getChannel()))
            bridge.resetContext(event.getPlayer().getUniqueId());
    }

    @EventHandler public void onDependencyDisable(PluginDisableEvent event) {
        if (event.getPlugin().getName().equals("WorldEdit"))
            getServer().getPluginManager().disablePlugin(this);
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("weso.bridge.admin")) return true;
        if (bridge == null) { sender.sendMessage("WESO Bridge: disabled."); return true; }
        BridgeServer.Metrics m = bridge.metrics();
        sender.sendMessage("WESO Bridge 0.1 (read-only transport): connections=" + m.connections()
                + ", admitted=" + m.admitted() + ", throttled=" + m.throttled() + ", malformed=" + m.malformed());
        sender.sendMessage("Selection metadata reads=" + m.selectionReads() + ", replies=" + m.replies()
                + ", reply bytes=" + m.encodedReplyBytes() + ". No block scans, subscriptions or mutations.");
        return true;
    }

    private final class SelectionHost implements BridgeServer.Host {
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
            getLogger().log(Level.WARNING, "WorldEdit bridge selection API failed.", ex);
        }
    }
}
