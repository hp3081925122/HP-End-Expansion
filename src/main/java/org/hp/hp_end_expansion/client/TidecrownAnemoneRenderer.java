package org.hp.hp_end_expansion.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.hp.hp_end_expansion.Hp_end_expansion;
import org.hp.hp_end_expansion.entity.tidelight.TidecrownAnemoneEntity;
import org.hp.hp_end_expansion.registry.ModEntities;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

@EventBusSubscriber(modid = Hp_end_expansion.MODID, value = Dist.CLIENT)
public final class TidecrownAnemoneRenderer extends GeoEntityRenderer<TidecrownAnemoneEntity> {
    private static final ResourceLocation RIBBON = tex("ribbon"), FOAM = tex("foam"), FLARE = tex("flare"), PETAL = tex("petal"), SEAL = tex("seal");
    public TidecrownAnemoneRenderer(EntityRendererProvider.Context context) {
        super(context, new Model()); shadowRadius = 0.9F; addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(Hp_end_expansion.MODID, path); }
    private static ResourceLocation tex(String name) { return id("textures/entity/tidecrown_fx_" + name + ".png"); }
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.TIDECROWN_ANEMONE.get(), TidecrownAnemoneRenderer::new);
    }
    @Override protected float getDeathMaxRotation(TidecrownAnemoneEntity entity) { return 0; }
    @Override public boolean shouldRender(TidecrownAnemoneEntity e, Frustum f, double x, double y, double z) {
        return e.shouldRender(x, y, z) && f.isVisible(e.getBoundingBox().inflate(e.skill() == TidecrownAnemoneEntity.NONE ? 2 : 14));
    }
    @Override public void render(TidecrownAnemoneEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        super.render(e, yaw, pt, ps, buffers, light);
        if (e.isDeadOrDying() || e.skill() == TidecrownAnemoneEntity.NONE) return;
        Vec3 cam = entityRenderDispatcher.camera.getPosition().subtract(e.getPosition(pt));
        float age = e.skillAge(pt);
        switch (e.skill()) {
            case TidecrownAnemoneEntity.LASH -> lash(e, age, cam, ps.last(), buffers);
            case TidecrownAnemoneEntity.BIND -> bind(e, age, cam, ps.last(), buffers);
            case TidecrownAnemoneEntity.BLOOM -> bloom(age, cam, ps.last(), buffers);
            default -> {}
        }
    }
    private static void lash(TidecrownAnemoneEntity e, float age, Vec3 cam, PoseStack.Pose p, MultiBufferSource b) {
        for (int side = 0; side < 2; side++) {
            int hit = side == 0 ? TidecrownAnemoneEntity.LASH_LEFT : TidecrownAnemoneEntity.LASH_RIGHT;
            if (age > hit + 7) continue;
            Vec3 from = e.lashStart(side), end = e.lashEnd(side), d = end.subtract(from);
            double length = d.length(); if (length < 0.01) continue; d = d.scale(1 / length);
            float charge = Mth.clamp(age / hit, 0, 1);
            if (age < hit) {
                AbyssFx.ribbon(p, AbyssFx.glow(b, RIBBON), from, d, cam.subtract(from), length, 0.055, 0, 1, 0.28F + charge * 0.3F);
                AbyssFx.axialRing(p, AbyssFx.glow(b, FOAM), end, d, 0.42, TidecrownAnemoneEntity.LASH_RADIUS, 16, 0.35F + charge * 0.35F);
            } else {
                float fade = 1 - (age - hit) / 7;
                VertexConsumer ribbon = AbyssFx.glow(b, RIBBON);
                AbyssFx.ribbon(p, ribbon, from, d, cam.subtract(from), length, 0.14, 0, 1, fade * 0.45F);
                Vec3 tip = e.lashTip(side);
                double splashLength = end.distanceTo(tip);
                if (length > from.distanceTo(tip)) {
                    Vec3 splashDirection = end.subtract(tip).normalize();
                    AbyssFx.ribbon(p, ribbon, tip, splashDirection, cam.subtract(tip), splashLength, TidecrownAnemoneEntity.LASH_RADIUS, 0, 1, fade);
                    AbyssFx.ribbon(p, ribbon, tip, splashDirection, new Vec3(0, 1, 0), splashLength, TidecrownAnemoneEntity.LASH_RADIUS, 0, 1, fade * 0.65F);
                }
                AbyssFx.billboard(p, AbyssFx.glow(b, FLARE), end, cam.subtract(end), 0.75 * fade, side * 0.7, fade);
                for (int i = 1; i <= 3; i++) {
                    Vec3 c = from.add(d.scale(length * i / 4));
                    AbyssFx.axialRing(p, AbyssFx.glow(b, FOAM), c, d, 0.12, 0.25 + (age - hit) * 0.045, 12, fade * 0.65F);
                }
            }
        }
    }
    private static void bind(TidecrownAnemoneEntity e, float age, Vec3 cam, PoseStack.Pose p, MultiBufferSource b) {
        Vec3 c = e.mark();
        double radius = TidecrownAnemoneEntity.BIND_RADIUS;
        float charge = Mth.clamp(age / TidecrownAnemoneEntity.BIND_HIT, 0, 1);
        if (age < TidecrownAnemoneEntity.BIND_HIT) {
            AbyssFx.decal(p, AbyssFx.glow(b, SEAL), c, radius / 0.88, -age * 0.05, 0.28F + charge * 0.4F);
            AbyssFx.flatRing(p, AbyssFx.glow(b, FOAM), c.add(0, 0.015, 0), radius - 0.16, radius, 40, 6, 0, 0.8F);
            double closing = radius + 1.2 * (1 - charge);
            AbyssFx.flatRing(p, AbyssFx.glow(b, FOAM), c.add(0, 0.03, 0), closing - 0.12, closing, 40, 6, age * 0.04F, charge * 0.5F);
            for (int i = 0; i < 6; i++) petal(p, b, c, i * Math.PI / 3 + age * 0.07, radius, 0.15 + charge * 0.6, 0.32, charge * 0.6F);
        } else {
            float t = (age - TidecrownAnemoneEntity.BIND_HIT) * 2, fade = Mth.clamp((13 - t) / 9, 0, 1);
            if (fade <= 0) return;
            AbyssFx.decal(p, AbyssFx.glow(b, SEAL), c, radius / 0.88, -age * 0.05, fade);
            AbyssFx.wallRing(p, AbyssFx.glow(b, FOAM), c, radius, 1.9, -0.35, 40, 6, fade * 0.65F);
            for (int i = 0; i < 6; i++) petal(p, b, c, i * Math.PI / 3 + age * 0.07, radius, 1.9, 0.5, fade);
            AbyssFx.billboard(p, AbyssFx.glow(b, FLARE), c.add(0, 0.3, 0), cam.subtract(c), 0.6 * fade, age * 0.14, fade);
        }
    }
    private static void bloom(float age, Vec3 cam, PoseStack.Pose p, MultiBufferSource b) {
        Vec3 mouth = new Vec3(0, 1.42, 0);
        if (age < TidecrownAnemoneEntity.BLOOM_START) {
            float charge = age / TidecrownAnemoneEntity.BLOOM_START;
            AbyssFx.flatRing(p, AbyssFx.glow(b, FOAM), new Vec3(0, 0.055, 0), 5.86, TidecrownAnemoneEntity.BLOOM_RADIUS, 48, 8, 0, 0.35F + charge * 0.35F);
            AbyssFx.billboard(p, AbyssFx.glow(b, FLARE), mouth, cam.subtract(mouth), 0.12 + charge * 0.75, age * 0.24, charge);
            for (int i = 0; i < 6; i++) {
                double phase = (charge * 1.5 + i / 6.0) % 1, angle = i * Math.PI / 3 + age * 0.16;
                Vec3 c = mouth.add(Math.cos(angle) * (1.6 - phase), (1 - phase) * 0.7, Math.sin(angle) * (1.6 - phase));
                AbyssFx.billboard(p, AbyssFx.glow(b, FLARE), c, cam.subtract(c), 0.12 + phase * 0.08, angle, charge * 0.7F);
            }
            return;
        }
        float fade = Mth.clamp((TidecrownAnemoneEntity.BLOOM_END + 3.5F - age) / 3.5F, 0, 1);
        if (fade <= 0) return;
        double r = TidecrownAnemoneEntity.waveRadius(age), inner = Math.max(0, r - TidecrownAnemoneEntity.WAVE_WIDTH);
        VertexConsumer foam = AbyssFx.glow(b, FOAM);
        AbyssFx.flatRing(p, foam, new Vec3(0, 0.065, 0), inner, r, 48, 8, age * 0.024F, fade);
        AbyssFx.wallRing(p, foam, new Vec3(0, 0.05, 0), r, TidecrownAnemoneEntity.WAVE_HEIGHT - 0.05, -0.3, 48, 8, fade * 0.85F);
        for (int i = 0; i < 6; i++) petal(p, b, Vec3.ZERO, i * Math.PI / 3, r, TidecrownAnemoneEntity.WAVE_HEIGHT, 0.42, fade * 0.8F);
        if (age < TidecrownAnemoneEntity.BLOOM_START + 2.5F) {
            float burst = 1 - (age - TidecrownAnemoneEntity.BLOOM_START) / 2.5F;
            AbyssFx.billboard(p, AbyssFx.glow(b, FLARE), mouth, cam.subtract(mouth), 1.5 * burst, 0.3, burst);
            AbyssFx.axialRing(p, foam, mouth, new Vec3(0, 1, 0), 0.4, 1.4, 32, burst);
        }
    }
    private static void petal(PoseStack.Pose p, MultiBufferSource b, Vec3 center, double a, double r, double h, double width, float fade) {
        Vec3 radial = new Vec3(Math.cos(a), 0, Math.sin(a)), tangent = new Vec3(-Math.sin(a), 0, Math.cos(a)).scale(width);
        Vec3 base = center.add(radial.scale(r)), tip = center.add(radial.scale(r * 0.78)).add(0, h, 0);
        AbyssFx.quad(p, AbyssFx.glow(b, PETAL), tip.subtract(tangent), tip.add(tangent), base.add(tangent), base.subtract(tangent), 0, 0, 1, 1, fade);
    }
    private static final class Model extends GeoModel<TidecrownAnemoneEntity> {
        @Override public ResourceLocation getModelResource(TidecrownAnemoneEntity e) { return id("geo/tidecrown_anemone.geo.json"); }
        @Override public ResourceLocation getTextureResource(TidecrownAnemoneEntity e) { return id("textures/entity/tidecrown_anemone.png"); }
        @Override public ResourceLocation getAnimationResource(TidecrownAnemoneEntity e) { return id("animations/tidecrown_anemone.animation.json"); }
    }
}
