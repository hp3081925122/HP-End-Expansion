package org.hp.hp_end_expansion.block.tidelight;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.component.SuspiciousStewEffects;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.hp.hp_end_expansion.registry.ModTidelight;

public final class TidelightFlowerBlock extends FlowerBlock {
    public static final MapCodec<TidelightFlowerBlock> CODEC = simpleCodec(TidelightFlowerBlock::new);
    public TidelightFlowerBlock(Properties properties) { super(new SuspiciousStewEffects(List.of()), properties); }
    @Override public MapCodec<? extends FlowerBlock> codec() { return CODEC; }
    @Override protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(ModTidelight.GLOWKELP_REEFSTONE.get()) || this == ModTidelight.LANTERN_ANEMONE.get() && state.is(ModTidelight.PEARL_SAND.get());
    }
}
