package org.hp.hp_end_expansion.worldgen;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;

public interface StarwreckBiomeAccess {
    void hp_setStarwreck(Holder<Biome> biome);
    void hp_setTidelight(Holder<Biome> biome);
}
