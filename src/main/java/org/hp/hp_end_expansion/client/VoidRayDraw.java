package org.hp.hp_end_expansion.client;

import net.minecraft.resources.ResourceLocation;
import org.hp.hp_end_expansion.Hp_end_expansion;

public final class VoidRayDraw {
    // 裂空鳐特效贴图
    public static final ResourceLocation BEAM = tex("void_beam");
    public static final ResourceLocation RING = tex("void_ring");
    public static final ResourceLocation RUNE = tex("void_rune");
    public static final ResourceLocation WARN = tex("void_warn");
    public static final ResourceLocation STAR = tex("void_star");

    private VoidRayDraw() {
    }

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(Hp_end_expansion.MODID, "textures/effect/" + name + ".png");
    }
}
