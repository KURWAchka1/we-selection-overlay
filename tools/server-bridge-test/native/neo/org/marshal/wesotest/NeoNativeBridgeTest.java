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
