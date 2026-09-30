package org.hp.hp_end_expansion.worldgen.tidelight;

import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.hp.hp_end_expansion.block.tidelight.*;
import org.hp.hp_end_expansion.registry.ModTidelight;
import org.hp.hp_end_expansion.worldgen.StarwreckNoise;

public final class TidelightTerrain {
    public final WorldGenLevel level;
    public final BoundingBox clip;
    public final StarwreckNoise noise;
    public TidelightTerrain(WorldGenLevel level, BoundingBox clip) {
        this.level = level; this.clip = clip; this.noise = new StarwreckNoise(level.getSeed() ^ 0x52454546L);
    }
    public static boolean ground(BlockState state) {
        return state.is(Blocks.END_STONE) || ReefstoneBlock.isReef(state) || state.is(ModTidelight.PEARL_SAND.get());
    }
    public static int surface(WorldGenLevel level, int x, int z) {
        int top = Math.min(160, level.getHeight(level instanceof ServerLevel ? Heightmap.Types.WORLD_SURFACE : Heightmap.Types.WORLD_SURFACE_WG, x, z) + 20);
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos(x, top, z);
        for (int y = top; y >= 20; y--) if (ground(level.getBlockState(at.setY(y)))) return y;
        return -1;
    }
    public boolean put(BlockPos pos, BlockState state) {
        return clip.isInside(pos) && level.setBlock(pos, state, 2);
    }
    public boolean reef(BlockPos pos) { return level.getBiome(pos).is(TidelightWorldgen.BIOME); }
    public Direction edge(BlockPos pos) {
        Direction best = null; int distance = 13;
        for (Direction dir : Direction.Plane.HORIZONTAL) for (int d = 2; d <= 12; d += 2) {
            BlockPos at = pos.relative(dir, d);
            if (surface(level, at.getX(), at.getZ()) < pos.getY() - 6) {
                if (d < distance) { distance = d; best = dir; }
                break;
            }
        }
        return best;
    }
    public void vine(BlockPos support, int length, RandomSource random) {
        if (!HangingGlowkelpBlock.support(level.getBlockState(support))) return;
        int count = 0;
        while (count < Math.min(12, length) && clip.isInside(support.below(count + 1)) && level.isEmptyBlock(support.below(count + 1))) count++;
        if (count < 3) return;
        for (int i = 1; i < count; i++) put(support.below(i), ModTidelight.HANGING_GLOWKELP_PLANT.get().defaultBlockState());
        put(support.below(count), ModTidelight.HANGING_GLOWKELP.get().defaultBlockState().setValue(HangingGlowkelpBlock.AGE, 25).setValue(HangingGlowkelpBlock.POD, random.nextInt(4) == 0));
    }
    public void fan(BlockPos pos, Direction facing, boolean wet) {
        BlockState fan = ModTidelight.LUMEN_CORAL_WALL_FAN.get().defaultBlockState().setValue(BaseCoralWallFanBlock.FACING, facing).setValue(BaseCoralWallFanBlock.WATERLOGGED, wet);
        if ((level.isEmptyBlock(pos) || wet && level.getBlockState(pos).is(Blocks.WATER)) && fan.canSurvive(level, pos)) put(pos, fan);
    }
}
