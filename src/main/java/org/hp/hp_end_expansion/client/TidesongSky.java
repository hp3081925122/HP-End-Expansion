package org.hp.hp_end_expansion.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.hp.hp_end_expansion.Hp_end_expansion;
import org.hp.hp_end_expansion.worldgen.tidelight.TidelightWorldgen;
import org.joml.Matrix4f;

/**
 * 鸣潮巨鲸在场时的天空「倒悬之海」：整片天变成从海底仰望的深海。
 * 天顶是滚动的海面波光，光柱从天顶斜射下来；海面上一圈圈涟漪荡开，几缕洋流光丝横跨天空；
 * 巨鲸每唱一段，就有一圈带菱形节点的声纹环从它的方向向整片天荡开；气泡和浮游荧光一路往天顶升。
 * <p>
 * 纯客户端：按实体注册名 {@code hp_end_expansion:tidesong_whale} 找巨鲸，实体类写好之前不需要改这里。
 * 鲸歌技能可调用 {@link #songPulse(Vec3)} 让声纹环和技能同步。{@code /hp_tidesky} 切换预览，不需要巨鲸在场。
 */
@EventBusSubscriber(modid = Hp_end_expansion.MODID, value = Dist.CLIENT)
public final class TidesongSky {
    private static final float R = 90;
    private static final ResourceLocation RING = tex("song_ring");
    /** 没有技能同步时，每隔多少 tick 自动唱一段。 */
    private static final int SONG_EVERY = 150, RING_LIFE = 110;

    private static final List<Ring> RINGS = new ArrayList<>();
    private static VertexBuffer staticSkyBuffer;
    private static float presence, presenceO;
    private static boolean preview;
    private static long ticks;
    private static int songTimer = 40;

    private TidesongSky() {}

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(Hp_end_expansion.MODID, "textures/environment/tidesong/" + name + ".png");
    }

    private record Ring(Vec3 dir, int born, float strength) {}

    /** 鲸歌：从世界坐标 {@code from} 的方向荡开一圈声纹环（两道，一强一弱）。只在客户端调用。 */
    public static void songPulse(Vec3 from) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameRenderer == null || mc.level == null) return;
        Vec3 d = from.subtract(mc.gameRenderer.getMainCamera().getPosition());
        if (d.lengthSqr() < 1.0E-4) d = new Vec3(0, 1, 0);
        emit(d.normalize());
    }

    private static void emit(Vec3 dir) {
        RINGS.add(new Ring(dir, (int) ticks, 1));
        RINGS.add(new Ring(dir, (int) ticks + 14, 0.55F));
        songTimer = SONG_EVERY;
    }

    @SubscribeEvent public static void commands(RegisterClientCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("hp_tidesky").executes(ctx -> {
            preview = !preview;
            ctx.getSource().sendSuccess(() -> Component.literal("倒悬之海预览：" + (preview ? "开" : "关")), false);
            return 1;
        }));
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        presenceO = presence;
        if (level == null || player == null) {
            presence = presenceO = 0;
            RINGS.clear();
            return;
        }
        if (mc.isPaused()) return;
        ticks++;
        boolean on = preview || level.getBiome(player.blockPosition()).is(TidelightWorldgen.BIOME);
        // 来得慢（约 6 秒铺满）、退得更慢
        presence = on ? Math.min(1, presence + 0.008F) : Math.max(0, presence - 0.004F);
        if (on && presence > 0.4F && --songTimer <= 0) {
            Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
            Vec3 from = sky(ticks * 0.002F, 0.9F).subtract(cam);
            emit(from.lengthSqr() < 1.0E-4 ? new Vec3(0, 1, 0) : from.normalize());
        }
        for (Iterator<Ring> it = RINGS.iterator(); it.hasNext(); ) if (ticks - it.next().born > RING_LIFE) it.remove();
    }

    @SubscribeEvent public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) return;
        float pt = e.getPartialTick().getGameTimeDeltaPartialTick(false);
        float L = Mth.lerp(pt, presenceO, presence);
        if (L < 0.003F) return;
        L = L * L * (3 - 2 * L);
        Matrix4f m = new Matrix4f(e.getModelViewMatrix());
        float t = ticks + pt;
        Tesselator tess = Tesselator.getInstance();

        RenderSystem.enableBlend();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();

        // 1. 深海底色：地平线近黑的深蓝，越往天顶越亮越青（和"中心一个黑洞旋涡"正好相反）
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        ensureStaticSkyBuffer(tess);
        if (staticSkyBuffer != null) {
            RenderSystem.setShaderColor(1, 1, 1, L);
            staticSkyBuffer.bind();
            staticSkyBuffer.drawWithShader(m, RenderSystem.getProjectionMatrix(), GameRenderer.getPositionColorShader());
            VertexBuffer.unbind();
            RenderSystem.setShaderColor(1, 1, 1, 1);
        }

        // 2. 加法：天顶亮斑、海面上一圈圈荡开的涟漪、从海面斜射下来的光柱
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder b = tess.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        ripples(b, m, t, L);
        rays(b, m, t, L);
        draw(b);

        // 3. 加法：横跨天空的几道潮光：没有贴图的柔光带，一阵阵亮波顺流推过去，中线上有几颗流光顺流滑行
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        b = tess.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        current(b, m, t, 0.42F, 0.20F, 0.0F, 1.0F, L);
        current(b, m, t, 0.18F, 0.15F, 2.1F, 0.7F, 0.8F * L);
        current(b, m, t, 0.75F, 0.12F, 4.0F, 1.3F, 0.65F * L);
        draw(b);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);

        // 5. 加法：鲸歌声纹环
        if (!RINGS.isEmpty()) {
            RenderSystem.setShaderTexture(0, RING);
            b = tess.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_TEX_COLOR);
            for (Ring r : RINGS) ring(b, m, r, t, L);
            draw(b);
        }

        // 6. 加法：往天顶升的气泡和浮游荧光
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        b = tess.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        motes(b, m, t, L);
        draw(b);

        RenderSystem.defaultBlendFunc();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    // ------------------------------------------------------------------ 各层

    private static void ensureStaticSkyBuffer(Tesselator tess) {
        if (staticSkyBuffer != null && !staticSkyBuffer.isInvalid()) return;
        if (staticSkyBuffer != null) staticSkyBuffer.close();
        staticSkyBuffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        BufferBuilder b = tess.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f identity = new Matrix4f();
        dome(b, identity, 1);
        glare(b, identity, 1);
        MeshData mesh = b.build();
        if (mesh == null) {
            staticSkyBuffer.close();
            staticSkyBuffer = null;
            return;
        }
        staticSkyBuffer.bind();
        staticSkyBuffer.upload(mesh);
        VertexBuffer.unbind();
    }

    /** 高度角(度)对应的底色：地平线下 d0，往上 d1、d2，天顶带一点潮光青。 */
    private static float[] deep(float el) {
        float[][] keys = {{-40, 0.02F, 0.06F, 0.09F, 0.96F}, {0, 0.03F, 0.10F, 0.14F, 0.95F}, {25, 0.05F, 0.17F, 0.22F, 0.93F},
            {60, 0.08F, 0.27F, 0.33F, 0.9F}, {90, 0.10F, 0.40F, 0.43F, 0.88F}};
        for (int i = 1; i < keys.length; i++) {
            if (el <= keys[i][0]) {
                float k = (el - keys[i - 1][0]) / (keys[i][0] - keys[i - 1][0]);
                float[] o = new float[4];
                for (int j = 0; j < 4; j++) o[j] = Mth.lerp(k, keys[i - 1][j + 1], keys[i][j + 1]);
                return o;
            }
        }
        return new float[] {keys[4][1], keys[4][2], keys[4][3], keys[4][4]};
    }

    private static void dome(BufferBuilder b, Matrix4f m, float L) {
        int az = 48;
        float[] els = {-90, -40, -15, 0, 12, 25, 40, 60, 75, 90};
        for (int i = 0; i + 1 < els.length; i++) {
            float[] c0 = deep(els[i]), c1 = deep(els[i + 1]);
            float e0 = (float) Math.toRadians(els[i]), e1 = (float) Math.toRadians(els[i + 1]);
            for (int j = 0; j < az; j++) {
                float a0 = j * Mth.TWO_PI / az, a1 = (j + 1) * Mth.TWO_PI / az;
                Vec3 p00 = sky(a0, e0), p10 = sky(a1, e0), p01 = sky(a0, e1), p11 = sky(a1, e1);
                quadC(b, m, p00, p10, p11, p01, c0, c0, c1, c1, L);
            }
        }
    }

    /**
     * 海面涟漪：9 路涟漪各按自己的周期，在海面上随机一点冒出来，一圈主环加一圈慢半拍的小环向外荡开、变淡。
     * 位置每个周期按哈希换一次（固定可复现，不是逐帧随机），只画在海面范围内。
     */
    private static void ripples(BufferBuilder b, Matrix4f m, float t, float L) {
        float maxPlane = Mth.sin((float) Math.toRadians(48));
        for (int i = 0; i < 9; i++) {
            float period = 70 + 50 * hash(i, 11);
            float cyc = (t + period * hash(i, 12)) / period;
            int c = Mth.floor(cyc);
            float k = cyc - c;
            float rr = maxPlane * Mth.sqrt(hash(i * 31 + c, 13)), ang = Mth.TWO_PI * hash(i * 31 + c, 14);
            float cx = rr * Mth.cos(ang), cz = rr * Mth.sin(ang);
            float edge = fade((float) Math.asin(Math.min(1, rr)), (float) Math.toRadians(55));
            float a = L * 0.32F * edge * (float) Math.pow(1 - k, 1.6) * Mth.clamp(k * 12, 0, 1);
            if (a < 0.004F) continue;
            ripple(b, m, cx, cz, 0.012F + 0.17F * k, 0.004F + 0.012F * k, a);
            if (k > 0.18F) ripple(b, m, cx, cz, 0.012F + 0.17F * (k - 0.18F), 0.003F + 0.008F * k, a * 0.55F);
        }
    }

    /** 海面上的一圈环：在水平面上画圆，再抬到天穹上。环带中线最亮，内外两侧淡到 0。 */
    private static void ripple(BufferBuilder b, Matrix4f m, float cx, float cz, float radius, float half, float a) {
        int n = 40;
        float cr = 0.42F, cg = 0.95F, cb = 0.88F;
        for (int j = 0; j < n; j++) {
            float p0 = j * Mth.TWO_PI / n, p1 = (j + 1) * Mth.TWO_PI / n;
            Vec3 i0 = plane(cx, cz, radius - half, p0), i1 = plane(cx, cz, radius - half, p1);
            Vec3 m0 = plane(cx, cz, radius, p0), m1 = plane(cx, cz, radius, p1);
            Vec3 o0 = plane(cx, cz, radius + half, p0), o1 = plane(cx, cz, radius + half, p1);
            if (i0 == null || i1 == null || m0 == null || m1 == null || o0 == null || o1 == null) continue;
            tri(b, m, i0, cr, cg, cb, 0, i1, cr, cg, cb, 0, m1, cr, cg, cb, a);
            tri(b, m, i0, cr, cg, cb, 0, m1, cr, cg, cb, a, m0, cr, cg, cb, a);
            tri(b, m, m0, cr, cg, cb, a, m1, cr, cg, cb, a, o1, cr, cg, cb, 0);
            tri(b, m, m0, cr, cg, cb, a, o1, cr, cg, cb, 0, o0, cr, cg, cb, 0);
        }
    }

    private static Vec3 plane(float cx, float cz, float radius, float phase) {
        float x = cx + radius * Mth.cos(phase), z = cz + radius * Mth.sin(phase);
        float y2 = 1 - x * x - z * z;
        if (y2 <= 0) return null;
        return new Vec3(x, Mth.sqrt(y2), z).scale(R - 1);
    }

    private static float fade(float rho, float max) {
        float k = Mth.clamp((max - rho) / (max * 0.55F), 0, 1);
        return k * k * (3 - 2 * k);
    }

    private static void glare(BufferBuilder b, Matrix4f m, float L) {
        int n = 32;
        float rho = (float) Math.toRadians(16);
        Vec3 top = new Vec3(0, R, 0);
        for (int j = 0; j < n; j++) {
            float a0 = j * Mth.TWO_PI / n, a1 = (j + 1) * Mth.TWO_PI / n;
            tri(b, m, top, 0.7F, 1, 0.92F, 0.32F * L, sky(a0, Mth.HALF_PI - rho), 0.3F, 0.8F, 0.75F, 0,
                sky(a1, Mth.HALF_PI - rho), 0.3F, 0.8F, 0.75F, 0);
        }
    }

    /** 光柱：从天顶附近沿经线垂到地平线，经线在天顶汇聚，天然就是从海面一点散开的样子。 */
    private static void rays(BufferBuilder b, Matrix4f m, float t, float L) {
        int n = 16, steps = 10;
        for (int i = 0; i < n; i++) {
            float h = hash(i, 3);
            float az = i * Mth.TWO_PI / n + 0.3F * Mth.sin(i * 1.7F) + 0.06F * Mth.sin(t * 0.004F + i);
            float half = 0.02F + 0.035F * h;
            float pulse = 0.35F + 0.65F * (0.5F + 0.5F * Mth.sin(t * (0.012F + 0.01F * hash(i, 5)) + i * 2.3F));
            for (int s = 0; s < steps; s++) {
                float e0 = (float) Math.toRadians(84 - 80F * s / steps), e1 = (float) Math.toRadians(84 - 80F * (s + 1) / steps);
                float k0 = profile(e0), k1 = profile(e1);
                float a0 = 0.11F * L * pulse * k0, a1 = 0.11F * L * pulse * k1;
                // 中间亮、两侧淡：一条光柱拆成左右两半，中线最亮
                Vec3 c0 = sky(az, e0), c1 = sky(az, e1);
                Vec3 l0 = sky(az - half, e0), l1 = sky(az - half, e1), r0 = sky(az + half, e0), r1 = sky(az + half, e1);
                tri(b, m, l0, 0.3F, 0.85F, 0.8F, 0, c0, 0.45F, 0.95F, 0.85F, a0, c1, 0.45F, 0.95F, 0.85F, a1);
                tri(b, m, l0, 0.3F, 0.85F, 0.8F, 0, c1, 0.45F, 0.95F, 0.85F, a1, l1, 0.3F, 0.85F, 0.8F, 0);
                tri(b, m, c0, 0.45F, 0.95F, 0.85F, a0, r0, 0.3F, 0.85F, 0.8F, 0, r1, 0.3F, 0.85F, 0.8F, 0);
                tri(b, m, c0, 0.45F, 0.95F, 0.85F, a0, r1, 0.3F, 0.85F, 0.8F, 0, c1, 0.45F, 0.95F, 0.85F, a1);
            }
        }
    }

    private static float profile(float el) {
        float k = Mth.clamp((el - (float) Math.toRadians(4)) / (float) Math.toRadians(80), 0, 1);
        return (float) Math.pow(Mth.sin(Mth.PI * k), 0.8);
    }

    /**
     * 一道潮光：绕天一整圈、高度上下起伏的柔光带。横截面分 4 档（边缘 0 → 外辉 → 内辉 → 中线），所以边缘是化开的，
     * 不是一条硬边的管子；亮度乘一道顺流推进的亮波，看得出水在往前流。中线上 10 颗流光顺流滑行，前亮后暗。
     */
    private static void current(BufferBuilder b, Matrix4f m, float t, float base, float width, float phase, float flow, float a) {
        int n = 128;
        float[] off = {-1, -0.55F, -0.18F, 0, 0.18F, 0.55F, 1};
        float[] ka = {0, 0.35F, 0.8F, 1, 0.8F, 0.35F, 0};
        float r = 0.18F, g = 0.7F, bl = 0.68F;
        for (int i = 0; i < n; i++) {
            float az0 = i * Mth.TWO_PI / n, az1 = (i + 1) * Mth.TWO_PI / n;
            float c0 = center(az0, t, base, phase), c1 = center(az1, t, base, phase);
            float w0 = halfWidth(az0, width, phase), w1 = halfWidth(az1, width, phase);
            float s0 = a * surge(az0, t, phase, flow), s1 = a * surge(az1, t, phase, flow);
            for (int k = 0; k + 1 < off.length; k++) {
                Vec3 p00 = sky(az0, c0 + off[k] * w0), p10 = sky(az1, c1 + off[k] * w1);
                Vec3 p01 = sky(az0, c0 + off[k + 1] * w0), p11 = sky(az1, c1 + off[k + 1] * w1);
                float a00 = 0.11F * s0 * ka[k], a10 = 0.11F * s1 * ka[k], a01 = 0.11F * s0 * ka[k + 1], a11 = 0.11F * s1 * ka[k + 1];
                tri(b, m, p00, r, g, bl, a00, p10, r, g, bl, a10, p11, r, g, bl, a11);
                tri(b, m, p00, r, g, bl, a00, p11, r, g, bl, a11, p01, r, g, bl, a01);
            }
        }
        // 流光：沿中线滑行的细长亮点，长度顺着流向
        for (int j = 0; j < 10; j++) {
            float az = (hash(j, (int) (phase * 10) + 20) * Mth.TWO_PI + t * 0.0016F * flow * (0.8F + 0.4F * hash(j, 21))) % Mth.TWO_PI;
            float lane = (hash(j, 22) - 0.5F) * 0.7F;
            float tw = 0.6F + 0.4F * Mth.sin(t * 0.07F + j * 1.9F);
            float ga = a * tw * surge(az, t, phase, flow);
            if (ga < 0.02F) continue;
            float len = 0.035F + 0.03F * hash(j, 23);
            for (int s = 0; s < 3; s++) {
                float h0 = az - len * s / 3, h1 = az - len * (s + 1) / 3;
                float e0 = center(h0, t, base, phase) + lane * halfWidth(h0, width, phase);
                float e1 = center(h1, t, base, phase) + lane * halfWidth(h1, width, phase);
                float th = 0.0035F * (1 - s * 0.3F);
                float al0 = ga * (1 - s / 3F), al1 = ga * (1 - (s + 1) / 3F);
                Vec3 q0 = sky(h0, e0 + th), q1 = sky(h0, e0 - th), q2 = sky(h1, e1 - th * 0.7F), q3 = sky(h1, e1 + th * 0.7F);
                tri(b, m, q0, 0.75F, 1, 0.94F, al0, q1, 0.75F, 1, 0.94F, al0, q2, 0.4F, 0.9F, 0.85F, al1);
                tri(b, m, q0, 0.75F, 1, 0.94F, al0, q2, 0.4F, 0.9F, 0.85F, al1, q3, 0.4F, 0.9F, 0.85F, al1);
            }
        }
    }

    private static float center(float az, float t, float base, float phase) {
        return base + 0.2F * Mth.sin(2 * az + phase + t * 0.0016F) + 0.05F * Mth.sin(5 * az + t * 0.003F);
    }

    private static float halfWidth(float az, float width, float phase) {
        return width * (0.7F + 0.3F * Mth.sin(3 * az + phase * 1.3F)) * 0.5F;
    }

    /** 顺流推进的亮波：两道不同波长的波相乘，亮段时长时短，不会整条一样亮。 */
    private static float surge(float az, float t, float phase, float flow) {
        float w1 = 0.5F + 0.5F * Mth.sin(az * 3 - t * 0.012F * flow + phase);
        float w2 = 0.5F + 0.5F * Mth.sin(az * 7 - t * 0.02F * flow + phase * 2);
        return 0.25F + 0.75F * w1 * (0.5F + 0.5F * w2);
    }

    /** 声纹环：以发出方向为圆心、角半径越扩越大的一圈环带，先快后慢地荡满整片天。 */
    private static void ring(BufferBuilder b, Matrix4f m, Ring r, float t, float L) {
        float age = t - r.born;
        if (age < 0) return;
        float k = Mth.clamp(age / RING_LIFE, 0, 1);
        float theta = (float) Math.toRadians(3 + 150 * (1 - Math.pow(1 - k, 2.2)));
        float half = (float) Math.toRadians(1.2 + 4.5 * k) * 0.5F;
        float a = L * r.strength * (float) Math.pow(1 - k, 1.3) * Mth.clamp(age / 4, 0, 1);
        if (a < 0.003F) return;
        Vec3 c = r.dir;
        Vec3 u = c.cross(new Vec3(0, 1, 0));
        if (u.lengthSqr() < 1.0E-4) u = new Vec3(1, 0, 0);
        u = u.normalize();
        Vec3 v = u.cross(c).normalize();
        int n = 72;
        float reps = 18;
        for (int i = 0; i < n; i++) {
            float p0 = i * Mth.TWO_PI / n, p1 = (i + 1) * Mth.TWO_PI / n;
            float u0 = reps * i / n, u1 = reps * (i + 1) / n;
            Vec3 i0 = cone(c, u, v, theta - half, p0), i1 = cone(c, u, v, theta - half, p1);
            Vec3 o0 = cone(c, u, v, theta + half, p0), o1 = cone(c, u, v, theta + half, p1);
            vt(b, m, i0, u0, 0, a);
            vt(b, m, i1, u1, 0, a);
            vt(b, m, o1, u1, 1, a);
            vt(b, m, i0, u0, 0, a);
            vt(b, m, o1, u1, 1, a);
            vt(b, m, o0, u0, 1, a);
        }
    }

    private static Vec3 cone(Vec3 c, Vec3 u, Vec3 v, double angle, double phase) {
        return c.scale(Math.cos(angle)).add(u.scale(Math.cos(phase) * Math.sin(angle))).add(v.scale(Math.sin(phase) * Math.sin(angle)))
            .normalize().scale(R - 8);
    }

    /** 气泡和浮游荧光：小方点从地平线下一路慢慢升向天顶，到顶前淡出，再从下面重新冒出来。 */
    private static void motes(BufferBuilder b, Matrix4f m, float t, float L) {
        float[][] cols = {{0.31F, 0.86F, 0.8F}, {0.74F, 1, 0.94F}, {0.94F, 0.92F, 1}, {0.77F, 0.71F, 0.9F}};
        for (int i = 0; i < 200; i++) {
            float az = hash(i, 1) * Mth.TWO_PI + t * 0.0002F * (hash(i, 7) - 0.5F);
            float span = 92, el = -10 + ((hash(i, 2) * span + t * (0.01F + 0.025F * hash(i, 4))) % span);
            float life = Mth.clamp((el + 10) / 12, 0, 1) * Mth.clamp((82 - el) / 18, 0, 1);
            float tw = 0.55F + 0.45F * Mth.sin(t * (0.05F + 0.08F * hash(i, 6)) + i);
            float a = L * life * tw * (0.35F + 0.5F * hash(i, 8));
            if (a < 0.01F) continue;
            float[] c = cols[(int) (hash(i, 9) * 3.99F)];
            Vec3 p = sky(az, (float) Math.toRadians(el)).scale((R - 10) / R);
            Vec3 n = p.normalize();
            Vec3 x = n.cross(new Vec3(0, 1, 0));
            if (x.lengthSqr() < 1.0E-4) x = new Vec3(1, 0, 0);
            x = x.normalize();
            Vec3 y = x.cross(n).normalize();
            float s = 0.18F + 0.32F * hash(i, 10);
            x = x.scale(s);
            y = y.scale(s);
            Vec3 q0 = p.subtract(x).subtract(y), q1 = p.add(x).subtract(y), q2 = p.add(x).add(y), q3 = p.subtract(x).add(y);
            tri(b, m, q0, c[0], c[1], c[2], a, q1, c[0], c[1], c[2], a, q2, c[0], c[1], c[2], a);
            tri(b, m, q0, c[0], c[1], c[2], a, q2, c[0], c[1], c[2], a, q3, c[0], c[1], c[2], a);
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

    private static void quadC(BufferBuilder b, Matrix4f m, Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3,
                              float[] c0, float[] c1, float[] c2, float[] c3, float L) {
        tri(b, m, p0, c0[0], c0[1], c0[2], c0[3] * L, p1, c1[0], c1[1], c1[2], c1[3] * L, p2, c2[0], c2[1], c2[2], c2[3] * L);
        tri(b, m, p0, c0[0], c0[1], c0[2], c0[3] * L, p2, c2[0], c2[1], c2[2], c2[3] * L, p3, c3[0], c3[1], c3[2], c3[3] * L);
    }

    private static void vt(BufferBuilder b, Matrix4f m, Vec3 p, float u, float v, float a) {
        b.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setUv(u, v).setColor(1, 1, 1, a);
    }

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

    /** 远处地形的雾也染成深海色，天和地接得上。 */
    @SubscribeEvent public static void fogColor(ViewportEvent.ComputeFogColor e) {
        float L = Mth.lerp((float) e.getPartialTick(), presenceO, presence);
        if (L < 0.001F) return;
        e.setRed(Mth.lerp(L, e.getRed(), 0.03F));
        e.setGreen(Mth.lerp(L, e.getGreen(), 0.11F));
        e.setBlue(Mth.lerp(L, e.getBlue(), 0.15F));
    }
}
