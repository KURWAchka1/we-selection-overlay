package org.marshal.weaddon_lightposition.bridge.fixture;

import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.selector.CuboidRegionSelector;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.java.JavaPlugin;

/** Never distributed; seeds metadata and explicit grants in a loopback-only test server. */
public final class PaperBridgeFixture extends JavaPlugin implements Listener {
    private final Map<UUID, PermissionAttachment> grants = new HashMap<>();
    private final Map<UUID, String> editAudits = new HashMap<>();
    private FixtureMeasurements measurements;
    private FixtureGameplay gameplay;
    private FixtureQueuedFaults queuedFaults;

    @Override public void onEnable() {
        if (!Boolean.getBoolean("weso.bridge.fixture") || getServer().getOnlineMode()
                || !getServer().getIp().equals("127.0.0.1")) {
            getLogger().severe("Fixture requires explicit JVM opt-in, loopback binding and offline test identities.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        Objects.requireNonNull(getCommand("wesofixture")).setExecutor(this);
        getServer().getPluginManager().registerEvents(this, this);
        gameplay = new FixtureGameplay(this);
        getServer().getPluginManager().registerEvents(gameplay, this);
        queuedFaults = new FixtureQueuedFaults(this, gameplay, name -> grants.get(
                Objects.requireNonNull(getServer().getPlayerExact(name)).getUniqueId())
                .setPermission("weso.bridge.selection", false));
        getServer().getPluginManager().registerEvents(queuedFaults, this);
        measurements = new FixtureMeasurements(this, gameplay);
        getServer().getPluginManager().registerEvents(measurements, this);
        if (Boolean.getBoolean("weso.bridge.faultFixture")) PacketPrefixes.report();
    }

    @Override public void onDisable() {
        if (gameplay != null) gameplay.stop();
    }

    @EventHandler public void join(PlayerJoinEvent event) {
        var player = event.getPlayer();
        if (!player.getName().startsWith("Weso")) return;
        var attachment = player.addAttachment(this);
        grants.put(player.getUniqueId(), attachment);
        boolean allowed = player.getName().equals("WesoAllowed") || player.getName().equals("WesoSecond")
                || player.getName().matches("WesoLoad[0-9]{2}");
        attachment.setPermission("weso.bridge.selection", allowed);
        attachment.setPermission("weso.bridge.selection.edit", allowed && Boolean.getBoolean("weso.bridge.editFixture"));
        for (String node : new String[]{"pos", "expand", "contract", "shift"})
            attachment.setPermission("worldedit.selection." + node, allowed);
        seed(player.getName());
        if (gameplay.active() && player.getName().matches("WesoLoad[0-9]{2}")) gameplay.preparePlayer(player);
        getLogger().info("WESO_FIXTURE_JOIN " + player.getName());
    }

    private void seed(String name) {
        var player = Objects.requireNonNull(getServer().getPlayerExact(name));
        var actor = BukkitAdapter.adapt(player);
        int x = name.equals("WesoSecond") ? -30 : 10;
        if (name.matches("WesoLoad[0-9]{2}")) x = 100 + Integer.parseInt(name.substring(8)) * 16;
        var session = WorldEdit.getInstance().getSessionManager().get(actor);
        session.setRegionSelector(actor.getWorld(), new CuboidRegionSelector(actor.getWorld(),
                BlockVector3.at(x, 64, 20), BlockVector3.at(x + 5, 70, 27)));
    }

    @EventHandler public void quit(PlayerQuitEvent event) {
        grants.remove(event.getPlayer().getUniqueId());
        editAudits.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler public void respawn(PlayerRespawnEvent event) {
        if (grants.containsKey(event.getPlayer().getUniqueId()))
            getLogger().info("WESO_FIXTURE_RESPAWN " + event.getPlayer().getName());
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof ConsoleCommandSender) || args.length != 2) return true;
        if (args[0].equals("measure")) {
            measurements.start(args[1]);
            return true;
        }
        if (args[0].equals("control") && args[1].equals("gameplay")) {
            gameplay.start();
            getLogger().info("WESO_FIXTURE_OK control gameplay");
            return true;
        }
        if (args[0].equals("control") && args[1].equals("baseline")) {
            for (var world : getServer().getWorlds()) {
                if (!world.setGameRule(org.bukkit.GameRules.SPAWN_MOBS, false)
                        || !world.setGameRule(org.bukkit.GameRules.ADVANCE_TIME, false)
                        || !world.setGameRule(org.bukkit.GameRules.ADVANCE_WEATHER, false)
                        || !world.setGameRule(org.bukkit.GameRules.RANDOM_TICK_SPEED, 0))
                    throw new IllegalStateException("Baseline gamerule rejected");
                world.setDifficulty(org.bukkit.Difficulty.PEACEFUL);
                world.setTime(6000); world.setStorm(false); world.setThundering(false);
                for (var entity : world.getEntities()) if (!(entity instanceof org.bukkit.entity.Player)) entity.remove();
            }
            for (var testPlayer : getServer().getOnlinePlayers()) {
                if (!testPlayer.getName().matches("WesoLoad[0-9]{2}"))
                    throw new IllegalStateException("Baseline requires load-fixture identities only");
                testPlayer.setAllowFlight(true); testPlayer.setFlying(true); testPlayer.setGravity(false);
                if (!testPlayer.teleport(new org.bukkit.Location(testPlayer.getWorld(), 0.5, 80, 0.5)))
                    throw new IllegalStateException("Baseline teleport cancelled");
            }
            getLogger().info("WESO_FIXTURE_OK control baseline");
            return true;
        }
        if (args[0].equals("channels") || args[0].equals("bridgeactive")) {
            verifyBridgeResources(args[0].equals("bridgeactive"));
            getLogger().info("WESO_FIXTURE_OK " + args[0] + " " + args[1]);
            return true;
        }
        var player = getServer().getPlayerExact(args[1]);
        if (player == null || !grants.containsKey(player.getUniqueId())) return true;
        if (args[0].startsWith("queued-")) {
            queuedFaults.arm(args[0].substring(7), args[1]);
            getLogger().info("WESO_FIXTURE_OK " + args[0] + " " + args[1]);
            return true;
        }
        switch (args[0]) {
            case "edit-pose" -> {
                player.setGameMode(org.bukkit.GameMode.CREATIVE);
                player.setAllowFlight(true); player.setFlying(true); player.setGravity(false);
                if (!player.teleport(new org.bukkit.Location(player.getWorld(), 25, 78, 40)))
                    throw new IllegalStateException("Gesture fixture teleport cancelled");
            }
            case "edit-bounds" -> {
                var actor = BukkitAdapter.adapt(player);
                try {
                    var region = WorldEdit.getInstance().getSessionManager().get(actor).getSelection(actor.getWorld());
                    var low = region.getMinimumPoint(); var high = region.getMaximumPoint();
                    getLogger().info("WESO_FIXTURE_BOUNDS " + player.getName() + " " + low.x() + "," + low.y() + "," + low.z()
                            + "," + high.x() + "," + high.y() + "," + high.z());
                } catch (com.sk89q.worldedit.IncompleteRegionException ex) { throw new IllegalStateException("Missing gesture selection", ex); }
            }
            case "editgrant", "editrevoke" -> grants.get(player.getUniqueId()).setPermission("weso.bridge.selection.edit", args[0].equals("editgrant"));
            case "expandonly", "contractonly", "shiftonly", "allgrants" -> {
                for (String node : new String[]{"expand", "contract", "shift"})
                    grants.get(player.getUniqueId()).setPermission("worldedit.selection." + node, args[0].equals("allgrants") || args[0].equals(node + "only"));
            }
            case "reverse" -> {
                var actor = BukkitAdapter.adapt(player);
                WorldEdit.getInstance().getSessionManager().get(actor).setRegionSelector(actor.getWorld(),
                        new CuboidRegionSelector(actor.getWorld(), BlockVector3.at(15, 70, 27), BlockVector3.at(10, 64, 20)));
            }
            case "extend" -> {
                var actor = BukkitAdapter.adapt(player); var session = WorldEdit.getInstance().getSessionManager().get(actor);
                session.setRegionSelector(actor.getWorld(), new com.sk89q.worldedit.regions.selector.ExtendingCuboidRegionSelector(session.getRegionSelector(actor.getWorld())));
            }
            case "unsupported" -> {
                var actor = BukkitAdapter.adapt(player);
                WorldEdit.getInstance().getSessionManager().get(actor).setRegionSelector(actor.getWorld(),
                        new CuboidRegionSelector(actor.getWorld(), BlockVector3.at(10, 64, 20), BlockVector3.at(15, 70, 27)) { });
            }
            case "edit-command" -> {
                if (!getServer().dispatchCommand(player, "/shift 1 east")) throw new IllegalStateException("WorldEdit shift command unavailable");
            }
            case "edit-audit-start" -> editAudits.put(player.getUniqueId(), editAudit(player));
            case "edit-audit-end" -> {
                if (!Objects.equals(editAudits.remove(player.getUniqueId()), editAudit(player))) throw new IllegalStateException("Selection editing changed fixture blocks");
                var actor = BukkitAdapter.adapt(player); var session = WorldEdit.getInstance().getSessionManager().get(actor);
                if (session.undo(null, actor) != null) throw new IllegalStateException("Selection editing added undo history");
            }
            case "revoke" -> grants.get(player.getUniqueId()).setPermission("weso.bridge.selection", false);
            case "grant" -> grants.get(player.getUniqueId()).setPermission("weso.bridge.selection", true);
            case "empty" -> {
                var actor = BukkitAdapter.adapt(player);
                WorldEdit.getInstance().getSessionManager().get(actor)
                        .setRegionSelector(actor.getWorld(), new CuboidRegionSelector(actor.getWorld()));
            }
            case "seed" -> seed(player.getName());
            case "seed-sync" -> {
                seed(player.getName()); var actor = BukkitAdapter.adapt(player);
                var session = WorldEdit.getInstance().getSessionManager().get(actor);
                session.getRegionSelector(actor.getWorld()).explainRegionAdjust(actor, session);
            }
            case "kill" -> player.setHealth(0);
            case "channeloff", "channelon" -> ChannelFaults.advertise(player, args[0].equals("channelon"));
            case "nether", "overworld" -> {
                var environment = args[0].equals("nether") ? org.bukkit.World.Environment.NETHER : org.bukkit.World.Environment.NORMAL;
                var destination = getServer().getWorlds().stream().filter(world -> world.getEnvironment() == environment)
                        .findFirst().orElseThrow();
                if (!player.teleport(new org.bukkit.Location(destination, 0.5, 100, 0.5)))
                    throw new IllegalStateException("Fixture dimension transition cancelled");
                seed(player.getName());
            }
            case "stopwe" -> getServer().getPluginManager().disablePlugin(
                    Objects.requireNonNull(getServer().getPluginManager().getPlugin("WorldEdit")));
            case "bridgeoff" -> getServer().getPluginManager().disablePlugin(bridgePlugin());
            case "bridgeon" -> getServer().getPluginManager().enablePlugin(bridgePlugin());
            default -> { return true; }
        }
        getLogger().info("WESO_FIXTURE_OK " + args[0] + " " + args[1]);
        return true;
    }

    private org.bukkit.plugin.Plugin bridgePlugin() {
        return Objects.requireNonNull(getServer().getPluginManager().getPlugin("WorldEditSelectionOverlayBridge"));
    }

    private String editAudit(org.bukkit.entity.Player player) {
        // Fixed loaded test area only. Never force loads to make an audit pass.
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            for (int x = 8; x <= 24; x++) for (int z = 18; z <= 30; z++) {
                if (!player.getWorld().isChunkLoaded(x >> 4, z >> 4)) throw new IllegalStateException("Audit chunk is not loaded");
                for (int y = 60; y <= 74; y++) digest.update(player.getWorld().getBlockAt(x, y, z).getBlockData().getAsString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException ex) { throw new AssertionError(ex); }
    }

    private void verifyBridgeResources(boolean active) {
        var plugin = bridgePlugin();
        String channel = "worldedit_selection_overlay:bridge";
        var messenger = getServer().getMessenger();
        var expectedChannels = active ? java.util.Set.of(channel) : java.util.Set.<String>of();
        var expectedOutgoing = active ? java.util.Set.of(channel, "worldedit_selection_overlay:restart") : java.util.Set.<String>of();
        int listeners = org.bukkit.event.HandlerList.getRegisteredListeners(plugin).size();
        if (plugin.isEnabled() != active
                || !messenger.getIncomingChannels(plugin).equals(expectedChannels)
                || !messenger.getOutgoingChannels(plugin).equals(expectedOutgoing)
                || messenger.getIncomingChannelRegistrations(plugin).size() != (active ? 1 : 0)
                || listeners != (active ? 5 : 0)
                || getServer().getScheduler().getActiveWorkers().stream().anyMatch(worker -> worker.getOwner() == plugin))
            throw new IllegalStateException("Bridge resource ownership mismatch: active=" + active + ", listeners=" + listeners);
        // Inspect our own field only in this opt-in test fixture, never in production.
        try {
            var field = plugin.getClass().getDeclaredField("bridge");
            field.setAccessible(true);
            Object core = field.get(plugin);
            if ((core != null) != active)
                throw new IllegalStateException("Bridge retained/missed core ownership");
            var taskField = plugin.getClass().getDeclaredField("drainTask");
            taskField.setAccessible(true);
            var dispatch = (org.bukkit.scheduler.BukkitTask) taskField.get(plugin);
            int pending = core == null ? 0 : (int) core.getClass().getMethod("pendingCount").invoke(core);
            var tasks = getServer().getScheduler().getPendingTasks().stream()
                    .filter(task -> task.getOwner() == plugin).toList();
            if ((pending > 0) != (dispatch != null)
                    || tasks.size() != (dispatch == null ? 0 : 1)
                    || dispatch != null && (!active || dispatch.isCancelled() || !dispatch.isSync()
                    || dispatch.getOwner() != plugin || tasks.getFirst().getTaskId() != dispatch.getTaskId()))
                throw new IllegalStateException("Bridge dispatch ownership mismatch: active=" + active
                        + ", pending=" + pending + ", tasks=" + tasks.size());
            getLogger().info("WESO_FIXTURE_RESOURCES active=" + active + ", pending=" + pending
                    + ", dispatchTasks=" + tasks.size());
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Fixture no longer matches companion field", ex);
        }
    }
}
