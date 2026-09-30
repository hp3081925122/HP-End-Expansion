package org.hp.hp_end_expansion.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Iterator;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.hp.hp_end_expansion.entity.VoidRayEntity;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public final class VoidRayRenderer extends GeoEntityRenderer<VoidRayEntity> {
    // 射线圆柱分段数
    private static final int BEAM_SIDES = 8;

    public VoidRayRenderer(EntityRendererProvider.Context context) {
        super(context, new VoidRayModel());
        this.shadowRadius = 0.75F;
        this.addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    public void render(VoidRayEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        super.render(entity, entityYaw, partialTick, poseStack, buffers, packedLight);
        Vec3 origin = new Vec3(Mth.lerp(partialTick, entity.xo, entity.getX()), Mth.lerp(partialTick, entity.yo, entity.getY()), Mth.lerp(partialTick, entity.zo, entity.getZ()));
        if (entity.isBeamFiring()) {
            this.renderBeam(entity, origin, partialTick, poseStack, buffers);
        }
        if (entity.trail.size() >= 2) {
            this.renderTrail(entity, origin, poseStack, buffers);
        }
    }

    // 虚空射线：外晕、过渡、白芯三层同心圆柱，外层随时间脉动
    private void renderBeam(VoidRayEntity entity, Vec3 origin, float partialTick, PoseStack poseStack, MultiBufferSource buffers) {
        Vec3 start = new Vec3(0.0D, VoidRayEntity.CORE_HEIGHT, 0.0D);
        Vec3 end = entity.getBeamPoint().subtract(origin);
        Vec3 axis = end.subtract(start);
        double length = axis.length();
        if (length < 0.1D) {
            return;
        }
        Vec3 dir = axis.scale(1.0D / length);
        Vec3 side = dir.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (side.lengthSqr() < 1.0E-4D) {
            side = new Vec3(1.0D, 0.0D, 0.0D);
        }
        side = side.normalize();
        Vec3 up = side.cross(dir).normalize();
        float age = entity.tickCount + partialTick;
        float open = Mth.clamp((entity.getSkillTick() - VoidRayEntity.BEAM_CHARGE + partialTick) / 3.0F, 0.0F, 1.0F);
        float pulse = 0.85F + 0.15F * Mth.sin(age * 1.3F);
        float scroll = -age * 0.25F;
        VertexConsumer buffer = buffers.getBuffer(RiftVfxDraw.additive(VoidRayDraw.BEAM));
        PoseStack.Pose pose = poseStack.last();
        this.cylinder(pose, buffer, start, end, side, up, 0.55F * open * pulse, (float) length, scroll * 0.6F, RiftVfxDraw.fade(0.35F));
        this.cylinder(pose, buffer, start, end, side, up, 0.3F * open, (float) length, scroll, RiftVfxDraw.fade(0.7F));
        this.cylinder(pose, buffer, start, end, side, up, 0.12F * open, (float) length, scroll * 1.5F, RiftVfxDraw.fade(1.0F));
        // 落点光斑：面向镜头的方片
        poseStack.pushPose();
        poseStack.translate(end.x, end.y, end.z);
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        VertexConsumer star = buffers.getBuffer(RiftVfxDraw.additive(VoidRayDraw.STAR));
        float s = 0.9F * open * pulse;
        RiftVfxDraw.quad(poseStack.last(), star, new Vec3(-s, s, 0.0D), new Vec3(s, s, 0.0D), new Vec3(s, -s, 0.0D), new Vec3(-s, -s, 0.0D),
            0.0F, 0.0F, 1.0F, 1.0F, RiftVfxDraw.fade(0.9F), true);
        poseStack.popPose();
    }

    // 沿轴向的多边形圆柱，u 绕周、v 沿长度滚动
    private void cylinder(PoseStack.Pose pose, VertexConsumer buffer, Vec3 start, Vec3 end, Vec3 side, Vec3 up, float radius, float length, float scroll, int color) {
        if (radius <= 0.001F) {
            return;
        }
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

    // 俯冲拖尾：历史点连成竖直条带，尾端收窄变暗
    private void renderTrail(VoidRayEntity entity, Vec3 origin, PoseStack poseStack, MultiBufferSource buffers) {
        VertexConsumer buffer = buffers.getBuffer(RiftVfxDraw.additive(RiftVfxDraw.ARC));
        PoseStack.Pose pose = poseStack.last();
        int count = entity.trail.size();
        Iterator<Vec3> it = entity.trail.iterator();
        Vec3 prev = it.next().subtract(origin);
        int i = 0;
        while (it.hasNext()) {
            Vec3 cur = it.next().subtract(origin);
            float f0 = 1.0F - (float) i / count;
            float f1 = 1.0F - (float) (i + 1) / count;
            float h0 = 1.1F * f0;
            float h1 = 1.1F * f1;
            double y = 0.6D;
            Vec3 a = prev.add(0.0D, y + h0, 0.0D);
            Vec3 b = cur.add(0.0D, y + h1, 0.0D);
            Vec3 c = cur.add(0.0D, y - h1 * 0.4D, 0.0D);
            Vec3 d = prev.add(0.0D, y - h0 * 0.4D, 0.0D);
            RiftVfxDraw.quad(pose, buffer, a, b, c, d, (float) i / count, 0.0F, (float) (i + 1) / count, 1.0F, RiftVfxDraw.fade(f0 * 0.9F), true);
            prev = cur;
            i++;
        }
    }

    @Override
    public boolean shouldRender(VoidRayEntity entity, Frustum frustum, double x, double y, double z) {
        // 射线期间裁剪范围覆盖到落点
        if (entity.isBeamFiring()) {
            AABB box = entity.getBoundingBoxForCulling();
            Vec3 point = entity.getBeamPoint();
            return frustum.isVisible(box.minmax(new AABB(point, point).inflate(1.0D)));
        }
        return super.shouldRender(entity, frustum, x, y, z);
    }
}
