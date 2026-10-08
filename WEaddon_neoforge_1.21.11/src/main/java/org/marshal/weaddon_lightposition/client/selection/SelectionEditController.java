package org.marshal.weaddon_lightposition.client.selection;

import net.neoforged.fml.ModList;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.marshal.weaddon_lightposition.client.overlay.SelectionRegion;
import org.marshal.weaddon_lightposition.selection.CuboidDraft;
import org.marshal.weaddon_lightposition.selection.CuboidDraft.Corner;
import org.marshal.weaddon_lightposition.selection.CuboidDraft.Face;
import org.marshal.weaddon_lightposition.selection.CuboidDraft.Point;
import org.marshal.weaddon_lightposition.selection.SelectionEditOptions;
import org.marshal.weaddon_lightposition.selection.SelectionEditSnapshot;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class SelectionEditController {
    private static final SelectionEditController INSTANCE = new SelectionEditController();
    private final SelectionEditRenderer renderer = new SelectionEditRenderer();
    private SelectionEditOptions options = SelectionEditOptions.defaults();
    private SelectionEditSource source;
    private KeyMapping editKey, fastKey;
    private Path settings;
    private SelectionEditSnapshot origin, shown;
    private CuboidDraft draft, acknowledged;
    private Face selected = Face.UP, locked;
    private Corner lockedCorner;
    private Point cornerDragStart;
    private SelectionEditRenderer.View dragView;
    private double dragStart, scrollRemainder;
    private boolean active, dragging, directCorner, pending, reading, latch;
    private int blockedButtons, ticks, epoch, ackTicks, commitSerial, lastReadTick = -4;
    private Object level, player;
    private long appeared;
    private int dragWidth, dragHeight;
    private Object dragCamera;
    private String reason = "";

    private SelectionEditController() { }
    public static SelectionEditController instance() { return INSTANCE; }
    public SelectionEditOptions options() { return options; }
    public boolean available() { return source != null && Minecraft.getInstance().getSingleplayerServer() != null; }
    public boolean active() {
        if (!active && !pending) return false;
        Minecraft client = Minecraft.getInstance();
        return client.level == level && client.player == player && client.screen == null && client.isWindowActive();
    }
    public boolean dragging() { return dragging; }
    public boolean cursorDrag() { return dragging && !directCorner; }
    public boolean visible() { return usable(Minecraft.getInstance()) && (active() || shown != null); }
    public boolean suppressSneak() { return active() && fastKey != null && fastKey.isDown(); }
    public boolean pending() { return pending; }
    public CuboidDraft draft() { return draft; }
    public Face target() { return locked != null ? locked : selected; }
    public String reason() { return reason; }
    public void keys(KeyMapping edit, KeyMapping fast) { editKey = edit; fastKey = fast; }
    public Component bindingName(boolean fast) { return (fast ? fastKey : editKey).getTranslatedKeyMessage(); }
    public void bind(boolean fast, net.minecraft.client.input.KeyEvent event) {
        (fast ? fastKey : editKey).setKey(event == null ? com.mojang.blaze3d.platform.InputConstants.UNKNOWN : com.mojang.blaze3d.platform.InputConstants.getKey(event));
        KeyMapping.resetMapping();
        Minecraft.getInstance().options.save();
    }

    public void initialize(Path directory) {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.RenderGuiEvent.Post event) -> renderHud(event.getGuiGraphics()));
        settings = directory.resolve("selection-editing.properties");
        if (Files.isRegularFile(settings)) {
            Properties p = new Properties();
            try (var input = Files.newInputStream(settings)) { p.load(input); options = SelectionEditOptions.load(p); }
            catch (java.io.IOException ex) { log().warn("Could not load selection editing settings.", ex); }
        }
        if (!ModList.get().isLoaded("worldedit")) return;
        try { source = (SelectionEditSource) Class.forName("org.marshal.weaddon_lightposition.client.selection.WorldEditSelectionEditSource").getDeclaredConstructor().newInstance(); }
        catch (ReflectiveOperationException | LinkageError ex) { log().warn("Local selection editing is unavailable.", ex); }
    }
    private static org.slf4j.Logger log() { return org.slf4j.LoggerFactory.getLogger("worldedit_selection_overlay/selection-edit"); }
    public void configure(boolean enabled, int step, int fast) {
        SelectionEditOptions next = new SelectionEditOptions(enabled, step, fast);
        if (options.equals(next)) return;
        options = next;
        if (!enabled) { cancel(); shown = null; }
        if (settings == null) return;
        try {
            Files.createDirectories(settings.getParent());
            try (var output = Files.newOutputStream(settings)) { options.properties().store(output, "WorldEdit selection editing"); }
        } catch (java.io.IOException ex) { log().warn("Could not save selection editing settings.", ex); }
    }

    public void tick(Minecraft client) {
        ticks++;
        boolean held = editKey != null && editKey.isDown();
        if (!held) latch = false;
        if (client.level != level || client.player != player) {
            pending = false; ++commitSerial; cancel();
            level = client.level; player = client.player; acknowledged = null; shown = null; lastReadTick = ticks - 4;
        }
        if (acknowledged != null && --ackTicks <= 0) acknowledged = null;
        if (!usable(client)) { if (active || reading) cancel(); shown = null; return; }
        if (dragging && (client.getWindow().getScreenWidth() != dragWidth || client.getWindow().getScreenHeight() != dragHeight
                || client.options.getCameraType() != dragCamera)) { cancel(); return; }
        if (active && !held && !directCorner) {
            if (dragging) cancel(); else submit();
            return;
        }
        if (pending) return;
        if (!active && held && !latch) begin(client);
        if (!reading && ticks - lastReadTick >= 4) read(client);
        if (cursorDrag()) client.player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
    }

    private boolean usable(Minecraft client) {
        return options.enabled() && source != null && client.player != null && client.level != null
                && client.level == level && client.player == player && client.getSingleplayerServer() != null
                && client.screen == null && client.isWindowActive();
    }
    private void begin(Minecraft client) {
        active = true; appeared = System.nanoTime(); reason = ""; origin = shown;
        draft = origin == null ? null : origin.bounds(); locked = null; lockedCorner = null;
        directCorner = false; scrollRemainder = 0; clearConstruction(client);
    }

    private void read(Minecraft client) {
        if (reading) return;
        reading = true; lastReadTick = ticks;
        int generation = epoch;
        source.read(client, value -> {
            if (generation != epoch) return;
            reading = false;
            if (!usable(client) || pending) return;
            if (!value.ready()) { shown = null; reason = value.reason(); if (origin != null) cancel(); return; }
            if (shown != value) appeared = System.nanoTime();
            shown = value;
            if (origin != null && origin != value) { reason = "stale"; cancel(); return; }
            if (active && origin == null) { origin = value; draft = value.bounds(); }
        });
    }

    public boolean scroll(double amount) {
        if (!active()) return false;
        if (!active || pending || dragging || draft == null || target() == null || !Double.isFinite(amount)) return true;
        scrollRemainder += amount;
        long notches = (long) scrollRemainder;
        scrollRemainder -= notches;
        if (notches == 0) return true;
        if (locked == null) locked = selected;
        int step = fastKey != null && fastKey.isDown() ? options.fastStep() : options.step();
        if (notches > 0 ? !origin.expand() : !origin.contract()) return true;
        draft = draft.resize(locked, Math.clamp(notches, -1000L, 1000L) * step, origin.minY(), origin.maxY());
        return true;
    }

    public boolean button(int button, int action) {
        if (active && !active()) cancel();
        if (button < 0 || button > 7) return active();
        int bit = 1 << button;
        boolean blocked = (blockedButtons & bit) != 0;
        if (action == GLFW.GLFW_RELEASE) {
            blockedButtons &= ~bit;
            if (button == 0 && dragging) {
                if (directCorner) followCorner(renderer.view(Minecraft.getInstance()));
                boolean captured = cursorDrag();
                dragging = false;
                if (captured) restoreCursor(); else clearConstruction(Minecraft.getInstance());
                submit(); return true;
            }
            // Releases of buttons held before editing must still clear vanilla's mouse flags.
            return blocked;
        }
        if (blocked || active()) {
            blockedButtons |= bit;
            if (button == 0 && active && !pending && !dragging && locked == null && draft != null) choose();
            return true;
        }
        if (button == 0 && action == GLFW.GLFW_PRESS && visible() && !pending && shown != null) {
            SelectionEditRenderer.View view = renderer.view(Minecraft.getInstance());
            Corner corner = renderer.cornerHit(shown.bounds(), view);
            if (corner != null) {
                begin(Minecraft.getInstance());
                if (beginCorner(corner, view, true)) { blockedButtons |= bit; return true; }
                cancel();
            }
        }
        return false;
    }

    private void choose() {
        Minecraft client = Minecraft.getInstance();
        SelectionEditRenderer.View view = renderer.view(client);
        Corner corner = renderer.cornerHit(draft, view);
        if (corner != null) { beginCorner(corner, view, false); return; }
        Face handle = renderer.handleHit(draft, view);
        Face face = handle != null ? handle : draft.intersect(view.eye(), view.forward());
        if (face == null) return;
        selected = face;
        if (handle == null) return;
        double start = CuboidDraft.axisParameter(view.eye(), view.forward(), draft.center(handle), handle);
        if (!Double.isFinite(start)) return;
        locked = handle; dragStart = start; capture(view);
    }
    private boolean beginCorner(Corner corner, SelectionEditRenderer.View view, boolean direct) {
        double distance = draft.corner(corner).subtract(view.eye()).dot(view.forward());
        Point start = direct ? CuboidDraft.rayPoint(view.eye(), view.forward(), distance)
                : CuboidDraft.planePoint(view.eye(), view.forward(), draft.corner(corner), view.forward());
        if (start == null) return false;
        lockedCorner = corner; cornerDragStart = start; directCorner = direct;
        if (direct) dragStart = distance;
        capture(view); return true;
    }
    private void capture(SelectionEditRenderer.View view) {
        Minecraft client = Minecraft.getInstance();
        dragView = view; dragging = true;
        dragWidth = client.getWindow().getScreenWidth(); dragHeight = client.getWindow().getScreenHeight();
        dragCamera = client.options.getCameraType();
        if (!directCorner) client.mouseHandler.releaseMouse();
    }
    public void move(double x, double y) {
        if (!cursorDrag() || draft == null || !active() || !Double.isFinite(x) || !Double.isFinite(y)) return;
        Minecraft client = Minecraft.getInstance();
        Point ray = dragView.ray(x / client.getWindow().getScreenWidth(), y / client.getWindow().getScreenHeight());
        int step = fastKey != null && fastKey.isDown() ? options.fastStep() : options.step();
        CuboidDraft next;
        if (lockedCorner != null) {
            Point position = CuboidDraft.planePoint(dragView.eye(), ray, origin.bounds().corner(lockedCorner), dragView.forward());
            if (position == null) return;
            updateCorner(position); return;
        } else {
            double parameter = CuboidDraft.axisParameter(dragView.eye(), ray, origin.bounds().center(locked), locked);
            if (!Double.isFinite(parameter)) return;
            long offset = CuboidDraft.snappedOffset(parameter - dragStart, step, locked.sign);
            next = origin.bounds().resize(locked, offset, origin.minY(), origin.maxY());
            if (!permitted(next, locked)) return;
        }
        draft = next;
    }
    private void followCorner(SelectionEditRenderer.View view) {
        updateCorner(CuboidDraft.rayPoint(view.eye(), view.forward(), dragStart));
    }
    private void updateCorner(Point position) {
        if (position == null || lockedCorner == null || origin == null || cornerDragStart == null) return;
        int step = directCorner ? 1 : (fastKey != null && fastKey.isDown() ? options.fastStep() : options.step());
        CuboidDraft next = origin.bounds().moveCorner(lockedCorner, position.subtract(cornerDragStart), step, origin.minY(), origin.maxY());
        for (int axis = 0; axis < 3; axis++) if (!permitted(next, lockedCorner.face(axis))) return;
        if (!next.equals(draft)) draft = next;
    }
    private boolean permitted(CuboidDraft next, Face face) {
        long delta = ((long) next.boundary(face) - origin.bounds().boundary(face)) * face.sign;
        return delta == 0 || (delta > 0 ? origin.expand() : origin.contract());
    }

    private void submit() {
        if (!active || pending) return;
        if (origin == null || (locked == null && lockedCorner == null) || draft.equals(origin.bounds())) { cancel(); return; }
        active = false; pending = true; latch = true; reading = false; ++epoch;
        CuboidDraft committed = draft;
        Object submittedLevel = level;
        int submittedSerial = ++commitSerial;
        source.commit(Minecraft.getInstance(), origin, committed, locked, lockedCorner, value -> {
            if (submittedSerial != commitSerial || level != submittedLevel) return;
            pending = false;
            if (value.ready() && value.bounds().equals(committed)) { acknowledged = committed; ackTicks = 40; }
            else {
                acknowledged = null;
                reason = value.reason();
                Minecraft.getInstance().gui.setOverlayMessage(Component.translatable("selection_edit.worldedit_selection_overlay." + reason), false);
            }
            draft = null; origin = shown = null; locked = null; lockedCorner = null; directCorner = false; dragView = null; cornerDragStart = null;
        });
    }
    public void cancel() {
        boolean captured = cursorDrag(), wasDragging = dragging;
        dragging = false; directCorner = false; active = false; reading = false; latch = true; ++epoch;
        dragView = null; cornerDragStart = null;
        if (!pending) { origin = null; draft = null; locked = null; lockedCorner = null; }
        if (captured) restoreCursor(); else if (wasDragging) clearConstruction(Minecraft.getInstance());
    }
    private static void restoreCursor() {
        Minecraft client = Minecraft.getInstance();
        if (client.screen == null && client.isWindowActive()) client.mouseHandler.grabMouse();
        clearConstruction(client);
    }
    private static void clearConstruction(Minecraft client) {
        for (KeyMapping key : new KeyMapping[]{client.options.keyAttack, client.options.keyUse, client.options.keyPickItem}) {
            key.setDown(false); while (key.consumeClick()) { }
        }
    }

    /** Freeze only the old isolation mask while the authoritative CUI sequence is incomplete. */
    public boolean acknowledgeIsolation(SelectionRegion region) {
        if (acknowledged == null || region == null) return false;
        var bounds = region.boundsForAnalysis();
        if (bounds == null) return false;
        if (bounds.minX() != acknowledged.minX() || bounds.minY() != acknowledged.minY() || bounds.minZ() != acknowledged.minZ()
                || bounds.maxX() != acknowledged.maxX() || bounds.maxY() != acknowledged.maxY() || bounds.maxZ() != acknowledged.maxZ()) return false;
        acknowledged = null; return true;
    }
    public boolean waitingForCui() { return pending || acknowledged != null; }
    public void renderWorld() {
        if (!visible()) return;
        Minecraft client = Minecraft.getInstance();
        SelectionEditRenderer.View view = cursorDrag() ? dragView : renderer.view(client);
        if (dragging && directCorner) followCorner(view);
        if (active() && draft != null) renderer.world(origin.bounds(), draft, lockedCorner == null ? target() : null,
                lockedCorner, view, appeared, pending);
        else if (shown != null) renderer.corners(shown.bounds(), renderer.cornerHit(shown.bounds(), view), view, appeared, false);
    }
    public void renderHud(net.minecraft.client.gui.GuiGraphics graphics) {
        if (active()) renderer.hud(graphics, draft, target(), lockedCorner, origin, reason, pending, appeared);
    }
}
