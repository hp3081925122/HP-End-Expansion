package org.hp.hp_end_expansion.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.hp.hp_end_expansion.Hp_end_expansion;

/** 深渊守望者特效的绘制工具：发光部分走 eyes（叠加发光），晶体走半透明自发光。所有面两面都画。 */
final class AbyssFx {
    static final ResourceLocation GLYPH = tex("glyph"), CRYSTAL = tex("crystal"), ARC = tex("arc"), RING = tex("ring"),
        VORTEX = tex("vortex"), BEAM = tex("beam"), FLARE = tex("flare");

    private AbyssFx() {}

    private static ResourceLocation tex(String n) {
        return ResourceLocation.fromNamespaceAndPath(Hp_end_expansion.MODID, "textures/entity/abyss_fx_" + n + ".png");
    }

    /** 叠加发光：亮度即不透明度，k 是 0-1 的强度。 */
    static VertexConsumer glow(MultiBufferSource b, ResourceLocation t) { return b.getBuffer(RenderType.eyes(t)); }
    static VertexConsumer solid(MultiBufferSource b, ResourceLocation t) { return b.getBuffer(RenderType.entityTranslucentEmissive(t)); }

    static void v(PoseStack.Pose p, VertexConsumer vc, Vec3 pos, float u, float v, float k) {
        float c = Mth.clamp(k, 0, 1);
        vc.addVertex(p, (float) pos.x, (float) pos.y, (float) pos.z).setColor(c, c, c, c).setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(p, 0, 1, 0);
    }

    /** a(u0,v0) b(u1,v0) c(u1,v1) d(u0,v1)，两面。 */
    static void quad(PoseStack.Pose p, VertexConsumer vc, Vec3 a, Vec3 b, Vec3 c, Vec3 d, float u0, float v0, float u1, float v1, float k) {
        v(p, vc, a, u0, v0, k); v(p, vc, b, u1, v0, k); v(p, vc, c, u1, v1, k); v(p, vc, d, u0, v1, k);
        v(p, vc, d, u0, v1, k); v(p, vc, c, u1, v1, k); v(p, vc, b, u1, v0, k); v(p, vc, a, u0, v0, k);
    }

    /** 贴地方形贴花，中心 c、半边长 r、绕 Y 转 rot（弧度）。 */
    static void decal(PoseStack.Pose p, VertexConsumer vc, Vec3 c, double r, double rot, float k) {
        double s = Math.sin(rot) * r, co = Math.cos(rot) * r;
        Vec3 ax = new Vec3(co, 0, s), az = new Vec3(-s, 0, co);
        quad(p, vc, c.subtract(ax).subtract(az), c.add(ax).subtract(az), c.add(ax).add(az), c.subtract(ax).add(az), 0, 0, 1, 1, k);
    }

    /** 朝向镜头的方片。 */
    static void billboard(PoseStack.Pose p, VertexConsumer vc, Vec3 c, Vec3 toCam, double size, double rot, float k) {
        Vec3 f = toCam.normalize();
        Vec3 r = f.cross(new Vec3(0, 1, 0));
        if (r.lengthSqr() < 1.0E-4) r = new Vec3(1, 0, 0);
        r = r.normalize();
        Vec3 u = r.cross(f).normalize();
        Vec3 a = r.scale(Math.cos(rot)).add(u.scale(Math.sin(rot))).scale(size);
        Vec3 b = u.scale(Math.cos(rot)).subtract(r.scale(Math.sin(rot))).scale(size);
        quad(p, vc, c.subtract(a).subtract(b), c.add(a).subtract(b), c.add(a).add(b), c.subtract(a).add(b), 0, 0, 1, 1, k);
    }

    /** 平放的环带：内半径 r0（透明端）到外半径 r1（亮端），环带贴图沿圆周平铺 tiles 次。 */
    static void flatRing(PoseStack.Pose p, VertexConsumer vc, Vec3 c, double r0, double r1, int seg, float tiles, float spin, float k) {
        for (int i = 0; i < seg; i++) {
            double a0 = (i / (double) seg) * Math.PI * 2 + spin, a1 = ((i + 1) / (double) seg) * Math.PI * 2 + spin;
            float u0 = i * tiles / seg, u1 = (i + 1) * tiles / seg;
            quad(p, vc, at(c, a0, r0, 0), at(c, a1, r0, 0), at(c, a1, r1, 0), at(c, a0, r1, 0), u0, 0, u1, 1, k);
        }
    }

    /** 立起的环墙：半径 r，底部亮、顶部透明，可外扩 lean。 */
    static void wallRing(PoseStack.Pose p, VertexConsumer vc, Vec3 c, double r, double h, double lean, int seg, float tiles, float k) {
        for (int i = 0; i < seg; i++) {
            double a0 = (i / (double) seg) * Math.PI * 2, a1 = ((i + 1) / (double) seg) * Math.PI * 2;
            float u0 = i * tiles / seg, u1 = (i + 1) * tiles / seg;
            quad(p, vc, at(c, a0, r + lean, h), at(c, a1, r + lean, h), at(c, a1, r, 0), at(c, a0, r, 0), u0, 0, u1, 1, k);
        }
    }

    /** 竖立的圆环（法线沿 axis），用于咆哮音波：内半径 r0 透明到外 r1 亮。 */
    static void axialRing(PoseStack.Pose p, VertexConsumer vc, Vec3 c, Vec3 axis, double r0, double r1, int seg, float k) {
        Vec3 n = axis.normalize();
        Vec3 x = n.cross(new Vec3(0, 1, 0));
        if (x.lengthSqr() < 1.0E-4) x = new Vec3(1, 0, 0);
        x = x.normalize();
        Vec3 y = x.cross(n).normalize();
        for (int i = 0; i < seg; i++) {
            double a0 = i * Math.PI * 2 / seg, a1 = (i + 1) * Math.PI * 2 / seg;
            Vec3 d0 = x.scale(Math.cos(a0)).add(y.scale(Math.sin(a0))), d1 = x.scale(Math.cos(a1)).add(y.scale(Math.sin(a1)));
            quad(p, vc, c.add(d0.scale(r0)), c.add(d1.scale(r0)), c.add(d1.scale(r1)), c.add(d0.scale(r1)), i * 8F / seg, 0, (i + 1) * 8F / seg, 1, k);
        }
    }

    /** 水平弧刃：从 head 角往回拖 trail 弧度，半径 r0-r1，高度 y；弧光贴图 u=0 在刃尖。 */
    static void arcBlade(PoseStack.Pose p, VertexConsumer vc, Vec3 c, double head, double trail, double r0, double r1, double tilt, int seg, float k) {
        for (int i = 0; i < seg; i++) {
            double t0 = i / (double) seg, t1 = (i + 1) / (double) seg;
            double a0 = head - trail * t0, a1 = head - trail * t1;
            quad(p, vc, at(c, a0, r1, tilt * r1), at(c, a1, r1, tilt * r1), at(c, a1, r0, tilt * r0), at(c, a0, r0, tilt * r0),
                (float) t0, 1, (float) t1, 0, k);
        }
    }

    /** 四棱晶刺：底面中心 base，沿 dir 长 len，底半宽 w，绕自身转 spin。 */
    static void crystal(PoseStack.Pose p, VertexConsumer vc, Vec3 base, Vec3 dir, double len, double w, double spin, float k) {
        Vec3 n = dir.normalize();
        Vec3 x = n.cross(new Vec3(0, 0, 1));
        if (x.lengthSqr() < 1.0E-4) x = new Vec3(1, 0, 0);
        x = x.normalize();
        Vec3 y = x.cross(n).normalize();
        Vec3 tip = base.add(n.scale(len));
        Vec3 mid = base.add(n.scale(len * 0.72));
        for (int i = 0; i < 4; i++) {
            double a0 = spin + i * Math.PI / 2, a1 = a0 + Math.PI / 2;
            Vec3 d0 = x.scale(Math.cos(a0)).add(y.scale(Math.sin(a0))).scale(w), d1 = x.scale(Math.cos(a1)).add(y.scale(Math.sin(a1))).scale(w);
            // 柱身 + 尖顶两段，尖顶贴图取最上面 1/4
            quad(p, vc, mid.add(d0), mid.add(d1), base.add(d1), base.add(d0), 0, 0.25F, 1, 1, k);
            quad(p, vc, tip, tip, mid.add(d1), mid.add(d0), 0.5F, 0, 0.5F, 0.25F, k);
        }
    }

    /** 从 head 沿 axis 拉出朝向镜头的长条。 */
    static void ribbon(PoseStack.Pose p, VertexConsumer vc, Vec3 head, Vec3 axis, Vec3 toCam, double len, double w, float v0, float v1, float k) {
        Vec3 side = axis.cross(toCam);
        if (side.lengthSqr() < 1.0E-6) side = axis.cross(new Vec3(1, 0, 0));
        side = side.normalize().scale(w);
        Vec3 end = head.add(axis.scale(len));
        quad(p, vc, head.add(side), head.subtract(side), end.subtract(side), end.add(side), 0, v0, 1, v1, k);
    }

    private static Vec3 at(Vec3 c, double a, double r, double y) {
        return c.add(Math.cos(a) * r, y, Math.sin(a) * r);
    }
}
