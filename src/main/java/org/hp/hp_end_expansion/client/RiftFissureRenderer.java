package org.hp.hp_end_expansion.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.hp.hp_end_expansion.entity.RiftFissureEntity;

public final class RiftFissureRenderer extends EntityRenderer<RiftFissureEntity> {
    public RiftFissureRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(RiftFissureEntity entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
        float age = entity.tickCount + partialTick;
        float length = entity.getLength();
        poseStack.pushPose();
        // 局部 +Z 为地裂前进方向
        poseStack.mulPose(Axis.YP.rotationDegrees(-entity.getYRot()));
        this.renderCrack(entity, age, length, poseStack, buffers);
        if (age >= RiftFissureEntity.ERUPT_TICK - 1) {
            this.renderSpikes(entity, age, length, poseStack, buffers);
        }
        poseStack.popPose();
        super.render(entity, yaw, partialTick, poseStack, buffers, light);
    }

    // 地面裂缝：分段折线向前蔓延，爆发前逐渐变亮，结束时淡出
    private void renderCrack(RiftFissureEntity entity, float age, float length, PoseStack poseStack, MultiBufferSource buffers) {
        float reach = length * Mth.clamp(age / RiftFissureEntity.SPREAD_TICKS, 0.0F, 1.0F);
        float charge = Mth.clamp((age - RiftFissureEntity.SPREAD_TICKS) / (RiftFissureEntity.ERUPT_TICK - RiftFissureEntity.SPREAD_TICKS), 0.0F, 1.0F);
        float fadeOut = Mth.clamp((RiftFissureEntity.LIFE_TICKS - age) / 8.0F, 0.0F, 1.0F);
        float bright = (0.55F + 0.45F * charge) * fadeOut;
        if (reach <= 0.01F || bright <= 0.0F) {
            return;
        }
        PoseStack.Pose pose = poseStack.last();
        VertexConsumer buffer = buffers.getBuffer(RiftVfxDraw.additive(RiftVfxDraw.CRACK));
        RandomSource rand = RandomSource.create(entity.getId() * 31L);
        int segments = Math.max(2, Mth.ceil(length / 0.75F));
        float step = length / segments;
        float prevX = 0.0F;
        float drift = 0.0F;
        int color = RiftVfxDraw.fade(bright);
        for (int i = 0; i < segments; i++) {
            float z0 = i * step;
            if (z0 >= reach) {
                break;
            }
            float z1 = Math.min((i + 1) * step, reach);
            float progress = (float) (i + 1) / segments;
            // 横向漂移带记忆，两端收束
            float target = (rand.nextFloat() - 0.5F) * 0.9F * entity.getWidth() * Mth.sin(Mth.PI * progress);
            drift = drift * 0.6F + target * 0.4F;
            float x1 = drift;
            float w0 = (0.35F + 0.25F * Mth.sin(Mth.PI * (float) i / segments)) * entity.getWidth();
            float w1 = (0.35F + 0.25F * Mth.sin(Mth.PI * progress)) * entity.getWidth();
            float y = 0.03F;
            Vec3 a = new Vec3(prevX - w0, y, z0);
            Vec3 b = new Vec3(prevX + w0, y, z0);
            Vec3 c = new Vec3(x1 + w1, y, z1);
            Vec3 d = new Vec3(x1 - w1, y, z1);
            float v0 = z0 / 2.0F;
            float v1 = z1 / 2.0F;
            RiftVfxDraw.quad(pose, buffer, a, b, c, d, 0.0F, v0, 1.0F, v1, color, false);
            prevX = x1;
        }
    }

    // 晶刺：沿裂线交错钻出，十字双面片，冒出后回落
    private void renderSpikes(RiftFissureEntity entity, float age, float length, PoseStack poseStack, MultiBufferSource buffers) {
        RandomSource rand = RandomSource.create(entity.getId() * 131L + 7L);
        VertexConsumer buffer = buffers.getBuffer(RiftVfxDraw.translucent(RiftVfxDraw.SPIKE));
        float width = entity.getWidth();
        int count = Math.max(3, Mth.floor(length / (1.1F * width)));
        for (int i = 0; i < count; i++) {
            float z = (i + 0.5F) * length / count + (rand.nextFloat() - 0.5F) * 0.4F;
            float x = (rand.nextFloat() - 0.5F) * 0.8F * width;
            float delay = i * 0.6F;
            float t = age - RiftFissureEntity.ERUPT_TICK + 1.0F - delay;
            if (t <= 0.0F) {
                continue;
            }
            // 迅速弹出、短暂停留、下沉退回
            float rise = Mth.clamp(t / 2.0F, 0.0F, 1.0F);
            float sink = Mth.clamp((t - 9.0F) / 6.0F, 0.0F, 1.0F);
            float height = (1.3F + rand.nextFloat() * 0.9F) * width * rise * (1.0F - sink);
            float half = (0.32F + rand.nextFloat() * 0.12F) * width;
            float tilt = (rand.nextFloat() - 0.5F) * 30.0F;
            float spin = rand.nextFloat() * 90.0F;
            if (height <= 0.02F) {
                continue;
            }
            float alpha = 1.0F - sink * 0.6F;
            poseStack.pushPose();
            poseStack.translate(x, 0.0D, z);
            poseStack.mulPose(Axis.YP.rotationDegrees(spin));
            poseStack.mulPose(Axis.XP.rotationDegrees(tilt));
            PoseStack.Pose pose = poseStack.last();
            int color = RiftVfxDraw.alpha(alpha);
            // 两片互相垂直的面，保证任意角度可见
            RiftVfxDraw.quad(pose, buffer,
                new Vec3(0.0F, height, 0.0F), new Vec3(0.0F, height, 0.0F), new Vec3(half, -0.1F, 0.0F), new Vec3(-half, -0.1F, 0.0F),
                0.0F, 0.0F, 1.0F, 1.0F, color, true);
            RiftVfxDraw.quad(pose, buffer,
                new Vec3(0.0F, height, 0.0F), new Vec3(0.0F, height, 0.0F), new Vec3(0.0F, -0.1F, half), new Vec3(0.0F, -0.1F, -half),
                0.0F, 0.0F, 1.0F, 1.0F, color, true);
            poseStack.popPose();
        }
    }

    @Override
    public boolean shouldRender(RiftFissureEntity entity, Frustum frustum, double x, double y, double z) {
        return frustum.isVisible(entity.getBoundingBoxForCulling());
    }

    @Override
    public ResourceLocation getTextureLocation(RiftFissureEntity entity) {
        return RiftVfxDraw.CRACK;
    }
}
