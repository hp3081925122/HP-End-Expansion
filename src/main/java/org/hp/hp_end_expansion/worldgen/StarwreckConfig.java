package org.hp.hp_end_expansion.worldgen;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class StarwreckConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue ENABLED;
    private static final ModConfigSpec.BooleanValue TIDELIGHT;
    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        ENABLED = builder.comment("启用末地外岛高地、中地和荒地的星骸荒原连片替换；保留主岛和小岛群系。更改后重启，仅影响新区块。 Enable Starwreck regions across End highlands, midlands and barrens; preserve the central End and small-island biomes. Restart required, new chunks only.")
            .define("enableStarwreckBiome", true);
        TIDELIGHT = builder.comment("启用潮光礁海及其结构；与星骸共用互斥区域，高地、中地和荒地统一替换，保留主岛和小岛。重启后影响新区块。 Enable Tidelight Reef and its structures in exclusive regions shared with Starwreck across End highlands, midlands and barrens. Preserve the central End and small islands. Restart required, new chunks only.")
            .define("enableTidelightReef", true);
        SPEC = builder.build();
    }
    public static boolean enabled() { return !SPEC.isLoaded() || ENABLED.get(); }
    public static boolean tidelightEnabled() { return SPEC.isLoaded() && TIDELIGHT.get(); }
}
