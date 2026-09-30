package org.hp.hp_end_expansion.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.hp.hp_end_expansion.registry.ModStarwreck;

public final class StarwreckTerrain {
    private final WorldGenLevel level;
    private final BoundingBox clip;
    private final long seed;
    public StarwreckTerrain(WorldGenLevel level, BoundingBox clip, long seed) { this.level = level; this.clip = clip; this.seed = seed; }
    public void put(int x, int y, int z, BlockState state) {
        BlockPos pos = new BlockPos(x, y, z);
        if (clip.isInside(pos) && !level.isOutsideBuildHeight(pos)) level.setBlock(pos, state, 2);
    }
    public static boolean terrain(BlockState state) {
        return state.is(Blocks.END_STONE) || state.is(ModStarwreck.STARWRECK_STONE.get()) || state.is(ModStarwreck.STAR_MOSS.get())
            || state.is(ModStarwreck.EMBER_STARWRECK_STONE.get()) || state.is(ModStarwreck.METEOR_ASH.get());
    }
    public static int surface(WorldGenLevel level, int x, int z) {
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
        for (int y = Math.min(top, 127); y >= 30; y--) {
            if (terrain(level.getBlockState(new BlockPos(x, y, z)))) return y;
        }
        return -1;
    }
    public static boolean safeCrater(WorldGenLevel level, BlockPos center, int radius, int depth, boolean nest) {
        int required = depth + (nest ? 18 : 4);
        for (int dy = 0; dy <= required; dy++) {
            if (!terrain(level.getBlockState(center.below(dy)))) return false;
        }
        int unsupported = 0;
        for (int i = 0; i < 16; i++) {
            double angle = i * Math.PI / 8;
            int x = center.getX() + (int) Math.round(Math.cos(angle) * radius);
            int z = center.getZ() + (int) Math.round(Math.sin(angle) * radius);
            int y = surface(level, x, z);
            if (y < center.getY() - 5) unsupported++;
        }
        if (unsupported > 4) return false;
        if (nest) {
            BlockPos cavity = center.offset(-3, -depth - 6, 0);
            for (int dx = -6; dx <= 6; dx++) for (int dz = -6; dz <= 6; dz++) {
                if (dx * dx + dz * dz > 36) continue;
                int bottom = cavity.getY() - (int) Math.ceil(Math.sqrt(36 - dx * dx - dz * dz)) - 4;
                for (int y = bottom; y <= center.getY() - depth; y++) {
                    if (!terrain(level.getBlockState(new BlockPos(cavity.getX() + dx, y, cavity.getZ() + dz)))) return false;
                }
            }
        }
        for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            double q = (dx * dx + dz * dz) / (double) (radius * radius);
            if (q > 0.9) continue;
            int floor = center.getY() - (int) Math.round(depth * (1 - q));
            for (int d = 0; d < 4; d++) {
                if (!terrain(level.getBlockState(new BlockPos(center.getX() + dx, floor - d, center.getZ() + dz)))) return false;
            }
        }
        return true;
    }
    public void crater(BlockPos center, int radius, int depth, double ember, boolean terraced) {
        int cx = center.getX(), cy = center.getY(), cz = center.getZ();
        for (int x = Math.max(cx - radius, clip.minX()); x <= Math.min(cx + radius, clip.maxX()); x++) {
            for (int z = Math.max(cz - radius, clip.minZ()); z <= Math.min(cz + radius, clip.maxZ()); z++) {
                double dx = (x - cx) / (double) radius, dz = (z - cz) / (radius * 0.9);
                double q = dx * dx + dz * dz;
                if (q > 1) continue;
                int drop = (int) Math.round(depth * (1 - q));
                if (terraced && q > 0.25 && drop > 1) drop = drop / 2 * 2;
                int floor = q > 0.86 ? cy + 1 + (StarwreckNoise.unit(seed, x, 19, z) > 0.6 ? 1 : 0) : cy - drop;
                for (int y = floor - 2; y <= cy + 3; y++) {
                    BlockState state = Blocks.AIR.defaultBlockState();
                    if (y <= floor) {
                        state = ModStarwreck.STARWRECK_STONE.get().defaultBlockState();
                        if (q < 0.17 && y > floor - (radius >= 11 ? 2 : 1)) state = ModStarwreck.METEOR_ASH.get().defaultBlockState();
                        else if (q >= 0.17 && q < 0.86 && StarwreckNoise.unit(seed, x, y, z) < ember) state = ModStarwreck.EMBER_STARWRECK_STONE.get().defaultBlockState();
                        else if (q > 0.86 && y == floor && StarwreckNoise.unit(seed, x, 12, z) < 0.65) state = ModStarwreck.STAR_MOSS.get().defaultBlockState();
                    }
                    put(x, y, z, state);
                }
            }
        }
    }
    public void nest(BlockPos center, int inner) {
        int outer = inner + 3;
        for (int x = Math.max(center.getX() - outer, clip.minX()); x <= Math.min(center.getX() + outer, clip.maxX()); x++) {
            for (int z = Math.max(center.getZ() - outer, clip.minZ()); z <= Math.min(center.getZ() + outer, clip.maxZ()); z++) {
                for (int y = center.getY() - outer; y <= center.getY() + outer; y++) {
                    double d = Math.sqrt(center.distToLowCornerSqr(x, y, z));
                    if (d > outer) continue;
                    BlockState state = d < inner ? Blocks.CAVE_AIR.defaultBlockState()
                        : d < inner + 1 ? ModStarwreck.STAR_CRYSTAL_BLOCK.get().defaultBlockState()
                        : d < inner + 2 ? ModStarwreck.EMBER_STARWRECK_STONE.get().defaultBlockState()
                        : ModStarwreck.STARWRECK_STONE.get().defaultBlockState();
                    put(x, y, z, state);
                }
            }
        }
        for (int dx = -inner; dx <= inner; dx++) for (int dy = -inner; dy <= inner; dy++) for (int dz = -inner; dz <= inner; dz++) {
            if (dx * dx + dy * dy + dz * dz >= inner * inner || StarwreckNoise.unit(seed, center.getX()+dx, center.getY()+dy, center.getZ()+dz) > 0.35) continue;
            for (Direction facing : Direction.values()) {
                int sx = dx - facing.getStepX(), sy = dy - facing.getStepY(), sz = dz - facing.getStepZ();
                if (sx*sx + sy*sy + sz*sz < inner*inner) continue;
                int n = (int)(StarwreckNoise.unit(seed ^ 77, center.getX()+dx, center.getY()+dy, center.getZ()+dz) * 4);
                put(center.getX()+dx, center.getY()+dy, center.getZ()+dz, crystal(n).defaultBlockState().setValue(AmethystClusterBlock.FACING, facing));
                break;
            }
        }
        for (int dx = -1; dx <= 1; dx++) for (int dz = 0; dz <= 1; dz++) {
            for (int y = center.getY(); y <= center.getY() + outer + 1; y++) put(center.getX()+dx, y, center.getZ()+dz, Blocks.AIR.defaultBlockState());
        }
        put(center.getX()-2, center.getY()+outer, center.getZ(), ModStarwreck.STAR_CRYSTAL_CLUSTER.get().defaultBlockState());
    }
    public static Block crystal(int stage) {
        return switch (stage) {
            case 0 -> ModStarwreck.SMALL_STAR_CRYSTAL_BUD.get();
            case 1 -> ModStarwreck.MEDIUM_STAR_CRYSTAL_BUD.get();
            case 2 -> ModStarwreck.LARGE_STAR_CRYSTAL_BUD.get();
            default -> ModStarwreck.STAR_CRYSTAL_CLUSTER.get();
        };
    }
    public void meteor(BlockPos center, boolean small) {
        RandomSource random = RandomSource.create(StarwreckNoise.hash(seed, center.getX(), center.getY(), center.getZ()));
        double angle = random.nextDouble() * Math.PI * 2;
        int blobs = small ? 1 : 2 + random.nextInt(3);
        for (int b = 0; b < blobs; b++) {
            double r = 1.5 + random.nextDouble() * 1.5;
            int cx = center.getX() + (int)Math.round(Math.cos(angle) * b * 1.6);
            int cy = center.getY() + b;
            int cz = center.getZ() + (int)Math.round(Math.sin(angle) * b * 1.6);
            for (int dx = -3; dx <= 3; dx++) for (int dy = -3; dy <= 3; dy++) for (int dz = -3; dz <= 3; dz++) {
                if ((dx*dx + dz*dz + dy*dy*0.8) > r*r + StarwreckNoise.unit(seed,cx+dx,cy+dy,cz+dz)*0.7) continue;
                Block block = StarwreckNoise.unit(seed, cx+dx,cy+dy,cz+dz) < 0.25 ? ModStarwreck.EMBER_STARWRECK_STONE.get() : ModStarwreck.STARWRECK_STONE.get();
                put(cx+dx,cy+dy,cz+dz,block.defaultBlockState());
            }
        }
        for (int i=0;i<1+random.nextInt(3);i++) put(center.getX(),center.getY()+i,center.getZ(),ModStarwreck.STAR_CRYSTAL_BLOCK.get().defaultBlockState());
        int buds = 2 + random.nextInt(4);
        for (int n=0;n<60 && buds>0;n++) {
            BlockPos p=center.offset(random.nextInt(11)-5,random.nextInt(8)-2,random.nextInt(11)-5);
            if (!clip.isInside(p) || !level.isEmptyBlock(p)) continue;
            Direction facing=Direction.values()[random.nextInt(6)];
            if (!terrain(level.getBlockState(p.relative(facing.getOpposite())))) continue;
            put(p.getX(),p.getY(),p.getZ(),crystal(random.nextInt(4)).defaultBlockState().setValue(AmethystClusterBlock.FACING,facing));
            buds--;
        }
    }
}
