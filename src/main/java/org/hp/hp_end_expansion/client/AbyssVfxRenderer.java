package org.hp.hp_end_expansion.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.hp.hp_end_expansion.entity.tidelight.AbyssVfxEntity;

/** 深渊守望者的独立特效：晶核弹道 + 预警法阵 + 晶刺丛、地面漩涡、冲击波、星芒爆闪。 */
final class AbyssVfxRenderer extends EntityRenderer<AbyssVfxEntity> {
    AbyssVfxRenderer(EntityRendererProvider.Context ctx) { super(ctx); }

    @Override public ResourceLocation getTextureLocation(AbyssVfxEntity e) { return AbyssFx.GLYPH; }

    @Override public void render(AbyssVfxEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource b, int light) {
        float age = e.getAge(pt);
        Vec3 cam = entityRenderDispatcher.camera.getPosition().subtract(e.getPosition(pt));
        PoseStack.Pose p = ps.last();
        switch (e.getKind()) {
            case AbyssVfxEntity.SPIKE -> spike(e, age, cam, p, b);
            case AbyssVfxEntity.VORTEX -> vortex(e, age, p, b);
            case AbyssVfxEntity.SHOCKWAVE -> shockwave(age, e.getSize(), AbyssVfxEntity.SHOCK_LIFE, cam, p, b);
            default -> burst(age, e.getSize(), cam, p, b);
        }
    }

    // ---------------- 晶刺 ----------------

    private static void spike(AbyssVfxEntity e, float age, Vec3 cam, PoseStack.Pose p, MultiBufferSource b) {
        Vec3 start = e.getStart().subtract(e.position());
        if (age < AbyssVfxEntity.SPIKE_FLIGHT) {
            // 晶核沿抛物线飞向落点，身后拖 5 个渐淡的残影
            VertexConsumer g = AbyssFx.glow(b, AbyssFx.FLARE);
            for (int i = 0; i < 6; i++) {
                float t = Mth.clamp((age - i * 0.6F) / AbyssVfxEntity.SPIKE_FLIGHT, 0, 1);
                Vec3 at = start.scale(1 - t).add(0, Math.sin(t * Math.PI) * 3.5, 0);
                AbyssFx.billboard(p, g, at, cam.subtract(at), 0.7 - i * 0.1, age * 0.5, 1 - i * 0.17F);
            }
            return;
        }
        float warn = age - AbyssVfxEntity.SPIKE_FLIGHT;
        float span = AbyssVfxEntity.SPIKE_ERUPT - AbyssVfxEntity.SPIKE_FLIGHT;
        double r = AbyssVfxEntity.SPIKE_RADIUS;
        if (age < AbyssVfxEntity.SPIKE_ERUPT) {
            // 法阵亮起并慢转；外面一圈环向法阵收拢，收到边缘的那一刻破土
            float k = Mth.clamp(warn / 3, 0, 1) * (0.65F + 0.35F * Mth.sin(age * 1.3F));
            AbyssFx.decal(p, AbyssFx.glow(b, AbyssFx.GLYPH), new Vec3(0, 0.03, 0), r, age * 0.06, k);
            double rr = r + 2.2 * (1 - warn / span);
            AbyssFx.flatRing(p, AbyssFx.glow(b, AbyssFx.RING), new Vec3(0, 0.05, 0), rr - 0.35, rr, 24, 6, 0, 0.8F);
            return;
        }
        float since = age - AbyssVfxEntity.SPIKE_ERUPT;
        float tail = AbyssVfxEntity.SPIKE_LIFE - age;
        float fade = Mth.clamp(tail / 8, 0, 1);
        // 晶刺丛：3 tick 内冲出地面并略微过冲，最后 8 tick 沉回地里淡出
        float grow = 1;
        double sink = (1 - fade) * 1.6;
        VertexConsumer s = AbyssFx.solid(b, AbyssFx.CRYSTAL);
        long seed = e.getId() * 341873128712L;
        for (int i = 0; i < 5; i++) {
            double ang = (seed >>> (i * 5) & 31) / 31.0 * Math.PI * 2;
            double tilt = i == 0 ? 0.08 : 0.35 + (seed >>> (i * 3 + 20) & 7) / 7.0 * 0.3;
            double len = (i == 0 ? 2.6 : 1.2 + (seed >>> (i * 4 + 9) & 7) / 7.0 * 0.8) * grow;
            double off = i == 0 ? 0 : 0.45;
            Vec3 dir = new Vec3(Math.cos(ang) * tilt, 1, Math.sin(ang) * tilt);
            Vec3 base = new Vec3(Math.cos(ang) * off, -0.3 - sink, Math.sin(ang) * off);
            AbyssFx.crystal(p, s, base, dir, len, i == 0 ? 0.32 : 0.2, ang, fade);
        }
        // 破土瞬间的冲击环和星芒
        if (since < 8) shockwave(since, (float) AbyssVfxEntity.SPIKE_RADIUS, 8, cam, p, b);
        if (since < 4) {
            Vec3 c = new Vec3(0, 1.4, 0);
            AbyssFx.billboard(p, AbyssFx.glow(b, AbyssFx.FLARE), c, cam.subtract(c), 1.6 * (1 + since * 0.3), since * 0.3, 1 - since / 4);
        }
        AbyssFx.decal(p, AbyssFx.glow(b, AbyssFx.GLYPH), new Vec3(0, 0.03, 0), r, age * 0.06, 0.5F * fade);
    }

    // ---------------- 漩涡 ----------------

    private static void vortex(AbyssVfxEntity e, float age, PoseStack.Pose p, MultiBufferSource b) {
        float R = e.getSize();
        float k = Mth.clamp(age / 6, 0, 1) * Mth.clamp((AbyssVfxEntity.VORTEX_LIFE - age) / 8, 0, 1);
        VertexConsumer v = AbyssFx.glow(b, AbyssFx.VORTEX);
        AbyssFx.decal(p, v, new Vec3(0, 0.04, 0), R, -age * 0.12, k);
        AbyssFx.decal(p, v, new Vec3(0, 0.12, 0), R * 0.55, age * 0.2, k * 0.7F);
        // 三股贴地旋流，半径随时间往里收
        VertexConsumer a = AbyssFx.glow(b, AbyssFx.ARC);
        for (int i = 0; i < 3; i++) {
            double ph = (age * 0.05 + i / 3.0) % 1;
            double r1 = R * (1 - ph * 0.8);
            AbyssFx.arcBlade(p, a, new Vec3(0, 0.3 + ph * 0.6, 0), -age * 0.25 + i * Math.PI * 2 / 3, 1.6, r1 - 0.9, r1, 0, 12, k * (float) (1 - ph));
        }
        // 外缘一圈翻起的水墙
        AbyssFx.wallRing(p, AbyssFx.glow(b, AbyssFx.RING), Vec3.ZERO, R, 1.3, 0.5, 40, 10, k * 0.55F);
    }

    // ---------------- 冲击波 / 爆闪 ----------------

    static void shockwave(float age, float size, int life, Vec3 cam, PoseStack.Pose p, MultiBufferSource b) {
        float t = Mth.clamp(age / life, 0, 1);
        double r = size;
        float k = 1 - t;
        VertexConsumer g = AbyssFx.glow(b, AbyssFx.RING);
        AbyssFx.flatRing(p, g, new Vec3(0, 0.06, 0), r * 0.55, r, 32, 8, 0, k);
        AbyssFx.wallRing(p, g, Vec3.ZERO, r, 1.4 * (1 - t) + 0.2, 0, 32, 8, k * 0.8F);
        if (age < 4) {
            Vec3 c = new Vec3(0, 0.4, 0);
            AbyssFx.billboard(p, AbyssFx.glow(b, AbyssFx.FLARE), c, cam.subtract(c), size * 0.5, age * 0.4, 1 - age / 4);
        }
    }

    private static void burst(float age, float size, Vec3 cam, PoseStack.Pose p, MultiBufferSource b) {
        float k = 1 - age / AbyssVfxEntity.BURST_LIFE;
        if (k <= 0) return;
        AbyssFx.billboard(p, AbyssFx.glow(b, AbyssFx.FLARE), Vec3.ZERO, cam, size * (1 + age * 0.25), age * 0.35, k);
        AbyssFx.billboard(p, AbyssFx.glow(b, AbyssFx.FLARE), Vec3.ZERO, cam, size * 0.6 * (1 + age * 0.5), -age * 0.5 + 0.7, k * 0.7F);
        AbyssFx.axialRing(p, AbyssFx.glow(b, AbyssFx.RING), Vec3.ZERO, cam, size * (0.3 + age * 0.28), size * (0.6 + age * 0.32), 24, k);
    }
}
