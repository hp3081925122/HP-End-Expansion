package com.hp_end_expansion.content.prismatic.worldgen;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;

// 群系引用属于单个末地来源实例，避免跨存档保存动态注册表对象。
public interface PrismaticBiomeAccess {
    void prismatic$setBiome(Holder<Biome> biome);
}
