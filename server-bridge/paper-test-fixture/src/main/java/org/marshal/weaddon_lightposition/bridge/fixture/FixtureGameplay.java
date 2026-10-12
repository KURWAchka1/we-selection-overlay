package org.marshal.weaddon_lightposition.bridge.fixture;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.world.block.BlockTypes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Cow;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Bounded world mutations in the opt-in synthetic fixture, never in the bridge. */
final class FixtureGameplay implements Listener {
    private final JavaPlugin plugin;
    private final List<Cow> cows = new ArrayList<>();
    private final int[] moves = new int[32], entityStartTicks = new int[16];
    private World world;
    private CuboidRegion editRegion;
    private boolean active, measuring, dirt;
    private int ticks, batches, changedBlocks, invalidMoves;

    FixtureGameplay(JavaPlugin plugin) { this.plugin = plugin; }

    void start() {
        if (active) throw new IllegalStateException("Gameplay fixture already active");
        world = Objects.requireNonNull(plugin.getServer().getWorld("bridge-fixture"));
        if (plugin.getServer().getOnlinePlayers().isEmpty()) throw new IllegalStateException("No fixture players");
        for (var player : plugin.getServer().getOnlinePlayers()) {
            if (!player.getName().matches("WesoLoad[0-9]{2}") || player.isOp())
                throw new IllegalStateException("Gameplay requires non-OP fixture identities");
            int index = Integer.parseInt(player.getName().substring(8));
            player.setAllowFlight(true); player.setFlying(true); player.setGravity(false);
            if (!player.teleport(new Location(world, 16, 80 + index % 2, 16)))
                throw new IllegalStateException("Gameplay teleport cancelled");
        }
        var adapted = BukkitAdapter.adapt(world);
        editRegion = new CuboidRegion(adapted, BlockVector3.at(4, 66, 4), BlockVector3.at(11, 73, 11));
        try (var edit = WorldEdit.getInstance().newEditSession(adapted)) {
            edit.setBlocks(new CuboidRegion(adapted, BlockVector3.at(0, 63, 0), BlockVector3.at(31, 63, 31)),
                    Objects.requireNonNull(BlockTypes.STONE).getDefaultState());
            edit.setBlocks(editRegion, Objects.requireNonNull(BlockTypes.STONE).getDefaultState());
            for (int y = 64; y <= 65; y++) for (int n = 0; n < 32; n++) {
                var barrier = Objects.requireNonNull(BlockTypes.BARRIER).getDefaultState();
                edit.setBlock(BlockVector3.at(0, y, n), barrier); edit.setBlock(BlockVector3.at(31, y, n), barrier);
                edit.setBlock(BlockVector3.at(n, y, 0), barrier); edit.setBlock(BlockVector3.at(n, y, 31), barrier);
            }
        } catch (com.sk89q.worldedit.WorldEditException ex) {
            throw new IllegalStateException("Gameplay setup edit failed", ex);
        }
        for (int n = 0; n < 16; n++) {
            Cow cow = world.spawn(new Location(world, 18.5 + (n % 4) * 3, 64, 6.5 + (n / 4) * 6), Cow.class);
            cow.setAdult(); cow.setAgeLock(true); cow.setInvulnerable(true); cow.setPersistent(false);
            cow.setRemoveWhenFarAway(false); cow.setAI(true);
            cows.add(cow);
        }
        active = true;
        plugin.getLogger().info("WESO_GAMEPLAY_READY entities=16, editCells=512, editPeriod=20");
    }

    boolean active() { return active; }

    void beginWindow() {
        if (!active || measuring) throw new IllegalStateException("Invalid gameplay window start");
        measuring = true;
        ticks = batches = changedBlocks = invalidMoves = 0;
        Arrays.fill(moves, 0);
        for (int i = 0; i < cows.size(); i++) entityStartTicks[i] = cows.get(i).getTicksLived();
    }

    void tick() {
        if (!active) return;
        if (++ticks % 20 != 0) return;
        dirt = !dirt;
        try (var edit = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(world))) {
            int changed = edit.setBlocks(editRegion, Objects.requireNonNull(dirt ? BlockTypes.DIRT : BlockTypes.STONE).getDefaultState());
            if (changed != 512) throw new IllegalStateException("Gameplay edit volume changed: " + changed);
            if (measuring) { batches++; changedBlocks += changed; }
        } catch (com.sk89q.worldedit.WorldEditException ex) {
            throw new IllegalStateException("Gameplay workload edit failed", ex);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void move(PlayerMoveEvent event) {
        if (!measuring || event instanceof PlayerTeleportEvent || !event.hasChangedPosition()) return;
        String name = event.getPlayer().getName();
        if (!name.matches("WesoLoad[0-9]{2}")) { invalidMoves++; return; }
        int index = Integer.parseInt(name.substring(8));
        Location to = event.getTo();
        if (index >= moves.length || to.getWorld() != world || to.getX() < 9 || to.getX() > 23
                || to.getZ() < 9 || to.getZ() > 23 || Math.abs(to.getY() - (80 + index % 2)) > .01) {
            invalidMoves++; return;
        }
        moves[index]++;
    }

    JsonObject finishWindow(int players) {
        if (!measuring || players < 2 || players > 32) throw new IllegalStateException("Invalid gameplay window end");
        measuring = false;
        var data = new JsonObject();
        data.addProperty("version", 1); data.addProperty("ticks", ticks);
        data.addProperty("editBatches", batches); data.addProperty("changedBlocks", changedBlocks);
        data.addProperty("invalidMoves", invalidMoves);
        data.add("acceptedMoves", new Gson().toJsonTree(Arrays.copyOf(moves, players)));
        data.addProperty("entities", cows.size());
        int[] lived = new int[cows.size()];
        for (int i = 0; i < cows.size(); i++) {
            Cow cow = cows.get(i);
            if (!cow.isValid() || !cow.hasAI() || cow.getWorld() != world)
                throw new IllegalStateException("Gameplay entity disappeared or stopped AI");
            lived[i] = cow.getTicksLived() - entityStartTicks[i];
        }
        data.add("entityTickDeltas", new Gson().toJsonTree(lived));
        return data;
    }

    void stop() {
        active = measuring = false;
        for (Cow cow : cows) cow.remove();
        cows.clear();
    }
}
