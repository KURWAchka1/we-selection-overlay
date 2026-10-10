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
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.GizmoCollector;
import net.minecraft.gizmos.RectGizmo;
import org.marshal.weaddon_lightposition.runtime.mc26_2.client.selection.SelectionEditController;
import org.marshal.weaddon_lightposition.runtime.mc26_2.client.overlay.OverlayColorProfile;
import org.marshal.weaddon_lightposition.runtime.mc26_2.client.overlay.OverlayColorSlot;
import org.marshal.weaddon_lightposition.runtime.mc26_2.client.overlay.OverlayRenderStyle;
import org.marshal.weaddon_lightposition.runtime.mc26_2.client.overlay.SelectionEditStyle;
import org.marshal.weaddon_lightposition.runtime.mc26_2.client.screen.UiStyle;
import org.marshal.weaddon_lightposition.runtime.mc26_2.selection.CuboidDraft;
import org.marshal.weaddon_lightposition.runtime.mc26_2.selection.CuboidDraft.Face;
import org.marshal.weaddon_lightposition.runtime.mc26_2.selection.CuboidDraft.Corner;
import org.marshal.weaddon_lightposition.runtime.mc26_2.selection.CuboidDraft.Point;
import org.marshal.weaddon_lightposition.runtime.mc26_2.selection.SelectionEditMode;
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
        try { new SelectionEditingChecks(client, overlay, clipboardSource, screenshot, dispatch).run(); }
        catch (RuntimeException | Error failure) {
            // Report before the game-test world's shutdown can mask an assertion with a barrier wait.
            failure.printStackTrace();
            throw failure;
        }
    }
    private void run() {
        onClient(() -> { prepareLogicalFocus(); checkSystemCursorFree(); return null; });
        List<?> historyBefore = onServer(this::history);
        List<?> blocksBefore = onServer(this::worldBlocks);
        onClient(() -> {
            check(!editing.options().enabled(), "Editing must be opt-in");
            checkCrosshair(false, "disabled editing");
            hold = (KeyMapping) field(editing, "editKey"); fast = (KeyMapping) field(editing, "fastKey");
            check(hold.getDefaultKey().getValue() == 71, "Default editing key must be G");
            call(editing, "bind", false, new KeyEvent(71, 0, 0));
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
            onClient(() -> { checkCrosshair(false, "G apply " + f); return null; });
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
            check(editing.cursorDrag(), "Held face drag lost precise cursor mode");
            client.options.keyRight.setDown(true); client.player.input.tick();
            check(!client.player.input.keyPresses.right(), "Precise drag retained movement input");
            client.options.keyRight.setDown(false);
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
        pause(150);
        check(onClient(() -> editing.active() && !editing.dragging() && editing.capturesCamera() && !client.mouseHandler.isMouseGrabbed()),
                "Held LMB release applied the draft or unlocked the camera");
        check(serverBounds().equals(original), "Held LMB release committed before G release");
        cursorAt(dragged.corner(Corner.WEST_UP_NORTH));
        onClient(() -> {
            nativeCall(client.mouseHandler, "onButton", client.getWindow().handle(), new MouseButtonInfo(0, 0), 1);
            check(editing.dragging() && field(editing, "lockedCorner") == Corner.WEST_UP_NORTH, "Second held gesture did not grab the corner");
            check(editing.draft().equals(dragged), "Second held gesture jumped or discarded the first change");
            nativeCall(client.mouseHandler, "onMove", client.getWindow().handle(), client.mouseHandler.xpos() + 80, client.mouseHandler.ypos() - 40);
            return null;
        });
        CuboidDraft composed = onClient(editing::draft);
        check(!composed.equals(dragged) && composed.maxX() == dragged.maxX(), "Second corner gesture discarded the edited opposite face");
        onClient(() -> { nativeCall(client.mouseHandler, "onButton", client.getWindow().handle(), new MouseButtonInfo(0, 0), 0); return null; });
        check(serverBounds().equals(original), "Second LMB release committed early");
        release();
        await(() -> !editing.pending(), "drag commit");
        check(serverBounds().equals(composed), "G release did not commit all changed faces");
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
            check(!editing.active() && client.gui.screen() == null, "Escape opened pause menu or retained draft");
            checkCrosshair(false, "Escape"); return null;
        });
        pause(300); check(!onClient(editing::active), "Cancelled hold re-entered without release");
        release(); check(serverBounds().equals(original), "Escape committed a draft");

        select(false); aim(Face.EAST, true); start(Face.EAST);
        onClient(() -> { nativeCall(client.mouseHandler, "onButton", client.getWindow().handle(), new MouseButtonInfo(0, 0), 1); return null; });
        check(onClient(editing::dragging), "Cancel probe could not drag");
        onClient(() -> { nativeCall(client.keyboardHandler, "keyPress", client.getWindow().handle(), 1, new KeyEvent(256, 0, 0)); return null; });
        release(); await(() -> !editing.active(), "drag cancellation");
        onClient(() -> {
            nativeCall(client.mouseHandler, "onButton", client.getWindow().handle(), new MouseButtonInfo(0, 0), 0);
            check(!client.options.keyAttack.isDown(), "Cancelled drag attacked on exit");
            check(client.mouseHandler.isMouseGrabbed(), "Cancelled drag did not restore cursor"); return null;
        });
        check(serverBounds().equals(original), "Escape during held drag committed");

        select(false); aim(Face.EAST, true); start(Face.EAST);
        onClient(() -> {
            nativeCall(client.mouseHandler, "onButton", client.getWindow().handle(), new MouseButtonInfo(0, 0), 1);
            nativeCall(client.mouseHandler, "onMove", client.getWindow().handle(), client.mouseHandler.xpos() + 70, client.mouseHandler.ypos() + 50);
            return null;
        });
        CuboidDraft keyReleasedDraft = onClient(editing::draft);
        check(!keyReleasedDraft.equals(original), "G release probe did not move the face");
        release(); await(() -> !editing.pending(), "G release with mouse held");
        check(serverBounds().equals(keyReleasedDraft), "G release while LMB held lost the final draft");
        onClient(() -> {
            nativeCall(client.mouseHandler, "onButton", client.getWindow().handle(), new MouseButtonInfo(0, 0), 0);
            check(client.mouseHandler.isMouseGrabbed() && !client.options.keyAttack.isDown(), "G release leaked attack or failed cursor restoration"); return null;
        });

        select(false); aim(Face.UP, false); start(Face.UP);
        onClient(() -> { nativeCall(client.mouseHandler, "onScroll", client.getWindow().handle(), 0.0, 1.0); return null; });
        select(false); // New selector, even with identical coordinates, invalidates the token.
        release(); await(() -> !editing.pending(), "stale rejection");
        check(serverBounds().equals(original), "Stale edit overwrote new selection");

        select(false); aim(Face.NORTH, false); start(Face.NORTH);
        onClient(() -> {
            nativeCall(client.mouseHandler, "onScroll", client.getWindow().handle(), 0.0, 2.0);
            client.gui.setScreen(new net.minecraft.client.gui.screens.ChatScreen("", false));
            checkCrosshair(false, "chat before next editing tick"); return null;
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
        select(false);
        Point preciseCorner = original.corner(Corner.EAST_UP_SOUTH);
        aimAt(preciseCorner.add(new Point(9, 7, 11)), preciseCorner);
        tap();
        await(() -> editing.active() && editing.draft() != null, "precise corner entry");
        cursorAt(preciseCorner);
        onClient(() -> {
            nativeCall(client.mouseHandler, "onButton", client.getWindow().handle(), new MouseButtonInfo(0, 0), 1);
            check(editing.cursorDrag() && !client.mouseHandler.isMouseGrabbed(), "Held corner did not preserve precise cursor dragging");
            editing.configure(true, 4, 8);
            checkCornerStep(Corner.EAST_UP_SOUTH, 5.2, 4);
            fast.setDown(true);
            checkCornerStep(Corner.EAST_UP_SOUTH, 5.2, 8);
            fast.setDown(false);
            call(editing, "updateCorner", field(editing, "cornerDragStart"));
            editing.configure(true, 1, 8);
            nativeCall(client.mouseHandler, "onMove", client.getWindow().handle(), client.getWindow().getScreenWidth() / 2.0 + 80,
                    client.getWindow().getScreenHeight() / 2.0 - 60);
            check(!editing.draft().equals(original), "Precise corner cursor did not change bounds");
            nativeCall(client.keyboardHandler, "keyPress", client.getWindow().handle(), 1, new KeyEvent(256, 0, 0));
            nativeCall(client.mouseHandler, "onButton", client.getWindow().handle(), new MouseButtonInfo(0, 0), 0);
            check(client.mouseHandler.isMouseGrabbed(), "Precise corner cancel did not restore cursor");
            return null;
        });
        release(); check(serverBounds().equals(original), "Precise corner cancellation committed");
        onClient(() -> { editing.configure(true, 4, 8); return null; });
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
            onClient(() -> { checkCrosshair(true, "free corner " + corner); return null; });
            double beforeYaw = onClient(() -> client.player.getYRot());
            onClient(() -> {
                check(!hold.isDown(), "Direct corner needed hold key");
                check(!editing.cursorDrag() && client.mouseHandler.isMouseGrabbed(), "Direct corner locked camera or released cursor");
                check(editing.draft().equals(original), "Direct corner jumped on initial grab");
                checkCornerStep(corner, 2.2, 1);
                fast.setDown(true);
                checkCornerStep(corner, 2.2, 1);
                hold.setDown(true);
                checkCornerStep(corner, 2.2, 1);
                hold.setDown(false); fast.setDown(false);
                call(editing, "updateCorner", field(editing, "cornerDragStart"));
                check(editing.draft().equals(original), "Step probe did not restore the corner draft");
                // Seed native's first-move guard, then send a relative delta instead of cancelling two absolute moves.
                double x = client.mouseHandler.xpos(), y = client.mouseHandler.ypos();
                nativeCall(client.mouseHandler, "onMove", client.getWindow().handle(), x, y);
                nativeCall(client.mouseHandler, "onMove", client.getWindow().handle(), x + 80, y - 60);
                nativeCall(client.mouseHandler, "handleAccumulatedMovement");
                return null;
            });
            pause(250);
            check(Math.abs(onClient(() -> client.player.getYRot()) - beforeYaw) > 0.05, "Direct corner mouse did not turn camera");
            Point beforeFlight = onClient(() -> new Point(client.player.getX(), client.player.getY(), client.player.getZ()));
            onClient(() -> {
                client.options.keyRight.setDown(true); client.options.keyJump.setDown(true); client.player.input.tick();
                check(client.player.input.keyPresses.right() && client.player.input.keyPresses.jump(), "Direct corner suppressed flight controls");
                return null;
            });
            pause(300);
            onClient(() -> { client.options.keyRight.setDown(false); client.options.keyJump.setDown(false); return null; });
            pause(150);
            Point afterFlight = onClient(() -> new Point(client.player.getX(), client.player.getY(), client.player.getZ()));
            check(afterFlight.subtract(beforeFlight).length() > 0.1 && afterFlight.y() > beforeFlight.y() + 0.05,
                    "Direct corner froze movement or upward flight");
            if (corner == Corner.EAST_UP_SOUTH) {
                double height = onClient(() -> client.player.getY());
                onClient(() -> { fast.setDown(true); client.options.keyShift.setDown(true); hold.setDown(true); return null; });
                pause(500);
                onClient(() -> {
                    check(!editing.cursorDrag() && client.mouseHandler.isMouseGrabbed(), "Pressing hold key during free drag locked camera");
                    check(client.player.input.keyPresses.shift(), "Free corner suppressed normal Shift input");
                    check(client.player.getY() < height - 0.05,
                            "Free corner Shift did not descend: before=" + height + " after=" + client.player.getY());
                    fast.setDown(false); client.options.keyShift.setDown(false); hold.setDown(false); return null;
                });
                pause(100);
            }
            CuboidDraft cornerDraft = onClient(editing::draft);
            check(!cornerDraft.equals(original), "Corner drag did not change bounds: " + corner);
            check(cornerDraft.corner(corner.opposite()).equals(original.corner(corner.opposite())), "Corner moved opposite point");
            check(serverBounds().equals(original), "Corner drag committed early");
            checkCornerOutline(corner);
            if (corner == Corner.EAST_UP_SOUTH) {
                onClient(() -> { client.gui.hud.getChat().clearMessages(false); return null; });
                pause(200); screenshot.accept("selection-edit-corner");
                contrastingCornerOutline(corner);
            }
            CuboidDraft releasedDraft = onClient(() -> {
                nativeCall(client.mouseHandler, "onButton", client.getWindow().handle(), new MouseButtonInfo(0, 0), 0);
                checkCrosshair(false, "free corner mouse release");
                return editing.draft();
            });
            await(() -> !editing.pending(), "corner commit " + corner);
            check(releasedDraft != null && serverBounds().equals(releasedDraft), "Corner commit failed: " + corner
                    + " expected=" + releasedDraft + " actual=" + serverBounds());
            onServer(() -> {
                var selector = WorldEdit.getInstance().getSessionManager().get(actor()).getRegionSelector(actor().getWorld());
                check(selector.getClass() == (corner.ordinal() % 2 == 0 ? ExtendingCuboidRegionSelector.class : CuboidRegionSelector.class), "Corner replaced selector type");
                var region = (CuboidRegion) selector.getRegion();
                check(region.getPos1().equals(movedPoint(ordered.get(0), releasedDraft)), "Corner changed Pos1 ordering: " + corner);
                check(region.getPos2().equals(movedPoint(ordered.get(1), releasedDraft)), "Corner changed Pos2 ordering: " + corner);
                return null;
            });
        }
        onClient(() -> { editing.configure(true, 1, 8); return null; });
        select(false); aim(Face.EAST, false);
        await(editing::visible, "passive markers");
        onClient(() -> {
            check(!editing.button(0, 1), "Passive markers captured an ordinary face click");
            checkCrosshair(false, "passive markers");
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
            checkCrosshair(false, "world identity mismatch before tick");
            call(editing, "tick", client);
            check(!editing.pending() && !editing.active() && editing.draft() == null && field(editing, "origin") == null && field(editing, "shown") == null,
                    "World identity change retained a pending context");
            return null;
        });
        closeFacesAndModes();
        crosshairLifecycle();
        check(onServer(this::history).equals(historyBefore), "Selection editing changed WorldEdit history");
        check(onServer(this::worldBlocks).equals(blocksBefore), "Selection editing changed world blocks");
        paletteHistory();
        editingPaletteAndClassic();
        for (String language : new String[]{"en_us", "ru_ru"}) {
            var reload = onClient(() -> { client.getLanguageManager().setSelected(language); return client.reloadResourcePacks(); });
            await(reload::isDone, "language reload");
            await(() -> client.gui.overlay() == null, "language overlay");
            onClient(() -> { client.gui.hud.getChat().clearMessages(false); client.gui.setScreen(null); return null; });
            if (language.equals("ru_ru")) chooseMode(SelectionEditMode.RESIZE, true);
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
        onClient(() -> { checkSystemCursorFree(); return null; });
        System.out.println("BACKGROUND INPUT VERIFY PASS: simulated game focus and mouse callbacks; real GLFW cursor remains NORMAL");
        System.out.println("SELECTION EDIT VERIFY PASS: G tap toggle, close/inside six-face dragging, hidden-cursor radial/dead zone, independent XYZ wheel/flight/compass, LMB retains draft, eight free-camera one-block corners, crosshair lifecycle, Escape, stale, isolation, clipboard, unchanged blocks/history");
    }
    private void closeFacesAndModes() {
        for (Face face : Face.values()) for (int side : new int[]{1, -1}) {
            select(false);
            Point normal = new Point(face.axis == 0 ? face.sign : 0, face.axis == 1 ? face.sign : 0, face.axis == 2 ? face.sign : 0);
            int tangentAxis = (face.axis + 1) % 3;
            Point anchor = original.center(face).add(new Point(tangentAxis == 0 ? 2 : 0, tangentAxis == 1 ? 2 : 0, tangentAxis == 2 ? 2 : 0));
            aimAt(anchor.add(normal.scale(side * 0.2)), anchor);
            tap(); await(() -> editing.active() && editing.draft() != null, "close face entry");
            onClient(() -> {
                double x = client.getWindow().getScreenWidth() / 2.0, y = client.getWindow().getScreenHeight() / 2.0;
                nativeCall(client.mouseHandler, "onMove", client.getWindow().handle(), x, y);
                nativeCall(client.mouseHandler, "onButton", client.getWindow().handle(), new MouseButtonInfo(0, 0), 1);
                check(editing.target() == face && field(editing, "lockedCorner") == null, "Close face did not select " + face + " side=" + side);
                check(editing.draft().equals(original) && !editing.dragging(), "Surface click moved before threshold");
                nativeCall(client.mouseHandler, "onMove", client.getWindow().handle(), x + 2, y);
                check(!editing.dragging() && editing.draft().equals(original), "Subthreshold jitter dragged the face");
                nativeCall(client.mouseHandler, "onMove", client.getWindow().handle(), x, y - 96);
                check(editing.dragging() && editing.draft().equals(original.resize(face, 2, -64, 319)), "Close/end-on drag failed " + face + " side=" + side + " actual=" + editing.draft());
                nativeCall(client.mouseHandler, "onMove", client.getWindow().handle(), x, y + 96);
                check(editing.draft().equals(original.resize(face, -2, -64, 319)), "Close drag changed direction or another axis");
                nativeCall(client.mouseHandler, "onMove", client.getWindow().handle(), x, y - 96);
                nativeCall(client.mouseHandler, "onButton", client.getWindow().handle(), new MouseButtonInfo(0, 0), 0);
                check(editing.active() && !client.mouseHandler.isMouseGrabbed(), "Close surface release applied early");
                return null;
            });
            check(serverBounds().equals(original), "Close drag changed server before toggle");
            if (face == Face.EAST && side == 1) { pause(200); screenshot.accept("selection-edit-close-face"); }
            release(); await(() -> !editing.pending(), "close face commit");
            check(serverBounds().equals(original.resize(face, 2, -64, 319)), "Close face commit failed");
        }
        select(false); aim(Face.SOUTH, false);
        onClient(() -> { hold.setDown(true); return null; }); pause(200);
        check(!onClient(editing::radialOpen) && !onClient(editing::active), "Short hold opened menu or entered editor");
        await(editing::radialOpen, "radial hold");
        onClient(() -> {
            checkSystemCursorFree();
            check(editing.capturesCamera(), "Radial did not freeze camera");
            checkCrosshair(true, "idle radial");
            radialPointer(1000, 0);
            check(Math.hypot((double) field(editing, "radialX"), (double) field(editing, "radialY")) <= (int) call(editing, "menuRadius", client),
                    "Radial pointer escaped the ring");
            double edge = (double) field(editing, "radialX");
            radialPointer(edge - 1, 0);
            check((double) field(editing, "radialX") < edge, "Pointer needed to backtrack after reaching the edge");
            radialPointer(0, 0);
            hold.setDown(false); return null;
        }); pause(150);
        check(!onClient(editing::active) && onClient(editing::mode) == SelectionEditMode.RESIZE, "Center choice applied or changed mode");
        onClient(() -> { checkCrosshair(false, "idle radial center cancellation"); return null; });
        check(onClient(() -> client.mouseHandler.isMouseGrabbed()), "Radial center failed to restore cursor");
        chooseMode(SelectionEditMode.MOVE, true);
        check(onClient(editing::mode) == SelectionEditMode.MOVE && !onClient(editing::active), "Radial selection unexpectedly applied or activated");
        for (double distance : new double[]{0.2, -0.2, 256}) {
            select(false);
            Point anchor = original.center(Face.EAST).add(new Point(0, 2, 2));
            aimAt(anchor.add(new Point(distance, 0, 0)), anchor);
            tap(); await(() -> editing.active() && editing.draft() != null, "near/inside/far carry");
            onClient(() -> {
                nativeCall(client.mouseHandler, "onButton", client.getWindow().handle(), new MouseButtonInfo(0, 0), 1);
                check(editing.dragging() && editing.draft().equals(original), "Near/inside/far pickup missed or jumped: " + distance);
                nativeCall(client.mouseHandler, "onScroll", client.getWindow().handle(), 0.0, 1.0);
                return null;
            });
            CuboidDraft nudge = onClient(editing::draft);
            check(!nudge.equals(original), "Near/inside/far carry did not nudge");
            release(); await(() -> !editing.pending(), "apply carry with LMB held");
            onClient(() -> {
                nativeCall(client.mouseHandler, "onButton", client.getWindow().handle(), new MouseButtonInfo(0, 0), 0);
                check(client.mouseHandler.isMouseGrabbed() && !client.options.keyAttack.isDown(), "Carry G leaked a trailing attack");
                checkSystemCursorFree();
                return null;
            });
            check(serverBounds().equals(nudge), "Carry G lost the final held-grip sample");
        }
        select(false); aim(Face.SOUTH, false); tap();
        await(() -> editing.active() && editing.draft() != null, "carry flight entry");
        double flightY = onClient(() -> {
            nativeCall(client.mouseHandler, "onButton", client.getWindow().handle(), new MouseButtonInfo(0, 0), 1);
            check(editing.dragging(), "Carry flight grip missed");
            client.options.keyShift.setDown(true);
            return client.player.getY();
        });
        pause(350);
        onClient(() -> {
            check(client.player.getY() < flightY - 0.1 && editing.dragging(), "Shift did not descend during carry");
            check(editing.draft().equals(original), "Flight moved the selection without wheel input");
            client.options.keyShift.setDown(false);
            return null;
        });
        onClient(() -> { client.gui.hud.getChat().clearMessages(false); return null; });
        // Fabric's accelerated game ticks do not guarantee the real-time HUD fade has finished.
        await(() -> System.nanoTime() - (long) field(editing, "appeared") >= 200_000_000L, "carry appearance");
        onClient(() -> {
            check(editing.active() && editing.dragging() && !editing.capturesCamera(), "Carry ended before screenshot");
            return null;
        });
        screenshot.accept("selection-edit-carry");
        onClient(() -> {
            nativeCall(client.keyboardHandler, "keyPress", client.getWindow().handle(), 1, new KeyEvent(256, 0, 0));
            nativeCall(client.mouseHandler, "onButton", client.getWindow().handle(), new MouseButtonInfo(0, 0), 0);
            return null;
        });
        check(serverBounds().equals(original), "Flight carry cancellation committed");
        select(true); aim(Face.EAST, true);
        List<BlockVector3> ordered = onServer(() -> {
            var r = (CuboidRegion) WorldEdit.getInstance().getSessionManager().get(actor()).getSelection(actor().getWorld());
            return List.of(r.getPos1(), r.getPos2());
        });
        tap(); await(() -> editing.active() && editing.draft() != null, "move entry");
        onClient(() -> {
            check(!editing.capturesCamera() && client.mouseHandler.isMouseGrabbed(), "Move entry captured camera");
            checkCrosshair(true, "idle active Move");
            check(!editing.scroll(1), "Idle Move captured hotbar wheel");
            double x = client.getWindow().getScreenWidth() / 2.0, y = client.getWindow().getScreenHeight() / 2.0;
            nativeCall(client.mouseHandler, "onButton", client.getWindow().handle(), new MouseButtonInfo(0, 0), 1);
            check(editing.dragging() && editing.draft().equals(original), "Move missed or jumped on grab");
            check(!editing.cursorDrag() && !editing.capturesCamera(), "Carry took precise cursor ownership");
            double yaw = client.player.getYRot();
            nativeCall(client.mouseHandler, "onMove", client.getWindow().handle(), x, y);
            nativeCall(client.mouseHandler, "onMove", client.getWindow().handle(), x + 100, y - 80);
            nativeCall(client.mouseHandler, "handleAccumulatedMovement");
            check(Math.abs(client.player.getYRot() - yaw) > 0.05, "Carry did not retain native mouse look");
            check(editing.draft().equals(original), "Stationary look translated the selection");
            client.options.keyRight.setDown(true); client.options.keyShift.setDown(true); client.player.input.tick();
            check(client.player.input.keyPresses.right() && client.player.input.keyPresses.shift() && !editing.suppressSneak(), "Carry suppressed flight/Shift input");
            client.options.keyRight.setDown(false); client.options.keyShift.setDown(false); client.player.input.tick();
            // Player translation is navigation only, never an input to the Move draft.
            client.player.setPos(client.player.getX() + 12, client.player.getY() + 3, client.player.getZ() - 9);
            check(editing.draft().equals(original), "Player movement translated the selection");
            Face wheel = (Face) call(field(editing, "carry"), "wheelAxis");
            nativeCall(client.mouseHandler, "onScroll", client.getWindow().handle(), 0.0, 0.25);
            nativeCall(client.mouseHandler, "onScroll", client.getWindow().handle(), 0.0, 0.25);
            nativeCall(client.mouseHandler, "onScroll", client.getWindow().handle(), 0.0, 0.25);
            nativeCall(client.mouseHandler, "onScroll", client.getWindow().handle(), 0.0, 0.25);
            Point nudge = new Point(wheel.axis == 0 ? wheel.sign : 0, wheel.axis == 1 ? wheel.sign : 0, wheel.axis == 2 ? wheel.sign : 0);
            check(editing.draft().equals(original.translate(nudge, 1, -64, 319)), "Fractional Move wheel lost its captured axis");
            for (int axis = 0; axis < 3; axis++) check(editing.draft().size(axis) == original.size(axis), "Move resized box");
            nativeCall(client.mouseHandler, "onButton", client.getWindow().handle(), new MouseButtonInfo(0, 0), 0);
            check(editing.active() && !editing.dragging() && client.mouseHandler.isMouseGrabbed(), "Carry release committed or captured camera");
            checkCrosshair(true, "Move draft after mouse release");
            check(!editing.scroll(1), "Released carry captured idle wheel");
            return null;
        });
        CuboidDraft moved = onClient(editing::draft);
        check(serverBounds().equals(original), "Whole move committed early");
        pause(250); screenshot.accept("selection-edit-move");
        // Opening the menu over an existing draft and releasing in its center must keep that draft.
        onClient(() -> { hold.setDown(true); return null; }); await(editing::radialOpen, "active menu");
        onClient(() -> {
            radialPointer(0, 0);
            hold.setDown(false); return null;
        }); pause(150);
        check(onClient(editing::draft).equals(moved) && onClient(editing::active), "Active radial center discarded or applied draft");
        check(onClient(() -> !editing.capturesCamera() && client.mouseHandler.isMouseGrabbed()), "Active Move menu failed to restore native camera");
        release(); await(() -> !editing.pending(), "move commit");
        check(serverBounds().equals(moved), "Whole move did not commit");
        onClient(() -> { checkCrosshair(false, "Move apply"); return null; });
        onServer(() -> {
            var selector = WorldEdit.getInstance().getSessionManager().get(actor()).getRegionSelector(actor().getWorld());
            check(selector.getClass() == ExtendingCuboidRegionSelector.class, "Shift replaced selector");
            var r = (CuboidRegion) selector.getRegion();
            var delta = BlockVector3.at(moved.minX() - original.minX(), moved.minY() - original.minY(), moved.minZ() - original.minZ());
            check(r.getPos1().equals(ordered.get(0).add(delta)) && r.getPos2().equals(ordered.get(1).add(delta)), "Shift swapped ordered corners");
            return null;
        });
        select(false); aim(Face.SOUTH, false); tap(); await(() -> editing.active() && editing.draft() != null, "move cancel entry");
        onClient(() -> {
            nativeCall(client.mouseHandler, "onButton", client.getWindow().handle(), new MouseButtonInfo(0, 0), 1);
            check(editing.dragging(), "Move cancellation missed grip");
            nativeCall(client.mouseHandler, "onScroll", client.getWindow().handle(), 0.0, 3.0);
            nativeCall(client.keyboardHandler, "keyPress", client.getWindow().handle(), 1, new KeyEvent(256, 0, 0));
            nativeCall(client.mouseHandler, "onButton", client.getWindow().handle(), new MouseButtonInfo(0, 0), 0);
            check(!editing.active() && client.gui.screen() == null && client.mouseHandler.isMouseGrabbed(), "Move Escape failed"); return null;
        });
        check(serverBounds().equals(original), "Move Escape committed");
        moveAxes();
        onServer(() -> {
            var actor = actor();
            var shifted = original.translate(new Point(12, 3, -9), 1, -64, 319);
            var noShift = permissionActor(actor, false, true);
            var denied = SelectionEditTransaction.commitBounds(noShift, SelectionEditTransaction.read(noShift), shifted);
            check(!denied.ready() && denied.reason().equals("permission"), "Shift bypassed permission");
            check(SelectionEditTransaction.read(actor).bounds().equals(original), "Rejected shift partially changed selection");
            var shiftOnly = permissionActor(actor, true, false);
            var allowed = SelectionEditTransaction.commitBounds(shiftOnly, SelectionEditTransaction.read(shiftOnly), shifted);
            check(allowed.ready() && allowed.bounds().equals(shifted), "Shift-only actor unexpectedly needed expand/contract rights");
            var deniedResize = SelectionEditTransaction.commitBounds(shiftOnly, allowed, shifted.resize(Face.UP, 2, -64, 319));
            check(!deniedResize.ready() && deniedResize.reason().equals("permission"), "Shift-only actor resized selection");
            return null;
        });
        select(false);
        chooseMode(SelectionEditMode.RESIZE, false);
        onClient(() -> {
            nativeCall(client.keyboardHandler, "keyPress", client.getWindow().handle(), 1, new KeyEvent(71, 0, 0));
            nativeCall(client.keyboardHandler, "keyPress", client.getWindow().handle(), 0, new KeyEvent(71, 0, 0));
            check(editing.active() && !editing.radialOpen(), "Fast native tap was lost between client ticks");
            nativeCall(client.keyboardHandler, "keyPress", client.getWindow().handle(), 1, new KeyEvent(71, 0, 0));
            nativeCall(client.keyboardHandler, "keyPress", client.getWindow().handle(), 0, new KeyEvent(71, 0, 0));
            check(!editing.active() && !editing.radialOpen(), "Second fast native tap did not exit");
            return null;
        });
        onClient(() -> { hold.setDown(true); return null; }); await(editing::radialOpen, "menu cancel entry");
        onClient(() -> { nativeCall(client.keyboardHandler, "keyPress", client.getWindow().handle(), 1, new KeyEvent(256, 0, 0)); return null; });
        pause(500); check(!onClient(editing::radialOpen) && !onClient(editing::active), "Esc reopened held menu");
        onClient(() -> { hold.setDown(false); return null; }); pause(100);
    }
    private void moveAxes() {
        crosshairGameMode("creative");
        var floor = BlockVector3.at(-6, 65, -6);
        var previous = onServer(() -> actor().getWorld().getFullBlock(floor));
        onServer(() -> {
            actor().getWorld().setBlock(floor, com.sk89q.worldedit.world.block.BlockTypes.STONE.getDefaultState());
            client.getSingleplayerServer().getPlayerList().getPlayers().getFirst().getAbilities().flying = false;
            return null;
        });
        try {
            select(false);
            onClient(() -> { client.player.getAbilities().flying = false; return null; });
            aimAt(new Point(-5.5, 67.62, -5.5), original.center(Face.SOUTH));
            tap(); await(() -> editing.active() && editing.draft() != null, "independent axis entry");
            onClient(() -> {
                check(client.player.getY() > 65.99 && client.player.getY() < 66.01, "Axis test player is not parked on its solid floor");
                nativeCall(client.mouseHandler, "onButton", client.getWindow().handle(), new MouseButtonInfo(0, 0), 1);
                check(editing.dragging(), "Axis test missed Move grip");
                var probe = new KeyMapping("key.weso.axis_probe", 88, hold.getCategory());
                KeyMapping.resetMapping();
                axisKey(88, 1);
                check(!probe.isDown() && !probe.consumeClick(), "Owned X leaked into a native key binding");
                check(editing.draft().equals(original) && wheelAxis() == Face.EAST, "Selecting X moved the draft");
                nativeCall(client.mouseHandler, "onScroll", client.getWindow().handle(), 0.0, 0.25);
                axisKey(89, 1); axisKey(89, 0);
                check(editing.draft().equals(original) && wheelAxis() == Face.UP, "Selecting Y rotated the X fraction");
                axisKey(88, 2);
                check(wheelAxis() == Face.UP && !probe.isDown() && !probe.consumeClick(), "Held X repeat stole the chosen axis");
                axisKey(88, 0);
                nativeCall(client.mouseHandler, "onScroll", client.getWindow().handle(), 0.0, -4.0);
                check(editing.draft().equals(original.translate(new Point(0, -4, 0), 1, -64, 319)), "Y wheel failed to move through the solid floor");
                check(client.player.getY() > 65.99 && client.player.getY() < 66.01, "Y wheel moved the parked player");
                axisKey(90, 1); axisKey(90, 0);
                nativeCall(client.mouseHandler, "onScroll", client.getWindow().handle(), 0.0, 2.0);
                axisKey(88, 1); axisKey(88, 0);
                nativeCall(client.mouseHandler, "onScroll", client.getWindow().handle(), 0.0, 0.75);
                check(editing.draft().equals(original.translate(new Point(1, -4, 2), 1, -64, 319)), "XYZ wheel lost displacement across switches");
                call(editing, "bind", true, new KeyEvent(89, 0, 0));
                axisKey(89, 1);
                check(fast.isDown() && wheelAxis() == Face.EAST, "Axis shortcut overrode the rebound modifier");
                axisKey(89, 0); call(editing, "bind", true, new KeyEvent(340, 0, 0));
                check(!(boolean) call(editing, "axisKey", 1, new KeyEvent(90, 0, 2)), "Ctrl+Z was stolen by Move");
                return null;
            });
            CuboidDraft beforeTravel = onClient(editing::draft);
            onClient(() -> { client.player.getAbilities().flying = true; return null; });
            onServer(() -> { client.getSingleplayerServer().getPlayerList().getPlayers().getFirst().getAbilities().flying = true; return null; });
            pause(150);
            Object renderer = field(editing, "renderer");
            double[] oldProjection = onClient(() -> ((double[]) field(renderer, "compassX")).clone());
            Point eye = onClient(() -> (Point) call(call(renderer, "view", client), "eye"));
            Point forward = onClient(() -> (Point) call(call(renderer, "view", client), "forward"));
            Point travel = new Point(7, 3, -5);
            aimAt(eye.add(travel), eye.add(travel).add(forward.scale(10)));
            onClient(() -> {
                check(editing.draft().equals(beforeTravel), "Navigation moved the held Move draft");
                double[] projection = (double[]) field(renderer, "compassX");
                for (int axis = 0; axis < 3; axis++) check(Math.abs(oldProjection[axis] - projection[axis]) < 0.001,
                        "Translation without turning rotated the compass");
                check(wheelAxis() == Face.EAST, "Navigation reselected the grip axis");
                return null;
            });
            Point anchor = onClient(() -> ((Point) field(editing, "carryAnchor"))
                    .add(new Point(editing.draft().minX(), editing.draft().minY(), editing.draft().minZ())));
            Point orbitEye = anchor.add(new Point(9, 4, 11));
            for (Point direction : new Point[]{new Point(0, 0, 1), new Point(0, 0, -1), new Point(1, 0, 0),
                    new Point(-1, 0, 0), new Point(0, 1, 0), new Point(0, -1, 0), new Point(1, 1, 1),
                    new Point(-1, 1, 1), new Point(1, -1, -1)}) {
                aimAt(orbitEye, orbitEye.add(direction.scale(10)));
                onClient(() -> {
                    Object view = call(renderer, "view", client);
                    Point right = (Point) call(view, "right"), up = (Point) call(view, "up"), facing = (Point) call(view, "forward");
                    double[] x = (double[]) field(renderer, "compassX"), y = (double[]) field(renderer, "compassY"), depth = (double[]) field(renderer, "compassDepth");
                    check(x.length == 3 && y.length == 3 && depth.length == 3, "Compass must contain only three positive axes");
                    for (int axis = 0; axis < 3; axis++) {
                        check(Double.isFinite(x[axis]) && Double.isFinite(y[axis]) && Double.isFinite(depth[axis]), "Non-finite compass projection");
                        check(Math.abs(x[axis] - right.at(axis) * 23) < 0.001 && Math.abs(y[axis] + up.at(axis) * 23) < 0.001
                                && Math.abs(depth[axis] - facing.at(axis)) < 0.001, "Compass does not match the actual camera: " + direction);
                    }
                    check(wheelAxis() == Face.EAST && editing.draft().equals(beforeTravel), "Camera turn changed the grip axes or draft");
                    return null;
                });
            }
            aimAt(anchor.add(new Point(9, 4, 11)), anchor);
            pause(200);
            onClient(() -> {
                check(editing.draft().equals(beforeTravel), "Navigation moved the held Move draft");
                check(!java.util.Arrays.equals(oldProjection, (double[]) field(renderer, "compassX")), "Compass ignored camera rotation");
                return null;
            });
            for (int scale : new int[]{2, 3, 4}) {
                onClient(() -> { client.options.guiScale().set(scale); client.resizeGui(); return null; });
                // Re-enter if a native display resize cancelled the old gesture.
                if (!onClient(editing::active)) {
                    select(false); aim(Face.EAST, true); tap(); await(editing::active, "compass scale entry");
                    onClient(() -> { nativeCall(client.mouseHandler, "onButton", client.getWindow().handle(), new MouseButtonInfo(0, 0), 1); return null; });
                }
                onClient(() -> { axisKey(89, 1); axisKey(89, 0); client.gui.hud.getChat().clearMessages(false); return null; });
                pause(250); screenshot.accept("selection-edit-axes-x" + scale);
            }
            onClient(() -> { editing.cancel(); nativeCall(client.mouseHandler, "onButton", client.getWindow().handle(), new MouseButtonInfo(0, 0), 0); return null; });
            select(false); aimAt(new Point(-5, 70, -5), original.center(Face.SOUTH)); tap(); await(editing::active, "axis ownership entry");
            onClient(() -> {
                nativeCall(client.mouseHandler, "onButton", client.getWindow().handle(), new MouseButtonInfo(0, 0), 1);
                check(editing.dragging() && field(editing, "carry") != null, "Ownership test missed its Move grip");
                axisKey(88, 1);
                check((int) field(editing, "ownedAxisKeys") == 1, "Move X press was not owned before cancellation");
                editing.cancel();
                check((boolean) call(editing, "axisKey", 2, new KeyEvent(88, 0, 0)), "Cancel forwarded a consumed held key repeat");
                axisKey(88, 0);
                check((int) field(editing, "ownedAxisKeys") == 0 && !(boolean) call(editing, "axisKey", 1, new KeyEvent(89, 0, 0)), "Idle editor retained axis ownership");
                nativeCall(client.mouseHandler, "onButton", client.getWindow().handle(), new MouseButtonInfo(0, 0), 0);
                return null;
            });
            select(false); aimAt(new Point(-5, 70, -5), original.center(Face.SOUTH)); tap(); await(editing::active, "rebound edit entry");
            onClient(() -> {
                nativeCall(client.mouseHandler, "onButton", client.getWindow().handle(), new MouseButtonInfo(0, 0), 1);
                check(editing.dragging(), "Rebound key test missed its Move grip");
                call(editing, "bind", false, new KeyEvent(88, 0, 0));
                axisKey(88, 1); axisKey(88, 0);
                check(!editing.active(), "X axis swallowed the rebound edit/apply key");
                call(editing, "bind", false, new KeyEvent(71, 0, 0));
                nativeCall(client.mouseHandler, "onButton", client.getWindow().handle(), new MouseButtonInfo(0, 0), 0);
                client.options.guiScale().set(2); client.resizeGui(); return null;
            });
            check(serverBounds().equals(original), "Axis cancellation or no-op rebound apply changed the real selection");
        } finally {
            onClient(() -> { editing.cancel(); call(editing, "bind", false, new KeyEvent(71, 0, 0)); call(editing, "bind", true, new KeyEvent(340, 0, 0)); return null; });
            onServer(() -> { actor().getWorld().setBlock(floor, previous); return null; });
            crosshairGameMode("spectator");
        }
        System.out.println("MOVE AXES VERIFY PASS: XYZ without re-grab, stationary floor penetration, fractional ownership, no flight carry, key-repeat/rebind/cancel, fixed grip axes with camera-only compass rotation, scales 2/3/4");
    }
    private Face wheelAxis() { return (Face) call(field(editing, "carry"), "wheelAxis"); }
    private void axisKey(int key, int action) {
        nativeCall(client.keyboardHandler, "keyPress", client.getWindow().handle(), action, new KeyEvent(key, 0, 0));
    }
    private void checkCrosshair(boolean hidden, String context) {
        check(editing.hidesCrosshair() == hidden, "Wrong crosshair suppression: " + context);
    }
    private void crosshairLifecycle() {
        crosshairGameMode("creative");
        select(false);
        aimAt(new Point(30, 90, 30), new Point(30, 110, 30));
        onClient(() -> { client.gui.hud.getChat().clearMessages(false); checkCrosshair(false, "sky baseline"); return null; });
        pause(200); screenshot.accept("crosshair-visible");
        tap(); await(editing::active, "crosshair resize entry");
        onClient(() -> { checkCrosshair(true, "active Resize"); return null; });
        pause(200); screenshot.accept("crosshair-hidden");
        tap(); await(() -> !editing.active(), "crosshair G exit");
        onClient(() -> { checkCrosshair(false, "unchanged G apply"); return null; });
        pause(200); screenshot.accept("crosshair-restored");
        tap(); await(editing::active, "crosshair focus entry");
        onClient(() -> {
            boolean focused = client.isWindowActive();
            setField(client.getWindow(), "focused", false);
            try {
                checkCrosshair(false, "focus lost before tick");
                call(editing, "tick", client);
            } finally { setField(client.getWindow(), "focused", focused); }
            check(!editing.active(), "Focus loss retained editing");
            checkCrosshair(false, "focus regained after cancellation");
            return null;
        });
        await(() -> field(editing, "shown") != null && !editing.pending(), "selection reacquired after focus loss");
        tap(); await(editing::active, "crosshair disable entry");
        onClient(() -> {
            editing.configure(false, 1, 8); checkCrosshair(false, "editing disabled");
            editing.configure(true, 1, 8); checkCrosshair(false, "editing re-enabled");
            return null;
        });
        crosshairGameMode("spectator");
        onClient(() -> { client.gui.hud.getChat().clearMessages(false); return null; });
        System.out.println("CROSSHAIR VERIFY PASS: Resize/Move, radial, eight corners, G/mouse release, Escape, chat, focus, world identity, disable/re-enable");
    }
    private void crosshairGameMode(String mode) {
        onServer(() -> {
            var server = client.getSingleplayerServer();
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "gamemode " + mode + " @p");
            return null;
        });
        pause(200);
        onClient(() -> { client.player.getAbilities().flying = true; return null; });
    }
    private com.sk89q.worldedit.entity.Player permissionActor(com.sk89q.worldedit.entity.Player actor, boolean shift, boolean resize) {
        return (com.sk89q.worldedit.entity.Player) java.lang.reflect.Proxy.newProxyInstance(actor.getClass().getClassLoader(),
                new Class<?>[]{com.sk89q.worldedit.entity.Player.class}, (proxy, method, args) -> {
                    if (method.getName().equals("hasPermission")) {
                        if (args[0].equals("worldedit.selection.shift")) return shift;
                        if (args[0].equals("worldedit.selection.expand") || args[0].equals("worldedit.selection.contract")) return resize;
                    }
                    try { return method.invoke(actor, args); }
                    catch (java.lang.reflect.InvocationTargetException ex) { throw ex.getCause(); }
                });
    }
    private void chooseMode(SelectionEditMode mode, boolean captureScreenshots) {
        onClient(() -> {
            prepareLogicalFocus();
            axisKey(71, 0);
            return null;
        });
        await(() -> client.isWindowActive() && client.gui.screen() == null && client.gui.overlay() == null
                && (editing.active() || field(editing, "shown") != null), "ready for mode hold");
        onClient(() -> { nativeCall(client.keyboardHandler, "keyPress", client.getWindow().handle(), 1, new KeyEvent(71, 0, 0)); return null; });
        await(editing::radialOpen, "mode menu");
        for (int scale : captureScreenshots ? new int[]{2, 3, 4} : new int[]{2}) {
            onClient(() -> {
                client.options.guiScale().set(scale); client.resizeGui();
                int radius = (int) call(editing, "menuRadius", client);
                double dx = radius * 0.65 * (mode == SelectionEditMode.MOVE ? 1 : -1);
                radialPointer(dx, 0);
                return null;
            }); pause(150);
            if (captureScreenshots) {
                screenshot.accept("selection-edit-radial-x" + scale);
                onClient(() -> { radialPointer(0, 0); return null; }); pause(150);
                screenshot.accept("selection-edit-radial-cancel-x" + scale);
                onClient(() -> {
                    int radius = (int) call(editing, "menuRadius", client);
                    radialPointer(radius * 0.65 * (mode == SelectionEditMode.MOVE ? -1 : 1), 0);
                    return null;
                }); pause(150);
                screenshot.accept("selection-edit-radial-other-x" + scale);
                onClient(() -> {
                    int radius = (int) call(editing, "menuRadius", client);
                    radialPointer(radius * 0.65 * (mode == SelectionEditMode.MOVE ? 1 : -1), 0);
                    return null;
                });
            }
        }
        onClient(() -> { nativeCall(client.keyboardHandler, "keyPress", client.getWindow().handle(), 0, new KeyEvent(71, 0, 0)); return null; }); pause(150);
        onClient(() -> { client.options.guiScale().set(2); client.resizeGui(); return null; });
    }
    private void radialPointer(double x, double y) {
        double cursorX = (double) field(editing, "radialMouseX") + (x - (double) field(editing, "radialX"))
                * client.getWindow().getScreenWidth() / client.getWindow().getGuiScaledWidth();
        double cursorY = (double) field(editing, "radialMouseY") + (y - (double) field(editing, "radialY"))
                * client.getWindow().getScreenHeight() / client.getWindow().getGuiScaledHeight();
        nativeCall(client.mouseHandler, "onMove", client.getWindow().handle(), cursorX, cursorY);
    }
    private void checkCornerStep(Corner corner, double distance, int step) {
        Point offset = new Point(distance * corner.face(0).sign, distance * corner.face(1).sign, distance * corner.face(2).sign);
        Point start = (Point) field(editing, "cornerDragStart");
        call(editing, "updateCorner", start.add(offset));
        check(editing.draft().equals(original.moveCorner(corner, offset, step, -64, 319)),
                "Wrong corner step: " + corner + " expected=" + step + " fast=" + fast.isDown() + " hold=" + hold.isDown());
    }
    private void paletteHistory() {
        onClient(() -> {
            float legacyWidth = (float) call(overlay, "lineWidthScale");
            call(overlay, "previewLineWidthScale", 1.35f);
            Object screen = Class.forName("org.marshal.weaddon_lightposition.runtime.mc26_2.client.screen.OverlayColorSettingsScreen")
                    .getConstructors()[0].newInstance(overlay, null);
            client.gui.setScreen((net.minecraft.client.gui.screens.Screen) screen);
            Object baseline = call(screen, "paletteState");
            double x = ((Number) call(screen, "spectrumX")).doubleValue(), y = ((Number) call(screen, "spectrumY")).doubleValue();
            client.gui.screen().mouseClicked(new net.minecraft.client.input.MouseButtonEvent(x + 20, y + 20, new MouseButtonInfo(0, 0)), false);
            client.gui.screen().mouseClicked(new net.minecraft.client.input.MouseButtonEvent(x + 20, y + 20, new MouseButtonInfo(1, 0)), false);
            client.gui.screen().mouseReleased(new net.minecraft.client.input.MouseButtonEvent(x + 20, y + 20, new MouseButtonInfo(1, 0)));
            for (int i = 0; i < 100; i++) call(screen, "updateSpectrum", x + 20 + i, y + 25, false);
            client.gui.screen().mouseReleased(new net.minecraft.client.input.MouseButtonEvent(x + 119, y + 25, new MouseButtonInfo(0, 0)));
            Object dragged = call(screen, "paletteState");
            check(!dragged.equals(baseline) && ((java.util.Collection<?>) field(screen, "undoStates")).size() == 1, "Palette drag did not create one history step");
            paletteKey(90, 2); check(call(screen, "paletteState").equals(baseline), "Palette Ctrl+Z did not undo the complete drag");
            call(screen, "beginPaletteEdit"); call(screen, "finishPaletteEdit");
            paletteKey(89, 2); check(call(screen, "paletteState").equals(dragged), "Palette no-op destroyed redo or Ctrl+Y failed");

            if ((boolean) call(screen, "stackedLayout")) client.gui.screen().mouseScrolled(20, 80, 0, -100);
            double fieldX = ((Number) call(screen, "controlsX")).doubleValue() + 40;
            double fieldY = ((Number) call(screen, "fieldsY")).doubleValue() + 5;
            client.gui.screen().mouseClicked(new net.minecraft.client.input.MouseButtonEvent(fieldX, fieldY, new MouseButtonInfo(0, 0)), false);
            check(client.gui.screen().getFocused() == field(screen, "hexField"), "Palette HEX field did not receive focus");
            call(screen, "onHexChanged", "#70123456"); call(screen, "onHexChanged", "#80345678");
            paletteKey(90, 2); check(call(screen, "paletteState").equals(dragged), "Numeric entry was not coalesced or opacity was lost");
            paletteKey(90, 3); Object typed = call(screen, "paletteState");
            check(!typed.equals(dragged), "Palette Ctrl+Shift+Z did not redo");
            paletteKey(90, 2);
            call(screen, "updateAlpha", x + 35, false); call(screen, "finishPaletteEdit");
            Object branched = call(screen, "paletteState"); paletteKey(89, 2);
            check(call(screen, "paletteState").equals(branched) && ((java.util.Collection<?>) field(screen, "redoStates")).isEmpty(), "Palette new edit retained old redo branch");

            setField(screen, "templateMenuOpen", true); paletteKey(90, 2);
            check(call(screen, "paletteState").equals(branched), "Palette undo leaked behind a popup");
            setField(screen, "templateMenuOpen", false);
            Class<?> presets = java.util.Arrays.stream(screen.getClass().getDeclaredClasses()).filter(t -> t.getSimpleName().equals("OverlayPreset")).findFirst().orElseThrow();
            setField(screen, "pendingPreset", presets.getEnumConstants()[1]);
            paletteKey(89, 2); check(call(screen, "paletteState").equals(branched), "Palette redo applied a pending preset");
            call(screen, "clickPresetConfirmation", ((Number) call(screen, "confirmApplyX")).doubleValue() + 5,
                    ((Number) call(screen, "confirmButtonsY")).doubleValue() + 5);
            Object preset = call(screen, "paletteState");
            paletteKey(90, 2); check(call(screen, "paletteState").equals(branched), "Preset undo failed to restore all colors/widths");
            paletteKey(89, 2); check(call(screen, "paletteState").equals(preset), "Preset redo failed");

            Class<?> resets = java.util.Arrays.stream(screen.getClass().getDeclaredClasses()).filter(t -> t.getSimpleName().equals("ResetAction")).findFirst().orElseThrow();
            for (Object reset : resets.getEnumConstants()) {
                setField(screen, "resetMenuOpen", true);
                call(screen, "tryClickResetMenu", ((Number) call(screen, "resetMenuX")).doubleValue() + 8,
                        ((Number) call(screen, "resetMenuItemY", reset)).doubleValue() + 5);
                paletteKey(90, 2); check(call(screen, "paletteState").equals(preset), "Palette reset was not atomic: " + reset);
            }
            Object beforeResize = call(screen, "paletteState");
            int count = ((java.util.Collection<?>) field(screen, "undoStates")).size();
            client.resizeGui();
            check(call(screen, "paletteState").equals(beforeResize) && ((java.util.Collection<?>) field(screen, "undoStates")).size() == count, "Palette resize lost history");
            call(screen, "applyPaletteState", baseline);
            check((float) call(overlay, "lineWidthScale") == 1.35f, "Palette history lost legacy global thickness");
            call(overlay, "setLineWidthScale", legacyWidth);
            System.out.println("PALETTE HISTORY VERIFY PASS: native Ctrl+Z/Y, grouped drag/input, alpha/widths, presets/resets, redo branch, popups, resize");
            return null;
        });
        pause(200); screenshot.accept("palette-history");
        onClient(() -> { client.gui.setScreen(null); return null; });
    }
    private void checkCornerOutline(Corner corner) {
        onClient(() -> {
            Object renderer = field(editing, "renderer");
            check(field(renderer, "outlineDraft").equals(editing.draft()), "Stale native outline geometry");
            check(field(renderer, "outlineCorner") == corner, "Native outline uses another corner");
            List<?> active = (List<?>) field(renderer, "outlineActive"), boundary = (List<?>) field(renderer, "outlineBoundary");
            check(active.size() == 3 && boundary.size() == 9, "Native outline has duplicate/missing edges");
            var markers = new java.util.ArrayList<RectGizmo>();
            try (var collection = Gizmos.withCollector(gizmo -> {
                check(gizmo instanceof RectGizmo, "Corner marker is not a native quad");
                markers.add((RectGizmo) gizmo);
                return GizmoCollector.IGNORED;
            })) {
                call(renderer, "corners", editing.draft(), corner, call(renderer, "view", client), field(editing, "appeared"), false);
            }
            check(!markers.isEmpty(), "Missing visible native corner markers");
            for (var marker : markers) check(!marker.style().hasStroke() && marker.style().hasFill()
                    && (marker.style().fill() & 0xFFFFFF) == (OverlayRenderStyle.color(OverlayColorSlot.EDIT_BOUNDARY) & 0xFFFFFF),
                    "Corner marker retained an outline or a dark inset");
            for (String collection : new String[]{"outlineBoundary", "outlineGhost"}) for (Object edge : (List<?>) field(renderer, collection)) {
                int axis = (int) call(edge, "axis");
                Point from = (Point) call(edge, "from"), to = (Point) call(edge, "to");
                for (Object stroke : active) {
                    if ((int) call(stroke, "axis") != axis) continue;
                    Point start = (Point) call(stroke, "from"), end = (Point) call(stroke, "to");
                    boolean collinear = true;
                    for (int coordinate = 0; coordinate < 3; coordinate++) if (coordinate != axis && from.at(coordinate) != start.at(coordinate)) collinear = false;
                    if (!collinear) continue;
                    check(Math.max(from.at(axis), Math.min(start.at(axis), end.at(axis))) >= Math.min(to.at(axis), Math.max(start.at(axis), end.at(axis))),
                            "Native boundary/ghost underlays a corner axis: " + corner);
                }
            }
            return null;
        });
    }
    private void contrastingCornerOutline(Corner corner) {
        OverlayColorSlot[] slots = {OverlayColorSlot.EDIT_BOUNDARY, OverlayColorSlot.EDIT_AXIS_X,
                OverlayColorSlot.EDIT_AXIS_Y, OverlayColorSlot.EDIT_AXIS_Z};
        int[] beforeColors = new int[4], colors = {0xFFFFFF00, 0xFF00FFFF, 0xFF101010, 0x80FFFFFF};
        float[] beforeWidths = new float[4];
        onClient(() -> {
            Object renderer = field(editing, "renderer"), geometry = field(renderer, "outlineBoundary");
            for (int i = 0; i < slots.length; i++) {
                beforeColors[i] = OverlayRenderStyle.color(slots[i]);
                beforeWidths[i] = OverlayRenderStyle.lineWidthScale(slots[i]);
                call(overlay, "previewOverlayColor", slots[i], colors[i]);
                call(overlay, "previewOverlayLineWidthScale", slots[i], i == 0 ? 4.0f : 0.5f);
            }
            call(renderer, "outline", original, editing.draft(), corner, null);
            check(field(renderer, "outlineBoundary") == geometry, "Palette changes unnecessarily rebuilt native outline geometry");
            return null;
        });
        try {
            pause(250); checkCornerOutline(corner);
            screenshot.accept("selection-edit-axis-outline");
            System.out.println("EDIT OUTLINE VERIFY PASS: 9 contour + 3 exclusive axis edges, clipped original ghost, independent RGBA/widths, borderless native corner quads");
        } finally {
            onClient(() -> {
                for (int i = 0; i < slots.length; i++) {
                    call(overlay, "previewOverlayColor", slots[i], beforeColors[i]);
                    call(overlay, "previewOverlayLineWidthScale", slots[i], beforeWidths[i]);
                }
                return null;
            });
        }
    }
    private void editingPaletteAndClassic() {
        Object styleBefore = onClient(() -> call(overlay, "uiStyle"));
        boolean blurBefore = onClient(() -> (boolean) call(overlay, "uiBlur"));
        boolean motionBefore = onClient(() -> (boolean) call(overlay, "reducedMotion"));
        Object stateBefore = onClient(() -> {
            call(overlay, "setUiStyle", UiStyle.GLASS);
            Object screen = Class.forName("org.marshal.weaddon_lightposition.runtime.mc26_2.client.screen.OverlayColorSettingsScreen")
                    .getConstructors()[0].newInstance(overlay, null);
            client.gui.setScreen((net.minecraft.client.gui.screens.Screen) screen);
            Object state = call(screen, "paletteState");
            OverlayColorSlot[] slots = {OverlayColorSlot.EDIT_BOUNDARY, OverlayColorSlot.EDIT_AXIS_X,
                    OverlayColorSlot.EDIT_AXIS_Y, OverlayColorSlot.EDIT_AXIS_Z};
            int[] colors = {0xC0F0F0F0, 0xD0FF6070, 0xE070FF90, 0xF070A0FF};
            call(screen, "beginPaletteEdit");
            for (int i = 0; i < slots.length; i++) {
                call(overlay, "previewOverlayColor", slots[i], colors[i]);
                call(overlay, "previewOverlayLineWidthScale", slots[i], 1.5f);
            }
            call(screen, "finishPaletteEdit");
            Object changed = call(screen, "paletteState");
            paletteKey(90, 2); check(call(screen, "paletteState").equals(state), "Editing palette undo lost color/width");
            paletteKey(89, 2); check(call(screen, "paletteState").equals(changed), "Editing palette redo lost color/width");
            var serialized = new OverlayColorProfile();
            for (int i = 0; i < slots.length; i++) {
                check(OverlayRenderStyle.color(slots[i]) == colors[i], "Editing color not delivered to world renderer");
                serialized.setColor(slots[i], colors[i]); serialized.setLineWidthScale(slots[i], 1.5f);
            }
            var loaded = new OverlayColorProfile();
            for (String line : serialized.toConfigLines("")) {
                int split = line.indexOf('='); loaded.applyConfigValue(line.substring(0, split), line.substring(split + 1));
            }
            for (String line : serialized.toLineWidthConfigLines("")) {
                int split = line.indexOf('='); loaded.applyLineWidthConfigValue(line.substring(0, split), line.substring(split + 1));
            }
            for (var slot : slots) check(loaded.color(slot) == serialized.color(slot) && loaded.lineWidthScale(slot) == 1.5f,
                    "Editing palette config roundtrip failed: " + slot);
            call(overlay, "setAllOverlayLineWidthScale", 1.25f);
            check((boolean) call(overlay, "allOverlayLineWidthsSame"), "Global width did not include editing slots");
            for (var slot : slots) check(OverlayRenderStyle.lineWidth(SelectionEditStyle.BOUNDARY_WIDTH, slot) == 1.25f,
                    "World editing width not linked to global slider");
            var preview = SelectionEditStyle.previewSegments();
            check(preview.size() == 12 && preview == SelectionEditStyle.previewSegments(), "Editing preview not cached or has duplicate strokes");
            call(overlay, "previewOverlayLineWidthScale", slots[1], 1.75f);
            check(!(boolean) call(overlay, "allOverlayLineWidthsSame"), "Custom editing width did not disable global slider");
            check(preview == SelectionEditStyle.previewSegments(), "Thickness unnecessarily rebuilt preview geometry");
            call(overlay, "previewOverlayColor", slots[1], 0xFF123456);
            check(preview != SelectionEditStyle.previewSegments(), "Editing preview ignored color invalidation");
            call(overlay, "resetOverlayColor", slots[1]);
            check(OverlayRenderStyle.color(slots[1]) == slots[1].defaultColor() && OverlayRenderStyle.lineWidthScale(slots[1]) == 1,
                    "Per-element editing reset did not restore both color and width");
            check(OverlayRenderStyle.color(slots[2]) == colors[2], "Per-element reset changed another axis");
            for (Corner corner : Corner.values()) for (int axis = 0; axis < 3; axis++) {
                Point from = original.corner(corner), end = SelectionEditStyle.axisEnd(original, corner, axis);
                for (int checkAxis = 0; checkAxis < 3; checkAxis++) check(end.at(checkAxis) == (checkAxis == axis
                        ? corner.face(axis).sign > 0 ? original.min(axis) : original.max(axis) + 1.0 : from.at(checkAxis)),
                        "Editing preview/world axis endpoint mismatch");
            }
            Class<?> presets = java.util.Arrays.stream(screen.getClass().getDeclaredClasses()).filter(t -> t.getSimpleName().equals("OverlayPreset")).findFirst().orElseThrow();
            for (Object preset : presets.getEnumConstants()) {
                OverlayColorProfile profile = (OverlayColorProfile) call(preset, "profile");
                check(profile.lineWidthScale(slots[0]) == profile.lineWidthScale(OverlayColorSlot.CUBOID_EDGE), "Preset skipped editing thickness");
                check(profile.color(slots[1]) != profile.color(slots[2]) && profile.color(slots[2]) != profile.color(slots[3]), "Preset axes lost distinction");
            }
            call(screen, "applyPaletteState", state);
            Class<?> groups = java.util.Arrays.stream(screen.getClass().getDeclaredClasses()).filter(t -> t.getSimpleName().equals("ColorGroup")).findFirst().orElseThrow();
            Object editingGroup = java.util.Arrays.stream(groups.getEnumConstants()).filter(t -> t.toString().equals("EDITING")).findFirst().orElseThrow();
            setField(screen, "selectedGroup", editingGroup); setField(screen, "selectedSlot", slots[0]);
            call(screen, "syncHsvFromSelectedColor"); call(screen, "syncFields"); call(screen, "syncGroupButtons"); call(screen, "layoutWidgets");
            return state;
        });
        for (int scale : new int[]{2, 3, 4}) {
            onClient(() -> { client.options.guiScale().set(scale); client.resizeGui(); return null; });
            pause(200); screenshot.accept("palette-editing-x" + scale);
            onClient(() -> { client.gui.screen().mouseScrolled(20, 80, 0, -100); return null; });
            pause(150); screenshot.accept("palette-editing-controls-x" + scale);
            onClient(() -> { client.gui.screen().mouseScrolled(20, 80, 0, 100); return null; });
        }
        onClient(() -> {
            call(client.gui.screen(), "applyPaletteState", stateBefore);
            client.options.guiScale().set(2); client.resizeGui();
            call(overlay, "setUiBlur", true); call(overlay, "setReducedMotion", true);
            call(overlay, "setUiStyle", UiStyle.LEGACY);
            Object screen = Class.forName("org.marshal.weaddon_lightposition.runtime.mc26_2.client.screen.OverlaySettingsScreen")
                    .getConstructors()[0].newInstance(overlay, null);
            client.gui.setScreen((net.minecraft.client.gui.screens.Screen) screen);
            call(screen, "setMenuOpen", true);
            check(((List<?>) field(screen, "menuControls")).size() == 3 && (int) call(screen, "menuHeight") == 103,
                    "Classic retained inapplicable appearance controls/space");
            check((boolean) call(overlay, "uiBlur") && (boolean) call(overlay, "reducedMotion"), "Classic erased modern preferences");
            return null;
        });
        pause(200); screenshot.accept("settings-classic-appearance");
        onClient(() -> {
            call(overlay, "setUiStyle", UiStyle.GLASS);
            Object screen = Class.forName("org.marshal.weaddon_lightposition.runtime.mc26_2.client.screen.OverlaySettingsScreen")
                    .getConstructors()[0].newInstance(overlay, null);
            client.gui.setScreen((net.minecraft.client.gui.screens.Screen) screen);
            call(screen, "setMenuOpen", true);
            check(((List<?>) field(screen, "menuControls")).size() == 5 && (int) call(screen, "menuHeight") == 155,
                    "Modern appearance controls did not return");
            call(overlay, "setUiStyle", styleBefore); call(overlay, "setUiBlur", blurBefore); call(overlay, "setReducedMotion", motionBefore);
            client.gui.setScreen(null); return null;
        });
        System.out.println("EDITING PALETTE VERIFY PASS: colors/alpha/widths, config roundtrip, shared preview strokes/cache, all-corner axes, presets, resets, history, Classic appearance preferences");
    }
    private void paletteKey(int key, int modifiers) {
        check(client.gui.screen().keyPressed(new KeyEvent(key, 0, modifiers)), "Palette shortcut was not consumed");
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
        aim(face, angled, true);
        onClient(() -> { prepareLogicalFocus(); return null; });
        tap();
        await(() -> editing.active() && editing.draft() != null, "enter editing " + face);
        onClient(() -> {
            check(editing.capturesCamera() && !client.mouseHandler.isMouseGrabbed(), "G did not immediately capture camera and release cursor");
            client.options.keyRight.setDown(true); client.player.input.tick();
            check(!client.player.input.keyPresses.right(), "G retained movement before dragging");
            client.options.keyRight.setDown(false);
            nativeCall(client.mouseHandler, "onMove", client.getWindow().handle(), client.getWindow().getScreenWidth() / 2.0, client.getWindow().getScreenHeight() / 2.0);
            check(editing.target() == previous, "Looking at a new face changed remembered choice");
            nativeCall(client.mouseHandler, "onButton", client.getWindow().handle(), new MouseButtonInfo(0, 0), 1);
            check(!editing.dragging(), "Whole face click unexpectedly dragged handle");
            nativeCall(client.mouseHandler, "onButton", client.getWindow().handle(), new MouseButtonInfo(0, 0), 0);
            check(editing.target() == face, "Distance face click did not select " + face);
            return null;
        });
        cursorAt(original.center(face));
    }
    private void cursorAt(Point point) {
        onClient(() -> {
            Object view = field(editing, "editView");
            Point offset = point.subtract((Point) call(view, "eye"));
            double depth = offset.dot((Point) call(view, "forward")), tangent = (double) call(view, "tangent"), aspect = (double) call(view, "aspect");
            check(depth > 0.05, "Cursor target behind captured camera");
            double x = (0.5 + offset.dot((Point) call(view, "right")) / (2 * depth * tangent * aspect)) * client.getWindow().getScreenWidth();
            double y = (0.5 - offset.dot((Point) call(view, "up")) / (2 * depth * tangent)) * client.getWindow().getScreenHeight();
            nativeCall(client.mouseHandler, "onMove", client.getWindow().handle(), x, y); return null;
        });
    }
    private void tap() {
        onClient(() -> { prepareLogicalFocus(); return null; });
        await(() -> client.isWindowActive() && (editing.active() || field(editing, "shown") != null), "ready for G tap");
        onClient(() -> { axisKey(71, 1); axisKey(71, 0); return null; }); pause(150);
    }
    private void release() {
        if (onClient(editing::active)) tap();
        else { onClient(() -> { hold.setDown(false); return null; }); pause(150); }
    }
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
    private void prepareLogicalFocus() {
        if (System.getenv("WESO_TEST_DESKTOP") == null) throw new AssertionError("Selection input tests require the private desktop runner");
        // Simulated input needs game focus, not OS focus on the user's desktop.
        setField(client.getWindow(), "focused", true);
    }
    private void checkSystemCursorFree() {
        check("active".equals(System.getProperty("weso.test.cursorIsolation")), "Native cursor guard was not loaded");
        check(org.lwjgl.glfw.GLFW.glfwGetInputMode(client.getWindow().handle(), org.lwjgl.glfw.GLFW.GLFW_CURSOR)
                == org.lwjgl.glfw.GLFW.GLFW_CURSOR_NORMAL, "Test client captured the system cursor");
    }
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
