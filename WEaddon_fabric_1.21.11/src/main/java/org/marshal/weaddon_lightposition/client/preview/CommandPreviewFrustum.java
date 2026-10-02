package org.marshal.weaddon_lightposition.client.preview;

import net.minecraft.client.render.Frustum;

public final class CommandPreviewFrustum {
    private static Frustum current;
    private CommandPreviewFrustum() { }
    public static void capture(Frustum frustum) { current = frustum; }
    public static Frustum current() { return current; }
}
