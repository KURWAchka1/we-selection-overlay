package org.marshal.weaddon_lightposition.selection.worldedit;

import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.RegionSelector;
import com.sk89q.worldedit.regions.selector.CuboidRegionSelector;
import com.sk89q.worldedit.regions.selector.ExtendingCuboidRegionSelector;
import com.sk89q.worldedit.world.World;
import org.marshal.weaddon_lightposition.selection.CuboidDraft;
import org.marshal.weaddon_lightposition.selection.SelectionEditSnapshot;

/** Call exclusively on the integrated server thread. Never creates an EditSession. */
public final class SelectionEditTransaction {
    private record Origin(LocalSession session, RegionSelector selector, World world, BlockVector3 pos1, BlockVector3 pos2) { }
    private SelectionEditTransaction() { }

    public static SelectionEditSnapshot read(Player actor) {
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(actor);
        if (session.hasWorldOverride()) return SelectionEditSnapshot.unavailable("session");
        World world = actor.getWorld();
        RegionSelector selector = session.getRegionSelector(world);
        if (selector.getClass() != CuboidRegionSelector.class && selector.getClass() != ExtendingCuboidRegionSelector.class)
            return SelectionEditSnapshot.unavailable("shape");
        try {
            CuboidRegion region = (CuboidRegion) selector.getRegion();
            boolean expand = actor.hasPermission("worldedit.selection.expand"), contract = actor.hasPermission("worldedit.selection.contract");
            if (!expand && !contract) return SelectionEditSnapshot.unavailable("permission");
            return new SelectionEditSnapshot(new Origin(session, selector, world, region.getPos1(), region.getPos2()), bounds(region),
                    world.getMinY(), world.getMaxY(), expand, contract, "");
        } catch (IncompleteRegionException ex) { return SelectionEditSnapshot.unavailable("selection"); }
    }

    public static boolean same(SelectionEditSnapshot original, SelectionEditSnapshot current) {
        if (!(original.token() instanceof Origin a) || !(current.token() instanceof Origin b)) return false;
        return a.session == b.session && a.selector == b.selector && a.world.equals(b.world) && a.pos1.equals(b.pos1) && a.pos2.equals(b.pos2);
    }

    public static SelectionEditSnapshot commit(Player actor, SelectionEditSnapshot original, CuboidDraft draft, CuboidDraft.Face face) {
        return commitAxes(actor, original, draft, new CuboidDraft.Face[]{face});
    }

    public static SelectionEditSnapshot commitCorner(Player actor, SelectionEditSnapshot original, CuboidDraft draft, CuboidDraft.Corner corner) {
        return commitAxes(actor, original, draft, new CuboidDraft.Face[]{corner.face(0), corner.face(1), corner.face(2)});
    }

    private static SelectionEditSnapshot commitAxes(Player actor, SelectionEditSnapshot original, CuboidDraft draft, CuboidDraft.Face[] faces) {
        SelectionEditSnapshot current = read(actor);
        if (!same(original, current)) return SelectionEditSnapshot.unavailable("stale");
        CuboidDraft validated = original.bounds();
        for (CuboidDraft.Face face : faces) {
            long delta = ((long) draft.boundary(face) - original.bounds().boundary(face)) * face.sign;
            if (delta != 0 && (delta > 0 ? !current.expand() : !current.contract())) return SelectionEditSnapshot.unavailable("permission");
            validated = validated.resize(face, delta, current.minY(), current.maxY());
        }
        if (!validated.equals(draft)) return SelectionEditSnapshot.unavailable("invalid");
        if (draft.equals(original.bounds())) return current;
        Origin origin = (Origin) current.token();
        try {
            CuboidRegion actual = (CuboidRegion) origin.selector.getRegion();
            CuboidRegion checked = actual.clone();
            applyAxes(checked, original.bounds(), draft, faces);
            if (!bounds(checked).equals(draft)) return SelectionEditSnapshot.unavailable("invalid");
            applyAxes(actual, original.bounds(), draft, faces);
            origin.selector.learnChanges();
            origin.selector.explainRegionAdjust(actor, origin.session);
            return read(actor);
        } catch (IncompleteRegionException ex) { return SelectionEditSnapshot.unavailable("stale"); }
    }

    private static void applyAxes(CuboidRegion region, CuboidDraft original, CuboidDraft draft, CuboidDraft.Face[] faces) {
        for (CuboidDraft.Face face : faces) {
            long delta = ((long) draft.boundary(face) - original.boundary(face)) * face.sign;
            if (delta != 0) apply(region, face, delta);
        }
    }

    private static void apply(CuboidRegion region, CuboidDraft.Face face, long delta) {
        int movement = Math.toIntExact(delta * face.sign);
        BlockVector3 vector = BlockVector3.at(face.axis == 0 ? movement : 0, face.axis == 1 ? movement : 0, face.axis == 2 ? movement : 0);
        if (delta > 0) region.expand(vector); else region.contract(vector);
    }
    private static CuboidDraft bounds(CuboidRegion r) {
        BlockVector3 a = r.getMinimumPoint(), b = r.getMaximumPoint();
        return new CuboidDraft(a.x(), a.y(), a.z(), b.x(), b.y(), b.z());
    }
}
