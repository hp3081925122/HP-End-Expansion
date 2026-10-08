package org.hp.hp_end_expansion.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.hp.hp_end_expansion.Hp_end_expansion;
import org.hp.hp_end_expansion.entity.tidelight.TideRemnantHermitCrabEntity;
import org.hp.hp_end_expansion.entity.tidelight.TideVfxEntity;
import org.hp.hp_end_expansion.entity.tidelight.TideWaterBoltEntity;
import org.hp.hp_end_expansion.registry.ModEntities;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * 潮骸寄居蟹客户端：本体（含骸潮炮水柱）、水箭、间歇泉、潮汐水墙的渲染，以及技能震屏。
 * 特效贴图都是带透明度的手绘像素，用信标光束的半透明渲染：不受光照影响、保留透明度、V 方向可平铺滚动。
 */
@EventBusSubscriber(modid = Hp_end_expansion.MODID, value = Dist.CLIENT)
public final class TideCrabClient {
    private static final ResourceLocation BOLT = tex("tide_water_bolt");
    private static final ResourceLocation BEAM = tex("tide_cannon_beam");
    private static final ResourceLocation GEYSER = tex("tide_geyser");
    private static final ResourceLocation WAVE = tex("tide_wave");
    private static float shake, shakeO;
    private static int ticks;

    private TideCrabClient() {}

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(Hp_end_expansion.MODID, "textures/entity/" + name + ".png");
    }

    private static VertexConsumer buffer(MultiBufferSource buffers, ResourceLocation texture) {
        return buffers.getBuffer(RenderType.beaconBeam(texture, true));
    }

    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.TIDE_REMNANT_HERMIT_CRAB.get(), CrabRenderer::new);
        event.registerEntityRenderer(ModEntities.TIDE_WATER_BOLT.get(), BoltRenderer::new);
        event.registerEntityRenderer(ModEntities.TIDE_VFX.get(), VfxRenderer::new);
    }

    // ---------------- 绘制工具 ----------------

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, Vec3 p, float u, float v, float alpha) {
        buffer.addVertex(pose, (float) p.x, (float) p.y, (float) p.z)
            .setColor(1, 1, 1, Mth.clamp(alpha, 0, 1))
            .setUv(u, v)
            .setLight(LightTexture.FULL_BRIGHT)
            .setNormal(pose, 0, 1, 0);
    }

    /** a(u0,v0) b(u1,v0) c(u1,v1) d(u0,v1)，两面都画。 */
    private static void quad(PoseStack.Pose pose, VertexConsumer buffer, Vec3 a, Vec3 b, Vec3 c, Vec3 d,
                             float u0, float v0, float u1, float v1, float alpha) {
        vertex(pose, buffer, a, u0, v0, alpha);
        vertex(pose, buffer, b, u1, v0, alpha);
        vertex(pose, buffer, c, u1, v1, alpha);
        vertex(pose, buffer, d, u0, v1, alpha);
        vertex(pose, buffer, d, u0, v1, alpha);
        vertex(pose, buffer, c, u1, v1, alpha);
        vertex(pose, buffer, b, u1, v0, alpha);
        vertex(pose, buffer, a, u0, v0, alpha);
    }

    /** 从 head 沿 axis 拉出 length、半宽 w 的朝向镜头的长条，v0 在 head 一端。 */
    private static void ribbon(PoseStack.Pose pose, VertexConsumer buffer, Vec3 head, Vec3 axis, Vec3 toCamera, double length, double w,
                               float v0, float v1, float alpha) {
        Vec3 side = axis.cross(toCamera);
        if (side.lengthSqr() < 1.0E-6) side = axis.cross(new Vec3(1, 0, 0));
        side = side.normalize().scale(w);
        Vec3 end = head.add(axis.scale(length));
        quad(pose, buffer, head.add(side), head.subtract(side), end.subtract(side), end.add(side), 0, v0, 1, v1, alpha);
    }

    // ---------------- 本体 ----------------

    private static final class CrabModel extends GeoModel<TideRemnantHermitCrabEntity> {
        private static final ResourceLocation MODEL = id("geo/tide_remnant_hermit_crab.geo.json");
        private static final ResourceLocation TEXTURE = id("textures/entity/tide_remnant_hermit_crab.png");
        private static final ResourceLocation ANIMATION = id("animations/tide_remnant_hermit_crab.animation.json");
        private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(Hp_end_expansion.MODID, path); }
        @Override public ResourceLocation getModelResource(TideRemnantHermitCrabEntity animatable) { return MODEL; }
        @Override public ResourceLocation getTextureResource(TideRemnantHermitCrabEntity animatable) { return TEXTURE; }
        @Override public ResourceLocation getAnimationResource(TideRemnantHermitCrabEntity animatable) { return ANIMATION; }

        @Override public void setCustomAnimations(TideRemnantHermitCrabEntity animatable, long instanceId, AnimationState<TideRemnantHermitCrabEntity> animationState) {
            super.setCustomAnimations(animatable, instanceId, animationState);
            boolean broken = animatable.isSkullBroken();
            GeoBone skull = getAnimationProcessor().getBone("skull");
            if (skull != null) skull.setHidden(broken);
            GeoBone shell = getAnimationProcessor().getBone("shell");
            if (shell != null) {
                shell.setHidden(broken);
                shell.setChildrenHidden(broken);
            }
        }
    }

    private static final class CrabRenderer extends GeoEntityRenderer<TideRemnantHermitCrabEntity> {
        CrabRenderer(EntityRendererProvider.Context context) {
            super(context, new CrabModel());
            shadowRadius = 1.0F;
            addRenderLayer(new AutoGlowingGeoLayer<>(this));
        }

        // 死亡由动画表现，不叠加原版侧翻
        @Override protected float getDeathMaxRotation(TideRemnantHermitCrabEntity animatable) { return 0; }

        @Override public void preRender(PoseStack poseStack, TideRemnantHermitCrabEntity animatable, BakedGeoModel model, @Nullable MultiBufferSource bufferSource,
                                        @Nullable VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
            super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
            if (!isReRender) model.getBone("vfx_snout").ifPresent(bone -> bone.setTrackingMatrices(true));
        }

        @Override public void renderFinal(PoseStack poseStack, TideRemnantHermitCrabEntity crab, BakedGeoModel model, MultiBufferSource bufferSource,
                                          @Nullable VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay, int colour) {
            super.renderFinal(poseStack, crab, model, bufferSource, buffer, partialTick, packedLight, packedOverlay, colour);
            if (crab.getSkill() != TideRemnantHermitCrabEntity.CANNON || crab.isDeadOrDying()) return;
            float age = crab.getSkillAge(partialTick);
            if (age < TideRemnantHermitCrabEntity.CANNON_SWEEP || age > TideRemnantHermitCrabEntity.CANNON_SWEEP_END + 1) return;
            float yaw = Mth.rotLerp(partialTick, crab.yBodyRotO, crab.yBodyRot);
            Vec3 dir = TideRemnantHermitCrabEntity.beamDirection(yaw, TideRemnantHermitCrabEntity.sweepAngle(age));
            Vec3 origin = model.getBone("vfx_snout")
                .map(bone -> { Vector3f v = bone.getLocalSpaceMatrix().transformPosition(new Vector3f()); return new Vec3(v.x, v.y, v.z); })
                .orElse(crab.beamOrigin(yaw, TideRemnantHermitCrabEntity.sweepAngle(age)).subtract(crab.position()));
            Vec3 base = crab.getPosition(partialTick);
            double length = TideRemnantHermitCrabEntity.beamLength(crab.level(), base.add(origin), dir);
            // 头 3 tick 由细变粗，最后 2 tick 收细
            float fade = Mth.clamp(Math.min((age - TideRemnantHermitCrabEntity.CANNON_SWEEP) / 3, (TideRemnantHermitCrabEntity.CANNON_SWEEP_END + 1 - age) / 2), 0, 1);
            double w = 0.42 * fade * (1 + 0.06 * Math.sin(age * 2.3));
            Vec3 toCamera = entityRenderDispatcher.camera.getPosition().subtract(base.add(origin)).normalize();
            // 贴图 16x32：宽 2w 对应一整块的高 4w，V 方向随时间滚动成流动的水柱
            float v0 = -age * 0.35F;
            float v1 = v0 + (float) (length / (4 * Math.max(w, 0.05)));
            ribbon(poseStack.last(), buffer(bufferSource, BEAM), origin, dir, toCamera, length, w, v0, v1, 0.95F);
            // 竖着再画一片，侧面看水柱不会变成一条线
            Vec3 up = dir.cross(new Vec3(0, 1, 0)).normalize();
            ribbon(poseStack.last(), buffer(bufferSource, BEAM), origin, dir, up, length, w * 0.8, v0 + 0.37F, v1 + 0.37F, 0.6F);
            // 吻端的水花
            Vec3 side = dir.cross(toCamera).normalize().scale(0.55 * fade);
            Vec3 upCam = side.cross(toCamera).normalize().scale(0.55 * fade);
            Vec3 c = origin.add(dir.scale(0.1));
            quad(poseStack.last(), buffer(bufferSource, BOLT), c.add(side).add(upCam), c.subtract(side).add(upCam),
                c.subtract(side).subtract(upCam), c.add(side).subtract(upCam), 0, 0, 1, 1, 0.9F);
        }
    }

    // ---------------- 水箭 ----------------

    private static final class BoltRenderer extends EntityRenderer<TideWaterBoltEntity> {
        BoltRenderer(EntityRendererProvider.Context context) {
            super(context);
            shadowRadius = 0;
        }

        @Override public void render(TideWaterBoltEntity bolt, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
            Vec3 v = bolt.getDeltaMovement();
            if (v.lengthSqr() > 1.0E-6) {
                Vec3 axis = v.normalize();
                Vec3 toCamera = entityRenderDispatcher.camera.getPosition().subtract(bolt.getPosition(partialTick)).normalize();
                // 16x16 水滴：圆头在上（v=0）朝前，尾巴拖在后面。长 0.6、宽 0.6 保持方形不拉伸
                Vec3 head = axis.scale(0.25).add(0, bolt.getBbHeight() * 0.5, 0);
                ribbon(poseStack.last(), buffer(buffers, BOLT), head, axis.scale(-1), toCamera, 0.6, 0.3, 0, 1, 1);
            }
            super.render(bolt, yaw, partialTick, poseStack, buffers, light);
        }

        @Override public ResourceLocation getTextureLocation(TideWaterBoltEntity entity) { return BOLT; }
    }

    // ---------------- 间歇泉 / 潮汐水墙 ----------------

    private static final class VfxRenderer extends EntityRenderer<TideVfxEntity> {
        private static final int SEGMENTS = 40;

        VfxRenderer(EntityRendererProvider.Context context) {
            super(context);
            shadowRadius = 0;
        }

        @Override public void render(TideVfxEntity vfx, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
            float age = vfx.tickCount + partialTick;
            byte kind = vfx.getKind();
            if (kind == TideVfxEntity.GEYSER) renderGeyser(vfx, age, partialTick, poseStack, buffers);
            else if (kind == TideVfxEntity.WAVE) renderWave(age, poseStack, buffers);
            else renderSplash(vfx, age, partialTick, poseStack, buffers);
            super.render(vfx, yaw, partialTick, poseStack, buffers, light);
        }

        // 钳子落点：一圈矮浪 6 tick 内从 0.4 扩到 2 格（再乘 scale）并压低淡出；中心一簇水柱 2 tick 冲到 1.4 格高再回落
        private void renderSplash(TideVfxEntity vfx, float age, float partialTick, PoseStack poseStack, MultiBufferSource buffers) {
            float s = vfx.getScale();
            float life = TideVfxEntity.SPLASH_LIFE;
            float grow = 1 - (float) Math.pow(1 - Math.min(1, age / 6), 2);
            float alpha = Mth.clamp((life - age) / 5, 0, 1);
            if (alpha <= 0) return;
            ring(poseStack, buffer(buffers, WAVE), (0.4F + 1.6F * grow) * s, 0.75F * s * (1 - 0.5F * grow), alpha);
            // 内圈更小更快的第二道浪，让落点有层次
            float g2 = Math.min(1, age / 4);
            if (age < 8) ring(poseStack, buffer(buffers, WAVE), (0.25F + 0.9F * g2) * s, 0.5F * s, Mth.clamp((8 - age) / 4, 0, 1));
            float up = Math.min(1, age / 2) * Mth.clamp((10 - age) / 6, 0, 1);
            if (up <= 0) return;
            column(poseStack, buffers, horizontalToCamera(vfx, partialTick), age, 1.1 * s * up, 0.22 * s * (1 + 0.3 * (1 - up)), 1);
        }

        private static void ring(PoseStack poseStack, VertexConsumer vc, float r, float height, float alpha) {
            int tiles = Math.max(1, Math.round((float) (Math.PI * 2 * r / (height * 2))));
            for (int i = 0; i < SEGMENTS; i++) {
                double a0 = i * Math.PI * 2 / SEGMENTS, a1 = (i + 1) * Math.PI * 2 / SEGMENTS;
                Vec3 p0 = new Vec3(Math.cos(a0) * r, 0, Math.sin(a0) * r), p1 = new Vec3(Math.cos(a1) * r, 0, Math.sin(a1) * r);
                float u0 = (float) i / SEGMENTS * tiles, u1 = (float) (i + 1) / SEGMENTS * tiles;
                quad(poseStack.last(), vc, p0.add(0, height, 0), p1.add(0, height, 0), p1, p0, u0, 0, u1, 1, alpha);
            }
        }

        // 间歇泉满高 3 格：喷出时 3 tick 冲到顶，最后 6 tick 回落。
        // 立体结构：底座是向外张开的水花裙边，柱身是 12 面圆柱（水流贴图向上滚动），柱顶是向外翻开的水花冠
        private void renderGeyser(TideVfxEntity vfx, float age, float partialTick, PoseStack poseStack, MultiBufferSource buffers) {
            float e = age - TideVfxEntity.GEYSER_ERUPT;
            if (e < 0) return;
            float end = TideVfxEntity.GEYSER_LIFE - TideVfxEntity.GEYSER_ERUPT;
            float k = Math.min(1, e / 3) * Mth.clamp((end - e) / 6, 0, 1);
            if (k <= 0) return;
            Vec3 toCamera = horizontalToCamera(vfx, partialTick);
            float wobble = 1 + 0.06F * Mth.sin(age * 1.7F);
            column(poseStack, buffers, toCamera, age, TideVfxEntity.GEYSER_HEIGHT * k, 0.42 * wobble, 1);
        }

        /** 一根完整的立体水柱：底座裙边 + 圆柱柱身 + 柱顶水花冠，h 是柱身顶端高度，r 是柱身半径。 */
        private static void column(PoseStack poseStack, MultiBufferSource buffers, Vec3 toCamera, float age, double h, double r, float alpha) {
            if (h <= 0.02) return;
            PoseStack.Pose pose = poseStack.last();
            VertexConsumer splash = buffer(buffers, GEYSER);
            // 底座：间歇泉贴图第 24-32 行，绕一圈平铺 3 次，从柱身半径往下张开到 1.9 倍
            double baseH = Math.min(0.5, h) * 1.0;
            tube(pose, splash, toCamera, 0, baseH, r * 1.9, r * 1.05, 24 / 32F, 1, 3, false, alpha);
            // 柱身：光柱水流贴图，白芯对着镜头，V 随时间减小，纹理向上流
            // 贴图 16x32，柱子在屏幕上宽 2r，所以一块贴图高 4r
            float v0 = -age * 0.3F;
            float v1 = v0 + (float) (h / (4 * r));
            tube(pose, buffer(buffers, BEAM), toCamera, 0, h, r, r, v0, v1, 1, true, alpha * 0.9F);
            // 柱顶水帽：水冲到顶后向外翻卷成圆顶，再沿外沿往下垂落并淡出，像喷泉顶端（剖面以 r 为单位）
            double g = Mth.clamp(h / (3 * r), 0.3, 1);
            double puff = 1 + 0.05 * Mth.sin(age * 2.3F);
            double[] py = {0.75, 0.68, 0.5, 0.25, -0.1, -0.55, -1.1};
            double[] pr = {0.0, 0.6, 1.1, 1.45, 1.7, 1.8, 1.75};
            float[] pa = {1, 1, 1, 0.95F, 0.85F, 0.5F, 0};
            double[] ys = new double[py.length], rs = new double[py.length];
            for (int i = 0; i < py.length; i++) {
                ys[i] = h + py[i] * r * g;
                rs[i] = Math.max(pr[i] * r * puff, i == 0 ? 0 : r * 0.9);
            }
            float[] as = new float[pa.length];
            for (int i = 0; i < pa.length; i++) as[i] = pa[i] * alpha * 0.9F;
            profile(pose, buffer(buffers, BEAM), toCamera, ys, rs, as, v1, r);
        }

        /** 按剖面（每圈的高度、半径、透明度）绕 Y 轴旋转成回转面，U 对着镜头取，V 沿剖面弧长接着柱身继续流动。 */
        private static void profile(PoseStack.Pose pose, VertexConsumer vc, Vec3 toCamera, double[] ys, double[] rs, float[] as, float vStart, double r) {
            int n = 12;
            double sx = -toCamera.z, sz = toCamera.x;
            float[] vs = new float[ys.length];
            vs[0] = vStart;
            for (int j = 1; j < ys.length; j++)
                vs[j] = vs[j - 1] + (float) (Math.hypot(ys[j] - ys[j - 1], rs[j] - rs[j - 1]) / (4 * r));
            for (int j = 0; j + 1 < ys.length; j++) {
                for (int i = 0; i < n; i++) {
                    double a0 = i * Math.PI * 2 / n, a1 = (i + 1) * Math.PI * 2 / n;
                    double c0 = Math.cos(a0), s0 = Math.sin(a0), c1 = Math.cos(a1), s1 = Math.sin(a1);
                    float u0 = (float) (0.5 + 0.5 * (c0 * sx + s0 * sz)), u1 = (float) (0.5 + 0.5 * (c1 * sx + s1 * sz));
                    Vec3 p00 = new Vec3(c0 * rs[j], ys[j], s0 * rs[j]), p10 = new Vec3(c1 * rs[j], ys[j], s1 * rs[j]);
                    Vec3 p01 = new Vec3(c0 * rs[j + 1], ys[j + 1], s0 * rs[j + 1]), p11 = new Vec3(c1 * rs[j + 1], ys[j + 1], s1 * rs[j + 1]);
                    vertex(pose, vc, p00, u0, vs[j], as[j]);
                    vertex(pose, vc, p10, u1, vs[j], as[j]);
                    vertex(pose, vc, p11, u1, vs[j + 1], as[j + 1]);
                    vertex(pose, vc, p01, u0, vs[j + 1], as[j + 1]);
                    vertex(pose, vc, p01, u0, vs[j + 1], as[j + 1]);
                    vertex(pose, vc, p11, u1, vs[j + 1], as[j + 1]);
                    vertex(pose, vc, p10, u1, vs[j], as[j]);
                    vertex(pose, vc, p00, u0, vs[j], as[j]);
                }
            }
        }

        /**
         * 绕 Y 轴的锥台侧面，y0 处半径 r0、y1 处半径 r1，贴图 V 从 v0（y0 端）到 v1（y1 端）。
         * faceCamera=true 时 U 按圆周点在屏幕上的横向位置取（中间对着镜头是 0.5，左右两侧是 0 和 1），
         * 让光柱贴图的白芯始终在柱子正中；否则 U 绕一圈平铺 uRepeat 次。
         */
        private static void tube(PoseStack.Pose pose, VertexConsumer vc, Vec3 toCamera, double y0, double y1, double r0, double r1,
                                 float v0, float v1, float uRepeat, boolean faceCamera, float alpha) {
            int n = 12;
            double sx = -toCamera.z, sz = toCamera.x;
            for (int i = 0; i < n; i++) {
                double a0 = i * Math.PI * 2 / n, a1 = (i + 1) * Math.PI * 2 / n;
                double c0 = Math.cos(a0), s0 = Math.sin(a0), c1 = Math.cos(a1), s1 = Math.sin(a1);
                float u0, u1;
                if (faceCamera) {
                    u0 = (float) (0.5 + 0.5 * (c0 * sx + s0 * sz));
                    u1 = (float) (0.5 + 0.5 * (c1 * sx + s1 * sz));
                } else {
                    u0 = (float) i / n * uRepeat;
                    u1 = (float) (i + 1) / n * uRepeat;
                }
                Vec3 b0 = new Vec3(c0 * r0, y0, s0 * r0), b1 = new Vec3(c1 * r0, y0, s1 * r0);
                Vec3 t0 = new Vec3(c0 * r1, y1, s0 * r1), t1 = new Vec3(c1 * r1, y1, s1 * r1);
                vertex(pose, vc, b0, u0, v0, alpha);
                vertex(pose, vc, b1, u1, v0, alpha);
                vertex(pose, vc, t1, u1, v1, alpha);
                vertex(pose, vc, t0, u0, v1, alpha);
                vertex(pose, vc, t0, u0, v1, alpha);
                vertex(pose, vc, t1, u1, v1, alpha);
                vertex(pose, vc, b1, u1, v0, alpha);
                vertex(pose, vc, b0, u0, v0, alpha);
            }
        }

        private Vec3 horizontalToCamera(TideVfxEntity vfx, float partialTick) {
            Vec3 toCamera = entityRenderDispatcher.camera.getPosition().subtract(vfx.getPosition(partialTick)).multiply(1, 0, 1);
            return toCamera.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : toCamera.normalize();
        }

        // 水墙 32x16：高 1.2，沿圆周一块贴图宽 2.4 格，浪尖朝上。最后 4 tick 淡出
        private void renderWave(float age, PoseStack poseStack, MultiBufferSource buffers) {
            float r = TideVfxEntity.waveRadius(age);
            float alpha = Mth.clamp((TideVfxEntity.WAVE_LIFE - age) / 4, 0, 1);
            if (alpha <= 0) return;
            float height = TideVfxEntity.WAVE_HEIGHT * (0.7F + 0.3F * Math.min(1, age / 4));
            int tiles = Math.max(1, Math.round((float) (Math.PI * 2 * r / 2.4)));
            VertexConsumer vc = buffer(buffers, WAVE);
            for (int i = 0; i < SEGMENTS; i++) {
                double a0 = i * Math.PI * 2 / SEGMENTS, a1 = (i + 1) * Math.PI * 2 / SEGMENTS;
                Vec3 p0 = new Vec3(Math.cos(a0) * r, 0, Math.sin(a0) * r), p1 = new Vec3(Math.cos(a1) * r, 0, Math.sin(a1) * r);
                float u0 = (float) i / SEGMENTS * tiles, u1 = (float) (i + 1) / SEGMENTS * tiles;
                quad(poseStack.last(), vc, p0.add(0, height, 0), p1.add(0, height, 0), p1, p0, u0, 0, u1, 1, alpha);
            }
        }

        @Override public ResourceLocation getTextureLocation(TideVfxEntity entity) { return WAVE; }
    }

    // ---------------- 震屏 ----------------

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ticks++;
        shakeO = shake;
        shake *= 0.75F;
        Player player = mc.player;
        if (player == null || mc.level == null || mc.isPaused()) return;
        for (TideRemnantHermitCrabEntity crab : mc.level.getEntitiesOfClass(TideRemnantHermitCrabEntity.class, player.getBoundingBox().inflate(24))) {
            if (!crab.isAlive()) continue;
            float near = (float) Mth.clamp(1 - crab.distanceTo(player) / 16, 0, 1);
            if (near <= 0) continue;
            int age = (int) crab.getSkillAge(0);
            byte skill = crab.getSkill();
            float k = 0;
            if (skill == TideRemnantHermitCrabEntity.CLAW && age >= TideRemnantHermitCrabEntity.CLAW_HIT && age < TideRemnantHermitCrabEntity.CLAW_HIT + 3) k = 0.45F;
            if (skill == TideRemnantHermitCrabEntity.GRAB && age >= TideRemnantHermitCrabEntity.GRAB_SLAM && age < TideRemnantHermitCrabEntity.GRAB_SLAM + 3) k = 0.55F;
            if (skill == TideRemnantHermitCrabEntity.CANNON) {
                if (age >= TideRemnantHermitCrabEntity.CANNON_SWEEP && age <= TideRemnantHermitCrabEntity.CANNON_SWEEP_END) k = 0.22F;
                if (age >= TideRemnantHermitCrabEntity.CANNON_BURST && age < TideRemnantHermitCrabEntity.CANNON_BURST + 4) k = 0.8F;
            }
            shake = Math.max(shake, k * near);
        }
    }

    // 跟随“画面扭曲效果”设置
    @SubscribeEvent public static void cameraShake(ViewportEvent.ComputeCameraAngles e) {
        float s0 = Mth.lerp((float) e.getPartialTick(), shakeO, shake);
        if (s0 < 0.01F) return;
        float scale = Minecraft.getInstance().options.screenEffectScale().get().floatValue();
        if (scale <= 0) return;
        float t = ticks + (float) e.getPartialTick();
        float s = s0 * s0 * 2.2F * scale;
        e.setPitch(e.getPitch() + Mth.sin(t * 2.7F) * s);
        e.setYaw(e.getYaw() + Mth.sin(t * 2.1F + 1.3F) * s * 0.7F);
        e.setRoll(e.getRoll() + Mth.sin(t * 3.3F + 0.5F) * s * 0.6F);
    }
}
