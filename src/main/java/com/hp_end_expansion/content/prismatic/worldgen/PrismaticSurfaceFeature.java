package com.hp_end_expansion.content.prismatic.worldgen;

import com.hp_end_expansion.content.prismatic.PrismaticContent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public final class PrismaticSurfaceFeature extends Feature<NoneFeatureConfiguration> {
    // 空配置特征每区块执行一次，具体材料始终来自已完成注册的内容入口。
    public PrismaticSurfaceFeature() { super(NoneFeatureConfiguration.CODEC); }

    // 只覆写当前区块的天然末地石，保留岛屿下方、虚空和人工结构。
    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        int startX = context.origin().getX() & ~15;
        int startZ = context.origin().getZ() & ~15;
        BlockState chalk = PrismaticContent.block("chalkstone").defaultBlockState();
        BlockState soil = PrismaticContent.block("prism_soil").defaultBlockState();
        BlockState ore = PrismaticContent.block("lumen_ore").defaultBlockState();
        boolean placed = false;
        // 每柱最多改三层壳岩，矿脉探测最多向下十二格，不进行邻区块扫描。
        for (int x = startX; x < startX + 16; x++) {
            for (int z = startZ; z < startZ + 16; z++) {
                int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
                BlockPos top = new BlockPos(x, y, z);
                if (y < 40 || !level.getBiome(top).is(PrismaticBiomeRouting.WASTES) || !level.getBlockState(top).is(Blocks.END_STONE)) continue;
                double band = PrismaticBiomeRouting.patch(x, z, 28, 0x534f494cL);
                for (int depth = 0; depth < 3; depth++) {
                    BlockPos pos = top.below(depth);
                    if (level.getBlockState(pos).is(Blocks.END_STONE)) level.setBlock(pos, depth == 0 && band > 0.37 ? soil : chalk, 2);
                }
                // 储光矿为小型离散脉，少量露头可直接发现，不必挖空整座岛。
                if (context.random().nextInt(19) == 0) {
                    int depth = 1 + context.random().nextInt(12);
                    for (int offset = 0; offset < 2 + context.random().nextInt(2); offset++) {
                        BlockPos pos = top.below(depth + offset);
                        BlockState existing = level.getBlockState(pos);
                        if (existing.is(Blocks.END_STONE) || existing.is(chalk.getBlock())) level.setBlock(pos, ore, 2);
                    }
                }
                if (band < 0.2 && context.random().nextInt(22) == 0) level.setBlock(top, ore, 2);
                placed = true;
            }
        }
        return placed;
    }
}
