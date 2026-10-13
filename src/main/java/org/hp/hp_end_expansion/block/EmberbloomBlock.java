package org.hp.hp_end_expansion.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.hp.hp_end_expansion.registry.ModStarwreck;

public final class EmberbloomBlock extends FlowerBlock {
    public EmberbloomBlock(Properties properties) { super(MobEffects.SATURATION, 0, properties); }
    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) { return state.is(ModStarwreck.STAR_MOSS.get()); }
}
