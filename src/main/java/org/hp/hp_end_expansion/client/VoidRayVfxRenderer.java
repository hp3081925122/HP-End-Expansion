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
import org.hp.hp_end_expansion.entity.VoidRayVfxEntity;

public final class VoidRayVfxRenderer extends EntityRenderer<VoidRayVfxEntity> {
    // 环形分段数
    private static final int RING_SEGMENTS = 32;

    public VoidRayVfxRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(VoidRayVfxEntity entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
        float age = entity.localAge(partialTick);
        if (age >= 0.0F) {
            int kind = entity.getKind();
            if (kind == VoidRayVfxEntity.KIND_WARN_LINE) {
                this.renderWarnLine(entity, age, poseStack, buffers);
            } else if (kind == VoidRayVfxEntity.KIND_SHOCK) {
                this.renderShock(entity, age, poseStack, buffers);
            } else if (kind == VoidRayVfxEntity.KIND_VORTEX) {
                this.renderVortex(entity, age, poseStack, buffers);
            } else if (kind == VoidRayVfxEntity.KIND_STAR || kind == VoidRayVfxEntity.KIND_BIG_STAR) {
                this.renderStar(entity, age, poseStack, buffers);
            } else if (kind == VoidRayVfxEntity.KIND_WARN_WIDE) {
                this.renderWarnLine(entity, age, poseStack, buffers);
            } else if (kind == VoidRayVfxEntity.KIND_BLACK_HOLE) {
                this.renderBlackHole(entity, age, poseStack, buffers);
            } else if (kind == VoidRayVfxEntity.KIND_STARDUST) {
                this.renderStardust(entity, age, poseStack, buffers);
            }
        }
        super.render(entity, yaw, partialTick, poseStack, buffers, light);
    }

    // 俯冲预警线：条带沿朝向展开，随前摇由暗到亮，末尾快速闪烁
    private void renderWarnLine(VoidRayVfxEntity entity, float age, PoseStack poseStack, MultiBufferSource buffers) {
        float life = entity.getLife();
        float length = entity.getSize();
        float grow = Mth.clamp(age / 4.0F, 0.0F, 1.0F);
        float bright = 0.35F + 0.65F * Mth.clamp(age / (life - 4.0F), 0.0F, 1.0F);
        if (age > life - 5.0F && ((int) age) % 2 == 0) {
            bright = 1.0F;
        }
        float fade = Mth.clamp((life - age) / 2.0F, 0.0F, 1.0F);
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-entity.getYRot()));
        PoseStack.Pose pose = poseStack.last();
        VertexConsumer buffer = buffers.getBuffer(RiftVfxDraw.additive(VoidRayDraw.WARN));
        float half = 0.7F;
        // 宽预警：两侧各 3 格
        if (entity.getKind() == VoidRayVfxEntity.KIND_WARN_WIDE) {
            half = 3.0F;
        }
        float len = length * grow;
        float scroll = -age * 0.08F;
        Vec3 a = new Vec3(-half, 0.04D, 0.0D);
        Vec3 b = new Vec3(half, 0.04D, 0.0D);
        Vec3 c = new Vec3(half, 0.04D, len);
        Vec3 d = new Vec3(-half, 0.04D, len);
        RiftVfxDraw.quad(pose, buffer, a, b, c, d, 0.0F, scroll, 1.0F, scroll + len / 1.4F, RiftVfxDraw.fade(bright * fade), false);
        poseStack.popPose();
    }

    // 地面冲击环：环向外扩张并收窄淡出
    private void renderShock(VoidRayVfxEntity entity, float age, PoseStack poseStack, MultiBufferSource buffers) {
        float life = entity.getLife();
        float p = Mth.clamp(age / life, 0.0F, 1.0F);
        float outer = entity.getSize() * (0.3F + 0.7F * (1.0F - (1.0F - p) * (1.0F - p)));
        float width = 1.1F * (1.0F - p) + 0.15F;
        float bright = 1.0F - p * p;
        this.ring(poseStack, buffers.getBuffer(RiftVfxDraw.additive(VoidRayDraw.RING)), outer, Math.max(0.0F, outer - width), 0.05F, 0.0F, RiftVfxDraw.fade(bright));
    }

    // 引力漩涡：前摇期间符文圈淡入，生效期间两层反向旋转并竖起吸入光柱
    private void renderVortex(VoidRayVfxEntity entity, float age, PoseStack poseStack, MultiBufferSource buffers) {
        float life = entity.getLife();
        float radius = entity.getSize();
        float arm = VoidRayVfxEntity.VORTEX_ARM;
        float appear = Mth.clamp(age / 6.0F, 0.0F, 1.0F);
        float fade = Mth.clamp((life - age) / 6.0F, 0.0F, 1.0F);
        float active = Mth.clamp((age - arm) / 4.0F, 0.0F, 1.0F);
        float warn = 0.3F + 0.3F * Mth.clamp(age / arm, 0.0F, 1.0F);
        float bright = (warn + (1.0F - warn) * active) * fade;
        float spin = age * (2.0F + 6.0F * active);
        VertexConsumer rune = buffers.getBuffer(RiftVfxDraw.additive(VoidRayDraw.RUNE));
        // 外层顺时针
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(spin));
        this.disc(poseStack.last(), rune, radius * appear, 0.05F, RiftVfxDraw.fade(bright));
        poseStack.popPose();
        // 内层逆时针，缩小
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-spin * 1.6F));
        this.disc(poseStack.last(), rune, radius * 0.55F * appear, 0.07F, RiftVfxDraw.fade(bright * 0.8F));
        poseStack.popPose();
        // 吸入光柱
        if (active > 0.0F) {
            float height = 3.5F * active;
            float pulse = 0.75F + 0.25F * Mth.sin(age * 0.8F);
            this.column(poseStack, buffers.getBuffer(RiftVfxDraw.additive(VoidRayDraw.BEAM)), 0.35F, height, -age * 0.15F, RiftVfxDraw.fade(active * fade * pulse));
            this.column(poseStack, buffers.getBuffer(RiftVfxDraw.additive(VoidRayDraw.BEAM)), 0.8F, height * 0.7F, -age * 0.1F, RiftVfxDraw.fade(active * fade * 0.35F));
        }
    }

    // 星陨：预警环收紧，陨星从高空坠落，落地冲击环
    private void renderStar(VoidRayVfxEntity entity, float age, PoseStack poseStack, MultiBufferSource buffers) {
        float fall = VoidRayVfxEntity.STAR_FALL;
        float radius = entity.getSize();
        VertexConsumer ring = buffers.getBuffer(RiftVfxDraw.additive(VoidRayDraw.RING));
        if (age < fall) {
            // 预警：内圈从外缘收向实际判定半径
            float p = age / fall;
            float bright = 0.4F + 0.6F * p;
            if (p > 0.8F && ((int) age) % 2 == 0) {
                bright = 1.0F;
            }
            this.ring(poseStack, ring, radius, radius - 0.2F, 0.05F, 0.0F, RiftVfxDraw.fade(bright * 0.7F));
            float inner = radius * (1.0F - p);
            this.ring(poseStack, ring, inner + 0.25F, inner, 0.06F, age * 3.0F, RiftVfxDraw.fade(bright));
            // 陨星：最后 10 tick 从 14 格高落下
            float drop = (fall - age) / 10.0F;
            if (drop <= 1.0F) {
                float y = drop * drop * 14.0F + 0.4F;
                float starScale = 1.0F;
                // 大陨石放大
                if (entity.getKind() == VoidRayVfxEntity.KIND_BIG_STAR) {
                    starScale = 2.2F;
                }
                poseStack.pushPose();
                poseStack.scale(starScale, starScale, starScale);
                this.star(poseStack, buffers, y / starScale, age);
                poseStack.popPose();
            }
        } else {
            float t = age - fall;
            float p = Mth.clamp(t / 10.0F, 0.0F, 1.0F);
            float outer = radius * (0.5F + 1.0F * p);
            this.ring(poseStack, ring, outer, outer * (0.4F + 0.5F * p), 0.05F, 0.0F, RiftVfxDraw.fade(1.0F - p));
        }
    }

    // 黑洞：悬空的旋转吸积盘与外圈
    private void renderBlackHole(VoidRayVfxEntity entity, float age, PoseStack poseStack, MultiBufferSource buffers) {
        float life = entity.getLife();
        float arm = VoidRayVfxEntity.HOLE_ARM;
        float appear = Mth.clamp(age / arm, 0.0F, 1.0F);
        float fade = Mth.clamp((life - age) / 8.0F, 0.0F, 1.0F);
        float radius = entity.getSize();
        VertexConsumer rune = buffers.getBuffer(RiftVfxDraw.additive(VoidRayDraw.RUNE));
        // 吸积盘：两层反向旋转
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(age * 9.0F));
        this.disc(poseStack.last(), rune, 2.2F * appear, 0.0F, RiftVfxDraw.fade(fade));
        poseStack.popPose();
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-age * 14.0F));
        this.disc(poseStack.last(), rune, 1.3F * appear, 0.02F, RiftVfxDraw.fade(fade * 0.8F));
        poseStack.popPose();
        // 核心星点
        this.star(poseStack, buffers, 0.0F, age);
        // 吸引范围外圈：向内收缩循环
        float cycle = (age % 20.0F) / 20.0F;
        float outer = radius * (1.0F - cycle);
        this.ring(poseStack, buffers.getBuffer(RiftVfxDraw.additive(VoidRayDraw.RING)), outer, Math.max(0.0F, outer - 0.3F), 0.0F, 0.0F, RiftVfxDraw.fade(fade * appear * 0.6F));
    }

    // 星尘领域：地面缓慢旋转的符文圈
    private void renderStardust(VoidRayVfxEntity entity, float age, PoseStack poseStack, MultiBufferSource buffers) {
        float life = entity.getLife();
        float appear = Mth.clamp(age / 6.0F, 0.0F, 1.0F);
        float fade = Mth.clamp((life - age) / 10.0F, 0.0F, 1.0F);
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(age * 1.5F));
        this.disc(poseStack.last(), buffers.getBuffer(RiftVfxDraw.additive(VoidRayDraw.RUNE)), entity.getSize() * appear, 0.05F, RiftVfxDraw.fade(0.45F * fade));
        poseStack.popPose();
    }

    // 陨星本体：面向镜头的晶核加竖直拖尾
    private void star(PoseStack poseStack, MultiBufferSource buffers, float y, float age) {
        poseStack.pushPose();
        poseStack.translate(0.0D, y, 0.0D);
        // 拖尾：两片交叉竖面
        VertexConsumer beam = buffers.getBuffer(RiftVfxDraw.additive(VoidRayDraw.BEAM));
        PoseStack.Pose pose = poseStack.last();
        float w = 0.3F;
        float tail = 3.0F;
        RiftVfxDraw.quad(pose, beam, new Vec3(-w, tail, 0.0D), new Vec3(w, tail, 0.0D), new Vec3(w, 0.0D, 0.0D), new Vec3(-w, 0.0D, 0.0D),
            0.0F, 0.0F, 1.0F, 1.0F, RiftVfxDraw.fade(0.8F), true);
        RiftVfxDraw.quad(pose, beam, new Vec3(0.0D, tail, -w), new Vec3(0.0D, tail, w), new Vec3(0.0D, 0.0D, w), new Vec3(0.0D, 0.0D, -w),
            0.0F, 0.0F, 1.0F, 1.0F, RiftVfxDraw.fade(0.8F), true);
        // 晶核：朝向镜头并自转
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        poseStack.mulPose(Axis.ZP.rotationDegrees(age * 20.0F));
        PoseStack.Pose core = poseStack.last();
        VertexConsumer starBuf = buffers.getBuffer(RiftVfxDraw.additive(VoidRayDraw.STAR));
        float s = 0.7F;
        RiftVfxDraw.quad(core, starBuf, new Vec3(-s, s, 0.0D), new Vec3(s, s, 0.0D), new Vec3(s, -s, 0.0D), new Vec3(-s, -s, 0.0D),
            0.0F, 0.0F, 1.0F, 1.0F, RiftVfxDraw.fade(1.0F), true);
        poseStack.popPose();
    }

    // 水平圆环条带
    private void ring(PoseStack poseStack, VertexConsumer buffer, float outer, float inner, float y, float spin, int color) {
        if (outer <= 0.01F) {
            return;
        }
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(spin));
        PoseStack.Pose pose = poseStack.last();
        for (int i = 0; i < RING_SEGMENTS; i++) {
            float a0 = Mth.TWO_PI * i / RING_SEGMENTS;
            float a1 = Mth.TWO_PI * (i + 1) / RING_SEGMENTS;
            float u0 = (float) i / RING_SEGMENTS * 4.0F;
            float u1 = (float) (i + 1) / RING_SEGMENTS * 4.0F;
            Vec3 o0 = new Vec3(Mth.cos(a0) * outer, y, Mth.sin(a0) * outer);
            Vec3 o1 = new Vec3(Mth.cos(a1) * outer, y, Mth.sin(a1) * outer);
            Vec3 i1 = new Vec3(Mth.cos(a1) * inner, y, Mth.sin(a1) * inner);
            Vec3 i0 = new Vec3(Mth.cos(a0) * inner, y, Mth.sin(a0) * inner);
            RiftVfxDraw.quad(pose, buffer, o0, o1, i1, i0, u0, 0.0F, u1, 1.0F, color, true);
        }
        poseStack.popPose();
    }

    // 水平圆盘贴图
    private void disc(PoseStack.Pose pose, VertexConsumer buffer, float radius, float y, int color) {
        Vec3 a = new Vec3(-radius, y, -radius);
        Vec3 b = new Vec3(radius, y, -radius);
        Vec3 c = new Vec3(radius, y, radius);
        Vec3 d = new Vec3(-radius, y, radius);
        RiftVfxDraw.quad(pose, buffer, a, b, c, d, 0.0F, 0.0F, 1.0F, 1.0F, color, true);
    }

    // 竖直光柱：四面，v 方向滚动
    private void column(PoseStack poseStack, VertexConsumer buffer, float radius, float height, float scroll, int color) {
        PoseStack.Pose pose = poseStack.last();
        for (int i = 0; i < 4; i++) {
            float a0 = Mth.HALF_PI * i + Mth.PI / 4.0F;
            float a1 = Mth.HALF_PI * (i + 1) + Mth.PI / 4.0F;
            Vec3 b0 = new Vec3(Mth.cos(a0) * radius, 0.0D, Mth.sin(a0) * radius);
            Vec3 b1 = new Vec3(Mth.cos(a1) * radius, 0.0D, Mth.sin(a1) * radius);
            Vec3 t1 = b1.add(0.0D, height, 0.0D);
            Vec3 t0 = b0.add(0.0D, height, 0.0D);
            RiftVfxDraw.quad(pose, buffer, t0, t1, b1, b0, 0.0F, scroll, 1.0F, scroll + height / 2.0F, color, true);
        }
    }

    @Override
    public boolean shouldRender(VoidRayVfxEntity entity, Frustum frustum, double x, double y, double z) {
        return frustum.isVisible(entity.getBoundingBoxForCulling());
    }

    @Override
    public ResourceLocation getTextureLocation(VoidRayVfxEntity entity) {
        return VoidRayDraw.RING;
    }
}
