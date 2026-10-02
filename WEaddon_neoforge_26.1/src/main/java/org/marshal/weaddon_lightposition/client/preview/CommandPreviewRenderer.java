package org.marshal.weaddon_lightposition.client.preview;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.Map;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.marshal.weaddon_lightposition.preview.PreviewChangeKind;
import org.marshal.weaddon_lightposition.preview.PreviewOptions;

final class CommandPreviewRenderer {
    private record Quad(BakedQuad baked, int tint, Direction cull) { }
    private record Marker(AABB box, int color, boolean fill) { }
    private final IdentityHashMap<BlockState, List<Quad>> models = new IdentityHashMap<>();
    private final Map<SectionKey, CommandPreviewGpuSection> sections = new LinkedHashMap<>();
    private record SectionKey(int x, int y, int z) { }
    private List<CommandPreviewGpuSection> visible = List.of();
    private final List<Marker> markers = new ArrayList<>();
    private net.minecraft.client.renderer.culling.Frustum cullFrustum;
    private CommandPreviewSnapshot prepared;
    private Object modelManager;
    private BlockState probeState;
    private Object probeModel;
    private int buildIndex;
    private CommandPreviewGpuSection building;
    private int sortCursor;
    private SectionKey markerSection;
    private int markerStart;
    private record MarkerSpan(double minX, double maxX, double minOther, double maxOther, int color) { }

    void clearGeometry() {
        for (var section : sections.values()) section.close();
        sections.clear(); visible = List.of(); prepared = null; markers.clear(); buildIndex = 0; building = null; sortCursor = 0; cullFrustum = null;
        markerSection = null; markerStart = 0;
    }
    void clear() { clearGeometry(); models.clear(); probeState = null; probeModel = null; }

    private Object bakedModel(Minecraft client, BlockState state) {
        return client.getModelManager().getBlockStateModelSet().get(state);
    }

    void render(PoseStack context, net.minecraft.client.renderer.culling.Frustum frustum, CommandPreviewSnapshot snapshot, PreviewOptions options) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || snapshot.state() != CommandPreviewSnapshot.State.READY) return;
        prepare(client, snapshot, options);
        Vec3 camera = client.gameRenderer.getMainCamera().position();
        cullFrustum = frustum;
        visible = sections.values().stream().filter(s -> s.uploaded() && !s.empty()
                && client.level.getChunkSource().getChunk(s.x >> 4, s.z >> 4,
                    net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false) != null
                && (frustum == null || frustum.isVisible(s.bounds))).sorted(Comparator.comparingDouble(
                    (CommandPreviewGpuSection s) -> s.bounds.getCenter().distanceToSqr(camera)).reversed()).toList();
        long sortDeadline = System.nanoTime() + 1_000_000;
        for (int i = 0; i < visible.size() && System.nanoTime() < sortDeadline; i++) {
            var section = visible.get(sortCursor++ % visible.size());
            if (section.needsSort(camera)) section.sort(camera);
        }
        if (sortCursor > 1_000_000) sortCursor = 0;
        if (visible.isEmpty()) return;
        for (var section : visible) section.prepareTransform(context.last().pose(), camera);
        try (var pass = CommandPreviewGpuSection.begin(client)) {
            for (var section : visible) section.draw(pass);
        }
    }

    private void prepare(Minecraft client, CommandPreviewSnapshot snapshot, PreviewOptions options) {
        if (modelManager != client.getModelManager() || probeState != null && probeModel != bakedModel(client, probeState)) {
            clear();
            modelManager = client.getModelManager();
        }
        if (prepared != snapshot) {
            clearGeometry();
            prepared = snapshot;
            buildIndex = 0;
        }
        long deadline = System.nanoTime() + 2_000_000;
        int count = 0;
        while (buildIndex < snapshot.cells().size() && count++ < 2_048 && System.nanoTime() < deadline) {
            var cell = snapshot.cells().get(buildIndex++);
            if (cell.kind() != PreviewChangeKind.REMOVED && cell.after() != null
                    && cell.after().getRenderShape() == net.minecraft.world.level.block.RenderShape.MODEL) {
                SectionKey key = new SectionKey(cell.position().getX() >> 4, cell.position().getY() >> 4, cell.position().getZ() >> 4);
                var section = sections.computeIfAbsent(key, k -> new CommandPreviewGpuSection(k.x * 16, k.y * 16, k.z * 16));
                if (building != section) {
                    if (building != null) building.upload(client.gameRenderer.getMainCamera().position());
                    building = section;
                }
                writeCell(section.builder(), cell, model(client, cell.after()), section.x, section.y, section.z, Math.round(options.opacity() * 255));
            } else {
                appendMarker(cell);
            }
        }
        if (buildIndex == snapshot.cells().size()) {
            if (building != null) { building.upload(client.gameRenderer.getMainCamera().position()); building = null; }
            finishMarkerSection();
        }
    }

    private List<Quad> model(Minecraft client, BlockState state) {
        List<Quad> cached = models.get(state);
        if (cached != null) return cached;
        ArrayList<BlockStateModelPart> parts = new ArrayList<>();
        client.getModelManager().getBlockStateModelSet().get(state).collectParts(RandomSource.create(42), parts);
        ArrayList<Quad> quads = new ArrayList<>();
        for (var part : parts) {
            for (Direction direction : Direction.values()) for (BakedQuad quad : part.getQuads(direction)) quads.add(quad(client, state, quad, direction));
            for (BakedQuad quad : part.getQuads(null)) quads.add(quad(client, state, quad, null));
        }
        cached = List.copyOf(quads);
        if (models.size() >= 256) { models.clear(); probeState = null; }
        if (probeState == null) { probeState = state; probeModel = bakedModel(client, state); }
        models.put(state, cached);
        return cached;
    }

    private static Quad quad(Minecraft client, BlockState state, BakedQuad quad, Direction cull) {
        int tint = 0xFFFFFF;
        if (quad.materialInfo().isTinted()) {
            var source = client.getBlockColors().getTintSource(state, quad.materialInfo().tintIndex());
            if (source != null) tint = source.color(state);
        }
        return new Quad(quad, tint, cull);
    }

    private void writeCell(VertexConsumer consumer, CommandPreviewSnapshot.Cell cell, List<Quad> quads, int x, int y, int z, int alpha) {
            int dx = cell.position().getX() - x, dy = cell.position().getY() - y, dz = cell.position().getZ() - z;
            for (Quad q : quads) {
                if (q.cull != null && (cell.faces() & (1 << q.cull.ordinal())) == 0) continue;
                int rgb = mixTint(q.tint, cell.kind().color());
                Direction normal = q.baked.direction();
                for (int v = 0; v < 4; v++) {
                    var position = q.baked.position(v);
                    long uv = q.baked.packedUV(v);
                    // Small symmetric expansion avoids coplanar flicker without changing chunk meshes.
                    consumer.addVertex((float) dx + position.x() * 1.003f - 0.0015f,
                                    (float) dy + position.y() * 1.003f - 0.0015f, (float) dz + position.z() * 1.003f - 0.0015f)
                            .setColor(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, alpha)
                            .setUv(net.minecraft.client.model.geom.builders.UVPair.unpackU(uv),
                                    net.minecraft.client.model.geom.builders.UVPair.unpackV(uv))
                            .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(0x00F000F0)
                            .setNormal(normal.getStepX(), normal.getStepY(), normal.getStepZ());
                }
            }
    }

    private static int mixTint(int tint, int accent) {
        int r = (tint >> 16 & 255) * (204 + (accent >> 16 & 255) / 5) / 255;
        int g = (tint >> 8 & 255) * (204 + (accent >> 8 & 255) / 5) / 255;
        int b = (tint & 255) * (204 + (accent & 255) / 5) / 255;
        return r << 16 | g << 8 | b;
    }

    void renderMarkers(CommandPreviewSnapshot snapshot, PreviewOptions options) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || prepared != snapshot || buildIndex != snapshot.cells().size()) return;
        for (Marker marker : markers) {
            if (cullFrustum != null && !cullFrustum.isVisible(marker.box)) continue;
            if (client.level.getChunkSource().getChunk((int) marker.box.minX >> 4, (int) marker.box.minZ >> 4,
                    net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false) == null) continue;
            int fill = marker.fill ? Math.round(options.opacity() * 38) << 24 | marker.color : 0;
            Gizmos.cuboid(marker.box.inflate(0.002), GizmoStyle.strokeAndFill(0xD0000000 | marker.color, 1.0f, fill), true).setAlwaysOnTop();
        }
    }

    // Paletted snapshots are already section/local-index ordered; no global sort is needed.
    private void appendMarker(CommandPreviewSnapshot.Cell cell) {
        int x = cell.position().getX(), y = cell.position().getY(), z = cell.position().getZ();
        int color = cell.kind().color();
        SectionKey key = new SectionKey(x >> 4, y >> 4, z >> 4);
        if (!key.equals(markerSection)) {
            finishMarkerSection();
            markerSection = key; markerStart = markers.size();
        }
        if (!markers.isEmpty()) {
            Marker last = markers.getLast();
            if (last.color == color && last.box.maxX == x && last.box.minY == y && last.box.minZ == z
                    && ((int) last.box.minX >> 4) == (x >> 4)) {
                markers.set(markers.size() - 1, new Marker(new AABB(last.box.minX, y, z, x + 1, y + 1, z + 1), color, true));
                return;
            }
        }
        markers.add(new Marker(new AABB(x, y, z, x + 1, y + 1, z + 1), color, true));
    }

    private void finishMarkerSection() {
        if (markerSection == null) return;
        var merged = mergeAxis(mergeAxis(markers.subList(markerStart, markers.size()), true), false);
        markers.subList(markerStart, markers.size()).clear();
        markers.addAll(merged);
        markerSection = null;
    }

    // Exact section-local cuboid merging: never bridge a hole or merge across chunks.
    private static List<Marker> mergeAxis(List<Marker> input, boolean alongZ) {
        ArrayList<Marker> output = new ArrayList<>();
        Map<MarkerSpan, Integer> previous = new HashMap<>();
        for (Marker marker : input) {
            AABB b = marker.box;
            MarkerSpan key = new MarkerSpan(b.minX, b.maxX, alongZ ? b.minY : b.minZ, alongZ ? b.maxY : b.maxZ, marker.color);
            Integer index = previous.get(key);
            if (index != null) {
                Marker last = output.get(index);
                AABB a = last.box;
                if (alongZ ? a.maxZ == b.minZ : a.maxY == b.minY) {
                    output.set(index, new Marker(new AABB(a.minX, a.minY, a.minZ, a.maxX,
                            alongZ ? a.maxY : b.maxY, alongZ ? b.maxZ : a.maxZ), marker.color, true));
                    continue;
                }
            }
            previous.put(key, output.size());
            output.add(marker);
        }
        return output;
    }
}
