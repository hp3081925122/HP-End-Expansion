package com.hp_end_expansion.content.prismatic.worldgen;

import com.hp_end_expansion.content.prismatic.PrismaticContent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public final class PrismaticVegetationFeature extends Feature<NoneFeatureConfiguration> {
    private static final String[] PLANTS = {"prism_grass", "mica_reed", "lantern_bloom", "glass_fern", "shard_cactus", "dusk_bloom"};

    // 地表与植被分阶段生成，使探索结构可先占据清晰的活动空间。
    public PrismaticVegetationFeature() { super(NoneFeatureConfiguration.CODEC); }

    // 树冠限制在本区块内部；植物只使用棱土，避免长在遗迹地板上。
    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        int startX = context.origin().getX() & ~15;
        int startZ = context.origin().getZ() & ~15;
        boolean placed = false;
        // 每区块至多两次树生长尝试，林间空地仍保持足够的战斗视线。
        for (int attempt = 0; attempt < 2; attempt++) {
            if (context.random().nextInt(3) != 0) continue;
            int x = startX + 4 + context.random().nextInt(8);
            int z = startZ + 4 + context.random().nextInt(8);
            BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z), z);
            if (level.getBiome(pos).is(PrismaticBiomeRouting.WASTES) && level.getBlockState(pos.below()).is(PrismaticContent.block("prism_soil"))) {
                placed |= PrismaticWorldgen.TREE.get().place(NoneFeatureConfiguration.INSTANCE, level, context.chunkGenerator(), context.random(), pos);
            }
        }
        // 植物以有限次数采样形成簇群，湿地或水源不参与任何条件。
        for (int attempt = 0; attempt < 42; attempt++) {
            int x = startX + context.random().nextInt(16);
            int z = startZ + context.random().nextInt(16);
            BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z), z);
            if (!level.getBiome(pos).is(PrismaticBiomeRouting.WASTES) || !level.isEmptyBlock(pos)
                    || !level.getBlockState(pos.below()).is(PrismaticContent.block("prism_soil"))) continue;
            double band = PrismaticBiomeRouting.patch(x, z, 28, 0x534f494cL);
            int choice = context.random().nextInt(12);
            int species = choice < 4 ? 0 : choice < 6 ? 1 : choice < 8 ? 2 : choice < 10 ? 3 : choice == 10 ? 4 : 5;
            if (band > 0.7 && context.random().nextBoolean()) species = 5;
            BlockState plant = PrismaticContent.block(PLANTS[species]).defaultBlockState()
                    .setValue(BlockStateProperties.AGE_3, 1 + context.random().nextInt(3));
            if (plant.canSurvive(level, pos)) {
                level.setBlock(pos, plant, 2);
                placed = true;
            }
        }
        return placed;
    }
}
