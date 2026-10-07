package org.hp.hp_end_expansion.client;

import net.minecraft.resources.ResourceLocation;
import org.hp.hp_end_expansion.Hp_end_expansion;

public final class VoidRayDraw {
    // 虚空紫特效贴图：裂隙螳后使用
    public static final ResourceLocation BEAM = tex("void_beam");
    public static final ResourceLocation RING = tex("void_ring");
    public static final ResourceLocation RUNE = tex("void_rune");
    public static final ResourceLocation WARN = tex("void_warn");
    public static final ResourceLocation STAR = tex("void_star");
    // 潮光青特效贴图：裂空鳐、噬星鳐王与星核使用
    public static final ResourceLocation TIDE_BEAM = tex("tide_beam");
    public static final ResourceLocation TIDE_RING = tex("tide_ring");
    public static final ResourceLocation TIDE_RUNE = tex("tide_rune");
    public static final ResourceLocation TIDE_WARN = tex("tide_warn");
    public static final ResourceLocation TIDE_STAR = tex("tide_star");
    public static final ResourceLocation TIDE_ARC = tex("tide_arc");

    // 一整套特效贴图
    public record Palette(ResourceLocation beam, ResourceLocation ring, ResourceLocation rune, ResourceLocation warn, ResourceLocation star) {
    }

    public static final Palette VOID = new Palette(BEAM, RING, RUNE, WARN, STAR);
    public static final Palette TIDE = new Palette(TIDE_BEAM, TIDE_RING, TIDE_RUNE, TIDE_WARN, TIDE_STAR);

    // 按特效归属选择贴图套装
    public static Palette of(boolean tide) {
        if (tide) {
            return TIDE;
        }
        return VOID;
    }

    private VoidRayDraw() {
    }

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(Hp_end_expansion.MODID, "textures/effect/" + name + ".png");
    }
}
