package org.hp.hp_end_expansion.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.hp.hp_end_expansion.Hp_end_expansion;

public final class RiftVfxDraw {
    // 特效贴图
    public static final ResourceLocation ARC = tex("rift_arc");
    public static final ResourceLocation PORTAL = tex("rift_portal");
    public static final ResourceLocation PORTAL_GLOW = tex("rift_portal_glow");
    public static final ResourceLocation CRACK = tex("rift_crack");
    public static final ResourceLocation SPIKE = tex("rift_spike");

    private RiftVfxDraw() {
    }

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(Hp_end_expansion.MODID, "textures/effect/" + name + ".png");
    }

    // 加法混合、全亮，用于弧光、辉光与地裂
    public static RenderType additive(ResourceLocation texture) {
        return RenderType.eyes(texture);
    }

    // 透明全亮、双面，用于裂隙本体与晶刺
    public static RenderType translucent(ResourceLocation texture) {
        return RenderType.entityTranslucentEmissive(texture);
    }

    // 输出一个四边形，double 为真时补一个反向面
    public static void quad(PoseStack.Pose pose, VertexConsumer buffer, Vec3 a, Vec3 b, Vec3 c, Vec3 d,
                            float u0, float v0, float u1, float v1, int color, boolean doubleSided) {
        vertex(pose, buffer, a, u0, v0, color);
        vertex(pose, buffer, b, u1, v0, color);
        vertex(pose, buffer, c, u1, v1, color);
        vertex(pose, buffer, d, u0, v1, color);
        if (doubleSided) {
            vertex(pose, buffer, d, u0, v1, color);
            vertex(pose, buffer, c, u1, v1, color);
            vertex(pose, buffer, b, u1, v0, color);
            vertex(pose, buffer, a, u0, v0, color);
        }
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, Vec3 p, float u, float v, int color) {
        buffer.addVertex(pose, (float) p.x, (float) p.y, (float) p.z)
            .setColor(color)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(LightTexture.FULL_BRIGHT)
            .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    // 按亮度缩放颜色，加法混合下用于淡出
    public static int fade(float brightness) {
        int c = Math.round(Math.max(0.0F, Math.min(1.0F, brightness)) * 255.0F);
        return 0xFF000000 | (c << 16) | (c << 8) | c;
    }

    // 透明度淡出
    public static int alpha(float alpha) {
        int a = Math.round(Math.max(0.0F, Math.min(1.0F, alpha)) * 255.0F);
        return (a << 24) | 0xFFFFFF;
    }
}
