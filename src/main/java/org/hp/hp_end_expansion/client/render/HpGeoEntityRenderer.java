package org.hp.hp_end_expansion.client.render;

import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.animation.state.AnimationTest;
import com.geckolib.constant.DataTickets;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.GeoRenderLayer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.hp.hp_end_expansion.entity.HpCullingEntity;
import org.joml.Matrix4f;

public class HpGeoEntityRenderer<T extends Entity & GeoAnimatable> extends GeoEntityRenderer<T,HpGeoRenderState> {
    protected Matrix4f entityRenderTranslations=new Matrix4f();
    private SubmitNodeCollector activeCollector;
    private CameraRenderState activeCamera;
    private HpGeoRenderState activeState;
    public HpGeoEntityRenderer(EntityRendererProvider.Context context,HpGeoModel<T> model) { super(context,model); }
    @Override public HpGeoRenderState createRenderState(T entity,Void context) { HpGeoRenderState state=new HpGeoRenderState(); state.entity=entity; return state; }
    @Override protected AABB getBoundingBoxForCulling(T entity) { return entity instanceof HpCullingEntity custom ? custom.getBoundingBoxForCulling() : super.getBoundingBoxForCulling(entity); }
    public void addRenderLayer(GeoRenderLayer<T,Void,HpGeoRenderState> layer) { withRenderLayer(layer); }
    protected float getDeathMaxRotation(T entity) { return 90; }
    @SuppressWarnings("unchecked") @Override protected float getDeathMaxRotation(GeoRenderState state) { return getDeathMaxRotation((T)((HpGeoRenderState)state).entity); }
    public boolean shouldShowName(T entity) { return super.shouldShowName(entity,entityRenderDispatcher.distanceToSqr(entity)); }
    @Override public boolean shouldShowName(T entity,double distance) { return shouldShowName(entity) && super.shouldShowName(entity,distance); }
    public net.minecraft.client.renderer.rendertype.RenderType getRenderType(T entity,net.minecraft.resources.Identifier texture,MultiBufferSource buffers,float partialTick) { return net.minecraft.client.renderer.rendertype.RenderTypes.entityCutout(texture); }
    @SuppressWarnings("unchecked") @Override public net.minecraft.client.renderer.rendertype.RenderType getRenderType(HpGeoRenderState state,net.minecraft.resources.Identifier texture) {
        if (state.isInvisible) return super.getRenderType(state,texture);
        return getRenderType((T)state.entity,texture,null,state.partialTick);
    }
    protected void submitItem(T entity,net.minecraft.world.item.ItemStack stack,net.minecraft.world.item.ItemDisplayContext context,PoseStack pose,int light,int overlay) {
        var state=new net.minecraft.client.renderer.item.ItemStackRenderState();
        net.minecraft.client.Minecraft.getInstance().getItemModelResolver().updateForNonLiving(state,stack,context,entity);
        state.submit(pose,activeCollector,light,overlay,0);
    }
    public void render(T entity,float yaw,float partialTick,PoseStack poseStack,MultiBufferSource buffers,int light) {
        super.submit(activeState,poseStack,activeCollector,activeCamera);
    }
    @SuppressWarnings("unchecked") @Override public void submit(HpGeoRenderState state,PoseStack poseStack,SubmitNodeCollector collector,CameraRenderState camera) {
        activeState=state; activeCollector=collector; activeCamera=camera;
        HpRenderBuffers buffers=new HpRenderBuffers();
        render((T)state.entity,state.entity.getYRot(),state.partialTick,poseStack,buffers,state.lightCoords);
        buffers.submit(collector);
        activeState=null; activeCollector=null; activeCamera=null;
    }
    public void preRender(PoseStack poseStack,T entity,HpBakedModel model,MultiBufferSource buffers,VertexConsumer consumer,boolean reRender,float partialTick,int light,int overlay,int color) {}
    public void renderFinal(PoseStack poseStack,T entity,HpBakedModel model,MultiBufferSource buffers,VertexConsumer consumer,float partialTick,int light,int overlay,int color) {}
    @SuppressWarnings("unchecked") @Override public void preRenderPass(RenderPassInfo<HpGeoRenderState> pass,SubmitNodeCollector collector) {
        super.preRenderPass(pass,collector);
        HpGeoRenderState state=pass.renderState();
        state.model=new HpBakedModel(pass.model());
        HpGeoModel<T> model=(HpGeoModel<T>)getGeoModel();
        model.activeModel=state.model;
        model.setCustomAnimations((T)state.entity,getInstanceId((T)state.entity,null),new AnimationTest<>((T)state.entity,state,null,null));
        model.activeModel=null;
        entityRenderTranslations=new Matrix4f(pass.getPreRenderMatrixState());
        preRender(pass.poseStack(),(T)state.entity,state.model,null,null,false,state.partialTick,pass.packedLight(),pass.packedOverlay(),pass.renderColor());
        for(HpBone bone:state.model.bones.values()) if(bone.tracking) pass.addPerBoneRender(bone.bone,(info,source,tasks)-> {
            bone.localMatrix.set(info.getPreRenderMatrixState()).invert().mul(info.poseStack().last().pose());
        });
    }
    @Override public void adjustModelBonesForRender(RenderPassInfo<HpGeoRenderState> pass,BoneSnapshots snapshots) {
        super.adjustModelBonesForRender(pass,snapshots);
        for(HpBone bone:pass.renderState().model.bones.values()) bone.apply(snapshots.get(bone.bone));
    }
    @SuppressWarnings("unchecked") @Override public void postRenderPass(RenderPassInfo<HpGeoRenderState> pass,SubmitNodeCollector collector) {
        super.postRenderPass(pass,collector);
        HpRenderBuffers buffers=new HpRenderBuffers();
        renderFinal(pass.poseStack(),(T)pass.renderState().entity,pass.renderState().model,buffers,null,pass.renderState().partialTick,pass.packedLight(),pass.packedOverlay(),pass.renderColor());
        buffers.submit(collector);
    }
}
