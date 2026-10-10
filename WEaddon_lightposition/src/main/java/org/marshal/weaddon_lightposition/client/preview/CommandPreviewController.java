package org.marshal.weaddon_lightposition.client.preview;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import java.util.Properties;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.marshal.weaddon_lightposition.preview.PreviewOptions;
import org.marshal.weaddon_lightposition.preview.PreviewDraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.marshal.weaddon_lightposition.client.clipboard.ClipboardPreviewController;
import org.marshal.weaddon_lightposition.preview.ClipboardTransformStatus;

public final class CommandPreviewController {
    private static final Logger LOGGER = LoggerFactory.getLogger("worldedit_selection_overlay/command-preview");
    private static final CommandPreviewController INSTANCE = new CommandPreviewController();
    private static final CommandPreviewSnapshot UNAVAILABLE = CommandPreviewSnapshot.status(CommandPreviewSnapshot.State.UNAVAILABLE, "local_only", "");
    private final CommandPreviewRenderer renderer = new CommandPreviewRenderer();
    private CommandPreviewSource source;
    private ClipboardPreviewController clipboard;
    private PreviewOptions options = PreviewOptions.defaults();
    private Path settings;
    private KeyMapping toggleKey;
    private String draft = "";
    private Object lastLevel;
    private record HudLine(net.minecraft.util.FormattedCharSequence text, int color) { }
    private List<HudLine> hud = List.of();
    private Object hudSnapshot;
    private Object hudLanguage;
    private int hudWidth;

    private CommandPreviewController() { }
    public static CommandPreviewController instance() { return INSTANCE; }
    public PreviewOptions options() { return options; }
    public CommandPreviewSnapshot snapshot() {
        return source == null ? UNAVAILABLE : source.snapshot();
    }

    public void initialize(Path configDirectory) {
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(client -> close());
        settings = configDirectory.resolve("command-preview.properties");
        if (Files.isRegularFile(settings)) {
            Properties values = new Properties();
            try (var input = Files.newInputStream(settings)) { values.load(input); options = PreviewOptions.load(values); }
            catch (IOException ex) { LOGGER.warn("Could not load command preview settings; using defaults.", ex); }
        }
        if (!FabricLoader.getInstance().isModLoaded("worldedit")) return;
        try {
            source = (CommandPreviewSource) Class.forName("org.marshal.weaddon_lightposition.client.preview.WorldEditCommandPreviewSource")
                    .getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException | LinkageError ex) { LOGGER.warn("WorldEdit live command preview is unavailable.", ex); }
    }

    public void setToggleKey(KeyMapping key) { toggleKey = key; }
    public void bindClipboardPreview(ClipboardPreviewController clipboard) { this.clipboard = clipboard; }
    public boolean handleToggleKey(KeyEvent key) {
        if (toggleKey == null || !toggleKey.matches(key)) return false;
        setEnabled(!options.enabled());
        while (toggleKey.consumeClick()) { }
        return true;
    }

    public void setEnabled(boolean enabled) {
        if (options.enabled() == enabled) return;
        options = options.withEnabled(enabled);
        invalidate();
        if (settings != null) {
            try {
                Files.createDirectories(settings.getParent());
                try (var output = Files.newOutputStream(settings)) { options.properties().store(output, "WorldEdit live command preview"); }
            } catch (IOException ex) { LOGGER.warn("Could not save command preview settings.", ex); }
        }
    }

    public void edited(String value) {
        if (draft.equals(value)) return;
        draft = value;
        invalidate();
    }

    public void invalidate() {
        if (source != null) source.clear();
        CommandPreviewTerrainState.update(Minecraft.getInstance(), CommandPreviewMask.empty());
        renderer.clearGeometry();
        request(Minecraft.getInstance());
    }

    public void selectionChanged() { if (source != null) source.selectionChanged(); }

    private void request(Minecraft client) {
        if (clipboard != null) clipboard.setTransformDraft(options.enabled() && client.screen instanceof ChatScreen ? draft : "");
        if (source != null) source.tick(client, ownsPreviewChannel() ? draft : "", options);
    }

    public void clearDraft() { edited(""); }
    public void close() {
        draft = "";
        if (clipboard != null) clipboard.setTransformDraft("");
        if (source != null) source.clear();
        renderer.clear();
        CommandPreviewTerrainState.update(Minecraft.getInstance(), CommandPreviewMask.empty());
    }
    public void tick(Minecraft client) {
        if (client.level != lastLevel) { lastLevel = client.level; clearDraft(); invalidate(); renderer.clear(); }
        if (!(client.screen instanceof ChatScreen)) clearDraft();
        if (toggleKey != null && !(client.screen instanceof ChatScreen)) {
            while (toggleKey.consumeClick()) setEnabled(!options.enabled());
        }
        request(client);
        CommandPreviewTerrainState.update(client, active() ? snapshot().terrain() : CommandPreviewMask.empty());
        CommandPreviewTerrainState.flush(client);
        if (!active()) renderer.clearGeometry();
    }

    public void renderBlocks(LevelRenderContext context) {
        if (active()) renderer.render(context, snapshot(), options);
    }
    public void renderMarkers() {
        if (active()) renderer.renderMarkers(snapshot(), options);
    }
    public boolean ownsPreviewChannel() {
        Minecraft client = Minecraft.getInstance();
        return options.enabled() && recognizes(draft) && client.screen instanceof ChatScreen;
    }
    private boolean active() { return ownsPreviewChannel() && snapshot().state() == CommandPreviewSnapshot.State.READY; }
    private static boolean recognizes(String input) {
        return PreviewDraft.recognizes(input);
    }

    public void renderHud(GuiGraphicsExtractor graphics) {
        Minecraft client = Minecraft.getInstance();
        boolean clipboardChannel = options.enabled() && clipboard != null && clipboard.hasTransformDraft()
                && client.screen instanceof ChatScreen;
        if (!ownsPreviewChannel() && !clipboardChannel) return;
        Object s = clipboardChannel ? clipboard.transformStatus() : snapshot();
        int x = 8, y = 8, width = Math.min(360, client.getWindow().getGuiScaledWidth() - 16);
        if (width < 48) return;
        Object language = net.minecraft.locale.Language.getInstance();
        if (hudSnapshot != s || hudWidth != width || hudLanguage != language) {
            hudSnapshot = s; hudWidth = width; hudLanguage = language;
            hud = clipboardChannel ? clipboardHudLines(client, (ClipboardTransformStatus) s, width - 16)
                    : hudLines(client, (CommandPreviewSnapshot) s, width - 16);
        }
        int count = Math.min(hud.size(), Math.max(2, (client.getWindow().getGuiScaledHeight() - 60) / 11));
        graphics.fill(x, y, x + width, y + count * 11 + 12, 0xC922262A);
        graphics.enableScissor(x, y, x + width, y + count * 11 + 12);
        for (int i = 0; i < count; i++) graphics.text(client.font, hud.get(i).text(), x + 8, y + 6 + i * 11, hud.get(i).color(), false);
        graphics.disableScissor();
    }

    private List<HudLine> clipboardHudLines(Minecraft client, ClipboardTransformStatus status, int width) {
        ArrayList<HudLine> lines = new ArrayList<>();
        addHudLine(client, lines, Component.translatable("preview.worldedit_selection_overlay.clipboard_title"), width, 0xFFB9E8D3);
        addHudLine(client, lines, Component.translatable("preview.worldedit_selection_overlay." + status.reason()), width, 0xFFF0F1F3);
        if (status.approximate()) addHudLine(client, lines,
                Component.translatable("preview.worldedit_selection_overlay.clipboard_no_interpolation"), width, 0xFFFFD275);
        return List.copyOf(lines);
    }

    private List<HudLine> hudLines(Minecraft client, CommandPreviewSnapshot s, int width) {
        ArrayList<HudLine> lines = new ArrayList<>();
        Component heading = Component.translatable("preview.worldedit_selection_overlay.title");
        Component summary = switch (s.state()) {
            case IDLE -> Component.translatable("preview.worldedit_selection_overlay.waiting");
            case COMPUTING -> Component.translatable("preview.worldedit_selection_overlay.computing", s.scanned(), s.total());
            case READY -> Component.translatable("preview.worldedit_selection_overlay.counts",
                    s.statistics().added(), s.statistics().replaced(), s.statistics().removed());
            default -> Component.translatable("preview.worldedit_selection_overlay." + s.reason());
        };
        addHudLine(client, lines, heading, width, 0xFFB9E8D3);
        addHudLine(client, lines, summary, width, 0xFFF0F1F3);
        if (s.random()) addHudLine(client, lines, Component.translatable("preview.worldedit_selection_overlay.random"), width, 0xFFFFD275);
        if (s.state() == CommandPreviewSnapshot.State.READY) addHudLine(client, lines,
                Component.translatable("preview.worldedit_selection_overlay.loaded_scope"), width, 0xFFC4CAD0);
        if (options.statistics() && s.state() == CommandPreviewSnapshot.State.READY) {
            for (int i = 0; i < Math.min(3, s.statistics().transitions().size()); i++) {
                var transition = s.statistics().transitions().get(i);
                String label = transition.before().replace("minecraft:", "") + " -> "
                        + transition.after().replace("minecraft:", "") + ": " + transition.count();
                addHudLine(client, lines, Component.literal(label), width, 0xFFC4CAD0);
            }
        }
        return List.copyOf(lines);
    }

    private static void addHudLine(Minecraft client, List<HudLine> lines, Component text, int width, int color) {
        for (var line : client.font.split(text, width)) lines.add(new HudLine(line, color));
    }
}
