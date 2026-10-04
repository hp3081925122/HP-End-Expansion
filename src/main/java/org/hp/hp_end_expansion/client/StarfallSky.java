package org.hp.hp_end_expansion.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.hp.hp_end_expansion.Hp_end_expansion;
import org.hp.hp_end_expansion.entity.starwreck.FallingStarEntity;
import org.hp.hp_end_expansion.entity.starwreck.SkyrenderEntity;
import org.hp.hp_end_expansion.entity.starwreck.StarChaserEntity;
import org.hp.hp_end_expansion.entity.starwreck.StarImpactEntity;
import org.hp.hp_end_expansion.entity.starwreck.StarRiftEntity;
import org.hp.hp_end_expansion.worldgen.StarwreckWorldgen;
import org.joml.Matrix4f;

/**
 * 星骸荒原的天空。平时：地平线一圈暗红余烬光，偶尔一道远处的流星。
 * 星雨期间：整片天压暗发红，裂隙方向的天空被照亮，远处下起流星雨；陨星落地时天空一闪、镜头一震，
 * 雾色跟着转红。星雨结束后约 7 秒慢慢退回平时。只读客户端已有的实体，不改星雨的计时和落点。
 */
@EventBusSubscriber(modid = Hp_end_expansion.MODID, value = Dist.CLIENT)
public final class StarfallSky {
    private static final float R = 90;
    private static final RandomSource RANDOM = RandomSource.create();
    private static final List<Streak> STREAKS = new ArrayList<>();
    private static final Set<Integer> SEEN_IMPACTS = new HashSet<>();
    private static float ambient, ambientO, event, eventO, flash, flashO, shake;
    // 裂天之主注视蓄力时整片天压暗的程度
    private static float gaze, gazeO, gazeTarget;
    private static Vec3 rift;
    private static Vec3 heading = new Vec3(0, -1, 0);
    private static long ticks;

    private StarfallSky() {}

    private static final class Streak {
        Vec3 start, travel;
        float speed, length, width, bright, age, life;
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        ambientO = ambient;
        eventO = event;
        flashO = flash;
        gazeO = gaze;
        gazeTarget = 0;
        if (level == null || player == null || level.dimension() != Level.END) {
            gaze = 0;
            ambient = event = flash = shake = 0;
            STREAKS.clear();
            SEEN_IMPACTS.clear();
            rift = null;
            return;
        }
        if (mc.isPaused()) return;
        ticks++;
        ambient = approach(ambient, level.getBiome(player.blockPosition()).is(StarwreckWorldgen.BIOME) ? 1 : 0, 0.01F);

        float target = 0;
        Vec3 nearestRift = null;
        boolean anyImpact = false;
        for (Entity entity : level.getEntitiesOfClass(Entity.class, player.getBoundingBox().inflate(192),
            x -> x instanceof StarRiftEntity || x instanceof FallingStarEntity || x instanceof StarImpactEntity || x instanceof StarChaserEntity
                || x instanceof SkyrenderEntity)) {
            if (entity instanceof SkyrenderEntity boss) {
                if (boss.isAlive()) skyrender(boss, player);
                if (boss.isAlive()) target = Math.max(target, 1);
                // 第三阶段坑上方那道不合拢的长缝，按星雨裂隙的方式把那片天照亮
                if (boss.isAlive() && boss.getPhase() >= 3) nearestRift = boss.getAnchor().add(0, SkyrenderEntity.SKY_TEAR_HEIGHT, 0);
                continue;
            }
            if (entity instanceof StarRiftEntity r) {
                target = Math.max(target, StarRiftEntity.openness(r.getAge()));
                nearestRift = r.position();
            } else if (entity instanceof FallingStarEntity star) {
                target = Math.max(target, star.isSmall() ? 0.75F : 1);
                Vec3 v = star.getDeltaMovement();
                if (v.lengthSqr() > 1.0E-4) heading = v.normalize();
            } else if (entity instanceof StarImpactEntity impact) {
                anyImpact = true;
                target = Math.max(target, impact.isSmall() ? 0.75F : 0.9F);
                if (SEEN_IMPACTS.add(impact.getId()) && impact.tickCount < 10) {
                    double d = impact.distanceTo(player);
                    float k = impact.isSmall() ? 0.4F : 1;
                    flash = Math.max(flash, k * (float) Mth.clamp(1.2 - d / 110, 0.3, 1));
                    shake = Math.max(shake, k * (float) Mth.clamp(1 - d / 48, 0, 1));
                }
            } else if (entity instanceof StarChaserEntity chaser && chaser.isAlive()) {
                // 嚎叫之后天空重新染红；嚎叫、撞墙、践踏、过载时震屏
                if (chaser.getPhase() >= 1) target = Math.max(target, 0.75F);
                float near = (float) Mth.clamp(1 - chaser.distanceTo(player) / 40, 0, 1);
                int age = chaser.getClientAge();
                byte state = chaser.getState();
                if (state == StarChaserEntity.HOWL && age >= StarChaserEntity.HOWL_ROAR && age < 34) shake = Math.max(shake, 0.55F * near);
                if (state == StarChaserEntity.STAGGER && age < 3) shake = Math.max(shake, 0.7F * near);
                if (state == StarChaserEntity.STOMP && age >= StarChaserEntity.STOMP_HIT && age < StarChaserEntity.STOMP_HIT + 3)
                    shake = Math.max(shake, 0.6F * near);
                if (state == StarChaserEntity.CHARGE) shake = Math.max(shake, 0.25F * near);
                if (state == StarChaserEntity.LEAVE && age >= StarChaserEntity.LEAVE_LIFT && age < StarChaserEntity.LEAVE_LIFT + 3)
                    shake = Math.max(shake, 0.5F * near);
                if (chaser.overloadAge >= 0 && chaser.overloadAge < 3) {
                    shake = Math.max(shake, 0.8F * near);
                    flash = Math.max(flash, 0.6F * near);
                }
            }
        }
        if (!anyImpact) SEEN_IMPACTS.clear();
        // 压暗来得慢、退得快：释放后约半秒回亮
        if (gaze < gazeTarget) gaze = gazeTarget;
        else gaze = approach(gaze, gazeTarget, 0.12F);
        rift = nearestRift != null ? nearestRift : rift;
        // 来得快、退得慢：落地后大约 7 秒才回到平时
        event = event < target ? approach(event, target, 0.07F) : approach(event, target, 0.007F);
        if (event <= 0 && nearestRift == null) rift = null;
        flash *= 0.86F;
        shake *= 0.88F;

        float rate = ambient * 0.03F + event * event * 0.5F;
        while (rate > 0) {
            if (RANDOM.nextFloat() < Math.min(1, rate)) STREAKS.add(newStreak(event > 0.2F));
            rate -= 1;
        }
        for (Iterator<Streak> it = STREAKS.iterator(); it.hasNext(); ) {
            Streak s = it.next();
            if (++s.age >= s.life) it.remove();
        }
    }

    /** 裂天之主：落地、跃袭落地、尾锤、人立怒吼和裂天撕开天幕时震屏；注视蓄力时天空跟着压暗。 */
    private static void skyrender(SkyrenderEntity boss, LocalPlayer player) {
        float near = (float) Mth.clamp(1 - boss.distanceTo(player) / 60, 0.15, 1);
        int age = boss.getClientAge();
        byte state = boss.getState();
        if (state == SkyrenderEntity.ROAR && boss.getPhase() <= 1 && age < 3) {
            shake = Math.max(shake, near);
            flash = Math.max(flash, 0.5F * near);
        }
        if (state == SkyrenderEntity.LEAP && age >= SkyrenderEntity.LEAP_LAND && age < SkyrenderEntity.LEAP_LAND + 3) shake = Math.max(shake, 0.8F * near);
        if (state == SkyrenderEntity.TAIL && age >= SkyrenderEntity.TAIL_HIT && age < SkyrenderEntity.TAIL_HIT + 3) shake = Math.max(shake, 0.5F * near);
        if (state == SkyrenderEntity.PHASE_UP && age >= 16 && age < 30) shake = Math.max(shake, 0.45F * near);
        if (state == SkyrenderEntity.REND && age >= SkyrenderEntity.REND_TEAR && age < SkyrenderEntity.REND_TEAR + 3) {
            shake = Math.max(shake, 0.4F * near);
            flash = Math.max(flash, 0.3F * near);
        }
        if (state == SkyrenderEntity.GAZE && age < boss.gazeCharge()) gazeTarget = Math.max(gazeTarget, age / (float) boss.gazeCharge());
        // 天陨：起势时天压到全黑，坠星时越压越震，落地一闪一震
        if (state == SkyrenderEntity.METEOR) {
            float wide = (float) Mth.clamp(1 - boss.distanceTo(player) / 90, 0.4, 1);
            if (age < SkyrenderEntity.METEOR_HIT + 2) gazeTarget = Math.max(gazeTarget, Mth.clamp(age / 30F, 0, 1));
            if (age == SkyrenderEntity.METEOR_TEAR || age == SkyrenderEntity.METEOR_TEAR + 15) shake = Math.max(shake, 0.4F * wide);
            if (age >= SkyrenderEntity.METEOR_DROP && age < SkyrenderEntity.METEOR_HIT) {
                float k = (age - SkyrenderEntity.METEOR_DROP) / (float) (SkyrenderEntity.METEOR_HIT - SkyrenderEntity.METEOR_DROP);
                // 前面只是低沉的颤，最后一秒越压越猛，落地前已经震得看不稳
                shake = Math.max(shake, (0.12F + 0.95F * k * k * k * k) * wide);
            }
            if (age >= SkyrenderEntity.METEOR_HIT && age < SkyrenderEntity.METEOR_HIT + 4) {
                shake = Math.max(shake, 1.45F * wide);
                flash = Math.max(flash, wide);
            }
            // 第二次爆闪
            if (age >= SkyrenderEntity.METEOR_HIT + 14 && age < SkyrenderEntity.METEOR_HIT + 17) shake = Math.max(shake, 0.9F * wide);
        }
    }

    private static Streak newStreak(boolean shower) {
        Streak s = new Streak();
        double az = RANDOM.nextDouble() * Math.PI * 2, el = Math.toRadians(18 + RANDOM.nextDouble() * 55);
        s.start = new Vec3(Math.cos(el) * Math.sin(az), Math.sin(el), Math.cos(el) * Math.cos(az));
        // 星雨时和真陨星同一个方向斜落；平时方向随机，但总是往下
        Vec3 h = shower ? new Vec3(heading.x, 0, heading.z) : new Vec3(RANDOM.nextDouble() - 0.5, 0, RANDOM.nextDouble() - 0.5);
        if (h.lengthSqr() < 1.0E-4) h = new Vec3(1, 0, 0);
        Vec3 base = h.normalize().scale(0.55 + RANDOM.nextDouble() * 0.2).add(0, -1, 0)
            .add((RANDOM.nextDouble() - 0.5) * 0.25, 0, (RANDOM.nextDouble() - 0.5) * 0.25);
        s.travel = base.subtract(s.start.scale(base.dot(s.start))).normalize();
        s.speed = 0.03F + RANDOM.nextFloat() * 0.03F;
        s.length = 0.06F + RANDOM.nextFloat() * 0.1F;
        s.width = 0.25F + RANDOM.nextFloat() * 0.3F;
        s.bright = shower ? 0.6F + RANDOM.nextFloat() * 0.4F : 0.35F + RANDOM.nextFloat() * 0.3F;
        s.life = 8 + RANDOM.nextInt(10);
        return s;
    }

    @SubscribeEvent public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level.dimension() != Level.END) return;
        float pt = e.getPartialTick().getGameTimeDeltaPartialTick(false);
        float amb = Mth.lerp(pt, ambientO, ambient), ev = Mth.lerp(pt, eventO, event), fl = Mth.lerp(pt, flashO, flash);
        float gz = Mth.lerp(pt, gazeO, gaze);
        if (amb + ev + fl + gz < 0.003F && STREAKS.isEmpty()) return;
        Matrix4f m = new Matrix4f(e.getModelViewMatrix());
        Vec3 cam = e.getCamera().getPosition();
        float time = ticks + pt;

        RenderSystem.enableBlend();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Tesselator tess = Tesselator.getInstance();

        // 1. 压暗发红：普通混合
        float tint = 0.1F * amb + 0.5F * ev;
        if (tint > 0.002F) {
            RenderSystem.defaultBlendFunc();
            BufferBuilder b = tess.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
            box(b, m, 0.17F, 0.035F, 0.02F, tint);
            draw(b);
        }

        // 2. 加法：地平线余烬、裂隙照亮的天空、落地闪光、流星
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        BufferBuilder b = tess.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        double riftAz = rift != null ? Math.atan2(rift.x - cam.x, rift.z - cam.z) : 0;
        horizon(b, m, 0.3F * amb + 0.55F * ev, rift != null ? ev : 0, riftAz);
        if (rift != null && ev > 0.01F) riftGlow(b, m, rift.subtract(cam).normalize(), ev * (0.85F + 0.15F * Mth.sin(time * 0.5F)));
        if (fl > 0.01F) box(b, m, 1, 0.65F, 0.35F, 0.4F * fl);
        for (Streak s : STREAKS) streak(b, m, s, pt);
        draw(b);

        // 3. 注视蓄力：整片天压成近黑，只剩它的眼睛在世界里发光
        RenderSystem.defaultBlendFunc();
        if (gz > 0.002F) {
            BufferBuilder dark = tess.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
            box(dark, m, 0.01F, 0.0F, 0.01F, 0.75F * gz);
            draw(dark);
        }
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    private static void horizon(BufferBuilder b, Matrix4f m, float a, float riftBias, double riftAz) {
        if (a <= 0.002F) return;
        int n = 48;
        float low = (float) Math.toRadians(-14), mid = (float) Math.toRadians(4), high = (float) Math.toRadians(30);
        for (int i = 0; i < n; i++) {
            float az0 = i * Mth.TWO_PI / n, az1 = (i + 1) * Mth.TWO_PI / n;
            float k0 = a * (1 + 0.9F * riftBias * (float) Math.max(0, Math.cos(az0 - riftAz)));
            float k1 = a * (1 + 0.9F * riftBias * (float) Math.max(0, Math.cos(az1 - riftAz)));
            band(b, m, az0, az1, low, mid, 0.55F, 0.16F, 0.05F, 0.6F * k0, 0.6F * k1, 0.9F * k0, 0.9F * k1);
            band(b, m, az0, az1, mid, high, 0.7F, 0.26F, 0.07F, 0.9F * k0, 0.9F * k1, 0, 0);
        }
    }

    private static void band(BufferBuilder b, Matrix4f m, float az0, float az1, float el0, float el1,
                             float r, float g, float bl, float a00, float a10, float a01, float a11) {
        Vec3 p00 = sky(az0, el0), p10 = sky(az1, el0), p01 = sky(az0, el1), p11 = sky(az1, el1);
        tri(b, m, p00, r, g, bl, a00, p10, r, g, bl, a10, p11, r, g, bl, a11);
        tri(b, m, p00, r, g, bl, a00, p11, r, g, bl, a11, p01, r, g, bl, a01);
    }

    private static void riftGlow(BufferBuilder b, Matrix4f m, Vec3 c, float a) {
        Vec3 u = c.cross(new Vec3(0, 1, 0));
        if (u.lengthSqr() < 1.0E-4) u = new Vec3(1, 0, 0);
        u = u.normalize();
        Vec3 v = u.cross(c).normalize();
        int n = 24;
        double inner = 0.22, outer = 0.6;
        for (int i = 0; i < n; i++) {
            double p0 = i * Math.PI * 2 / n, p1 = (i + 1) * Math.PI * 2 / n;
            Vec3 i0 = ring(c, u, v, inner, p0), i1 = ring(c, u, v, inner, p1), o0 = ring(c, u, v, outer, p0), o1 = ring(c, u, v, outer, p1);
            tri(b, m, c.scale(R), 1, 0.55F, 0.2F, 0.5F * a, i0, 0.95F, 0.4F, 0.12F, 0.3F * a, i1, 0.95F, 0.4F, 0.12F, 0.3F * a);
            tri(b, m, i0, 0.95F, 0.4F, 0.12F, 0.3F * a, i1, 0.95F, 0.4F, 0.12F, 0.3F * a, o1, 0.6F, 0.2F, 0.05F, 0);
            tri(b, m, i0, 0.95F, 0.4F, 0.12F, 0.3F * a, o1, 0.6F, 0.2F, 0.05F, 0, o0, 0.6F, 0.2F, 0.05F, 0);
        }
    }

    private static Vec3 ring(Vec3 c, Vec3 u, Vec3 v, double angle, double phase) {
        return c.scale(Math.cos(angle)).add(u.scale(Math.cos(phase) * Math.sin(angle))).add(v.scale(Math.sin(phase) * Math.sin(angle))).normalize().scale(R);
    }

    private static void streak(BufferBuilder b, Matrix4f m, Streak s, float pt) {
        float age = s.age + pt;
        float a = s.bright * Mth.sin(Mth.PI * Mth.clamp(age / s.life, 0, 1));
        if (a <= 0.01F) return;
        float d = s.speed * age;
        Vec3 head = s.start.add(s.travel.scale(d)).normalize().scale(R - 2);
        Vec3 tail = s.start.add(s.travel.scale(Math.max(0, d - s.length))).normalize().scale(R - 2);
        Vec3 side = head.subtract(tail).cross(head);
        if (side.lengthSqr() < 1.0E-6) return;
        side = side.normalize().scale(s.width);
        tri(b, m, head.add(side), 1, 0.9F, 0.7F, a, head.subtract(side), 1, 0.9F, 0.7F, a, tail, 1, 0.45F, 0.12F, 0);
    }

    private static Vec3 sky(float az, float el) {
        return new Vec3(Mth.cos(el) * Mth.sin(az) * R, Mth.sin(el) * R, Mth.cos(el) * Mth.cos(az) * R);
    }

    private static void box(BufferBuilder b, Matrix4f m, float r, float g, float bl, float a) {
        float s = R;
        float[][] c = {{-s, -s, -s}, {s, -s, -s}, {s, s, -s}, {-s, s, -s}, {-s, -s, s}, {s, -s, s}, {s, s, s}, {-s, s, s}};
        int[][] faces = {{0, 1, 2, 3}, {5, 4, 7, 6}, {4, 0, 3, 7}, {1, 5, 6, 2}, {3, 2, 6, 7}, {4, 5, 1, 0}};
        for (int[] f : faces) {
            Vec3 p0 = v(c[f[0]]), p1 = v(c[f[1]]), p2 = v(c[f[2]]), p3 = v(c[f[3]]);
            tri(b, m, p0, r, g, bl, a, p1, r, g, bl, a, p2, r, g, bl, a);
            tri(b, m, p0, r, g, bl, a, p2, r, g, bl, a, p3, r, g, bl, a);
        }
    }

    private static Vec3 v(float[] p) { return new Vec3(p[0], p[1], p[2]); }

    private static void tri(BufferBuilder b, Matrix4f m, Vec3 p0, float r0, float g0, float b0, float a0,
                            Vec3 p1, float r1, float g1, float b1, float a1, Vec3 p2, float r2, float g2, float b2, float a2) {
        b.addVertex(m, (float) p0.x, (float) p0.y, (float) p0.z).setColor(r0, g0, b0, a0);
        b.addVertex(m, (float) p1.x, (float) p1.y, (float) p1.z).setColor(r1, g1, b1, a1);
        b.addVertex(m, (float) p2.x, (float) p2.y, (float) p2.z).setColor(r2, g2, b2, a2);
    }

    private static void draw(BufferBuilder b) {
        MeshData mesh = b.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);
    }

    @SubscribeEvent public static void fogColor(ViewportEvent.ComputeFogColor e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level.dimension() != Level.END) return;
        float pt = (float) e.getPartialTick();
        float ev = Mth.lerp(pt, eventO, event), fl = Mth.lerp(pt, flashO, flash);
        float k = Math.min(1, 0.55F * ev + 0.6F * fl);
        if (k > 0.001F) {
            e.setRed(Mth.lerp(k, e.getRed(), 0.42F + 0.4F * fl));
            e.setGreen(Mth.lerp(k, e.getGreen(), 0.12F + 0.25F * fl));
            e.setBlue(Mth.lerp(k, e.getBlue(), 0.05F + 0.1F * fl));
        }
        // 注视蓄力时雾色一起压暗
        float gz = Mth.lerp(pt, gazeO, gaze);
        if (gz > 0.001F) {
            float dim = 1 - 0.7F * gz;
            e.setRed(e.getRed() * dim);
            e.setGreen(e.getGreen() * dim);
            e.setBlue(e.getBlue() * dim);
        }
    }

    // 落地震屏，强度随距离衰减，跟随“画面扭曲效果”设置
    @SubscribeEvent public static void cameraShake(ViewportEvent.ComputeCameraAngles e) {
        if (shake < 0.01F) return;
        float scale = Minecraft.getInstance().options.screenEffectScale().get().floatValue();
        if (scale <= 0) return;
        float t = ticks + (float) e.getPartialTick();
        float s = shake * shake * 2.2F * scale;
        e.setPitch(e.getPitch() + Mth.sin(t * 2.7F) * s);
        e.setYaw(e.getYaw() + Mth.sin(t * 2.1F + 1.3F) * s * 0.7F);
        e.setRoll(e.getRoll() + Mth.sin(t * 3.3F + 0.5F) * s * 0.6F);
    }

    private static float approach(float value, float target, float step) {
        return value < target ? Math.min(target, value + step) : Math.max(target, value - step);
    }
}
