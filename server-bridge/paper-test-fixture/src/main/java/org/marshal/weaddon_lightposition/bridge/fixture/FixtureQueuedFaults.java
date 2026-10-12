package org.marshal.weaddon_lightposition.bridge.fixture;

import com.destroystokyo.paper.event.server.ServerTickStartEvent;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Observes real queued wire input before a fault; never injects a core request. */
final class FixtureQueuedFaults implements Listener {
    private final JavaPlugin fixture;
    private final FixtureGameplay gameplay;
    private final java.util.function.Consumer<String> revoke;
    private String action, name;
    private int attempts, completed;

    FixtureQueuedFaults(JavaPlugin fixture, FixtureGameplay gameplay, java.util.function.Consumer<String> revoke) {
        this.fixture = fixture;
        this.gameplay = gameplay;
        this.revoke = revoke;
    }

    void arm(String action, String name) {
        if (this.action != null || completed >= 16 || !name.matches("WesoLoad[0-9]{2}")
                || !java.util.Set.of("revoke", "disconnect", "bridgeoff", "stopwe").contains(action))
            throw new IllegalArgumentException("Invalid queued fault");
        this.action = action;
        this.name = name;
        attempts = 0;
    }

    @EventHandler public void tick(ServerTickStartEvent ignored) {
        if (action == null) return;
        try {
            if (++attempts > 200) throw new IllegalStateException("Target never had a queued wire request");
            Plugin plugin = Objects.requireNonNull(fixture.getServer().getPluginManager()
                    .getPlugin("WorldEditSelectionOverlayBridge"));
            Object core = field(plugin, "bridge");
            if (core == null) throw new IllegalStateException("No active core");
            var player = Objects.requireNonNull(fixture.getServer().getPlayerExact(name));
            UUID id = player.getUniqueId();
            Object peer = ((Map<?, ?>) field(core, "pending")).get(id);
            if (peer == null) return;
            byte[] frame = ((byte[]) field(peer, "pending")).clone();
            if (frame.length != 30 || frame[5] != 3) return;
            var result = new JsonObject();
            result.addProperty("version", 1);
            result.addProperty("action", action);
            result.addProperty("player", name);
            result.addProperty("at", Instant.now().toString());
            result.addProperty("attempts", attempts);
            result.addProperty("requestHex", HexFormat.of().formatHex(frame));
            result.add("before", snapshot(core));
            String label = "fault-" + (++completed) + "-" + action;
            switch (action) {
                case "revoke" -> revoke.accept(name);
                case "disconnect" -> player.kick(Component.text("Queued lifecycle fixture"));
                case "bridgeoff" -> fixture.getServer().getPluginManager().disablePlugin(plugin);
                case "stopwe" -> {
                    gameplay.pauseEdits();
                    fixture.getServer().getPluginManager().disablePlugin(Objects.requireNonNull(
                            fixture.getServer().getPluginManager().getPlugin("WorldEdit")));
                }
                default -> throw new IllegalStateException("Unknown queued fault");
            }
            result.add("after", snapshot(core));
            result.addProperty("retainedTarget", ((Map<?, ?>) field(core, "peers")).containsKey(id));
            result.addProperty("bridgeEnabled", plugin.isEnabled());
            var directory = fixture.getDataFolder().toPath();
            Files.createDirectories(directory);
            Files.writeString(directory.resolve(label + ".json"), new Gson().toJson(result), StandardCharsets.UTF_8);
            fixture.getLogger().info("WESO_QUEUED_FAULT_DONE " + label);
            action = name = null;
        } catch (ReflectiveOperationException | IOException | RuntimeException ex) {
            action = name = null;
            throw new IllegalStateException("Queued lifecycle fixture failed", ex);
        }
    }

    private static Object field(Object target, String name) throws ReflectiveOperationException {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static JsonObject snapshot(Object core) throws ReflectiveOperationException {
        var result = new JsonObject();
        var gson = new Gson();
        result.add("metrics", gson.toJsonTree(core.getClass().getMethod("metrics").invoke(core)));
        result.add("queue", gson.toJsonTree(core.getClass().getMethod("queueMetrics").invoke(core)));
        return result;
    }
}
