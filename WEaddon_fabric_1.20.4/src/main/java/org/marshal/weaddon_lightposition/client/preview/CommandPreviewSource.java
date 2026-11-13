package org.marshal.weaddon_lightposition.client.preview;

import net.minecraft.client.MinecraftClient;
import org.marshal.weaddon_lightposition.preview.PreviewOptions;

public interface CommandPreviewSource {
    void tick(MinecraftClient client, String draft, PreviewOptions options);
    CommandPreviewSnapshot snapshot();
    void clear();
    void selectionChanged();
}
