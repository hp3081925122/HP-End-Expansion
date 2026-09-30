package org.hp.hp_end_expansion.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.hp.hp_end_expansion.registry.ModStarwreck;

public final class StarwreckStoneBlock extends Block implements BonemealableBlock {
    public static final MapCodec<StarwreckStoneBlock> CODEC = simpleCodec(StarwreckStoneBlock::new);

    public StarwreckStoneBlock(Properties properties) { super(properties); }

    @Override
    protected MapCodec<? extends Block> codec() { return CODEC; }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        if (!level.getBlockState(pos.above()).isAir()) return false;
        if (state.is(ModStarwreck.STAR_MOSS.get())) return true;
        for (BlockPos adjacent : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1))) {
            if (level.getBlockState(adjacent).is(ModStarwreck.STAR_MOSS.get())) return true;
        }
        return false;
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) { return true; }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        if (!state.is(ModStarwreck.STAR_MOSS.get())) {
            level.setBlockAndUpdate(pos, ModStarwreck.STAR_MOSS.get().defaultBlockState());
            return;
        }
        for (int i = 0; i < 32; i++) {
            BlockPos at = pos.offset(random.nextInt(7) - 3, random.nextInt(3) - 1, random.nextInt(7) - 3);
            if (level.getBlockState(at).is(ModStarwreck.STAR_MOSS.get()) && level.isEmptyBlock(at.above())) {
                Block plant = random.nextInt(5) == 0 ? ModStarwreck.EMBERBLOOM.get() : ModStarwreck.STAR_MOSS_SPROUTS.get();
                level.setBlockAndUpdate(at.above(), plant.defaultBlockState());
            }
        }
    }
}
