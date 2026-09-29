package org.marshal.weaddon_lightposition.client.overlay;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public final class SelectionManager {
    private SelectionRegion single;
    private final LinkedHashMap<UUID, SelectionRegion> multiRegions = new LinkedHashMap<>();
    private UUID activeMultiRegion;
    private final ArrayList<SelectionRegion> renderScratch = new ArrayList<>(8);

    public void clear() {
        single = null;
        multiRegions.clear();
        activeMultiRegion = null;
        renderScratch.clear();
    }

    public void onSelectionEvent(boolean multi, List<String> args) {
        if (!multi) {
            single = createRegion(args.isEmpty() ? "clear" : args.get(0));
            return;
        }

        if (args.isEmpty()) {
            multiRegions.clear();
            activeMultiRegion = null;
            return;
        }

        String shapeKey = args.get(0);
        if ("clear".equals(shapeKey) && args.size() < 2) {
            multiRegions.clear();
            activeMultiRegion = null;
            return;
        }
        if (args.size() < 2) {
            return;
        }

        UUID regionId = parseUuid(args.get(1));
        if (regionId == null) {
            return;
        }

        SelectionRegion region = createRegion(shapeKey);
        if (region == null) {
            multiRegions.remove(regionId);
            if (regionId.equals(activeMultiRegion)) {
                activeMultiRegion = null;
            }
            return;
        }

        multiRegions.put(regionId, region);
        activeMultiRegion = regionId;
    }

    public SelectionRegion targetRegion(boolean multi) {
        if (!multi) {
            return single;
        }

        if (activeMultiRegion == null) {
            return null;
        }
        return multiRegions.get(activeMultiRegion);
    }

    public void setFallbackCuboidPoint(int id, BlockPos point) {
        if (single == null || single.shape() != RegionShape.CUBOID) {
            single = new SelectionRegion(RegionShape.CUBOID);
        }
        single.applyPoint3D(id, new Vec3(point.getX(), point.getY(), point.getZ()));
    }

    public List<SelectionRegion> regionsForRender() {
        renderScratch.clear();
        if (single != null) {
            renderScratch.add(single);
        }
        renderScratch.addAll(multiRegions.values());
        return renderScratch;
    }

    public SelectionRegion primaryRegionForAnalysis() {
        if (single != null) {
            return single;
        }

        if (activeMultiRegion != null) {
            SelectionRegion active = multiRegions.get(activeMultiRegion);
            if (active != null) {
                return active;
            }
        }

        if (multiRegions.isEmpty()) {
            return null;
        }
        return multiRegions.values().iterator().next();
    }

    private static SelectionRegion createRegion(String shapeKey) {
        if (shapeKey == null || "clear".equals(shapeKey)) {
            return null;
        }

        RegionShape shape = RegionShape.fromProtocolKey(shapeKey);
        if (shape == null) {
            return null;
        }
        return new SelectionRegion(shape);
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
