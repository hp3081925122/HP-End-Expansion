package org.hp.hp_end_expansion.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.hp.hp_end_expansion.entity.HpCullingEntity;

public class HpEntityRenderer<T extends Entity> extends EntityRenderer<T, HpEntityRenderer.State<T>> {
    protected CameraRenderState cameraRenderState;
    public HpEntityRenderer(EntityRendererProvider.Context context) { super(context); }
    public static class State<T extends Entity> extends EntityRenderState { public T entity; }
    @Override public State<T> createRenderState() { return new State<>(); }
    @Override public void extractRenderState(T entity, State<T> state, float partialTick) { super.extractRenderState(entity,state,partialTick); state.entity=entity; }
    @Override protected AABB getBoundingBoxForCulling(T entity) { return entity instanceof HpCullingEntity custom ? custom.getBoundingBoxForCulling() : super.getBoundingBoxForCulling(entity); }
    public Identifier getTextureLocation(T entity) { return null; }
    public void render(T entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {}
    @Override public void submit(State<T> state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        cameraRenderState=camera;
        HpRenderBuffers buffers=new HpRenderBuffers();
        render(state.entity,state.entity.getYRot(),state.partialTick,poseStack,buffers,state.lightCoords);
        buffers.submit(collector);
        super.submit(state,poseStack,collector,camera);
    }
}
