package org.hp.hp_end_expansion.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.hp.hp_end_expansion.entity.StarCoreEntity;

public final class StarCoreRenderer extends EntityRenderer<StarCoreEntity> {
    public StarCoreRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(StarCoreEntity entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
        float age = entity.tickCount + partialTick;
        float pulse = 0.85F + 0.15F * Mth.sin(age * 0.4F);
        // 面向镜头的星核光斑，受击时变暗闪烁
        poseStack.pushPose();
        poseStack.translate(0.0D, 0.6D, 0.0D);
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        poseStack.mulPose(Axis.ZP.rotationDegrees(age * 6.0F));
        VertexConsumer buffer = buffers.getBuffer(RiftVfxDraw.additive(VoidRayDraw.STAR));
        float s = 1.1F * pulse;
        float bright = 1.0F;
        if (entity.hurtTime > 0) {
            bright = 0.5F;
        }
        RiftVfxDraw.quad(poseStack.last(), buffer, new Vec3(-s, s, 0.0D), new Vec3(s, s, 0.0D), new Vec3(s, -s, 0.0D), new Vec3(-s, -s, 0.0D),
            0.0F, 0.0F, 1.0F, 1.0F, RiftVfxDraw.fade(bright), true);
        poseStack.popPose();
        super.render(entity, yaw, partialTick, poseStack, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(StarCoreEntity entity) {
        return VoidRayDraw.STAR;
    }
}
