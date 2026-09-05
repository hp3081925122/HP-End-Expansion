package com.hp_end_expansion.content.prismatic.worldgen;

import com.hp_end_expansion.content.prismatic.PrismaticContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public final class PrismaticTreeFeature extends Feature<NoneFeatureConfiguration> {
    // 树苗与自然树使用同一个特征，树形不会因获取途径不同而变化。
    public PrismaticTreeFeature() { super(NoneFeatureConfiguration.CODEC); }

    // 先检查全部树体所需空间，再放树干与扁冠，失败时不会留下半棵树。
    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        int height = 4 + context.random().nextInt(3);
        BlockState ground = level.getBlockState(origin.below());
        if ((!ground.is(PrismaticContent.block("prism_soil")) && !ground.is(Blocks.END_STONE))
                || origin.getY() < level.getMinBuildHeight() + 1 || origin.getY() + height + 1 >= level.getMaxBuildHeight()) return false;
        // 生长最多检查七乘五的双层树冠，骨粉使用同样的碰撞和写入边界检查。
        for (int y = 0; y <= height; y++) {
            int radiusX = y >= height - 1 ? (y == height ? 2 : 3) : 0;
            int radiusZ = y >= height - 1 ? 2 : 0;
            for (int x = -radiusX; x <= radiusX; x++) {
                for (int z = -radiusZ; z <= radiusZ; z++) {
                    if (Math.abs(x) + Math.abs(z) > radiusX + 1) continue;
                    BlockPos pos = origin.offset(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (!level.ensureCanWrite(pos) || (!state.isAir() && !state.canBeReplaced() && !state.is(BlockTags.LEAVES)
                            && !state.is(PrismaticContent.block("prism_sapling")))) return false;
                }
            }
        }
        // 中央树干和两侧矿枝提供真实原木支撑，枝条轴向可供玩家正常采伐。
        BlockState log = PrismaticContent.block("prism_log").defaultBlockState();
        for (int y = 0; y < height; y++) level.setBlock(origin.above(y), log, 2);
        for (int x = -2; x <= 2; x++) {
            if (x != 0) level.setBlock(origin.offset(x, height - 1, 0), log.setValue(RotatedPillarBlock.AXIS, Direction.Axis.X), 2);
        }
        // 初始叶距按到枝干的曼哈顿距离计算；保留自然叶属性，砍树后仍能正常腐烂。
        BlockState leaves = PrismaticContent.block("prism_leaves").defaultBlockState().setValue(LeavesBlock.PERSISTENT, false);
        for (int y = height - 1; y <= height; y++) {
            int radiusX = y == height ? 2 : 3;
            for (int x = -radiusX; x <= radiusX; x++) {
                for (int z = -2; z <= 2; z++) {
                    if (Math.abs(x) + Math.abs(z) > radiusX + 1 || (y == height - 1 && z == 0 && Math.abs(x) <= 2)) continue;
                    int distance = Math.max(1, Math.max(0, Math.abs(x) - 2) + Math.abs(z) + y - height + 1);
                    level.setBlock(origin.offset(x, y, z), leaves.setValue(LeavesBlock.DISTANCE, distance), 2);
                }
            }
        }
        return true;
    }
}
