package org.marshal.weaddon_lightposition.client.clipboard;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ClipboardPreviewController {
    private static final Logger LOGGER = LoggerFactory.getLogger("worldedit_selection_overlay/clipboard");
    private static final String WORLDEDIT_SOURCE_CLASS =
            "org.marshal.weaddon_lightposition.client.clipboard.WorldEditClipboardPreviewSource";

    private final ClipboardPreviewRenderer renderer = new ClipboardPreviewRenderer();
    private ClipboardPreviewSource source = new UnavailableSource();
    private boolean initialized;
    private boolean enabled = true;

    public void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;

        if (!ModList.get().isLoaded("worldedit")) {
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
            source.clear();
            return;
        }
        source.tick(client);
    }

    public void renderBlocks(PoseStack poseStack) {
        if (!enabled) {
            return;
        }
        renderer.render(poseStack, source.snapshot());
    }

    public void renderBounds() {
        if (!enabled) {
            return;
        }
        renderer.renderBounds(source.snapshot());
    }

    public void clear() {
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

    public boolean active() {
        return enabled && source.snapshot().active();
    }

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
