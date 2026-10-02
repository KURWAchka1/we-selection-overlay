package org.marshal.weaddon_lightposition.preview;

import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.function.RegionFunction;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.function.Supplier;

/** Resumable exterior flood and Manhattan shell passes, matching EditSession.hollowOutRegion. */
final class PreviewHollowProvider implements PreviewProvider {
    static final int[][] NEIGHBORS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
    private final Region region;
    private final PreviewExtent extent;
    private final RegionFunction replace;
    private final int thickness;
    private final Supplier<Iterator<BlockVector3>> candidates;
    private final Set<BlockVector3> outside = new HashSet<>(), nextLayer = new HashSet<>();
    private final ArrayDeque<BlockVector3> queue = new ArrayDeque<>();
    private Iterator<BlockVector3> iterator;
    private int phase, layer = 1;
    private boolean checkedCoverage;

    PreviewHollowProvider(Region region, PreviewExtent extent, RegionFunction replace, int thickness,
                          Supplier<Iterator<BlockVector3>> candidates) {
        this.region = region; this.extent = extent; this.replace = replace; this.thickness = thickness;
        this.candidates = candidates; iterator = candidates.get();
    }

    public boolean hasNext() { return phase < 5; }
    public void advance() throws WorldEditException {
        if (!checkedCoverage) {
            checkedCoverage = true;
            var span = extent.getMaximumPoint().subtract(extent.getMinimumPoint()).add(1, 1, 1);
            if ((long) span.x() * span.y() * span.z() != extent.original().size()) throw new IllegalArgumentException("unloaded");
        }
        if (phase == 0) {
            if (iterator.hasNext()) {
                var p = iterator.next();
                var min = region.getMinimumPoint(); var max = region.getMaximumPoint();
                if (p.x() < min.x() || p.x() > max.x() || p.y() < min.y() || p.y() > max.y() || p.z() < min.z() || p.z() > max.z()) return;
                if (p.x() == min.x() || p.x() == max.x() || p.y() == min.y() || p.y() == max.y() || p.z() == min.z() || p.z() == max.z()) queue.addLast(p);
            } else phase = 1;
        } else if (phase == 1) {
            if (queue.isEmpty()) { phase = thickness > 1 ? 2 : 4; iterator = candidates.get(); return; }
            var p = queue.removeFirst();
            if (outside.contains(p)) return;
            if (!extent.captured(p)) throw new IllegalArgumentException("unloaded");
            if (extent.getBlock(p).getBlockType().getMaterial().isSolid()) return;
            outside.add(p);
            if (region.contains(p)) for (var d : NEIGHBORS) queue.addLast(p.add(d[0], d[1], d[2]));
        } else if (phase == 2) {
            if (iterator.hasNext()) {
                var p = iterator.next();
                if (region.contains(p) && !outside.contains(p) && touchesOutside(p)) nextLayer.add(p);
            } else {
                phase = nextLayer.isEmpty() ? 4 : 3;
                iterator = phase == 4 ? candidates.get() : nextLayer.iterator();
            }
        } else if (phase == 3) {
            if (iterator.hasNext()) outside.add(iterator.next());
            else { nextLayer.clear(); phase = ++layer >= thickness ? 4 : 2; iterator = candidates.get(); }
        } else if (iterator.hasNext()) {
            var p = iterator.next();
            if (region.contains(p) && !touchesOutside(p)) replace.apply(p);
        } else phase = 5;
    }

    private boolean touchesOutside(BlockVector3 p) {
        for (var d : NEIGHBORS) if (outside.contains(p.add(d[0], d[1], d[2]))) return true;
        return false;
    }
}
