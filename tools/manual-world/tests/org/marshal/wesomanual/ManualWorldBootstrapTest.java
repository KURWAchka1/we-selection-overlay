package org.marshal.wesomanual;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public final class ManualWorldBootstrapTest {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]);
        Files.createDirectories(root);
        Host fresh = new Host(root.resolve("fresh"));
        var first = new ManualWorldBootstrap(fresh);
        first.tick();
        check(fresh.creates == 1 && fresh.opens == 0, "Missing world must be created exactly once");
        first.tick();
        check(fresh.creates == 1, "A pending startup must never be restarted");
        fresh.player = true;
        first.tick();
        fresh.configuration.complete(new ManualWorldBootstrap.Ready(true, true));
        first.tick();
        for (int i = 0; i < 100; i++) first.tick();
        check(fresh.configures == 1 && fresh.stops == 0, "Normal mode must become idle without closing the game");

        Host saved = new Host(root.resolve("saved"));
        Files.createDirectories(saved.path);
        Path sentinel = saved.path.resolve("sentinel.txt");
        Files.writeString(sentinel, "preserve me");
        var resume = new ManualWorldBootstrap(saved);
        resume.tick();
        check(saved.opens == 1 && saved.creates == 0, "Existing save must be opened, not recreated");
        check(Files.readString(sentinel).equals("preserve me"), "Existing world was overwritten");

        Host malformed = new Host(root.resolve("not-a-directory"));
        Files.writeString(malformed.path, "keep this invalid save path");
        new ManualWorldBootstrap(malformed).tick();
        check(malformed.opens == 1 && malformed.creates == 0, "Even malformed existing paths must not be replaced");

        Host cancelled = new Host(root.resolve("cancelled"));
        var cancel = new ManualWorldBootstrap(cancelled);
        cancel.tick();
        cancelled.title = true;
        for (int i = 0; i < 10; i++) cancel.tick();
        check(cancelled.creates == 1, "Cancelled or failed world load must not loop");

        System.setProperty("weso.manual.verify", "true");
        try {
            Host probe = new Host(root.resolve("probe"));
            Files.createDirectories(probe.path);
            Files.writeString(probe.path.resolve("manual-world-probe.txt"), "previous verification");
            var verify = new ManualWorldBootstrap(probe);
            verify.tick();
            probe.player = true;
            verify.tick();
            probe.configuration.complete(new ManualWorldBootstrap.Ready(true, true));
            verify.tick();
            check(probe.reopening, "Second probe must check save persistence");
            verify.tick();
            check(probe.stops == 1, "Only completed verification must close the client");
            check(Files.isRegularFile(probe.path.resolve("manual-world-probe.txt")), "Probe result missing");
        } finally {
            System.clearProperty("weso.manual.verify");
        }
        System.out.println("MANUAL WORLD BOOTSTRAP UNIT PASS");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class Host implements ManualWorldBootstrap.Host {
        private final Path path;
        private boolean title = true, player, reopening;
        private int creates, opens, configures, stops;
        private final CompletableFuture<ManualWorldBootstrap.Ready> configuration = new CompletableFuture<>();

        private Host(Path path) { this.path = path; }
        public boolean readyToOpen() { return title; }
        public boolean hasPlayer() { return player; }
        public Path worldPath(String id) { return path; }
        public void createWorld(String id) { creates++; title = false; }
        public void openWorld(String id) { opens++; title = false; }
        public CompletableFuture<ManualWorldBootstrap.Ready> configurePlayer(Path expected) {
            check(path.toAbsolutePath().normalize().equals(expected), "Wrong world path");
            configures++;
            return configuration;
        }
        public CompletableFuture<?> verifySave(boolean reopening) {
            this.reopening = reopening;
            return CompletableFuture.completedFuture(null);
        }
        public void stopClient() { stops++; }
    }
}
