package org.marshal.weaddon_lightposition.client.preview;

import net.minecraft.client.Minecraft;
import org.marshal.weaddon_lightposition.preview.PreviewOptions;

public interface CommandPreviewSource {
    void tick(Minecraft client, String draft, PreviewOptions options);
    CommandPreviewSnapshot snapshot();
    void clear();
}
