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
    private FixtureMeasurements measurements;

    @Override public void onEnable() {
        if (!Boolean.getBoolean("weso.bridge.fixture") || getServer().getOnlineMode()
                || !getServer().getIp().equals("127.0.0.1")) {
            getLogger().severe("Fixture requires explicit JVM opt-in, loopback binding and offline test identities.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        Objects.requireNonNull(getCommand("wesofixture")).setExecutor(this);
        getServer().getPluginManager().registerEvents(this, this);
        measurements = new FixtureMeasurements(this);
        getServer().getPluginManager().registerEvents(measurements, this);
    }

    @EventHandler public void join(PlayerJoinEvent event) {
        var player = event.getPlayer();
        if (!player.getName().startsWith("Weso")) return;
        var attachment = player.addAttachment(this);
        grants.put(player.getUniqueId(), attachment);
        boolean allowed = player.getName().equals("WesoAllowed") || player.getName().equals("WesoSecond")
                || player.getName().matches("WesoLoad[0-9]{2}");
        attachment.setPermission("weso.bridge.selection", allowed);
        for (String node : new String[]{"pos", "expand", "contract", "shift"})
            attachment.setPermission("worldedit.selection." + node, allowed);
        seed(player.getName());
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
        var player = getServer().getPlayerExact(args[1]);
        if (player == null || !grants.containsKey(player.getUniqueId())) return true;
        switch (args[0]) {
            case "revoke" -> grants.get(player.getUniqueId()).setPermission("weso.bridge.selection", false);
            case "grant" -> grants.get(player.getUniqueId()).setPermission("weso.bridge.selection", true);
            case "empty" -> {
                var actor = BukkitAdapter.adapt(player);
                WorldEdit.getInstance().getSessionManager().get(actor)
                        .setRegionSelector(actor.getWorld(), new CuboidRegionSelector(actor.getWorld()));
            }
            case "seed" -> seed(player.getName());
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
            case "channels" -> verifyBridgeResources(false);
            case "bridgeactive" -> verifyBridgeResources(true);
            default -> { return true; }
        }
        getLogger().info("WESO_FIXTURE_OK " + args[0] + " " + args[1]);
        return true;
    }

    private org.bukkit.plugin.Plugin bridgePlugin() {
        return Objects.requireNonNull(getServer().getPluginManager().getPlugin("WorldEditSelectionOverlayBridge"));
    }

    private void verifyBridgeResources(boolean active) {
        var plugin = bridgePlugin();
        String channel = "worldedit_selection_overlay:bridge";
        var messenger = getServer().getMessenger();
        var expectedChannels = active ? java.util.Set.of(channel) : java.util.Set.<String>of();
        int listeners = org.bukkit.event.HandlerList.getRegisteredListeners(plugin).size();
        if (plugin.isEnabled() != active
                || !messenger.getIncomingChannels(plugin).equals(expectedChannels)
                || !messenger.getOutgoingChannels(plugin).equals(expectedChannels)
                || messenger.getIncomingChannelRegistrations(plugin).size() != (active ? 1 : 0)
                || listeners != (active ? 5 : 0)
                || getServer().getScheduler().getPendingTasks().stream().anyMatch(task -> task.getOwner() == plugin)
                || getServer().getScheduler().getActiveWorkers().stream().anyMatch(worker -> worker.getOwner() == plugin))
            throw new IllegalStateException("Bridge resource ownership mismatch: active=" + active + ", listeners=" + listeners);
        // Inspect our own field only in this opt-in test fixture, never in production.
        try {
            var field = plugin.getClass().getDeclaredField("bridge");
            field.setAccessible(true);
            if ((field.get(plugin) != null) != active)
                throw new IllegalStateException("Bridge retained/missed core ownership");
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Fixture no longer matches companion field", ex);
        }
    }
}
