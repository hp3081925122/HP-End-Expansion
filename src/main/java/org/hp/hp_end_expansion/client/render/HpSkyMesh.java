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
    private static final org.slf4j.Logger LOGGER=com.mojang.logging.LogUtils.getLogger();
    private static final java.util.Set<net.minecraft.resources.Identifier> LOGGED_TEXTURES=new java.util.HashSet<>();
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
        var colorTarget=RenderSystem.outputColorTextureOverride!=null ? RenderSystem.outputColorTextureOverride : mc.getMainRenderTarget().getColorTextureView();
        var depthTarget=RenderSystem.outputDepthTextureOverride!=null ? RenderSystem.outputDepthTextureOverride : mc.getMainRenderTarget().getDepthTextureView();
        var texture=HpSkyDraw.texture!=null ? mc.getTextureManager().getTexture(HpSkyDraw.texture) : null;
        if(texture!=null && LOGGED_TEXTURES.add(HpSkyDraw.texture)) LOGGER.debug("Sky texture ready before render pass: {}",HpSkyDraw.texture);
        try(var pass=RenderSystem.getDevice().createCommandEncoder().createRenderPass(()->"HP End Expansion sky",colorTarget,OptionalInt.empty(),depthTarget,OptionalDouble.empty())) {
            pass.setPipeline(HpSkyDraw.pipeline(state));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms",uniforms);
            if(texture!=null) pass.bindTexture("Sampler0",texture.getTextureView(),texture.getSampler());
            pass.setVertexBuffer(0,buffer);
            pass.setIndexBuffer(indexBuffer,indices.type());
            pass.drawIndexed(0,0,state.indexCount(),1);
        }
    }
    @Override public void close() { if(buffer!=null) { buffer.close();buffer=null; } }
}
