package org.hp.hp_end_expansion.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.hp.hp_end_expansion.entity.starwreck.StarShardEntity;
import org.joml.Quaternionf;

/** 抛出的碎星：金色圣辉裹着一颗白芯，十字光芒随飞行旋转闪烁，身后拖一条白金火舌。 */
public final class StarShardRenderer extends EntityRenderer<StarShardEntity> {
    public StarShardRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0;
    }

    @Override public void render(StarShardEntity entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
        float age = entity.tickCount + partialTick;
        Quaternionf camera = entityRenderDispatcher.cameraOrientation();
        Vec3 v = entity.getDeltaMovement();
        if (v.lengthSqr() > 1.0E-6) {
            Vec3 toCamera = entityRenderDispatcher.camera.getPosition().subtract(entity.getPosition(partialTick)).normalize();
            StarfallDraw.ribbon(poseStack.last(), BearerVfxRenderer.add(buffers, BearerVfxRenderer.FLARE), Vec3.ZERO, v.normalize().scale(-1), toCamera,
                2.4F, 0.3F, 0.02F, 0, 1, 1, 0.8F, 0.45F);
        }
        float pulse = 0.85F + 0.15F * (float) Math.sin(age * 1.3);
        BearerVfxRenderer.glow(poseStack, buffers, camera, Vec3.ZERO, 0.8F * pulse, age * 8, 1, 0.82F, 0.5F);
        BearerVfxRenderer.glow(poseStack, buffers, camera, Vec3.ZERO, 0.35F, -age * 20, 1, 0.97F, 0.88F);
        poseStack.pushPose();
        poseStack.mulPose(camera);
        poseStack.mulPose(Axis.ZP.rotationDegrees(age * 12));
        var spike = BearerVfxRenderer.add(buffers, BearerVfxRenderer.SPIKE);
        float len = 0.9F * pulse;
        // 两道互相垂直的光刺组成十字，从中心向两头各伸一半
        StarfallDraw.rect(poseStack.last(), spike, -0.06F, 0, 0.06F, len, 0, 0, 1, 1, 0, 1, 0.92F, 0.7F);
        StarfallDraw.rect(poseStack.last(), spike, -0.06F, -len, 0.06F, 0, 0, 0, 0, 1, 1, 1, 0.92F, 0.7F);
        poseStack.mulPose(Axis.ZP.rotationDegrees(90));
        StarfallDraw.rect(poseStack.last(), spike, -0.06F, 0, 0.06F, len, 0, 0, 1, 1, 0, 1, 0.92F, 0.7F);
        StarfallDraw.rect(poseStack.last(), spike, -0.06F, -len, 0.06F, 0, 0, 0, 0, 1, 1, 1, 0.92F, 0.7F);
        poseStack.popPose();
        super.render(entity, yaw, partialTick, poseStack, buffers, light);
    }

    @Override public ResourceLocation getTextureLocation(StarShardEntity entity) { return BearerVfxRenderer.GLOW; }
}
