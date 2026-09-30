package org.hp.hp_end_expansion.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.hp.hp_end_expansion.registry.ModStarwreck;

public final class StarwreckPlantBlock extends BushBlock {
    public static final MapCodec<StarwreckPlantBlock> CODEC = simpleCodec(StarwreckPlantBlock::new);
    public StarwreckPlantBlock(Properties properties) { super(properties); }
    @Override
    public MapCodec<? extends BushBlock> codec() { return CODEC; }
    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(ModStarwreck.STAR_MOSS.get()) || state.is(ModStarwreck.STARWRECK_STONE.get());
    }
}
