package org.hp.hp_end_expansion.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public final class ColoredFallingBlock extends FallingBlock {
    private final int color;

    public ColoredFallingBlock(int color, BlockBehaviour.Properties properties) {
        super(properties);
        this.color = color;
    }

    @Override
    public int getDustColor(BlockState state, BlockGetter level, BlockPos pos) {
        return color;
    }
}
