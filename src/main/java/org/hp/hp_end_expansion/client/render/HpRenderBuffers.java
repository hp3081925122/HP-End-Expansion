package org.hp.hp_end_expansion.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;

public final class HpRenderBuffers implements MultiBufferSource {
    private final Map<RenderType, Buffer> buffers = new LinkedHashMap<>();
    @Override public VertexConsumer getBuffer(RenderType type) { return buffers.computeIfAbsent(type, key -> new Buffer()); }
    public void submit(SubmitNodeCollector collector) {
        buffers.forEach((type, buffer) -> {
            if (!buffer.vertices.isEmpty()) {
                List<Vertex> vertices = List.copyOf(buffer.vertices);
                collector.submitCustomGeometry(new PoseStack(), type, (pose, consumer) -> {
                    for (Vertex v : vertices) consumer.addVertex(pose, v.x, v.y, v.z).setColor(v.color).setUv(v.u, v.v)
                        .setUv1(v.overlayU, v.overlayV).setUv2(v.lightU, v.lightV).setNormal(pose, v.nx, v.ny, v.nz).setLineWidth(v.width);
                });
            }
        });
    }
    private static final class Vertex {
        float x, y, z, u, v, nx, ny = 1, nz, width = 1;
        int color = -1, overlayU, overlayV, lightU, lightV;
    }
    private static final class Buffer implements VertexConsumer {
        final List<Vertex> vertices = new ArrayList<>();
        Vertex current;
        @Override public VertexConsumer addVertex(float x, float y, float z) { current = new Vertex(); current.x=x; current.y=y; current.z=z; vertices.add(current); return this; }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { return setColor((a<<24)|(r<<16)|(g<<8)|b); }
        @Override public VertexConsumer setColor(int color) { current.color=color; return this; }
        @Override public VertexConsumer setUv(float u, float v) { current.u=u; current.v=v; return this; }
        @Override public VertexConsumer setUv1(int u, int v) { current.overlayU=u; current.overlayV=v; return this; }
        @Override public VertexConsumer setUv2(int u, int v) { current.lightU=u; current.lightV=v; return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { current.nx=x; current.ny=y; current.nz=z; return this; }
        @Override public VertexConsumer setLineWidth(float width) { current.width=width; return this; }
    }
}
