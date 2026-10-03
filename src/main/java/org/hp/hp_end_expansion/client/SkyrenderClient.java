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
    static final ResourceLocation ROCK_TEX = id("textures/effect/sky_star_rock.png");
    static final ResourceLocation ROCK_GLOW = id("textures/effect/sky_star_rock_glow.png");
    static final ResourceLocation STAR_FLARE = id("textures/effect/sky_star_flare.png");
    static final ResourceLocation STAR_CORONA = id("textures/effect/sky_star_corona.png");
    static final ResourceLocation IMPACT_SHADOW = id("textures/effect/sky_impact_shadow.png");
    static final ResourceLocation IMPACT_CRACKS = id("textures/effect/sky_impact_cracks.png");
    static final ResourceLocation METEOR_VIGNETTE = id("textures/effect/meteor_vignette.png");
    static final ResourceLocation AIR_SHEATH = id("textures/effect/sky_air_sheath.png");
    private static final int TEAR_SEGMENTS = 14, REND_SEGMENTS = 18;
    @Nullable private static ItemStack omen;

    private SkyrenderClient() {}

    static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(Hp_end_expansion.MODID, path); }

    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(StarwreckEntities.SKYRENDER.get(), BossRenderer::new);
        event.registerEntityRenderer(StarwreckEntities.SKY_SHARD.get(), ShardRenderer::new);
        event.registerEntityRenderer(StarwreckEntities.SKY_METEOR.get(), MeteorRenderer::new);
    }

    @SubscribeEvent public static void particles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.SKY_MOTE.get(), MoteProvider::new);
    }

    // ---------- 天陨：落地前四周暗角往里收，落地一拍白闪 ----------
    @SubscribeEvent public static void gui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;
        GuiGraphics g = event.getGuiGraphics();
        int w = g.guiWidth(), h = g.guiHeight();
        for (SkyrenderEntity e : mc.level.getEntitiesOfClass(SkyrenderEntity.class, player.getBoundingBox().inflate(SkyrenderEntity.GAZE_RANGE + 8))) {
            if (!e.isAlive()) continue;
            // 天陨落地：一拍纯白，之后不到半秒褪掉，把后面的火球、光柱和碎片露出来
            if (e.getState() == SkyrenderEntity.METEOR) {
                float since = e.getClientAge() + event.getPartialTick().getGameTimeDeltaPartialTick(false) - SkyrenderEntity.METEOR_HIT;
                // 最后两秒：四周一圈柔边暗角慢慢压进来，整屏被星光烤得发橙；星压到镜头身上时整屏变成熔岩色
                if (since > -40 && since < 0) {
                    float k = 1 + since / 40;
                    // GuiGraphics.blit 自己不开混合，不开的话半透明的暗角会画成实心
                    RenderSystem.enableBlend();
                    RenderSystem.defaultBlendFunc();
                    g.setColor(1, 1, 1, Mth.clamp(0.2F + 0.8F * k * k, 0, 1));
                    g.blit(METEOR_VIGNETTE, 0, 0, w, h, 0, 0, 256, 256, 256, 256);
                    g.setColor(1, 1, 1, 1);
                    RenderSystem.disableBlend();
                    g.fill(0, 0, w, h, ((int) (k * k * k * 70) << 24) | 0xFF7A2C);
                }
                float engulf = meteorEngulf(mc, event.getPartialTick().getGameTimeDeltaPartialTick(false));
                if (engulf > 0) g.fill(0, 0, w, h, ((int) (engulf * 235) << 24) | 0x3A0E06);
                if (since >= 0 && since < 9) {
                    float a = since < 1 ? 1 : (float) Math.pow(1 - (since - 1) / 8, 2.5);
                    g.fill(0, 0, w, h, ((int) (Mth.clamp(a, 0, 1) * 250) << 24) | (since < 1 ? 0xFFFFFF : 0xFFF2E0));
                }
            }
        }
    }

    /** 镜头被星岩罩住的程度：镜头离星心不到 1.05 个星半径开始，越往里越深。撞击后星就崩了，不再算。 */
    private static float meteorEngulf(Minecraft mc, float pt) {
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        float best = 0;
        for (SkyMeteorEntity m : mc.level.getEntitiesOfClass(SkyMeteorEntity.class, new AABB(cam, cam).inflate(256))) {
            float t = m.tickCount + pt;
            if (t < SkyrenderEntity.METEOR_DROP || t >= SkyrenderEntity.METEOR_HIT + 1) continue;
            double d = m.getPosition(pt).add(m.starPos(t)).distanceTo(cam);
            best = Math.max(best, clamp01((float) (1.05 - d / m.starRadius()) / 0.35F));
        }
        return best;
    }

    /** 天陨的镜头：落地前两秒视野慢慢收窄，星显得越压越近；撞击那拍猛地拉宽再弹回。跟随“视场角效果”设置。 */
    @SubscribeEvent public static void meteorFov(ViewportEvent.ComputeFov event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !event.usedConfiguredFov()) return;
        float scale = mc.options.fovEffectScale().get().floatValue();
        if (scale <= 0) return;
        Vec3 cam = event.getCamera().getPosition();
        float pt = (float) event.getPartialTick();
        double mul = 1;
        for (SkyMeteorEntity m : mc.level.getEntitiesOfClass(SkyMeteorEntity.class, new AABB(cam, cam).inflate(256))) {
            float since = m.tickCount + pt - SkyrenderEntity.METEOR_HIT;
            if (since > -40 && since < 0) {
                float k = 1 + since / 40;
                mul = Math.min(mul, 1 - 0.14 * k * k);
            } else if (since >= 0 && since < 16) {
                float q = 1 - since / 16;
                mul = Math.max(mul, 1 + 0.32 * q * q * Math.min(1, since));
            }
        }
        if (mul != 1) event.setFOV(event.getFOV() * (1 + (mul - 1) * scale));
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

    // ---------- 天陨 ----------
    /**
     * 天裂开在坑外三百格的天上（玩家看向首领时的身后），星先从裂缝后面探出一小块，再整团挤出来，
     * 带着激波罩、气流线和一圈圈马赫环，朝玩家越压越大，斜着越落越快砸进坑心。
     * 星本体是方块拼的实心岩团：落地再往地里压 3 拍，烧白后崩成 20 块飞散的岩块，冲起一根光柱，光尘再飘回天裂，天裂合拢。
     */
    static final class MeteorRenderer extends EntityRenderer<SkyMeteorEntity> {
        private static final int RING_SEGMENTS = 48, DUST = 40;

        MeteorRenderer(EntityRendererProvider.Context context) {
            super(context);
            shadowRadius = 0;
        }

        @Override public ResourceLocation getTextureLocation(SkyMeteorEntity e) { return ROCK_TEX; }

        @Override public void render(SkyMeteorEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
            float t = e.tickCount + pt;
            Quaternionf cam = entityRenderDispatcher.cameraOrientation();
            Vec3 camera = entityRenderDispatcher.camera.getPosition();
            Vec3 world = e.getPosition(pt);
            float arena = e.arenaRadius(), radius = e.starRadius();
            e.lockViewDirection(camera);
            Vec3 sky = e.skyPoint(), eye = camera.subtract(world);
            float since = t - SkyrenderEntity.METEOR_HIT;
            pushNear(ps, eye, nearScale(eye, sky));
            renderTear(e, ps, buffers, cam, t, sky, radius);
            ps.popPose();
            renderShadow(ps, buffers, t, arena);
            renderStar(e, ps, buffers, cam, camera, world, eye, t, radius, sky);
            if (since < 0) return;
            renderBurst(ps, buffers, cam, camera, world, since, radius);
            renderDebris(e, ps, buffers, since, radius, sky, e.getId(), eye);
            renderDust(ps, buffers, cam, eye, since, sky, arena, e.getId());
            renderWaves(ps, buffers, since, arena);
            renderScorch(ps, buffers, since);
        }

        /**
         * 远处天象的等比拉近系数：天裂和刚探出来的星在三百多格外，原样画会被视距雾吃掉（8 区块视距时雾从 115 格起）。
         * 以镜头为中心整体缩小 f 倍：方向不变、尺寸同比缩，屏幕上一模一样，只是落在雾的起点以内。近处 f = 1 不动。
         */
        private static float nearScale(Vec3 eye, Vec3 p) {
            float far = Mth.clamp(RenderSystem.getShaderFogStart() * 0.8F, 40, 2000), near = far * 0.6F;
            double d = p.distanceTo(eye);
            if (d <= near) return 1;
            double span = far - near;
            return (float) ((near + span * (1 - Math.exp(-(d - near) / span))) / d);
        }

        /** 压一层以镜头为中心缩放 f 倍的姿态，用完 popPose。 */
        private static void pushNear(PoseStack ps, Vec3 eye, float f) {
            ps.pushPose();
            if (f >= 1) return;
            ps.translate(eye.x, eye.y, eye.z);
            ps.scale(f, f, f);
            ps.translate(-eye.x, -eye.y, -eye.z);
        }

        /** 0..1 的固定伪随机：同一只实体每帧取到的一样，碎片和光尘不会闪来闪去。 */
        private static float rand(int seed, int i, int salt) {
            int h = seed * 0x9E3779B1 + i * 0x85EBCA6B + salt * 0xC2B2AE35;
            h ^= h >>> 15;
            h *= 0x2C1B3C6D;
            h ^= h >>> 12;
            h *= 0x297A2D39;
            h ^= h >>> 15;
            return (h >>> 8) / (float) (1 << 24);
        }

        private static float spike(float x) { return x < 0 ? 0 : clamp01(1 - x / 6); }

        /**
         * 天裂：两爪各撕一下，第一下撕开一半、第二下全开，撕开那拍裂边一亮；星挤过来时裂口被撑宽，
         * 星心穿过裂口那拍裂边再一亮、炸开两圈压缩空气；落地后等光尘飘回来再合拢。贴图是竖缝，斜过来当横缝用。
         */
        private void renderTear(SkyMeteorEntity e, PoseStack ps, MultiBufferSource buffers, Quaternionf cam, float t, Vec3 sky, float radius) {
            float age = t - SkyrenderEntity.METEOR_TEAR;
            if (age < 0) return;
            float open = (0.55F * clamp01(age / 8) + 0.45F * clamp01((age - 15) / 10)) * clamp01(1 - (t - SkyrenderEntity.METEOR_HIT - 40) / 30);
            if (open <= 0) return;
            float burst = t - e.breakTick();
            float glow = 0.85F + 0.15F * Mth.sin(t * 0.3F) + 0.9F * Math.max(spike(age), spike(age - 15)) + 1.3F * spike(burst);
            Vec3 lead = e.lead();
            float pass = (float) e.starPos(t).subtract(sky).dot(lead) / radius;
            float bulge = radius * 0.55F * clamp01(1 - Math.abs(pass) / 1.3F);
            // 天裂在三百多格开外，尺寸跟着放大，才不会远成一条细线
            sprite(ps, buffers, cam, STAR_CORONA, sky, (90 + 1.2F * bulge) * open, 0, 0.8F * open * glow, 0.5F * open * glow, 0.28F * open * glow);
            float half = 2 + 22 * open + bulge, length = 80 * clamp01(open * 1.5F);
            ps.pushPose();
            ps.translate(sky.x, sky.y, sky.z);
            ps.mulPose(cam);
            ps.mulPose(Axis.ZP.rotationDegrees(74));
            Vec3 a = new Vec3(-half, length, 0), b = new Vec3(half, length, 0), c = new Vec3(half, -length, 0), d = new Vec3(-half, -length, 0);
            quad(ps.last(), buffers.getBuffer(RenderType.entityTranslucent(TEAR)), a, b, c, d, 0, 0, 1, 1, 1, 1, 1, 1);
            ps.translate(0, 0, 0.05);
            quad(ps.last(), buffers.getBuffer(StarfallDraw.additive(TEAR_GLOW)), a, b, c, d, 0, 0, 1, 1, glow, glow, glow, 1);
            ps.popPose();
            VertexConsumer boom = buffers.getBuffer(StarfallDraw.additive(StarfallDraw.RING));
            for (int i = 0; i < 2; i++) {
                float s = burst - i * 3;
                if (s < 0 || s >= 26) continue;
                float p = s / 26, grow = 1 - (1 - p) * (1 - p), br = (float) Math.pow(1 - p, 1.5) * (i == 0 ? 0.85F : 0.5F);
                ring(ps.last(), boom, sky, lead, radius * (1.1F + 7 * grow), radius * (0.5F + 1.2F * p), br, br * 0.8F, br * 0.62F);
            }
        }

        /** 垂直于 axis 的一组正交基。 */
        private static Vec3[] basis(Vec3 axis) {
            Vec3 u = axis.cross(new Vec3(0, 1, 0));
            if (u.lengthSqr() < 1.0E-6) u = axis.cross(new Vec3(1, 0, 0));
            u = u.normalize();
            return new Vec3[]{u, axis.cross(u).normalize()};
        }

        /** 摆在垂直于 axis 的平面上的一圈热浪（只取 heat_ring 正中一列：内缘暗、外缘亮，像往外推的激波前沿，边缘平滑没有火舌）。outer 外半径，width 环宽。 */
        private static void ring(PoseStack.Pose pose, VertexConsumer vc, Vec3 center, Vec3 axis, float outer, float width, float r, float g, float b) {
            Vec3[] uv = basis(axis);
            float inner = Math.max(0, outer - width);
            for (int j = 0; j < RING_SEGMENTS; j++) {
                float a0 = j * Mth.TWO_PI / RING_SEGMENTS, a1 = (j + 1) * Mth.TWO_PI / RING_SEGMENTS;
                Vec3 d0 = uv[0].scale(Mth.cos(a0)).add(uv[1].scale(Mth.sin(a0))), d1 = uv[0].scale(Mth.cos(a1)).add(uv[1].scale(Mth.sin(a1)));
                quad(pose, vc, center.add(d0.scale(inner)), center.add(d1.scale(inner)), center.add(d1.scale(outer)), center.add(d0.scale(outer)),
                    0.5F, 0, 0.5F, 1, r, g, b, 1);
            }
        }

        /** 四个顶点各带颜色的四边形，正反两面都画。 */
        private static void shade(PoseStack.Pose pose, VertexConsumer vc, Vec3[] p, float[][] uv, float[][] c) {
            for (int k = 0; k < 4; k++) vertex(pose, vc, p[k], uv[k][0], uv[k][1], c[k][0], c[k][1], c[k][2], 1);
            for (int k = 3; k >= 0; k--) vertex(pose, vc, p[k], uv[k][0], uv[k][1], c[k][0], c[k][1], c[k][2], 1);
        }

        private static void flat(PoseStack ps, VertexConsumer vc, float s, float y, float u0, float v0, float u1, float v1, float r, float g, float b, float a) {
            quad(ps.last(), vc, new Vec3(-s, y, -s), new Vec3(s, y, -s), new Vec3(s, y, s), new Vec3(-s, y, s), u0, v0, u1, v1, r, g, b, a);
        }

        /**
         * 铺满整个坑的落点暗影：星探出来就开始铺，越临近越深。放射裂纹一直隐约可见，
         * 最后两秒从坑心往外烧亮（只露出贴图中间那一块，露出的范围往外扩）。
         */
        private void renderShadow(PoseStack ps, MultiBufferSource buffers, float t, float arena) {
            float grow = clamp01((t - SkyrenderEntity.METEOR_DROP + 20) / 40);
            float fade = clamp01(1 - (t - SkyrenderEntity.METEOR_HIT) / 30);
            float k = SkyMeteorEntity.drop(t);
            float alpha = grow * fade * (0.35F + 0.6F * k);
            if (alpha <= 0) return;
            float s = arena * (0.85F + 0.15F * k);
            flat(ps, buffers.getBuffer(RenderType.entityTranslucent(IMPACT_SHADOW)), s, 0.06F, 0, 0, 1, 1, 1, 1, 1, alpha);
            VertexConsumer cracks = buffers.getBuffer(StarfallDraw.additive(IMPACT_CRACKS));
            float dim = 0.22F * grow * fade;
            flat(ps, cracks, s, 0.08F, 0, 0, 1, 1, dim, dim * 0.9F, dim * 0.85F, 1);
            float burn = clamp01((t - (SkyrenderEntity.METEOR_HIT - 40)) / 40);
            if (burn > 0) {
                float f = 0.12F + 0.88F * burn, lo = 0.5F - 0.5F * f, hi = 0.5F + 0.5F * f;
                float b = fade * (0.6F + 0.4F * burn);
                flat(ps, cracks, s * f, 0.09F, lo, lo, hi, hi, b, b * 0.92F, b * 0.85F, 1);
            }
        }

        // ---------- 星岩 ----------
        /**
         * 星体：一块芯加 20 块外壳，单位半径 1（乘星半径）。外壳方向取正二十面体 12 个顶点和立方体 8 个角。
         * 每块：中心 xyz、三轴尺寸、依次绕 y / x / z 转的角度（度）。改形状就改这张表。
         */
        private static final float[][] ROCK = {
            {0F, 0F, 0F, 1.08F, 1F, 1.04F, 20F, 35F, 10F},
            {0F, 0.263F, 0.425F, 0.82F, 0.7F, 0.74F, 12F, 31F, 8F},
            {0F, -0.242F, 0.391F, 0.7F, 0.78F, 0.66F, 37F, 9F, 22F},
            {0F, 0.273F, -0.442F, 0.76F, 0.66F, 0.8F, 58F, 44F, 15F},
            {0F, -0.231F, -0.374F, 0.88F, 0.72F, 0.7F, 21F, 63F, 40F},
            {0.252F, 0.408F, 0F, 0.68F, 0.84F, 0.72F, 74F, 18F, 33F},
            {-0.279F, 0.451F, 0F, 0.8F, 0.74F, 0.62F, 8F, 52F, 61F},
            {0.237F, -0.383F, 0F, 0.74F, 0.7F, 0.86F, 47F, 27F, 5F},
            {-0.263F, -0.425F, 0F, 0.86F, 0.66F, 0.72F, 66F, 71F, 28F},
            {0.4F, 0F, 0.247F, 0.72F, 0.8F, 0.68F, 29F, 14F, 49F},
            {-0.434F, 0F, 0.268F, 0.66F, 0.72F, 0.84F, 83F, 36F, 12F},
            {0.366F, 0F, -0.226F, 0.84F, 0.76F, 0.7F, 15F, 58F, 73F},
            {-0.417F, 0F, -0.258F, 0.7F, 0.68F, 0.78F, 52F, 5F, 38F},
            {0.266F, 0.266F, 0.266F, 0.62F, 0.66F, 0.6F, 34F, 47F, 19F},
            {0.289F, 0.289F, -0.289F, 0.66F, 0.58F, 0.64F, 71F, 23F, 56F},
            {0.254F, -0.254F, 0.254F, 0.58F, 0.64F, 0.66F, 18F, 66F, 31F},
            {0.3F, -0.3F, -0.3F, 0.64F, 0.6F, 0.58F, 59F, 12F, 77F},
            {-0.271F, 0.271F, 0.271F, 0.6F, 0.66F, 0.62F, 42F, 39F, 9F},
            {-0.26F, 0.26F, -0.26F, 0.66F, 0.62F, 0.56F, 7F, 81F, 44F},
            {-0.294F, -0.294F, 0.294F, 0.58F, 0.6F, 0.66F, 63F, 28F, 63F},
            {-0.277F, -0.277F, -0.277F, 0.62F, 0.56F, 0.6F, 26F, 55F, 24F},
        };
        private static final Quaternionf[] ROCK_ROT = new Quaternionf[ROCK.length];
        private static final Quaternionf ROCK_BASE = new Quaternionf().rotateY(0.7F).rotateX(0.45F);
        /** 单位立方体（半边长 1）的 6 个面，每面四角，和面法线。 */
        private static final float[][][] CUBE = {
            {{-1, 1, -1}, {1, 1, -1}, {1, 1, 1}, {-1, 1, 1}},
            {{-1, -1, 1}, {1, -1, 1}, {1, -1, -1}, {-1, -1, -1}},
            {{-1, 1, 1}, {1, 1, 1}, {1, -1, 1}, {-1, -1, 1}},
            {{1, 1, -1}, {-1, 1, -1}, {-1, -1, -1}, {1, -1, -1}},
            {{1, 1, 1}, {1, 1, -1}, {1, -1, -1}, {1, -1, 1}},
            {{-1, 1, -1}, {-1, 1, 1}, {-1, -1, 1}, {-1, -1, -1}},
        };
        private static final float[][] CUBE_N = {{0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}, {1, 0, 0}, {-1, 0, 0}};
        /** 撞击后继续往地里压的拍数，压完才崩开。 */
        private static final float SINK = 3;

        static {
            for (int i = 0; i < ROCK.length; i++) {
                float[] l = ROCK[i];
                ROCK_ROT[i] = new Quaternionf().rotateY(l[6] * Mth.DEG_TO_RAD).rotateX(l[7] * Mth.DEG_TO_RAD).rotateZ(l[8] * Mth.DEG_TO_RAD);
            }
        }

        /** 一块岩在这一帧的位置、朝向、半边长，和它的亮度参数。 */
        private record Lump(Vec3 center, Quaternionf rot, float hx, float hy, float hz, int tile, Vec3 rock, Glow glow) {}

        /**
         * 亮度参数。lead 是星的前进方向；rock 是星心，用来算顶点在星上朝哪边。
         * 底层：base + leadGain × 面朝前进方向的程度；裂纹：crack × (0.3 + 0.7 × 顶点朝前的程度)；
         * 发热层：heatLead × 朝前的程度^1.6 + heatAll。white 是撞击时整体烧白。
         */
        private record Glow(Vec3 lead, float base, float leadGain, float crack, float heatLead, float heatAll, float white) {}

        /** 星的翻滚：绕“前进方向 × 竖直”慢慢转，从天裂到落地约转 60 度，看得出是个实心的团块在滚。 */
        private static Quaternionf starRotation(float t, Vec3 lead) {
            Vec3 axis = lead.cross(new Vec3(0, 1, 0));
            if (axis.lengthSqr() < 1.0E-4) axis = new Vec3(1, 0, 0);
            axis = axis.normalize();
            return new Quaternionf().rotationAxis(t * 0.011F, (float) axis.x, (float) axis.y, (float) axis.z).mul(ROCK_BASE);
        }

        private static Vec3 rotate(Quaternionf q, float x, float y, float z) {
            Vector3f v = q.transform(new Vector3f(x, y, z));
            return new Vec3(v.x, v.y, v.z);
        }

        /** 依次画底层、裂纹、发热三层；每层取一次缓冲画完全部岩块，不交错着取（交错取会把前一层的缓冲提前结束）。 */
        private static void drawLumps(PoseStack.Pose pose, MultiBufferSource buffers, Lump[] lumps) {
            VertexConsumer body = buffers.getBuffer(StarfallDraw.solid(ROCK_TEX));
            for (Lump l : lumps) if (l != null) drawLump(pose, body, l, 0);
            VertexConsumer cracks = buffers.getBuffer(StarfallDraw.additive(ROCK_GLOW));
            for (Lump l : lumps) if (l != null) drawLump(pose, cracks, l, 1);
            VertexConsumer heat = buffers.getBuffer(StarfallDraw.additive(STAR_CORONA));
            for (Lump l : lumps) if (l != null) drawLump(pose, heat, l, 2);
        }

        private static void drawLump(PoseStack.Pose pose, VertexConsumer vc, Lump l, int layer) {
            Glow g = l.glow;
            // 发光两层略微放大，免得和底层抢同一个深度
            float inflate = layer == 0 ? 1 : 1.012F;
            float u0 = (l.tile & 1) * 0.5F, v0 = (l.tile >> 1) * 0.5F, u1 = u0 + 0.5F, v1 = v0 + 0.5F;
            float[][] uv = {{u0, v0}, {u1, v0}, {u1, v1}, {u0, v1}};
            Vec3[] p = new Vec3[4];
            float[][] c = new float[4][3];
            for (int f = 0; f < CUBE.length; f++) {
                Vec3 n = rotate(l.rot, CUBE_N[f][0], CUBE_N[f][1], CUBE_N[f][2]);
                float facing = (float) Math.max(0, n.dot(g.lead));
                for (int k = 0; k < 4; k++) {
                    float[] q = CUBE[f][k];
                    p[k] = l.center.add(rotate(l.rot, q[0] * l.hx * inflate, q[1] * l.hy * inflate, q[2] * l.hz * inflate));
                    Vec3 out = p[k].subtract(l.rock);
                    float front = out.lengthSqr() < 1.0E-6 ? 0 : (float) out.normalize().dot(g.lead);
                    if (layer == 0) {
                        // 背光面压成近黑的夜空，朝下砸的那面被烧亮；顶面略受天裂的光
                        float s = g.base + g.leadGain * facing + 0.1F * (float) Math.max(0, n.y) + g.white;
                        c[k][0] = c[k][1] = c[k][2] = s;
                    } else if (layer == 1) {
                        float b = g.crack * (0.3F + 0.7F * (0.5F + 0.5F * front)) + g.white;
                        c[k][0] = b;
                        c[k][1] = b * 0.95F;
                        c[k][2] = b * 0.9F;
                    } else {
                        float h = g.heatLead * (float) Math.pow(clamp01((front - 0.05F) / 0.95F), 2) + g.heatAll;
                        c[k][0] = h + g.white;
                        c[k][1] = 0.5F * h + g.white;
                        c[k][2] = 0.18F * h + 0.85F * g.white;
                    }
                }
                // 发热层整面取日冕贴图正中那一小块，相当于一张均匀的暖白
                for (int k = 0; k < 4; k++) {
                    float u = layer == 2 ? 0.5F : uv[k][0], v = layer == 2 ? 0.5F : uv[k][1];
                    vertex(pose, vc, p[k], u, v, c[k][0], c[k][1], c[k][2], 1);
                }
                for (int k = 3; k >= 0; k--) {
                    float u = layer == 2 ? 0.5F : uv[k][0], v = layer == 2 ? 0.5F : uv[k][1];
                    vertex(pose, vc, p[k], u, v, c[k][0], c[k][1], c[k][2], 1);
                }
            }
        }

        /**
         * 巨星：21 块方块拼的实心岩团，朝下砸的半边烧亮、背面压成夜空色，熔裂纹单独发光。
         * 本体先画、写深度，背后的日冕和光芒后画，被本体挡住的部分自然消失，只在轮廓外露出一圈火边。
         * 远处光芒最强，越近越淡、岩体越热。撞击后再往地里压 3 拍，压完由 renderDebris 接手崩开。
         */
        private void renderStar(SkyMeteorEntity e, PoseStack ps, MultiBufferSource buffers, Quaternionf cam, Vec3 camera, Vec3 world, Vec3 eye, float t,
                                float radius, Vec3 sky) {
            if (t < SkyrenderEntity.METEOR_DROP) return;
            float since = t - SkyrenderEntity.METEOR_HIT;
            if (since >= SINK) return;
            float k = SkyMeteorEntity.drop(t), white = clamp01(since / SINK), r = radius;
            Vec3 at = e.starPos(t);
            if (since > 0) at = at.add(0, -0.6F * radius * white, 0);
            Vec3 lead = e.lead();
            // 星心在天裂平面前面多少个星半径：-1.2 整团还藏在裂缝后面，+1.2 整团挤出来
            float pass = (float) at.subtract(sky).dot(lead) / r, out = clamp01((pass + 1.2F) / 2.4F), squeeze = 1 - out;
            Quaternionf q = starRotation(t, lead);
            float hot = 0.25F + 0.75F * k;
            // 烧亮的是朝地的下半边，不是朝前进方向：星大致朝玩家飞来，按前进方向烧会把玩家看到的一整面都烧成橙色
            Vec3 heatDir = lead.scale(0.5).add(0, -1, 0).normalize();
            // 刚从天裂里挤出来时裂纹偏亮
            Glow glow = new Glow(heatDir, 0.18F, 0.42F * hot, 0.45F + 0.55F * k + 0.6F * squeeze, 0.42F * hot, 0.4F * squeeze, white);
            Lump[] lumps = new Lump[ROCK.length];
            for (int i = 0; i < ROCK.length; i++) {
                float[] l = ROCK[i];
                Vec3 c = at.add(rotate(q, l[0], l[1], l[2]).scale(r));
                // 还在裂缝后面的岩块不画，正穿过裂缝的从零长出来：远处先探出一小块，再整团挤出来
                float grow = clamp01(((float) c.subtract(sky).dot(lead) / r + 0.3F) / 0.55F);
                if (grow <= 0.02F) continue;
                grow = grow * grow * (3 - 2 * grow);
                // 星压到镜头身上时，罩住镜头的那几块不画（屏幕由 gui 里的熔岩色接管），免得看到方块内壁
                float reach = Math.max(l[3], Math.max(l[4], l[5])) * 0.87F * r;
                if (c.distanceToSqr(eye) < reach * reach) continue;
                float s = 0.5F * r * grow;
                lumps[i] = new Lump(c, new Quaternionf(q).mul(ROCK_ROT[i]), l[3] * s, l[4] * s, l[5] * s, i & 3, at, glow);
            }
            pushNear(ps, eye, nearScale(eye, at));
            drawLumps(ps.last(), buffers, lumps);
            Vec3 view = world.add(at).subtract(camera);
            if (view.lengthSqr() < 1.0E-4) view = new Vec3(0, -1, 0);
            view = view.normalize();
            float live = 1 - white, pulse = 0.88F + 0.12F * Mth.sin(t * 0.45F), show = 0.35F + 0.65F * out;
            // 日冕放在星心稍后：轮廓里的部分被岩体挡住，只剩贴着轮廓的一圈火边，外面再一层淡的大光晕。还藏在裂缝里时只透出一团光
            Vec3 behind = at.add(view.scale(r * 0.35F));
            float rim = ((0.9F + 0.6F * k) * pulse * live) * show + 1.5F * white;
            sprite(ps, buffers, cam, STAR_CORONA, behind, r * 1.9F, 0, rim, 0.6F * rim, 0.28F * rim);
            float halo = (0.3F + 0.15F * k) * live * show;
            sprite(ps, buffers, cam, STAR_CORONA, behind, r * 3.6F, 0, halo, 0.5F * halo, 0.24F * halo);
            // 光芒：远处像裂缝里亮起一颗星，越近越淡
            float rays = (0.95F - 0.65F * k) * live * (0.6F + 0.4F * out);
            sprite(ps, buffers, cam, STAR_FLARE, behind, r * 3.4F, t * 0.6F, rays, 0.82F * rays, 0.6F * rays);
            // 迎面那侧的热激波：贴着朝下砸的那面亮起，越快越亮
            float bow = (0.15F + 0.85F * k * k) * live * out;
            sprite(ps, buffers, cam, STAR_CORONA, at.add(lead.scale(r * 0.8F)), r * 1.25F, 0, bow, 0.62F * bow, 0.28F * bow);
            renderTail(ps, buffers, camera, world, at, lead.scale(-1), pass * r, t, r, k, live * out);
            // 空气：激波罩和气流线跟下落速度走（0.1 → 1），挤出裂缝以后才有
            float air = out * out * (0.25F + 2.25F * k * k) / 2.5F * live;
            if (air > 0.01F) {
                renderSheath(ps.last(), buffers.getBuffer(StarfallDraw.additive(AIR_SHEATH)), eye, at, lead, r, t, air);
                renderStreaks(ps.last(), buffers.getBuffer(StarfallDraw.additive(STAR_CORONA)), eye, at, lead, r, t, air, e.getId());
            }
            ps.popPose();
            renderShocks(e, ps, buffers, eye, t, r, lead);
            // 压迫面：星的火光照在坑底，离地越近越亮、越大
            float press = k * k * 0.9F * live;
            if (press > 0.01F) {
                float py = (float) Math.max(0.1, at.y - r * 1.05), pr = r * (1.6F + 1.0F * k);
                quad(ps.last(), buffers.getBuffer(StarfallDraw.additive(STAR_CORONA)), new Vec3(at.x - pr, py, at.z - pr), new Vec3(at.x + pr, py, at.z - pr),
                    new Vec3(at.x + pr, py, at.z + pr), new Vec3(at.x - pr, py, at.z + pr), 0, 0, 1, 1, press, 0.85F * press, 0.65F * press, 1);
            }
        }

        private static final int SHEATH_RINGS = 9, SHEATH_SEGMENTS = 28, STREAKS = 36;
        /** 激波罩从前缘往后包到的角度（约 117°），再往后就拖进尾流。 */
        private static final float SHEATH_ARC = 2.05F;
        /** 马赫环：每隔几拍甩出一圈，一圈活多少拍。 */
        private static final float SHOCK_GAP = 5, SHOCK_LIFE = 20;

        /**
         * 激波罩：星前面被压缩烧亮的空气，一层套在岩团外面的弹头形薄壳（1.3 倍岩半径，过了侧面往后拖长）。
         * 只有掠射的边缘亮、正对镜头的那面透明：迎面看是贴着轮廓一圈流动的火边，侧面看是罩在星前面的一弯月牙。
         * 贴图沿壳往后流，越快流得越急、拖得越长。power 0..1 跟下落速度走。镜头快贴到壳上时淡掉，免得掠射的壳面糊成一整片橙。
         */
        private static void renderSheath(PoseStack.Pose pose, VertexConsumer vc, Vec3 eye, Vec3 at, Vec3 lead, float r, float t, float power) {
            float close = clamp01(((float) at.distanceTo(eye) / r - 1.6F) / 1.2F);
            if (close <= 0) return;
            Vec3[] uv = basis(lead);
            Vec3 c = at.add(lead.scale(0.12F * r));
            float rs = 1.3F * r, stretch = 1 + 1.6F * power, flow = t * (0.05F + 0.1F * power);
            Vec3[][] p = new Vec3[SHEATH_RINGS + 1][SHEATH_SEGMENTS + 1];
            float[][][] col = new float[SHEATH_RINGS + 1][SHEATH_SEGMENTS + 1][];
            for (int j = 0; j <= SHEATH_RINGS; j++) {
                float th = SHEATH_ARC * j / SHEATH_RINGS, ct = Mth.cos(th), st = Mth.sin(th), front = Math.max(0, ct);
                float along = rs * ct * (ct < 0 ? stretch : 1);
                // 前缘最亮最白，往后转成橙红、淡出
                float w = (0.5F + 0.5F * front) * clamp01((SHEATH_ARC - th) / (SHEATH_ARC - 1.2F)) * 2 * power * close;
                for (int i = 0; i <= SHEATH_SEGMENTS; i++) {
                    float ph = Mth.TWO_PI * i / SHEATH_SEGMENTS;
                    Vec3 radial = uv[0].scale(Mth.cos(ph)).add(uv[1].scale(Mth.sin(ph)));
                    Vec3 pos = c.add(lead.scale(along)).add(radial.scale(rs * st));
                    Vec3 sight = pos.subtract(eye);
                    double len = sight.length();
                    float facing = len < 1.0E-4 ? 1 : (float) Math.abs(lead.scale(ct).add(radial.scale(st)).dot(sight) / len);
                    float b = w * (1 - facing) * (1 - facing);
                    p[j][i] = pos;
                    col[j][i] = new float[]{b, b * (0.5F + 0.4F * front), b * (0.2F + 0.35F * front)};
                }
            }
            for (int j = 0; j < SHEATH_RINGS; j++) {
                float v0 = 1.5F * j / SHEATH_RINGS - flow, v1 = 1.5F * (j + 1) / SHEATH_RINGS - flow;
                for (int i = 0; i < SHEATH_SEGMENTS; i++) {
                    float u0 = 2F * i / SHEATH_SEGMENTS, u1 = 2F * (i + 1) / SHEATH_SEGMENTS;
                    shade(pose, vc, new Vec3[]{p[j][i], p[j][i + 1], p[j + 1][i + 1], p[j + 1][i]},
                        new float[][]{{u0, v0}, {u1, v0}, {u1, v1}, {u0, v1}}, new float[][]{col[j][i], col[j][i + 1], col[j + 1][i + 1], col[j + 1][i]});
                }
            }
        }

        /** 气流线：一条条亮线贴着激波罩从前缘滑到侧面，再往后、往外甩进尾流；迎面看就是从星的轮廓往四面八方射出去的速度线。 */
        private static void renderStreaks(PoseStack.Pose pose, VertexConsumer vc, Vec3 eye, Vec3 at, Vec3 lead, float r, float t, float power, int seed) {
            Vec3[] uv = basis(lead);
            Vec3 c = at.add(lead.scale(0.12F * r));
            float rs = 1.36F * r;
            for (int i = 0; i < STREAKS; i++) {
                float period = (8 + 6 * rand(seed, i, 41)) * (1.3F - 0.6F * power);
                float s = (t / period + rand(seed, i, 42)) % 1;
                float ph = Mth.TWO_PI * (i + 0.6F * rand(seed, i, 43)) / STREAKS;
                Vec3 radial = uv[0].scale(Mth.cos(ph)).add(uv[1].scale(Mth.sin(ph)));
                Vec3 head = streakPoint(c, lead, radial, rs, r, s, power), tail = streakPoint(c, lead, radial, rs, r, s - 0.16F - 0.1F * power, power);
                Vec3 side = head.subtract(tail).cross(head.subtract(eye));
                if (side.lengthSqr() < 1.0E-8) continue;
                side = side.normalize().scale(r * (0.05F + 0.05F * rand(seed, i, 45)));
                float b = 1.3F * power * Mth.sin(Mth.PI * s) * (0.55F + 0.45F * rand(seed, i, 44));
                float[] hc = {b, 0.86F * b, 0.66F * b}, tc = {0, 0, 0};
                // 横截面取日冕贴图正中那一行：中间亮、两边淡；头亮尾灭
                shade(pose, vc, new Vec3[]{head.add(side), head.subtract(side), tail.subtract(side.scale(0.4)), tail.add(side.scale(0.4))},
                    new float[][]{{0, 0.5F}, {1, 0.5F}, {1, 0.5F}, {0, 0.5F}}, new float[][]{hc, hc, tc, tc});
            }
        }

        /** 气流线上 s（0..1）处的位置：从前缘 20° 沿壳滑到 140°，过了侧面往后拖长、往外甩。 */
        private static Vec3 streakPoint(Vec3 c, Vec3 lead, Vec3 radial, float rs, float r, float s, float power) {
            float th = 0.35F + 2.1F * Math.max(0, s), ct = Mth.cos(th), st = Mth.sin(th);
            float along = rs * ct * (ct < 0 ? 1 + 1.6F * power : 1);
            float fling = r * 0.7F * (float) Math.pow(Math.max(0, th - Mth.HALF_PI) / 0.9F, 1.5);
            return c.add(lead.scale(along)).add(radial.scale(rs * st + fling));
        }

        /**
         * 马赫环：星挤出天裂后，每 5 拍在星身后留下一圈被推开的空气，原地往外扩、淡掉，一串连起来是一只往后张开的锥；
         * 迎面看就是一圈圈从星的轮廓荡开的波纹。越快越亮、扩得越大，落地后很快淡掉。
         */
        private void renderShocks(SkyMeteorEntity e, PoseStack ps, MultiBufferSource buffers, Vec3 eye, float t, float r, Vec3 lead) {
            float first = e.breakTick() + 4, hit = SkyrenderEntity.METEOR_HIT;
            if (t < first) return;
            VertexConsumer vc = buffers.getBuffer(StarfallDraw.additive(StarfallDraw.RING));
            for (float born = first; born <= t && born <= hit - 3; born += SHOCK_GAP) {
                float a = t - born;
                if (a >= SHOCK_LIFE) continue;
                float p = a / SHOCK_LIFE, k = SkyMeteorEntity.drop(born), speed = (0.25F + 2.25F * k * k) / 2.5F;
                float b = 0.42F * speed * (1 - p) * (1 - p) * clamp01(1 - (t - hit) / 3);
                if (b < 0.01F) continue;
                Vec3 center = e.starPos(born).subtract(lead.scale(0.15F * r));
                float grow = 1 - (1 - p) * (1 - p);
                pushNear(ps, eye, nearScale(eye, center));
                ring(ps.last(), vc, center, lead, r * (1.2F + (1.6F + 1.6F * speed) * grow), r * (0.25F + 0.5F * p), b, 0.82F * b, 0.66F * b);
                ps.popPose();
            }
        }

        /**
         * 崩开：芯留在坑里 6 拍里塌缩；20 块外壳带着撞击那一刻的朝向，从各自的位置往外、往上崩出去，
         * 边飞边翻、边烧边缩（半边长从 0.38 缩到 0.22 倍），落地后停住，10 拍后再 14 拍缩没。整块从白热冷到暗红要 40 拍。
         */
        private void renderDebris(SkyMeteorEntity e, PoseStack ps, MultiBufferSource buffers, float since, float radius, Vec3 sky, int seed, Vec3 eye) {
            float s = since - SINK;
            if (s < 0) return;
            Vec3 lead = e.starPos(SkyrenderEntity.METEOR_HIT).subtract(sky).normalize();
            Quaternionf q = starRotation(SkyrenderEntity.METEOR_HIT + SINK, lead);
            Vec3 core = e.starPos(SkyrenderEntity.METEOR_HIT).add(0, -0.6F * radius, 0);
            float hot = clamp01(1 - s / 40), g = 0.055F;
            Glow glow = new Glow(lead, 0.22F + 0.45F * hot, 0, 0.35F + 0.65F * hot, 0, 0.55F * (float) Math.pow(hot, 1.5), 0);
            Lump[] lumps = new Lump[ROCK.length];
            for (int i = 0; i < ROCK.length; i++) {
                float[] l = ROCK[i];
                if (i == 0) {
                    float sc = 1 - clamp01(s / 6);
                    if (sc > 0) lumps[0] = new Lump(core, new Quaternionf(q).mul(ROCK_ROT[0]), l[3] * 0.5F * radius * sc, l[4] * 0.5F * radius * sc,
                        l[5] * 0.5F * radius * sc, 0, core, glow);
                    continue;
                }
                Vec3 o = rotate(q, l[0], l[1], l[2]);
                // 往上抛得高，从玩家头顶越过去落到坑外，不正面糊脸；起点收到 0.7 个半径，像整团塌下去再炸开
                Vec3 v = new Vec3(o.x, o.y + 0.9, o.z).normalize().scale(0.8F + 0.55F * rand(seed, i, 31));
                Vec3 p0 = core.add(o.scale(radius * 0.7F));
                float half = Math.max(l[3], Math.max(l[4], l[5])) * 0.5F * radius;
                float he = half * 0.3F;
                double disc = v.y * v.y + 2 * g * (p0.y - he);
                if (disc < 0) continue;
                float land = (float) ((v.y + Math.sqrt(disc)) / g), fs = Math.min(s, land);
                Vec3 p = p0.add(v.scale(fs)).add(0, -0.5F * g * fs * fs, 0);
                float sc = 0.38F - 0.16F * clamp01(fs / 24);
                if (s > land) sc *= clamp01(1 - (s - land - 10) / 14);
                if (sc <= 0.01F) continue;
                // 落到镜头身上的那块不画，免得整屏变成方块内壁
                if (p.distanceToSqr(eye) < Mth.square(half * sc * 1.9F)) continue;
                Vec3 ax = new Vec3(rand(seed, i, 33) - 0.5F, rand(seed, i, 34) - 0.5F, rand(seed, i, 35) - 0.5F).normalize();
                Quaternionf rot = new Quaternionf().rotationAxis(fs * (0.06F + 0.1F * rand(seed, i, 32)), (float) ax.x, (float) ax.y, (float) ax.z)
                    .mul(q).mul(ROCK_ROT[i]);
                lumps[i] = new Lump(p, rot, l[3] * 0.5F * radius * sc, l[4] * 0.5F * radius * sc, l[5] * 0.5F * radius * sc, i & 3, p, glow);
            }
            drawLumps(ps.last(), buffers, lumps);
        }

        /**
         * 尾焰：一条沿来路拖回天裂方向的软光带（日冕贴图的下半张：头上最亮，往后和两侧淡出），
         * 外加两条换帧的火舌。meteor_tail 一张四帧，一条火舌只取四分之一宽。下落越快拖得越长，最多拖回天裂再往后一个星半径。
         * back 是来路方向，room 是星心到天裂平面的距离（还没挤出来时为负，拖不出尾巴）。
         */
        private void renderTail(PoseStack ps, MultiBufferSource buffers, Vec3 camera, Vec3 world, Vec3 at, Vec3 back, float room, float t, float size, float k,
                                float live) {
            if (room + size <= 1 || live <= 0) return;
            float speed = 0.12F + 2.64F * k * k;
            float len = Math.min(room + size, size * (1.4F + 1.5F * speed));
            Vec3 toCamera = camera.subtract(world.add(at)).normalize();
            Vec3 side = back.cross(toCamera);
            if (side.lengthSqr() < 1.0E-6) side = back.cross(new Vec3(1, 0, 0));
            side = side.normalize();
            float heat = (0.4F + 0.6F * k) * live;
            Vec3 end = at.add(back.scale(len));
            float w = size * 0.95F;
            quad(ps.last(), buffers.getBuffer(StarfallDraw.additive(STAR_CORONA)), at.add(side.scale(w)), at.subtract(side.scale(w)),
                end.subtract(side.scale(w * 0.35F)), end.add(side.scale(w * 0.35F)), 0, 0.5F, 1, 1, heat, 0.6F * heat, 0.3F * heat, 1);
            VertexConsumer flame = buffers.getBuffer(StarfallDraw.additive(StarfallDraw.TAIL));
            int frame = (int) (t * 0.5F);
            float[][] layers = {{0.62F, 0.9F, 0.9F}, {0.38F, 0.65F, 1}};
            for (int i = 0; i < layers.length; i++) {
                float u0 = ((frame + i * 2) & 3) * 0.25F, b = heat * layers[i][2];
                StarfallDraw.ribbon(ps.last(), flame, at.add(back.scale(size * 0.8F)), back, toCamera, len * layers[i][1], size * layers[i][0], size * 0.08F,
                    u0, u0 + 0.25F, b, 0.62F * b, 0.3F * b);
            }
        }

        /** 撞击：白热火球鼓起再褪成橙红，外面一圈更大更淡的热浪，一根 3 拍冲到顶的光柱。 */
        private void renderBurst(PoseStack ps, MultiBufferSource buffers, Quaternionf cam, Vec3 camera, Vec3 world, float since, float radius) {
            if (since >= 30) return;
            Vec3 at = new Vec3(0, radius * 0.3F, 0);
            float q = 1 - (1 - clamp01(since / 22)) * (1 - clamp01(since / 22));
            float f = (float) Math.pow(clamp01(1 - since / 26), 1.3), cool = clamp01(since / 18);
            sprite(ps, buffers, cam, STAR_CORONA, at, radius * (1.0F + 2.0F * q), 0, f, f * (0.95F - 0.5F * cool), f * (0.85F - 0.65F * cool));
            float wide = (float) Math.pow(clamp01(1 - since / 30), 2) * 0.45F;
            sprite(ps, buffers, cam, STAR_CORONA, at.add(0, radius * 0.4F, 0), radius * (2.5F + 3.5F * q), 0, wide, wide * 0.55F, wide * 0.28F);
            float col = clamp01(1 - since / 24);
            if (col <= 0) return;
            Vec3 toCamera = camera.subtract(world).normalize();
            Vec3 side = new Vec3(0, 1, 0).cross(toCamera);
            if (side.lengthSqr() < 1.0E-6) side = new Vec3(1, 0, 0);
            side = side.normalize();
            float h = 90 * clamp01((since + 1) / 4);
            Vec3 top = new Vec3(0, h, 0);
            VertexConsumer vc = buffers.getBuffer(StarfallDraw.additive(STAR_CORONA));
            for (int i = 0; i < 2; i++) {
                float w = radius * (i == 0 ? 0.5F : 0.18F) * (0.35F + 0.65F * col), b = col * (i == 0 ? 0.9F : 1.3F);
                quad(ps.last(), vc, side.scale(w), side.scale(-w), top.subtract(side.scale(w * 0.3F)), top.add(side.scale(w * 0.3F)), 0, 0.5F, 1, 1,
                    b, b * (i == 0 ? 0.82F : 0.98F), b * (i == 0 ? 0.62F : 0.92F), 1);
            }
        }

        /** 光尘：撞击后从坑里升起，绕个弯飘回天裂，越远画得越大好让人看得见，最后一颗赶在天裂合拢前进去。远处的按 nearScale 拉近。 */
        private void renderDust(PoseStack ps, MultiBufferSource buffers, Quaternionf cam, Vec3 eye, float since, Vec3 sky, float arena, int seed) {
            for (int i = 0; i < DUST; i++) {
                float start = 8 + i * 0.75F, dur = 26 + 10 * rand(seed, i, 11);
                float p = (since - start) / dur;
                if (p <= 0 || p >= 1) continue;
                float ang = rand(seed, i, 12) * Mth.TWO_PI, rr = arena * 0.65F * Mth.sqrt(rand(seed, i, 13));
                Vec3 p0 = new Vec3(Mth.cos(ang) * rr, 1 + 4 * rand(seed, i, 14), Mth.sin(ang) * rr);
                Vec3 p2 = sky.add((rand(seed, i, 15) - 0.5F) * 24, (rand(seed, i, 16) - 0.5F) * 6, (rand(seed, i, 17) - 0.5F) * 24);
                Vec3 p1 = p0.add(p2).scale(0.5).add(0, 8 + 8 * rand(seed, i, 18), 0)
                    .add(new Vec3(-Mth.sin(ang), 0, Mth.cos(ang)).scale(16 * (rand(seed, i, 19) - 0.5F)));
                float e = p * p * (3 - 2 * p);
                Vec3 at = p0.scale((1 - e) * (1 - e)).add(p1.scale(2 * e * (1 - e))).add(p2.scale(e * e));
                float b = clamp01(p * 8) * (1 - p * 0.6F);
                float size = (0.7F + 0.7F * rand(seed, i, 20)) * (1 + 4 * e);
                pushNear(ps, eye, nearScale(eye, at));
                sprite(ps, buffers, cam, STAR_CORONA, at, size, 0, b * 1.2F, b * 1.02F, b * 0.72F);
                ps.popPose();
            }
        }

        /** 落地两道贴地冲击波，12 拍里扩到坑外 6 格。 */
        private void renderWaves(PoseStack ps, MultiBufferSource buffers, float since, float arena) {
            VertexConsumer buffer = null;
            for (int delay : new int[]{0, 4}) {
                float a = since - delay;
                if (a < 0 || a >= 16) continue;
                if (buffer == null) buffer = buffers.getBuffer(StarfallDraw.additive(StarfallDraw.RING));
                float q = clamp01(a / 12);
                float outer = (arena + 6) * (1 - (1 - q) * (1 - q));
                float inner = Math.max(0, outer - 2.5F * (1 - 0.5F * q));
                float bright = (1 - a / 16) * (delay == 0 ? 1 : 0.7F);
                float wall = 3 * (1 - q * 0.8F);
                for (int i = 0; i < RING_SEGMENTS; i++) {
                    float a0 = i * Mth.TWO_PI / RING_SEGMENTS, a1 = (i + 1) * Mth.TWO_PI / RING_SEGMENTS;
                    float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
                    float u0 = i * 6F / RING_SEGMENTS, u1 = (i + 1) * 6F / RING_SEGMENTS;
                    quad(ps.last(), buffer, new Vec3(c0 * inner, 0.1, s0 * inner), new Vec3(c1 * inner, 0.1, s1 * inner),
                        new Vec3(c1 * outer, 0.1, s1 * outer), new Vec3(c0 * outer, 0.1, s0 * outer), u0, 0, u1, 1, bright, 0.88F * bright, 0.75F * bright, 1);
                    float wb = 0.6F * bright;
                    quad(ps.last(), buffer, new Vec3(c0 * outer, wall, s0 * outer), new Vec3(c1 * outer, wall, s1 * outer),
                        new Vec3(c1 * outer, 0.05, s1 * outer), new Vec3(c0 * outer, 0.05, s0 * outer), u0, 0, u1, 1, wb, 0.85F * wb, 0.7F * wb, 1);
                }
            }
        }

        /** 坑中心的焦痕：落地时烧得发橙，150 拍里冷成暗红并淡掉。 */
        private void renderScorch(PoseStack ps, MultiBufferSource buffers, float since) {
            float bright = clamp01(since / 4) * clamp01(1 - since / 150);
            if (bright <= 0) return;
            float heat = clamp01(1 - since / 50);
            flat(ps, buffers.getBuffer(StarfallDraw.additive(StarfallDraw.SCORCH)), 10, 0.04F, 0, 0, 1, 1, bright, bright * (0.35F + 0.5F * heat),
                bright * (0.12F + 0.35F * heat), 1);
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
