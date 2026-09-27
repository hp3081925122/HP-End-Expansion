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
import org.hp.hp_end_expansion.entity.RiftBladeEntity;

public final class RiftBladeRenderer extends EntityRenderer<RiftBladeEntity> {
    // 月牙弧段数
    private static final int SEGMENTS = 10;

    public RiftBladeRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(RiftBladeEntity entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
        float age = entity.tickCount + partialTick;
        float grow = Mth.clamp(age / 2.0F, 0.0F, 1.0F) * 1.3F;
        poseStack.pushPose();
        poseStack.translate(0.0D, 0.2D, 0.0D);
        // 沿飞行方向摆正，月牙凸面朝前
        poseStack.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partialTick, entity.yRotO, entity.getYRot())));
        poseStack.mulPose(Axis.XP.rotationDegrees(-Mth.lerp(partialTick, entity.xRotO, entity.getXRot())));
        poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.sin(age * 0.6F) * 6.0F));
        PoseStack.Pose pose = poseStack.last();
        VertexConsumer buffer = buffers.getBuffer(RiftVfxDraw.additive(RiftVfxDraw.ARC));
        // 主月牙与外层淡辉两层
        this.crescent(pose, buffer, 0.75F * grow, 0.42F * grow, RiftVfxDraw.fade(1.0F));
        this.crescent(pose, buffer, 0.95F * grow, 0.72F * grow, RiftVfxDraw.fade(0.35F));
        poseStack.popPose();
        super.render(entity, yaw, partialTick, poseStack, buffers, light);
    }

    // 水平月牙：外弧在 +Z 前方，两端向后收尖
    private void crescent(PoseStack.Pose pose, VertexConsumer buffer, float outer, float inner, int color) {
        for (int i = 0; i < SEGMENTS; i++) {
            float t0 = (float) i / SEGMENTS;
            float t1 = (float) (i + 1) / SEGMENTS;
            float a0 = (-80.0F + 160.0F * t0) * Mth.DEG_TO_RAD;
            float a1 = (-80.0F + 160.0F * t1) * Mth.DEG_TO_RAD;
            float taper0 = Mth.sin(Mth.PI * t0);
            float taper1 = Mth.sin(Mth.PI * t1);
            float in0 = outer - (outer - inner) * taper0;
            float in1 = outer - (outer - inner) * taper1;
            Vec3 o0 = new Vec3(Mth.sin(a0) * outer, 0.0D, Mth.cos(a0) * outer - outer * 0.5F);
            Vec3 o1 = new Vec3(Mth.sin(a1) * outer, 0.0D, Mth.cos(a1) * outer - outer * 0.5F);
            Vec3 i1 = new Vec3(Mth.sin(a1) * in1, 0.0D, Mth.cos(a1) * in1 - outer * 0.5F);
            Vec3 i0 = new Vec3(Mth.sin(a0) * in0, 0.0D, Mth.cos(a0) * in0 - outer * 0.5F);
            RiftVfxDraw.quad(pose, buffer, o0, o1, i1, i0, t0, 0.0F, t1, 1.0F, color, true);
        }
    }

    @Override
    public ResourceLocation getTextureLocation(RiftBladeEntity entity) {
        return RiftVfxDraw.ARC;
    }
}
