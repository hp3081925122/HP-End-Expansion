package com.hp_end_expansion.content.prismatic.block;

import com.hp_end_expansion.HpEndExpansion;
import com.hp_end_expansion.content.prismatic.PrismaticContent;
import com.mojang.serialization.MapCodec;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.BlockState;

public class PrismaticSaplingBlock extends SaplingBlock {
    // 树苗与自然生成共用同一已注册树特征，避免两套树形漂移。
    private static final TreeGrower GROWER = new TreeGrower("hp_end_expansion_prismatic", Optional.empty(),
            Optional.of(ResourceKey.create(Registries.CONFIGURED_FEATURE, ResourceLocation.fromNamespaceAndPath(HpEndExpansion.MODID, "prismatic_tree"))), Optional.empty());
    public static final MapCodec<PrismaticSaplingBlock> CODEC = simpleCodec(PrismaticSaplingBlock::new);

    // 继承原版树苗阶段与骨粉交互，仅替换土壤和生长光照条件。
    public PrismaticSaplingBlock(Properties properties) { super(GROWER, properties); }

    // 编解码固定使用本群系树生长器。
    @Override public MapCodec<? extends SaplingBlock> codec() { return CODEC; }

    // 棱冠木可在末地石和风化棱土上扎根。
    @Override protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(PrismaticContent.block("prism_soil")) || state.is(Blocks.END_STONE);
    }

    // 使用已加载半径检查，取消天空亮度门槛以保证末地自然成长。
    @Override protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.isAreaLoaded(pos, 4) && random.nextInt(7) == 0) advanceTree(level, pos, state, random);
    }
}
