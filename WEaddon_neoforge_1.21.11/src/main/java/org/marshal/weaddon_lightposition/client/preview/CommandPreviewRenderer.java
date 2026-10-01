package org.marshal.weaddon_lightposition.client.preview;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.block.model.BakedQuad;
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
    private record DrawCell(CommandPreviewSnapshot.Cell cell, List<Quad> quads) { }
    private record Marker(AABB box, int color, boolean fill) { }
    private final IdentityHashMap<BlockState, List<Quad>> models = new IdentityHashMap<>();
    private final ArrayList<DrawCell> mesh = new ArrayList<>();
    private List<DrawCell> geometry = List.of();
    private List<Marker> markers = List.of();
    private CommandPreviewSnapshot prepared;
    private Object modelManager;
    private int buildIndex;

    void clear() { prepared = null; mesh.clear(); geometry = List.of(); models.clear(); markers = List.of(); buildIndex = 0; }

    void render(PoseStack context, CommandPreviewSnapshot snapshot, PreviewOptions options) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || snapshot.state() != CommandPreviewSnapshot.State.READY) return;
        prepare(client, snapshot);
        Vec3 camera = client.gameRenderer.getMainCamera().position();
        double distanceSqr = (double) options.renderDistance() * options.renderDistance();
        int alpha = Math.round(options.opacity() * 255);
        List<DrawCell> batch = geometry;
        var buffers = client.renderBuffers().bufferSource();
        draw(context.last(), buffers.getBuffer(RenderTypes.entityTranslucentEmissive(TextureAtlas.LOCATION_BLOCKS)), camera, distanceSqr, alpha, batch);
        buffers.endBatch(RenderTypes.entityTranslucentEmissive(TextureAtlas.LOCATION_BLOCKS));
    }

    private void prepare(Minecraft client, CommandPreviewSnapshot snapshot) {
        if (modelManager != client.getModelManager()) { clear(); modelManager = client.getModelManager(); }
        if (prepared != snapshot) {
            prepared = snapshot;
            mesh.clear();
            geometry = List.of();
            buildIndex = 0;
            markers = mergeMarkers(snapshot.cells());
        }
        long deadline = System.nanoTime() + 2_000_000;
        int count = 0;
        int previous = buildIndex;
        while (buildIndex < snapshot.cells().size() && count++ < 128 && System.nanoTime() < deadline) {
            var cell = snapshot.cells().get(buildIndex++);
            if (cell.kind() != PreviewChangeKind.REMOVED && cell.after() != null) mesh.add(new DrawCell(cell, model(client, cell.after())));
        }
        if (previous != buildIndex) geometry = List.copyOf(mesh);
    }

    private List<Quad> model(Minecraft client, BlockState state) {
        List<Quad> cached = models.get(state);
        if (cached != null) return cached;
        ArrayList<BlockModelPart> parts = new ArrayList<>();
        client.getBlockRenderer().getBlockModel(state).collectParts(RandomSource.create(42), parts);
        ArrayList<Quad> quads = new ArrayList<>();
        for (var part : parts) {
            for (Direction direction : Direction.values()) for (BakedQuad quad : part.getQuads(direction)) quads.add(quad(client, state, quad, direction));
            for (BakedQuad quad : part.getQuads(null)) quads.add(quad(client, state, quad, null));
        }
        cached = List.copyOf(quads);
        models.put(state, cached);
        return cached;
    }

    private static Quad quad(Minecraft client, BlockState state, BakedQuad quad, Direction cull) {
        int tint = 0xFFFFFF;
        if (quad.isTinted()) tint = client.getBlockColors().getColor(state, null, null, quad.tintIndex());
        return new Quad(quad, tint, cull);
    }

    private void draw(PoseStack.Pose pose, VertexConsumer consumer, Vec3 camera, double distanceSqr, int alpha, List<DrawCell> batch) {
        for (DrawCell draw : batch) {
            var cell = draw.cell;
            double dx = cell.position().getX() - camera.x, dy = cell.position().getY() - camera.y, dz = cell.position().getZ() - camera.z;
            if (dx * dx + dy * dy + dz * dz > distanceSqr) continue;
            for (Quad q : draw.quads) {
                if (q.cull != null && (cell.faces() & (1 << q.cull.ordinal())) == 0) continue;
                int rgb = mixTint(q.tint, cell.kind().color());
                Direction normal = q.baked.direction();
                for (int v = 0; v < 4; v++) {
                    var position = q.baked.position(v);
                    long uv = q.baked.packedUV(v);
                    // Small symmetric expansion avoids coplanar flicker without changing chunk meshes.
                    consumer.addVertex(pose, (float) dx + position.x() * 1.003f - 0.0015f,
                                    (float) dy + position.y() * 1.003f - 0.0015f, (float) dz + position.z() * 1.003f - 0.0015f)
                            .setColor(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, alpha)
                            .setUv(net.minecraft.client.model.geom.builders.UVPair.unpackU(uv),
                                    net.minecraft.client.model.geom.builders.UVPair.unpackV(uv))
                            .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(0x00F000F0)
                            .setNormal(pose, normal.getStepX(), normal.getStepY(), normal.getStepZ());
                }
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
        if (client.level == null) return;
        prepare(client, snapshot);
        Vec3 camera = client.gameRenderer.getMainCamera().position();
        double distanceSqr = (double) options.renderDistance() * options.renderDistance();
        for (Marker marker : markers) {
            if (marker.box.getCenter().distanceToSqr(camera) > distanceSqr) continue;
            int fill = marker.fill ? Math.round(options.opacity() * 38) << 24 | marker.color : 0;
            Gizmos.cuboid(marker.box.inflate(0.002), GizmoStyle.strokeAndFill(0xD0000000 | marker.color, 1.0f, fill)).setAlwaysOnTop();
        }
    }

    // Merge adjacent X runs once, instead of submitting a box around every edited cell.
    private static List<Marker> mergeMarkers(List<CommandPreviewSnapshot.Cell> cells) {
        List<CommandPreviewSnapshot.Cell> sorted = cells.stream()
                .filter(c -> c.kind() == PreviewChangeKind.REMOVED || c.after() == null
                        || c.after().getRenderShape() != net.minecraft.world.level.block.RenderShape.MODEL)
                .sorted(Comparator
                .comparingInt((CommandPreviewSnapshot.Cell c) -> c.position().getY())
                .thenComparingInt(c -> c.position().getZ()).thenComparingInt(c -> c.position().getX())).toList();
        ArrayList<Marker> output = new ArrayList<>();
        for (int i = 0; i < sorted.size();) {
            var start = sorted.get(i++);
            int endX = start.position().getX();
            while (i < sorted.size()) {
                var next = sorted.get(i);
                if (next.kind() != start.kind() || next.position().getY() != start.position().getY()
                        || next.position().getZ() != start.position().getZ() || next.position().getX() != endX + 1) break;
                endX++; i++;
            }
            output.add(new Marker(new AABB(start.position().getX(), start.position().getY(), start.position().getZ(),
                    endX + 1, start.position().getY() + 1, start.position().getZ() + 1), start.kind().color(),
                    start.kind() == PreviewChangeKind.REMOVED || start.after() == null || start.after().getRenderShape() != net.minecraft.world.level.block.RenderShape.MODEL));
        }
        return List.copyOf(output);
    }
}
