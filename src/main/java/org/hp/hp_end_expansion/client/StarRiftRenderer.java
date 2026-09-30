package org.hp.hp_end_expansion.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.hp.hp_end_expansion.entity.starwreck.StarRiftEntity;

/**
 * 星雨裂隙：绕竖轴朝向镜头的撕裂口。张开时先从中点向上下拉出一条亮缝，再逐帧撕宽；闭合倒放。
 * 三层：身后一大片暗热光、镂空本体（带紫黑外缘）、加法辉光（芯补亮 + 外缘外三级热光）。
 */
public final class StarRiftRenderer extends EntityRenderer<StarRiftEntity> {
    private static final int FRAMES = 6;
    private static final float GLOW_WIDTH = StarRiftEntity.StarRiftVisual.WIDTH * 40F / 24F;

    public StarRiftRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0;
    }

    @Override public void render(StarRiftEntity entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
        float age = entity.visualAge(partialTick);
        float open = StarRiftEntity.openness(age);
        if (open <= 0) return;
        float length = Mth.clamp(open * 3, 0.06F, 1);
        float widen = Mth.clamp((open - 1F / 3) * 1.5F, 0, 1);
        int frame = Math.min(FRAMES - 1, (int) (widen * FRAMES));
        float pulse = 0.85F + 0.15F * Mth.sin(age * 0.55F) + 0.06F * Mth.sin(age * 1.7F);
        // 陨星离开那一拍芯里闪一下，只在芯内
        float flare = Math.max(0, 1 - Math.abs(age - StarRiftEntity.OPEN_TICKS) / 5F);

        Vec3 cam = entityRenderDispatcher.camera.getPosition();
        float face = (float) (Mth.atan2(cam.x - entity.getX(), cam.z - entity.getZ()) * Mth.RAD_TO_DEG);
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(face));
        PoseStack.Pose pose = poseStack.last();
        float h = StarRiftEntity.StarRiftVisual.HEIGHT * 0.5F * length;
        float v0 = 0.5F - length * 0.5F, v1 = 0.5F + length * 0.5F;
        float u0 = frame / (float) FRAMES, u1 = (frame + 1) / (float) FRAMES;

        float halo = open * pulse * 0.7F;
        StarfallDraw.rect(pose, buffers.getBuffer(StarfallDraw.additive(StarfallDraw.HALO)), -4.5F, -h * 1.8F, 4.5F, h * 1.8F, -0.08F,
            0, 0, 1, 1, 0.6F * halo, 0.34F * halo, 0.15F * halo);
        float w = StarRiftEntity.StarRiftVisual.WIDTH * 0.5F;
        StarfallDraw.rect(pose, buffers.getBuffer(StarfallDraw.solid(StarfallDraw.RIFT)), -w, -h, w, h, 0,
            u0, v0, u1, v1, 1, 1, 1);
        float glow = pulse * (0.7F + 0.3F * open) + flare * 0.9F;
        float gw = GLOW_WIDTH * 0.5F;
        StarfallDraw.rect(pose, buffers.getBuffer(StarfallDraw.additive(StarfallDraw.RIFT_GLOW)), -gw, -h, gw, h, 0.02F,
            u0, v0, u1, v1, glow, glow, glow);
        if (flare > 0) {
            StarfallDraw.rect(pose, buffers.getBuffer(StarfallDraw.additive(StarfallDraw.HALO)), -0.9F, -2.4F, 0.9F, 2.4F, 0.04F,
                0, 0, 1, 1, flare, 0.85F * flare, 0.55F * flare);
        }
        poseStack.popPose();
        super.render(entity, yaw, partialTick, poseStack, buffers, light);
    }

    @Override public boolean shouldRender(StarRiftEntity entity, Frustum frustum, double x, double y, double z) {
        return entity.shouldRender(x, y, z) && frustum.isVisible(entity.getBoundingBox().inflate(5, 7, 5));
    }

    @Override public ResourceLocation getTextureLocation(StarRiftEntity entity) { return StarfallDraw.RIFT; }
}
