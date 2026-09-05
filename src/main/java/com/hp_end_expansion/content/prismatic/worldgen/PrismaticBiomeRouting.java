package com.hp_end_expansion.content.prismatic.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.TheEndBiomeSource;

public final class PrismaticBiomeRouting {
    public static final ResourceKey<Biome> WASTES = ResourceKey.create(Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath("hp_end_expansion", "prismatic_wastes"));

    // 在原版编解码之后从同一注册表上下文取得群系，不改变末地存档格式。
    public static MapCodec<TheEndBiomeSource> withBiome(MapCodec<TheEndBiomeSource> original) {
        return RecordCodecBuilder.mapCodec(instance -> instance.group(
                original.forGetter(source -> source), RegistryOps.<Biome, TheEndBiomeSource>retrieveElement(WASTES))
                .apply(instance, (source, biome) -> {
                    ((PrismaticBiomeAccess) source).prismatic$setBiome(biome);
                    // 低噪声调试确认引用来自正式解码上下文，原版引导阶段不再查询模组群系。
                    LogUtils.getLogger().debug("Bound prismatic biome to decoded End biome source: {}", biome.key().location());
                    return source;
                }));
    }

    // 平滑格点噪声形成连片荒原；不读取区块、不缓存世界，也不影响原版地形密度。
    public static double patch(int blockX, int blockZ, int scale, long salt) {
        double x = (double) blockX / scale;
        double z = (double) blockZ / scale;
        int cellX = (int) Math.floor(x);
        int cellZ = (int) Math.floor(z);
        double dx = x - cellX;
        double dz = z - cellZ;
        dx = dx * dx * (3.0 - 2.0 * dx);
        dz = dz * dz * (3.0 - 2.0 * dz);
        double a = value(cellX, cellZ, salt) * (1.0 - dx) + value(cellX + 1, cellZ, salt) * dx;
        double b = value(cellX, cellZ + 1, salt) * (1.0 - dx) + value(cellX + 1, cellZ + 1, salt) * dx;
        return a * (1.0 - dz) + b * dz;
    }

    // 整数散列只提供稳定的空间取样值，坐标为负时仍连续。
    private static double value(int x, int z, long salt) {
        long value = (x * 341873128712L + z * 132897987541L) ^ salt;
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        value ^= value >>> 31;
        return (value >>> 11) * 0x1.0p-53;
    }

    private PrismaticBiomeRouting() { }
}
