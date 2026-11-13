package org.marshal.weaddon_lightposition.client.clipboard;

import net.minecraft.client.MinecraftClient;
import org.marshal.weaddon_lightposition.preview.ClipboardTransformDraft;
import org.marshal.weaddon_lightposition.preview.ClipboardTransformStatus;

public interface ClipboardPreviewSource {
    void tick(MinecraftClient client);

    ClipboardPreviewSnapshot snapshot();

    void clear();

    default void setTransformDraft(ClipboardTransformDraft draft) { }

    default ClipboardTransformStatus transformStatus() { return ClipboardTransformStatus.status("local_only"); }

    default boolean available() {
        return true;
    }
}
