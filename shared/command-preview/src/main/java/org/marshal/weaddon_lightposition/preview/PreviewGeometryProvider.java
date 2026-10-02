package org.marshal.weaddon_lightposition.preview;

import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.function.RegionFunction;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.math.interpolation.KochanekBartelsInterpolation;
import com.sk89q.worldedit.math.interpolation.Node;
import com.sk89q.worldedit.regions.ConvexPolyhedralRegion;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/** Official spline interpolation; incremental rasterization, dilation and shell filtering. */
final class PreviewGeometryProvider implements PreviewProvider {
    private final PreviewCommand command;
    private final PreviewExtent extent;
    private final RegionFunction replace;
    private final List<BlockVector3> vertices;
    private final Set<BlockVector3> path = new HashSet<>(), volume = new HashSet<>();
    private KochanekBartelsInterpolation spline;
    private double t, dt;
    private int segment, step, phase;
    private Iterator<BlockVector3> iterator;
    private BlockVector3 center;
    private CuboidRegion offsets;
    private Iterator<BlockVector3> offset;

    PreviewGeometryProvider(PreviewCommand command, Region region, PreviewExtent extent, RegionFunction replace) {
        this.command = command; this.extent = extent; this.replace = replace;
        if (command.kind() == PreviewCommand.Kind.LINE && region instanceof CuboidRegion cuboid)
            vertices = List.of(cuboid.getPos1(), cuboid.getPos2());
        else if (region instanceof ConvexPolyhedralRegion convex) vertices = List.copyOf(convex.getVertices());
        else throw new IllegalArgumentException("invalid");
        if (vertices.size() < 2) throw new IllegalArgumentException("invalid");
    }

    public boolean hasNext() { return phase < 3; }
    public void advance() throws WorldEditException {
        if (phase == 0) {
            if (command.kind() == PreviewCommand.Kind.CURVE) sampleSpline(); else sampleLine();
        } else if (phase == 1) {
            if (offset == null || !offset.hasNext()) {
                if (!iterator.hasNext()) { iterator = volume.iterator(); phase = 2; return; }
                center = iterator.next();
                int r = (int) Math.ceil(command.thickness());
                var min = extent.getMinimumPoint().subtract(center);
                var max = extent.getMaximumPoint().subtract(center);
                if (min.x() > r || min.y() > r || min.z() > r || max.x() < -r || max.y() < -r || max.z() < -r) return;
                offsets = new CuboidRegion(BlockVector3.at(Math.max(-r, min.x()), Math.max(-r, min.y()), Math.max(-r, min.z())),
                        BlockVector3.at(Math.min(r, max.x()), Math.min(r, max.y()), Math.min(r, max.z())));
                offset = offsets.iterator();
            }
            var d = offset.next();
            if ((double) d.x() * d.x() + (double) d.y() * d.y() + (double) d.z() * d.z() <= command.thickness() * command.thickness())
                volume.add(center.add(d));
        } else if (iterator.hasNext()) {
            var p = iterator.next();
            if (extent.captured(p) && (!command.shell() || surface(p))) replace.apply(p);
        } else phase = 3;
    }

    private void sampleLine() {
        if (segment >= vertices.size() - 1) { dilate(); return; }
        var a = vertices.get(segment); var b = vertices.get(segment + 1);
        int length = Math.max(Math.abs(b.x() - a.x()), Math.max(Math.abs(b.y() - a.y()), Math.abs(b.z() - a.z())));
        double progress = length == 0 ? 0 : (double) step / length;
        path.add(BlockVector3.at(Math.round(a.x() + (b.x() - a.x()) * progress),
                Math.round(a.y() + (b.y() - a.y()) * progress), Math.round(a.z() + (b.z() - a.z()) * progress)));
        if (++step > length) { segment++; step = 0; }
    }

    private void sampleSpline() {
        if (spline == null) {
            spline = new KochanekBartelsInterpolation();
            spline.setNodes(vertices.stream().map(v -> new Node(v.toVector3().add(Vector3.at(0.5, 0.5, 0.5)))).toList());
            double length = spline.arcLength(0, 1);
            dt = length == 0 ? Double.POSITIVE_INFINITY : 1 / length / 10;
        }
        if (t > 1) { dilate(); return; }
        path.add(spline.getPosition(t).toBlockPoint()); t += dt;
    }

    private void dilate() { iterator = path.iterator(); phase = 1; }
    private boolean surface(BlockVector3 p) {
        for (var d : PreviewHollowProvider.NEIGHBORS) if (!volume.contains(p.add(d[0], d[1], d[2]))) return true;
        return false;
    }
}
