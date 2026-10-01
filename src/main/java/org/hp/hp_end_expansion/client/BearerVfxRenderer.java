package org.hp.hp_end_expansion.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.hp.hp_end_expansion.Hp_end_expansion;
import org.hp.hp_end_expansion.entity.starwreck.BearerVfxEntity;
import org.hp.hp_end_expansion.entity.starwreck.StarBearerEntity;
import org.hp.hp_end_expansion.entity.starwreck.StarShardEntity;
import org.joml.Quaternionf;

/**
 * 负星者专属特效：圣印、光墙、光柱、白金火舌、碎星光刺。全部走加法混合，颜色分量先夹到 1 再写顶点，
 * 否则 float 转 byte 会溢出成暗色。
 */
public final class BearerVfxRenderer extends EntityRenderer<BearerVfxEntity> {
    public static final ResourceLocation SIGIL = tex("bearer_sigil");
    public static final ResourceLocation WAVE = tex("bearer_wave");
    public static final ResourceLocation FLARE = tex("bearer_flare");
    public static final ResourceLocation SPIKE = tex("bearer_spike");
    public static final ResourceLocation GLOW = tex("bearer_glow");
    // 负光招式（横扫、光束）：靛紫到青白的冷色，和白金圣火分开
    public static final ResourceLocation ARC = tex("bearer_arc");
    public static final ResourceLocation BEAM = tex("bearer_beam");
    public static final ResourceLocation LENS = tex("bearer_lens");
    public static final ResourceLocation HALO = tex("bearer_halo");
    private static final int WALL_SEGMENTS = 48, ARC_SEGMENTS = 28;

    public BearerVfxRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0;
    }

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(Hp_end_expansion.MODID, "textures/effect/" + name + ".png");
    }

    static float c(float v) { return Mth.clamp(v, 0, 1); }

    static VertexConsumer add(MultiBufferSource buffers, ResourceLocation texture) { return buffers.getBuffer(StarfallDraw.additive(texture)); }

    /** 平铺在当前姿态 XZ 平面上的正方形贴图，半边长 r。 */
    static void flat(PoseStack.Pose pose, VertexConsumer buffer, float r, float red, float green, float blue) {
        StarfallDraw.quad(pose, buffer, new Vec3(-r, 0, -r), new Vec3(r, 0, -r), new Vec3(r, 0, r), new Vec3(-r, 0, r),
            0, 0, 1, 1, c(red), c(green), c(blue));
    }

    /** 朝向镜头的圣辉。 */
    public static void glow(PoseStack poseStack, MultiBufferSource buffers, Quaternionf camera, Vec3 at, float size, float spin,
                            float red, float green, float blue) {
        sprite(poseStack, buffers, camera, GLOW, at, size, spin, red, green, blue);
    }

    /** 朝向镜头的贴图片。 */
    public static void sprite(PoseStack poseStack, MultiBufferSource buffers, Quaternionf camera, ResourceLocation texture, Vec3 at, float size, float spin,
                              float red, float green, float blue) {
        if (size <= 0) return;
        poseStack.pushPose();
        poseStack.translate(at.x, at.y, at.z);
        poseStack.mulPose(camera);
        if (spin != 0) poseStack.mulPose(Axis.ZP.rotationDegrees(spin));
        StarfallDraw.rect(poseStack.last(), add(buffers, texture), -size, -size, size, size, 0, 0, 0, 1, 1, c(red), c(green), c(blue));
        poseStack.popPose();
    }

    /** 垂直于 axis 的方片（光环），绕 axis 转 spin 度。 */
    static void disc(PoseStack.Pose pose, VertexConsumer buffer, Vec3 center, Vec3 axis, float r, float spin, float red, float green, float blue) {
        if (r <= 0) return;
        Vec3 e1 = axis.cross(new Vec3(0, 1, 0));
        if (e1.lengthSqr() < 1.0E-4) e1 = axis.cross(new Vec3(1, 0, 0));
        e1 = e1.normalize();
        Vec3 e2 = e1.cross(axis).normalize();
        float a = spin * Mth.DEG_TO_RAD;
        Vec3 u = e1.scale(Mth.cos(a)).add(e2.scale(Mth.sin(a))).scale(r);
        Vec3 v = e2.scale(Mth.cos(a)).subtract(e1.scale(Mth.sin(a))).scale(r);
        StarfallDraw.quad(pose, buffer, center.subtract(u).subtract(v), center.add(u).subtract(v), center.add(u).add(v), center.subtract(u).add(v),
            0, 0, 1, 1, c(red), c(green), c(blue));
    }

    /** 沿 dir 拉出的朝镜头光带，u 横跨宽度、v 沿长度（可滚动）。 */
    static void beamRibbon(PoseStack.Pose pose, VertexConsumer buffer, Vec3 from, Vec3 dir, float len, Vec3 toCamera, float w,
                           float u0, float u1, float v0, float v1, float red, float green, float blue) {
        if (w <= 0) return;
        Vec3 side = dir.cross(toCamera);
        if (side.lengthSqr() < 1.0E-6) side = dir.cross(new Vec3(1, 0, 0));
        side = side.normalize().scale(w);
        Vec3 to = from.add(dir.scale(len));
        StarfallDraw.quad(pose, buffer, from.add(side), from.subtract(side), to.subtract(side), to.add(side), u0, v0, u1, v1, c(red), c(green), c(blue));
    }

    @Override public void render(BearerVfxEntity entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
        float age = entity.tickCount + partialTick;
        poseStack.pushPose();
        switch (entity.kind()) {
            case BearerVfxEntity.FLARE -> flare(entity, age, poseStack, buffers);
            case BearerVfxEntity.SHATTER -> shatter(entity, age, poseStack, buffers);
            case BearerVfxEntity.BRAND -> brand(entity, age, poseStack, buffers);
            case BearerVfxEntity.ARC -> arc(entity, age, poseStack, buffers);
            default -> slam(entity, age, poseStack, buffers);
        }
        poseStack.popPose();
        super.render(entity, yaw, partialTick, poseStack, buffers, light);
    }

    /** 圣砸：圣印先猛亮再慢慢褪去，光墙沿伤害环推出，中心光柱只亮几拍，八道地裂光纹从印心放射。 */
    private void slam(BearerVfxEntity entity, float age, PoseStack poseStack, MultiBufferSource buffers) {
        float k = entity.scale();
        poseStack.scale(k, k, k);
        float appear = Math.min(1, age / 3);
        float fade = age < 4 ? 1 : Math.max(0, 1 - (age - 4) / 24);
        fade *= fade;
        float hot = age < 3 ? 1 : 0.8F;

        poseStack.pushPose();
        poseStack.translate(0, 0.03, 0);
        poseStack.mulPose(Axis.YP.rotationDegrees(age * 3));
        flat(poseStack.last(), add(buffers, SIGIL), 2.4F * (0.65F + 0.35F * appear), hot * fade, 0.8F * hot * fade, 0.45F * hot * fade);
        poseStack.mulPose(Axis.YP.rotationDegrees(-age * 9));
        poseStack.translate(0, 0.01, 0);
        flat(poseStack.last(), add(buffers, SIGIL), 1.1F, fade, 0.95F * fade, 0.8F * fade);
        poseStack.popPose();

        RandomSource rays = RandomSource.create(entity.getId() * 31L);
        VertexConsumer spike = add(buffers, SPIKE);
        for (int i = 0; i < 8; i++) {
            float len = 1.6F + rays.nextFloat() * 1.4F, w = 0.08F + rays.nextFloat() * 0.06F;
            poseStack.pushPose();
            poseStack.translate(0, 0.04, 0);
            poseStack.mulPose(Axis.YP.rotationDegrees(i * 45 + rays.nextFloat() * 20));
            float r0 = 0.5F, r1 = r0 + len * appear, b = 0.85F * fade;
            StarfallDraw.quad(poseStack.last(), spike, new Vec3(-w, 0, r0), new Vec3(w, 0, r0), new Vec3(w * 0.3, 0, r1), new Vec3(-w * 0.3, 0, r1),
                0, 0, 1, 1, c(b), c(0.85F * b), c(0.55F * b));
            poseStack.popPose();
        }

        if (age <= BearerVfxEntity.RING_TICKS + 2) {
            float t = Math.min(1, age / BearerVfxEntity.RING_TICKS);
            float radius = BearerVfxEntity.ringRadius(age);
            float h = 1.3F * (1 - 0.6F * t);
            float b = (float) Math.pow(1 - Math.min(1, age / (BearerVfxEntity.RING_TICKS + 2)), 1.2);
            VertexConsumer wall = add(buffers, WAVE);
            PoseStack.Pose pose = poseStack.last();
            for (int i = 0; i < WALL_SEGMENTS; i++) {
                double a0 = i * Mth.TWO_PI / WALL_SEGMENTS, a1 = (i + 1) * Mth.TWO_PI / WALL_SEGMENTS;
                float u0 = i / (float) WALL_SEGMENTS * 10, u1 = (i + 1) / (float) WALL_SEGMENTS * 10;
                double x0 = Math.cos(a0) * radius, z0 = Math.sin(a0) * radius, x1 = Math.cos(a1) * radius, z1 = Math.sin(a1) * radius;
                StarfallDraw.quad(pose, wall, new Vec3(x0, h, z0), new Vec3(x1, h, z1), new Vec3(x1, 0, z1), new Vec3(x0, 0, z0),
                    u0, 0, u1, 1, c(b), c(0.88F * b), c(0.6F * b));
            }
        }

        if (age < 5) {
            float f = 1 - age / 5, w = 0.25F + 0.55F * f, height = 5 * (0.6F + 0.4F * age / 5);
            // 光墙换过贴图，共享的 BufferBuilder 已经结束，必须重新取光刺的缓冲
            VertexConsumer pillar = add(buffers, SPIKE);
            for (int plane = 0; plane < 2; plane++) {
                poseStack.pushPose();
                poseStack.mulPose(Axis.YP.rotationDegrees(plane * 90 + 45));
                StarfallDraw.rect(poseStack.last(), pillar, -w, 0, w, height, 0, 0, 1, 1, 0, c(f), c(0.95F * f), c(0.8F * f));
                poseStack.popPose();
            }
        }
    }

    /** 星焰：三片火舌面绕喷射轴排开，外层金、内芯白，纹理沿喷射方向滚动；喷口一团闪光。 */
    private void flare(BearerVfxEntity entity, float age, PoseStack poseStack, MultiBufferSource buffers) {
        float grow = Math.min(1, age / 3);
        float fade = age < 7 ? 1 : Math.max(0, 1 - (age - 7) / 5);
        if (age < 5) glow(poseStack, buffers, entityRenderDispatcher.cameraOrientation(), Vec3.ZERO, 1.6F, age * 15, 1 - age / 5, 0.95F * (1 - age / 5), 0.8F * (1 - age / 5));
        poseStack.mulPose(Axis.YP.rotationDegrees(-entity.getYRot()));
        poseStack.mulPose(Axis.XP.rotationDegrees(10));
        float length = 4.6F * grow, scroll = age * 0.18F;
        VertexConsumer flame = add(buffers, FLARE);
        for (int layer = 0; layer < 2; layer++) {
            boolean core = layer == 1;
            float w0 = core ? 0.15F : 0.3F, w1 = (core ? 0.9F : 2.4F) * grow, len = core ? length * 0.8F : length;
            float b = (core ? 1 : 0.85F) * fade;
            int planes = core ? 2 : 3;
            for (int p = 0; p < planes; p++) {
                poseStack.pushPose();
                poseStack.mulPose(Axis.ZP.rotationDegrees(p * 180F / planes));
                StarfallDraw.quad(poseStack.last(), flame, new Vec3(-w0, 0, 0), new Vec3(w0, 0, 0), new Vec3(w1, 0, len), new Vec3(-w1, 0, len),
                    0, scroll, 1, scroll + 1.2F, c(b), c((core ? 0.96F : 0.8F) * b), c((core ? 0.85F : 0.45F) * b));
                poseStack.popPose();
            }
        }
    }

    /** 碎星：白闪、十二道放射光刺、一横一斜两圈光环。 */
    private void shatter(BearerVfxEntity entity, float age, PoseStack poseStack, MultiBufferSource buffers) {
        float t = Math.min(1, age / 16);
        float flash = (1 - t) * (1 - t);
        Quaternionf camera = entityRenderDispatcher.cameraOrientation();
        glow(poseStack, buffers, camera, Vec3.ZERO, 1 + age * 0.5F, age * 6, flash * 1.2F, flash * 1.1F, flash);
        Vec3 toCamera = entityRenderDispatcher.camera.getPosition().subtract(entity.position()).normalize();
        RandomSource r = RandomSource.create(entity.getId() * 17L);
        VertexConsumer spike = add(buffers, SPIKE);
        for (int i = 0; i < 12; i++) {
            Vec3 dir = new Vec3(r.nextGaussian(), r.nextGaussian() * 0.7 + 0.2, r.nextGaussian()).normalize();
            float len = (0.6F + age * 0.4F) * (0.7F + 0.3F * r.nextFloat());
            StarfallDraw.ribbon(poseStack.last(), spike, Vec3.ZERO, dir, toCamera, len, 0.16F * (1 - t), 0.01F, 0, 1,
                c(flash * 1.3F), c(flash * 1.15F), c(flash * 0.8F));
        }
        float radius = 0.4F + age * 0.32F, width = 0.22F * (1 - t);
        for (int ring = 0; ring < 2; ring++) {
            poseStack.pushPose();
            if (ring == 1) poseStack.mulPose(Axis.XP.rotationDegrees(62));
            PoseStack.Pose pose = poseStack.last();
            for (int i = 0; i < 32; i++) {
                double a0 = i * Mth.TWO_PI / 32, a1 = (i + 1) * Mth.TWO_PI / 32;
                Vec3 i0 = new Vec3(Math.cos(a0) * radius, 0, Math.sin(a0) * radius), i1 = new Vec3(Math.cos(a1) * radius, 0, Math.sin(a1) * radius);
                Vec3 o0 = i0.scale((radius + width) / radius), o1 = i1.scale((radius + width) / radius);
                StarfallDraw.quad(pose, spike, i0, i1, o1, o0, 0, 0, 1, 0.2F, c(flash), c(0.85F * flash), c(0.5F * flash));
            }
            poseStack.popPose();
        }
    }

    /** 抛星落点：小圣印随蓄势加速旋转、变亮，外圈一道收拢的虚印锁定落点；临落地时天上垂下一线光。 */
    private void brand(BearerVfxEntity entity, float age, PoseStack poseStack, MultiBufferSource buffers) {
        int warn = Math.max(1, entity.warn());
        float charge = Math.min(1, age / warn);
        float post = Math.max(0, age - warn);
        float b = (0.25F + 0.75F * charge) * (0.85F + 0.15F * Mth.sin(age * 0.8F));
        if (post > 0) b = Math.max(0, 1 - post / 10);
        float r = StarShardEntity.RADIUS;
        poseStack.pushPose();
        poseStack.translate(0, 0.03, 0);
        poseStack.mulPose(Axis.YP.rotationDegrees(age * 2 + charge * charge * warn * 6));
        flat(poseStack.last(), add(buffers, SIGIL), r, b, 0.72F * b, 0.38F * b);
        if (post == 0) {
            poseStack.mulPose(Axis.YP.rotationDegrees(-age * 7));
            poseStack.translate(0, 0.01, 0);
            float lock = 0.5F * b * charge;
            flat(poseStack.last(), add(buffers, SIGIL), r * (1.7F - 0.7F * charge), lock, 0.8F * lock, 0.5F * lock);
        }
        poseStack.popPose();
        if (charge > 0.55F && post == 0) {
            float k = (charge - 0.55F) / 0.45F * 0.7F;
            VertexConsumer spike = add(buffers, SPIKE);
            for (int plane = 0; plane < 2; plane++) {
                poseStack.pushPose();
                poseStack.mulPose(Axis.YP.rotationDegrees(plane * 90 + 45));
                StarfallDraw.rect(poseStack.last(), spike, -0.12F, 0, 0.12F, 7, 0, 0, 1, 1, 0, c(k), c(0.9F * k), c(0.6F * k));
                poseStack.popPose();
            }
        }
    }

    /** 负星横扫：靛紫拖尾、青白刃口的斜劈扇面，外沿立一圈弧形光幕；晚 1.5 拍跟一道暗些的回响弧；前锋挂一颗四芒星。 */
    private void arc(BearerVfxEntity entity, float age, PoseStack poseStack, MultiBufferSource buffers) {
        int swing = BearerVfxEntity.ARC_SWING;
        float fade = age < swing + 1 ? 1 : Math.max(0, 1 - (age - swing - 1) / 5);
        if (fade <= 0) return;
        float lead = BearerVfxEntity.arcLead(age), tail = BearerVfxEntity.arcLead(age - 3.5F);
        if (age <= swing + 0.5F) {
            float a = lead * Mth.DEG_TO_RAD, r = BearerVfxEntity.ARC_R;
            Vec3 tip = BearerVfxEntity.toWorld(entity.getYRot(), Mth.sin(a) * r, BearerVfxEntity.arcY(lead), Mth.cos(a) * r);
            sprite(poseStack, buffers, entityRenderDispatcher.cameraOrientation(), LENS, tip, 1.1F, age * 20, 1, 1, 1);
        }
        poseStack.mulPose(Axis.YP.rotationDegrees(-entity.getYRot()));
        sweepBand(poseStack.last(), buffers, tail, lead, 1.2F, BearerVfxEntity.ARC_R, 0, fade);
        sweepBand(poseStack.last(), buffers, BearerVfxEntity.arcLead(age - 5.5F), BearerVfxEntity.arcLead(age - 1.5F),
            1.0F, BearerVfxEntity.ARC_R * 0.82F, 0.4F, 0.45F * fade);
    }

    private static Vec3 arcPoint(float angle, float r, float dy) {
        float a = angle * Mth.DEG_TO_RAD;
        return new Vec3(Mth.sin(a) * r, BearerVfxEntity.arcY(angle) + dy, Mth.cos(a) * r);
    }

    /** 扇面内低外高（像一片斜劈的刃），外沿再立一圈光幕，平视也看得见。u 沿弧（0 弧尾到 1 前锋），v 沿半径。 */
    private static void sweepBand(PoseStack.Pose pose, MultiBufferSource buffers, float tail, float lead, float r0, float r1, float dy, float b) {
        if (lead - tail < 0.5F || b <= 0) return;
        VertexConsumer buffer = add(buffers, ARC);
        for (int i = 0; i < ARC_SEGMENTS; i++) {
            float t0 = i / (float) ARC_SEGMENTS, t1 = (i + 1) / (float) ARC_SEGMENTS;
            float a0 = Mth.lerp(t0, tail, lead), a1 = Mth.lerp(t1, tail, lead);
            StarfallDraw.quad(pose, buffer, arcPoint(a0, r0, dy - 0.3F), arcPoint(a1, r0, dy - 0.3F), arcPoint(a1, r1, dy + 0.1F), arcPoint(a0, r1, dy + 0.1F),
                t0, 0, t1, 1, c(b), c(b), c(b));
            float w = 0.8F * b;
            StarfallDraw.quad(pose, buffer, arcPoint(a0, r1, dy - 0.75F), arcPoint(a1, r1, dy - 0.75F), arcPoint(a1, r1, dy + 0.55F), arcPoint(a0, r1, dy + 0.55F),
                t0, 0.35F, t1, 1, c(w), c(w), c(w));
        }
    }

    /**
     * 负星者身上的负光，坐标都相对实体渲染原点：横扫前摇时拳头上越聚越亮的冷星和收拢的光环；
     * 光束蓄力时胸口星晶聚光、两道光环向内收、一根细瞄准线；照射时双层光束、顺着光束外流的光环、两端四芒星。
     */
    public static void negative(StarBearerEntity bearer, PoseStack poseStack, MultiBufferSource buffers, float partialTick, Quaternionf camera,
                                Vec3 cameraPos, @Nullable Vec3 chest, @Nullable Vec3 fist) {
        int act = bearer.clientAction();
        float age = bearer.clientActionAge(partialTick);
        int swing = StarBearerEntity.SWEEP_SWING;
        if (act == StarBearerEntity.ACT_SWEEP && fist != null && age < swing + 2) {
            float charge = Math.min(1, age / swing), out = age > swing ? Math.max(0, 1 - (age - swing) / 2) : 1;
            float b = (0.3F + 0.7F * charge) * out;
            sprite(poseStack, buffers, camera, LENS, fist, (0.35F + 0.75F * charge) * out, age * 9, b, b, b);
            sprite(poseStack, buffers, camera, HALO, fist, (1.3F - 0.75F * charge) * out, -age * 14, 0.6F * b, 0.6F * b, 0.6F * b);
        }
        if (act != StarBearerEntity.ACT_BEAM || chest == null) return;
        Vec3 dir = StarBearerEntity.beamDir(Mth.rotLerp(partialTick, bearer.beamYawO, bearer.beamYaw()), Mth.lerp(partialTick, bearer.beamPitchO, bearer.beamPitch()));
        float len = Mth.lerp(partialTick, bearer.beamLengthO, bearer.beamLength);
        if (len < 0.3F) len = bearer.beamLength > 0.3F ? bearer.beamLength : StarBearerEntity.BEAM_RANGE;
        Vec3 end = chest.add(dir.scale(len));
        Vec3 toCamera = cameraPos.subtract(bearer.getPosition(partialTick).add(chest.add(end).scale(0.5))).normalize();
        int fire = StarBearerEntity.BEAM_FIRE, stop = StarBearerEntity.BEAM_STOP;
        if (age < fire) {
            float charge = age / fire, b = 0.3F + 0.7F * charge;
            sprite(poseStack, buffers, camera, LENS, chest, 0.4F + 1.0F * charge, age * 4, b, b, b);
            disc(poseStack.last(), add(buffers, HALO), chest, dir, 1.7F - 1.2F * charge, age * 6, 0.7F * b, 0.7F * b, 0.7F * b);
            disc(poseStack.last(), add(buffers, HALO), chest.add(dir.scale(0.3)), dir, 1.1F - 0.7F * charge, -age * 11, 0.5F * b, 0.5F * b, 0.5F * b);
            float flicker = (0.25F + 0.5F * charge) * (0.75F + 0.25F * Mth.sin(age * 2.3F));
            beamRibbon(poseStack.last(), add(buffers, BEAM), chest, dir, len, toCamera, 0.02F + 0.035F * charge, 0.42F, 0.58F, 0, 1, flicker, flicker, flicker);
            return;
        }
        float f = age - fire;
        float close = age < stop ? 1 : Math.max(0, 1 - (age - stop) / 4);
        if (close <= 0) return;
        float open = Math.min(1, f / 2), flash = f < 3 ? 1 - f / 3 : 0;
        float w = (0.42F * (1 + 0.1F * Mth.sin(age * 1.9F)) * open + 0.5F * flash) * close;
        float scroll = -age * 0.45F;
        beamRibbon(poseStack.last(), add(buffers, BEAM), chest, dir, len, toCamera, w, 0, 1, scroll, scroll + len / 1.6F, close, close, close);
        beamRibbon(poseStack.last(), add(buffers, BEAM), chest, dir, len, toCamera, w * 0.32F, 0.4F, 0.6F, 0, 1, close, close, close);
        // 细光环每 2 格一道，以 1.2 格/拍顺着光束往外流
        VertexConsumer halo = add(buffers, HALO);
        for (float s = (f * 1.2F) % 2.0F + 0.6F; s < len; s += 2.0F) {
            float b = 0.8F * close * (1 - 0.6F * s / len);
            disc(poseStack.last(), halo, chest.add(dir.scale(s)), dir, (0.55F + 0.25F * flash) * close, s * 40 + age * 8, b, b, b);
        }
        float pulse = 0.85F + 0.15F * Mth.sin(age * 3.1F);
        sprite(poseStack, buffers, camera, LENS, chest, (1.3F + 1.2F * flash) * close * pulse, age * 3, close, close, close);
        sprite(poseStack, buffers, camera, LENS, end, 1.2F * close * pulse, -age * 5, close, close, close);
        float hit = 0.8F * close;
        disc(poseStack.last(), add(buffers, HALO), end.subtract(dir.scale(0.05)), dir, (0.8F + 0.25F * Mth.sin(age * 2.2F)) * close, age * 15, hit, hit, hit);
    }

    @Override public ResourceLocation getTextureLocation(BearerVfxEntity entity) { return SIGIL; }
}
