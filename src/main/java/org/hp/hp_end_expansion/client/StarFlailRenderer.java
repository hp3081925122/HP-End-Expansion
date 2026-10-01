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
import net.minecraft.world.phys.Vec3;
import org.hp.hp_end_expansion.entity.starwreck.StarFlailEntity;

/** 掷出的链锤：一块翻滚的星骸岩，身后一条短尾焰。 */
public final class StarFlailRenderer extends EntityRenderer<StarFlailEntity> {
    private static final float[][] LUMPS = {
        {0, 0, 0, 0.42F, 0.36F, 0.38F, 0, 0},
        {0.16F, 0.1F, -0.06F, 0.26F, 0.24F, 0.28F, 30, 12},
        {-0.14F, -0.08F, 0.1F, 0.28F, 0.22F, 0.24F, -24, -10},
    };

    public StarFlailRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.25F;
    }

    @Override public void render(StarFlailEntity entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
        float age = entity.tickCount + partialTick;
        Vec3 velocity = entity.getDeltaMovement();
        if (velocity.lengthSqr() < 1.0E-6) velocity = entity.position().subtract(entity.xo, entity.yo, entity.zo);
        double speed = velocity.length();
        Vec3 dir = speed > 1.0E-4 ? velocity.scale(1 / speed) : new Vec3(0, -1, 0);
        Vec3 toCamera = entityRenderDispatcher.camera.getPosition().subtract(entity.getPosition(partialTick)).normalize();

        poseStack.pushPose();
        poseStack.translate(0, 0.2, 0);
        PoseStack.Pose pose = poseStack.last();
        VertexConsumer tail = buffers.getBuffer(StarfallDraw.additive(StarfallDraw.TAIL));
        int frame = (int) (age * 0.5F);
        float length = Mth.clamp((float) speed * 1.6F, 0.3F, 2.2F);
        float[][] layers = {{0.34F, 0.08F, 1, 0.45F}, {0.18F, 0.04F, 0.7F, 0.9F}};
        for (int i = 0; i < layers.length; i++) {
            float[] l = layers[i];
            float u0 = ((frame + i) & 3) * 0.25F;
            StarfallDraw.ribbon(pose, tail, Vec3.ZERO, dir.scale(-1), toCamera, length * l[2], l[0], l[1], u0, u0 + 0.25F, l[3], l[3] * 0.7F, l[3] * 0.3F);
        }
        Vec3 spin = dir.cross(new Vec3(0, 1, 0));
        if (spin.lengthSqr() < 1.0E-4) spin = new Vec3(1, 0, 0);
        spin = spin.normalize();
        poseStack.mulPose(new org.joml.Quaternionf().rotationAxis(age * 0.35F, (float) spin.x, (float) spin.y, (float) spin.z));
        VertexConsumer rock = buffers.getBuffer(StarfallDraw.solid(StarfallDraw.METEOR));
        for (float[] l : LUMPS) lump(poseStack, rock, l, 1, false);
        VertexConsumer glow = buffers.getBuffer(StarfallDraw.additive(StarfallDraw.METEOR_GLOW));
        float heat = 0.8F + 0.2F * Mth.sin(age * 1.4F);
        for (float[] l : LUMPS) lump(poseStack, glow, l, heat, true);
        poseStack.popPose();
        super.render(entity, yaw, partialTick, poseStack, buffers, light);
    }

    private static void lump(PoseStack poseStack, VertexConsumer buffer, float[] l, float bright, boolean additive) {
        poseStack.pushPose();
        poseStack.translate(l[0], l[1], l[2]);
        poseStack.mulPose(Axis.YP.rotationDegrees(l[6]));
        poseStack.mulPose(Axis.XP.rotationDegrees(l[7]));
        poseStack.scale(l[3], l[4], l[5]);
        cube(poseStack.last(), buffer, bright, additive);
        poseStack.popPose();
    }

    private static void cube(PoseStack.Pose pose, VertexConsumer buffer, float bright, boolean additive) {
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
            float b = additive ? bright : shade[i];
            StarfallDraw.quad(pose, buffer, v(faces[i][0]), v(faces[i][1]), v(faces[i][2]), v(faces[i][3]), 0, 0, 0.5F, 0.5F, b, b, b);
        }
    }

    private static Vec3 v(float[] p) { return new Vec3(p[0], p[1], p[2]); }

    @Override public boolean shouldRender(StarFlailEntity entity, Frustum frustum, double x, double y, double z) {
        return entity.shouldRender(x, y, z) && frustum.isVisible(entity.getBoundingBox().inflate(3));
    }

    @Override public ResourceLocation getTextureLocation(StarFlailEntity entity) { return StarfallDraw.METEOR; }
}
