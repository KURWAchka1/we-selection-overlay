package org.marshal.weaddon_lightposition.client.preview;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import java.util.Optional;
import java.util.OptionalDouble;

/** Textured vertices are immutable; only transparent indices follow the camera. */
final class CommandPreviewGpuSection implements AutoCloseable {
    private static final RenderSetup SETUP = RenderSetup.builder(RenderPipelines.ENTITY_TRANSLUCENT_EMISSIVE)
            .withTexture("Sampler0", TextureAtlas.LOCATION_BLOCKS).useOverlay().createRenderSetup();
    final int x, y, z;
    final AABB bounds;
    private final ByteBufferBuilder allocator = new ByteBufferBuilder(32_768);
    private BufferBuilder builder;
    private GpuBuffer vertices, indices;
    private GpuBufferSlice transform;
    private MeshData.SortState sorting;
    private int indexCount;
    private double sortX = Double.NaN, sortY, sortZ;
    long vertexBytes;

    CommandPreviewGpuSection(int x, int y, int z) {
        this.x = x; this.y = y; this.z = z;
        bounds = new AABB(x - .01, y - .01, z - .01, x + 16.01, y + 16.01, z + 16.01);
        builder = new BufferBuilder(allocator, RenderPipelines.ENTITY_TRANSLUCENT_EMISSIVE.getVertexFormatMode(),
                RenderPipelines.ENTITY_TRANSLUCENT_EMISSIVE.getVertexFormat());
    }
    BufferBuilder builder() { return builder; }
    boolean uploaded() { return builder == null; }
    boolean empty() { return vertices == null; }

    void upload(Vec3 camera) {
        try (MeshData data = builder.build()) {
            builder = null;
            if (data == null) return;
            sorting = data.sortQuads(allocator, sorter(camera));
            indexCount = data.drawState().indexCount();
            vertexBytes = data.vertexBuffer().remaining();
            vertices = RenderSystem.getDevice().createBuffer(() -> "WESO preview vertices", GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, data.vertexBuffer());
            indices = RenderSystem.getDevice().createBuffer(() -> "WESO preview indices", GpuBuffer.USAGE_INDEX | GpuBuffer.USAGE_COPY_DST,
                    data.indexBuffer());
            rememberCamera(camera);
        } finally { allocator.close(); }
    }

    boolean needsSort(Vec3 camera) {
        double dx = camera.x() - sortX, dy = camera.y() - sortY, dz = camera.z() - sortZ;
        return sorting != null && dx * dx + dy * dy + dz * dz >= 1;
    }
    void sort(Vec3 camera) {
        try (var temporary = new ByteBufferBuilder(32_768);
             var data = sorting.buildSortedIndexBuffer(temporary, sorter(camera))) {
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(indices.slice(), data.byteBuffer());
            rememberCamera(camera);
        }
    }
    private VertexSorting sorter(Vec3 camera) {
        return VertexSorting.byDistance((float) (camera.x() - x), (float) (camera.y() - y), (float) (camera.z() - z));
    }
    private void rememberCamera(Vec3 camera) { sortX = camera.x(); sortY = camera.y(); sortZ = camera.z(); }

    static RenderPass begin(Minecraft client) {
        var target = client.getMainRenderTarget();
        var color = RenderSystem.outputColorTextureOverride != null ? RenderSystem.outputColorTextureOverride : target.getColorTextureView();
        var depth = RenderSystem.outputDepthTextureOverride != null ? RenderSystem.outputDepthTextureOverride : target.getDepthTextureView();
        var textures = SETUP.getTextures();
        var pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "WESO command preview", color,
                java.util.OptionalInt.empty(), depth, OptionalDouble.empty());
        try {
            pass.setPipeline(RenderPipelines.ENTITY_TRANSLUCENT_EMISSIVE);
            RenderSystem.bindDefaultUniforms(pass);
            for (var texture : textures.entrySet()) pass.bindTexture(texture.getKey(), texture.getValue().textureView(), texture.getValue().sampler());
            return pass;
        } catch (RuntimeException | Error ex) { pass.close(); throw ex; }
    }
    void prepareTransform(Matrix4f view, Vec3 camera) {
            Matrix4f matrix = new Matrix4f(RenderSystem.getModelViewStack()).mul(view)
                    .translate((float) (x - camera.x()), (float) (y - camera.y()), (float) (z - camera.z()));
            transform = RenderSystem.getDynamicUniforms().writeTransform(matrix, new Vector4f(1, 1, 1, 1), new Vector3f(), new Matrix4f());
    }
    void draw(RenderPass pass) {
            pass.setUniform("DynamicTransforms", transform);
            pass.setVertexBuffer(0, vertices);
            pass.setIndexBuffer(indices, sorting.indexType());
            pass.drawIndexed(0, 0, indexCount, 1);
    }

    @Override public void close() {
        if (vertices != null) vertices.close();
        if (indices != null) indices.close();
        vertices = null; indices = null; sorting = null; transform = null;
        allocator.close();
    }
}
