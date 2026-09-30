package org.hp.hp_end_expansion.block.tidelight;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import org.hp.hp_end_expansion.registry.ModTidelight;

public final class ReefstoneBlock extends Block implements BonemealableBlock {
    public static final MapCodec<ReefstoneBlock> CODEC = simpleCodec(ReefstoneBlock::new);
    public ReefstoneBlock(Properties properties) { super(properties); }
    @Override protected MapCodec<? extends Block> codec() { return CODEC; }
    public static boolean isReef(BlockState state) {
        return state.is(ModTidelight.REEFSTONE.get()) || state.is(ModTidelight.GLOWKELP_REEFSTONE.get()) || state.is(ModTidelight.TIDEMARKED_REEFSTONE.get());
    }
    @Override public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        if (!level.getBlockState(pos.above()).isAir()) return false;
        if (state.is(ModTidelight.GLOWKELP_REEFSTONE.get())) return true;
        for (BlockPos near : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1)))
            if (level.getBlockState(near).is(ModTidelight.GLOWKELP_REEFSTONE.get())) return true;
        return false;
    }
    @Override public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) { return true; }
    @Override public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        if (!state.is(ModTidelight.GLOWKELP_REEFSTONE.get())) {
            level.setBlockAndUpdate(pos, ModTidelight.GLOWKELP_REEFSTONE.get().defaultBlockState());
            return;
        }
        for (int i = 0; i < 32; i++) {
            BlockPos at = pos.offset(random.nextInt(7) - 3, random.nextInt(3), random.nextInt(7) - 3);
            Block block = switch (random.nextInt(6)) {
                case 0 -> ModTidelight.LANTERN_ANEMONE.get();
                case 1, 2 -> ModTidelight.LUMEN_CORAL.get();
                default -> ModTidelight.TIDE_WHISKERS.get();
            };
            BlockState plant = block.defaultBlockState();
            if (level.isEmptyBlock(at) && plant.canSurvive(level, at)) level.setBlockAndUpdate(at, plant);
        }
    }
}
