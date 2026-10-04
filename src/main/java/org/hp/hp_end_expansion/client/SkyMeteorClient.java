package org.hp.hp_end_expansion.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.hp.hp_end_expansion.Hp_end_expansion;
import org.hp.hp_end_expansion.entity.starwreck.SkyMeteorEntity;
import org.hp.hp_end_expansion.entity.starwreck.SkyrenderEntity;

/**
 * 天陨的屏幕效果：星冲出旋涡口那一拍天上一亮；落地前两秒暗角压进来、整屏烤橙、视野收窄；
 * 落地一拍白闪，碎岩剪影从屏幕中间扑面飞出去，14 拍后再闪一次；之后整屏压成余烬的暗红。
 */
@EventBusSubscriber(modid = Hp_end_expansion.MODID, value = Dist.CLIENT)
public final class SkyMeteorClient {
    private static final ResourceLocation ROCKS = SkyrenderClient.id("textures/effect/meteor_flash_rocks.png");
    private static final float HIT = SkyrenderEntity.METEOR_HIT;

    private SkyMeteorClient() {}

    private static float clamp01(float v) { return Mth.clamp(v, 0, 1); }

    private static Iterable<SkyMeteorEntity> meteors(Minecraft mc, Vec3 cam) {
        return mc.level.getEntitiesOfClass(SkyMeteorEntity.class, new AABB(cam, cam).inflate(256));
    }

    @SubscribeEvent public static void gui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        GuiGraphics g = event.getGuiGraphics();
        int w = g.guiWidth(), h = g.guiHeight();
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        for (SkyMeteorEntity m : meteors(mc, cam)) {
            float t = m.tickCount + pt, since = t - HIT, brk = t - m.breakTick();
            // 星冲出旋涡口：天上一亮
            if (brk >= 0 && brk < 8) fill(g, w, h, 0.4F * (1 - brk / 8), 0xFFE2A8);
            // 最后两秒：四周一圈柔边暗角压进来，整屏被星光烤得发橙
            if (since > -40 && since < 0) {
                float k = 1 + since / 40;
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                g.setColor(1, 1, 1, clamp01(0.2F + 0.8F * k * k));
                g.blit(SkyrenderClient.METEOR_VIGNETTE, 0, 0, w, h, 0, 0, 256, 256, 256, 256);
                g.setColor(1, 1, 1, 1);
                RenderSystem.disableBlend();
                fill(g, w, h, k * k * k * 0.27F, 0xFF7A2C);
            }
            // 星压到镜头身上：整屏变成熔岩色
            if (t >= SkyrenderEntity.METEOR_DROP && t < HIT + 1) {
                double d = m.getPosition(pt).add(m.starPos(t)).distanceTo(cam);
                float engulf = clamp01((float) (1.05 - d / m.starRadius()) / 0.35F);
                if (engulf > 0) fill(g, w, h, engulf * 0.92F, 0x3A0E06);
            }
            // 余烬：整屏压成暗红，四角更深
            if (since >= 16) {
                float a = 0.22F * smoothstep((since - 16) / 10) * (1 - smoothstep((since - 100) / 30));
                if (a > 0.005F) {
                    fill(g, w, h, a, 0x7A1A08);
                    RenderSystem.enableBlend();
                    RenderSystem.defaultBlendFunc();
                    g.setColor(1, 1, 1, clamp01(a * 3));
                    g.blit(SkyrenderClient.METEOR_VIGNETTE, 0, 0, w, h, 0, 0, 256, 256, 256, 256);
                    g.setColor(1, 1, 1, 1);
                    RenderSystem.disableBlend();
                }
            }
            flash(g, w, h, since, 1, m.getId());
            flash(g, w, h, since - 14, 0.75F, m.getId() + 7);
        }
    }

    /** 一次爆闪：一拍纯白，之后 11 拍褪成黄橙；同时碎岩剪影从屏幕中间转着扑面飞出去。 */
    private static void flash(GuiGraphics g, int w, int h, float s, float amp, int seed) {
        if (s < 0 || s >= 12) return;
        float a = s < 1 ? 1 : (1 - (s - 1) / 11) * (1 - (s - 1) / 11);
        int col = s < 1 ? 0xFFF6E2 : lerpColor(0xFFF0C8, 0xFFB850, clamp01((s - 1) / 8));
        fill(g, w, h, a * amp, col);
        if (s >= 10) return;
        float p = s / 10;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        for (int j = 0; j < 12; j++) {
            float n0 = SkyMeteorEntity.noise(seed, j, 131), n1 = SkyMeteorEntity.noise(seed, j, 132), n2 = SkyMeteorEntity.noise(seed, j, 133);
            // 每块从屏幕中间附近不同的地方冒出来，越飞越大、越快，近的几块大到占小半个屏幕
            float ang = Mth.TWO_PI * (j + 0.8F * n0) / 12, dist = h * (0.05F + 0.3F * n2 + 1.3F * (float) Math.pow(p, 1.5));
            float size = h * (0.12F + 0.9F * (float) Math.pow(p, 1.3)) * (0.5F + 0.7F * n1);
            g.pose().pushPose();
            g.pose().translate(w / 2F + Mth.cos(ang) * dist * 1.4F, h / 2F + Mth.sin(ang) * dist, 0);
            g.pose().mulPose(Axis.ZP.rotationDegrees(n0 * 360 + p * 360 * (n1 - 0.5F)));
            g.pose().scale(size / 32, size / 32, 1);
            g.setColor(1, 1, 1, clamp01(amp + 0.3F));
            g.blit(ROCKS, -16, -16, 32, 32, (j & 1) * 32, ((j >> 1) & 1) * 32, 32, 32, 64, 64);
            g.pose().popPose();
        }
        g.setColor(1, 1, 1, 1);
        RenderSystem.disableBlend();
    }

    private static void fill(GuiGraphics g, int w, int h, float alpha, int rgb) {
        int a = (int) (clamp01(alpha) * 250);
        if (a > 0) g.fill(0, 0, w, h, (a << 24) | rgb);
    }

    private static float smoothstep(float v) {
        v = clamp01(v);
        return v * v * (3 - 2 * v);
    }

    private static int lerpColor(int a, int b, float k) {
        int r = (int) Mth.lerp(k, a >> 16 & 255, b >> 16 & 255), gg = (int) Mth.lerp(k, a >> 8 & 255, b >> 8 & 255), bb = (int) Mth.lerp(k, a & 255, b & 255);
        return r << 16 | gg << 8 | bb;
    }

    /** 落地前两秒视野慢慢收窄，星显得越压越近；撞击那拍猛地拉宽再弹回。跟随“视场角效果”设置。 */
    @SubscribeEvent public static void fov(ViewportEvent.ComputeFov event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !event.usedConfiguredFov()) return;
        float scale = mc.options.fovEffectScale().get().floatValue();
        if (scale <= 0) return;
        float pt = (float) event.getPartialTick();
        double mul = 1;
        for (SkyMeteorEntity m : meteors(mc, event.getCamera().getPosition())) {
            float since = m.tickCount + pt - HIT;
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
}
