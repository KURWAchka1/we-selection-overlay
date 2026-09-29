package org.marshal.weaddon_lightposition.client.clipboard;

import net.minecraft.client.MinecraftClient;

public interface ClipboardPreviewSource {
    void tick(MinecraftClient client);

    ClipboardPreviewSnapshot snapshot();

    void clear();

    default boolean available() {
        return true;
    }
}
