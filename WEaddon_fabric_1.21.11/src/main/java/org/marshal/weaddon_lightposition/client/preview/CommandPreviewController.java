package org.marshal.weaddon_lightposition.client.preview;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import java.util.Properties;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.marshal.weaddon_lightposition.preview.PreviewOptions;
import org.marshal.weaddon_lightposition.preview.PreviewDraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CommandPreviewController {
    private static final Logger LOGGER = LoggerFactory.getLogger("worldedit_selection_overlay/command-preview");
    private static final CommandPreviewController INSTANCE = new CommandPreviewController();
    private static final CommandPreviewSnapshot UNAVAILABLE = CommandPreviewSnapshot.status(CommandPreviewSnapshot.State.UNAVAILABLE, "local_only", "");
    private final CommandPreviewRenderer renderer = new CommandPreviewRenderer();
    private CommandPreviewSource source;
    private PreviewOptions options = PreviewOptions.defaults();
    private Path settings;
    private KeyBinding toggleKey;
    private String draft = "";
    private long editedAt;
    private Object lastLevel;
    private record HudLine(net.minecraft.text.OrderedText text, int color) { }
    private List<HudLine> hud = List.of();
    private CommandPreviewSnapshot hudSnapshot;
    private Object hudLanguage;
    private int hudWidth;

    private CommandPreviewController() { }
    public static CommandPreviewController instance() { return INSTANCE; }
    public PreviewOptions options() { return options; }
    public CommandPreviewSnapshot snapshot() {
        return source == null ? UNAVAILABLE : source.snapshot();
    }

    public void initialize(Path configDirectory) {
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

    public void setToggleKey(KeyBinding key) { toggleKey = key; }
    public boolean handleToggleKey(KeyInput key) {
        if (toggleKey == null || !toggleKey.matchesKey(key)) return false;
        setEnabled(!options.enabled());
        while (toggleKey.wasPressed()) { }
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
        editedAt = System.nanoTime();
        if (source != null) source.clear();
        renderer.clear();
    }

    public void clearDraft() { edited(""); }
    public void tick(MinecraftClient client) {
        if (client.world != lastLevel) { lastLevel = client.world; clearDraft(); invalidate(); }
        if (!(client.currentScreen instanceof ChatScreen)) clearDraft();
        if (toggleKey != null && !(client.currentScreen instanceof ChatScreen)) {
            while (toggleKey.wasPressed()) setEnabled(!options.enabled());
        }
        if (source == null) return;
        boolean ready = options.enabled() && recognizes(draft)
                && System.nanoTime() - editedAt >= options.debounceMillis() * 1_000_000L;
        source.tick(client, ready ? draft : "", options);
    }

    public void renderBlocks(WorldRenderContext context) {
        if (active()) renderer.render(context, snapshot(), options);
    }
    public void renderMarkers() {
        if (active()) renderer.renderMarkers(snapshot(), options);
    }
    public boolean ownsPreviewChannel() {
        MinecraftClient client = MinecraftClient.getInstance();
        return options.enabled() && recognizes(draft) && client.currentScreen instanceof ChatScreen;
    }
    private boolean active() { return ownsPreviewChannel() && snapshot().state() == CommandPreviewSnapshot.State.READY; }
    private static boolean recognizes(String input) {
        return PreviewDraft.recognizes(input);
    }

    public void renderHud(DrawContext graphics) {
        if (!ownsPreviewChannel()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        CommandPreviewSnapshot s = snapshot();
        int x = 8, y = 8, width = Math.min(360, client.getWindow().getScaledWidth() - 16);
        if (width < 48) return;
        Object language = net.minecraft.util.Language.getInstance();
        if (hudSnapshot != s || hudWidth != width || hudLanguage != language) {
            hudSnapshot = s; hudWidth = width; hudLanguage = language;
            hud = hudLines(client, s, width - 16);
        }
        int count = Math.min(hud.size(), Math.max(2, (client.getWindow().getScaledHeight() - 60) / 11));
        graphics.fill(x, y, x + width, y + count * 11 + 12, 0xC922262A);
        graphics.enableScissor(x, y, x + width, y + count * 11 + 12);
        for (int i = 0; i < count; i++) graphics.drawText(client.textRenderer, hud.get(i).text(), x + 8, y + 6 + i * 11, hud.get(i).color(), false);
        graphics.disableScissor();
    }

    private List<HudLine> hudLines(MinecraftClient client, CommandPreviewSnapshot s, int width) {
        ArrayList<HudLine> lines = new ArrayList<>();
        Text heading = Text.translatable("preview.worldedit_selection_overlay.title");
        Text summary = switch (s.state()) {
            case IDLE -> Text.translatable("preview.worldedit_selection_overlay.waiting");
            case COMPUTING -> Text.translatable("preview.worldedit_selection_overlay.computing", s.scanned(), s.total());
            case READY -> Text.translatable("preview.worldedit_selection_overlay.counts",
                    s.statistics().added(), s.statistics().replaced(), s.statistics().removed());
            default -> Text.translatable("preview.worldedit_selection_overlay." + s.reason());
        };
        addHudLine(client, lines, heading, width, 0xFFB9E8D3);
        addHudLine(client, lines, summary, width, 0xFFF0F1F3);
        if (s.random()) addHudLine(client, lines, Text.translatable("preview.worldedit_selection_overlay.random"), width, 0xFFFFD275);
        if (s.limited()) addHudLine(client, lines, Text.translatable("preview.worldedit_selection_overlay.render_limit"), width, 0xFFFFD275);
        if (options.statistics() && s.state() == CommandPreviewSnapshot.State.READY) {
            for (int i = 0; i < Math.min(3, s.statistics().transitions().size()); i++) {
                var transition = s.statistics().transitions().get(i);
                String label = transition.before().replace("minecraft:", "") + " -> "
                        + transition.after().replace("minecraft:", "") + ": " + transition.count();
                addHudLine(client, lines, Text.literal(label), width, 0xFFC4CAD0);
            }
        }
        return List.copyOf(lines);
    }

    private static void addHudLine(MinecraftClient client, List<HudLine> lines, Text text, int width, int color) {
        for (var line : client.textRenderer.wrapLines(text, width)) lines.add(new HudLine(line, color));
    }
}
