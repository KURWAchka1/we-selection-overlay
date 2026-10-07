package org.marshal.wesomanual;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ManualWorldBootstrap {
    public static final String WORLD_ID = "weso-test-world";
    private static final Logger LOG = LoggerFactory.getLogger("WESO Manual World");
    private final Host host;
    private final boolean verification = Boolean.getBoolean("weso.manual.verify");
    private boolean started, complete, verifying;
    private Path expectedWorld;
    private CompletableFuture<?> pending;

    public ManualWorldBootstrap(Host host) {
        this.host = host;
    }

    public void tick() {
        if (complete) return;
        try {
            if (pending != null) {
                if (!pending.isDone()) return;
                Object result = pending.join();
                pending = null;
                if (verifying) {
                    Files.writeString(expectedWorld.resolve("manual-world-probe.txt"), "verified\n");
                    LOG.info("MANUAL WORLD VERIFY PASS: {}", expectedWorld);
                    finish();
                    return;
                }
                Ready ready = (Ready) result;
                if (!ready.creative() || !ready.operator()) {
                    throw new IllegalStateException("Test player did not receive Creative and operator permissions");
                }
                LOG.info("MANUAL WORLD READY: creative=true operator=true world={}", expectedWorld);
                if (verification) {
                    verifying = true;
                    pending = host.verifySave(Files.isRegularFile(expectedWorld.resolve("manual-world-probe.txt")));
                } else {
                    complete = true;
                }
                return;
            }
            if (started) {
                if (host.hasPlayer()) {
                    pending = host.configurePlayer(expectedWorld);
                } else if (host.readyToOpen()) {
                    LOG.warn("Manual world opening was cancelled or failed; it will not be retried automatically");
                    finish();
                }
                return;
            }
            if (!host.readyToOpen()) return;
            expectedWorld = host.worldPath(WORLD_ID).toAbsolutePath().normalize();
            started = true;
            if (Files.exists(expectedWorld, LinkOption.NOFOLLOW_LINKS)) {
                LOG.info("Opening existing manual test world: {}", expectedWorld);
                host.openWorld(WORLD_ID);
            } else {
                LOG.info("Creating flat Creative manual test world with commands: {}", expectedWorld);
                host.createWorld(WORLD_ID);
            }
        } catch (Exception failure) {
            LOG.error("MANUAL WORLD FAILED; existing saves have not been replaced", failure);
            finish();
        }
    }

    private void finish() {
        complete = true;
        if (verification) host.stopClient();
    }

    public record Ready(boolean creative, boolean operator) {}

    public interface Host {
        boolean readyToOpen();
        boolean hasPlayer();
        Path worldPath(String id);
        void createWorld(String id);
        void openWorld(String id);
        CompletableFuture<Ready> configurePlayer(Path expectedWorld);
        CompletableFuture<?> verifySave(boolean reopening);
        void stopClient();
    }
}
