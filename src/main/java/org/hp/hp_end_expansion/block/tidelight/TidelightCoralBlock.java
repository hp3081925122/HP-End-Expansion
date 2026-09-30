package org.hp.hp_end_expansion.block.tidelight;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.*;
import org.hp.hp_end_expansion.registry.ModTidelight;

public final class TidelightCoralBlock extends BaseCoralPlantTypeBlock {
    public static final MapCodec<TidelightCoralBlock> CODEC = simpleCodec(TidelightCoralBlock::new);
    public TidelightCoralBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(WATERLOGGED, false));
    }
    @Override public MapCodec<? extends TidelightCoralBlock> codec() { return CODEC; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this == ModTidelight.PEARL_CLAM.get() ? Block.box(2, 0, 3, 14, 7, 13) : Block.box(2, 0, 2, 14, 15, 14);
    }
    @Override protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (this == ModTidelight.PEARL_CLAM.get()) return super.canSurvive(state, level, pos);
        BlockState below = level.getBlockState(pos.below());
        return ReefstoneBlock.isReef(below) || state.getValue(WATERLOGGED) && below.is(ModTidelight.PEARL_SAND.get());
    }
    @Override public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(WATERLOGGED) || random.nextInt(12) != 0) return;
        BlockPos surface = pos;
        for (int i = 0; i < 3 && level.getFluidState(surface.above()).is(Fluids.WATER); i++) surface = surface.above();
        if (level.isEmptyBlock(surface.above())) level.addParticle(ModTidelight.TIDE_BUBBLE.get(), surface.getX() + random.nextDouble(), surface.getY() + 1.02, surface.getZ() + random.nextDouble(), 0, 0, 0);
    }
}
