package org.hp.hp_end_expansion.block;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.component.SuspiciousStewEffects;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.hp.hp_end_expansion.registry.ModStarwreck;

public final class EmberbloomBlock extends FlowerBlock {
    public static final MapCodec<EmberbloomBlock> CODEC = simpleCodec(EmberbloomBlock::new);
    public EmberbloomBlock(Properties properties) { super(new SuspiciousStewEffects(List.of()), properties); }
    @Override
    public MapCodec<? extends FlowerBlock> codec() { return CODEC; }
    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) { return state.is(ModStarwreck.STAR_MOSS.get()); }
}
