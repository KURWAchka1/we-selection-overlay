package org.marshal.wesotest;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@Mod("weso_native_bridge_test")
public final class NeoNativeBridgeTest {
    public NeoNativeBridgeTest() {
        var checks = new NativeBridgeChecks(new NativeBridgeChecks.Adapter() {
            private boolean opened;
            private Minecraft client() { return Minecraft.getInstance(); }
            @Override public boolean joined() { return client().level != null && client().player != null; }
            @Override public Object player() { return client().player; }
            @Override public Object world() { return client().level; }
            @Override public Object connection() { return client().getConnection(); }
            @Override public Object game() { return client(); }
            @Override public void focusForEditing(boolean focused) { client().setWindowActive(focused); }
            @Override public void editKey(Object key, boolean held) { ((KeyMapping) key).setDown(held); }
            @Override public NativeGestureInput gestureInput() {
                return new NativeGestureInput() {
                    public void key(int code, int action) { NativeGestureInput.callback(client().keyboardHandler, "keyPress", new Class<?>[]{long.class, int.class, net.minecraft.client.input.KeyEvent.class}, client().getWindow().handle(), action, new net.minecraft.client.input.KeyEvent(code, 0, 0)); }
                    public void button(int action) { NativeGestureInput.callback(client().mouseHandler, "onButton", new Class<?>[]{long.class, net.minecraft.client.input.MouseButtonInfo.class, int.class}, client().getWindow().handle(), new net.minecraft.client.input.MouseButtonInfo(0, 0), action); }
                    public void cursor(double x, double y) { NativeGestureInput.callback(client().mouseHandler, "onMove", new Class<?>[]{long.class, double.class, double.class}, client().getWindow().handle(), x, y); }
                    public void scroll(double amount) { NativeGestureInput.callback(client().mouseHandler, "onScroll", new Class<?>[]{long.class, double.class, double.class}, client().getWindow().handle(), 0.0, amount); }
                    public void aim(double x, double y, double z) {
                        var eye = client().player.getEyePosition(); double dx = x - eye.x, dy = y - eye.y, dz = z - eye.z;
                        client().player.setYRot((float) Math.toDegrees(Math.atan2(-dx, dz)));
                        client().player.setXRot((float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz))));
                    }
                    public int width() { return client().getWindow().getScreenWidth(); }
                    public int height() { return client().getWindow().getScreenHeight(); }
                    public double yaw() { return client().player.getYRot(); }
                    public double pitch() { return client().player.getXRot(); }
                    public boolean grabbed() { return client().mouseHandler.isMouseGrabbed(); }
                    public boolean attacking() { return client().options.keyAttack.isDown() || client().mouseHandler.isLeftPressed(); }
                    public boolean screenOpen() { return client().screen != null; }
                    public void chat(boolean open) { client().setScreen(open ? new net.minecraft.client.gui.screens.ChatScreen("", false) : null); }
                    public void grab() { client().mouseHandler.grabMouse(); }
                };
            }
            @Override public boolean respawnIfDead() {
                if (!client().player.isDeadOrDying()) return false;
                client().player.respawn(); client().setScreen(null); return true;
            }
            @Override public void reconnect() {
                net.minecraft.client.gui.screens.ConnectScreen.startConnecting(
                        new net.minecraft.client.gui.screens.TitleScreen(), client(),
                        net.minecraft.client.multiplayer.resolver.ServerAddress.parseString("127.0.0.1:25579"),
                        new net.minecraft.client.multiplayer.ServerData("Weso loopback fixture", "127.0.0.1:25579",
                                net.minecraft.client.multiplayer.ServerData.Type.OTHER), false, null);
            }
            @Override public void configure() {
                client().options.framerateLimit().set(40);
                client().options.pauseOnLostFocus = false;
                if (client().screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen screen) screen.onClose();
            }
            @Override public Object overlay() {
                if (!opened) { KeyMapping.click(InputConstants.Type.KEYSYM.getOrCreate(79)); opened = true; return null; }
                var screen = client().screen;
                if (screen == null || !screen.getClass().getSimpleName().equals("OverlaySettingsScreen")) return null;
                try { var field = screen.getClass().getDeclaredField("overlayClient"); field.setAccessible(true); var result = field.get(screen); client().setScreen(null); return result; }
                catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
            }
            @Override public void stop() { client().stop(); }
        });
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> checks.tick());
    }
}
