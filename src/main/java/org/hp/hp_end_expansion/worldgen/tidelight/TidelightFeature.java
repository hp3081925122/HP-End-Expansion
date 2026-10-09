package org.hp.hp_end_expansion.worldgen.tidelight;

import com.mojang.serialization.Codec;
import net.minecraft.core.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.*;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.hp.hp_end_expansion.block.tidelight.ReefstoneBlock;
import org.hp.hp_end_expansion.registry.ModTidelight;
import org.hp.hp_end_expansion.worldgen.*;

public final class TidelightFeature extends Feature<TidelightFeature.Settings> {
    public record Settings(String kind) implements FeatureConfiguration {
        public static final Codec<Settings> CODEC = Codec.STRING.fieldOf("kind").xmap(Settings::new, Settings::kind).codec();
    }
    public TidelightFeature() { super(Settings.CODEC); }
    @Override public boolean place(FeaturePlaceContext<Settings> context) {
        if (!StarwreckConfig.tidelightEnabled()) return false;
        WorldGenLevel level = context.level(); RandomSource random = context.random();
        int minX = context.origin().getX() & ~15, minZ = context.origin().getZ() & ~15;
        TidelightTerrain terrain = new TidelightTerrain(level, new BoundingBox(minX - 16, level.getMinBuildHeight(), minZ - 16, minX + 31, level.getMaxBuildHeight() - 1, minZ + 31));
        String kind = context.config().kind();
        if (kind.equals("surface")) { surface(terrain, minX, minZ); return true; }
        if (kind.equals("curtains")) { curtains(terrain, minX, minZ, random); return true; }
        if (kind.equals("vegetation")) { vegetation(terrain, minX, minZ, random); return true; }
        BlockPos center = new BlockPos(minX + 8, TidelightTerrain.surface(level, minX + 8, minZ + 8), minZ + 8);
        if (center.getY() < 30 || !terrain.reef(center)) return false;
        switch (kind) {
            case "pool" -> { if (random.nextBoolean()) return pool(terrain, center, random); }
            case "spires" -> {
                for (int i = 0; i < 2; i++) {
                    int x = minX + 3 + random.nextInt(10), z = minZ + 3 + random.nextInt(10);
                    BlockPos at = new BlockPos(x, TidelightTerrain.surface(level, x, z), z);
                    if (terrain.reef(at)) spire(terrain, at, 4 + random.nextInt(4), 1, terrain.edge(at), random);
                }
                Direction edge = terrain.edge(center);
                if (edge != null && random.nextInt(3) == 0) spire(terrain, center, 9 + random.nextInt(8), 2 + random.nextInt(2), edge, random);
            }
            case "arch" -> { if (random.nextInt(6) == 0) arch(terrain, center, random); }
            case "floating" -> { if (random.nextInt(4) == 0) floating(terrain, center, random); }
            default -> { }
        }
        return true;
    }
    private void surface(TidelightTerrain t, int minX, int minZ) {
        for (int x = minX; x < minX + 16; x++) for (int z = minZ; z < minZ + 16; z++) {
            int y = TidelightTerrain.surface(t.level, x, z);
            BlockPos top = new BlockPos(x, y, z);
            if (y < 25 || !t.reef(top)) continue;
            double cover = t.noise.sample(x / 20.0, z / 20.0);
            Block surface = cover < 0.66 ? ModTidelight.GLOWKELP_REEFSTONE.get() : ModTidelight.REEFSTONE.get();
            if (cover > 0.79 && y <= 60 && t.level.getBlockState(top.below()).is(Blocks.END_STONE)) surface = ModTidelight.PEARL_SAND.get();
            int depth = 3 + (int)(StarwreckNoise.unit(t.level.getSeed(), x, 52, z) * 3);
            for (int d = 0; d <= depth; d++) {
                if (!t.level.getBlockState(top.below(d)).is(Blocks.END_STONE)) break;
                t.put(top.below(d), (d == 0 ? surface : ModTidelight.REEFSTONE.get()).defaultBlockState());
            }
            for (int bottom = y; bottom >= 12; bottom--) {
                BlockPos at = new BlockPos(x, bottom, z);
                if (!TidelightTerrain.ground(t.level.getBlockState(at))) continue;
                boolean air = true;
                for (int d = 1; d <= 8; d++) if (!t.level.isEmptyBlock(at.below(d))) { air = false; break; }
                if (air) {
                    for (int d = 0; d < 2; d++) if (t.level.getBlockState(at.above(d)).is(Blocks.END_STONE)) t.put(at.above(d), ModTidelight.REEFSTONE.get().defaultBlockState());
                    break;
                }
            }
        }
    }
    private boolean pool(TidelightTerrain t, BlockPos center, RandomSource random) {
        int radius = 3 + random.nextInt(4), depth = 1 + random.nextInt(2);
        int low = center.getY(), high = center.getY();
        for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            if (dx * dx + dz * dz > radius * radius) continue;
            int height = TidelightTerrain.surface(t.level, center.getX() + dx, center.getZ() + dz);
            low = Math.min(low, height); high = Math.max(high, height);
        }
        if (low < 35 || high - low > 4) return false;
        center = new BlockPos(center.getX(), low, center.getZ());
        int clearance = high - low + 2;
        for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            if (dx * dx + dz * dz > radius * radius) continue;
            BlockPos top = center.offset(dx, 0, dz);
            for (int up = 1; up <= clearance; up++) {
                BlockState state = t.level.getBlockState(top.above(up));
                if (!state.canBeReplaced() && !TidelightTerrain.ground(state)) return false;
            }
            for (int d = 0; d <= depth + 3; d++) if (!TidelightTerrain.ground(t.level.getBlockState(top.below(d)))) return false;
        }
        boolean wet = true;
        for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            int q = dx * dx + dz * dz;
            if (q > radius * radius) continue;
            BlockPos top = center.offset(dx, 0, dz);
            boolean rim = q > (radius - 1) * (radius - 1);
            for (int d = -clearance; d <= depth + 2; d++) {
                BlockState state = d < 0 ? Blocks.AIR.defaultBlockState() : rim ? ModTidelight.TIDEMARKED_REEFSTONE.get().defaultBlockState() : d == 0 ? Blocks.AIR.defaultBlockState() : d <= depth ? (wet ? Blocks.WATER : Blocks.AIR).defaultBlockState() : ModTidelight.PEARL_SAND.get().defaultBlockState();
                t.put(top.below(d), state);
            }
        }
        int clams = wet ? 1 + random.nextInt(3) : random.nextInt(2);
        for (int i = 0; i < clams; i++) {
            BlockPos at = center.offset(i - 1, -depth, 0);
            t.put(at, ModTidelight.PEARL_CLAM.get().defaultBlockState().setValue(BaseCoralPlantTypeBlock.WATERLOGGED, wet));
        }
        if (wet) for (int i = 0; i < 6; i++) {
            int dx = random.nextInt(radius * 2 - 1) - radius + 1, dz = random.nextInt(radius * 2 - 1) - radius + 1;
            if (dx * dx + dz * dz >= (radius - 1) * (radius - 1)) continue;
            BlockPos at = center.offset(dx, -depth, dz);
            if (t.level.getBlockState(at).is(Blocks.WATER)) t.put(at, (random.nextBoolean() ? ModTidelight.LUMEN_CORAL.get() : ModTidelight.LUMEN_CORAL_FAN.get()).defaultBlockState().setValue(BaseCoralPlantTypeBlock.WATERLOGGED, true));
        }
        return true;
    }
    private boolean spire(TidelightTerrain t, BlockPos base, int height, int radius, Direction outward, RandomSource random) {
        if (base.getY() < 30) return false;
        for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            if (dx * dx + dz * dz <= radius * radius && !TidelightTerrain.ground(t.level.getBlockState(base.offset(dx, -1, dz)))) return false;
        }
        for (int dy = 0; dy <= height; dy++) {
            int bend = outward == null ? 0 : dy * 2 / height;
            BlockPos axis = outward == null ? base.above(dy) : base.above(dy).relative(outward, bend);
            int r = Math.max(0, radius - (dy * (radius + 1) / (height + 1)));
            for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > r * r + 1) continue;
                BlockPos at = axis.offset(dx, 0, dz);
                BlockState old = t.level.getBlockState(at);
                if (!old.canBeReplaced() && !TidelightTerrain.ground(old)) continue;
                Block block = dy != height && dy % 4 == 2 && radius > 1 ? ModTidelight.LUMEN_CORAL_BLOCK.get() : dy % 3 == 0 ? ModTidelight.TIDEMARKED_REEFSTONE.get() : ModTidelight.REEFSTONE.get();
                t.put(at, block.defaultBlockState());
                if (dy > 2 && dy % 4 == 0) for (Direction dir : Direction.Plane.HORIZONTAL) if (random.nextInt(3) == 0) t.fan(at.relative(dir), dir, false);
            }
            if (dy == height && t.level.isEmptyBlock(axis.above()) && ModTidelight.LUMEN_CORAL.get().defaultBlockState().canSurvive(t.level, axis.above())) t.put(axis.above(), ModTidelight.LUMEN_CORAL.get().defaultBlockState());
        }
        return true;
    }
    private void arch(TidelightTerrain t, BlockPos center, RandomSource random) {
        Direction side = random.nextBoolean() ? Direction.EAST : Direction.SOUTH;
        BlockPos a = center.relative(side, -4), b = center.relative(side, 4);
        int ay = TidelightTerrain.surface(t.level, a.getX(), a.getZ()), by = TidelightTerrain.surface(t.level, b.getX(), b.getZ());
        if (Math.abs(ay - center.getY()) > 2 || Math.abs(by - center.getY()) > 2 || !t.reef(a) || !t.reef(b)) return;
        int top = center.getY() + 10 + random.nextInt(3);
        a = new BlockPos(a.getX(), ay, a.getZ()); b = new BlockPos(b.getX(), by, b.getZ());
        if (!spire(t, a, top - ay - 1, 2, null, random) || !spire(t, b, top - by - 1, 2, null, random)) return;
        for (int d = -4; d <= 4; d++) {
            BlockPos at = center.relative(side, d).above(top - center.getY() + (Math.abs(d) <= 2 ? 1 : 0));
            t.put(at, ModTidelight.REEFSTONE.get().defaultBlockState());
            if (Math.abs(d) <= 2) t.put(at.below(), ModTidelight.REEFSTONE.get().defaultBlockState());
        }
    }
    private void floating(TidelightTerrain t, BlockPos center, RandomSource random) {
        Direction outward = t.edge(center);
        if (outward == null) return;
        int edge = 1;
        while (edge <= 12 && TidelightTerrain.surface(t.level, center.relative(outward, edge).getX(), center.relative(outward, edge).getZ()) >= center.getY() - 6) edge++;
        if (edge > 12) return;
        int radius = 2 + random.nextInt(3), gap = 4 + random.nextInt(7);
        BlockPos axis = center.relative(outward, edge + gap).above(6 + random.nextInt(13));
        if (!t.clip.isInside(axis.offset(-radius, -3, -radius)) || !t.clip.isInside(axis.offset(radius, 2, radius))) return;
        for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) for (int dy = -2; dy <= 1; dy++) {
            double q = (dx * dx + dz * dz) / (double)(radius * radius) + dy * dy / 6.0;
            if (q <= 1 && !t.level.isEmptyBlock(axis.offset(dx, dy, dz))) return;
        }
        for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            int low = 3, high = -3;
            for (int dy = -2; dy <= 1; dy++) if ((dx * dx + dz * dz) / (double)(radius * radius) + dy * dy / 6.0 <= 1) { low = Math.min(low, dy); high = dy; }
            for (int dy = low; dy <= high; dy++) t.put(axis.offset(dx, dy, dz), (dy == high ? ModTidelight.GLOWKELP_REEFSTONE.get() : ModTidelight.REEFSTONE.get()).defaultBlockState());
        }
        if (random.nextFloat() < 0.3F && axis.getY() - center.getY() <= gap + 2) {
            BlockPos start = center.relative(outward, Math.max(0, edge - 2));
            int distance = edge + gap - Math.max(0, edge - 2);
            for (int d = 0; d < distance; d++) {
                int y = center.getY() + (axis.getY() - center.getY()) * d / distance;
                t.put(new BlockPos(start.getX() + outward.getStepX() * d, y, start.getZ() + outward.getStepZ() * d), ModTidelight.REEFSTONE.get().defaultBlockState());
            }
        }
    }
    private void vegetation(TidelightTerrain t, int minX, int minZ, RandomSource random) {
        for (int group = 0; group < 6; group++) {
            int gx = minX + random.nextInt(16), gz = minZ + random.nextInt(16);
            if (group >= 3) for (int probe = 0; probe < 24; probe++) {
                int px = minX + random.nextInt(16), pz = minZ + random.nextInt(16);
                BlockPos at = new BlockPos(px, TidelightTerrain.surface(t.level, px, pz), pz);
                boolean preferred = false;
                for (Direction dir : Direction.Plane.HORIZONTAL) for (int d = 1; d <= 3; d++) {
                    BlockPos near = at.relative(dir, d);
                    if (group == 3 && (t.level.getBlockState(near).is(ModTidelight.PEARL_SAND.get()) || t.level.getBlockState(near).is(ModTidelight.TIDEMARKED_REEFSTONE.get()))) preferred = true;
                    if (group > 3 && (ReefstoneBlock.isReef(t.level.getBlockState(near.above(2))) || t.level.getBlockState(near.above(2)).is(ModTidelight.LUMEN_CORAL_BLOCK.get()))) preferred = true;
                }
                if (preferred) { gx = px; gz = pz; break; }
            }
            Block plant = group < 3 ? ModTidelight.TIDE_WHISKERS.get() : group == 3 ? ModTidelight.LANTERN_ANEMONE.get() : ModTidelight.LUMEN_CORAL.get();
            int attempts = group < 3 ? 6 + random.nextInt(5) : 2 + random.nextInt(4);
            for (int n = 0; n < attempts; n++) {
                int x = gx + random.nextInt(7) - 3, z = gz + random.nextInt(7) - 3;
                BlockPos at = new BlockPos(x, TidelightTerrain.surface(t.level, x, z) + 1, z);
                if (at.getY() > 30 && t.reef(at) && t.level.isEmptyBlock(at) && plant.defaultBlockState().canSurvive(t.level, at)) t.put(at, plant.defaultBlockState());
            }
        }
    }
    private void curtains(TidelightTerrain t, int minX, int minZ, RandomSource random) {
        for (int x = minX; x < minX + 16; x++) for (int z = minZ; z < minZ + 16; z++) {
            boolean inBiome = t.reef(new BlockPos(x, 64, z));
            double shape = t.noise.sample(x / 14.0, z / 14.0);
            int length = 3 + (int)Math.round(shape * 9);
            for (int y = 140; y >= 16; y--) {
                BlockPos at = new BlockPos(x, y, z);
                BlockState state = t.level.getBlockState(at);
                if (!inBiome && !ReefstoneBlock.isReef(state) && !state.is(ModTidelight.REEF_BONE_BLOCK.get())) continue;
                if (!ReefstoneBlock.isReef(state) && !state.is(Blocks.END_STONE) && !state.is(ModTidelight.REEF_BONE_BLOCK.get()) && !state.is(ModTidelight.LUMEN_CORAL_BLOCK.get())) continue;
                if (!t.level.isEmptyBlock(at.below())) continue;
                boolean clear = true;
                for (int d = 2; d <= 8; d++) if (!t.level.isEmptyBlock(at.below(d))) { clear = false; break; }
                double density = clear ? 0.75 : 0.333;
                if (StarwreckNoise.unit(t.level.getSeed(), x, y, z) < density) t.vine(at, clear ? length : Math.min(6, length), random);
            }
        }
    }
}
