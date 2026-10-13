package org.hp.hp_end_expansion.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.hp.hp_end_expansion.registry.ModStarwreck;

public final class StarwreckPlantBlock extends BushBlock {
    public StarwreckPlantBlock(Properties properties) { super(properties); }
    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(ModStarwreck.STAR_MOSS.get()) || state.is(ModStarwreck.STARWRECK_STONE.get());
    }
}
