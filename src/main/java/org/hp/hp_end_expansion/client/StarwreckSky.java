package org.hp.hp_end_expansion.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import org.hp.hp_end_expansion.client.render.HpSkyDraw;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import org.hp.hp_end_expansion.client.render.HpSkyMesh;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.hp.hp_end_expansion.Hp_end_expansion;
import org.hp.hp_end_expansion.worldgen.StarwreckWorldgen;
import org.joml.Matrix4f;

/**
 * 星骸荒原的天空「残星天幕」：天幕从地平线的余烬褐红褪到天顶的靛黑；一条倾斜的星骸带横跨天空，
 * 碎块剪影朝地平线那侧描着余烬边；地平线上一缕缕热帘摇曳，余烬从天顶慢慢飘落，零星几颗垂死的星猛地一亮随即熄灭。
 * <p>
 * 纯客户端。渲染和雾色都先于 {@link StarfallSky}，星雨、落地闪光、注视压暗照旧叠在上面。
 * {@code /hp_starsky} 切换预览，不需要站在星骸荒原里。
 */
@EventBusSubscriber(modid = Hp_end_expansion.MODID, value = Dist.CLIENT)
public final class StarwreckSky {
    private static final float R = 90;
    private static final Vec3 UP = new Vec3(0, 1, 0);
    // 星骸带：所在大圆的法线仰角 35°，带的最高处约 55°
    private static final float BELT_HALF = 0.16F;
    private static final Vec3 BELT_N = sky(4.05F, (float) Math.toRadians(35)).normalize();
    private static final Vec3 BELT_E1 = UP.cross(BELT_N).normalize(), BELT_E2 = BELT_N.cross(BELT_E1).normalize();

    private static HpSkyMesh staticSkyBuffer;
    private static float presence, presenceO;
    private static boolean preview;
    private static long ticks;

    private StarwreckSky() {}

    @SubscribeEvent public static void commands(RegisterClientCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("hp_starsky").executes(ctx -> {
            // 切换预览开关
            preview = !preview;
            String state;
            if (preview) state = "开";
            else state = "关";
            ctx.getSource().sendSuccess(() -> Component.literal("残星天幕预览：" + state), false);
            return 1;
        }));
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        presenceO = presence;
        // 退出世界时清空
        if (level == null || player == null) {
            presence = 0;
            presenceO = 0;
            return;
        }
        if (mc.isPaused()) return;
        ticks++;
        // 只在末地的星骸荒原里出现，预览不限地点
        boolean on = preview || (level.dimension() == Level.END && level.getBiome(player.blockPosition()).is(StarwreckWorldgen.BIOME));
        // 来得慢（约 6 秒铺满）、退得更慢
        if (on) presence = Math.min(1, presence + 0.008F);
        else presence = Math.max(0, presence - 0.004F);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void render(RenderLevelStageEvent.AfterSky e) {
        float pt = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float L = Mth.lerp(pt, presenceO, presence);
        if (L < 0.003F) return;
        L = L * L * (3 - 2 * L);
        Matrix4f m = new Matrix4f(e.getModelViewMatrix());
        float t = ticks + pt;
        Tesselator tess = Tesselator.getInstance();



        // 1. 静态天幕：底色渐变、星骸带尘光、暗淡的远星，缓存在顶点缓冲里
        HpSkyDraw.normal();
        HpSkyDraw.colored();
        ensureStaticSkyBuffer(tess);
        if (staticSkyBuffer != null) {
            HpSkyDraw.color(1, 1, 1, L);

            staticSkyBuffer.draw(m);

            HpSkyDraw.color(1, 1, 1, 1);
        }

        // 2. 加法：地平线热帘、垂死的星
        HpSkyDraw.additive();
        HpSkyDraw.colored();
        BufferBuilder b = tess.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        curtains(b, m, t, L);
        dyingStars(b, m, t, L);
        draw(b);

        // 3. 普通混合：星骸带碎块的剪影，挡住身后的光
        HpSkyDraw.normal();
        b = tess.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        debris(b, m, t, L, false);
        draw(b);

        // 4. 加法：碎块朝地平线那侧的余烬边、飘落的余烬
        HpSkyDraw.additive();
        b = tess.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        debris(b, m, t, L, true);
        embers(b, m, t, L);
        draw(b);

        // 还原渲染状态
        HpSkyDraw.normal();



    }

    // ------------------------------------------------------------------ 静态天幕

    private static void ensureStaticSkyBuffer(Tesselator tess) {
        if (staticSkyBuffer != null && !staticSkyBuffer.isInvalid()) return;
        if (staticSkyBuffer != null) staticSkyBuffer.close();
        staticSkyBuffer = new HpSkyMesh();
        // 底色、尘光、远星一次性烘进顶点缓冲
        BufferBuilder b = tess.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f identity = new Matrix4f();
        dome(b, identity);
        beltDust(b, identity);
        starField(b, identity);
        MeshData mesh = b.build();
        if (mesh == null) {
            staticSkyBuffer.close();
            staticSkyBuffer = null;
            return;
        }

        staticSkyBuffer.upload(mesh);

    }

    /** 高度角(度)对应的底色：地平线是余烬褐红，往上褪成暗紫，天顶近乎靛黑。 */
    private static float[] ember(float el) {
        float[][] keys = {{-90, 0.018F, 0.010F, 0.012F, 0.97F}, {-20, 0.035F, 0.016F, 0.015F, 0.96F}, {0, 0.115F, 0.045F, 0.030F, 0.95F},
            {8, 0.090F, 0.036F, 0.034F, 0.94F}, {25, 0.056F, 0.026F, 0.044F, 0.93F}, {55, 0.030F, 0.018F, 0.050F, 0.92F},
            {90, 0.016F, 0.012F, 0.036F, 0.92F}};
        // 在相邻两档之间插值
        for (int i = 1; i < keys.length; i++) {
            if (el <= keys[i][0]) {
                float k = (el - keys[i - 1][0]) / (keys[i][0] - keys[i - 1][0]);
                float[] o = new float[4];
                for (int j = 0; j < 4; j++) o[j] = Mth.lerp(k, keys[i - 1][j + 1], keys[i][j + 1]);
                return o;
            }
        }
        float[] top = keys[keys.length - 1];
        return new float[] {top[1], top[2], top[3], top[4]};
    }

    private static void dome(BufferBuilder b, Matrix4f m) {
        int az = 48;
        float[] els = {-90, -20, -6, 0, 4, 8, 15, 25, 40, 55, 72, 90};
        // 按高度分圈铺满整个球面
        for (int i = 0; i + 1 < els.length; i++) {
            float[] c0 = ember(els[i]), c1 = ember(els[i + 1]);
            float e0 = (float) Math.toRadians(els[i]), e1 = (float) Math.toRadians(els[i + 1]);
            for (int j = 0; j < az; j++) {
                float a0 = j * Mth.TWO_PI / az, a1 = (j + 1) * Mth.TWO_PI / az;
                quadC(b, m, sky(a0, e0), sky(a1, e0), sky(a1, e1), sky(a0, e1), c0, c0, c1, c1);
            }
        }
    }

    /** 星骸带的尘光：沿倾斜大圆铺开的柔光带，中线偏暖、两侧偏紫，浓淡沿带起伏，地平线以下淡掉。 */
    private static void beltDust(BufferBuilder b, Matrix4f m) {
        int n = 120;
        float[] off = {-1, -0.5F, -0.15F, 0, 0.15F, 0.5F, 1};
        float[] ka = {0, 0.4F, 0.85F, 1, 0.85F, 0.4F, 0};
        for (int i = 0; i < n; i++) {
            float th0 = i * Mth.TWO_PI / n, th1 = (i + 1) * Mth.TWO_PI / n;
            // 带宽和浓淡沿带起伏
            float w0 = BELT_HALF * (0.75F + 0.25F * Mth.sin(th0 * 3 + 1)), w1 = BELT_HALF * (0.75F + 0.25F * Mth.sin(th1 * 3 + 1));
            float d0 = dust(th0), d1 = dust(th1);
            // 横截面分档，边缘化开
            for (int k = 0; k + 1 < off.length; k++) {
                Vec3 n00 = belt(th0, off[k] * w0), n10 = belt(th1, off[k] * w1);
                Vec3 n01 = belt(th0, off[k + 1] * w0), n11 = belt(th1, off[k + 1] * w1);
                quadC(b, m, n00.scale(R - 1), n10.scale(R - 1), n11.scale(R - 1), n01.scale(R - 1),
                    dustColor(n00, d0 * ka[k], off[k]), dustColor(n10, d1 * ka[k], off[k]),
                    dustColor(n11, d1 * ka[k + 1], off[k + 1]), dustColor(n01, d0 * ka[k + 1], off[k + 1]));
            }
        }
    }

    private static float dust(float th) {
        return 0.55F + 0.25F * Mth.sin(th * 3 + 0.7F) + 0.2F * Mth.sin(th * 7 + 2.1F);
    }

    private static float[] dustColor(Vec3 n, float a, float off) {
        float warm = 1 - Math.abs(off);
        return new float[] {Mth.lerp(warm, 0.22F, 0.36F), Mth.lerp(warm, 0.10F, 0.17F), Mth.lerp(warm, 0.17F, 0.14F), 0.17F * a * horizonFade(n)};
    }

    /** 暗淡的远星：一半散满全天，一半挤在星骸带附近，偏暖白、琥珀和淡蓝三种。 */
    private static void starField(BufferBuilder b, Matrix4f m) {
        float[][] cols = {{1, 0.92F, 0.8F}, {1, 0.74F, 0.5F}, {0.86F, 0.86F, 1}};
        for (int i = 0; i < 700; i++) {
            // 选位置
            Vec3 n;
            if (i % 2 == 0) n = sky(hash(i, 101) * Mth.TWO_PI, (float) Math.asin(-0.03F + 1.03F * hash(i, 102))).normalize();
            else n = belt(hash(i, 105) * Mth.TWO_PI, (hash(i, 103) + hash(i, 104) - 1) * BELT_HALF * 1.3F);
            float a = (0.18F + 0.55F * hash(i, 106) * hash(i, 106)) * horizonFade(n);
            if (a < 0.02F) continue;
            // 画一个朝向视点的小方点
            float[] c = cols[(int) (hash(i, 108) * 2.99F)];
            float s = 0.09F + 0.13F * hash(i, 107) * hash(i, 107);
            Vec3 p = n.scale(R - 3);
            Vec3[] xy = basis(n);
            Vec3 x = xy[0].scale(s), y = xy[1].scale(s);
            Vec3 q0 = p.subtract(x).subtract(y), q1 = p.add(x).subtract(y), q2 = p.add(x).add(y), q3 = p.subtract(x).add(y);
            tri(b, m, q0, c[0], c[1], c[2], a, q1, c[0], c[1], c[2], a, q2, c[0], c[1], c[2], a);
            tri(b, m, q0, c[0], c[1], c[2], a, q2, c[0], c[1], c[2], a, q3, c[0], c[1], c[2], a);
        }
    }

    // ------------------------------------------------------------------ 动态层

    /** 地平线热帘：一缕缕从地平线升起的余烬热光，上端左右摇曳、明暗起伏。 */
    private static void curtains(BufferBuilder b, Matrix4f m, float t, float L) {
        int n = 30, steps = 6;
        float bottom = (float) Math.toRadians(-3);
        for (int i = 0; i < n; i++) {
            // 每缕热帘的方位、宽窄、高度各不相同
            float az = i * Mth.TWO_PI / n + 0.25F * Mth.sin(i * 2.3F) + 0.04F * Mth.sin(t * 0.005F + i * 1.7F);
            float half = 0.03F + 0.05F * hash(i, 31);
            float top = (float) Math.toRadians(10 + 18 * hash(i, 32));
            // 两道快慢不同的闪动相乘
            float flick = (0.5F + 0.5F * Mth.sin(t * (0.025F + 0.04F * hash(i, 33)) + i * 1.3F)) * (0.6F + 0.4F * Mth.sin(t * 0.009F + i * 0.7F));
            float base = 0.09F * L * (0.3F + 0.7F * flick);
            if (base < 0.003F) continue;
            float sway = 0.05F * Mth.sin(t * 0.017F + i * 2.9F);
            // 一缕拆成左右两半，中线最亮；越往上越窄、越偏暗红
            for (int s = 0; s < steps; s++) {
                float k0 = (float) s / steps, k1 = (float) (s + 1) / steps;
                float e0 = Mth.lerp(k0, bottom, top), e1 = Mth.lerp(k1, bottom, top);
                float x0 = az + sway * k0 * k0, x1 = az + sway * k1 * k1;
                float w0 = half * (1 - 0.4F * k0), w1 = half * (1 - 0.4F * k1);
                float a0 = base * heatProfile(k0), a1 = base * heatProfile(k1);
                float g0 = Mth.lerp(k0, 0.45F, 0.16F), g1 = Mth.lerp(k1, 0.45F, 0.16F);
                float b0 = Mth.lerp(k0, 0.12F, 0.1F), b1 = Mth.lerp(k1, 0.12F, 0.1F);
                Vec3 c0 = sky(x0, e0), c1 = sky(x1, e1);
                Vec3 l0 = sky(x0 - w0, e0), l1 = sky(x1 - w1, e1), r0 = sky(x0 + w0, e0), r1 = sky(x1 + w1, e1);
                tri(b, m, l0, 1, g0, b0, 0, c0, 1, g0, b0, a0, c1, 1, g1, b1, a1);
                tri(b, m, l0, 1, g0, b0, 0, c1, 1, g1, b1, a1, l1, 1, g1, b1, 0);
                tri(b, m, c0, 1, g0, b0, a0, r0, 1, g0, b0, 0, r1, 1, g1, b1, 0);
                tri(b, m, c0, 1, g0, b0, a0, r1, 1, g1, b1, 0, c1, 1, g1, b1, a1);
            }
        }
    }

    private static float heatProfile(float k) {
        return Mth.clamp(k * 5, 0, 1) * (float) Math.pow(1 - k, 1.3);
    }

    /** 垂死的星：平时轻轻闪烁，寿命将尽时猛地一亮、拉出十字星芒、转成橙红，随即坍缩熄灭，过一阵在别处重新亮起。 */
    private static void dyingStars(BufferBuilder b, Matrix4f m, float t, float L) {
        for (int i = 0; i < 70; i++) {
            float period = 500 + 700 * hash(i, 41);
            float cyc = (t + period * hash(i, 42)) / period;
            int c = Mth.floor(cyc);
            float k = cyc - c;
            // 每个周期换一个位置
            int id = i * 53 + c;
            float az = hash(id, 43) * Mth.TWO_PI;
            float el = (float) Math.asin(0.06F + 0.92F * hash(id, 44));
            float base = 0.35F + 0.4F * hash(id, 45);
            float size = 0.22F + 0.2F * hash(id, 46);
            float a;
            float s = size, spike = 0;
            // 生命周期：亮起、闪烁、回光、坍缩、熄灭
            if (k < 0.08F) {
                a = base * k / 0.08F;
            } else if (k < 0.8F) {
                a = base * (0.6F + 0.4F * Mth.sin(t * (0.07F + 0.1F * hash(i, 47)) + i));
            } else if (k < 0.87F) {
                float f = (k - 0.8F) / 0.07F;
                a = base * (1 + 1.8F * f);
                s = size * (1 + f);
                spike = f;
            } else if (k < 0.92F) {
                float f = (k - 0.87F) / 0.05F;
                a = base * 2.8F * (1 - f) * (1 - f);
                s = size * (2 - 1.6F * f);
                spike = 1 - f;
            } else {
                continue;
            }
            a *= L;
            if (a < 0.01F) continue;
            // 星点，越接近死亡越偏橙红
            Vec3 p = sky(az, el).scale((R - 4) / R);
            Vec3[] xy = basis(p);
            float g = Mth.lerp(spike, 0.9F, 0.6F), bl = Mth.lerp(spike, 0.78F, 0.3F);
            glow(b, m, p, xy[0].scale(s), xy[1].scale(s), 1, g, bl, a);
            // 回光时的十字星芒
            if (spike > 0.02F) {
                float len = s * (2 + 4 * spike), thin = s * 0.18F;
                glow(b, m, p, xy[0].scale(len), xy[1].scale(thin), 1, g, bl, a * 0.6F);
                glow(b, m, p, xy[0].scale(thin), xy[1].scale(len), 1, g, bl, a * 0.6F);
            }
        }
    }

    /** 星骸带碎块：沿带缓慢漂移、各自慢慢翻转，越靠中线越密；剪影朝地平线那侧描余烬边，越低越亮。 */
    private static void debris(BufferBuilder b, Matrix4f m, float t, float L, boolean rim) {
        for (int i = 0; i < 84; i++) {
            float theta = hash(i, 61) * Mth.TWO_PI + t * (0.00006F + 0.0001F * hash(i, 62));
            float w = (hash(i, 63) + hash(i, 64) - 1) * BELT_HALF;
            Vec3 n = belt(theta, w);
            // 地平线以下的碎块淡掉
            float fade = horizonFade(n);
            if (fade < 0.01F) continue;
            // 少数几块特别大
            float size = 0.45F + 1.5F * hash(i, 65) * hash(i, 65);
            if (i < 6) size = 2.4F + 1.6F * hash(i, 66);
            Vec3 p = n.scale(R - 6);
            Vec3[] xy = basis(n);
            float spin = hash(i, 67) * Mth.TWO_PI + t * 0.0015F * (hash(i, 68) - 0.5F);
            if (!rim) {
                rock(b, m, p, xy[0].scale(size), xy[1].scale(size), i, spin, 0, 0, 0.95F * L * fade, false);
                continue;
            }
            // 光从下方的地平线余烬照上来，越低越亮
            float low = 1 - 0.7F * Mth.clamp((float) n.y, 0, 1);
            float a = L * fade * 0.45F * low * low;
            rock(b, m, p, xy[0].scale(size), xy[1].scale(size), i, spin, 0, -1, a, true);
        }
    }

    /** 一块碎石：5~7 边的不规则多边形。rim 为假时画暗色剪影，为真时只画朝 (lx,ly) 那侧的余烬描边。 */
    private static void rock(BufferBuilder b, Matrix4f m, Vec3 c, Vec3 x, Vec3 y, int salt, float spin, float lx, float ly,
                             float a, boolean rim) {
        int sides = 5 + (int) (hash(salt, 71) * 2.99F);
        float[] px = new float[sides], py = new float[sides];
        // 顶点角度和半径都带一点随机，轮廓才像碎石
        for (int j = 0; j < sides; j++) {
            float ang = spin + (j + (hash(salt * 13 + j, 72) - 0.5F) * 0.6F) * Mth.TWO_PI / sides;
            float rad = 0.55F + 0.45F * hash(salt * 13 + j, 73);
            px[j] = Mth.cos(ang) * rad;
            py[j] = Mth.sin(ang) * rad;
        }
        for (int j = 0; j < sides; j++) {
            int q = (j + 1) % sides;
            Vec3 p0 = at(c, x, y, px[j], py[j]), p1 = at(c, x, y, px[q], py[q]);
            // 暗色剪影：中心略亮，边缘最暗
            if (!rim) {
                tri(b, m, c, 0.03F, 0.017F, 0.016F, a, p0, 0.012F, 0.008F, 0.01F, a, p1, 0.012F, 0.008F, 0.01F, a);
                continue;
            }
            // 受光边：顶点朝向和光照方向越一致越亮，从边缘往里淡掉
            float l0 = lit(px[j], py[j], lx, ly) * a, l1 = lit(px[q], py[q], lx, ly) * a;
            if (l0 + l1 < 0.004F) continue;
            Vec3 n0 = at(c, x, y, px[j] * 0.72F, py[j] * 0.72F), n1 = at(c, x, y, px[q] * 0.72F, py[q] * 0.72F);
            tri(b, m, n0, 1, 0.42F, 0.12F, 0, p0, 1, 0.62F, 0.25F, l0, p1, 1, 0.62F, 0.25F, l1);
            tri(b, m, n0, 1, 0.42F, 0.12F, 0, p1, 1, 0.62F, 0.25F, l1, n1, 1, 0.42F, 0.12F, 0);
        }
    }

    private static float lit(float px, float py, float lx, float ly) {
        float len = Mth.sqrt(px * px + py * py);
        if (len < 1.0E-4F) return 0;
        float d = (px * lx + py * ly) / len;
        if (d <= 0) return 0;
        return d * d;
    }

    /** 飘落的余烬：从天顶附近往地平线慢慢落，左右轻轻飘、忽明忽暗，上方拖一小段余光。 */
    private static void embers(BufferBuilder b, Matrix4f m, float t, float L) {
        float[][] cols = {{1, 0.55F, 0.16F}, {1, 0.78F, 0.38F}, {0.95F, 0.32F, 0.1F}, {1, 0.9F, 0.65F}};
        for (int i = 0; i < 160; i++) {
            // 位置：高度一路往下落，落到地平线下再从顶上重来
            float az = hash(i, 91) * Mth.TWO_PI + 0.035F * Mth.sin(t * 0.006F + i * 0.9F);
            float span = 90, el = 80 - ((hash(i, 92) * span + t * (0.006F + 0.016F * hash(i, 93))) % span);
            float life = Mth.clamp((80 - el) / 12, 0, 1) * Mth.clamp((el + 10) / 14, 0, 1);
            float flick = 0.5F + 0.5F * Mth.sin(t * (0.08F + 0.12F * hash(i, 94)) + i * 1.7F);
            float a = L * life * (0.3F + 0.7F * flick) * (0.35F + 0.45F * hash(i, 95));
            if (a < 0.01F) continue;
            float[] c = cols[(int) (hash(i, 96) * 3.99F)];
            Vec3 p = sky(az, (float) Math.toRadians(el)).scale((R - 12) / R);
            Vec3[] xy = basis(p);
            float s = 0.16F + 0.3F * hash(i, 97);
            // 亮点
            glow(b, m, p, xy[0].scale(s), xy[1].scale(s), c[0], c[1], c[2], a);
            // 上方的余光，看得出在往下落
            glow(b, m, p.add(xy[1].scale(s * 1.8F)), xy[0].scale(s * 0.45F), xy[1].scale(s * 2), c[0], c[1], c[2], a * 0.35F);
        }
    }

    // ------------------------------------------------------------------ 工具

    private static float hash(int i, int salt) {
        int h = i * 374761393 + salt * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xFFFF) / 65535F;
    }

    private static Vec3 sky(float az, float el) {
        return new Vec3(Mth.cos(el) * Mth.sin(az) * R, Mth.sin(el) * R, Mth.cos(el) * Mth.cos(az) * R);
    }

    /** 星骸带上的单位方向：theta 沿带绕一圈，w 是偏离中线的角度。 */
    private static Vec3 belt(float theta, float w) {
        return BELT_E1.scale(Mth.cos(theta)).add(BELT_E2.scale(Mth.sin(theta))).scale(Mth.cos(w)).add(BELT_N.scale(Mth.sin(w)));
    }

    private static float horizonFade(Vec3 n) {
        return Mth.clamp(((float) n.normalize().y + 0.03F) / 0.12F, 0, 1);
    }

    /** 朝向视点的切平面基：x 水平，y 朝天顶一侧。 */
    private static Vec3[] basis(Vec3 n) {
        Vec3 x = n.cross(UP);
        if (x.lengthSqr() < 1.0E-6) x = new Vec3(1, 0, 0);
        x = x.normalize();
        return new Vec3[] {x, x.cross(n).normalize()};
    }

    private static Vec3 at(Vec3 c, Vec3 x, Vec3 y, float u, float v) {
        return c.add(x.scale(u)).add(y.scale(v));
    }

    /** 柔光菱形：中心最亮、四个角淡到 0；x、y 是两条半轴。 */
    private static void glow(BufferBuilder b, Matrix4f m, Vec3 p, Vec3 x, Vec3 y, float r, float g, float bl, float a) {
        Vec3 q0 = p.add(x), q1 = p.add(y), q2 = p.subtract(x), q3 = p.subtract(y);
        tri(b, m, p, r, g, bl, a, q0, r, g, bl, 0, q1, r, g, bl, 0);
        tri(b, m, p, r, g, bl, a, q1, r, g, bl, 0, q2, r, g, bl, 0);
        tri(b, m, p, r, g, bl, a, q2, r, g, bl, 0, q3, r, g, bl, 0);
        tri(b, m, p, r, g, bl, a, q3, r, g, bl, 0, q0, r, g, bl, 0);
    }

    private static void quadC(BufferBuilder b, Matrix4f m, Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3,
                              float[] c0, float[] c1, float[] c2, float[] c3) {
        tri(b, m, p0, c0[0], c0[1], c0[2], c0[3], p1, c1[0], c1[1], c1[2], c1[3], p2, c2[0], c2[1], c2[2], c2[3]);
        tri(b, m, p0, c0[0], c0[1], c0[2], c0[3], p2, c2[0], c2[1], c2[2], c2[3], p3, c3[0], c3[1], c3[2], c3[3]);
    }

    private static void tri(BufferBuilder b, Matrix4f m, Vec3 p0, float r0, float g0, float b0, float a0,
                            Vec3 p1, float r1, float g1, float b1, float a1, Vec3 p2, float r2, float g2, float b2, float a2) {
        b.addVertex(m, (float) p0.x, (float) p0.y, (float) p0.z).setColor(r0, g0, b0, a0);
        b.addVertex(m, (float) p1.x, (float) p1.y, (float) p1.z).setColor(r1, g1, b1, a1);
        b.addVertex(m, (float) p2.x, (float) p2.y, (float) p2.z).setColor(r2, g2, b2, a2);
    }

    private static void draw(BufferBuilder b) {
        MeshData mesh = b.build();
        if (mesh != null) HpSkyDraw.draw(mesh);
    }

    /** 远处地形的雾也染成余烬暗褐，天地接得上。 */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void fogColor(ViewportEvent.ComputeFogColor e) {
        float pt = (float) e.getPartialTick();
        float L = Mth.lerp(pt, presenceO, presence);
        if (L < 0.001F) return;
        e.setRed(Mth.lerp(L, e.getRed(), 0.1F));
        e.setGreen(Mth.lerp(L, e.getGreen(), 0.045F));
        e.setBlue(Mth.lerp(L, e.getBlue(), 0.035F));
    }
}
