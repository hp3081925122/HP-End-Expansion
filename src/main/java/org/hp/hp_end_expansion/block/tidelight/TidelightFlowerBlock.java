package org.hp.hp_end_expansion.block.tidelight;

import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.hp.hp_end_expansion.registry.ModTidelight;

public final class TidelightFlowerBlock extends FlowerBlock {
    public TidelightFlowerBlock(Properties properties) { super(MobEffects.SATURATION, 0, properties); }
    @Override protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(ModTidelight.GLOWKELP_REEFSTONE.get()) || this == ModTidelight.LANTERN_ANEMONE.get() && state.is(ModTidelight.PEARL_SAND.get());
    }
}
