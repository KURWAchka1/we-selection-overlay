package org.marshal.wesotest;

import com.mojang.blaze3d.platform.InputConstants;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.core.BlockPos;

public final class UiGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> {
            client.options.renderDistance().set(4);
            client.options.guiScale().set(2);
            client.options.enableVsync().set(false);
            client.options.framerateLimit().set(120);
            client.options.inactivityFpsLimit().set(net.minecraft.client.InactivityFpsLimit.MINIMIZED);
            client.resizeGui();
        });
        try (var world = context.worldBuilder().create()) {
            world.getServer().runCommand("gamemode spectator @p");
            world.getServer().runCommand("fill -12 64 -12 32 64 32 stone");
            world.getServer().runCommand("fill 0 65 0 5 70 5 stone");
            world.getServer().runCommand("fill 0 68 0 5 68 5 diamond_block");
            world.getServer().runCommand("tp @p 16 76 21 145 25");
            world.getServer().runCommand("time set day");
            world.getConnection().waitForChunksRender();
            fps(context, "world-ui-closed");
            open(context, 79, "OverlaySettingsScreen");
            Object overlay = context.computeOnClient(client -> field(client.gui.screen(), "overlayClient"));
            context.runOnClient(client -> {
                call(overlay, "setUiBlur", false);
                call(overlay, "setReducedMotion", false);
                Object selection = field(overlay, "selectionManager");
                call(selection, "setFallbackCuboidPoint", 0, new BlockPos(0, 65, 0));
                call(selection, "setFallbackCuboidPoint", 1, new BlockPos(5, 70, 5));
                call(overlay, "setIsolatedBlockIds", Set.of());
            });
            context.waitTicks(20);
            clickField(context, "overlayButton");
            context.runOnClient(client -> { if (((AbstractWidget) field(client.gui.screen(), "gridButton")).visible) throw new AssertionError("Grid did not hide"); });
            clickField(context, "overlayButton");
            context.runOnClient(client -> { if (!((AbstractWidget) field(client.gui.screen(), "gridButton")).visible) throw new AssertionError("Grid did not appear"); });
            context.takeScreenshot("ui-settings-glass-2x-en");
            context.clickScreenButton("Appearance");
            context.waitTicks(8);
            context.takeScreenshot("ui-appearance-menu");
            context.clickScreenButton("Classic");
            profile(context, overlay, "settings-classic");
            context.takeScreenshot("ui-settings-classic");
            context.clickScreenButton("Appearance");
            context.clickScreenButton("Glass");
            profile(context, overlay, "settings-glass");
            context.runOnClient(client -> call(overlay, "setUiBlur", true));
            context.waitTicks(8);
            context.takeScreenshot("ui-settings-optional-blur");
            context.runOnClient(client -> call(overlay, "setUiBlur", false));
            clickField(context, "colorEditorButton");
            context.waitFor(client -> client.gui.screen().getClass().getSimpleName().equals("OverlayColorSettingsScreen"));
            context.waitTicks(12);
            profile(context, overlay, "palette-glass");
            context.takeScreenshot("ui-palette-glass-2x-en");
            context.runOnClient(client -> client.gui.screen().onClose());
            context.waitTicks(12);
            context.clickScreenButton("Appearance");
            context.clickScreenButton("Classic");
            clickField(context, "colorEditorButton");
            profile(context, overlay, "palette-classic");
            context.runOnClient(client -> client.gui.screen().onClose());
            context.waitTicks(12);
            context.clickScreenButton("Appearance");
            context.clickScreenButton("Glass");
            clickField(context, "colorEditorButton");
            context.waitTicks(12);
            context.clickScreenButton("Presets...");
            context.takeScreenshot("ui-palette-presets");
            context.runOnClient(client -> {
                Object screen = client.gui.screen();
                if (client.gui.screen().getFocused() != null) throw new AssertionError("Palette input focused behind popup");
                Class<?> type = java.util.Arrays.stream(screen.getClass().getDeclaredClasses()).filter(c -> c.getSimpleName().equals("OverlayPreset")).findFirst().orElseThrow();
                Object first = type.getEnumConstants()[0];
                double x = ((Number) call(screen, "templateMenuX")).doubleValue() + 10;
                double y = ((Number) call(screen, "templateMenuItemY", first)).doubleValue() + 5;
                call(screen, "tryClickTemplateMenu", x, y);
                client.gui.screen().keyPressed(new net.minecraft.client.input.KeyEvent(257, 0, 0));
                if (field(screen, "pendingPreset") == null) throw new AssertionError("Enter applied the preset");
            });
            context.waitTicks(4);
            context.takeScreenshot("ui-palette-preset-confirmation");
            context.runOnClient(client -> {
                client.gui.screen().keyPressed(new net.minecraft.client.input.KeyEvent(256, 0, 0));
                if (field(client.gui.screen(), "pendingPreset") != null) throw new AssertionError("Escape did not cancel preset");
            });
            context.runOnClient(client -> client.gui.screen().onClose());
            context.waitTicks(12);
            context.runOnClient(client -> client.gui.setScreen(null));
            open(context, 66, "SelectionBlocksScreen");
            context.waitTicks(30);
            profile(context, overlay, "blocks-glass");
            context.takeScreenshot("ui-blocks-glass-2x-en");
            context.runOnClient(client -> call(overlay, "setIsolatedBlockIds", Set.of("minecraft:diamond_block")));
            context.waitTicks(15);
            context.takeScreenshot("ui-blocks-checked");
            context.runOnClient(client -> call(overlay, "setIsolatedBlockIds", Set.of()));
            context.waitTicks(15);
            context.runOnClient(client -> client.gui.setScreen(null));
            fps(context, "world-warmed-after-ui");
            context.runOnClient(client -> call(overlay, "setUiStyle", style(overlay, "LEGACY")));
            open(context, 66, "SelectionBlocksScreen");
            profile(context, overlay, "blocks-classic");
            context.runOnClient(client -> {
                call(overlay, "setUiStyle", style(overlay, "GLASS"));
                client.gui.setScreen(null);
            });
            open(context, 79, "OverlaySettingsScreen");
            context.clickScreenButton("Appearance");
            context.clickScreenButton("Studio");
            context.waitTicks(12);
            bounds(context);
            context.takeScreenshot("ui-settings-studio");
            clickField(context, "colorEditorButton");
            context.waitTicks(12);
            bounds(context);
            context.takeScreenshot("ui-palette-studio");
            context.runOnClient(client -> client.gui.setScreen(null));
            open(context, 66, "SelectionBlocksScreen");
            context.waitTicks(12);
            bounds(context);
            context.takeScreenshot("ui-blocks-studio");
            context.runOnClient(client -> {
                call(overlay, "setUiStyle", style(overlay, "GLASS"));
                call(overlay, "setReducedMotion", true);
                client.gui.setScreen(null);
            });
            open(context, 79, "OverlaySettingsScreen");
            context.runOnClient(client -> {
                client.gui.screen().onClose();
                if (client.gui.screen() != null) throw new AssertionError("Reduced Motion delayed closing");
                call(overlay, "setReducedMotion", false);
            });

            AtomicReference<java.util.concurrent.CompletableFuture<Void>> reload = new AtomicReference<>();
            context.runOnClient(client -> {
                client.getLanguageManager().setSelected("ru_ru");
                reload.set(client.reloadResourcePacks());
            });
            context.waitFor(client -> reload.get().isDone());
            for (int[] size : new int[][]{{1920, 1080}, {1280, 720}}) {
            context.runOnClient(client -> client.getWindow().setWindowed(size[0], size[1]));
            context.waitFor(client -> client.getWindow().getWidth() == size[0] && client.getWindow().getHeight() == size[1]);
            for (int scale : new int[]{2, 3, 4}) {
                String suffix = size[0] + "x" + size[1] + "-" + scale + "x-ru";
                context.runOnClient(client -> {
                    client.options.guiScale().set(scale);
                    client.resizeGui();
                    client.gui.setScreen(null);
                });
                open(context, 79, "OverlaySettingsScreen");
                context.waitTicks(12);
                bounds(context);
                context.takeScreenshot("ui-settings-glass-" + suffix);
                clickField(context, "colorEditorButton");
                context.waitTicks(12);
                bounds(context);
                context.runOnClient(client -> {
                    Object screen = client.gui.screen();
                    int lineY = ((Number) call(screen, "lineWidthSliderY")).intValue();
                    int redY = ((Number) call(screen, "redSliderY")).intValue();
                    if (lineY - redY < 106) throw new AssertionError("Thickness label too close to alpha slider");
                    if (!(Boolean) call(screen, "stackedLayout")) {
                        int side = ((Number) call(screen, "leftWidth")).intValue();
                        for (Object object : (java.util.List<?>) field(screen, "groupButtons")) {
                            var button = (AbstractWidget) object;
                            if (button.getX() != side - button.getX() - button.getWidth()) throw new AssertionError("Asymmetric shape row padding");
                        }
                    }
                });
                context.takeScreenshot("ui-palette-glass-" + suffix);
                context.runOnClient(client -> client.gui.screen().mouseScrolled(client.gui.screen().width / 2.0, client.gui.screen().height / 2.0, 0, -40));
                context.waitTicks(4);
                bounds(context);
                context.takeScreenshot("ui-palette-controls-" + suffix);
                for (int step = 0; step < 8; step++) {
                    if (context.computeOnClient(client -> ((AbstractWidget) field(client.gui.screen(), "redField")).visible)) break;
                    context.runOnClient(client -> client.gui.screen().mouseScrolled(client.gui.screen().width / 2.0, client.gui.screen().height / 2.0, 0, 1));
                    context.waitTicks(2);
                }
                context.takeScreenshot("ui-palette-fields-" + suffix);
                context.runOnClient(client -> {
                    var field = (net.minecraft.client.gui.components.EditBox) field(client.gui.screen(), "redField");
                    if (!field.visible) throw new AssertionError("Color input not reachable by scrolling");
                    field.setFocused(true);
                    field.setValue("128");
                    if (!field.getValue().equals("128")) throw new AssertionError("Color input failed");
                    client.gui.screen().mouseClicked(new net.minecraft.client.input.MouseButtonEvent(field.getX() + 4, field.getY() + 9, new net.minecraft.client.input.MouseButtonInfo(0, 0)), false);
                    if (field.getCursorPosition() != 0) throw new AssertionError("Input left padding shifted caret");
                    client.gui.screen().mouseClicked(new net.minecraft.client.input.MouseButtonEvent(field.getX() + 4 + client.font.width("12"), field.getY() + 9, new net.minecraft.client.input.MouseButtonInfo(0, 0)), false);
                    if (field.getCursorPosition() != 2) throw new AssertionError("Input click does not match text position");
                });
                context.runOnClient(client -> client.gui.setScreen(null));
                open(context, 66, "SelectionBlocksScreen");
                context.waitTicks(15);
                bounds(context);
                context.runOnClient(client -> {
                    Object screen = client.gui.screen();
                    if ((Boolean) call(screen, "hasRail")) {
                        for (String name : new String[]{"searchField", "sortButton", "filterButton", "refreshButton"}) {
                            var widget = (AbstractWidget) field(screen, name);
                            if (widget.getX() != 188 - widget.getX() - widget.getWidth()) throw new AssertionError("Asymmetric inspector rail padding");
                        }
                    }
                });
                context.takeScreenshot("ui-blocks-glass-" + suffix);
            }
            }
            context.runOnClient(client -> {
                call(overlay, "setUiBlur", false);
                call(overlay, "setReducedMotion", false);
                client.gui.setScreen(null);
            });
            System.out.println("WESO UI PASS: settings, palette, inspector, popovers, styles, EN/RU, GUI 2x/3x/4x");
        }
    }

    private static void profile(ClientGameTestContext context, Object overlay, String label) {
        if (Boolean.getBoolean("weso.ui.skipProfile")) return;
        Class<?> type;
        try {
            type = Class.forName(overlay.getClass().getPackageName() + ".screen.UiPerformance");
        } catch (ClassNotFoundException e) { throw new AssertionError(e); }
        context.waitTicks(30);
        context.runOnClient(client -> invokeStatic(type, "reset"));
        context.waitTicks(160);
        double[] result = context.computeOnClient(client -> (double[]) invokeStatic(type, "result"));
        if (result[0] < 100) throw new AssertionError("Not enough UI samples");
        System.out.printf(java.util.Locale.ROOT, "WESO UI CPU %s: n=%.0f median=%.3f ms p95=%.3f ms%n", label, result[0], result[1], result[2]);
        fps(context, label);
        if (!label.startsWith("palette") && result[2] > 2.0) throw new AssertionError("UI extraction exceeded 2 ms p95: " + label);
    }

    private static void fps(ClientGameTestContext context, String label) {
        if (Boolean.getBoolean("weso.ui.skipProfile")) return;
        int min = Integer.MAX_VALUE, total = 0;
        for (int i = 0; i < 5; i++) {
            context.waitTicks(20);
            int value = context.computeOnClient(client -> client.getFps());
            total += value;
            min = Math.min(min, value);
        }
        System.out.printf(java.util.Locale.ROOT, "WESO UI FPS %s: mean=%.1f min=%d cap=120 vsync=off%n", label, total / 5.0, min);
    }

    private static Object invokeStatic(Class<?> type, String method) {
        try { return type.getMethod(method).invoke(null); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    private static Object style(Object overlay, String name) {
        try {
            for (Object value : Class.forName(overlay.getClass().getPackageName() + ".screen.UiStyle").getEnumConstants()) {
                if (((Enum<?>) value).name().equals(name)) return value;
            }
            throw new AssertionError("Missing style: " + name);
        } catch (ClassNotFoundException e) { throw new AssertionError(e); }
    }

    private static void bounds(ClientGameTestContext context) {
        context.runOnClient(client -> {
            var screen = client.gui.screen();
            for (var child : screen.children()) {
                if (child instanceof AbstractWidget widget && widget.visible) {
                    if (widget.getX() < 0 || widget.getY() < 0 || widget.getX() + widget.getWidth() > screen.width
                            || widget.getY() + widget.getHeight() > screen.height) {
                        throw new AssertionError("Widget outside screen: " + widget.getMessage().getString());
                    }
                }
            }
        });
    }

    private static void open(ClientGameTestContext context, int key, String name) {
        context.runOnClient(client -> KeyMapping.click(InputConstants.Type.KEYSYM.getOrCreate(key)));
        context.waitFor(client -> client.gui.screen() != null && client.gui.screen().getClass().getSimpleName().equals(name));
    }

    private static void clickField(ClientGameTestContext context, String name) {
        String message = context.computeOnClient(client -> ((AbstractWidget) field(client.gui.screen(), name)).getMessage().getString());
        context.clickScreenButton(message);
    }

    private static Object field(Object target, String name) {
        try { Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    private static Object call(Object target, String name, Object... args) {
        for (Method method : target.getClass().getDeclaredMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == args.length) {
                try { method.setAccessible(true); return method.invoke(target, args); }
                catch (ReflectiveOperationException e) { throw new AssertionError(e); }
            }
        }
        throw new AssertionError("Missing method: " + name);
    }
}
