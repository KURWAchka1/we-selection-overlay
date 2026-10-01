package org.marshal.weaddon_lightposition.client.preview;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.VertexConsumer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.BlockModelPart;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.util.math.Direction;
import net.minecraft.client.render.DrawStyle;
import net.minecraft.world.debug.gizmo.GizmoDrawing;
import net.minecraft.util.math.random.Random;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.marshal.weaddon_lightposition.preview.PreviewChangeKind;
import org.marshal.weaddon_lightposition.preview.PreviewOptions;

final class CommandPreviewRenderer {
    private record Quad(BakedQuad baked, int tint, Direction cull) { }
    private record DrawCell(CommandPreviewSnapshot.Cell cell, List<Quad> quads) { }
    private record Marker(Box box, int color, boolean fill) { }
    private final IdentityHashMap<BlockState, List<Quad>> models = new IdentityHashMap<>();
    private final ArrayList<DrawCell> mesh = new ArrayList<>();
    private List<DrawCell> geometry = List.of();
    private List<Marker> markers = List.of();
    private CommandPreviewSnapshot prepared;
    private Object modelManager;
    private int buildIndex;

    void clear() { prepared = null; mesh.clear(); geometry = List.of(); models.clear(); markers = List.of(); buildIndex = 0; }

    void render(WorldRenderContext context, CommandPreviewSnapshot snapshot, PreviewOptions options) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || snapshot.state() != CommandPreviewSnapshot.State.READY) return;
        prepare(client, snapshot);
        Vec3d camera = client.gameRenderer.getCamera().getCameraPos();
        double distanceSqr = (double) options.renderDistance() * options.renderDistance();
        int alpha = Math.round(options.opacity() * 255);
        List<DrawCell> batch = geometry;
        var consumer = context.consumers().getBuffer(RenderLayers.entityTranslucentEmissive(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE));
        draw(context.matrices().peek(), consumer, camera, distanceSqr, alpha, batch);
    }

    private void prepare(MinecraftClient client, CommandPreviewSnapshot snapshot) {
        if (modelManager != client.getBakedModelManager()) { clear(); modelManager = client.getBakedModelManager(); }
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

    private List<Quad> model(MinecraftClient client, BlockState state) {
        List<Quad> cached = models.get(state);
        if (cached != null) return cached;
        ArrayList<BlockModelPart> parts = new ArrayList<>();
        client.getBlockRenderManager().getModel(state).addParts(Random.create(42), parts);
        ArrayList<Quad> quads = new ArrayList<>();
        for (var part : parts) {
            for (Direction direction : Direction.values()) for (BakedQuad quad : part.getQuads(direction)) quads.add(quad(client, state, quad, direction));
            for (BakedQuad quad : part.getQuads(null)) quads.add(quad(client, state, quad, null));
        }
        cached = List.copyOf(quads);
        models.put(state, cached);
        return cached;
    }

    private static Quad quad(MinecraftClient client, BlockState state, BakedQuad quad, Direction cull) {
        int tint = 0xFFFFFF;
        if (quad.hasTint()) tint = client.getBlockColors().getColor(state, null, null, quad.tintIndex());
        return new Quad(quad, tint, cull);
    }

    private void draw(MatrixStack.Entry pose, VertexConsumer consumer, Vec3d camera, double distanceSqr, int alpha, List<DrawCell> batch) {
        for (DrawCell draw : batch) {
            var cell = draw.cell;
            double dx = cell.position().getX() - camera.x, dy = cell.position().getY() - camera.y, dz = cell.position().getZ() - camera.z;
            if (dx * dx + dy * dy + dz * dz > distanceSqr) continue;
            for (Quad q : draw.quads) {
                if (q.cull != null && (cell.faces() & (1 << q.cull.ordinal())) == 0) continue;
                int rgb = mixTint(q.tint, cell.kind().color());
                Direction normal = q.baked.face();
                for (int v = 0; v < 4; v++) {
                    var position = q.baked.getPosition(v);
                    long uv = q.baked.getTexcoords(v);
                    // Small symmetric expansion avoids coplanar flicker without changing chunk meshes.
                    consumer.vertex(pose, (float) dx + position.x() * 1.003f - 0.0015f,
                                    (float) dy + position.y() * 1.003f - 0.0015f, (float) dz + position.z() * 1.003f - 0.0015f)
                            .color(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, alpha)
                            .texture(net.minecraft.client.util.math.Vector2f.getX(uv),
                                    net.minecraft.client.util.math.Vector2f.getY(uv))
                            .overlay(net.minecraft.client.render.OverlayTexture.DEFAULT_UV).light(0x00F000F0)
                            .normal(pose, normal.getOffsetX(), normal.getOffsetY(), normal.getOffsetZ());
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
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;
        prepare(client, snapshot);
        Vec3d camera = client.gameRenderer.getCamera().getCameraPos();
        double distanceSqr = (double) options.renderDistance() * options.renderDistance();
        for (Marker marker : markers) {
            if (marker.box.getCenter().squaredDistanceTo(camera) > distanceSqr) continue;
            int fill = marker.fill ? Math.round(options.opacity() * 38) << 24 | marker.color : 0;
            GizmoDrawing.box(marker.box.expand(0.002), DrawStyle.filledAndStroked(0xD0000000 | marker.color, 1.0f, fill)).ignoreOcclusion();
        }
    }

    // Merge adjacent X runs once, instead of submitting a box around every edited cell.
    private static List<Marker> mergeMarkers(List<CommandPreviewSnapshot.Cell> cells) {
        List<CommandPreviewSnapshot.Cell> sorted = cells.stream()
                .filter(c -> c.kind() == PreviewChangeKind.REMOVED || c.after() == null
                        || c.after().getRenderType() != net.minecraft.block.BlockRenderType.MODEL)
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
            output.add(new Marker(new Box(start.position().getX(), start.position().getY(), start.position().getZ(),
                    endX + 1, start.position().getY() + 1, start.position().getZ() + 1), start.kind().color(),
                    start.kind() == PreviewChangeKind.REMOVED || start.after() == null || start.after().getRenderType() != net.minecraft.block.BlockRenderType.MODEL));
        }
        return List.copyOf(output);
    }
}
