package org.marshal.wesotest;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

public final class FabricNativeBridgeTest implements ClientModInitializer {
    @Override public void onInitializeClient() {
        var checks = new NativeBridgeChecks(new NativeBridgeChecks.Adapter() {
            private boolean opened;
            private MinecraftClient client() { return MinecraftClient.getInstance(); }
            @Override public boolean joined() { return client().world != null && client().player != null; }
            @Override public Object player() { return client().player; }
            @Override public Object world() { return client().world; }
            @Override public Object connection() { return client().getNetworkHandler(); }
            @Override public boolean respawnIfDead() {
                if (!client().player.isDead()) return false;
                client().player.requestRespawn(); client().setScreen(null); return true;
            }
            @Override public void reconnect() {
                net.minecraft.client.gui.screen.multiplayer.ConnectScreen.connect(
                        new net.minecraft.client.gui.screen.TitleScreen(), client(),
                        net.minecraft.client.network.ServerAddress.parse("127.0.0.1:25579"),
                        new net.minecraft.client.network.ServerInfo("Weso loopback fixture", "127.0.0.1:25579",
                                net.minecraft.client.network.ServerInfo.ServerType.OTHER), false, null);
            }
            @Override public void configure() {
                client().options.getMaxFps().setValue(40);
                client().options.pauseOnLostFocus = false;
                if (client().currentScreen instanceof net.minecraft.client.gui.screen.AccessibilityOnboardingScreen screen) screen.close();
            }
            @Override public Object overlay() {
                if (!opened) { KeyBinding.onKeyPressed(InputUtil.Type.KEYSYM.createFromCode(79)); opened = true; return null; }
                var screen = client().currentScreen;
                if (screen == null || !screen.getClass().getSimpleName().equals("OverlaySettingsScreen")) return null;
                try { var field = screen.getClass().getDeclaredField("overlayClient"); field.setAccessible(true); var result = field.get(screen); client().setScreen(null); return result; }
                catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
            }
            @Override public void stop() { client().scheduleStop(); }
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> checks.tick());
    }
}
