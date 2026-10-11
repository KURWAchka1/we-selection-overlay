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
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.java.JavaPlugin;

/** Never distributed; seeds metadata and explicit grants in a loopback-only test server. */
public final class PaperBridgeFixture extends JavaPlugin implements Listener {
    private final Map<UUID, PermissionAttachment> grants = new HashMap<>();

    @Override public void onEnable() {
        if (!Boolean.getBoolean("weso.bridge.fixture") || getServer().getOnlineMode()
                || !getServer().getIp().equals("127.0.0.1")) {
            getLogger().severe("Fixture requires explicit JVM opt-in, loopback binding and offline test identities.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        Objects.requireNonNull(getCommand("wesofixture")).setExecutor(this);
        getServer().getPluginManager().registerEvents(this, this);
    }

    @EventHandler public void join(PlayerJoinEvent event) {
        var player = event.getPlayer();
        if (!player.getName().startsWith("Weso")) return;
        var attachment = player.addAttachment(this);
        grants.put(player.getUniqueId(), attachment);
        boolean allowed = player.getName().equals("WesoAllowed") || player.getName().equals("WesoSecond");
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
        var session = WorldEdit.getInstance().getSessionManager().get(actor);
        session.setRegionSelector(actor.getWorld(), new CuboidRegionSelector(actor.getWorld(),
                BlockVector3.at(x, 64, 20), BlockVector3.at(x + 5, 70, 27)));
    }

    @EventHandler public void quit(PlayerQuitEvent event) {
        grants.remove(event.getPlayer().getUniqueId());
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof ConsoleCommandSender) || args.length != 2) return true;
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
            default -> { return true; }
        }
        getLogger().info("WESO_FIXTURE_OK " + args[0] + " " + args[1]);
        return true;
    }
}
