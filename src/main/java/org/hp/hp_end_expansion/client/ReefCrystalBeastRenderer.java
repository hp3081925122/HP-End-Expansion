package org.hp.hp_end_expansion.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.hp.hp_end_expansion.Hp_end_expansion;
import org.hp.hp_end_expansion.entity.tidelight.ReefCrystalBeastEntity;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

import java.util.ArrayList;
import java.util.List;

import static org.hp.hp_end_expansion.entity.tidelight.ReefCrystalBeastEntity.*;

/**
 * 礁晶兽渲染：模型 + 发光层，以及「潮吸吐息」的特效。
 * 蓄力时往嘴里汇集的水流不用贴图：每股是三根细水绳拧在一起的程序化管子，每个顶点按朝向现算颜色——侧缘亮而实、正对视线的中间透，
 * 沿长度有亮带朝嘴走，拧花也朝嘴转。水柱、水球、脉冲环、冲击水花用贴图。坐标都相对实体脚底、世界轴向。
 */
public final class ReefCrystalBeastRenderer extends GeoEntityRenderer<ReefCrystalBeastEntity> {
    private static final ResourceLocation WHITE = fx("white");
    private static final ResourceLocation RING = fx("ring");
    private static final ResourceLocation SPLASH = fx("splash"), SPLASH_GLOW = fx("splash_glow");
    private static final ResourceLocation JET = fx("jet"), JET_GLOW = fx("jet_glow");
    private static final ResourceLocation ORB = fx("orb"), ORB_GLOW = fx("orb_glow");
    private static final int STREAM_SEGMENTS = 22, STRANDS = 3, SIDES = 8;
    // 潮光礁海荧藻色阶
    private static final float[] DEEP = rgb(0x0F6B72), MID = rgb(0x18A3A0), BRIGHT = rgb(0x4FDCCB), FOAM = rgb(0xBDFFF0);

    public ReefCrystalBeastRenderer(EntityRendererProvider.Context context) {
        super(context, new Model());
        shadowRadius = 1.0F;
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    private static ResourceLocation fx(String name) {
        return ResourceLocation.fromNamespaceAndPath(Hp_end_expansion.MODID, "textures/effect/reef_beast_" + name + ".png");
    }

    private static float[] rgb(int c) { return new float[]{(c >> 16 & 255) / 255F, (c >> 8 & 255) / 255F, (c & 255) / 255F}; }

    @Override public void render(ReefCrystalBeastEntity entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
        super.render(entity, yaw, partialTick, poseStack, buffers, light);
        int phase = entity.phase();
        if (phase == NONE || phase == OPEN && entity.phaseAge(partialTick) < OPEN_TICKS - 3) return;
        Vec3 camera = entityRenderDispatcher.camera.getPosition().subtract(entity.getPosition(partialTick));
        float bodyYaw = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
        float time = entity.tickCount + partialTick;
        float age = entity.phaseAge(partialTick);
        Vec3 forward = Vec3.directionFromRotation(0, bodyYaw);
        switch (phase) {
            case OPEN -> {
                float r = 0.12F * (age - (OPEN_TICKS - 3)) / 3;
                orb(poseStack, buffers, mouthOffset(bodyYaw, OPEN).add(forward.scale(0.1 + 0.7 * r)), r, time, 1);
            }
            case CHARGE -> charge(poseStack, buffers, bodyYaw, forward, age, time, camera);
            case FIRE -> fire(entity, poseStack, buffers, bodyYaw, age, time, camera, partialTick);
            default -> {
                // 收口：水球余量缩回去
                float k = 1 - age / 5;
                if (k > 0) orb(poseStack, buffers, mouthOffset(bodyYaw, RECOVER).add(forward.scale(0.2)), 0.3F * k, time, k);
            }
        }
    }

    // ---------------- 蓄力：六股拧绳水流 + 地面涟漪 + 口中水球 ----------------
    private record Strand(Vec3[] pts, float[] rad, float[] fade, float time) {}

    private void charge(PoseStack poseStack, MultiBufferSource buffers, float bodyYaw, Vec3 forward, float age, float time, Vec3 camera) {
        PoseStack.Pose pose = poseStack.last();
        float p = Mth.clamp(age / CHARGE_TICKS, 0, 1);
        // 水球前段跟水头一起涨起来，蓄满后微微鼓动；球心随半径往嘴前推，卡在张开的上下颌之间而不是埋进鼻尖
        float grow = Mth.clamp((p - 0.1F) / 0.75F, 0, 1);
        float radius = 0.14F + 0.36F * grow + 0.03F * Mth.sin(time * 0.9F) * grow;
        Vec3 mouth = mouthOffset(bodyYaw, CHARGE).add(forward.scale(0.1 + 0.7 * radius));
        // 水头前三成时间从源头流到嘴；最后一成半水流从外侧断开、被吸干
        float head = Math.min(1, p / 0.3F);
        float tail = Math.max(0, (p - 0.85F) / 0.15F);
        List<Strand> strands = new ArrayList<>();
        int n = STREAM_SEGMENTS + 1;
        for (int i = 0; i < STREAMS; i++) {
            float h = Mth.clamp(head * 1.2F - (i % 3) * 0.06F, 0, 1);  // 六股错开一点到达
            if (h <= tail) continue;
            Vec3[] c = new Vec3[n];
            float[] s = new float[n];
            for (int k = 0; k < n; k++) {
                s[k] = tail + (h - tail) * k / STREAM_SEGMENTS;
                c[k] = streamPoint(i, s[k], time, bodyYaw, mouth);
            }
            Vec3[][] fr = frames(c);
            float[] arc = arcLengths(c);
            // 三根细绳绕中心线拧着走：拧花随时间朝嘴转，越近嘴绕得越紧，最后并成一股扎进水球
            for (int m = 0; m < STRANDS; m++) {
                Vec3[] pts = new Vec3[n];
                float[] rad = new float[n], fade = new float[n];
                float thick = m == 0 ? 1.15F : 0.85F;
                for (int k = 0; k < n; k++) {
                    // 源头从地面冒出、断开处从外侧收细：两头都没有管口切面
                    float open = Math.min(Mth.clamp(s[k] * 6, 0, 1), tail > 0 ? Mth.clamp((s[k] - tail) / 0.06F, 0, 1) : 1);
                    float phi = Mth.TWO_PI * m / STRANDS + arc[k] * 2.2F - time * 0.45F + i;
                    float hr = (0.03F + 0.17F * (1 - s[k])) * (0.3F + 0.7F * open);
                    pts[k] = c[k].add(fr[0][k].scale(Mth.cos(phi) * hr)).add(fr[1][k].scale(Mth.sin(phi) * hr));
                    rad[k] = (0.075F - 0.035F * s[k]) * thick * (0.4F + 0.6F * open);
                    fade[k] = open;
                }
                if (h < 0.999F) rad[n - 1] = 0.012F;  // 水头收尖
                strands.add(new Strand(pts, rad, fade, time + i * 7 + m * 3));
            }
        }
        // 1.21 的共享缓冲里换 RenderType 会结束上一批、旧的 VertexConsumer 失效：所有水体一遍画完，再一遍画亮芯
        VertexConsumer body = buffers.getBuffer(RenderType.entityTranslucentCull(WHITE));
        for (Strand st : strands) tubeBody(pose, body, st, camera);
        VertexConsumer glow = buffers.getBuffer(RenderType.eyes(WHITE));
        for (Strand st : strands) tubeGlow(pose, glow, st, camera);
        // 源头涟漪：水被抽起的地方一圈圈往里收
        if (p < 0.85F) {
            VertexConsumer ring = buffers.getBuffer(RenderType.entityTranslucent(RING));
            for (int i = 0; i < STREAMS; i++) {
                Vec3 src = streamPoint(i, 0, time, bodyYaw, mouth);
                float cycle = (time * 0.08F + i * 0.37F) % 1F;
                ringQuad(pose, ring, new Vec3(src.x, 0.05 + i * 0.002, src.z), new Vec3(0, 1, 0), 1.1F - 0.7F * cycle, time * 2 + i * 40,
                    Math.min(1, cycle * 3) * (1 - p * 0.6F) * 0.85F);
            }
        }
        orb(poseStack, buffers, mouth, radius, time, 1);
    }

    // ---------------- 吐射：水柱 + 压力环 + 嘴边水球 + 冲击水花 ----------------
    private void fire(ReefCrystalBeastEntity entity, PoseStack poseStack, MultiBufferSource buffers, float bodyYaw, float age, float time, Vec3 camera, float partial) {
        PoseStack.Pose pose = poseStack.last();
        Vec3 from = mouthOffset(bodyYaw, FIRE);
        float aimYaw = Mth.rotLerp(partial, entity.aimYawO, entity.aimYaw());
        float aimPitch = Mth.lerp(partial, entity.aimPitchO, entity.aimPitch());
        Vec3 dir = Vec3.directionFromRotation(aimPitch, aimYaw);
        float len = Math.min(entity.jetLength(), (age + 1) * 5);
        float power = Mth.clamp((FIRE_TICKS - age) / 5, 0, 1);       // 最后 5 tick 变细消失
        float burst = age < 3 ? 1.35F - 0.12F * age : 1;             // 刚喷出那几拍更粗
        if (len > 0.05F && power > 0) {
            float r = 0.4F * burst * (0.35F + 0.65F * power);
            // 水柱：朝镜头的贴图带，贴图沿长度往外滚；上面叠一条更窄、滚得更快的加法亮芯
            jetRibbon(pose, buffers.getBuffer(RenderType.entityTranslucent(JET)), from, dir, len, r, time, camera, 0.35F, 1, 0.85F * power);
            jetRibbon(pose, buffers.getBuffer(RenderType.eyes(JET_GLOW)), from, dir, len, r * 0.55F, time, camera, 0.6F, 0.85F * power, 1);
            // 压力环：每 3 tick 从嘴放出一圈，沿水柱以每 tick 0.9 格往外走，越远越淡
            VertexConsumer ring = buffers.getBuffer(RenderType.entityTranslucent(RING));
            for (int n = 0; n < 8; n++) {
                float t = age - n * 3;
                if (t < 0) continue;
                float d = t * 0.9F + 0.4F;
                if (d > len) continue;
                ringQuad(pose, ring, from.add(dir.scale(d)), dir, r * (1.6F + 0.06F * d), time * 6 + n * 50, (1 - d / (float) JET_RANGE) * power * 0.9F);
            }
            // 冲击点水花：朝镜头，叠一层加法亮芯
            Vec3 end = from.add(dir.scale(len));
            float pulse = 0.85F + 0.15F * Mth.sin(time * 1.7F);
            billboard(poseStack, buffers.getBuffer(RenderType.entityTranslucent(SPLASH)), end, 0.95F * pulse * burst * (0.5F + 0.5F * power), time * 9, 1, 1, 1, 0.9F * power);
            billboard(poseStack, buffers.getBuffer(RenderType.eyes(SPLASH_GLOW)), end, 0.7F * pulse, -time * 5, power, power, power, 1);
        }
        // 嘴边：蓄满的水球在开喷时被挤成一圈喷口
        float k = Math.max(0, 1 - age / 4);
        if (k > 0) orb(poseStack, buffers, from.add(dir.scale(0.2)), 0.5F * k + 0.2F, time, k);
        ringQuad(pose, buffers.getBuffer(RenderType.entityTranslucent(RING)), from.add(dir.scale(0.15)), dir, 0.55F * burst, time * 12, power);
    }

    /** 水柱贴图带：贴图 16×64，宽 2r 时一整张沿长度铺 8r；出口挤成细颈再撑开，边缘随时间起伏。 */
    private static void jetRibbon(PoseStack.Pose pose, VertexConsumer vc, Vec3 from, Vec3 dir, float len, float r, float time, Vec3 camera,
                                  float scroll, float bright, float alpha) {
        int seg = Math.max(4, Mth.ceil(len * 2));
        float tile = r * 8, shift = time * scroll % 1F;
        Vec3 pa = null, sa = null;
        float va = 0;
        for (int k = 0; k <= seg; k++) {
            float d = len * k / seg, s = (float) k / seg;
            Vec3 c = from.add(dir.scale(d));
            float w = r * (0.6F + 0.4F * Mth.clamp(d / 0.6F, 0, 1)) * (1 + 0.15F * s) * (1 + 0.08F * Mth.sin(d * 2.5F - time * 1.3F));
            Vec3 sd = side(dir, camera.subtract(c)).scale(w);
            float v = d / tile - shift;
            if (pa != null) quad(pose, vc, pa.subtract(sa), pa.add(sa), c.add(sd), c.subtract(sd), 0, va, 1, v, bright, bright, bright, alpha);
            pa = c; sa = sd; va = v;
        }
    }

    /** 口中水球：朝镜头的贴图片慢慢转，叠一层加法亮芯。贴图里的球直径约占 0.8 格宽，所以片子半宽取 1.25r。 */
    private void orb(PoseStack poseStack, MultiBufferSource buffers, Vec3 c, float r, float time, float fade) {
        if (r <= 0.01F || fade <= 0) return;
        float size = r * 1.25F;
        billboard(poseStack, buffers.getBuffer(RenderType.entityTranslucent(ORB)), c, size, time * 4, 1, 1, 1, 0.9F * fade);
        billboard(poseStack, buffers.getBuffer(RenderType.eyes(ORB_GLOW)), c, size, time * 4, 0.8F * fade, 0.8F * fade, 0.8F * fade, 1);
    }

    // ---------------- 程序化水绳 ----------------

    /** 水体：SIDES 边的管子，只画朝外的面（cull），中间不会叠出两层奶白。四点顺序 (k,j)(k,j+1)(k+1,j+1)(k+1,j) 绕向朝外。 */
    private static void tubeBody(PoseStack.Pose pose, VertexConsumer vc, Strand st, Vec3 camera) {
        Vec3[] pts = st.pts();
        Vec3[][] fr = frames(pts);
        float[] arc = arcLengths(pts);
        for (int k = 0; k < pts.length - 1; k++) {
            for (int j = 0; j < SIDES; j++) {
                for (int[] q : new int[][]{{k, j}, {k, j + 1}, {k + 1, j + 1}, {k + 1, j}}) {
                    int i = q[0];
                    tubeVertex(pose, vc, pts[i], fr[0][i], fr[1][i], st.rad()[i], q[1], arc[i], st.fade()[i], st.time(), camera);
                }
            }
        }
    }

    /** 亮芯：沿中心线一条朝镜头的加法带，亮度跟着亮带走。 */
    private static void tubeGlow(PoseStack.Pose pose, VertexConsumer vc, Strand st, Vec3 camera) {
        Vec3[] pts = st.pts();
        float[] arc = arcLengths(pts), rad = st.rad(), fade = st.fade();
        for (int k = 0; k < pts.length - 1; k++) {
            Vec3 sa = side(tangent(pts, k), camera.subtract(pts[k])).scale(rad[k] * 0.45);
            Vec3 sb = side(tangent(pts, k + 1), camera.subtract(pts[k + 1])).scale(rad[k + 1] * 0.45);
            float ca = coreBright(arc[k], st.time()) * fade[k], cb = coreBright(arc[k + 1], st.time()) * fade[k + 1];
            glowVertex(pose, vc, pts[k].add(sa), ca);
            glowVertex(pose, vc, pts[k].subtract(sa), ca);
            glowVertex(pose, vc, pts[k + 1].subtract(sb), cb);
            glowVertex(pose, vc, pts[k + 1].add(sb), cb);
        }
    }

    /** 平行移动框架 {法线, 副法线}，转弯时不扭。 */
    private static Vec3[][] frames(Vec3[] pts) {
        int n = pts.length;
        Vec3[] nrm = new Vec3[n], bin = new Vec3[n];
        Vec3 nn = perpendicular(tangent(pts, 0));
        for (int k = 0; k < n; k++) {
            Vec3 t = tangent(pts, k);
            nn = nn.subtract(t.scale(nn.dot(t)));
            nn = nn.lengthSqr() < 1.0E-6 ? perpendicular(t) : nn.normalize();
            nrm[k] = nn;
            bin[k] = t.cross(nn).normalize();
        }
        return new Vec3[][]{nrm, bin};
    }

    private static float[] arcLengths(Vec3[] pts) {
        float[] arc = new float[pts.length];
        for (int k = 1; k < pts.length; k++) arc[k] = arc[k - 1] + (float) pts[k].distanceTo(pts[k - 1]);
        return arc;
    }

    /** 沿长度的亮带：六次方，窄而亮的一道，以每 tick 约 0.23 格朝嘴走。 */
    private static float band(float arc, float time) {
        float b = Math.max(0, Mth.sin(arc * 2.4F - time * 0.55F));
        b *= b;
        return b * b * b;
    }

    private static float coreBright(float arc, float time) { return 0.16F + 0.5F * band(arc, time); }

    private static void tubeVertex(PoseStack.Pose pose, VertexConsumer vc, Vec3 c, Vec3 nrm, Vec3 bin, float r, int j, float arc, float fade,
                                   float time, Vec3 camera) {
        float a = Mth.TWO_PI * j / SIDES;
        Vec3 out = nrm.scale(Mth.cos(a)).add(bin.scale(Mth.sin(a)));
        // 表面起伏：沿长度的波 + 绕一圈两个波峰
        float wob = 1 + 0.14F * Mth.sin(arc * 5 - time * 1.1F + 2 * a);
        shade(pose, vc, c.add(out.scale(r * wob)), out, camera, band(arc, time), fade);
    }

    /** 水的着色：侧缘亮而实、正面透；亮带和顶面高光把颜色推向泡沫白。 */
    private static void shade(PoseStack.Pose pose, VertexConsumer vc, Vec3 p, Vec3 normal, Vec3 camera, float band, float fade) {
        Vec3 view = camera.subtract(p).normalize();
        float fres = 1 - (float) Math.abs(normal.dot(view));
        float f2 = fres * fres;
        float top = (float) Math.max(0, normal.y);
        top *= top; top *= top;
        float hi = Mth.clamp(band * 0.75F + top * 0.45F, 0, 1);
        float[] base = new float[3];
        for (int i = 0; i < 3; i++) {
            float body = Mth.lerp(f2, Mth.lerp(fres, DEEP[i], MID[i]), BRIGHT[i]);
            base[i] = Mth.lerp(hi, body, FOAM[i]);
        }
        float alpha = Mth.clamp(0.2F + 0.62F * fres * (float) Math.sqrt(fres) + 0.35F * hi, 0, 0.95F) * fade;
        StarfallDraw.vertex(pose, vc, p, 0.5F, 0.5F, base[0], base[1], base[2], alpha);
    }

    private static void glowVertex(PoseStack.Pose pose, VertexConsumer vc, Vec3 p, float bright) {
        StarfallDraw.vertex(pose, vc, p, 0.5F, 0.5F, BRIGHT[0] * bright, BRIGHT[1] * bright, BRIGHT[2] * bright, 1);
    }

    // ---------------- 小工具 ----------------
    private void billboard(PoseStack poseStack, VertexConsumer vc, Vec3 at, float r, float spin, float red, float green, float blue, float alpha) {
        poseStack.pushPose();
        poseStack.translate(at.x, at.y, at.z);
        poseStack.mulPose(entityRenderDispatcher.cameraOrientation());
        poseStack.mulPose(Axis.ZP.rotationDegrees(spin));
        PoseStack.Pose pose = poseStack.last();
        quad(pose, vc, new Vec3(-r, -r, 0), new Vec3(r, -r, 0), new Vec3(r, r, 0), new Vec3(-r, r, 0), 0, 1, 1, 0, red, green, blue, alpha);
        poseStack.popPose();
    }

    /** 垂直于 axis 的方形面片，用环形贴图。 */
    private static void ringQuad(PoseStack.Pose pose, VertexConsumer vc, Vec3 c, Vec3 axis, float r, float spin, float alpha) {
        if (alpha <= 0) return;
        Vec3 e1 = perpendicular(axis), e2 = axis.cross(e1).normalize();
        float a = spin * Mth.DEG_TO_RAD;
        Vec3 u = e1.scale(Mth.cos(a) * r).add(e2.scale(Mth.sin(a) * r));
        Vec3 v = e2.scale(Mth.cos(a) * r).subtract(e1.scale(Mth.sin(a) * r));
        quad(pose, vc, c.subtract(u).subtract(v), c.add(u).subtract(v), c.add(u).add(v), c.subtract(u).add(v), 0, 0, 1, 1, 1, 1, 1, alpha);
    }

    private static Vec3 perpendicular(Vec3 axis) {
        Vec3 ref = Math.abs(axis.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        return axis.cross(ref).normalize();
    }

    private static Vec3 tangent(Vec3[] pts, int k) {
        Vec3 a = pts[Math.max(0, k - 1)], b = pts[Math.min(pts.length - 1, k + 1)];
        Vec3 t = b.subtract(a);
        return t.lengthSqr() < 1.0E-8 ? new Vec3(0, 1, 0) : t.normalize();
    }

    /** 带子横向：切线 × 视线；视线和切线平行时随便取一个垂直方向。 */
    private static Vec3 side(Vec3 tangent, Vec3 toCamera) {
        Vec3 s = tangent.cross(toCamera);
        if (s.lengthSqr() < 1.0E-6) s = perpendicular(tangent);
        return s.normalize();
    }

    /** a(u0,v0) b(u1,v0) c(u1,v1) d(u0,v1)，两面都画。 */
    private static void quad(PoseStack.Pose pose, VertexConsumer vc, Vec3 a, Vec3 b, Vec3 c, Vec3 d,
                             float u0, float v0, float u1, float v1, float red, float green, float blue, float alpha) {
        StarfallDraw.vertex(pose, vc, a, u0, v0, red, green, blue, alpha);
        StarfallDraw.vertex(pose, vc, b, u1, v0, red, green, blue, alpha);
        StarfallDraw.vertex(pose, vc, c, u1, v1, red, green, blue, alpha);
        StarfallDraw.vertex(pose, vc, d, u0, v1, red, green, blue, alpha);
        StarfallDraw.vertex(pose, vc, d, u0, v1, red, green, blue, alpha);
        StarfallDraw.vertex(pose, vc, c, u1, v1, red, green, blue, alpha);
        StarfallDraw.vertex(pose, vc, b, u1, v0, red, green, blue, alpha);
        StarfallDraw.vertex(pose, vc, a, u0, v0, red, green, blue, alpha);
    }

    private static final class Model extends GeoModel<ReefCrystalBeastEntity> {
        private static final ResourceLocation MODEL = id("geo/reef_crystal_beast.geo.json");
        private static final ResourceLocation TEXTURE = id("textures/entity/reef_crystal_beast.png");
        private static final ResourceLocation ANIMATION = id("animations/reef_crystal_beast.animation.json");
        private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(Hp_end_expansion.MODID, path); }
        @Override public ResourceLocation getModelResource(ReefCrystalBeastEntity entity) { return MODEL; }
        @Override public ResourceLocation getTextureResource(ReefCrystalBeastEntity entity) { return TEXTURE; }
        @Override public ResourceLocation getAnimationResource(ReefCrystalBeastEntity entity) { return ANIMATION; }
    }
}
