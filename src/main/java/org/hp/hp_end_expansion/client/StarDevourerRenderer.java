package org.hp.hp_end_expansion.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.hp.hp_end_expansion.entity.StarDevourerEntity;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public final class StarDevourerRenderer extends GeoEntityRenderer<StarDevourerEntity> {
    // 射线圆柱分段数
    private static final int BEAM_SIDES = 8;

    public StarDevourerRenderer(EntityRendererProvider.Context context) {
        super(context, new StarDevourerModel());
        this.shadowRadius = 1.75F;
        this.withScale(2.5F);
        this.addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    public void render(StarDevourerEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        super.render(entity, entityYaw, partialTick, poseStack, buffers, packedLight);
        // 棱镜射线：主射线加三条子射线
        if (entity.isBeamFiring()) {
            Vec3 origin = new Vec3(Mth.lerp(partialTick, entity.xo, entity.getX()), Mth.lerp(partialTick, entity.yo, entity.getY()), Mth.lerp(partialTick, entity.zo, entity.getZ()));
            Vec3 start = entity.coreOffset(StarDevourerEntity.CORE_LEFT);
            Vec3 point = entity.getBeamPoint();
            float age = entity.tickCount + partialTick;
            float open = Mth.clamp((entity.getSkillTick() - StarDevourerEntity.BEAM_CHARGE + partialTick) / 3.0F, 0.0F, 1.0F);
            float pulse = 0.85F + 0.15F * Mth.sin(age * 1.3F);
            this.renderBeam(poseStack, buffers, start, point.subtract(origin), 0.9F * open * pulse, age);
            for (Vec3 sub : entity.subBeamEnds(origin.add(start), point)) {
                this.renderBeam(poseStack, buffers, point.subtract(origin), sub.subtract(origin), 0.45F * open * pulse, age);
            }
        }
    }

    // 三层同心圆柱与落点光斑
    private void renderBeam(PoseStack poseStack, MultiBufferSource buffers, Vec3 start, Vec3 end, float width, float age) {
        Vec3 axis = end.subtract(start);
        double length = axis.length();
        if (length < 0.1D || width <= 0.001F) {
            return;
        }
        // 计算射线横截面基向量
        Vec3 dir = axis.scale(1.0D / length);
        Vec3 side = dir.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (side.lengthSqr() < 1.0E-4D) {
            side = new Vec3(1.0D, 0.0D, 0.0D);
        }
        side = side.normalize();
        Vec3 up = side.cross(dir).normalize();
        float scroll = -age * 0.25F;
        VertexConsumer buffer = buffers.getBuffer(RiftVfxDraw.additive(VoidRayDraw.BEAM));
        PoseStack.Pose pose = poseStack.last();
        this.cylinder(pose, buffer, start, end, side, up, 0.6F * width, (float) length, scroll * 0.6F, RiftVfxDraw.fade(0.35F));
        this.cylinder(pose, buffer, start, end, side, up, 0.33F * width, (float) length, scroll, RiftVfxDraw.fade(0.7F));
        this.cylinder(pose, buffer, start, end, side, up, 0.13F * width, (float) length, scroll * 1.5F, RiftVfxDraw.fade(1.0F));
        // 落点光斑
        poseStack.pushPose();
        poseStack.translate(end.x, end.y, end.z);
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        VertexConsumer star = buffers.getBuffer(RiftVfxDraw.additive(VoidRayDraw.STAR));
        float s = width;
        RiftVfxDraw.quad(poseStack.last(), star, new Vec3(-s, s, 0.0D), new Vec3(s, s, 0.0D), new Vec3(s, -s, 0.0D), new Vec3(-s, -s, 0.0D),
            0.0F, 0.0F, 1.0F, 1.0F, RiftVfxDraw.fade(0.9F), true);
        poseStack.popPose();
    }

    // 沿轴向的多边形圆柱
    private void cylinder(PoseStack.Pose pose, VertexConsumer buffer, Vec3 start, Vec3 end, Vec3 side, Vec3 up, float radius, float length, float scroll, int color) {
        for (int i = 0; i < BEAM_SIDES; i++) {
            float a0 = Mth.TWO_PI * i / BEAM_SIDES;
            float a1 = Mth.TWO_PI * (i + 1) / BEAM_SIDES;
            Vec3 o0 = side.scale(Mth.cos(a0) * radius).add(up.scale(Mth.sin(a0) * radius));
            Vec3 o1 = side.scale(Mth.cos(a1) * radius).add(up.scale(Mth.sin(a1) * radius));
            float u0 = (float) i / BEAM_SIDES;
            float u1 = (float) (i + 1) / BEAM_SIDES;
            RiftVfxDraw.quad(pose, buffer, start.add(o0), start.add(o1), end.add(o1), end.add(o0),
                u0, scroll, u1, scroll + length / 2.0F, color, true);
        }
    }

    @Override
    public boolean shouldRender(StarDevourerEntity entity, Frustum frustum, double x, double y, double z) {
        // 射线期间裁剪范围覆盖到落点
        if (entity.isBeamFiring()) {
            Vec3 point = entity.getBeamPoint();
            return frustum.isVisible(entity.getBoundingBoxForCulling().minmax(new AABB(point, point).inflate(StarDevourerEntity.SUB_BEAM_RANGE)));
        }
        return super.shouldRender(entity, frustum, x, y, z);
    }
}
