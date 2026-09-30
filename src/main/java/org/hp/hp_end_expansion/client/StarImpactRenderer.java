package org.hp.hp_end_expansion.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.hp.hp_end_expansion.entity.starwreck.StarImpactEntity;

/**
 * 陨星落地：一拍白热闪光和沿来路的残影，接着贴地的热浪环推到 4 格，环上立一圈不到 2 格高的热墙，
 * 地面留一片贴着方块顶面的焦痕，5 秒内从黄白冷到暗红再熄灭。
 */
public final class StarImpactRenderer extends EntityRenderer<StarImpactEntity> {
    private static final int SEGMENTS = 40;
    private static final int SCORCH_CELLS = 3;
    private static final float SCORCH_RADIUS = SCORCH_CELLS + 0.5F;

    public StarImpactRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0;
    }

    @Override public void render(StarImpactEntity entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
        float age = entity.tickCount + partialTick;
        PoseStack.Pose pose = poseStack.last();
        renderScorch(entity, age, pose, buffers);
        renderRing(age, pose, buffers);
        if (age < 4) {
            // 陨星实体在落点上方一拍就消失了，这道残影把最后几格补上
            Vec3 back = entity.getViewVector(1).scale(-1);
            Vec3 toCamera = entityRenderDispatcher.camera.getPosition().subtract(entity.position()).normalize();
            float k = 1 - age / 4;
            StarfallDraw.ribbon(pose, buffers.getBuffer(StarfallDraw.additive(StarfallDraw.TAIL)), new Vec3(0, 0.4, 0), back, toCamera,
                9 * k, 1.0F, 0.25F, 0, 0.25F, k, k, k);
        }
        if (age < 6) {
            float k = (1 - age / 6) * (1 - age / 6);
            float size = 3 + age * 1.3F;
            poseStack.pushPose();
            poseStack.translate(0, 0.6, 0);
            poseStack.mulPose(entityRenderDispatcher.cameraOrientation());
            StarfallDraw.rect(poseStack.last(), buffers.getBuffer(StarfallDraw.additive(StarfallDraw.HALO)), -size, -size, size, size, 0,
                0, 0, 1, 1, k, 0.82F * k, 0.55F * k);
            poseStack.popPose();
        }
        super.render(entity, yaw, partialTick, poseStack, buffers, light);
    }

    private void renderRing(float age, PoseStack.Pose pose, MultiBufferSource buffers) {
        int end = StarImpactEntity.RING_TICKS + 5;
        if (age >= end) return;
        float t = Mth.clamp(age / StarImpactEntity.RING_TICKS, 0, 1);
        float radius = StarImpactEntity.ringRadius(age);
        float inner = Math.max(0, radius - 1.1F * (1 - 0.5F * t));
        float bright = (1 - 0.35F * t) * Mth.clamp((end - age) / 5, 0, 1);
        float wall = 1.7F * (float) Math.pow(1 - t * 0.85F, 0.7F);
        VertexConsumer buffer = buffers.getBuffer(StarfallDraw.additive(StarfallDraw.RING));
        for (int i = 0; i < SEGMENTS; i++) {
            float a0 = i * Mth.TWO_PI / SEGMENTS, a1 = (i + 1) * Mth.TWO_PI / SEGMENTS;
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            float u0 = i * 4F / SEGMENTS, u1 = (i + 1) * 4F / SEGMENTS;
            float y = 0.08F;
            StarfallDraw.quad(pose, buffer,
                new Vec3(c0 * inner, y, s0 * inner), new Vec3(c1 * inner, y, s1 * inner),
                new Vec3(c1 * radius, y, s1 * radius), new Vec3(c0 * radius, y, s0 * radius),
                u0, 0, u1, 1, bright, 0.9F * bright, 0.8F * bright);
            float wb = 0.6F * bright;
            StarfallDraw.quad(pose, buffer,
                new Vec3(c0 * radius, wall, s0 * radius), new Vec3(c1 * radius, wall, s1 * radius),
                new Vec3(c1 * radius, 0.05, s1 * radius), new Vec3(c0 * radius, 0.05, s0 * radius),
                u0, 0, u1, 1, wb, 0.85F * wb, 0.7F * wb);
        }
    }

    private void renderScorch(StarImpactEntity entity, float age, PoseStack.Pose pose, MultiBufferSource buffers) {
        float grow = Mth.clamp(age / 6, 0, 1);
        float bright = grow * Mth.clamp((StarImpactEntity.LIFE - age) / (StarImpactEntity.LIFE - 12F), 0, 1);
        if (bright <= 0) return;
        float heat = Mth.clamp(1 - age / 40, 0, 1);
        float r = bright, g = bright * (0.35F + 0.5F * heat), b = bright * (0.12F + 0.35F * heat);
        if (entity.ground == null) entity.ground = scanGround(entity);
        VertexConsumer buffer = buffers.getBuffer(StarfallDraw.additive(StarfallDraw.SCORCH));
        int size = SCORCH_CELLS * 2 + 1;
        double ox = Mth.floor(entity.getX()) - entity.getX(), oz = Mth.floor(entity.getZ()) - entity.getZ();
        for (int dx = -SCORCH_CELLS; dx <= SCORCH_CELLS; dx++) {
            for (int dz = -SCORCH_CELLS; dz <= SCORCH_CELLS; dz++) {
                float h = entity.ground[(dx + SCORCH_CELLS) * size + dz + SCORCH_CELLS];
                if (Float.isNaN(h) || dx * dx + dz * dz > (SCORCH_CELLS + 0.6F) * (SCORCH_CELLS + 0.6F)) continue;
                double x0 = ox + dx, z0 = oz + dz, y = h + 0.02;
                float u0 = (float) ((x0 + SCORCH_RADIUS) / (2 * SCORCH_RADIUS)), u1 = u0 + 1 / (2 * SCORCH_RADIUS);
                float v0 = (float) ((z0 + SCORCH_RADIUS) / (2 * SCORCH_RADIUS)), v1 = v0 + 1 / (2 * SCORCH_RADIUS);
                StarfallDraw.quad(pose, buffer, new Vec3(x0, y, z0), new Vec3(x0 + 1, y, z0), new Vec3(x0 + 1, y, z0 + 1), new Vec3(x0, y, z0 + 1),
                    Mth.clamp(u0, 0, 1), Mth.clamp(v0, 0, 1), Mth.clamp(u1, 0, 1), Mth.clamp(v1, 0, 1), r, g, b);
            }
        }
    }

    // 每格从落点上方 2 格往下找第一块顶上是空的实心方块，焦痕就贴在它顶面
    private static float[] scanGround(StarImpactEntity entity) {
        Level level = entity.level();
        int size = SCORCH_CELLS * 2 + 1;
        float[] out = new float[size * size];
        int bx = Mth.floor(entity.getX()), by = Mth.floor(entity.getY()), bz = Mth.floor(entity.getZ());
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -SCORCH_CELLS; dx <= SCORCH_CELLS; dx++) {
            for (int dz = -SCORCH_CELLS; dz <= SCORCH_CELLS; dz++) {
                float found = Float.NaN;
                for (int y = by + 2; y >= by - 3; y--) {
                    pos.set(bx + dx, y, bz + dz);
                    if (level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) continue;
                    pos.setY(y + 1);
                    if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) break;
                    found = (float) (y + 1 - entity.getY());
                    break;
                }
                out[(dx + SCORCH_CELLS) * size + dz + SCORCH_CELLS] = found;
            }
        }
        return out;
    }

    @Override public boolean shouldRender(StarImpactEntity entity, Frustum frustum, double x, double y, double z) {
        return entity.shouldRender(x, y, z) && frustum.isVisible(entity.getBoundingBox().inflate(6, 9, 6));
    }

    @Override public ResourceLocation getTextureLocation(StarImpactEntity entity) { return StarfallDraw.SCORCH; }
}
