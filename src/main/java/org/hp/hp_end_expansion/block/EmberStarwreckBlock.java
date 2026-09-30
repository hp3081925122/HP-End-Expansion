package org.hp.hp_end_expansion.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.hp.hp_end_expansion.registry.ModParticles;

public final class EmberStarwreckBlock extends DropExperienceBlock {
    public static final MapCodec<EmberStarwreckBlock> CODEC = simpleCodec(EmberStarwreckBlock::new);
    public EmberStarwreckBlock(Properties properties) { super(UniformInt.of(1, 2), properties); }
    @Override
    public MapCodec<? extends DropExperienceBlock> codec() { return CODEC; }
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(18) == 0 && level.isEmptyBlock(pos.above())) {
            level.addParticle(ModParticles.STAR_EMBER.get(), pos.getX() + random.nextDouble(), pos.getY() + 1.05,
                pos.getZ() + random.nextDouble(), 0, 0, 0);
        }
    }
}
