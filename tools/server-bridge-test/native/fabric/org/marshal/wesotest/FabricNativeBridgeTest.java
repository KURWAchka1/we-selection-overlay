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
            @Override public Object game() { return client(); }
            @Override public void focusForEditing(boolean focused) { client().onWindowFocusChanged(focused); }
            @Override public void editKey(Object key, boolean held) { ((KeyBinding) key).setPressed(held); }
            @Override public NativeGestureInput gestureInput() {
                return new NativeGestureInput() {
                    public void key(int code, int action) { NativeGestureInput.callback(client().keyboard, "method_1466", new Class<?>[]{long.class, int.class, net.minecraft.client.input.KeyInput.class}, client().getWindow().getHandle(), action, new net.minecraft.client.input.KeyInput(code, 0, 0)); }
                    public void button(int action) { NativeGestureInput.callback(client().mouse, "method_1601", new Class<?>[]{long.class, net.minecraft.client.input.MouseInput.class, int.class}, client().getWindow().getHandle(), new net.minecraft.client.input.MouseInput(0, 0), action); }
                    public void cursor(double x, double y) { NativeGestureInput.callback(client().mouse, "method_1600", new Class<?>[]{long.class, double.class, double.class}, client().getWindow().getHandle(), x, y); }
                    public void scroll(double amount) { NativeGestureInput.callback(client().mouse, "method_1598", new Class<?>[]{long.class, double.class, double.class}, client().getWindow().getHandle(), 0.0, amount); }
                    public void aim(double x, double y, double z) {
                        var eye = client().player.getEyePos(); double dx = x - eye.x, dy = y - eye.y, dz = z - eye.z;
                        client().player.setYaw((float) Math.toDegrees(Math.atan2(-dx, dz)));
                        client().player.setPitch((float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz))));
                    }
                    public int width() { return client().getWindow().getWidth(); }
                    public int height() { return client().getWindow().getHeight(); }
                    public double yaw() { return client().player.getYaw(); }
                    public double pitch() { return client().player.getPitch(); }
                    public boolean grabbed() { return client().mouse.isCursorLocked(); }
                    public boolean attacking() { return client().options.attackKey.isPressed() || client().mouse.wasLeftButtonClicked(); }
                    public boolean screenOpen() { return client().currentScreen != null; }
                    public void chat(boolean open) { client().setScreen(open ? new net.minecraft.client.gui.screen.ChatScreen("", false) : null); }
                    public void grab() { client().mouse.lockCursor(); }
                };
            }
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
