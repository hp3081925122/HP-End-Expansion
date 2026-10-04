package org.hp.hp_end_expansion.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import javax.annotation.Nullable;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.hp.hp_end_expansion.Hp_end_expansion;
import org.hp.hp_end_expansion.entity.starwreck.SkyShardEntity;
import org.hp.hp_end_expansion.entity.starwreck.SkyMeteorEntity;
import org.hp.hp_end_expansion.entity.starwreck.SkyrenderEntity;
import org.hp.hp_end_expansion.registry.ModParticles;
import org.hp.hp_end_expansion.registry.StarwreckEntities;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3f;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * 裂天之主的客户端：本体渲染和它身上挂的画面（召唤裂口与兆石、跃袭落点暗影、爪扫后的空中裂痕、
 * 裂天的长缝、第三阶段天上不合拢的长缝、注视的眼光），天幕坠片，天幕碎屑粒子，以及被注视时的屏幕暗角。
 * 判定全在服务端，这里只读同步的状态、动作计数和目标点，公式和服务端共用。
 */
@EventBusSubscriber(modid = Hp_end_expansion.MODID, value = Dist.CLIENT)
public final class SkyrenderClient {
    static final ResourceLocation SHARD = id("textures/effect/sky_shard.png");
    static final ResourceLocation SHARD_GLOW = id("textures/effect/sky_shard_glow.png");
    static final ResourceLocation TEAR = id("textures/effect/sky_tear.png");
    static final ResourceLocation TEAR_GLOW = id("textures/effect/sky_tear_glow.png");
    static final ResourceLocation RIBBON = id("textures/effect/sky_ribbon.png");
    static final ResourceLocation RIBBON_GLOW = id("textures/effect/sky_ribbon_glow.png");
    static final ResourceLocation SHADOW = id("textures/effect/sky_shadow.png");
    static final ResourceLocation SHADOW_GLOW = id("textures/effect/sky_shadow_glow.png");
    static final ResourceLocation GLOW = id("textures/effect/sky_glow.png");
    static final ResourceLocation RAYS = id("textures/effect/sky_gaze_rays.png");
    static final ResourceLocation VIGNETTE = id("textures/effect/gaze_vignette.png");
    static final ResourceLocation GAZE_OVERLAY = id("textures/effect/gaze_overlay.png");
    static final ResourceLocation GAZE_OVERLAY_GLOW = id("textures/effect/gaze_overlay_glow.png");
    static final ResourceLocation STAR_FLARE = id("textures/effect/sky_star_flare.png");
    static final ResourceLocation STAR_CORONA = id("textures/effect/sky_star_corona.png");
    static final ResourceLocation METEOR_VIGNETTE = id("textures/effect/meteor_vignette.png");
    static final ResourceLocation AIR_SHEATH = id("textures/effect/sky_air_sheath.png");
    private static final int TEAR_SEGMENTS = 14, REND_SEGMENTS = 18;
    @Nullable private static ItemStack omen;

    private SkyrenderClient() {}

    static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(Hp_end_expansion.MODID, path); }

    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(StarwreckEntities.SKYRENDER.get(), BossRenderer::new);
        event.registerEntityRenderer(StarwreckEntities.SKY_SHARD.get(), ShardRenderer::new);
        event.registerEntityRenderer(StarwreckEntities.SKY_METEOR.get(), SkyMeteorRenderer::new);
    }

    @SubscribeEvent public static void particles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.SKY_MOTE.get(), MoteProvider::new);
    }

    // ---------- 注视：被看见时屏幕四边像天幕一样被撕开，裂口一下下跳亮，释放时白闪 ----------
    // 画在快捷栏和血条下面（和原版南瓜头、细雪是同一层），不挡玩家看血
    @SubscribeEvent public static void guiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, id("skyrender_gaze"), SkyrenderClient::gazeLayer);
    }

    static void gazeLayer(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        // 注视只罩生存玩家
        if (player == null || mc.level == null || player.isCreative() || player.isSpectator()) return;
        SkyrenderEntity boss = null;
        for (SkyrenderEntity e : mc.level.getEntitiesOfClass(SkyrenderEntity.class, player.getBoundingBox().inflate(SkyrenderEntity.GAZE_RANGE + 8))) {
            if (e.isAlive() && e.getState() == SkyrenderEntity.GAZE) boss = e;
        }
        if (boss == null) return;
        int charge = boss.gazeCharge();
        float age = boss.getClientAge() + delta.getGameTimeDeltaPartialTick(false);
        if (age > charge + 6 || !boss.sees(player)) return;
        int w = g.guiWidth(), h = g.guiHeight();
        float k = Mth.clamp(age / charge, 0, 1);
        float fade = age < charge ? 1 : 1 - (age - charge) / 6;
        // 裂框从屏幕外往里收：开头只露出几个楔子尖，蓄满时整圈裂边都在屏幕里
        int inset = (int) (Math.min(w, h) * 0.22F * (1 - k));
        // 裂口的光像心跳一样一下下跳亮，越往后跳得越快；最后 8 拍一直烧亮
        float beat = (float) Math.pow(0.5 + 0.5 * Math.sin(age * (0.16F + 0.15F * k)), 3);
        float glow = Math.max(k * (0.45F + 0.55F * beat), Mth.clamp(1 - (charge - age) / 8, 0, 1));
        int x = -inset, y = -inset, dw = w + inset * 2, dh = h + inset * 2;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.setColor(1, 1, 1, Mth.clamp((0.25F + k) * fade, 0, 1));
        g.blit(GAZE_OVERLAY, x, y, dw, dh, 0, 0, 256, 144, 256, 144);
        // 发光层走加法：黑的地方不加光，只有裂边和裂纹亮
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        g.setColor(1, 1, 1, Mth.clamp(glow * fade, 0, 1));
        g.blit(GAZE_OVERLAY_GLOW, x, y, dw, dh, 0, 0, 256, 144, 256, 144);
        RenderSystem.defaultBlendFunc();
        g.setColor(1, 1, 1, 1);
        RenderSystem.disableBlend();
        if (age >= charge) g.fill(0, 0, w, h, ((int) (Mth.clamp(fade, 0, 1) * 230) << 24) | 0xFFF4E8);
    }

    // ---------- 共用：画四边形 ----------
    static void vertex(PoseStack.Pose pose, VertexConsumer vc, Vec3 p, float u, float v, float r, float g, float b, float a) {
        vc.addVertex(pose, (float) p.x, (float) p.y, (float) p.z).setColor(Mth.clamp(r, 0, 1), Mth.clamp(g, 0, 1), Mth.clamp(b, 0, 1), Mth.clamp(a, 0, 1))
            .setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0, 1, 0);
    }

    /** a(u0,v0) b(u1,v0) c(u1,v1) d(u0,v1)，正反两面都画。 */
    static void quad(PoseStack.Pose pose, VertexConsumer vc, Vec3 a, Vec3 b, Vec3 c, Vec3 d, float u0, float v0, float u1, float v1,
                     float r, float g, float bl, float alpha) {
        vertex(pose, vc, a, u0, v0, r, g, bl, alpha);
        vertex(pose, vc, b, u1, v0, r, g, bl, alpha);
        vertex(pose, vc, c, u1, v1, r, g, bl, alpha);
        vertex(pose, vc, d, u0, v1, r, g, bl, alpha);
        vertex(pose, vc, d, u0, v1, r, g, bl, alpha);
        vertex(pose, vc, c, u1, v1, r, g, bl, alpha);
        vertex(pose, vc, b, u1, v0, r, g, bl, alpha);
        vertex(pose, vc, a, u0, v0, r, g, bl, alpha);
    }

    /** 绕竖轴朝向镜头的立面，at 为底边中点。depth 沿朝向镜头的方向前后错开：光晕放后面，发光裂边放前面。 */
    static void upright(PoseStack ps, VertexConsumer vc, Vec3 camera, Vec3 world, Vec3 at, float halfWidth, float height, float depth,
                        float r, float g, float b, float alpha) {
        if (halfWidth <= 0.001F || height <= 0.001F) return;
        float face = (float) (Mth.atan2(camera.x - world.x, camera.z - world.z) * Mth.RAD_TO_DEG);
        ps.pushPose();
        ps.translate(at.x, at.y, at.z);
        ps.mulPose(Axis.YP.rotationDegrees(face));
        ps.translate(0, 0, depth);
        quad(ps.last(), vc, new Vec3(-halfWidth, height, 0), new Vec3(halfWidth, height, 0), new Vec3(halfWidth, 0, 0), new Vec3(-halfWidth, 0, 0),
            0, 0, 1, 1, r, g, b, alpha);
        ps.popPose();
    }

    /** 天幕破口：后面一层暖光，中间夜空底，前面一层同形状的发光裂边。 */
    static void skyHole(PoseStack ps, MultiBufferSource buffers, Vec3 camera, Vec3 world, Vec3 at, float halfWidth, float height,
                        float haloHalfWidth, float haloHeight, float haloDrop, float halo, float glow) {
        upright(ps, buffers.getBuffer(StarfallDraw.additive(GLOW)), camera, world, at.add(0, -haloDrop, 0), haloHalfWidth, haloHeight, -0.08F,
            halo, halo * 0.8F, halo * 0.55F, 1);
        upright(ps, buffers.getBuffer(RenderType.entityTranslucent(TEAR)), camera, world, at, halfWidth, height, 0, 1, 1, 1, 1);
        upright(ps, buffers.getBuffer(StarfallDraw.additive(TEAR_GLOW)), camera, world, at, halfWidth, height, 0.02F, glow, glow, glow, 1);
    }

    static void sprite(PoseStack ps, MultiBufferSource buffers, Quaternionf camera, ResourceLocation tex, Vec3 at, float size, float spin, float r, float g, float b) {
        if (size <= 0.001F) return;
        ps.pushPose();
        ps.translate(at.x, at.y, at.z);
        ps.mulPose(camera);
        ps.mulPose(Axis.ZP.rotationDegrees(spin));
        quad(ps.last(), buffers.getBuffer(StarfallDraw.additive(tex)), new Vec3(-size, size, 0), new Vec3(size, size, 0), new Vec3(size, -size, 0),
            new Vec3(-size, -size, 0), 0, 0, 1, 1, r, g, b, 1);
        ps.popPose();
    }

    private static float clamp01(float v) { return Mth.clamp(v, 0, 1); }

    // ---------- 本体 ----------
    static final class BossRenderer extends GeoEntityRenderer<SkyrenderEntity> {
        BossRenderer(EntityRendererProvider.Context context) {
            super(context, new BossModel());
            withScale(SkyrenderEntity.MODEL_SCALE);
            shadowRadius = 2.2F;
            addRenderLayer(new AutoGlowingGeoLayer<>(this));
        }

        @Override protected float getDeathMaxRotation(SkyrenderEntity animatable) { return 0; }

        @Override public void preRender(PoseStack poseStack, SkyrenderEntity animatable, BakedGeoModel model, @Nullable MultiBufferSource bufferSource,
                                        @Nullable VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
            // 发光层重画时 GeckoLib 会覆盖本体的变换，先存下来再还原，眼睛的位置才对得上
            Matrix4f base = entityRenderTranslations;
            super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
            if (isReRender) entityRenderTranslations = base;
            else model.getBone("eye").ifPresent(bone -> bone.setTrackingMatrices(true));
        }

        @Override public void renderFinal(PoseStack poseStack, SkyrenderEntity animatable, BakedGeoModel model, MultiBufferSource bufferSource,
                                          @Nullable VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay, int colour) {
            super.renderFinal(poseStack, animatable, model, bufferSource, buffer, partialTick, packedLight, packedOverlay, colour);
            // 眼睛骨骼的轴心就是竖瞳中心，记下来给注视的眼光用
            model.getBone("eye").ifPresent(bone -> {
                Vector3f local = bone.getLocalSpaceMatrix().transformPosition(new Vector3f());
                animatable.eyeAnchor = new Vec3(local.x, local.y, local.z);
            });
        }

        @Override public void render(SkyrenderEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
            byte state = e.getState();
            Vec3 pos = e.getPosition(pt);
            Vec3 camera = entityRenderDispatcher.camera.getPosition();
            float age = e.getClientAge() + pt;
            // 仪式：模型还在天外，只画裂口和兆石
            if (state != SkyrenderEntity.RITUAL) super.render(e, yaw, pt, ps, buffers, light);
            renderRift(e, state, age, pos, camera, ps, buffers);
            if (state == SkyrenderEntity.RITUAL && age < SkyrenderEntity.RITUAL_LEN - 4) renderOmen(e, age, pos, ps, buffers);
            if (e.isDeadOrDying()) return;
            if (state == SkyrenderEntity.LEAP && age < SkyrenderEntity.LEAP_LAND + 1) renderLeapShadow(e, age, pos, ps, buffers);
            if (state == SkyrenderEntity.RAKE && age >= SkyrenderEntity.RAKE_FROM - 1) renderTears(e, age, pt, ps, buffers);
            if (state == SkyrenderEntity.REND && age >= SkyrenderEntity.REND_TEAR) renderRend(e, age, pt, ps, buffers);
            if (e.getPhase() >= 3) renderSkyTear(e, state, age, pos, camera, ps, buffers);
            if (state == SkyrenderEntity.GAZE) renderGaze(e, age, ps, buffers);
        }

        /** 召唤的裂口：仪式第 2 秒起从一道细线撕开，本体从里面坠下，落地后 2 秒合拢。 */
        private void renderRift(SkyrenderEntity e, byte state, float age, Vec3 pos, Vec3 camera, PoseStack ps, MultiBufferSource buffers) {
            float open;
            if (state == SkyrenderEntity.RITUAL) open = clamp01((age - SkyrenderEntity.RITUAL_RIFT) / 120);
            else if (state == SkyrenderEntity.DESCEND) open = 1;
            else if (state == SkyrenderEntity.ROAR && e.getPhase() <= 1) open = clamp01(1 - age / 40);
            else return;
            if (open <= 0) return;
            Vec3 world = e.getAnchor().add(0, SkyrenderEntity.RIFT_HEIGHT - 4, 0);
            Vec3 at = world.subtract(pos);
            float length = clamp01(open * 2.5F), widen = clamp01(open * 1.5F - 0.2F);
            float pulse = 0.85F + 0.15F * Mth.sin(age * 0.5F);
            skyHole(ps, buffers, camera, world, at, 0.2F + 2.4F * widen, 14 * length, 6 * open, 16 * length, 1, 0.7F * open * pulse,
                pulse * (0.75F + 0.25F * widen));
        }

        /** 兆石悬在落点上方慢慢转，越到后面转得越快。 */
        private void renderOmen(SkyrenderEntity e, float age, Vec3 pos, PoseStack ps, MultiBufferSource buffers) {
            if (omen == null) omen = new ItemStack(StarwreckEntities.SKY_EYE_OMEN.get());
            Vec3 at = e.getAnchor().add(0, 2 + 0.15 * Mth.sin(age * 0.1F), 0).subtract(pos);
            float spin = age * (3 + age / 20);
            ps.pushPose();
            ps.translate(at.x, at.y, at.z);
            ps.mulPose(Axis.YP.rotationDegrees(spin));
            ps.scale(1.6F, 1.6F, 1.6F);
            Minecraft.getInstance().getItemRenderer().renderStatic(omen, ItemDisplayContext.GROUND, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                ps, buffers, e.level(), e.getId());
            ps.popPose();
        }

        /** 跃袭落点：地上铺开一片身体大小的夜空暗影，越临近落地越深，最后几拍破口亮起来。 */
        private void renderLeapShadow(SkyrenderEntity e, float age, Vec3 pos, PoseStack ps, MultiBufferSource buffers) {
            float k = clamp01((age - 2) / (SkyrenderEntity.LEAP_LAND - 2));
            if (k <= 0) return;
            Vec3 aim = e.getAim();
            Vec3 c = aim.subtract(pos).add(0, 0.06, 0);
            Vec3 f = e.forward();
            Vec3 s = new Vec3(-f.z, 0, f.x);
            double scale = 0.7 + 0.3 * k;
            Vec3 lf = f.scale(3.8 * scale), ls = s.scale(2.6 * scale);
            quad(ps.last(), buffers.getBuffer(RenderType.entityTranslucent(SHADOW)), c.add(lf).subtract(ls), c.add(lf).add(ls), c.subtract(lf).add(ls),
                c.subtract(lf).subtract(ls), 0, 0, 1, 1, 1, 1, 1, 0.25F + 0.65F * k);
            float glow = clamp01((age - (SkyrenderEntity.LEAP_LAND - 8)) / 8);
            if (glow > 0) quad(ps.last(), buffers.getBuffer(StarfallDraw.additive(SHADOW_GLOW)), c.add(lf).subtract(ls).add(0, 0.02, 0), c.add(lf).add(ls).add(0, 0.02, 0),
                c.subtract(lf).add(ls).add(0, 0.02, 0), c.subtract(lf).subtract(ls).add(0, 0.02, 0), 0, 0, 1, 1, glow, glow * 0.92F, glow * 0.85F, 1);
        }

        /** 爪扫划开的三道空中裂痕：前锋扫到哪儿就裂到哪儿，停一会儿后猛地合拢。中间宽、两头尖，合拢那一下裂边最亮。 */
        private void renderTears(SkyrenderEntity e, float age, float pt, PoseStack ps, MultiBufferSource buffers) {
            if (age > SkyrenderEntity.RAKE_SNAP + 2) return;
            float close = 1;
            if (age > SkyrenderEntity.RAKE_SNAP - 3) close = clamp01((SkyrenderEntity.RAKE_SNAP - age) / 3);
            float glow = 0.8F + 0.2F * (1 - close);
            drawTears(e, age, close, ps.last(), buffers.getBuffer(RenderType.entityTranslucent(RIBBON)), 1);
            drawTears(e, age, close, ps.last(), buffers.getBuffer(StarfallDraw.additive(RIBBON_GLOW)), glow);
        }

        private static void drawTears(SkyrenderEntity e, float age, float close, PoseStack.Pose pose, VertexConsumer vc, float light) {
            int side = e.getVariant();
            double lead = SkyrenderEntity.rakeLead(age, side);
            Vec3 f = e.forward();
            Vec3 left = new Vec3(f.z, 0, -f.x);
            double mid = (SkyrenderEntity.TEAR_LOW + SkyrenderEntity.TEAR_HIGH) / 2, half = (SkyrenderEntity.TEAR_HIGH - SkyrenderEntity.TEAR_LOW) / 2;
            for (double r : SkyrenderEntity.TEAR_RADII) {
                for (int i = 0; i < TEAR_SEGMENTS; i++) {
                    double t0 = i / (double) TEAR_SEGMENTS, t1 = (i + 1) / (double) TEAR_SEGMENTS;
                    double a0 = Mth.lerp(t0, -SkyrenderEntity.TEAR_SPAN, SkyrenderEntity.TEAR_SPAN), a1 = Mth.lerp(t1, -SkyrenderEntity.TEAR_SPAN, SkyrenderEntity.TEAR_SPAN);
                    // 前锋还没扫到的那一段不画
                    if (side > 0 && Math.min(a0, a1) < lead) continue;
                    if (side < 0 && Math.max(a0, a1) > lead) continue;
                    double h0 = half * Math.pow(Math.sin(Math.PI * t0), 0.6) * close, h1 = half * Math.pow(Math.sin(Math.PI * t1), 0.6) * close;
                    Vec3 p0 = dir(f, left, a0).scale(r), p1 = dir(f, left, a1).scale(r);
                    float u0 = (float) (t0 * r * 0.6), u1 = (float) (t1 * r * 0.6);
                    quad(pose, vc, p0.add(0, mid + h0, 0), p1.add(0, mid + h1, 0), p1.add(0, mid - h1, 0), p0.add(0, mid - h0, 0), u0, 0, u1, 1,
                        light, light, light, 1);
                }
            }
        }

        private static Vec3 dir(Vec3 f, Vec3 left, double degrees) {
            double a = Math.toRadians(degrees);
            return f.scale(Math.cos(a)).add(left.scale(Math.sin(a)));
        }

        /** 裂天：头顶 13 格处沿锁定方向撕开一道长缝，碎片从里面一路砸下去；第三阶段左右各一道。 */
        private void renderRend(SkyrenderEntity e, float age, float pt, PoseStack ps, MultiBufferSource buffers) {
            float open = clamp01((age - SkyrenderEntity.REND_TEAR) / 6);
            if (age > SkyrenderEntity.REND_LEN - 10) open *= clamp01((SkyrenderEntity.REND_LEN + 10 - age) / 20);
            if (open <= 0) return;
            Vec3 f = e.forward();
            double length = e.getAim().subtract(e.getPosition(pt)).horizontalDistance();
            Vec3[] dirs = e.getPhase() >= 3 ? new Vec3[]{rotate(f, 18), rotate(f, -18)} : new Vec3[]{f};
            VertexConsumer sky = buffers.getBuffer(RenderType.entityTranslucent(RIBBON));
            for (Vec3 d : dirs) renderRendLine(ps.last(), sky, d, length, open, 1);
            float glow = open * (0.85F + 0.15F * Mth.sin(age * 0.6F));
            VertexConsumer lit = buffers.getBuffer(StarfallDraw.additive(RIBBON_GLOW));
            for (Vec3 d : dirs) renderRendLine(ps.last(), lit, d, length, open, glow);
        }

        private static Vec3 rotate(Vec3 v, double degrees) {
            double a = Math.toRadians(degrees), c = Math.cos(a), s = Math.sin(a);
            return new Vec3(v.x * c - v.z * s, 0, v.x * s + v.z * c);
        }

        private static void renderRendLine(PoseStack.Pose pose, VertexConsumer vc, Vec3 dir, double length, float open, float light) {
            Vec3 side = new Vec3(-dir.z, 0, dir.x);
            Vec3 start = dir.scale(3).add(0, 13, 0);
            for (int i = 0; i < REND_SEGMENTS; i++) {
                double t0 = i / (double) REND_SEGMENTS, t1 = (i + 1) / (double) REND_SEGMENTS;
                double w0 = 1.3 * Math.pow(Math.sin(Math.PI * t0), 0.5) * open, w1 = 1.3 * Math.pow(Math.sin(Math.PI * t1), 0.5) * open;
                Vec3 p0 = start.add(dir.scale((length - 3) * t0)), p1 = start.add(dir.scale((length - 3) * t1));
                // 贴图横向铺一遍对应 5.2 格长，和缝最宽处的 2.6 格配起来像素是方的
                float u0 = (float) (t0 * length / 5.2), u1 = (float) (t1 * length / 5.2);
                quad(pose, vc, p0.add(side.scale(w0)), p1.add(side.scale(w1)), p1.subtract(side.scale(w1)), p0.subtract(side.scale(w0)), u0, 0, u1, 1,
                    light, light, light, 1);
            }
        }

        /** 第三阶段：坑上方一道再也不合拢的竖缝，转换时撕开，之后一直缓慢起伏。 */
        private void renderSkyTear(SkyrenderEntity e, byte state, float age, Vec3 pos, Vec3 camera, PoseStack ps, MultiBufferSource buffers) {
            float open = 1;
            if (state == SkyrenderEntity.PHASE_UP) open = clamp01((age - 20) / 20);
            if (open <= 0) return;
            Vec3 world = e.getAnchor().add(0, SkyrenderEntity.SKY_TEAR_HEIGHT, 0);
            Vec3 at = world.subtract(pos);
            float pulse = 0.85F + 0.15F * Mth.sin((e.tickCount + age) * 0.07F);
            skyHole(ps, buffers, camera, world, at, 3.2F * open * pulse, 20 * open, 9 * open, 26 * open, 3, 0.55F * pulse, pulse);
        }

        /** 注视：蓄力时竖瞳越来越亮，外面几道细长光芒慢慢转着胀开（纯画面）；释放那一下炸开一团白光。 */
        private void renderGaze(SkyrenderEntity e, float age, PoseStack ps, MultiBufferSource buffers) {
            Vec3 eye = e.eyeAnchor;
            if (eye == null) return;
            int charge = e.gazeCharge();
            Quaternionf cam = entityRenderDispatcher.cameraOrientation();
            if (age < charge) {
                float k = age / charge;
                float pulse = 0.85F + 0.15F * Mth.sin(age * 0.9F);
                sprite(ps, buffers, cam, GLOW, eye, 0.4F + 1.2F * k, age * 2, 1, 0.85F + 0.15F * k, 0.55F + 0.4F * k);
                sprite(ps, buffers, cam, RAYS, eye, (1 + 4 * k) * pulse, -age, 0.7F * k, 0.42F * k, 0.2F * k);
                return;
            }
            float flash = 1 - (age - charge) / 6;
            if (flash > 0) sprite(ps, buffers, cam, GLOW, eye, 5 * flash + 1, 0, flash, flash, flash * 0.9F);
        }
    }

    static final class BossModel extends GeoModel<SkyrenderEntity> {
        private final ResourceLocation model = id("geo/skyrender.geo.json"), texture = id("textures/entity/skyrender.png"),
            enraged = id("textures/entity/skyrender_enraged.png"), petrify = id("textures/entity/skyrender_petrify.png"),
            animation = id("animations/skyrender.animation.json");

        @Override public ResourceLocation getModelResource(SkyrenderEntity e) { return model; }
        @Override public ResourceLocation getAnimationResource(SkyrenderEntity e) { return animation; }

        // 熄瞳：死亡 1.5 秒后整张换成石化贴图；第三阶段裂缝烧得发白
        @Override public ResourceLocation getTextureResource(SkyrenderEntity e) {
            if (e.deathTime > 30) return petrify;
            if (e.getPhase() >= 3) return enraged;
            return texture;
        }

        @Override public void setCustomAnimations(SkyrenderEntity e, long instanceId, AnimationState<SkyrenderEntity> state) {
            super.setCustomAnimations(e, instanceId, state);
            // 眼睑左右滑开，开合程度按阶段和招式算
            float open = lidOpen(e, e.getClientAge() + state.getPartialTick());
            GeoBone left = getAnimationProcessor().getBone("lid_left");
            GeoBone right = getAnimationProcessor().getBone("lid_right");
            if (left != null) left.setPosX(2.6F * open);
            if (right != null) right.setPosX(-2.6F * open);
        }

        private static float lidOpen(SkyrenderEntity e, float age) {
            if (e.isDeadOrDying()) return clamp01(0.7F - e.deathTime / 25F);
            byte s = e.getState();
            byte phase = e.getPhase();
            if (s == SkyrenderEntity.RITUAL || s == SkyrenderEntity.DESCEND) return 0;
            // 天陨：撕天到落地竖瞳全开，跪伏时半闭
            if (s == SkyrenderEntity.METEOR) {
                if (age < SkyrenderEntity.METEOR_LEN) return clamp01(age / 12);
                return 0.3F;
            }
            if (s == SkyrenderEntity.ROAR && phase <= 1) return 0.25F * clamp01(age / 12);
            if (s == SkyrenderEntity.PHASE_UP && phase == 2) return clamp01((age - 16) / 20);
            if (phase <= 1) return 0;
            if (s == SkyrenderEntity.GAZE) {
                if (age < e.gazeCharge()) return 0.7F + 0.3F * clamp01(age / 10);
                return 0.15F;
            }
            return 0.7F;
        }
    }

    // ---------- 天幕坠片 ----------
    static final class ShardRenderer extends EntityRenderer<SkyShardEntity> {
        ShardRenderer(EntityRendererProvider.Context context) {
            super(context);
            shadowRadius = 0;
        }

        @Override public ResourceLocation getTextureLocation(SkyShardEntity e) { return SHARD; }

        @Override public void render(SkyShardEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
            float t = e.tickCount + pt;
            int warn = e.warn();
            float r = e.radius();
            // 落点暗影：越临近越深越大，落地后淡掉
            float k = clamp01(t / warn);
            float fade = 1;
            if (t > warn) fade = clamp01(1 - (t - warn) / 6);
            if (fade > 0) {
                float s = r * (0.6F + 0.5F * k);
                quad(ps.last(), buffers.getBuffer(RenderType.entityTranslucent(SHADOW)), new Vec3(-s, 0.05, -s), new Vec3(s, 0.05, -s), new Vec3(s, 0.05, s),
                    new Vec3(-s, 0.05, s), 0, 0, 1, 1, 1, 1, 1, (0.2F + 0.7F * k) * fade);
                // 快砸下来时暗影里的裂纹亮起来
                float crack = clamp01((k - 0.55F) / 0.45F) * fade * 0.8F;
                if (crack > 0) quad(ps.last(), buffers.getBuffer(StarfallDraw.additive(SHADOW_GLOW)), new Vec3(-s, 0.07, -s), new Vec3(s, 0.07, -s),
                    new Vec3(s, 0.07, s), new Vec3(-s, 0.07, s), 0, 0, 1, 1, crack, crack * 0.92F, crack * 0.85F, 1);
            }
            // 最后 DROP 拍碎片从高处竖着插下来
            float drop = 0;
            if (t < warn - SkyShardEntity.DROP) return;
            if (t < warn) drop = (float) Math.pow((warn - t) / SkyShardEntity.DROP, 2) * 18;
            int stand = e.stand();
            float alpha = 1;
            if (t > warn) {
                if (stand <= 0) alpha = clamp01(1 - (t - warn) / SkyShardEntity.SHATTER);
                else alpha = clamp01((warn + stand - t) / 20);
            }
            if (alpha <= 0) return;
            float scale = r / 1.5F;
            ps.pushPose();
            ps.translate(0, drop, 0);
            ps.mulPose(Axis.YP.rotationDegrees(-e.getYRot()));
            if (drop > 0) ps.mulPose(Axis.ZP.rotationDegrees(t * 9));
            PoseStack.Pose pose = ps.last();
            shardQuads(pose, buffers.getBuffer(RenderType.entityTranslucent(SHARD)), scale, 1, alpha);
            // 碎边的光跟着碎片一起淡掉
            shardQuads(pose, buffers.getBuffer(StarfallDraw.additive(SHARD_GLOW)), scale, alpha * (0.8F + 0.2F * Mth.sin(t * 0.4F)), 1);
            ps.popPose();
        }

        /** 十字交叉的两片，下端插进地里 0.2 格。 */
        private static void shardQuads(PoseStack.Pose pose, VertexConsumer vc, float scale, float light, float alpha) {
            for (int i = 0; i < 2; i++) {
                Vec3 a = new Vec3(0, 0, -0.75 * scale);
                if (i == 0) a = new Vec3(-0.75 * scale, 0, 0);
                Vec3 b = a.scale(-1);
                quad(pose, vc, a.add(0, 3.2 * scale, 0), b.add(0, 3.2 * scale, 0), b.add(0, -0.2, 0), a.add(0, -0.2, 0), 0, 0, 1, 1, light, light, light, alpha);
            }
        }
    }

    // ---------- 天幕碎屑粒子：小碎片边飘边翻转 ----------
    static final class Mote extends TextureSheetParticle {
        private final float spin;

        Mote(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
            super(level, x, y, z, dx, dy, dz);
            xd = dx + (random.nextDouble() - 0.5) * 0.04;
            yd = dy + 0.01 + random.nextDouble() * 0.02;
            zd = dz + (random.nextDouble() - 0.5) * 0.04;
            pickSprite(sprites);
            lifetime = 30 + random.nextInt(30);
            quadSize = 0.12F + random.nextFloat() * 0.14F;
            gravity = -0.01F;
            friction = 0.93F;
            hasPhysics = false;
            roll = oRoll = random.nextFloat() * Mth.TWO_PI;
            spin = (random.nextFloat() - 0.5F) * 0.24F;
        }

        @Override public void tick() {
            oRoll = roll;
            super.tick();
            roll += spin;
            alpha = 1 - (float) age / lifetime;
        }

        @Override public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }
        @Override protected int getLightColor(float partialTick) { return LightTexture.FULL_BRIGHT; }
    }

    record MoteProvider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override public Mote createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double dx, double dy, double dz) {
            return new Mote(level, x, y, z, dx, dy, dz, sprites);
        }
    }
}
