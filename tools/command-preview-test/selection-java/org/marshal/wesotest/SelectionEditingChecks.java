package org.marshal.wesotest;

import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.fabric.FabricAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.selector.CuboidRegionSelector;
import com.sk89q.worldedit.regions.selector.ExtendingCuboidRegionSelector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.marshal.weaddon_lightposition.runtime.mc26_2.client.selection.SelectionEditController;
import org.marshal.weaddon_lightposition.runtime.mc26_2.selection.CuboidDraft;
import org.marshal.weaddon_lightposition.runtime.mc26_2.selection.CuboidDraft.Face;
import org.marshal.weaddon_lightposition.runtime.mc26_2.selection.CuboidDraft.Corner;
import org.marshal.weaddon_lightposition.runtime.mc26_2.selection.CuboidDraft.Point;
import org.marshal.weaddon_lightposition.runtime.mc26_2.selection.worldedit.SelectionEditTransaction;
import java.util.Set;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Disposable-world checks run off both game threads; every mutation is dispatched to its owner. */
public final class SelectionEditingChecks {
    public interface Dispatch {
        <T> T client(Callable<T> task);
        <T> T server(Callable<T> task);
        void pause(long millis);
    }
    private final Minecraft client;
    private final Object overlay, clipboardSource;
    private final Consumer<String> screenshot;
    private final Dispatch dispatch;
    private final SelectionEditController editing = SelectionEditController.instance();
    private KeyMapping hold, fast;
    private Object cachedClipboard;
    private boolean lastAimAngled;
    private final CuboidDraft original = new CuboidDraft(-8, 66, -8, -3, 71, -3);
    private SelectionEditingChecks(Minecraft client, Object overlay, Object clipboardSource, Consumer<String> screenshot, Dispatch dispatch) {
        this.client = client; this.overlay = overlay; this.clipboardSource = clipboardSource; this.screenshot = screenshot; this.dispatch = dispatch;
    }
    public static void run(Minecraft client, Object overlay, Object clipboardSource, Consumer<String> screenshot) {
        run(client, overlay, clipboardSource, screenshot, new Dispatch() {
            public <T> T client(Callable<T> task) { return client.submit(() -> checked(task)).join(); }
            public <T> T server(Callable<T> task) { return client.getSingleplayerServer().submit(() -> checked(task)).join(); }
            public void pause(long millis) { try { Thread.sleep(millis); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new AssertionError(ex); } }
        });
    }
    public static void run(Minecraft client, Object overlay, Object clipboardSource, Consumer<String> screenshot, Dispatch dispatch) {
        new SelectionEditingChecks(client, overlay, clipboardSource, screenshot, dispatch).run();
    }
    private void run() {
        List<?> historyBefore = onServer(this::history);
        List<?> blocksBefore = onServer(this::worldBlocks);
        onClient(() -> {
            check(!editing.options().enabled(), "Editing must be opt-in");
            hold = (KeyMapping) field(editing, "editKey"); fast = (KeyMapping) field(editing, "fastKey");
            check(hold.isUnbound(), "Editing must not take a default key");
            cachedClipboard = call(call(clipboardSource, "snapshot"), "blocks");
            client.player.getAbilities().flying = true;
            check(editing.target() == Face.UP, "Initial face must be the upper side");
            editing.configure(true, 1, 8); return null;
        });
        onServer(() -> { client.getSingleplayerServer().getPlayerList().getPlayers().getFirst().getAbilities().flying = true; return null; });
        for (Face f : Face.values()) {
            select(false);
            aim(f, false);
            start(f);
            onClient(() -> { nativeCall(client.mouseHandler, "onScroll", client.getWindow().handle(), 0.0, 1.0); return null; });
            CuboidDraft draft = onClient(editing::draft);
            check(draft.equals(original.resize(f, 1, -64, 319)), "Wheel face sign: " + f + " got=" + draft);
            check(serverBounds().equals(original), "Draft changed authoritative region");
            release();
            await(() -> !editing.pending(), "wheel commit");
            check(serverBounds().equals(draft), "Wheel failed on " + f);
        }
        select(true); aim(Face.EAST, false); start(Face.EAST);
        double heightBeforeShift = onClient(() -> client.player.getY());
        onClient(() -> { fast.setDown(true); client.options.keyShift.setDown(true); return null; });
        pause(300);
        onClient(() -> {
            check(!client.player.input.keyPresses.shift(), "Editing Shift retained sneak input");
            check(Math.abs(client.player.getY() - heightBeforeShift) < 0.05, "Editing Shift lowered flying player");
            client.options.keyShift.setDown(false); return null;
        });
        onClient(() -> { fast.setDown(true); nativeCall(client.mouseHandler, "onScroll", client.getWindow().handle(), 0.0, -1.0); fast.setDown(false); return null; });
        check(onClient(editing::draft).size(0) == 1, "Fast step crossed opposite face");
        release(); await(() -> !editing.pending(), "extending commit");
        onServer(() -> {
            var actor = actor(); var selector = WorldEdit.getInstance().getSessionManager().get(actor).getRegionSelector(actor.getWorld());
            check(selector.getClass() == ExtendingCuboidRegionSelector.class, "Extending selector was replaced"); return null;
        });
        select(false); aim(Face.EAST, true); start(Face.EAST);
        onClient(() -> { call(overlay, "setIsolatedBlockIds", Set.of("minecraft:stone")); return null; });
        onClient(() -> { nativeCall(client.mouseHandler, "onButton", client.getWindow().handle(), new MouseButtonInfo(0, 0), 1); return null; });
        check(onClient(editing::dragging), "Handle did not start drag");
        onClient(() -> {
            check(!client.mouseHandler.isMouseGrabbed(), "Cursor remained captured during drag");
            nativeCall(client.mouseHandler, "onMove", client.getWindow().handle(), client.getWindow().getScreenWidth() / 2.0 + 80,
                    client.getWindow().getScreenHeight() / 2.0 + 80);
            check(!client.options.keyAttack.isDown(), "Drag leaked into attack"); return null;
        });
        CuboidDraft dragged = onClient(editing::draft);
        check(!dragged.equals(original), "Drag did not change the face");
        check(serverBounds().equals(original), "Drag modified server before release");
        onClient(() -> { client.gui.hud.getChat().clearMessages(false); return null; });
        pause(300);
        screenshot.accept("selection-edit-drag");
        onClient(() -> { nativeCall(client.mouseHandler, "onButton", client.getWindow().handle(), new MouseButtonInfo(0, 0), 0); return null; });
        await(() -> !editing.pending(), "drag commit");
        check(serverBounds().equals(dragged), "Drag release did not commit");
        await(() -> !editing.waitingForCui(), "CUI acknowledgement");
        check(onClient(() -> ((Set<?>) field(overlay, "isolatedBlockIds")).equals(Set.of("minecraft:stone"))), "Resize cleared isolation types");
        check(onClient(() -> call(call(clipboardSource, "snapshot"), "blocks")) == cachedClipboard, "Editing discarded clipboard geometry cache");
        check(onClient(() -> (boolean) call(overlay, "clipboardPreviewEnabled")), "Editing changed clipboard preference");
        release(); onClient(() -> { call(overlay, "setIsolatedBlockIds", Set.of()); return null; });

        // Native Escape must cancel the draft and latch until the held key is released.
        select(false); aim(Face.SOUTH, false); start(Face.SOUTH);
        onClient(() -> {
            nativeCall(client.mouseHandler, "onScroll", client.getWindow().handle(), 0.0, 3.0);
            nativeCall(client.keyboardHandler, "keyPress", client.getWindow().handle(), 1, new KeyEvent(256, 0, 0));
            check(!editing.active() && client.gui.screen() == null, "Escape opened pause menu or retained draft"); return null;
        });
        pause(300); check(!onClient(editing::active), "Cancelled hold re-entered without release");
        release(); check(serverBounds().equals(original), "Escape committed a draft");

        select(false); aim(Face.EAST, true); start(Face.EAST);
        onClient(() -> { nativeCall(client.mouseHandler, "onButton", client.getWindow().handle(), new MouseButtonInfo(0, 0), 1); return null; });
        check(onClient(editing::dragging), "Cancel probe could not drag");
        release(); await(() -> !editing.active(), "drag cancellation");
        onClient(() -> {
            nativeCall(client.mouseHandler, "onButton", client.getWindow().handle(), new MouseButtonInfo(0, 0), 0);
            check(!client.options.keyAttack.isDown(), "Cancelled drag attacked on exit");
            check(client.mouseHandler.isMouseGrabbed(), "Cancelled drag did not restore cursor"); return null;
        });
        check(serverBounds().equals(original), "Releasing hold during drag committed");

        select(false); aim(Face.UP, false); start(Face.UP);
        onClient(() -> { nativeCall(client.mouseHandler, "onScroll", client.getWindow().handle(), 0.0, 1.0); return null; });
        select(false); // New selector, even with identical coordinates, invalidates the token.
        release(); await(() -> !editing.pending(), "stale rejection");
        check(serverBounds().equals(original), "Stale edit overwrote new selection");

        select(false); aim(Face.NORTH, false); start(Face.NORTH);
        onClient(() -> {
            nativeCall(client.mouseHandler, "onScroll", client.getWindow().handle(), 0.0, 2.0);
            client.gui.setScreen(new net.minecraft.client.gui.screens.ChatScreen("", false)); return null;
        });
        pause(150); check(!onClient(editing::active), "Chat retained editing controls");
        release(); onClient(() -> { client.gui.setScreen(null); return null; });
        check(serverBounds().equals(original), "Chat committed an unfinished edit");

        onServer(() -> {
            var actor = actor(); var first = SelectionEditTransaction.read(actor);
            var session = WorldEdit.getInstance().getSessionManager().get(actor);
            var region = (CuboidRegion) session.getSelection(actor.getWorld());
            BlockVector3 p1 = region.getPos1(), p2 = region.getPos2();
            var result = SelectionEditTransaction.commit(actor, first, first.bounds().resize(Face.WEST, 2, first.minY(), first.maxY()), Face.WEST);
            check(result.ready(), "Native transaction failed");
            check(region.getPos1().equals(p1), "Reversed Pos1 lost identity");
            check(region.getPos2().equals(p2.add(-2, 0, 0)), "Reversed Pos2 lost identity"); return null;
        });
        for (Corner corner : Corner.values()) {
            select(corner.ordinal() % 2 == 0);
            List<BlockVector3> ordered = onServer(() -> {
                var region = (CuboidRegion) WorldEdit.getInstance().getSessionManager().get(actor()).getSelection(actor().getWorld());
                return List.of(region.getPos1(), region.getPos2());
            });
            Point position = original.corner(corner);
            Point eye = position.add(new Point(9 * corner.face(0).sign, 7 * corner.face(1).sign, 11 * corner.face(2).sign));
            aimAt(eye, position);
            await(() -> editing.visible() && !editing.active(), "passive corner " + corner);
            onClient(() -> { nativeCall(client.mouseHandler, "onButton", client.getWindow().handle(), new MouseButtonInfo(0, 0), 1); return null; });
            check(onClient(editing::dragging), "Direct corner did not drag: " + corner);
            onClient(() -> {
                check(!hold.isDown(), "Direct corner needed hold key");
                nativeCall(client.mouseHandler, "onMove", client.getWindow().handle(), client.getWindow().getScreenWidth() / 2.0 + 80,
                        client.getWindow().getScreenHeight() / 2.0 - 60);
                return null;
            });
            CuboidDraft cornerDraft = onClient(editing::draft);
            check(!cornerDraft.equals(original), "Corner drag did not change bounds: " + corner);
            check(cornerDraft.corner(corner.opposite()).equals(original.corner(corner.opposite())), "Corner moved opposite point");
            check(serverBounds().equals(original), "Corner drag committed early");
            if (corner == Corner.EAST_UP_SOUTH) {
                onClient(() -> { client.gui.hud.getChat().clearMessages(false); return null; });
                pause(200); screenshot.accept("selection-edit-corner");
            }
            onClient(() -> { nativeCall(client.mouseHandler, "onButton", client.getWindow().handle(), new MouseButtonInfo(0, 0), 0); return null; });
            await(() -> !editing.pending(), "corner commit " + corner);
            check(serverBounds().equals(cornerDraft), "Corner commit failed: " + corner);
            onServer(() -> {
                var selector = WorldEdit.getInstance().getSessionManager().get(actor()).getRegionSelector(actor().getWorld());
                check(selector.getClass() == (corner.ordinal() % 2 == 0 ? ExtendingCuboidRegionSelector.class : CuboidRegionSelector.class), "Corner replaced selector type");
                var region = (CuboidRegion) selector.getRegion();
                check(region.getPos1().equals(movedPoint(ordered.get(0), cornerDraft)), "Corner changed Pos1 ordering: " + corner);
                check(region.getPos2().equals(movedPoint(ordered.get(1), cornerDraft)), "Corner changed Pos2 ordering: " + corner);
                return null;
            });
        }
        select(false); aim(Face.EAST, false);
        await(editing::visible, "passive markers");
        onClient(() -> {
            check(!editing.button(0, 1), "Passive markers captured an ordinary face click");
            check(!editing.scroll(1), "Passive markers captured ordinary wheel input");
            client.options.keyShift.setDown(true); client.player.input.tick();
            check(client.player.input.keyPresses.shift(), "Normal Shift was disabled outside editing");
            client.options.keyShift.setDown(false); return null;
        });
        onClient(() -> { client.gui.hud.getChat().clearMessages(false); return null; });
        pause(200); screenshot.accept("selection-edit-passive");
        Point cornerPosition = original.corner(Corner.EAST_UP_SOUTH);
        aimAt(cornerPosition.add(new Point(9, 7, 11)), cornerPosition);
        onClient(() -> {
            nativeCall(client.mouseHandler, "onButton", client.getWindow().handle(), new MouseButtonInfo(0, 0), 1);
            check(editing.dragging(), "Corner cancel probe failed");
            nativeCall(client.keyboardHandler, "keyPress", client.getWindow().handle(), 1, new KeyEvent(256, 0, 0));
            nativeCall(client.mouseHandler, "onButton", client.getWindow().handle(), new MouseButtonInfo(0, 0), 0);
            check(!editing.active() && client.gui.screen() == null && !client.options.keyAttack.isDown(), "Corner Escape leaked input");
            return null;
        });
        check(serverBounds().equals(original), "Cancelled corner committed");
        select(false); aim(Face.DOWN, false); start(Face.DOWN);
        onClient(() -> {
            check(field(field(editing, "source"), "last") instanceof java.lang.ref.WeakReference<?>, "Source cache strongly retains the world");
            hold.setDown(false);
            setField(editing, "pending", true);
            setField(editing, "level", new Object());
            call(editing, "tick", client);
            check(!editing.pending() && !editing.active() && editing.draft() == null && field(editing, "origin") == null && field(editing, "shown") == null,
                    "World identity change retained a pending context");
            return null;
        });
        check(onServer(this::history).equals(historyBefore), "Selection editing changed WorldEdit history");
        check(onServer(this::worldBlocks).equals(blocksBefore), "Selection editing changed world blocks");
        for (String language : new String[]{"en_us", "ru_ru"}) {
            var reload = onClient(() -> { client.getLanguageManager().setSelected(language); return client.reloadResourcePacks(); });
            await(reload::isDone, "language reload");
            await(() -> client.gui.overlay() == null, "language overlay");
            onClient(() -> { client.gui.hud.getChat().clearMessages(false); return null; });
        for (int scale : new int[]{2, 3, 4}) {
            onClient(() -> {
                client.options.guiScale().set(scale); client.resizeGui();
                client.gui.setScreen((net.minecraft.client.gui.screens.Screen) Class.forName(
                        "org.marshal.weaddon_lightposition.runtime.mc26_2.client.screen.SelectionEditSettingsScreen").getConstructors()[0].newInstance(new Object[]{null}));
                return null;
            });
            pause(250);
            screenshot.accept("selection-edit-settings-" + language + "-x" + scale);
            onClient(() -> {
                var screen = client.gui.screen();
                int w = (int) call(screen, "panelWidth"), h = (int) call(screen, "panelHeight"), y = (int) call(screen, "panelY");
                int x = (client.getWindow().getGuiScaledWidth() - w) / 2;
                for (var child : screen.children()) if (child instanceof net.minecraft.client.gui.components.AbstractWidget widget) {
                    int wx = widget.getX(), wy = widget.getY();
                    int ww = widget.getWidth(), wh = widget.getHeight();
                    check(wx >= x + 8 && wx + ww <= x + w - 8 && wy >= y && wy + wh <= y + h - 8, "Settings escaped panel at GUI scale " + scale);
                }
                return null;
            });
            onClient(() -> {
                client.gui.setScreen(new net.minecraft.client.gui.screens.options.controls.KeyBindsScreen(null, client.options));
                for (var child : client.gui.screen().children()) if (child instanceof net.minecraft.client.gui.components.AbstractSelectionList<?> list) {
                    int index = 0;
                    for (Object entry : (List<?>) list.children()) {
                        boolean ours = false;
                        for (var field : entry.getClass().getDeclaredFields()) if (KeyMapping.class.isAssignableFrom(field.getType())) {
                            field.setAccessible(true); if (field.get(entry) == hold || field.get(entry) == fast) ours = true;
                        }
                        if (ours) { call(list, "setScrollAmount", ((Number) call(list, "getRowTop", index)).doubleValue() - list.getY()); break; }
                        index++;
                    }
                }
                check(client.font.width(net.minecraft.network.chat.Component.translatable("key.worldedit_selection_overlay.selection_edit")) <= 110, "Face key label too wide");
                check(client.font.width(net.minecraft.network.chat.Component.translatable("key.worldedit_selection_overlay.selection_edit_fast")) <= 110, "Fast key label too wide");
                return null;
            });
            pause(250); screenshot.accept("selection-edit-controls-" + language + "-x" + scale);
        }
        }
        var reload = onClient(() -> { client.getLanguageManager().setSelected("en_us"); return client.reloadResourcePacks(); });
        await(reload::isDone, "restore language");
        await(() -> client.gui.overlay() == null, "restore overlay");
        onClient(() -> { client.gui.setScreen(null); client.options.guiScale().set(2); client.resizeGui(); editing.configure(false, 1, 8); return null; });
        System.out.println("SELECTION EDIT VERIFY PASS: six clicked/remembered faces, eight direct corners, flight Shift, drag, cancel, stale, extending, isolation, clipboard, unchanged blocks/history");
    }
    private void select(boolean extending) {
        onServer(() -> {
            var actor = actor(); var session = WorldEdit.getInstance().getSessionManager().get(actor);
            var selector = extending ? new ExtendingCuboidRegionSelector(actor.getWorld()) : new CuboidRegionSelector(actor.getWorld());
            selector.selectPrimary(BlockVector3.at(original.maxX(), original.maxY(), original.maxZ()), com.sk89q.worldedit.extension.platform.permission.ActorSelectorLimits.forActor(actor));
            selector.selectSecondary(BlockVector3.at(original.minX(), original.minY(), original.minZ()), com.sk89q.worldedit.extension.platform.permission.ActorSelectorLimits.forActor(actor));
            session.setRegionSelector(actor.getWorld(), selector); session.dispatchCUISelection(actor); return null;
        }); pause(250);
    }
    private void aim(Face f, boolean angled) {
        aim(f, angled, false);
        lastAimAngled = angled;
    }
    private void aim(Face f, boolean angled, boolean offCenter) {
        var center = original.center(f);
        if (offCenter) center = center.add(new Point(f.axis == 2 ? 1.5 : 0, f.axis == 0 ? 1.5 : 0, f.axis == 1 ? 1.5 : 0));
        double[] eye = {center.x(), center.y(), center.z()}; eye[f.axis] += 9 * f.sign;
        if (angled) eye[(f.axis + 1) % 3] += 5;
        aimAt(new Point(eye[0], eye[1], eye[2]), center);
    }
    private void aimAt(Point eye, Point center) {
        double dx = center.x() - eye.x(), dy = center.y() - eye.y(), dz = center.z() - eye.z();
        double yaw = Math.toDegrees(Math.atan2(-dx, dz)), pitch = -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
        onServer(() -> { var server = client.getSingleplayerServer(); server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
            "tp @p " + eye.x() + " " + (eye.y() - 1.62) + " " + eye.z() + " " + yaw + " " + pitch); return null; }); pause(500);
    }
    private void start(Face face) {
        Face previous = onClient(editing::target);
        boolean angled = lastAimAngled;
        aim(face, false, true);
        onClient(() -> { org.lwjgl.glfw.GLFW.glfwFocusWindow(client.getWindow().handle()); hold.setDown(true); return null; });
        await(() -> editing.active() && editing.draft() != null, "enter editing " + face);
        onClient(() -> {
            check(editing.target() == previous, "Looking at a new face changed remembered choice");
            nativeCall(client.mouseHandler, "onButton", client.getWindow().handle(), new MouseButtonInfo(0, 0), 1);
            check(!editing.dragging(), "Whole face click unexpectedly dragged handle");
            nativeCall(client.mouseHandler, "onButton", client.getWindow().handle(), new MouseButtonInfo(0, 0), 0);
            check(editing.target() == face, "Distance face click did not select " + face);
            return null;
        });
        aim(face, angled);
    }
    private void release() { onClient(() -> { hold.setDown(false); return null; }); pause(150); }
    private com.sk89q.worldedit.entity.Player actor() { return FabricAdapter.get().fromNativePlayer(client.getSingleplayerServer().getPlayerList().getPlayers().getFirst()); }
    private List<?> history() {
        return List.copyOf((java.util.Collection<?>) field(WorldEdit.getInstance().getSessionManager().get(actor()), "history"));
    }
    private List<?> worldBlocks() {
        var world = actor().getWorld();
        var blocks = new java.util.ArrayList<com.sk89q.worldedit.world.block.BaseBlock>();
        for (int x = -10; x <= -1; x++) for (int y = 64; y <= 73; y++) for (int z = -10; z <= -1; z++) {
            blocks.add(world.getFullBlock(BlockVector3.at(x, y, z)));
        }
        return List.copyOf(blocks);
    }
    private CuboidDraft serverBounds() { return onServer(() -> SelectionEditTransaction.read(actor()).bounds()); }
    private BlockVector3 movedPoint(BlockVector3 point, CuboidDraft draft) {
        return BlockVector3.at(point.x() == original.maxX() ? draft.maxX() : draft.minX(),
                point.y() == original.maxY() ? draft.maxY() : draft.minY(), point.z() == original.maxZ() ? draft.maxZ() : draft.minZ());
    }
    private <T> T onClient(Callable<T> action) { return dispatch.client(action); }
    private <T> T onServer(Callable<T> action) { return dispatch.server(action); }
    private static <T> T checked(Callable<T> action) { try { return action.call(); } catch (Exception ex) { throw new AssertionError(ex); } }
    private void await(BooleanSupplier test, String label) {
        long deadline = System.nanoTime() + 10_000_000_000L;
        while (!onClient(test::getAsBoolean)) { if (System.nanoTime() > deadline) throw new AssertionError("Selection editing timeout: " + label + " reason=" + onClient(editing::reason)); pause(25); }
    }
    private void pause(long millis) { dispatch.pause(millis); }
    private static Object field(Object target, String name) {
        try { var field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target); }
        catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
    }
    private static void setField(Object target, String name, Object value) {
        try { var field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target, value); }
        catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
    }
    private static Object call(Object target, String name, Object... args) {
        try { for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass())
            for (var method : type.getDeclaredMethods()) if (method.getName().equals(name) && method.getParameterCount() == args.length) {
                method.setAccessible(true); return method.invoke(target, args);
            }
            throw new AssertionError("Missing method " + name); } catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
    }
    private static void nativeCall(Object target, String name, Object... args) {
        try { for (var method : target.getClass().getDeclaredMethods()) if (method.getName().equals(name) && method.getParameterCount() == args.length) {
            method.setAccessible(true); method.invoke(target, args); return;
        } throw new AssertionError("Missing native method " + name); } catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
