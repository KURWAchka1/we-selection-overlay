package org.marshal.weaddon_lightposition.client.selection;

import net.neoforged.fml.ModList;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.marshal.weaddon_lightposition.client.overlay.SelectionRegion;
import org.marshal.weaddon_lightposition.selection.CuboidDraft;
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
    private SelectionEditSnapshot origin;
    private CuboidDraft draft, acknowledged;
    private Face target, locked;
    private SelectionEditRenderer.View dragView;
    private double dragStart, scrollRemainder;
    private boolean active, dragging, pending, reading, latch;
    private int blockedButtons, ticks, epoch, ackTicks, commitSerial;
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
    public boolean pending() { return pending; }
    public CuboidDraft draft() { return draft; }
    public Face target() { return locked != null ? locked : target; }
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
        if (!enabled) cancel();
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
            level = client.level; player = client.player; acknowledged = null;
        }
        if (acknowledged != null && --ackTicks <= 0) acknowledged = null;
        boolean valid = options.enabled() && source != null && client.player != null && client.level != null
                && client.getSingleplayerServer() != null && client.screen == null && client.isWindowActive();
        if (!valid) { if (active) cancel(); return; }
        if (dragging && (client.getWindow().getScreenWidth() != dragWidth || client.getWindow().getScreenHeight() != dragHeight
                || client.options.getCameraType() != dragCamera)) { cancel(); return; }
        if (active && !held) {
            if (dragging) cancel(); else submit();
            return;
        }
        if (!held || latch || pending) return;
        if (!active) {
            active = true; appeared = System.nanoTime(); reason = ""; origin = null; draft = null; locked = target = null;
            scrollRemainder = 0; clearConstruction(client); read(client);
        } else if (!reading && ticks % 4 == 0) read(client);
        if (dragging) client.player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
    }

    private void read(Minecraft client) {
        if (reading) return;
        reading = true;
        int generation = epoch;
        source.read(client, value -> {
            if (generation != epoch) return;
            reading = false;
            if (!active || pending) return;
            if (!value.ready()) { reason = value.reason(); if (origin != null) cancel(); return; }
            if (origin != null && origin != value) { reason = "stale"; cancel(); return; }
            if (origin == null) { origin = value; draft = value.bounds(); }
        });
    }

    public boolean scroll(double amount) {
        if (!active()) return false;
        if (!active || pending || dragging || draft == null || target() == null || !Double.isFinite(amount)) return true;
        scrollRemainder += amount;
        long notches = (long) scrollRemainder;
        scrollRemainder -= notches;
        if (notches == 0) return true;
        if (locked == null) locked = target;
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
            if (button == 0 && dragging) { dragging = false; restoreCursor(); submit(); return true; }
            // Releases of buttons held before editing must still clear vanilla's mouse flags.
            return blocked;
        }
        if (blocked || active()) {
            blockedButtons |= bit;
            if (button == 0 && active && !pending && !dragging && locked == null && draft != null && target != null) beginDrag();
            return true;
        }
        return false;
    }

    private void beginDrag() {
        Minecraft client = Minecraft.getInstance();
        SelectionEditRenderer.View view = renderer.view(client);
        Face handle = renderer.handleHit(draft, view);
        if (handle == null || handle != target) return;
        double start = CuboidDraft.axisParameter(view.eye(), view.forward(), draft.center(handle), handle);
        if (!Double.isFinite(start)) return;
        locked = handle; dragView = view; dragStart = start; dragging = true;
        dragWidth = client.getWindow().getScreenWidth(); dragHeight = client.getWindow().getScreenHeight();
        dragCamera = client.options.getCameraType();
        client.mouseHandler.releaseMouse();
    }
    public void move(double x, double y) {
        if (!dragging || draft == null || !active()) return;
        Minecraft client = Minecraft.getInstance();
        Point ray = dragView.ray(x / client.getWindow().getScreenWidth(), y / client.getWindow().getScreenHeight());
        double parameter = CuboidDraft.axisParameter(dragView.eye(), ray, origin.bounds().center(locked), locked);
        if (!Double.isFinite(parameter)) return;
        int step = fastKey != null && fastKey.isDown() ? options.fastStep() : options.step();
        long offset = Math.round((parameter - dragStart) / step) * step * locked.sign;
        if (offset > 0 ? !origin.expand() : !origin.contract()) return;
        draft = origin.bounds().resize(locked, offset, origin.minY(), origin.maxY());
    }

    private void submit() {
        if (!active || pending) return;
        if (origin == null || locked == null || draft.equals(origin.bounds())) { cancel(); return; }
        active = false; pending = true; latch = true; reading = false; ++epoch;
        CuboidDraft committed = draft;
        Object submittedLevel = level;
        int submittedSerial = ++commitSerial;
        source.commit(Minecraft.getInstance(), origin, committed, locked, value -> {
            if (submittedSerial != commitSerial || level != submittedLevel) return;
            pending = false;
            if (value.ready() && value.bounds().equals(committed)) { acknowledged = committed; ackTicks = 40; }
            else {
                acknowledged = null;
                reason = value.reason();
                Minecraft.getInstance().gui.setOverlayMessage(Component.translatable("selection_edit.worldedit_selection_overlay." + reason), false);
            }
            draft = null; origin = null; locked = target = null;
        });
    }
    public void cancel() {
        boolean wasDragging = dragging;
        dragging = false; active = false; reading = false; latch = true; ++epoch;
        if (!pending) { origin = null; draft = null; locked = target = null; }
        if (wasDragging) restoreCursor();
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
        if (!active() || draft == null) return;
        Minecraft client = Minecraft.getInstance();
        SelectionEditRenderer.View view = dragging ? dragView : renderer.view(client);
        if (!dragging && locked == null) {
            Face handle = renderer.handleHit(draft, view);
            target = handle != null ? handle : draft.intersect(view.eye(), view.forward());
        }
        renderer.world(origin.bounds(), draft, target(), view, appeared, pending);
    }
    public void renderHud(net.minecraft.client.gui.GuiGraphics graphics) {
        if (active()) renderer.hud(graphics, draft, target(), origin, reason, pending, appeared);
    }
}
