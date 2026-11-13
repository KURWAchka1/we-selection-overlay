package org.marshal.weaddon_lightposition.client.preview;

import net.minecraft.client.render.VertexConsumer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.util.math.Direction;
import org.marshal.weaddon_lightposition.client.render.LegacyOverlay;
import net.minecraft.util.math.random.Random;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.marshal.weaddon_lightposition.preview.PreviewChangeKind;
import org.marshal.weaddon_lightposition.preview.PreviewOptions;

final class CommandPreviewRenderer {
    private record Quad(BakedQuad baked, int tint, Direction cull) { }
    private record Marker(Box box, int color, boolean fill) { }
    private final IdentityHashMap<BlockState, List<Quad>> models = new IdentityHashMap<>();
    private final Map<SectionKey, CommandPreviewGpuSection> sections = new LinkedHashMap<>();
    private record SectionKey(int x, int y, int z) { }
    private List<CommandPreviewGpuSection> visible = List.of();
    private final List<Marker> markers = new ArrayList<>();
    private net.minecraft.client.render.Frustum cullFrustum;
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

    private Object bakedModel(MinecraftClient client, BlockState state) {
        return client.getBlockRenderManager().getModel(state);
    }

    void render(WorldRenderContext context, CommandPreviewSnapshot snapshot, PreviewOptions options) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || snapshot.state() != CommandPreviewSnapshot.State.READY) return;
        prepare(client, snapshot, options);
        Vec3d camera = client.gameRenderer.getCamera().getPos();
        var frustum = context.frustum();
        cullFrustum = frustum;
        visible = sections.values().stream().filter(s -> s.uploaded() && !s.empty()
                && client.world.getChunkManager().getChunk(s.x >> 4, s.z >> 4,
                    net.minecraft.world.chunk.ChunkStatus.FULL, false) != null
                && (frustum == null || frustum.isVisible(s.bounds))).sorted(Comparator.comparingDouble(
                    (CommandPreviewGpuSection s) -> s.bounds.getCenter().squaredDistanceTo(camera)).reversed()).toList();
        long sortDeadline = System.nanoTime() + 1_000_000;
        for (int i = 0; i < visible.size() && System.nanoTime() < sortDeadline; i++) {
            var section = visible.get(sortCursor++ % visible.size());
            if (section.needsSort(camera)) section.sort(camera);
        }
        if (sortCursor > 1_000_000) sortCursor = 0;
        if (visible.isEmpty() || context.matrixStack() == null) return;
        CommandPreviewGpuSection.LAYER.startDrawing();
        try {
            var view = context.matrixStack().peek().getPositionMatrix();
            for (var section : visible) section.draw(view, camera);
        } finally {
            net.minecraft.client.gl.VertexBuffer.unbind();
            CommandPreviewGpuSection.LAYER.endDrawing();
        }
    }

    private void prepare(MinecraftClient client, CommandPreviewSnapshot snapshot, PreviewOptions options) {
        if (modelManager != client.getBakedModelManager() || probeState != null && probeModel != bakedModel(client, probeState)) {
            clear();
            modelManager = client.getBakedModelManager();
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
                    && cell.after().getRenderType() == net.minecraft.block.BlockRenderType.MODEL) {
                SectionKey key = new SectionKey(cell.position().getX() >> 4, cell.position().getY() >> 4, cell.position().getZ() >> 4);
                var section = sections.computeIfAbsent(key, k -> new CommandPreviewGpuSection(k.x * 16, k.y * 16, k.z * 16));
                if (building != section) {
                    if (building != null) building.upload(client.gameRenderer.getCamera().getPos());
                    building = section;
                }
                writeCell(section.builder(), cell, model(client, cell.after()), section.x, section.y, section.z, Math.round(options.opacity() * 255));
            } else {
                appendMarker(cell);
            }
        }
        if (buildIndex == snapshot.cells().size()) {
            if (building != null) { building.upload(client.gameRenderer.getCamera().getPos()); building = null; }
            finishMarkerSection();
        }
    }

    private List<Quad> model(MinecraftClient client, BlockState state) {
        List<Quad> cached = models.get(state);
        if (cached != null) return cached;
        var model = client.getBlockRenderManager().getModel(state);
        ArrayList<Quad> quads = new ArrayList<>();
        for (Direction direction : Direction.values()) for (BakedQuad quad : model.getQuads(state, direction, Random.create(42)))
            quads.add(quad(client, state, quad, direction));
        for (BakedQuad quad : model.getQuads(state, null, Random.create(42))) quads.add(quad(client, state, quad, null));
        cached = List.copyOf(quads);
        if (models.size() >= 256) { models.clear(); probeState = null; }
        if (probeState == null) { probeState = state; probeModel = bakedModel(client, state); }
        models.put(state, cached);
        return cached;
    }

    private static Quad quad(MinecraftClient client, BlockState state, BakedQuad quad, Direction cull) {
        int tint = 0xFFFFFF;
        if (quad.hasColor()) tint = client.getBlockColors().getColor(state, null, null, quad.getColorIndex());
        return new Quad(quad, tint, cull);
    }

    private void writeCell(VertexConsumer consumer, CommandPreviewSnapshot.Cell cell, List<Quad> quads, int x, int y, int z, int alpha) {
            int dx = cell.position().getX() - x, dy = cell.position().getY() - y, dz = cell.position().getZ() - z;
            for (Quad q : quads) {
                if (q.cull != null && (cell.faces() & (1 << q.cull.ordinal())) == 0) continue;
                int rgb = mixTint(q.tint, cell.kind().color());
                Direction normal = q.baked.getFace();
                int[] data = q.baked.getVertexData();
                int stride = data.length / 4;
                for (int v = 0; v < 4; v++) {
                    int i = v * stride;
                    // Small symmetric expansion avoids coplanar flicker without changing chunk meshes.
                    consumer.vertex(dx + Float.intBitsToFloat(data[i]) * 1.003f - 0.0015f,
                                    dy + Float.intBitsToFloat(data[i + 1]) * 1.003f - 0.0015f,
                                    dz + Float.intBitsToFloat(data[i + 2]) * 1.003f - 0.0015f)
                            .color(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, alpha)
                            .texture(Float.intBitsToFloat(data[i + 4]), Float.intBitsToFloat(data[i + 5]))
                            .overlay(net.minecraft.client.render.OverlayTexture.DEFAULT_UV).light(0x00F000F0)
                            .normal(normal.getOffsetX(), normal.getOffsetY(), normal.getOffsetZ()).next();
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
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || prepared != snapshot || buildIndex != snapshot.cells().size()) return;
        for (Marker marker : markers) {
            if (cullFrustum != null && !cullFrustum.isVisible(marker.box)) continue;
            if (client.world.getChunkManager().getChunk((int) marker.box.minX >> 4, (int) marker.box.minZ >> 4,
                    net.minecraft.world.chunk.ChunkStatus.FULL, false) == null) continue;
            int fill = marker.fill ? Math.round(options.opacity() * 38) << 24 | marker.color : 0;
            LegacyOverlay.box(marker.box.expand(0.002), LegacyOverlay.Style.filledAndStroked(0xD0000000 | marker.color, 1.0f, fill));
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
            Marker last = markers.get(markers.size() - 1);
            if (last.color == color && last.box.maxX == x && last.box.minY == y && last.box.minZ == z
                    && ((int) last.box.minX >> 4) == (x >> 4)) {
                markers.set(markers.size() - 1, new Marker(new Box(last.box.minX, y, z, x + 1, y + 1, z + 1), color, true));
                return;
            }
        }
        markers.add(new Marker(new Box(x, y, z, x + 1, y + 1, z + 1), color, true));
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
            Box b = marker.box;
            MarkerSpan key = new MarkerSpan(b.minX, b.maxX, alongZ ? b.minY : b.minZ, alongZ ? b.maxY : b.maxZ, marker.color);
            Integer index = previous.get(key);
            if (index != null) {
                Marker last = output.get(index);
                Box a = last.box;
                if (alongZ ? a.maxZ == b.minZ : a.maxY == b.minY) {
                    output.set(index, new Marker(new Box(a.minX, a.minY, a.minZ, a.maxX,
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
