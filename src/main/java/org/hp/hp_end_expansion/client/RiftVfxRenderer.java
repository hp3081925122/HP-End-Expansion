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
import org.hp.hp_end_expansion.entity.RiftVfxEntity;

public final class RiftVfxRenderer extends EntityRenderer<RiftVfxEntity> {
    // 弧光细分段数
    private static final int ARC_SEGMENTS = 16;

    public RiftVfxRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(RiftVfxEntity entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
        float age = entity.tickCount + partialTick;
        float life = entity.getLife();
        if (entity.getKind() == RiftVfxEntity.KIND_SLASH) {
            this.renderSlash(entity, age, life, poseStack, buffers);
        } else {
            this.renderPortal(entity, age, life, poseStack, buffers);
        }
        super.render(entity, yaw, partialTick, poseStack, buffers, light);
    }

    // 镰刃弧光：前 3 tick 扫出，随后收窄淡出
    private void renderSlash(RiftVfxEntity entity, float age, float life, PoseStack poseStack, MultiBufferSource buffers) {
        float scale = entity.getScale();
        float sweep = Mth.clamp(age / 3.0F, 0.0F, 1.0F);
        float fadeT = Mth.clamp((age - 3.0F) / (life - 3.0F), 0.0F, 1.0F);
        float bright = 1.0F - fadeT * fadeT;
        if (bright <= 0.0F) {
            return;
        }
        poseStack.pushPose();
        // 以生物朝向为基准，弧面按滚转角倾斜
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-entity.getYRot()));
        poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(entity.getRoll()));
        poseStack.translate(0.0D, 0.0D, -1.2D * scale);
        PoseStack.Pose pose = poseStack.last();
        VertexConsumer buffer = buffers.getBuffer(RiftVfxDraw.additive(RiftVfxDraw.ARC));
        float outer = 2.3F * scale;
        float inner = outer - (0.75F - fadeT * 0.45F) * scale;
        float start = -75.0F;
        float span = 150.0F * sweep;
        int color = RiftVfxDraw.fade(bright);
        for (int i = 0; i < ARC_SEGMENTS; i++) {
            float a0 = (start + span * i / ARC_SEGMENTS) * Mth.DEG_TO_RAD;
            float a1 = (start + span * (i + 1) / ARC_SEGMENTS) * Mth.DEG_TO_RAD;
            float u0 = (float) i / ARC_SEGMENTS;
            float u1 = (float) (i + 1) / ARC_SEGMENTS;
            Vec3 o0 = new Vec3(Mth.sin(a0) * outer, 0.0D, Mth.cos(a0) * outer);
            Vec3 o1 = new Vec3(Mth.sin(a1) * outer, 0.0D, Mth.cos(a1) * outer);
            Vec3 i1 = new Vec3(Mth.sin(a1) * inner, 0.0D, Mth.cos(a1) * inner);
            Vec3 i0 = new Vec3(Mth.sin(a0) * inner, 0.0D, Mth.cos(a0) * inner);
            RiftVfxDraw.quad(pose, buffer, o0, o1, i1, i0, u0, 0.0F, u1, 1.0F, color, true);
        }
        poseStack.popPose();
    }

    // 裂隙门：绕竖轴朝向镜头，张开、停留、闭合
    private void renderPortal(RiftVfxEntity entity, float age, float life, PoseStack poseStack, MultiBufferSource buffers) {
        float scale = entity.getScale();
        float open = Mth.clamp(age / 3.0F, 0.0F, 1.0F);
        float close = Mth.clamp((life - age) / 4.0F, 0.0F, 1.0F);
        float width = Math.min(open, close);
        if (width <= 0.0F) {
            return;
        }
        Vec3 cam = this.entityRenderDispatcher.camera.getPosition();
        double dx = cam.x - entity.getX();
        double dz = cam.z - entity.getZ();
        float face = (float) (Mth.atan2(dx, dz) * Mth.RAD_TO_DEG);
        poseStack.pushPose();
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(face));
        PoseStack.Pose pose = poseStack.last();
        float halfW = 0.75F * scale * width;
        float h = 2.8F * scale * (0.6F + 0.4F * open);
        float bottom = 0.05F + (2.8F * scale - h) * 0.5F;
        // 本体：透明虚空面
        this.portalQuad(pose, buffers.getBuffer(RiftVfxDraw.translucent(RiftVfxDraw.PORTAL)), halfW, bottom, h, RiftVfxDraw.alpha(width), 0.0F);
        // 辉光：更宽的加法层，随时间轻微脉动
        float pulse = 0.85F + 0.15F * Mth.sin(age * 0.9F);
        this.portalQuad(pose, buffers.getBuffer(RiftVfxDraw.additive(RiftVfxDraw.PORTAL_GLOW)), halfW * 1.35F, bottom - 0.1F, h + 0.2F, RiftVfxDraw.fade(width * pulse), 0.01F);
        poseStack.popPose();
    }

    private void portalQuad(PoseStack.Pose pose, VertexConsumer buffer, float halfW, float bottom, float h, int color, float z) {
        Vec3 a = new Vec3(-halfW, bottom + h, z);
        Vec3 b = new Vec3(halfW, bottom + h, z);
        Vec3 c = new Vec3(halfW, bottom, z);
        Vec3 d = new Vec3(-halfW, bottom, z);
        RiftVfxDraw.quad(pose, buffer, a, b, c, d, 0.0F, 0.0F, 1.0F, 1.0F, color, true);
    }

    @Override
    public boolean shouldRender(RiftVfxEntity entity, Frustum frustum, double x, double y, double z) {
        // 特效尺寸大于碰撞箱，按扩大范围裁剪
        return frustum.isVisible(entity.getBoundingBox().inflate(3.0D));
    }

    @Override
    public ResourceLocation getTextureLocation(RiftVfxEntity entity) {
        return RiftVfxDraw.ARC;
    }
}
