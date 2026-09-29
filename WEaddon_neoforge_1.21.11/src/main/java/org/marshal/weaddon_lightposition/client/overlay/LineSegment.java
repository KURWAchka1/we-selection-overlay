package org.marshal.weaddon_lightposition.client.overlay;

import net.minecraft.world.phys.Vec3;

public record LineSegment(Vec3 from, Vec3 to, int color, float width, OverlayColorSlot slot) {
}
