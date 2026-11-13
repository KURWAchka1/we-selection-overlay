package org.marshal.weaddon_lightposition.client.preview;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.BufferBuilder;
import com.mojang.blaze3d.systems.VertexSorter;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/** Textured vertices are immutable; only transparent indices follow the camera. */
final class CommandPreviewGpuSection implements AutoCloseable {
    static final RenderLayer LAYER = RenderLayer.getEntityTranslucentEmissiveNoOutline(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);
    final int x, y, z;
    final Box bounds;
    private BufferBuilder builder;
    private VertexBuffer mesh;
    private BufferBuilder.TransparentSortingData sorting;
    private double sortX = Double.NaN, sortY, sortZ;
    long vertexBytes;

    CommandPreviewGpuSection(int x, int y, int z) {
        this.x = x; this.y = y; this.z = z;
        bounds = new Box(x - .01, y - .01, z - .01, x + 16.01, y + 16.01, z + 16.01);
        builder = new BufferBuilder(32_768);
        builder.begin(LAYER.getDrawMode(), LAYER.getVertexFormat());
    }
    BufferBuilder builder() { return builder; }
    boolean uploaded() { return builder == null; }
    boolean empty() { return mesh == null; }

    void upload(Vec3d camera) {
        try {
            builder.setSorter(sorter(camera));
            sorting = builder.getSortingData();
            var data = builder.endNullable();
            if (data == null) {
                sorting = null;
                return;
            }
            vertexBytes = data.getVertexBuffer().remaining();
            mesh = new VertexBuffer(VertexBuffer.Usage.STATIC);
            upload(data);
            rememberCamera(camera);
        } finally {
            builder.close();
            builder = null;
        }
    }

    boolean needsSort(Vec3d camera) {
        double dx = camera.x - sortX, dy = camera.y - sortY, dz = camera.z - sortZ;
        return sorting != null && dx * dx + dy * dy + dz * dz >= 1;
    }
    void sort(Vec3d camera) {
        BufferBuilder temporary = new BufferBuilder(4096);
        try {
            temporary.begin(LAYER.getDrawMode(), LAYER.getVertexFormat());
            temporary.beginSortedIndexBuffer(sorting);
            temporary.setSorter(sorter(camera));
            upload(temporary.end());
            rememberCamera(camera);
        } finally { temporary.close(); }
    }

    private void upload(BufferBuilder.BuiltBuffer data) {
        mesh.bind();
        try { mesh.upload(data); }
        finally { VertexBuffer.unbind(); }
    }
    private VertexSorter sorter(Vec3d camera) {
        return VertexSorter.byDistance((float) (camera.x - x), (float) (camera.y - y), (float) (camera.z - z));
    }
    private void rememberCamera(Vec3d camera) { sortX = camera.x; sortY = camera.y; sortZ = camera.z; }

    void draw(Matrix4f view, Vec3d camera) {
        Matrix4f matrix = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(view)
                .translate((float) (x - camera.x), (float) (y - camera.y), (float) (z - camera.z));
        mesh.bind();
        mesh.draw(matrix, RenderSystem.getProjectionMatrix(), RenderSystem.getShader());
    }

    @Override public void close() {
        if (mesh != null) mesh.close();
        if (builder != null) builder.close();
        mesh = null; builder = null; sorting = null;
    }
}
