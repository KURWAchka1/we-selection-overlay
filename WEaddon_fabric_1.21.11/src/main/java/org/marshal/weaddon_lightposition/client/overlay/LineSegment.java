package org.marshal.weaddon_lightposition.client.overlay;

import net.minecraft.util.math.Vec3d;

public record LineSegment(Vec3d from, Vec3d to, int color, float width, OverlayColorSlot slot) {
}
