package org.marshal.weaddon_lightposition.client.clipboard;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.marshal.weaddon_lightposition.preview.ClipboardTransformDraft;
import org.marshal.weaddon_lightposition.preview.ClipboardTransformStatus;

public final class ClipboardPreviewController {
    private static final Logger LOGGER = LoggerFactory.getLogger("worldedit_selection_overlay/clipboard");
    private static final String WORLDEDIT_SOURCE_CLASS =
            "org.marshal.weaddon_lightposition.client.clipboard.WorldEditClipboardPreviewSource";

    private final ClipboardPreviewRenderer renderer = new ClipboardPreviewRenderer();
    private ClipboardPreviewSource source = new UnavailableSource();
    private boolean initialized;
    private boolean enabled = true;
    private String transformInput = "";
    private ClipboardTransformDraft transformDraft = ClipboardTransformDraft.none();

    public void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;

        if (!FabricLoader.getInstance().isModLoaded("worldedit")) {
            LOGGER.info("WorldEdit is not loaded; clipboard preview source is unavailable.");
            return;
        }

        try {
            Object instance = Class.forName(WORLDEDIT_SOURCE_CLASS).getDeclaredConstructor().newInstance();
            if (instance instanceof ClipboardPreviewSource previewSource) {
                source = previewSource;
                LOGGER.info("WorldEdit clipboard preview source enabled.");
            }
        } catch (ReflectiveOperationException | LinkageError ex) {
            LOGGER.warn("Could not initialize WorldEdit clipboard preview source.", ex);
        }
    }

    public void tick(Minecraft client) {
        if (!enabled) {
            return;
        }
        source.tick(client);
    }

    public void renderBlocks(LevelRenderContext context) {
        if (!enabled) {
            return;
        }
        renderer.render(context, source.snapshot());
    }

    public void renderBounds() {
        if (!enabled) {
            return;
        }
        renderer.renderBounds(source.snapshot());
    }

    public void clear() {
        transformInput = "";
        transformDraft = ClipboardTransformDraft.none();
        source.clear();
        renderer.clearCache();
    }

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled) {
            return;
        }
        this.enabled = enabled;
        if (!enabled) {
            clear();
        }
    }

    public boolean available() {
        return source.available();
    }

    public boolean active() { return enabled && source.snapshot().active(); }

    public void setTransformDraft(String input) {
        if (!enabled) input = "";
        if (transformInput.equals(input)) return;
        transformInput = input;
        transformDraft = ClipboardTransformDraft.parse(input);
        source.setTransformDraft(transformDraft);
    }

    public boolean hasTransformDraft() { return enabled && transformDraft.recognized(); }
    public ClipboardTransformStatus transformStatus() { return source.transformStatus(); }

    private static final class UnavailableSource implements ClipboardPreviewSource {
        @Override
        public void tick(Minecraft client) {
        }

        @Override
        public ClipboardPreviewSnapshot snapshot() {
            return ClipboardPreviewSnapshot.empty();
        }

        @Override
        public void clear() {
        }

        @Override
        public boolean available() {
            return false;
        }
    }
}
