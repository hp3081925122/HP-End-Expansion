package org.hp.hp_end_expansion.client;

import net.minecraft.resources.Identifier;
import org.hp.hp_end_expansion.Hp_end_expansion;

public final class VoidRayDraw {
    // 虚空紫特效贴图：裂隙螳后使用
    public static final Identifier BEAM = tex("void_beam");
    public static final Identifier RING = tex("void_ring");
    public static final Identifier RUNE = tex("void_rune");
    public static final Identifier WARN = tex("void_warn");
    public static final Identifier STAR = tex("void_star");
    // 潮光青特效贴图：裂空鳐、噬星鳐王与星核使用
    public static final Identifier TIDE_BEAM = tex("tide_beam");
    public static final Identifier TIDE_RING = tex("tide_ring");
    public static final Identifier TIDE_RUNE = tex("tide_rune");
    public static final Identifier TIDE_WARN = tex("tide_warn");
    public static final Identifier TIDE_STAR = tex("tide_star");
    public static final Identifier TIDE_ARC = tex("tide_arc");

    // 一整套特效贴图
    public record Palette(Identifier beam, Identifier ring, Identifier rune, Identifier warn, Identifier star) {
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

    private static Identifier tex(String name) {
        return Identifier.fromNamespaceAndPath(Hp_end_expansion.MODID, "textures/effect/" + name + ".png");
    }
}
