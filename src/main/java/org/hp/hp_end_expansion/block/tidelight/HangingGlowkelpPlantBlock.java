package org.hp.hp_end_expansion.block.tidelight;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import org.hp.hp_end_expansion.registry.ModTidelight;

public final class HangingGlowkelpPlantBlock extends GrowingPlantBodyBlock {
    public static final MapCodec<HangingGlowkelpPlantBlock> CODEC = simpleCodec(HangingGlowkelpPlantBlock::new);
    public HangingGlowkelpPlantBlock(Properties properties) { super(properties, Direction.DOWN, Block.box(2, 0, 2, 14, 16, 14), false); }
    @Override protected MapCodec<? extends HangingGlowkelpPlantBlock> codec() { return CODEC; }
    @Override protected GrowingPlantHeadBlock getHeadBlock() { return ModTidelight.HANGING_GLOWKELP.get(); }
    @Override protected boolean canAttachTo(BlockState state) { return HangingGlowkelpBlock.support(state); }
    private BlockPos head(LevelReader level, BlockPos pos) {
        for (int i = 1; i <= 12; i++) {
            BlockPos at = pos.below(i);
            BlockState state = level.getBlockState(at);
            if (state.is(getHeadBlock())) return at;
            if (!state.is(this)) break;
        }
        return null;
    }
    @Override public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        BlockPos end = head(level, pos);
        return end != null && getHeadBlock().isValidBonemealTarget(level, end, level.getBlockState(end));
    }
    @Override public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        BlockPos end = head(level, pos);
        if (end != null) getHeadBlock().performBonemeal(level, random, end, level.getBlockState(end));
    }
    @Override public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) { return new ItemStack(ModTidelight.GLOWKELP_POD.get()); }
}
