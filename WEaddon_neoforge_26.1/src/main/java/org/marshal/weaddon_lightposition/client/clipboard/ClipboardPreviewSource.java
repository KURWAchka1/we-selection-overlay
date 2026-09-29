package org.marshal.weaddon_lightposition.client.clipboard;

import net.minecraft.client.Minecraft;

public interface ClipboardPreviewSource {
    void tick(Minecraft client);

    ClipboardPreviewSnapshot snapshot();

    void clear();

    default boolean available() {
        return true;
    }
}
