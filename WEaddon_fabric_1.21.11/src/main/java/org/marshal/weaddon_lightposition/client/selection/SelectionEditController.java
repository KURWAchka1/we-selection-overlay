package org.marshal.weaddon_lightposition.client.selection;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.marshal.weaddon_lightposition.client.overlay.SelectionRegion;
import org.marshal.weaddon_lightposition.selection.CuboidDraft;
import org.marshal.weaddon_lightposition.selection.CuboidDraft.Corner;
import org.marshal.weaddon_lightposition.selection.CuboidDraft.Face;
import org.marshal.weaddon_lightposition.selection.CuboidDraft.Point;
import org.marshal.weaddon_lightposition.selection.SelectionEditOptions;
import org.marshal.weaddon_lightposition.selection.FaceDrag;
import org.marshal.weaddon_lightposition.selection.SelectionEditKey;
import org.marshal.weaddon_lightposition.selection.SelectionEditMode;
import org.marshal.weaddon_lightposition.selection.SelectionEditSnapshot;
import org.marshal.weaddon_lightposition.selection.SelectionCarry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class SelectionEditController {
    private static final SelectionEditController INSTANCE = new SelectionEditController();
    private final SelectionEditRenderer renderer = new SelectionEditRenderer();
    private final SelectionEditKey keyGesture = new SelectionEditKey();
    private SelectionEditMode mode = SelectionEditMode.RESIZE;
    private FaceDrag faceDrag;
    private SelectionEditOptions options = SelectionEditOptions.defaults();
    private SelectionEditSource source;
    private KeyBinding editKey, fastKey;
    private Path settings;
    private SelectionEditSnapshot origin, shown;
    private CuboidDraft draft, acknowledged, dragOrigin;
    private Face selected = Face.UP, locked;
    private Corner lockedCorner;
    private Point cornerDragStart;
    private SelectionCarry carry;
    private Point carryAnchor;
    private SelectionEditRenderer.View dragView, editView;
    private double dragStart, scrollRemainder;
    private boolean active, dragging, directCorner, pending, reading, radialOpen, dragArmed;
    private double pressX, pressY, radialX, radialY, radialMouseX, radialMouseY;
    private long menuAppeared;
    private int blockedButtons, ticks, epoch, ackTicks, commitSerial, lastReadTick = -4;
    private Object level, player;
    private long appeared;
    private int dragWidth, dragHeight;
    private Object dragCamera, dragCameraEntity;
    private String reason = "";

    private SelectionEditController() { }
    public static SelectionEditController instance() { return INSTANCE; }
    public SelectionEditOptions options() { return options; }
    public boolean available() { return source != null && MinecraftClient.getInstance().getServer() != null; }
    public boolean active() {
        if (!active && !pending) return false;
        MinecraftClient client = MinecraftClient.getInstance();
        return client.world == level && client.player == player && client.currentScreen == null && client.isWindowFocused();
    }
    public boolean dragging() { return dragging; }
    public boolean cursorDrag() { return (dragging || dragArmed) && !directCorner && mode == SelectionEditMode.RESIZE; }
    public boolean capturesCamera() { return (active && !directCorner && mode == SelectionEditMode.RESIZE || radialOpen) && inputActive(); }
    public boolean inputActive() {
        if (!active && !pending && !radialOpen) return false;
        MinecraftClient client = MinecraftClient.getInstance();
        return client.world == level && client.player == player && client.currentScreen == null && client.isWindowFocused();
    }
    public SelectionEditMode mode() { return mode; }
    public boolean radialOpen() { return radialOpen; }
    public boolean hidesCrosshair() { return (active || radialOpen) && usable(MinecraftClient.getInstance()); }
    public boolean visible() { return usable(MinecraftClient.getInstance()) && (active() || shown != null); }
    public boolean suppressSneak() { return capturesCamera() && fastKey != null && fastKey.isPressed(); }
    public boolean pending() { return pending; }
    public CuboidDraft draft() { return draft; }
    public Face target() { return locked != null ? locked : selected; }
    public String reason() { return reason; }
    public void keys(KeyBinding edit, KeyBinding fast) { editKey = edit; fastKey = fast; }
    public Text bindingName(boolean fast) { return (fast ? fastKey : editKey).getBoundKeyLocalizedText(); }
    public void bind(boolean fast, net.minecraft.client.input.KeyInput event) {
        (fast ? fastKey : editKey).setBoundKey(event == null ? net.minecraft.client.util.InputUtil.UNKNOWN_KEY : net.minecraft.client.util.InputUtil.fromKeyCode(event));
        KeyBinding.updateKeysByCode();
        MinecraftClient.getInstance().options.write();
    }

    public void initialize(Path directory) {
        net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.replaceElement(
                net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.CROSSHAIR,
                original -> (graphics, delta) -> { if (!hidesCrosshair()) original.render(graphics, delta); });
        net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.attachElementAfter(
                net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.CHAT,
                net.minecraft.util.Identifier.of("worldedit_selection_overlay", "selection_edit"), (graphics, delta) -> renderHud(graphics));
        settings = directory.resolve("selection-editing.properties");
        if (Files.isRegularFile(settings)) {
            Properties p = new Properties();
            try (var input = Files.newInputStream(settings)) {
                p.load(input); options = SelectionEditOptions.load(p);
                mode = SelectionEditMode.load(p.getProperty("mode"));
            }
            catch (java.io.IOException ex) { log().warn("Could not load selection editing settings.", ex); }
        }
        if (!FabricLoader.getInstance().isModLoaded("worldedit")) return;
        try { source = (SelectionEditSource) Class.forName("org.marshal.weaddon_lightposition.client.selection.WorldEditSelectionEditSource").getDeclaredConstructor().newInstance(); }
        catch (ReflectiveOperationException | LinkageError ex) { log().warn("Local selection editing is unavailable.", ex); }
    }
    private static org.slf4j.Logger log() { return org.slf4j.LoggerFactory.getLogger("worldedit_selection_overlay/selection-edit"); }
    public void configure(boolean enabled, int step, int fast) {
        SelectionEditOptions next = new SelectionEditOptions(enabled, step, fast);
        if (options.equals(next)) return;
        options = next;
        if (!enabled) { cancel(); shown = null; }
        saveSettings();
    }
    private void saveSettings() {
        if (settings == null) return;
        try {
            Files.createDirectories(settings.getParent());
            Properties values = options.properties(); values.setProperty("mode", mode.key);
            try (var output = Files.newOutputStream(settings)) { values.store(output, "WorldEdit selection editing"); }
        } catch (java.io.IOException ex) { log().warn("Could not save selection editing settings.", ex); }
    }

    public void tick(MinecraftClient client) {
        ticks++;
        boolean held = editKey != null && editKey.isPressed();
        if (client.world != level || client.player != player) {
            pending = false; ++commitSerial; cancel();
            level = client.world; player = client.player; acknowledged = null; shown = null; lastReadTick = ticks - 4;
        }
        if (acknowledged != null && --ackTicks <= 0) acknowledged = null;
        if (!usable(client)) {
            if (active || radialOpen || reading) cancel();
            keyGesture.cancel(held); shown = null; return;
        }
        if ((dragging || capturesCamera()) && (client.getWindow().getWidth() != dragWidth || client.getWindow().getHeight() != dragHeight
                || client.options.getPerspective() != dragCamera || client.getCameraEntity() != dragCameraEntity)) { cancel(); return; }
        pollKey(client);
        if (pending) return;
        if (!reading && ticks - lastReadTick >= 4) read(client);
        followCarry();
        if (capturesCamera()) client.player.setVelocity(net.minecraft.util.math.Vec3d.ZERO);
    }

    /** Native press/release callbacks also catch taps shorter than a client tick. */
    public void keyEvent() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (usable(client)) pollKey(client);
        else keyGesture.cancel(editKey != null && editKey.isPressed());
    }
    private void pollKey(MinecraftClient client) {
        boolean held = editKey != null && editKey.isPressed();
        if (editKey != null) while (editKey.wasPressed()) { }
        if (pending || directCorner) { keyGesture.cancel(held); return; }
        switch (keyGesture.update(held, System.nanoTime())) {
            case OPEN_MENU -> { if (!dragging && !dragArmed) openMenu(client); }
            case CLOSE_MENU -> closeMenu(client);
            case TOGGLE -> {
                if (active) {
                    followCarry();
                    if (cursorDrag()) move(client.mouse.getX(), client.mouse.getY());
                    dragging = dragArmed = false; restoreCursor(); submit();
                } else begin(client, false);
            }
            default -> { }
        }
    }

    private void openMenu(MinecraftClient client) {
        radialOpen = true; radialX = radialY = 0; menuAppeared = System.nanoTime();
        if (!active || mode == SelectionEditMode.MOVE) editView = renderer.view(client);
        captureContext(client); clearConstruction(client);
        client.mouse.unlockCursor();
        GLFW.glfwSetInputMode(client.getWindow().getHandle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
        double[] x = {0}, y = {0};
        GLFW.glfwGetCursorPos(client.getWindow().getHandle(), x, y);
        radialMouseX = x[0]; radialMouseY = y[0];
    }
    private void closeMenu(MinecraftClient client) {
        if (!radialOpen) return;
        var chosen = SelectionEditMode.pick(radialX, radialY, menuRadius(client));
        radialOpen = false;
        if (chosen != null && chosen != mode) { mode = chosen; locked = null; lockedCorner = null; saveSettings(); }
        if (active && mode == SelectionEditMode.RESIZE) {
            editView = renderer.view(client);
            GLFW.glfwSetInputMode(client.getWindow().getHandle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        }
        else { editView = null; restoreCursor(); }
    }
    private static int menuRadius(MinecraftClient client) {
        return Math.max(16, Math.min(76, Math.min(client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight()) / 2 - 10));
    }

    private boolean usable(MinecraftClient client) {
        return options.enabled() && source != null && client.player != null && client.world != null
                && client.world == level && client.player == player && client.getServer() != null
                && client.currentScreen == null && client.isWindowFocused();
    }
    private void begin(MinecraftClient client, boolean direct) {
        if (shown == null || !shown.ready()) return;
        active = true; appeared = System.nanoTime(); reason = ""; origin = shown;
        draft = origin == null ? null : origin.bounds(); locked = null; lockedCorner = null;
        directCorner = direct; scrollRemainder = 0; clearConstruction(client);
        dragging = dragArmed = false; faceDrag = null; carry = null; carryAnchor = null;
        if (!direct) {
            editView = renderer.view(client);
            captureContext(client);
            if (mode == SelectionEditMode.RESIZE) client.mouse.unlockCursor();
        }
    }

    private void read(MinecraftClient client) {
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
        if (!inputActive()) return false;
        if (active && !radialOpen && !pending && mode == SelectionEditMode.MOVE) {
            if (carry == null || !dragging) return false;
            followCarry();
            draft = carry.scroll(amount, options.step());
            return true;
        }
        if (radialOpen || !active || pending || dragging || dragArmed || draft == null || !Double.isFinite(amount)) return true;
        scrollRemainder += amount;
        long notches = (long) scrollRemainder;
        scrollRemainder -= notches;
        if (notches == 0) return true;
        int step = fastKey != null && fastKey.isPressed() ? options.fastStep() : options.step();
        locked = selected; lockedCorner = null;
        if (notches > 0 ? !origin.expand() : !origin.contract()) return true;
        draft = draft.resize(locked, Math.clamp(notches, -1000L, 1000L) * step, origin.minY(), origin.maxY());
        return true;
    }

    public boolean button(int button, int action) {
        if ((active || radialOpen) && !inputActive()) cancel();
        if (button < 0 || button > 7) return active();
        int bit = 1 << button;
        boolean blocked = (blockedButtons & bit) != 0;
        if (action == GLFW.GLFW_RELEASE) {
            blockedButtons &= ~bit;
            if (button == 0 && (dragging || dragArmed)) {
                followCarry();
                if (directCorner) followCorner(renderer.view(MinecraftClient.getInstance()));
                boolean direct = directCorner;
                dragging = dragArmed = false; faceDrag = null; carry = null; carryAnchor = null;
                clearConstruction(MinecraftClient.getInstance());
                if (direct) submit();
                return true;
            }
            // Releases of buttons held before editing must still clear vanilla's mouse flags.
            return blocked;
        }
        if (blocked || inputActive()) {
            blockedButtons |= bit;
            if (button == 0 && !radialOpen && active && !pending && !dragging && !dragArmed && draft != null) choose();
            return true;
        }
        if (button == 0 && action == GLFW.GLFW_PRESS && visible() && !pending && shown != null) {
            SelectionEditRenderer.View view = renderer.view(MinecraftClient.getInstance());
            Corner corner = renderer.cornerHit(shown.bounds(), view);
            if (corner != null) {
                begin(MinecraftClient.getInstance(), true);
                if (beginCorner(corner, view, true)) { blockedButtons |= bit; return true; }
                cancel();
            }
        }
        return false;
    }

    private void choose() {
        MinecraftClient client = MinecraftClient.getInstance();
        SelectionEditRenderer.View view = mode == SelectionEditMode.MOVE ? renderer.view(client) : editView;
        Point ray = editingRay(view);
        if (mode == SelectionEditMode.MOVE) {
            if (!origin.shift()) return;
            Point anchor = renderer.moveHit(draft, view);
            if (anchor == null) return;
            try { carry = new SelectionCarry(draft, playerRoot(client), view.forward(), origin.minY(), origin.maxY()); }
            catch (IllegalArgumentException ex) { return; }
            carryAnchor = anchor.subtract(new Point(draft.minX(), draft.minY(), draft.minZ()));
            locked = null; lockedCorner = null; faceDrag = null; dragOrigin = draft;
            capture(view); return;
        }
        Corner corner = renderer.cornerHit(draft, view, ray);
        if (corner != null) { beginCorner(corner, view, false); return; }
        Face handle = renderer.handleHit(draft, view, ray);
        Face face = handle != null ? handle : draft.intersect(view.eye(), ray);
        if (face == null) return;
        selected = face; locked = face; lockedCorner = null;
        Point anchor = handle == null ? facePoint(view, ray, face) : draft.center(face);
        faceDrag = FaceDrag.capture(view.eye(), view.forward(), view.right(), view.up(),
                anchor, face, view.tangent(), view.pixelsHigh(), client.mouse.getX(), client.mouse.getY());
        dragOrigin = draft; carry = null; carryAnchor = null;
        if (handle == null) arm(view); else capture(view);
    }
    private Point facePoint(SelectionEditRenderer.View view, Point ray, Face face) {
        double distance = (draft.center(face).at(face.axis) - view.eye().at(face.axis)) / ray.at(face.axis);
        return view.eye().add(ray.scale(Math.max(0, distance)));
    }
    private void arm(SelectionEditRenderer.View view) {
        MinecraftClient client = MinecraftClient.getInstance();
        pressX = client.mouse.getX(); pressY = client.mouse.getY();
        dragView = view; dragArmed = true; captureContext(client);
    }
    private boolean beginCorner(Corner corner, SelectionEditRenderer.View view, boolean direct) {
        double distance = draft.corner(corner).subtract(view.eye()).dot(view.forward());
        Point start = direct ? CuboidDraft.rayPoint(view.eye(), view.forward(), distance)
                : CuboidDraft.planePoint(view.eye(), editingRay(view), draft.corner(corner), view.forward());
        if (start == null) return false;
        lockedCorner = corner; locked = null; cornerDragStart = start; directCorner = direct; dragOrigin = draft;
        dragArmed = false; faceDrag = null; carry = null; carryAnchor = null;
        if (direct) dragStart = distance;
        capture(view); return true;
    }
    private void capture(SelectionEditRenderer.View view) {
        MinecraftClient client = MinecraftClient.getInstance();
        dragView = view; dragging = true;
        captureContext(client);
    }
    private void captureContext(MinecraftClient client) {
        dragWidth = client.getWindow().getWidth(); dragHeight = client.getWindow().getHeight();
        dragCamera = client.options.getPerspective();
        dragCameraEntity = client.getCameraEntity();
    }
    private Point editingRay(SelectionEditRenderer.View view) {
        MinecraftClient client = MinecraftClient.getInstance();
        return directCorner || mode == SelectionEditMode.MOVE ? view.forward() : view.ray(client.mouse.getX() / client.getWindow().getWidth(),
                client.mouse.getY() / client.getWindow().getHeight());
    }
    public void move(double x, double y) {
        if (!Double.isFinite(x) || !Double.isFinite(y)) return;
        if (radialOpen && inputActive()) {
            MinecraftClient client = MinecraftClient.getInstance();
            double nextX = radialX + (x - radialMouseX) * client.getWindow().getScaledWidth() / client.getWindow().getWidth();
            double nextY = radialY + (y - radialMouseY) * client.getWindow().getScaledHeight() / client.getWindow().getHeight();
            radialMouseX = x; radialMouseY = y;
            if (!Double.isFinite(nextX) || !Double.isFinite(nextY)) return;
            radialX = nextX; radialY = nextY;
            double length = Math.hypot(radialX, radialY), maximum = menuRadius(client) - 5;
            if (length > maximum) {
                radialX *= maximum / length; radialY *= maximum / length;
            }
            return;
        }
        if (!cursorDrag() || draft == null || !active()) return;
        if (dragArmed) {
            if (faceDrag != null ? !faceDrag.moved(x, y) : Math.hypot(x - pressX, y - pressY) < 4) return;
            dragArmed = false; dragging = true;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        Point ray = dragView.ray(x / client.getWindow().getWidth(), y / client.getWindow().getHeight());
        int step = fastKey != null && fastKey.isPressed() ? options.fastStep() : options.step();
        CuboidDraft next;
        if (lockedCorner != null) {
            Point position = CuboidDraft.planePoint(dragView.eye(), ray, dragOrigin.corner(lockedCorner), dragView.forward());
            if (position == null) return;
            updateCorner(position); return;
        } else {
            if (faceDrag == null) return;
            double distance = faceDrag.outward(x, y);
            if (!Double.isFinite(distance)) return;
            long offset = CuboidDraft.snappedOffset(distance, step, 1);
            next = dragOrigin.resize(locked, offset, origin.minY(), origin.maxY());
            if (!permitted(next, locked)) return;
        }
        draft = next;
    }
    private static Point playerRoot(MinecraftClient client) {
        return new Point(client.player.getX(), client.player.getY(), client.player.getZ());
    }
    private void followCarry() {
        if (carry != null && dragging && active()) draft = carry.follow(playerRoot(MinecraftClient.getInstance()));
    }
    private void followCorner(SelectionEditRenderer.View view) {
        updateCorner(CuboidDraft.rayPoint(view.eye(), view.forward(), dragStart));
    }
    private void updateCorner(Point position) {
        if (position == null || lockedCorner == null || origin == null || cornerDragStart == null) return;
        int step = directCorner ? 1 : (fastKey != null && fastKey.isPressed() ? options.fastStep() : options.step());
        CuboidDraft next = dragOrigin.moveCorner(lockedCorner, position.subtract(cornerDragStart), step, origin.minY(), origin.maxY());
        for (int axis = 0; axis < 3; axis++) if (!permitted(next, lockedCorner.face(axis))) return;
        if (!next.equals(draft)) draft = next;
    }
    private boolean permitted(CuboidDraft next, Face face) {
        long delta = ((long) next.boundary(face) - origin.bounds().boundary(face)) * face.sign;
        return delta == 0 || (delta > 0 ? origin.expand() : origin.contract());
    }

    private void submit() {
        if (!active || pending) return;
        if (origin == null || draft == null || draft.equals(origin.bounds())) { cancel(); return; }
        active = false; pending = true; reading = false; ++epoch;
        CuboidDraft committed = draft;
        Object submittedLevel = level;
        int submittedSerial = ++commitSerial;
        source.commit(MinecraftClient.getInstance(), origin, committed, locked, lockedCorner, value -> {
            if (submittedSerial != commitSerial || level != submittedLevel) return;
            pending = false;
            if (value.ready() && value.bounds().equals(committed)) { acknowledged = committed; ackTicks = 40; }
            else {
                acknowledged = null;
                reason = value.reason();
                MinecraftClient.getInstance().inGameHud.setOverlayMessage(Text.translatable("selection_edit.worldedit_selection_overlay." + reason), false);
            }
            draft = dragOrigin = null; origin = shown = null; locked = null; lockedCorner = null; directCorner = false; dragView = editView = null; cornerDragStart = carryAnchor = null; faceDrag = null; carry = null;
        });
    }
    public void cancel() {
        boolean captured = active && !directCorner && mode == SelectionEditMode.RESIZE || radialOpen, wasDragging = dragging;
        dragging = dragArmed = radialOpen = false; directCorner = false; active = false; reading = false; ++epoch;
        keyGesture.cancel(editKey != null && editKey.isPressed());
        dragView = editView = null; dragOrigin = null; cornerDragStart = carryAnchor = null; faceDrag = null; carry = null;
        if (!pending) { origin = null; draft = null; locked = null; lockedCorner = null; }
        if (captured) restoreCursor(); else if (wasDragging) clearConstruction(MinecraftClient.getInstance());
    }
    private static void restoreCursor() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen == null && client.isWindowFocused()) client.mouse.lockCursor();
        else {
            client.mouse.unlockCursor();
            // The radial uses disabled GLFW input with MinecraftClient's grab flag cleared.
            GLFW.glfwSetInputMode(client.getWindow().getHandle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        }
        clearConstruction(client);
    }
    private static void clearConstruction(MinecraftClient client) {
        for (KeyBinding key : new KeyBinding[]{client.options.attackKey, client.options.useKey, client.options.pickItemKey}) {
            key.setPressed(false); while (key.wasPressed()) { }
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
        MinecraftClient client = MinecraftClient.getInstance();
        SelectionEditRenderer.View view = capturesCamera() ? editView : renderer.view(client);
        followCarry();
        if (dragging && directCorner) followCorner(view);
        if (active() && draft != null) renderer.world(origin.bounds(), draft, lockedCorner == null ? target() : null,
                lockedCorner != null ? lockedCorner : renderer.cornerHit(draft, view, editingRay(view)), view, appeared, pending,
                !directCorner && mode == SelectionEditMode.MOVE);
        else if (shown != null) renderer.corners(shown.bounds(), renderer.cornerHit(shown.bounds(), view), view, appeared, false);
        if (active() && draft != null && !directCorner && mode == SelectionEditMode.MOVE) {
            Point anchor = carryAnchor == null ? null : carryAnchor.add(new Point(draft.minX(), draft.minY(), draft.minZ()));
            renderer.moveGrip(draft, view, anchor, carry == null ? null : carry.wheelAxis(), appeared);
        }
    }
    public void renderHud(net.minecraft.client.gui.DrawContext graphics) {
        if (active() && !radialOpen) renderer.hud(graphics, draft, target(), lockedCorner, origin, reason, pending, appeared,
                !directCorner && mode == SelectionEditMode.MOVE, carry == null ? null : carry.wheelAxis());
        if (radialOpen && inputActive()) renderer.menu(graphics, radialX, radialY, menuRadius(MinecraftClient.getInstance()), mode, menuAppeared);
    }
}
