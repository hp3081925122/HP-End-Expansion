package org.hp.hp_end_expansion.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.hp.hp_end_expansion.entity.starwreck.StarMarkEntity;

/**
 * 召星落点圈：外圈热浪边框一直在，内圈亮面从中心往外涨，涨满时陨星正好落下；
 * 天上一道细光柱指向落点，越临近越粗越亮。陨星落地后整圈闪一下再消失。
 */
public final class StarMarkRenderer extends EntityRenderer<StarMarkEntity> {
    private static final int SEGMENTS = 28;

    public StarMarkRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(StarMarkEntity mark, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
        float age = mark.tickCount + partialTick;
        float charge = mark.charge(partialTick);
        float after = Math.max(0, age - StarMarkEntity.WARN_TICKS) / (StarMarkEntity.LIFE - StarMarkEntity.WARN_TICKS);
        float out = Mth.clamp(1 - after, 0, 1);
        float appear = Mth.clamp(age / 4, 0, 1);
        float pulse = 0.7F + 0.3F * Mth.sin(age * (0.35F + 1.4F * charge));
        float radius = StarMarkEntity.RADIUS;
        PoseStack.Pose pose = poseStack.last();

        VertexConsumer halo = buffers.getBuffer(StarfallDraw.additive(StarfallDraw.HALO));
        float hb = appear * out * (0.35F + 0.65F * charge * charge);
        float hs = radius * (0.4F + 0.9F * charge);
        for (int i = 0; i < 2; i++)
            StarfallDraw.quad(pose, halo, new Vec3(-hs, 0.06, -hs), new Vec3(hs, 0.06, -hs), new Vec3(hs, 0.06, hs), new Vec3(-hs, 0.06, hs),
                0, 0, 1, 1, hb, 0.8F * hb, 0.5F * hb);

        VertexConsumer ring = buffers.getBuffer(StarfallDraw.additive(StarfallDraw.RING));
        float edge = appear * pulse * (after > 0 ? 1.4F * out : 0.75F + 0.25F * charge);
        annulus(pose, ring, radius - 0.35F, radius, 0.08F, Math.min(1, edge), age * 0.02F);
        // 涨满的亮边：从中心往外推，碰到外圈时陨星落地
        float fill = radius * charge;
        if (after <= 0 && fill > 0.2F) annulus(pose, ring, Math.max(0, fill - 0.45F), fill, 0.07F, 0.55F + 0.45F * charge, -age * 0.03F);

        VertexConsumer tail = buffers.getBuffer(StarfallDraw.additive(StarfallDraw.TAIL));
        float bb = appear * out * (0.25F + 0.75F * charge) * pulse;
        if (bb > 0.01F) {
            Vec3 toCamera = entityRenderDispatcher.camera.getPosition().subtract(mark.getPosition(partialTick));
            StarfallDraw.ribbon(pose, tail, new Vec3(0, 0.05, 0), new Vec3(0, 1, 0), toCamera, 22,
                0.08F + 0.35F * charge, 0.03F, 0, 0.25F, bb, 0.85F * bb, 0.55F * bb);
        }
        super.render(mark, yaw, partialTick, poseStack, buffers, light);
    }

    private static void annulus(PoseStack.Pose pose, VertexConsumer buffer, float inner, float outer, float y, float b, float spin) {
        for (int i = 0; i < SEGMENTS; i++) {
            float a0 = i * Mth.TWO_PI / SEGMENTS + spin, a1 = (i + 1) * Mth.TWO_PI / SEGMENTS + spin;
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            float u0 = i * 4F / SEGMENTS, u1 = (i + 1) * 4F / SEGMENTS;
            StarfallDraw.quad(pose, buffer,
                new Vec3(c0 * inner, y, s0 * inner), new Vec3(c1 * inner, y, s1 * inner),
                new Vec3(c1 * outer, y, s1 * outer), new Vec3(c0 * outer, y, s0 * outer),
                u0, 0, u1, 1, b, 0.85F * b, 0.6F * b);
        }
    }

    @Override public ResourceLocation getTextureLocation(StarMarkEntity entity) { return StarfallDraw.RING; }
}
