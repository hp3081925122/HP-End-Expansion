package org.hp.hp_end_expansion.worldgen;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.level.biome.Climate;

public final class StarwreckNoise {
    public enum Region { VANILLA, STARWRECK, TIDELIGHT }
    public record Sampling(Climate.Sampler sampler, StarwreckNoise noise) { }
    private static final Map<Climate.Sampler, StarwreckNoise> SAMPLERS = Collections.synchronizedMap(new WeakHashMap<>());
    private final long seed;
    public StarwreckNoise(long seed) { this.seed = seed; }
    public static void register(Climate.Sampler sampler, long seed) { SAMPLERS.put(sampler, new StarwreckNoise(seed)); }
    public static void register(Climate.Sampler sampler, StarwreckNoise noise) { SAMPLERS.put(sampler, noise); }
    public static StarwreckNoise forSampler(Climate.Sampler sampler) { return SAMPLERS.getOrDefault(sampler, new StarwreckNoise(0)); }
    public static long hash(long seed, int x, int y, int z) {
        long h = seed ^ x * 0x632BE59BD9B4E019L ^ y * 0x9E3779B97F4A7C15L ^ z * 0x85157AF5D66D7CE9L;
        h = (h ^ h >>> 30) * 0xBF58476D1CE4E5B9L;
        h = (h ^ h >>> 27) * 0x94D049BB133111EBL;
        return h ^ h >>> 31;
    }
    public static double unit(long seed, int x, int y, int z) { return (hash(seed, x, y, z) >>> 11) * 0x1.0p-53; }
    public double sample(double x, double z) {
        return sample(seed, x, z);
    }
    private static double sample(long seed, double x, double z) {
        int ix = (int) Math.floor(x), iz = (int) Math.floor(z);
        double fx = x - ix, fz = z - iz;
        fx = fx * fx * (3 - 2 * fx); fz = fz * fz * (3 - 2 * fz);
        double a = unit(seed, ix, 0, iz), b = unit(seed, ix + 1, 0, iz);
        double c = unit(seed, ix, 0, iz + 1), d = unit(seed, ix + 1, 0, iz + 1);
        return (a + (b - a) * fx) * (1 - fz) + (c + (d - c) * fx) * fz;
    }
    public Region region(int blockX, int blockZ) {
        long regionSeed = seed ^ 0x524547494F4E534CL;
        double x = blockX + (sample(regionSeed, blockX / 96.0, blockZ / 96.0) - 0.5) * 80.0;
        double z = blockZ + (sample(regionSeed ^ 0x574152505AL, blockX / 96.0, blockZ / 96.0) - 0.5) * 80.0;
        int cellX = (int) Math.floor(x / 384.0), cellZ = (int) Math.floor(z / 384.0);
        double nearest = Double.POSITIVE_INFINITY;
        int selectedX = cellX, selectedZ = cellZ;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            int cx = cellX + dx, cz = cellZ + dz;
            double centerX = (cx + 0.15 + unit(regionSeed, cx, 1, cz) * 0.7) * 384.0;
            double centerZ = (cz + 0.15 + unit(regionSeed, cx, 2, cz) * 0.7) * 384.0;
            double distanceX = x - centerX, distanceZ = z - centerZ;
            double distance = distanceX * distanceX + distanceZ * distanceZ;
            if (distance < nearest) {
                nearest = distance;
                selectedX = cx;
                selectedZ = cz;
            }
        }
        double choice = unit(regionSeed, selectedX, 3, selectedZ);
        return choice < 0.25 ? Region.STARWRECK : choice < 0.5 ? Region.TIDELIGHT : Region.VANILLA;
    }
    public boolean isStarwreck(int blockX, int blockZ) { return region(blockX, blockZ) == Region.STARWRECK; }
    public boolean isTidelight(int blockX, int blockZ) { return region(blockX, blockZ) == Region.TIDELIGHT; }
}
