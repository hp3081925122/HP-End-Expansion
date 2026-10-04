package org.hp.hp_end_expansion.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.hp.hp_end_expansion.entity.starwreck.SkyMeteorEntity;
import org.hp.hp_end_expansion.entity.starwreck.SkyrenderEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * 天陨（设计文档 skyrender_meteor_ultimate.md），照着参考视频重做：
 * 首领背后亮起金色光环 → 天上卷开一只原版云块拼的天旋涡，深处烧着一颗岩浆块拼的太阳 →
 * 太阳冲出旋涡口，口沿燃成火环，陨石雨跟着砸满全场 → 主星落地：白闪里碎岩扑面，一圈贴地冲击墙、
 * 蘑菇云和放射光痕往外推，岩块崩飞 → 第二次爆闪 → 坑底裂开一格一格的熔岩缝，碎岩悬在半空，余烬往上飘。
 * 星、陨石、碎岩、熔岩缝和火都直接用原版方块图集里的岩浆、岩浆块、火和黑石，跟着原版动画一起动。
 */
final class SkyMeteorRenderer extends EntityRenderer<SkyMeteorEntity> {
    static final ResourceLocation VORTEX_CLOUD = SkyrenderClient.id("textures/effect/sky_vortex_cloud.png");
    static final ResourceLocation BLAST_WALL = SkyrenderClient.id("textures/effect/sky_blast_wall.png");
    static final ResourceLocation BLAST_SMOKE = SkyrenderClient.id("textures/effect/sky_blast_smoke.png");
    private static final ResourceLocation CORONA = SkyrenderClient.STAR_CORONA, FLARE = SkyrenderClient.STAR_FLARE;
    private static final float HIT = SkyrenderEntity.METEOR_HIT, TEAR = SkyrenderEntity.METEOR_TEAR;
    /** 撞击后继续往地里压的拍数，压完才崩开。 */
    private static final float SINK = 3;

    // 这一帧的镜头（实体本地坐标）和远处拉近的范围。只在渲染线程用
    private static Vec3 eye = Vec3.ZERO, right = new Vec3(1, 0, 0), up = new Vec3(0, 1, 0);
    private static float near = 60, far = 100;

    /** 这一帧要用的东西，省得每个方法传一长串参数。 */
    private record Frame(SkyMeteorEntity e, PoseStack.Pose pose, MultiBufferSource buf, float t, Vec3 sky, Vec3 lead, float r, float arena, float brk) {}

    SkyMeteorRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0;
    }

    @Override public ResourceLocation getTextureLocation(SkyMeteorEntity e) { return TextureAtlas.LOCATION_BLOCKS; }

    @Override public void render(SkyMeteorEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        float t = e.tickCount + pt;
        Vec3 camera = entityRenderDispatcher.camera.getPosition(), world = e.getPosition(pt);
        e.lockViewDirection(camera);
        eye = camera.subtract(world);
        Quaternionf cam = entityRenderDispatcher.cameraOrientation();
        right = rotate(cam, 1, 0, 0);
        up = rotate(cam, 0, 1, 0);
        far = Mth.clamp(RenderSystem.getShaderFogStart() * 0.8F, 40, 2000);
        near = far * 0.6F;
        Frame f = new Frame(e, ps.last(), buffers, t, e.skyPoint(), e.lead(), e.starRadius(), e.arenaRadius(), e.breakTick());
        renderHalo(f);
        renderVortex(f);
        renderStar(f);
        renderRain(f);
        renderSparks(f);
        if (t < HIT) return;
        renderBurst(f);
        renderBlast(f);
        renderDebris(f);
        renderCracks(f);
        renderFloaters(f);
    }

    // ---------- 共用 ----------
    static float clamp01(float v) { return Mth.clamp(v, 0, 1); }

    static float smooth(float v) {
        v = clamp01(v);
        return v * v * (3 - 2 * v);
    }

    static Vec3 rotate(Quaternionf q, float x, float y, float z) {
        Vector3f v = q.transform(new Vector3f(x, y, z));
        return new Vec3(v.x, v.y, v.z);
    }

    static TextureAtlasSprite block(String name) {
        return Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(ResourceLocation.withDefaultNamespace("block/" + name));
    }

    /**
     * 远处拉近：离镜头超过 near 的点沿视线平滑压到 far 以内（视距 8 区块时雾从 115 格起，天旋涡在三百多格外）。
     * 方向不变，所以屏幕上看起来一模一样；按距离单调压缩，前后遮挡关系也不变。
     */
    static Vec3 map(Vec3 p) {
        Vec3 d = p.subtract(eye);
        double len = d.length();
        if (len <= near) return p;
        double span = far - near, m = near + span * (1 - Math.exp(-(len - near) / span));
        return eye.add(d.scale(m / len));
    }

    static void put(PoseStack.Pose pose, VertexConsumer vc, Vec3 p, float u, float v, float r, float g, float b, float a) {
        Vec3 q = map(p);
        vc.addVertex(pose, (float) q.x, (float) q.y, (float) q.z).setColor(clamp01(r), clamp01(g), clamp01(b), clamp01(a))
            .setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0, 1, 0);
    }

    /** a(u0,v0) b(u1,v0) c(u1,v1) d(u0,v1)，正反两面都画。 */
    static void quad(PoseStack.Pose pose, VertexConsumer vc, Vec3 a, Vec3 b, Vec3 c, Vec3 d, float u0, float v0, float u1, float v1,
                     float r, float g, float bl, float alpha) {
        put(pose, vc, a, u0, v0, r, g, bl, alpha);
        put(pose, vc, b, u1, v0, r, g, bl, alpha);
        put(pose, vc, c, u1, v1, r, g, bl, alpha);
        put(pose, vc, d, u0, v1, r, g, bl, alpha);
        put(pose, vc, d, u0, v1, r, g, bl, alpha);
        put(pose, vc, c, u1, v1, r, g, bl, alpha);
        put(pose, vc, b, u1, v0, r, g, bl, alpha);
        put(pose, vc, a, u0, v0, r, g, bl, alpha);
    }

    /** 四个顶点各带颜色的四边形，正反两面都画。 */
    static void shade(PoseStack.Pose pose, VertexConsumer vc, Vec3[] p, float[][] uv, float[][] c) {
        for (int k = 0; k < 4; k++) put(pose, vc, p[k], uv[k][0], uv[k][1], c[k][0], c[k][1], c[k][2], 1);
        for (int k = 3; k >= 0; k--) put(pose, vc, p[k], uv[k][0], uv[k][1], c[k][0], c[k][1], c[k][2], 1);
    }

    /** 朝向镜头的方片（加法层），spin 是绕视线转的角度（度）。 */
    static void sprite(Frame f, ResourceLocation tex, Vec3 at, float size, float spin, float r, float g, float b) {
        if (size <= 0.001F || r + g + b <= 0.003F) return;
        float c = Mth.cos(spin * Mth.DEG_TO_RAD), s = Mth.sin(spin * Mth.DEG_TO_RAD);
        Vec3 x = right.scale(c).add(up.scale(s)).scale(size), y = up.scale(c).subtract(right.scale(s)).scale(size);
        quad(f.pose, f.buf.getBuffer(StarfallDraw.additive(tex)), at.subtract(x).add(y), at.add(x).add(y), at.add(x).subtract(y), at.subtract(x).subtract(y),
            0, 0, 1, 1, r, g, b, 1);
    }

    /** 方块图集里的一张原版贴图当方片画，up 是方片的“上”（火苗往哪边舔）。 */
    static void blockSprite(PoseStack.Pose pose, VertexConsumer vc, TextureAtlasSprite sp, Vec3 base, Vec3 dirUp, float halfWidth, float height,
                            float r, float g, float b) {
        Vec3 side = dirUp.cross(eye.subtract(base));
        if (side.lengthSqr() < 1.0E-8) return;
        side = side.normalize().scale(halfWidth);
        Vec3 top = base.add(dirUp.scale(height));
        quad(pose, vc, top.subtract(side), top.add(side), base.add(side), base.subtract(side), sp.getU0(), sp.getV0(), sp.getU1(), sp.getV1(), r, g, b, 1);
    }

    /** 两点之间朝向镜头的一道光（横截面取日冕正中那一行：中间亮、两边淡），头亮尾灭。 */
    static void streak(PoseStack.Pose pose, VertexConsumer vc, Vec3 head, Vec3 tail, float width, float r, float g, float b) {
        Vec3 side = head.subtract(tail).cross(head.subtract(eye));
        if (side.lengthSqr() < 1.0E-8) return;
        side = side.normalize().scale(width);
        float[] hc = {r, g, b}, tc = {0, 0, 0};
        shade(pose, vc, new Vec3[]{head.add(side), head.subtract(side), tail.subtract(side.scale(0.4)), tail.add(side.scale(0.4))},
            new float[][]{{0, 0.5F}, {1, 0.5F}, {1, 0.5F}, {0, 0.5F}}, new float[][]{hc, hc, tc, tc});
    }

    /** 摆在垂直于 axis 的平面上的一圈热浪（只取 heat_ring 正中一列：内缘暗、外缘亮）。outer 外半径，width 环宽。 */
    static void ring(PoseStack.Pose pose, VertexConsumer vc, Vec3 center, Vec3 axis, float outer, float width, float r, float g, float b) {
        Vec3[] uv = SkyMeteorEntity.basis(axis);
        float inner = Math.max(0, outer - width);
        int n = 48;
        for (int j = 0; j < n; j++) {
            float a0 = j * Mth.TWO_PI / n, a1 = (j + 1) * Mth.TWO_PI / n;
            Vec3 d0 = uv[0].scale(Mth.cos(a0)).add(uv[1].scale(Mth.sin(a0))), d1 = uv[0].scale(Mth.cos(a1)).add(uv[1].scale(Mth.sin(a1)));
            quad(pose, vc, center.add(d0.scale(inner)), center.add(d1.scale(inner)), center.add(d1.scale(outer)), center.add(d0.scale(outer)),
                0.5F, 0, 0.5F, 1, r, g, b, 1);
        }
    }

    // ---------- 星：岩浆块拼的太阳 ----------
    /** 体素球的半径（格数），整颗星的外半径约 1.1 个星半径。 */
    private static final float GRID = 5.3F;
    private static final int[][] FACE_N = {{0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}, {1, 0, 0}, {-1, 0, 0}};
    private static final float[][][] FACE_C = {
        {{-1, 1, -1}, {-1, 1, 1}, {1, 1, 1}, {1, 1, -1}}, {{-1, -1, 1}, {-1, -1, -1}, {1, -1, -1}, {1, -1, 1}},
        {{-1, 1, 1}, {-1, -1, 1}, {1, -1, 1}, {1, 1, 1}}, {{1, 1, -1}, {1, -1, -1}, {-1, -1, -1}, {-1, 1, -1}},
        {{1, 1, 1}, {1, -1, 1}, {1, -1, -1}, {1, 1, -1}}, {{-1, 1, -1}, {-1, -1, -1}, {-1, -1, 1}, {-1, 1, 1}}};
    /** 星面上的岩浆块纹：三道手摆的大圆，落在圆上的体素用岩浆块，其余是岩浆。 */
    private static final float[][] VEINS = {{0.94F, 0.28F, 0.19F}, {-0.18F, 0.88F, 0.44F}, {0.35F, -0.27F, 0.9F}};
    /** 外露的面：{x, y, z, 面, 种类(0 岩浆 / 1 岩浆块)}。 */
    private static final int[][] STAR_FACES;
    /** 外露的体素：{x, y, z, 种类}，崩开时挑一部分飞出去。 */
    private static final int[][] STAR_SURFACE;

    static {
        List<int[]> faces = new ArrayList<>(), surface = new ArrayList<>();
        int n = (int) Math.ceil(GRID);
        for (int x = -n; x <= n; x++) for (int y = -n; y <= n; y++) for (int z = -n; z <= n; z++) {
            if (!inside(x, y, z)) continue;
            int kind = vein(x, y, z);
            boolean exposed = false;
            for (int f = 0; f < 6; f++) {
                if (inside(x + FACE_N[f][0], y + FACE_N[f][1], z + FACE_N[f][2])) continue;
                faces.add(new int[]{x, y, z, f, kind});
                exposed = true;
            }
            if (exposed) surface.add(new int[]{x, y, z, kind});
        }
        STAR_FACES = faces.toArray(new int[0][]);
        STAR_SURFACE = surface.toArray(new int[0][]);
    }

    private static boolean inside(int x, int y, int z) { return x * x + y * y + z * z <= GRID * GRID; }

    private static int vein(int x, int y, int z) {
        double l = Math.sqrt(x * x + y * y + z * z);
        if (l < 1) return 0;
        for (float[] v : VEINS) {
            double vl = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
            if (Math.abs((x * v[0] + y * v[1] + z * v[2]) / (l * vl)) < 0.1) return 1;
        }
        return 0;
    }

    /** 原版那样的面明暗：顶面最亮、底面最暗，按转过之后的朝向算。 */
    private static float faceShade(Quaternionf q, int face) {
        return 0.72F + 0.28F * (float) rotate(q, FACE_N[face][0], FACE_N[face][1], FACE_N[face][2]).y;
    }

    private static void voxFace(PoseStack.Pose pose, VertexConsumer vc, Vec3 c, Quaternionf q, float half, int face, TextureAtlasSprite sp,
                                float r, float g, float b) {
        Vec3[] p = new Vec3[4];
        for (int k = 0; k < 4; k++) {
            float[] o = FACE_C[face][k];
            p[k] = c.add(rotate(q, o[0] * half, o[1] * half, o[2] * half));
        }
        quad(pose, vc, p[0], p[1], p[2], p[3], sp.getU0(), sp.getV0(), sp.getU1(), sp.getV1(), r, g, b, 1);
    }

    /** 一整块方块；shaded 时带原版的面明暗（实心层用），不带时六面一样亮（发光层用）。 */
    static void voxel(PoseStack.Pose pose, VertexConsumer vc, Vec3 c, Quaternionf q, float half, TextureAtlasSprite sp, float r, float g, float b, boolean shaded) {
        for (int f = 0; f < 6; f++) {
            float s = shaded ? faceShade(q, f) : 1;
            voxFace(pose, vc, c, q, half, f, sp, r * s, g * s, b * s);
        }
    }

    /** 星的翻滚：绕“前进方向 × 竖直”慢慢转。 */
    private static Quaternionf starRotation(float t, Vec3 lead) {
        Vec3 axis = lead.cross(new Vec3(0, 1, 0));
        if (axis.lengthSqr() < 1.0E-4) axis = new Vec3(1, 0, 0);
        axis = axis.normalize();
        return new Quaternionf().rotationAxis(t * 0.011F, (float) axis.x, (float) axis.y, (float) axis.z).rotateY(0.7F).rotateX(0.45F);
    }

    /** 下落速度 0.2..1（下落曲线的斜率归一化）。 */
    private static float speed(float k) { return (0.45F + 1.65F * k * k) / 2.1F; }

    /**
     * 星：撕天时就在旋涡深处亮起来，从一个火点长成整颗；冲出旋涡口后带着激波罩、气流线和马赫环越压越大。
     * 实心层是原版岩浆（岩浆块纹路），发光层再叠一遍同一张贴图；背后的日冕只在轮廓外露出一圈火边。
     */
    private void renderStar(Frame f) {
        float t = f.t, since = t - HIT;
        if (t < TEAR + 5 || since >= SINK) return;
        float k = SkyMeteorEntity.drop(t), white = clamp01(since / SINK), live = 1 - white;
        // 旋涡里的“太阳”比落下来的星大：冲出旋涡口前 8 拍鼓胀、烧白，炸开那拍（白闪盖住）只剩星核冲出来
        float swell = smooth((t - (f.brk - 8)) / 8), sun = t < f.brk ? 2.4F + 0.6F * swell : 1, burst = t < f.brk ? 0.8F * swell * swell : 0;
        float appear = smooth((t - TEAR - 5) / 25), r = f.r * (0.15F + 0.85F * appear) * sun, s = 1.1F * r / GRID;
        Vec3 at = f.e.starPos(t);
        if (since > 0) at = at.add(0, -0.6F * f.r * white, 0);
        Quaternionf q = starRotation(t, f.lead);
        TextureAtlasSprite lava = block("lava_still"), magma = block("magma");
        // 镜头钻进星里时，罩住镜头的体素不画（屏幕由 gui 里的熔岩色接管），免得看到方块内壁
        float skip = s * 1.3F;
        VertexConsumer vc = f.buf.getBuffer(StarfallDraw.solid(TextureAtlas.LOCATION_BLOCKS));
        for (int[] fc : STAR_FACES) {
            Vec3 c = at.add(rotate(q, fc[0] * s, fc[1] * s, fc[2] * s));
            if (c.distanceToSqr(eye) < skip * skip) continue;
            float sh = faceShade(q, fc[3]) * (fc[4] == 1 ? 0.85F : 1) + white + burst;
            voxFace(f.pose, vc, c, q, s * 0.5F, fc[3], fc[4] == 1 ? magma : lava, sh, sh, sh);
        }
        float glow = (0.3F + 0.45F * k) * live + white + burst;
        vc = f.buf.getBuffer(StarfallDraw.additive(TextureAtlas.LOCATION_BLOCKS));
        for (int[] fc : STAR_FACES) {
            Vec3 c = at.add(rotate(q, fc[0] * s, fc[1] * s, fc[2] * s));
            if (c.distanceToSqr(eye) < skip * skip) continue;
            float b = glow * (fc[4] == 1 ? 0.55F : 0.85F);
            voxFace(f.pose, vc, c, q, s * 0.505F, fc[3], fc[4] == 1 ? magma : lava, b, b, b);
        }
        Vec3 view = at.subtract(eye);
        view = view.lengthSqr() < 1.0E-4 ? new Vec3(0, -1, 0) : view.normalize();
        Vec3 behind = at.add(view.scale(r * 0.35F));
        float pulse = 0.88F + 0.12F * Mth.sin(t * 0.45F);
        // 日冕：轮廓里的被岩体挡住，只剩一圈火边；外面一层淡的大光晕。远处像旋涡深处的一颗太阳，光芒最盛
        float rim = (1.0F + 0.5F * k) * pulse * live * appear + 1.5F * (white + burst);
        sprite(f, CORONA, behind, r * 1.9F, 0, rim, 0.66F * rim, 0.3F * rim);
        float halo = (0.45F + 0.15F * k) * live * appear;
        sprite(f, CORONA, behind, r * 3.8F, 0, halo, 0.55F * halo, 0.24F * halo);
        float rays = (1.1F - 0.75F * k) * live * appear;
        sprite(f, FLARE, behind, r * 3.6F, t * 0.6F, rays, 0.84F * rays, 0.55F * rays);
        float out = clamp01((t - f.brk) / 6);
        float bow = (0.15F + 0.85F * k * k) * live * out;
        sprite(f, CORONA, at.add(f.lead.scale(r * 0.8F)), r * 1.25F, 0, bow, 0.62F * bow, 0.28F * bow);
        float room = (float) at.subtract(f.sky).dot(f.lead);
        renderTail(f, at, room, r, k, live * out);
        float air = out * out * speed(k) * live;
        if (air > 0.01F) {
            renderSheath(f, at, r, air);
            renderStreaks(f, at, r, air);
        }
        renderShocks(f, r);
        // 压迫面：星的火光照在坑底，离地越近越亮、越大
        float press = k * k * 0.9F * live;
        if (press > 0.01F) {
            float py = (float) Math.max(0.1, at.y - r * 1.05), pr = r * (1.6F + 1.0F * k);
            quad(f.pose, f.buf.getBuffer(StarfallDraw.additive(CORONA)), new Vec3(at.x - pr, py, at.z - pr), new Vec3(at.x + pr, py, at.z - pr),
                new Vec3(at.x + pr, py, at.z + pr), new Vec3(at.x - pr, py, at.z + pr), 0, 0, 1, 1, press, 0.85F * press, 0.65F * press, 1);
        }
    }

    /** 尾焰：一条沿来路拖回旋涡口的软光带，外加两条换帧的火舌（meteor_tail 一张四帧）。room 是星心冲出旋涡口多远。 */
    private void renderTail(Frame f, Vec3 at, float room, float size, float k, float live) {
        if (room + size <= 1 || live <= 0) return;
        Vec3 back = f.lead.scale(-1);
        float sp = 0.12F + 2.64F * k * k, len = Math.min(room + size, size * (1.4F + 1.5F * sp)), heat = (0.4F + 0.6F * k) * live;
        Vec3 side = back.cross(eye.subtract(at));
        if (side.lengthSqr() < 1.0E-8) return;
        side = side.normalize();
        Vec3 end = at.add(back.scale(len));
        float w = size * 0.95F;
        quad(f.pose, f.buf.getBuffer(StarfallDraw.additive(CORONA)), at.add(side.scale(w)), at.subtract(side.scale(w)), end.subtract(side.scale(w * 0.35F)),
            end.add(side.scale(w * 0.35F)), 0, 0.5F, 1, 1, heat, 0.6F * heat, 0.3F * heat, 1);
        VertexConsumer flame = f.buf.getBuffer(StarfallDraw.additive(StarfallDraw.TAIL));
        int frame = (int) (f.t * 0.5F);
        float[][] layers = {{0.62F, 0.9F, 0.9F}, {0.38F, 0.65F, 1}};
        for (int i = 0; i < layers.length; i++) {
            float u0 = ((frame + i * 2) & 3) * 0.25F, b = heat * layers[i][2], w0 = size * layers[i][0], w1 = size * 0.08F;
            Vec3 head = at.add(back.scale(size * 0.8F)), tip = head.add(back.scale(len * layers[i][1]));
            quad(f.pose, flame, head.add(side.scale(w0)), head.subtract(side.scale(w0)), tip.subtract(side.scale(w1)), tip.add(side.scale(w1)),
                u0, 0, u0 + 0.25F, 1, b, 0.62F * b, 0.3F * b, 1);
        }
    }

    private static final int SHEATH_RINGS = 9, SHEATH_SEGMENTS = 28, STREAKS = 36;
    /** 激波罩从前缘往后包到的角度（约 117°）。 */
    private static final float SHEATH_ARC = 2.05F;

    /**
     * 激波罩：星前面被压缩烧亮的空气，套在星外面的弹头形薄壳（1.3 倍星半径，过了侧面往后拖长）。只有掠射的边缘亮：
     * 迎面看是贴着轮廓一圈流动的火边，侧面看是罩在星前面的一弯月牙。镜头快贴到壳上时淡掉。
     */
    private void renderSheath(Frame f, Vec3 at, float r, float power) {
        float close = clamp01(((float) at.distanceTo(eye) / r - 1.6F) / 1.2F);
        if (close <= 0) return;
        Vec3 lead = f.lead;
        Vec3[] uv = SkyMeteorEntity.basis(lead);
        Vec3 c = at.add(lead.scale(0.12F * r));
        float rs = 1.3F * r, stretch = 1 + 1.6F * power, flow = f.t * (0.05F + 0.1F * power);
        Vec3[][] p = new Vec3[SHEATH_RINGS + 1][SHEATH_SEGMENTS + 1];
        float[][][] col = new float[SHEATH_RINGS + 1][SHEATH_SEGMENTS + 1][];
        for (int j = 0; j <= SHEATH_RINGS; j++) {
            float th = SHEATH_ARC * j / SHEATH_RINGS, ct = Mth.cos(th), st = Mth.sin(th), front = Math.max(0, ct);
            float along = rs * ct * (ct < 0 ? stretch : 1);
            float w = (0.5F + 0.5F * front) * clamp01((SHEATH_ARC - th) / (SHEATH_ARC - 1.2F)) * 2 * power * close;
            for (int i = 0; i <= SHEATH_SEGMENTS; i++) {
                float ph = Mth.TWO_PI * i / SHEATH_SEGMENTS;
                Vec3 radial = uv[0].scale(Mth.cos(ph)).add(uv[1].scale(Mth.sin(ph)));
                Vec3 pos = c.add(lead.scale(along)).add(radial.scale(rs * st)), sight = pos.subtract(eye);
                double len = sight.length();
                float facing = len < 1.0E-4 ? 1 : (float) Math.abs(lead.scale(ct).add(radial.scale(st)).dot(sight) / len);
                float b = w * (1 - facing) * (1 - facing);
                p[j][i] = pos;
                col[j][i] = new float[]{b, b * (0.5F + 0.4F * front), b * (0.2F + 0.35F * front)};
            }
        }
        VertexConsumer vc = f.buf.getBuffer(StarfallDraw.additive(SkyrenderClient.AIR_SHEATH));
        for (int j = 0; j < SHEATH_RINGS; j++) {
            float v0 = 1.5F * j / SHEATH_RINGS - flow, v1 = 1.5F * (j + 1) / SHEATH_RINGS - flow;
            for (int i = 0; i < SHEATH_SEGMENTS; i++) {
                float u0 = 2F * i / SHEATH_SEGMENTS, u1 = 2F * (i + 1) / SHEATH_SEGMENTS;
                shade(f.pose, vc, new Vec3[]{p[j][i], p[j][i + 1], p[j + 1][i + 1], p[j + 1][i]},
                    new float[][]{{u0, v0}, {u1, v0}, {u1, v1}, {u0, v1}}, new float[][]{col[j][i], col[j][i + 1], col[j + 1][i + 1], col[j + 1][i]});
            }
        }
    }

    /** 气流线：贴着激波罩从前缘滑到侧面，再往后、往外甩进尾流；迎面看就是从星的轮廓往四面射出去的速度线。 */
    private void renderStreaks(Frame f, Vec3 at, float r, float power) {
        Vec3 lead = f.lead;
        Vec3[] uv = SkyMeteorEntity.basis(lead);
        Vec3 c = at.add(lead.scale(0.12F * r));
        float rs = 1.36F * r;
        int seed = f.e.getId();
        VertexConsumer vc = f.buf.getBuffer(StarfallDraw.additive(CORONA));
        for (int i = 0; i < STREAKS; i++) {
            float period = (8 + 6 * SkyMeteorEntity.noise(seed, i, 41)) * (1.3F - 0.6F * power);
            float s = (f.t / period + SkyMeteorEntity.noise(seed, i, 42)) % 1;
            float ph = Mth.TWO_PI * (i + 0.6F * SkyMeteorEntity.noise(seed, i, 43)) / STREAKS;
            Vec3 radial = uv[0].scale(Mth.cos(ph)).add(uv[1].scale(Mth.sin(ph)));
            Vec3 head = streakPoint(c, lead, radial, rs, r, s, power), tail = streakPoint(c, lead, radial, rs, r, s - 0.16F - 0.1F * power, power);
            float b = 1.3F * power * Mth.sin(Mth.PI * s) * (0.55F + 0.45F * SkyMeteorEntity.noise(seed, i, 44));
            streak(f.pose, vc, head, tail, r * (0.05F + 0.05F * SkyMeteorEntity.noise(seed, i, 45)), b, 0.86F * b, 0.66F * b);
        }
    }

    /** 气流线上 s（0..1）处的位置：从前缘 20° 沿壳滑到 140°，过了侧面往后拖长、往外甩。 */
    private static Vec3 streakPoint(Vec3 c, Vec3 lead, Vec3 radial, float rs, float r, float s, float power) {
        float th = 0.35F + 2.1F * Math.max(0, s), ct = Mth.cos(th), st = Mth.sin(th);
        float along = rs * ct * (ct < 0 ? 1 + 1.6F * power : 1);
        float fling = r * 0.7F * (float) Math.pow(Math.max(0, th - Mth.HALF_PI) / 0.9F, 1.5);
        return c.add(lead.scale(along)).add(radial.scale(rs * st + fling));
    }

    /** 马赫环：冲出旋涡后每 5 拍在星身后留下一圈往外荡开的空气，一串连起来是一只往后张开的锥。落地后很快淡掉。 */
    private void renderShocks(Frame f, float r) {
        float first = f.brk + 4;
        if (f.t < first) return;
        VertexConsumer vc = f.buf.getBuffer(StarfallDraw.additive(StarfallDraw.RING));
        for (float born = first; born <= f.t && born <= HIT - 3; born += 5) {
            float a = f.t - born;
            if (a >= 20) continue;
            float p = a / 20, sp = speed(SkyMeteorEntity.drop(born));
            float b = 0.42F * sp * (1 - p) * (1 - p) * clamp01(1 - (f.t - HIT) / 3);
            if (b < 0.01F) continue;
            float grow = 1 - (1 - p) * (1 - p);
            ring(f.pose, vc, f.e.starPos(born).subtract(f.lead.scale(0.15F * r)), f.lead, r * (1.2F + (1.6F + 1.6F * sp) * grow), r * (0.25F + 0.5F * p),
                b, 0.82F * b, 0.66F * b);
        }
    }

    // ---------- 起势：首领背后的金色光环，一道光冲上天 ----------
    private void renderHalo(Frame f) {
        float t = f.t;
        if (t > 56) return;
        SkyrenderEntity boss = null;
        for (SkyrenderEntity b : f.e.level().getEntitiesOfClass(SkyrenderEntity.class, f.e.getBoundingBox().inflate(f.arena + 40))) {
            if (b.isAlive() && b.getState() == SkyrenderEntity.METEOR) boss = b;
        }
        if (boss == null) return;
        Vec3 bp = boss.position().subtract(f.e.position());
        float h = boss.getBbHeight();
        Vec3 toCam = new Vec3(eye.x - bp.x, 0, eye.z - bp.z);
        toCam = toCam.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : toCam.normalize();
        Vec3 c = bp.add(0, h * 0.7F, 0).subtract(toCam.scale(h * 0.5F));
        float grow = smooth(t / 12), a = grow * clamp01((54 - t) / 12);
        sprite(f, CORONA, c, h * 1.7F * grow, 0, a, 0.8F * a, 0.38F * a);
        sprite(f, FLARE, c, h * 2.7F * grow, t * 1.5F, 0.95F * a, 0.72F * a, 0.3F * a);
        sprite(f, FLARE, c, h * 2.0F * grow, 22 - t * 1.1F, 0.6F * a, 0.42F * a, 0.16F * a);
        Vec3 face = eye.subtract(c);
        ring(f.pose, f.buf.getBuffer(StarfallDraw.additive(StarfallDraw.RING)), c, face.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : face.normalize(),
            h * 1.35F * grow, h * 0.3F, 0.9F * a, 0.66F * a, 0.28F * a);
        // 撕天那一下：一道金光从光环冲进天旋涡
        float beam = clamp01((t - 26) / 3) * clamp01((44 - t) / 8);
        if (beam > 0) {
            VertexConsumer vc = f.buf.getBuffer(StarfallDraw.additive(CORONA));
            streak(f.pose, vc, c, f.sky, 3.5F * beam, beam, 0.85F * beam, 0.45F * beam);
            streak(f.pose, vc, f.sky, c, 6F * beam, beam, 0.8F * beam, 0.4F * beam);
        }
    }

    // ---------- 天旋涡：原版云块拼的漏斗，深处烧着星 ----------
    /** 每一圈云块：离旋涡口多深、半径多大、几块。越深越窄、转得越快。 */
    private static final float[] V_DEPTH = {0, 30, 65, 105, 150, 200, 255, 315}, V_RAD = {230, 195, 160, 128, 100, 76, 56, 40};
    private static final int[] V_COUNT = {34, 32, 28, 24, 20, 17, 14, 12};
    /** 旋臂条数，以及从旋涡口到最深一圈旋臂拧过去多少弧度。 */
    private static final float V_ARMS = 3, V_TWIST = 4.2F;
    /** 云的底色：末地夜空那种灰紫，不偏褐，被星光照到的地方才是金橙。 */
    private static final float[] CLOUD = {0.26F, 0.2F, 0.36F}, GOLD = {1.0F, 0.66F, 0.3F}, FIRE = {1.0F, 0.45F, 0.12F};

    /**
     * 一团云六个面的颜色，面顺序：外侧、内侧（朝漏斗中心）、深处、朝下（朝玩家）、两侧。
     * 实心层像原版云那样按面分明暗，内侧被深处的星光照成金边；发光层只给内侧和口沿的火。
     */
    private static float[][] cloudColors(int layer, float tone, float gold, float deep, float fire, float flash) {
        float[][] col = new float[6][];
        if (layer == 0) {
            col[0] = tint(CLOUD, 0.45F * tone, null, 0);
            col[1] = tint(CLOUD, 0.7F * tone, GOLD, 0.8F * gold);
            col[2] = tint(CLOUD, 0.35F * tone, null, 0);
            col[3] = tint(CLOUD, tone, GOLD, (0.12F + 0.3F * deep) * gold);
            col[4] = col[5] = tint(CLOUD, 0.78F * tone, GOLD, 0.2F * gold);
        } else {
            float in = gold * (0.35F + 0.45F * deep) + flash, front = 0.1F * gold + 0.6F * flash;
            col[0] = col[2] = tint(FIRE, 0, FIRE, 0.4F * fire + 0.3F * flash);
            col[1] = tint(GOLD, in, FIRE, 1.1F * fire);
            col[3] = tint(GOLD, front, FIRE, 0.7F * fire);
            col[4] = col[5] = tint(GOLD, 0.5F * in, FIRE, 0.6F * fire);
        }
        return col;
    }

    private static float spike(float x, float len) { return x < 0 ? 0 : clamp01(1 - x / len); }

    /**
     * 第 25 拍起云块从四周卷进来，内侧被深处的星光照成金边，越深越亮；第 40 拍起金色光痕从漏斗深处往外喷；
     * 星冲出旋涡口那一拍整个漏斗炸亮，口沿两圈燃成火环，口沿上一圈原版火苗往外舔，炸开两圈压缩空气。
     * 落地 60 拍后开始散，110 拍后散尽。
     */
    private void renderVortex(Frame f) {
        float t = f.t, age = t - (TEAR - 5);
        if (age < 0) return;
        float open = smooth(age / 35) * (1 - smooth((t - HIT - 60) / 50));
        if (open <= 0) return;
        float ign = clamp01((t - f.brk + 3) / 6) * (1 - smooth((t - HIT - 20) / 40)), flash = spike(t - f.brk, 10);
        float heat = 0.35F + 0.65F * smooth((t - 40) / 60);
        Vec3 axis = f.lead.scale(-1);
        Vec3[] uv = SkyMeteorEntity.basis(axis);
        int seed = f.e.getId();
        float spinT = Math.max(0, t - (TEAR - 5));
        for (int layer = 0; layer < 2; layer++) {
            VertexConsumer vc = f.buf.getBuffer(layer == 0 ? StarfallDraw.solid(VORTEX_CLOUD) : StarfallDraw.additive(VORTEX_CLOUD));
            for (int j = 0; j < V_RAD.length; j++) {
                float deep = j / (float) (V_RAD.length - 1), fire = j < 2 ? ign : 0.25F * ign;
                float spin = (0.006F + 0.004F * j) * spinT + 0.00005F * spinT * spinT * (1 + 0.4F * j);
                for (int i = 0; i < V_COUNT[j]; i++) {
                    int k = j * 64 + i;
                    float n0 = SkyMeteorEntity.noise(seed, k, 51), n1 = SkyMeteorEntity.noise(seed, k, 52), n2 = SkyMeteorEntity.noise(seed, k, 55);
                    float base = Mth.TWO_PI * (i + 0.35F * n0) / V_COUNT[j] + j * 0.45F;
                    // 三条旋臂：相位随深度拧过去，跟着云一起转。臂上云团又厚又亮，臂间薄而暗，漏斗看着是卷进去的，不是一圈圈套管
                    float arm = 0.5F + 0.5F * Mth.cos(V_ARMS * base - V_TWIST * deep + 1.3F * n2);
                    arm = arm * arm * (3 - 2 * arm);
                    float ang = base + spin, bulk = 0.55F + 0.7F * arm;
                    float rad = V_RAD[j] * (0.35F + 0.65F * open) * (1 + 0.1F * Mth.sin(2 * ang + j)) * (0.9F + 0.2F * SkyMeteorEntity.noise(seed, k, 53));
                    float size = (0.3F + 0.7F * open) * bulk;
                    // 宽度超过一格弧长，相邻云团互相压住，不留缝
                    float wt = Mth.TWO_PI * rad / V_COUNT[j] * (0.6F + 0.3F * n1) * (0.75F + 0.35F * arm);
                    float th = (8 + 0.12F * rad) * (0.7F + 0.5F * n0) * 0.5F * size;
                    float len = (24 + 18 * n1) * 0.5F * size;
                    Vec3 radial = uv[0].scale(Mth.cos(ang)).add(uv[1].scale(Mth.sin(ang))), tangent = uv[1].scale(Mth.cos(ang)).subtract(uv[0].scale(Mth.sin(ang)));
                    Vec3 c = f.sky.add(axis.scale(V_DEPTH[j] + 20 * (SkyMeteorEntity.noise(seed, k, 54) - 0.5F))).add(radial.scale(rad + th));
                    // 旋臂上亮、臂间暗；外圈离天光远，再压暗一档
                    float tone = (0.6F + 0.5F * arm) * (0.8F + 0.3F * deep);
                    float gold = heat * (0.5F + 0.5F * deep) * (0.55F + 0.6F * arm);
                    float[][] col = cloudColors(layer, tone, gold, deep, fire, flash);
                    box(f.pose, vc, c, radial, tangent, axis, th, wt, len, col);
                    // 臂上的云团再叠一个小一圈的鼓包，朝漏斗中心凸出来，轮廓像原版云那样一阶一阶的。
                    // 鼓包的侧面、前面都缩在大块里面（侧边到 0.7-0.85 倍、前面到 0.8-0.9 倍），只有朝内那面露出来，不会和大块的面共面闪烁
                    if (arm > 0.45F) {
                        float s2 = 0.5F + 0.1F * n2;
                        Vec3 c2 = c.subtract(radial.scale(th * (1 + 0.6F * s2))).add(tangent.scale(wt * (n2 < 0.5F ? -0.25F : 0.25F))).subtract(axis.scale(len * 0.3F));
                        box(f.pose, vc, c2, radial, tangent, axis, th * s2, wt * (0.45F + 0.15F * n0), len * s2,
                            cloudColors(layer, tone * 1.08F, gold * 1.15F, deep, fire, flash));
                    }
                }
            }
        }
        // 漏斗深处的光和旋涡口一层淡光
        Vec3 deepEnd = f.sky.add(axis.scale(SkyMeteorEntity.START_DEPTH + 20));
        float core = open * heat;
        sprite(f, CORONA, deepEnd, 70 * core, 0, core, 0.7F * core, 0.32F * core);
        sprite(f, CORONA, f.sky, 160 * open, 0, 0.16F * open + 0.9F * flash, 0.1F * open + 0.75F * flash, 0.05F * open + 0.45F * flash);
        renderRays(f, axis, uv, open);
        renderFireRing(f, axis, uv, ign);
        // 冲出旋涡口那一拍：两圈压缩空气从口沿炸开
        VertexConsumer boom = f.buf.getBuffer(StarfallDraw.additive(StarfallDraw.RING));
        for (int i = 0; i < 2; i++) {
            float s = t - f.brk - i * 3;
            if (s < 0 || s >= 26) continue;
            float p = s / 26, grow = 1 - (1 - p) * (1 - p), br = (float) Math.pow(1 - p, 1.5) * (i == 0 ? 0.85F : 0.5F);
            ring(f.pose, boom, f.sky, f.lead, f.r * (1.1F + 7 * grow), f.r * (0.5F + 1.2F * p), br, br * 0.8F, br * 0.62F);
        }
    }

    /** a × ka + b × kb。 */
    private static float[] tint(float[] a, float ka, float[] b, float kb) {
        float[] c = {a[0] * ka, a[1] * ka, a[2] * ka};
        if (b != null) for (int i = 0; i < 3; i++) c[i] += b[i] * kb;
        return c;
    }

    /** 一个任意朝向的长方体：ay/ax/az 三个单位轴和各自的半边长，col 按 +ay、-ay、+az、-az、+ax、-ax 给每面颜色。 */
    private static void box(PoseStack.Pose pose, VertexConsumer vc, Vec3 c, Vec3 ay, Vec3 ax, Vec3 az, float hy, float hx, float hz, float[][] col) {
        Vec3 x = ax.scale(hx), y = ay.scale(hy), z = az.scale(hz);
        Vec3[][] faces = {
            {c.add(y).subtract(x).subtract(z), c.add(y).add(x).subtract(z), c.add(y).add(x).add(z), c.add(y).subtract(x).add(z)},
            {c.subtract(y).subtract(x).add(z), c.subtract(y).add(x).add(z), c.subtract(y).add(x).subtract(z), c.subtract(y).subtract(x).subtract(z)},
            {c.add(z).subtract(x).add(y), c.add(z).add(x).add(y), c.add(z).add(x).subtract(y), c.add(z).subtract(x).subtract(y)},
            {c.subtract(z).add(x).add(y), c.subtract(z).subtract(x).add(y), c.subtract(z).subtract(x).subtract(y), c.subtract(z).add(x).subtract(y)},
            {c.add(x).add(y).add(z), c.add(x).add(y).subtract(z), c.add(x).subtract(y).subtract(z), c.add(x).subtract(y).add(z)},
            {c.subtract(x).add(y).subtract(z), c.subtract(x).add(y).add(z), c.subtract(x).subtract(y).add(z), c.subtract(x).subtract(y).subtract(z)}};
        for (int i = 0; i < 6; i++) {
            float[] k = col[i];
            if (k[0] + k[1] + k[2] <= 0.003F) continue;
            quad(pose, vc, faces[i][0], faces[i][1], faces[i][2], faces[i][3], 0, 0, 1, 1, k[0], k[1], k[2], 1);
        }
    }

    /** 金色光痕：从漏斗深处沿着漏斗往外喷到镜头这边，像穿过隧道。冲出旋涡口时最亮，之后散掉。 */
    private void renderRays(Frame f, Vec3 axis, Vec3[] uv, float open) {
        float t = f.t, power = (smooth((t - 40) / 20) * (1 - smooth((t - f.brk - 10) / 20)) + spike(t - f.brk, 10)) * open;
        if (power <= 0.01F) return;
        int seed = f.e.getId();
        VertexConsumer vc = f.buf.getBuffer(StarfallDraw.additive(CORONA));
        for (int i = 0; i < 40; i++) {
            float period = 16 + 8 * SkyMeteorEntity.noise(seed, i, 61), s = (t / period + SkyMeteorEntity.noise(seed, i, 62)) % 1;
            float ang = Mth.TWO_PI * (i + 0.7F * SkyMeteorEntity.noise(seed, i, 63)) / 40;
            Vec3 radial = uv[0].scale(Mth.cos(ang)).add(uv[1].scale(Mth.sin(ang)));
            Vec3 from = f.sky.add(axis.scale(SkyMeteorEntity.START_DEPTH * 0.9F)).add(radial.scale(8));
            Vec3 to = f.sky.add(radial.scale(V_RAD[0] * (0.9F + 0.5F * SkyMeteorEntity.noise(seed, i, 64)))).subtract(axis.scale(60));
            Vec3 head = from.add(to.subtract(from).scale(s)), tail = from.add(to.subtract(from).scale(Math.max(0, s - 0.25F)));
            float b = 1.3F * power * Mth.sin(Mth.PI * s) * (0.6F + 0.4F * SkyMeteorEntity.noise(seed, i, 65));
            streak(f.pose, vc, head, tail, 3 + 4 * SkyMeteorEntity.noise(seed, i, 66), b, 0.82F * b, 0.42F * b);
        }
    }

    /** 火环：旋涡口一圈原版火苗往外舔（实心一层、发光一层），外面几团橙光。 */
    private void renderFireRing(Frame f, Vec3 axis, Vec3[] uv, float ign) {
        if (ign <= 0.01F) return;
        int seed = f.e.getId();
        TextureAtlasSprite fire = block("fire_0");
        for (int layer = 0; layer < 2; layer++) {
            VertexConsumer vc = f.buf.getBuffer(layer == 0 ? StarfallDraw.solid(TextureAtlas.LOCATION_BLOCKS) : StarfallDraw.additive(TextureAtlas.LOCATION_BLOCKS));
            for (int i = 0; i < 60; i++) {
                float n0 = SkyMeteorEntity.noise(seed, i, 71), n1 = SkyMeteorEntity.noise(seed, i, 72);
                float ang = Mth.TWO_PI * (i + 0.6F * n0) / 60;
                Vec3 radial = uv[0].scale(Mth.cos(ang)).add(uv[1].scale(Mth.sin(ang)));
                Vec3 base = f.sky.add(radial.scale(V_RAD[0] * (0.78F + 0.25F * n1))).add(axis.scale(4 * n0));
                float flicker = 0.85F + 0.15F * Mth.sin(f.t * 0.7F + i * 1.7F), height = (30 + 20 * n1) * ign * flicker;
                Vec3 dirUp = radial.scale(0.75).subtract(axis.scale(0.45)).normalize();
                float b = layer == 0 ? 1 : 0.45F * ign;
                blockSprite(f.pose, vc, fire, base, dirUp, height * 0.42F, height, b, layer == 0 ? b : 0.7F * b, layer == 0 ? b : 0.45F * b);
            }
        }
        for (int i = 0; i < 8; i++) {
            float ang = Mth.TWO_PI * (i + 0.5F * SkyMeteorEntity.noise(seed, i, 73)) / 8;
            Vec3 at = f.sky.add(uv[0].scale(Mth.cos(ang) * V_RAD[0] * 0.9F)).add(uv[1].scale(Mth.sin(ang) * V_RAD[0] * 0.9F));
            sprite(f, CORONA, at, 55 * ign, 0, 0.5F * ign, 0.24F * ign, 0.08F * ign);
        }
    }

    // ---------- 陨石雨 ----------
    /** 陨石雨：一块块岩浆方块从旋涡口那一圈砸下来，拖着火舌；落地一团火光、一圈贴地热浪、几簇原版火苗。 */
    private void renderRain(Frame f) {
        float t = f.t;
        if (t < f.brk) return;
        SkyMeteorEntity e = f.e;
        int seed = e.getId();
        TextureAtlasSprite lava = block("lava_still"), fire = block("fire_0");
        for (int layer = 0; layer < 2; layer++) {
            VertexConsumer vc = f.buf.getBuffer(layer == 0 ? StarfallDraw.solid(TextureAtlas.LOCATION_BLOCKS) : StarfallDraw.additive(TextureAtlas.LOCATION_BLOCKS));
            for (int i = 0; i < SkyMeteorEntity.RAIN; i++) {
                float p = (t - e.rainStart(i)) / e.rainTime(i);
                if (p < 0 || p >= 1) continue;
                Quaternionf q = new Quaternionf().rotationXYZ(t * 0.2F + i, t * 0.13F, i * 0.7F);
                float b = layer == 0 ? 1 : 0.7F;
                voxel(f.pose, vc, e.rainPos(i, p), q, e.rainSize(i) * (layer == 0 ? 1 : 1.01F), lava, b, b, b, layer == 0);
            }
        }
        VertexConsumer tail = f.buf.getBuffer(StarfallDraw.additive(StarfallDraw.TAIL));
        int frame = (int) (t * 0.5F);
        for (int i = 0; i < SkyMeteorEntity.RAIN; i++) {
            float p = (t - e.rainStart(i)) / e.rainTime(i);
            if (p < 0 || p >= 1) continue;
            Vec3 at = e.rainPos(i, p), dir = e.rainTo(i).subtract(e.rainFrom(i)).normalize();
            float size = e.rainSize(i), len = size * (10 + 16 * p), u0 = ((frame + i) & 3) * 0.25F;
            Vec3 side = dir.cross(eye.subtract(at));
            if (side.lengthSqr() < 1.0E-8) continue;
            side = side.normalize();
            Vec3 head = at.subtract(dir.scale(size * 0.4F)), tip = head.subtract(dir.scale(len));
            quad(f.pose, tail, head.add(side.scale(size * 1.8F)), head.subtract(side.scale(size * 1.8F)), tip.subtract(side.scale(size * 0.25F)),
                tip.add(side.scale(size * 0.25F)), u0, 0, u0 + 0.25F, 1, 1, 0.66F, 0.32F, 1);
        }
        for (int i = 0; i < SkyMeteorEntity.RAIN; i++) {
            float p = (t - e.rainStart(i)) / e.rainTime(i);
            if (p >= 0 && p < 1) sprite(f, CORONA, e.rainPos(i, p), e.rainSize(i) * 4.5F, 0, 1, 0.6F, 0.25F);
        }
        // 落地
        VertexConsumer ring = f.buf.getBuffer(StarfallDraw.additive(StarfallDraw.RING));
        for (int i = 0; i < SkyMeteorEntity.RAIN; i++) {
            float s = t - e.rainStart(i) - e.rainTime(i);
            if (s < 0 || s >= 14) continue;
            float q = s / 14, b = (1 - q) * (1 - q);
            ring(f.pose, ring, e.rainTo(i).add(0, 0.2, 0), new Vec3(0, 1, 0), 2 + 9 * (1 - (1 - q) * (1 - q)), 2 + 2 * q, b, 0.8F * b, 0.55F * b);
        }
        VertexConsumer flames = f.buf.getBuffer(StarfallDraw.solid(TextureAtlas.LOCATION_BLOCKS));
        for (int i = 0; i < SkyMeteorEntity.RAIN; i++) {
            float s = t - e.rainStart(i) - e.rainTime(i);
            if (s < 0 || s >= 14) continue;
            float hgt = 3.5F * (1 - s / 14) * e.rainSize(i) / 2;
            Vec3 to = e.rainTo(i);
            for (int j = 0; j < 3; j++) {
                float a = Mth.TWO_PI * (j + SkyMeteorEntity.noise(seed, i, 90 + j)) / 3;
                blockSprite(f.pose, flames, fire, to.add(Mth.cos(a) * 1.5F, 0, Mth.sin(a) * 1.5F), new Vec3(0, 1, 0), hgt * 0.45F, hgt, 1, 1, 1);
            }
        }
        for (int i = 0; i < SkyMeteorEntity.RAIN; i++) {
            float s = t - e.rainStart(i) - e.rainTime(i);
            if (s < 0 || s >= 14) continue;
            float b = (float) Math.pow(1 - s / 14, 1.5);
            sprite(f, CORONA, e.rainTo(i).add(0, 2, 0), 6 + 10 * (s / 14), 0, b, 0.75F * b, 0.4F * b);
        }
    }

    /** 火星雨：冲出旋涡后满场斜落的细亮线，越近越粗。 */
    private void renderSparks(Frame f) {
        float t = f.t, power = clamp01((t - f.brk) / 8) * (1 - smooth((t - HIT - 5) / 15));
        if (power <= 0.01F) return;
        Vec3 dir = f.lead.scale(0.35).add(0, -1, 0).normalize();
        int seed = f.e.getId();
        VertexConsumer vc = f.buf.getBuffer(StarfallDraw.additive(CORONA));
        for (int i = 0; i < 90; i++) {
            float n0 = SkyMeteorEntity.noise(seed, i, 101), n1 = SkyMeteorEntity.noise(seed, i, 102), n2 = SkyMeteorEntity.noise(seed, i, 103);
            float a = Mth.TWO_PI * n0, rr = 6 + 80 * Mth.sqrt(n1), top = 40 + 60 * n2;
            Vec3 base = new Vec3(Mth.cos(a) * rr, 0, Mth.sin(a) * rr), start = base.subtract(dir.scale(top / -dir.y));
            float s = (t / (10 + 8 * n2) + n1) % 1;
            Vec3 head = start.add(base.subtract(start).scale(s)), tail = start.add(base.subtract(start).scale(Math.max(0, s - 0.08F)));
            float b = power * (0.5F + 0.5F * n0) * Mth.sin(Mth.PI * s);
            streak(f.pose, vc, head, tail, 0.15F + 0.2F * n2, b, 0.75F * b, 0.35F * b);
        }
    }

    /** 撞击：白热火球鼓起再褪成橙红，外面一圈更大更淡的热浪，一根 3 拍冲到顶的光柱。 */
    private void renderBurst(Frame f) {
        float since = f.t - HIT;
        if (since >= 30) return;
        float radius = f.r;
        Vec3 at = new Vec3(0, radius * 0.3F, 0);
        float q = 1 - (1 - clamp01(since / 22)) * (1 - clamp01(since / 22));
        float k = (float) Math.pow(clamp01(1 - since / 26), 1.3), cool = clamp01(since / 18);
        sprite(f, CORONA, at, radius * (1.0F + 2.0F * q), 0, k, k * (0.95F - 0.5F * cool), k * (0.85F - 0.65F * cool));
        float wide = (float) Math.pow(clamp01(1 - since / 30), 2) * 0.45F;
        sprite(f, CORONA, at.add(0, radius * 0.4F, 0), radius * (2.5F + 3.5F * q), 0, wide, wide * 0.55F, wide * 0.28F);
        float col = clamp01(1 - since / 24);
        if (col <= 0) return;
        Vec3 side = new Vec3(0, 1, 0).cross(eye);
        side = side.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : side.normalize();
        Vec3 top = new Vec3(0, 90 * clamp01((since + 1) / 4), 0);
        VertexConsumer vc = f.buf.getBuffer(StarfallDraw.additive(CORONA));
        for (int i = 0; i < 2; i++) {
            float w = radius * (i == 0 ? 0.5F : 0.18F) * (0.35F + 0.65F * col), b = col * (i == 0 ? 0.9F : 1.3F);
            quad(f.pose, vc, side.scale(w), side.scale(-w), top.subtract(side.scale(w * 0.3F)), top.add(side.scale(w * 0.3F)), 0, 0.5F, 1, 1,
                b, b * (i == 0 ? 0.82F : 0.98F), b * (i == 0 ? 0.62F : 0.92F), 1);
        }
    }

    // ---------- 落地之后 ----------
    /**
     * 冲击：一圈白热的贴地激波往外推（落地后 14 拍再来一圈小的），激波上立着一道往上舔的火墙，
     * 地上一道道放射光痕往外甩，坑上鼓起一团被火光从底下照亮的蘑菇云。
     */
    private void renderBlast(Frame f) {
        float s = f.t - HIT;
        if (s >= 80) return;
        int seed = f.e.getId();
        Vec3 upY = new Vec3(0, 1, 0);
        for (int w = 0; w < 2; w++) {
            float a = s - (w == 0 ? 0 : 14);
            if (a < 0 || a >= 34) continue;
            float p = a / 34, grow = 1 - (float) Math.pow(1 - p, 2.2), amp = w == 0 ? 1 : 0.6F;
            float rad = f.arena * 3 * grow * (w == 0 ? 1 : 0.7F) + 3, b = (float) Math.pow(1 - p, 1.2) * 1.2F * amp;
            VertexConsumer rv = f.buf.getBuffer(StarfallDraw.additive(StarfallDraw.RING));
            ring(f.pose, rv, new Vec3(0, 0.3, 0), upY, rad, 10 + 14 * p, b, 0.85F * b, 0.6F * b);
            ring(f.pose, rv, new Vec3(0, 0.35, 0), upY, rad * 0.82F, 6, 0.5F * b, 0.38F * b, 0.22F * b);
            float wall = (float) Math.pow(1 - p, 1.3) * amp, hgt = (26 * (float) Math.pow(1 - p, 0.7) + 4) * amp;
            VertexConsumer wv = f.buf.getBuffer(StarfallDraw.additive(BLAST_WALL));
            int n = 48;
            for (int j = 0; j < n; j++) {
                float a0 = j * Mth.TWO_PI / n, a1 = (j + 1) * Mth.TWO_PI / n, u0 = j * 6F / n, u1 = (j + 1) * 6F / n;
                Vec3 b0 = new Vec3(Mth.cos(a0) * rad, 0, Mth.sin(a0) * rad), b1 = new Vec3(Mth.cos(a1) * rad, 0, Mth.sin(a1) * rad);
                quad(f.pose, wv, b0.add(0, hgt, 0), b1.add(0, hgt, 0), b1, b0, u0, 0, u1, 1, wall, 0.8F * wall, 0.5F * wall, 1);
            }
            VertexConsumer sv = f.buf.getBuffer(StarfallDraw.additive(CORONA));
            for (int i = 0; i < 56; i++) {
                float n0 = SkyMeteorEntity.noise(seed, i + w * 64, 111), ang = Mth.TWO_PI * (i + 0.8F * n0) / 56;
                Vec3 d = new Vec3(Mth.cos(ang), 0, Mth.sin(ang));
                float head = rad * (0.75F + 0.3F * SkyMeteorEntity.noise(seed, i + w * 64, 112)), len = 6 + 22 * (1 - p);
                float bb = b * (0.6F + 0.4F * n0);
                streak(f.pose, sv, d.scale(head).add(0, 0.4, 0), d.scale(Math.max(0, head - len)).add(0, 0.4, 0), 0.5F + 0.8F * n0, bb, 0.8F * bb, 0.5F * bb);
            }
        }
        renderDome(f, s);
    }

    /** 蘑菇云：落地后鼓起的一团压扁的半球烟，边上一圈被火光照亮，越升越高、越摊越大，40 拍后慢慢散。 */
    private void renderDome(Frame f, float s) {
        if (s < 2 || s > 75) return;
        float p = (s - 2) / 73, rad = f.arena * (0.6F + 1.3F * (1 - (1 - p) * (1 - p))), hgt = rad * 0.55F, lift = 6 + 14 * p;
        float alpha = clamp01(s / 6) * (1 - smooth((s - 35) / 40)) * 0.85F;
        if (alpha <= 0.01F) return;
        int rings = 7, seg = 32;
        float arc = 95 * Mth.DEG_TO_RAD, scroll = s * 0.01F;
        VertexConsumer vc = f.buf.getBuffer(RenderType.entityTranslucent(BLAST_SMOKE));
        for (int j = 0; j < rings; j++) {
            float t0 = arc * j / rings, t1 = arc * (j + 1) / rings;
            for (int i = 0; i < seg; i++) {
                float p0 = Mth.TWO_PI * i / seg, p1 = Mth.TWO_PI * (i + 1) / seg;
                Vec3 a = domePoint(t0, p0, rad, hgt, lift), b = domePoint(t0, p1, rad, hgt, lift), c = domePoint(t1, p1, rad, hgt, lift), d = domePoint(t1, p0, rad, hgt, lift);
                float lit = 0.75F + 0.35F * (j + 1) / rings;
                quad(f.pose, vc, a, b, c, d, i * 4F / seg, j * 2F / rings - scroll, (i + 1) * 4F / seg, (j + 1) * 2F / rings - scroll,
                    lit, 0.8F * lit, 0.7F * lit, alpha);
            }
        }
        float glow = (1 - p) * 0.6F;
        sprite(f, CORONA, new Vec3(0, lift, 0), rad * 1.2F, 0, glow, 0.5F * glow, 0.2F * glow);
    }

    private static Vec3 domePoint(float th, float ph, float rad, float hgt, float lift) {
        return new Vec3(Mth.sin(th) * Mth.cos(ph) * rad, lift + Mth.cos(th) * hgt, Mth.sin(th) * Mth.sin(ph) * rad);
    }

    /**
     * 崩开：星面上挑出的体素带着撞击那一刻的朝向，从各自的位置往外、往上崩出去，边飞边翻；
     * 从岩浆冷成岩浆块再冷成黑石（每块冷得快慢不一样），落地后停住，60 拍后 20 拍缩没。
     */
    private void renderDebris(Frame f) {
        float s = f.t - HIT - SINK;
        if (s < 0) return;
        int seed = f.e.getId();
        Quaternionf q0 = starRotation(HIT + SINK, f.lead);
        Vec3 core = f.e.starPos(HIT).add(0, -0.6F * f.r, 0);
        float vs = 1.1F * f.r / GRID, g = 0.055F;
        TextureAtlasSprite lava = block("lava_still"), magma = block("magma"), black = block("blackstone");
        List<Object[]> chunks = new ArrayList<>();
        for (int i = 0; i < STAR_SURFACE.length; i += 5) {
            int[] v = STAR_SURFACE[i];
            float n0 = SkyMeteorEntity.noise(seed, i, 81), n1 = SkyMeteorEntity.noise(seed, i, 82);
            Vec3 o = rotate(q0, v[0], v[1], v[2]);
            Vec3 vel = o.normalize().add(0, 0.9, 0).normalize().scale(0.9F + 0.9F * n0);
            Vec3 p0 = core.add(o.scale(vs * 0.7F));
            float half = vs * (0.3F + 0.2F * n1);
            double disc = vel.y * vel.y + 2 * g * (p0.y - half);
            if (disc < 0) continue;
            float land = (float) ((vel.y + Math.sqrt(disc)) / g), fs = Math.min(s, land);
            Vec3 pos = p0.add(vel.scale(fs)).add(0, -0.5F * g * fs * fs, 0);
            float sc = clamp01(1 - (s - land - 60) / 20);
            if (sc <= 0.01F || pos.distanceToSqr(eye) < half * half * 4) continue;
            Vec3 ax = new Vec3(SkyMeteorEntity.noise(seed, i, 83) - 0.5, SkyMeteorEntity.noise(seed, i, 84) - 0.5, SkyMeteorEntity.noise(seed, i, 85) - 0.5);
            if (ax.lengthSqr() < 1.0E-4) ax = new Vec3(0, 1, 0);
            ax = ax.normalize();
            Quaternionf q = new Quaternionf().rotationAxis(fs * (0.08F + 0.12F * n1), (float) ax.x, (float) ax.y, (float) ax.z).mul(q0);
            int cool = s < 12 + 20 * n0 ? 0 : s < 30 + 30 * n1 ? 1 : 2;
            chunks.add(new Object[]{pos, q, half * sc, cool});
        }
        for (int layer = 0; layer < 2; layer++) {
            VertexConsumer vc = f.buf.getBuffer(layer == 0 ? StarfallDraw.solid(TextureAtlas.LOCATION_BLOCKS) : StarfallDraw.additive(TextureAtlas.LOCATION_BLOCKS));
            for (Object[] c : chunks) {
                int cool = (int) c[3];
                if (layer == 1 && cool == 2) continue;
                TextureAtlasSprite sp = cool == 0 ? lava : cool == 1 ? magma : black;
                float b = layer == 0 ? 1 : cool == 0 ? 0.8F : 0.4F;
                voxel(f.pose, vc, (Vec3) c[0], (Quaternionf) c[1], (float) c[2] * (layer == 0 ? 1 : 1.01F), sp, b, b, b, layer == 0);
            }
        }
    }

    /** 熔岩缝：落地后从坑心往外一格一格裂开（原版岩浆贴图，贴着方块网格）；最后 40 拍慢慢熄掉。 */
    private void renderCracks(Frame f) {
        float s = f.t - HIT;
        if (s < 4) return;
        float life = SkyMeteorEntity.LIFE - HIT, fade = 1 - smooth((s - (life - 70)) / 40);
        if (fade <= 0) return;
        float reach = f.arena * 1.2F * clamp01((s - 4) / 24);
        TextureAtlasSprite lava = block("lava_still");
        VertexConsumer vc = f.buf.getBuffer(StarfallDraw.additive(TextureAtlas.LOCATION_BLOCKS));
        for (int[] c : f.e.crackCells()) {
            if (c[2] > reach * 10) continue;
            float edge = clamp01((reach * 10 - c[2]) / 30), b = fade * edge * (0.8F + 0.2F * Mth.sin(f.t * 0.1F + c[0] * 0.7F + c[1] * 1.3F));
            quad(f.pose, vc, new Vec3(c[0], 0.03, c[1]), new Vec3(c[0] + 1, 0.03, c[1]), new Vec3(c[0] + 1, 0.03, c[1] + 1), new Vec3(c[0], 0.03, c[1] + 1),
                lava.getU0(), lava.getV0(), lava.getU1(), lava.getV1(), b, b, b, 1);
        }
    }

    /** 悬石：落地 16 拍后坑里坑外飘起一圈碎岩，慢慢转着悬在半空，最后一起落回地里。 */
    private void renderFloaters(Frame f) {
        float s = f.t - HIT - 16;
        if (s < 0) return;
        int seed = f.e.getId();
        TextureAtlasSprite magma = block("magma"), black = block("blackstone");
        for (int layer = 0; layer < 2; layer++) {
            VertexConsumer vc = f.buf.getBuffer(layer == 0 ? StarfallDraw.solid(TextureAtlas.LOCATION_BLOCKS) : StarfallDraw.additive(TextureAtlas.LOCATION_BLOCKS));
            for (int i = 0; i < 24; i++) {
                float n0 = SkyMeteorEntity.noise(seed, i, 121), n1 = SkyMeteorEntity.noise(seed, i, 122), n2 = SkyMeteorEntity.noise(seed, i, 123);
                boolean hot = n2 < 0.35F;
                if (layer == 1 && !hot) continue;
                float a = Mth.TWO_PI * n0, rr = f.arena * (0.2F + 1.0F * n1), h = 3 + 16 * n2;
                float y = h * smooth(s / 40) - (h + 4) * smooth((s - 70) / 25) + 0.4F * Mth.sin(f.t * 0.05F + i);
                if (y < -2) continue;
                float half = 0.6F + 1.6F * n1;
                Vec3 at = new Vec3(Mth.cos(a) * rr, y, Mth.sin(a) * rr);
                Quaternionf q = new Quaternionf().rotationXYZ(f.t * 0.02F * (0.5F + n0) + i, f.t * 0.015F + n2 * 3, i * 0.9F);
                float b = layer == 0 ? 1 : 0.35F;
                voxel(f.pose, vc, at, q, half * (layer == 0 ? 1 : 1.01F), hot ? magma : black, b, b, b, layer == 0);
            }
        }
    }
}
