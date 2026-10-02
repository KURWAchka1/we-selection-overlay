package org.marshal.weaddon_lightposition.client.preview;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.RenderPass;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.render.BuiltBuffer;
import com.mojang.blaze3d.systems.VertexSorter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import java.util.Optional;
import java.util.OptionalDouble;

/** Textured vertices are immutable; only transparent indices follow the camera. */
final class CommandPreviewGpuSection implements AutoCloseable {
    private static final RenderSetup SETUP = RenderSetup.builder(RenderPipelines.ENTITY_TRANSLUCENT_EMISSIVE)
            .texture("Sampler0", SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE).useOverlay().build();
    final int x, y, z;
    final Box bounds;
    private final BufferAllocator allocator = new BufferAllocator(32_768);
    private BufferBuilder builder;
    private GpuBuffer vertices, indices;
    private GpuBufferSlice transform;
    private BuiltBuffer.SortState sorting;
    private int indexCount;
    private double sortX = Double.NaN, sortY, sortZ;
    long vertexBytes;

    CommandPreviewGpuSection(int x, int y, int z) {
        this.x = x; this.y = y; this.z = z;
        bounds = new Box(x - .01, y - .01, z - .01, x + 16.01, y + 16.01, z + 16.01);
        builder = new BufferBuilder(allocator, RenderPipelines.ENTITY_TRANSLUCENT_EMISSIVE.getVertexFormatMode(),
                RenderPipelines.ENTITY_TRANSLUCENT_EMISSIVE.getVertexFormat());
    }
    BufferBuilder builder() { return builder; }
    boolean uploaded() { return builder == null; }
    boolean empty() { return vertices == null; }

    void upload(Vec3d camera) {
        try (BuiltBuffer data = builder.endNullable()) {
            builder = null;
            if (data == null) return;
            sorting = data.sortQuads(allocator, sorter(camera));
            indexCount = data.getDrawParameters().indexCount();
            vertexBytes = data.getBuffer().remaining();
            vertices = RenderSystem.getDevice().createBuffer(() -> "WESO preview vertices", GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, data.getBuffer());
            indices = RenderSystem.getDevice().createBuffer(() -> "WESO preview indices", GpuBuffer.USAGE_INDEX | GpuBuffer.USAGE_COPY_DST,
                    data.getSortedBuffer());
            rememberCamera(camera);
        } finally { allocator.close(); }
    }

    boolean needsSort(Vec3d camera) {
        double dx = camera.x - sortX, dy = camera.y - sortY, dz = camera.z - sortZ;
        return sorting != null && dx * dx + dy * dy + dz * dz >= 1;
    }
    void sort(Vec3d camera) {
        try (var temporary = new BufferAllocator(32_768);
             var data = sorting.sortAndStore(temporary, sorter(camera))) {
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(indices.slice(), data.getBuffer());
            rememberCamera(camera);
        }
    }
    private VertexSorter sorter(Vec3d camera) {
        return VertexSorter.byDistance((float) (camera.x - x), (float) (camera.y - y), (float) (camera.z - z));
    }
    private void rememberCamera(Vec3d camera) { sortX = camera.x; sortY = camera.y; sortZ = camera.z; }

    static RenderPass begin(MinecraftClient client) {
        var target = client.getFramebuffer();
        var color = RenderSystem.outputColorTextureOverride != null ? RenderSystem.outputColorTextureOverride : target.getColorAttachmentView();
        var depth = RenderSystem.outputDepthTextureOverride != null ? RenderSystem.outputDepthTextureOverride : target.getDepthAttachmentView();
        var textures = SETUP.resolveTextures();
        var pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "WESO command preview", color,
                java.util.OptionalInt.empty(), depth, OptionalDouble.empty());
        try {
            pass.setPipeline(RenderPipelines.ENTITY_TRANSLUCENT_EMISSIVE);
            RenderSystem.bindDefaultUniforms(pass);
            for (var texture : textures.entrySet()) pass.bindTexture(texture.getKey(), texture.getValue().textureView(), texture.getValue().sampler());
            return pass;
        } catch (RuntimeException | Error ex) { pass.close(); throw ex; }
    }
    void prepareTransform(Matrix4f view, Vec3d camera) {
            Matrix4f matrix = new Matrix4f(RenderSystem.getModelViewStack()).mul(view)
                    .translate((float) (x - camera.x), (float) (y - camera.y), (float) (z - camera.z));
            transform = RenderSystem.getDynamicUniforms().write(matrix, new Vector4f(1, 1, 1, 1), new Vector3f(), new Matrix4f());
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
