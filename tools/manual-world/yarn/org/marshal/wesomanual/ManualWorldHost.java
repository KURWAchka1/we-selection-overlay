package org.marshal.wesomanual;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.command.permission.LeveledPermissionPredicate;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.server.PlayerConfigEntry;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.level.LevelInfo;
import net.minecraft.world.rule.GameRules;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.block.Blocks;

public final class ManualWorldHost implements ManualWorldBootstrap.Host {
    private MinecraftClient client;

    public boolean readyToOpen() {
        if (client == null) client = MinecraftClient.getInstance();
        if (client == null) return false;
        if (client.getOverlay() != null) return false;
        if (client.currentScreen instanceof AccessibilityOnboardingScreen screen) {
            screen.close();
            return false;
        }
        return client.currentScreen instanceof TitleScreen;
    }

    public boolean hasPlayer() {
        return client.player != null && client.world != null && client.getServer() != null;
    }

    public Path worldPath(String id) {
        return client.getLevelStorage().resolve(id);
    }

    public void createWorld(String id) {
        var settings = new LevelInfo("WESO Test World", GameMode.CREATIVE, false, Difficulty.PEACEFUL,
                true, new GameRules(DataConfiguration.SAFE_MODE.enabledFeatures()), DataConfiguration.SAFE_MODE);
        client.createIntegratedServerLoader().createAndStart(id, settings, new GeneratorOptions(0L, false, false),
                registries -> registries.getOrThrow(RegistryKeys.WORLD_PRESET)
                        .getOrThrow(WorldPresets.FLAT).value().createDimensionsRegistryHolder(), client.currentScreen);
    }

    public void openWorld(String id) {
        var parent = client.currentScreen;
        client.createIntegratedServerLoader().start(id, () -> client.setScreen(parent));
    }

    public CompletableFuture<ManualWorldBootstrap.Ready> configurePlayer(Path expectedWorld) {
        var server = client.getServer();
        var id = client.player.getUuid();
        return server.submit(() -> {
            if (!server.getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize().equals(expectedWorld)) {
                throw new IllegalStateException("Refusing to configure an unrelated world");
            }
            var player = server.getPlayerManager().getPlayer(id);
            if (player == null) throw new IllegalStateException("Test player has not joined the server");
            var identity = new PlayerConfigEntry(player.getGameProfile());
            server.setDefaultGameMode(GameMode.CREATIVE);
            player.changeGameMode(GameMode.CREATIVE);
            server.getPlayerManager().addToOperators(identity, Optional.of(LeveledPermissionPredicate.OWNERS), Optional.empty());
            return new ManualWorldBootstrap.Ready(player.interactionManager.getGameMode() == GameMode.CREATIVE,
                    server.getPlayerManager().isOperator(identity)
                            && server.getPermissionLevel(identity).getLevel() == LeveledPermissionPredicate.OWNERS.getLevel());
        });
    }

    public CompletableFuture<?> verifySave(boolean reopening) {
        var server = client.getServer();
        var id = client.player.getUuid();
        return server.submit(() -> {
            var pos = new BlockPos(16, -60, 16);
            if (reopening) {
                if (!server.getOverworld().getBlockState(pos).isOf(Blocks.DIAMOND_BLOCK)) {
                    throw new IllegalStateException("Saved probe block was not preserved");
                }
            } else {
                server.getOverworld().setBlockState(pos, Blocks.DIAMOND_BLOCK.getDefaultState());
            }
            var player = server.getPlayerManager().getPlayer(id);
            player.changeGameMode(GameMode.SURVIVAL);
            server.getPlayerManager().removeFromOperators(new PlayerConfigEntry(player.getGameProfile()));
        });
    }

    public void stopClient() {
        client.scheduleStop();
    }
}
