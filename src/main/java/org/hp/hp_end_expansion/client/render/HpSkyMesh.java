package org.hp.hp_end_expansion.client.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.MeshData;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

public final class HpSkyMesh implements AutoCloseable {
    private GpuBuffer buffer;
    private MeshData.DrawState state;
    public boolean isInvalid() { return buffer==null || buffer.isClosed(); }
    public void upload(MeshData mesh) {
        close();
        try(mesh) { state=mesh.drawState();buffer=RenderSystem.getDevice().createBuffer(()->"HP End Expansion sky",GpuBuffer.USAGE_VERTEX,mesh.vertexBuffer()); }
    }
    public void draw(Matrix4fc matrix) {
        if(isInvalid()) return;
        var mc=Minecraft.getInstance();
        var uniforms=RenderSystem.getDynamicUniforms().writeTransform(matrix,HpSkyDraw.color,new Vector3f(),new Matrix4f());
        var indices=RenderSystem.getSequentialBuffer(state.mode());
        var indexBuffer=indices.getBuffer(state.indexCount());
        try(var pass=RenderSystem.getDevice().createCommandEncoder().createRenderPass(()->"HP End Expansion sky",mc.getMainRenderTarget().getColorTextureView(),OptionalInt.empty(),mc.getMainRenderTarget().getDepthTextureView(),OptionalDouble.empty())) {
            pass.setPipeline(HpSkyDraw.pipeline(state));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms",uniforms);
            if(HpSkyDraw.texture!=null) { var texture=mc.getTextureManager().getTexture(HpSkyDraw.texture);pass.bindTexture("Sampler0",texture.getTextureView(),texture.getSampler()); }
            pass.setVertexBuffer(0,buffer);
            pass.setIndexBuffer(indexBuffer,indices.type());
            pass.drawIndexed(0,0,state.indexCount(),1);
        }
    }
    @Override public void close() { if(buffer!=null) { buffer.close();buffer=null; } }
}
