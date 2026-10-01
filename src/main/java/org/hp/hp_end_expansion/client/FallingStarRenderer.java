package org.hp.hp_end_expansion.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.hp.hp_end_expansion.entity.starwreck.FallingStarEntity;
import org.joml.Quaternionf;

/**
 * 陨星：五块互相咬合的星骸岩团成约 1.5 格的不规则石块，裂缝单独一层加法发光；
 * 身后三层朝镜头的尾焰沿位移反方向拉出，长度跟速度走，所以刚出裂隙时很短。
 */
public final class FallingStarRenderer extends EntityRenderer<FallingStarEntity> {
    // 每块：中心 xyz、尺寸 xyz、绕 y / x 的角度、贴图 u/v 偏移
    private static final float[][] LUMPS = {
        {0, 0, 0, 1.1F, 0.95F, 1.0F, 0, 0, 0, 0},
        {0.36F, 0.24F, -0.12F, 0.62F, 0.6F, 0.7F, 25, 12, 0.5F, 0},
        {-0.32F, -0.24F, 0.2F, 0.72F, 0.55F, 0.62F, -20, -8, 0, 0.5F},
        {0.06F, 0.36F, 0.34F, 0.5F, 0.46F, 0.52F, 40, 20, 0.5F, 0.5F},
        {-0.22F, 0.12F, -0.42F, 0.56F, 0.5F, 0.46F, -35, 0, 0.25F, 0.25F},
    };
    private static final Vec3 CENTER = new Vec3(0, 0.7, 0);

    public FallingStarRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.6F;
    }

    @Override public void render(FallingStarEntity entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
        float age = entity.tickCount + partialTick;
        Vec3 velocity = entity.getDeltaMovement();
        if (velocity.lengthSqr() < 1.0E-6) velocity = entity.position().subtract(entity.xo, entity.yo, entity.zo);
        double speed = velocity.length();
        Vec3 dir = speed > 1.0E-4 ? velocity.scale(1 / speed) : new Vec3(0, -1, 0);
        // 刚从芯里挤出来时整体偏小、偏亮，几拍后长到原样
        float emerge = Mth.clamp(age / 5, 0, 1);
        Vec3 world = entity.getPosition(partialTick).add(CENTER);
        Vec3 toCamera = entityRenderDispatcher.camera.getPosition().subtract(world).normalize();

        poseStack.pushPose();
        if (entity.isSmall()) poseStack.scale(0.5F, 0.5F, 0.5F);
        renderTail(poseStack, buffers, dir.scale(-1), toCamera, (float) speed, age, emerge);
        renderGlow(poseStack, buffers, age, emerge);
        renderBody(poseStack, buffers, dir, age, emerge);
        poseStack.popPose();
        super.render(entity, yaw, partialTick, poseStack, buffers, light);
    }

    private void renderTail(PoseStack poseStack, MultiBufferSource buffers, Vec3 axis, Vec3 toCamera, float speed, float age, float emerge) {
        float length = Mth.clamp(speed * 2.6F, 0.5F, 8);
        PoseStack.Pose pose = poseStack.last();
        VertexConsumer buffer = buffers.getBuffer(StarfallDraw.additive(StarfallDraw.TAIL));
        int frame = (int) (age * 0.5F);
        float flicker = 0.9F + 0.1F * Mth.sin(age * 2.3F);
        float[][] layers = {{1.3F, 0.5F, 1, 0.42F}, {0.85F, 0.28F, 1, 0.8F}, {0.42F, 0.1F, 0.6F, 1}};
        for (int i = 0; i < layers.length; i++) {
            float[] l = layers[i];
            float u0 = ((frame + i) & 3) * 0.25F;
            float b = l[3] * flicker * emerge;
            StarfallDraw.ribbon(pose, buffer, CENTER, axis, toCamera, length * l[2], l[0], l[1], u0, u0 + 0.25F, b, b, b);
        }
    }

    private void renderGlow(PoseStack poseStack, MultiBufferSource buffers, float age, float emerge) {
        poseStack.pushPose();
        poseStack.translate(CENTER.x, CENTER.y, CENTER.z);
        poseStack.mulPose(entityRenderDispatcher.cameraOrientation());
        PoseStack.Pose pose = poseStack.last();
        VertexConsumer buffer = buffers.getBuffer(StarfallDraw.additive(StarfallDraw.HALO));
        float size = 1.9F * (0.95F + 0.05F * Mth.sin(age * 1.9F));
        StarfallDraw.rect(pose, buffer, -size, -size, size, size, 0, 0, 0, 1, 1, 0.62F, 0.32F, 0.12F);
        float hot = 1 - emerge;
        if (hot > 0) StarfallDraw.rect(pose, buffer, -1.2F, -1.2F, 1.2F, 1.2F, 0.01F, 0, 0, 1, 1, hot, 0.9F * hot, 0.7F * hot);
        poseStack.popPose();
    }

    private void renderBody(PoseStack poseStack, MultiBufferSource buffers, Vec3 dir, float age, float emerge) {
        Vec3 spinAxis = dir.cross(new Vec3(0, 1, 0));
        if (spinAxis.lengthSqr() < 1.0E-4) spinAxis = new Vec3(1, 0, 0);
        spinAxis = spinAxis.normalize();
        poseStack.pushPose();
        poseStack.translate(CENTER.x, CENTER.y, CENTER.z);
        // 约 10° 一拍，看得出在翻滚但不糊
        poseStack.mulPose(new Quaternionf().rotationAxis(age * 0.18F, (float) spinAxis.x, (float) spinAxis.y, (float) spinAxis.z));
        float scale = 0.55F + 0.45F * emerge;
        poseStack.scale(scale, scale, scale);
        // 两种 RenderType 不能同时握着缓冲：后取的会结束先取的，再写就会 Not building
        VertexConsumer rock = buffers.getBuffer(StarfallDraw.solid(StarfallDraw.METEOR));
        for (float[] l : LUMPS) {
            poseStack.pushPose();
            placeLump(poseStack, l);
            poseStack.scale(l[3], l[4], l[5]);
            cube(poseStack.last(), rock, l[8], l[9], 1, false);
            poseStack.popPose();
        }
        VertexConsumer cracks = buffers.getBuffer(StarfallDraw.additive(StarfallDraw.METEOR_GLOW));
        float heat = 0.85F + 0.15F * Mth.sin(age * 1.3F);
        for (float[] l : LUMPS) {
            poseStack.pushPose();
            placeLump(poseStack, l);
            poseStack.scale(l[3] * 1.02F, l[4] * 1.02F, l[5] * 1.02F);
            cube(poseStack.last(), cracks, l[8], l[9], heat, true);
            poseStack.popPose();
        }
        poseStack.popPose();
    }

    private static void placeLump(PoseStack poseStack, float[] lump) {
        poseStack.translate(lump[0], lump[1], lump[2]);
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(lump[6]));
        poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(lump[7]));
    }

    // 单位立方体，每面取 16x16 贴图的一半；本体按朝向压暗做出体积
    private static void cube(PoseStack.Pose pose, VertexConsumer buffer, float ou, float ov, float bright, boolean additive) {
        float s = 0.5F;
        float[][][] faces = {
            {{-s, s, -s}, {s, s, -s}, {s, s, s}, {-s, s, s}},
            {{-s, -s, s}, {s, -s, s}, {s, -s, -s}, {-s, -s, -s}},
            {{-s, s, s}, {s, s, s}, {s, -s, s}, {-s, -s, s}},
            {{s, s, -s}, {-s, s, -s}, {-s, -s, -s}, {s, -s, -s}},
            {{s, s, s}, {s, s, -s}, {s, -s, -s}, {s, -s, s}},
            {{-s, s, -s}, {-s, s, s}, {-s, -s, s}, {-s, -s, -s}},
        };
        float[] shade = {1, 0.55F, 0.85F, 0.7F, 0.8F, 0.65F};
        for (int i = 0; i < faces.length; i++) {
            float[][] f = faces[i];
            float b = additive ? bright : shade[i];
            StarfallDraw.quad(pose, buffer, v(f[0]), v(f[1]), v(f[2]), v(f[3]), ou, ov, ou + 0.5F, ov + 0.5F, b, b, b);
        }
    }

    private static Vec3 v(float[] p) { return new Vec3(p[0], p[1], p[2]); }

    @Override public boolean shouldRender(FallingStarEntity entity, Frustum frustum, double x, double y, double z) {
        return entity.shouldRender(x, y, z) && frustum.isVisible(entity.getBoundingBox().inflate(8));
    }

    @Override public ResourceLocation getTextureLocation(FallingStarEntity entity) { return StarfallDraw.METEOR; }
}
