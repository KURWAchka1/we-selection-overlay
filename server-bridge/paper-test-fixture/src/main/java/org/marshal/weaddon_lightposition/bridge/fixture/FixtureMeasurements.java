package org.marshal.weaddon_lightposition.bridge.fixture;

import com.destroystokyo.paper.event.server.ServerTickEndEvent;
import com.destroystokyo.paper.event.server.ServerTickStartEvent;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.management.ThreadMXBean;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Arrays;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Test-only, bounded samples; never linked or bundled into the companion. */
final class FixtureMeasurements implements Listener {
    private final JavaPlugin plugin;
    private final ThreadMXBean threads;
    private final int sampleCount;
    private final double[] ticks;
    private String label, startedAt;
    private int samples, chunkLoads, generatedChunks, phases;
    private long startNanos, startCpu, startAllocated, startGcMillis, startGcCount;
    private long tickCpu, tickAllocated;
    private FixtureTickEvent pendingTick;

    FixtureMeasurements(JavaPlugin plugin) {
        this.plugin = plugin;
        sampleCount = Integer.getInteger("weso.bridge.measureTicks", 600);
        if (sampleCount < 100 || sampleCount > 2400) throw new IllegalArgumentException("Invalid fixture sample count");
        ticks = new double[sampleCount];
        threads = ManagementFactory.getPlatformMXBean(ThreadMXBean.class);
        if (threads == null || !threads.isCurrentThreadCpuTimeSupported() || !threads.isThreadAllocatedMemorySupported())
            throw new IllegalStateException("Fixture requires thread CPU/allocation measurements");
        threads.setThreadCpuTimeEnabled(true);
        threads.setThreadAllocatedMemoryEnabled(true);
    }

    void start(String name) {
        if (label != null || phases >= 16 || !name.matches("[a-z][a-z0-9-]{0,31}"))
            throw new IllegalArgumentException("Invalid or overlapping measurement");
        label = name;
        phases++;
        samples = chunkLoads = generatedChunks = 0;
        pendingTick = null;
        startGcMillis = gcMillis();
        startGcCount = gcCount();
        startedAt = Instant.now().toString();
        plugin.getLogger().info("WESO_MEASURE_START " + label);
        startCpu = threads.getCurrentThreadCpuTime();
        startAllocated = threads.getCurrentThreadAllocatedBytes();
        startNanos = System.nanoTime();
    }

    @EventHandler public void chunk(ChunkLoadEvent event) {
        if (label != null) {
            chunkLoads++;
            if (event.isNewChunk()) generatedChunks++;
        }
    }

    @EventHandler public void beginTick(ServerTickStartEvent event) {
        if (label == null) return;
        if (pendingTick != null) throw new IllegalStateException("Unpaired fixture tick start");
        pendingTick = new FixtureTickEvent();
        pendingTick.phase = label;
        pendingTick.sample = samples;
        pendingTick.paperTick = event.getTickNumber();
        tickCpu = threads.getCurrentThreadCpuTime();
        tickAllocated = threads.getCurrentThreadAllocatedBytes();
        pendingTick.begin();
    }

    @EventHandler public void tick(ServerTickEndEvent event) {
        if (label == null) return;
        // A console command can start a window after this tick's start event.
        if (pendingTick == null) return;
        pendingTick.end();
        if (pendingTick.paperTick != event.getTickNumber()) throw new IllegalStateException("Unpaired fixture tick end");
        double duration = event.getTickDuration();
        if (!Double.isFinite(duration) || duration < 0) throw new IllegalStateException("Invalid Paper tick sample");
        pendingTick.paperMillis = duration;
        pendingTick.bracketCpuNanos = threads.getCurrentThreadCpuTime() - tickCpu;
        pendingTick.bracketAllocatedBytes = threads.getCurrentThreadAllocatedBytes() - tickAllocated;
        pendingTick.commit();
        pendingTick = null;
        ticks[samples++] = duration;
        if (samples == sampleCount) finish();
    }

    private void finish() {
        long nanos = System.nanoTime() - startNanos;
        long cpu = threads.getCurrentThreadCpuTime() - startCpu;
        long allocated = threads.getCurrentThreadAllocatedBytes() - startAllocated;
        String endedAt = Instant.now().toString();
        var data = new JsonObject();
        data.addProperty("label", label);
        data.addProperty("tickTimelineVersion", 1);
        data.addProperty("startedAt", startedAt);
        data.addProperty("endedAt", endedAt);
        data.addProperty("samples", samples);
        data.addProperty("wallMillis", nanos / 1_000_000.0);
        data.addProperty("ownerCpuMillis", cpu / 1_000_000.0);
        data.addProperty("ownerAllocatedBytes", allocated);
        data.addProperty("gcMillis", gcMillis() - startGcMillis);
        data.addProperty("gcCount", gcCount() - startGcCount);
        data.addProperty("usedHeapBytes", ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed());
        data.addProperty("players", plugin.getServer().getOnlinePlayers().size());
        data.addProperty("chunkLoads", chunkLoads);
        data.addProperty("generatedChunks", generatedChunks);
        data.add("tickMillis", new Gson().toJsonTree(ticks));
        double[] sorted = ticks.clone();
        Arrays.sort(sorted);
        data.addProperty("p50", sorted[(int) Math.ceil(samples * .50) - 1]);
        data.addProperty("p95", sorted[(int) Math.ceil(samples * .95) - 1]);
        data.addProperty("p99", sorted[(int) Math.ceil(samples * .99) - 1]);
        data.addProperty("max", sorted[samples - 1]);
        try {
            var directory = plugin.getDataFolder().toPath();
            Files.createDirectories(directory);
            Files.writeString(directory.resolve("load-" + label + ".json"), new Gson().toJson(data), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot write fixture measurement", ex);
        }
        plugin.getLogger().info("WESO_MEASURE_DONE " + label);
        label = null;
    }

    private static long gcMillis() {
        return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(b -> Math.max(0, b.getCollectionTime())).sum();
    }

    private static long gcCount() {
        return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(b -> Math.max(0, b.getCollectionCount())).sum();
    }
}
