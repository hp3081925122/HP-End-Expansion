package org.hp.hp_end_expansion.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.hp.hp_end_expansion.Hp_end_expansion;

/** 星雨特效共用的贴图和画四边形的工具。加法层的亮度直接写在顶点颜色的 RGB 里。 */
public final class StarfallDraw {
    public static final ResourceLocation RIFT = tex("star_rift");
    public static final ResourceLocation RIFT_GLOW = tex("star_rift_glow");
    public static final ResourceLocation HALO = tex("star_halo");
    public static final ResourceLocation TAIL = tex("meteor_tail");
    public static final ResourceLocation RING = tex("heat_ring");
    public static final ResourceLocation SCORCH = tex("star_scorch");
    public static final ResourceLocation METEOR = tex("meteor");
    public static final ResourceLocation METEOR_GLOW = tex("meteor_glow");

    private StarfallDraw() {}

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(Hp_end_expansion.MODID, "textures/effect/" + name + ".png");
    }

    public static RenderType additive(ResourceLocation texture) { return RenderType.eyes(texture); }
    public static RenderType solid(ResourceLocation texture) { return RenderType.entityCutoutNoCull(texture); }

    public static void vertex(PoseStack.Pose pose, VertexConsumer buffer, Vec3 p, float u, float v, float r, float g, float b, float a) {
        buffer.addVertex(pose, (float) p.x, (float) p.y, (float) p.z)
            .setColor(r, g, b, a)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(LightTexture.FULL_BRIGHT)
            .setNormal(pose, 0, 1, 0);
    }

    /** a(u0,v0) b(u1,v0) c(u1,v1) d(u0,v1)，两面都画。rgb 已乘好亮度。 */
    public static void quad(PoseStack.Pose pose, VertexConsumer buffer, Vec3 a, Vec3 b, Vec3 c, Vec3 d,
                            float u0, float v0, float u1, float v1, float r, float g, float bl) {
        vertex(pose, buffer, a, u0, v0, r, g, bl, 1);
        vertex(pose, buffer, b, u1, v0, r, g, bl, 1);
        vertex(pose, buffer, c, u1, v1, r, g, bl, 1);
        vertex(pose, buffer, d, u0, v1, r, g, bl, 1);
        vertex(pose, buffer, d, u0, v1, r, g, bl, 1);
        vertex(pose, buffer, c, u1, v1, r, g, bl, 1);
        vertex(pose, buffer, b, u1, v0, r, g, bl, 1);
        vertex(pose, buffer, a, u0, v0, r, g, bl, 1);
    }

    /** 当前姿态平面上的矩形，x 从 x0 到 x1，y 从 y0 到 y1，放在 z。 */
    public static void rect(PoseStack.Pose pose, VertexConsumer buffer, float x0, float y0, float x1, float y1, float z,
                            float u0, float v0, float u1, float v1, float r, float g, float b) {
        quad(pose, buffer, new Vec3(x0, y1, z), new Vec3(x1, y1, z), new Vec3(x1, y0, z), new Vec3(x0, y0, z), u0, v0, u1, v1, r, g, b);
    }

    /** 朝向镜头的飘带：从 head 沿 axis 拉 length，头宽 w0、尾宽 w1。v=0 在头。 */
    public static void ribbon(PoseStack.Pose pose, VertexConsumer buffer, Vec3 head, Vec3 axis, Vec3 toCamera, float length,
                              float w0, float w1, float u0, float u1, float r, float g, float b) {
        Vec3 side = axis.cross(toCamera);
        if (side.lengthSqr() < 1.0E-6) side = axis.cross(new Vec3(1, 0, 0));
        side = side.normalize();
        Vec3 end = head.add(axis.scale(length));
        quad(pose, buffer, head.add(side.scale(w0)), head.subtract(side.scale(w0)), end.subtract(side.scale(w1)), end.add(side.scale(w1)),
            u0, 0, u1, 1, r, g, b);
    }
}
