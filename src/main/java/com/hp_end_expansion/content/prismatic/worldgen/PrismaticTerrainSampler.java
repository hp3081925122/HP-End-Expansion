package com.hp_end_expansion.content.prismatic.worldgen;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import java.util.Arrays;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

public final class PrismaticTerrainSampler extends NoiseChunk {
    // 公开编解码器为原版标记的 unit codec，解析空对象取得同一个单例，不访问受保护枚举。
    private static final DensityFunctions.BeardifierOrMarker EMPTY_TERRAIN = (DensityFunctions.BeardifierOrMarker)
            DensityFunctions.BeardifierOrMarker.CODEC.codec().codec().parse(JsonOps.INSTANCE, new JsonObject()).result().orElseThrow();

    // 单元内共享原版插值缓存，避免每一柱重复初始化同一份密度函数与缓存。
    private PrismaticTerrainSampler(RandomState state, int x, int z, NoiseSettings noise, NoiseGeneratorSettings settings, Aquifer.FluidPicker fluids) {
        super(1, state, x, z, noise, EMPTY_TERRAIN, settings, fluids, Blender.empty());
    }

    // 严格沿用原版逐柱算法的噪声设置、流体选择、插值顺序和高度谓词，只合并重复单元计算。
    public static Terrain sample(NoiseBasedChunkGenerator generator, RandomState state, LevelHeightAccessor heightAccessor,
            int minX, int minZ, int width, int depth) {
        NoiseGeneratorSettings settings = generator.generatorSettings().value();
        NoiseSettings noise = settings.noiseSettings().clampToHeightAccessor(heightAccessor);
        int cellWidth = noise.getCellWidth();
        int cellHeight = noise.getCellHeight();
        int minCellY = Math.floorDiv(noise.minY(), cellHeight);
        int cellCountY = Math.floorDiv(noise.height(), cellHeight);
        int[] heights = new int[width * depth];
        Arrays.fill(heights, heightAccessor.getMinBuildHeight() - 1);
        boolean[] supported = new boolean[width * depth];
        byte[] checkedDepth = new byte[width * depth];
        // 流体规则直接对应 NoiseBasedChunkGenerator.createFluidPicker，包含原版深部熔岩分支。
        Aquifer.FluidStatus lava = new Aquifer.FluidStatus(-54, Blocks.LAVA.defaultBlockState());
        Aquifer.FluidStatus normal = new Aquifer.FluidStatus(settings.seaLevel(), settings.defaultFluid());
        Aquifer.FluidPicker fluids = (x, y, z) -> y < Math.min(-54, settings.seaLevel()) ? lava : normal;
        var opaque = Heightmap.Types.WORLD_SURFACE_WG.isOpaque();
        // 负坐标按 floorDiv 对齐原版单元边界，边界外的列不参与结果或额外扫描。
        for (int cellX = Math.floorDiv(minX, cellWidth); cellX <= Math.floorDiv(minX + width - 1, cellWidth); cellX++) {
            for (int cellZ = Math.floorDiv(minZ, cellWidth); cellZ <= Math.floorDiv(minZ + depth - 1, cellWidth); cellZ++) {
                int baseX = cellX * cellWidth;
                int baseZ = cellZ * cellWidth;
                int fromX = Math.max(minX, baseX);
                int toX = Math.min(minX + width - 1, baseX + cellWidth - 1);
                int fromZ = Math.max(minZ, baseZ);
                int toZ = Math.min(minZ + depth - 1, baseZ + cellWidth - 1);
                int pending = (toX - fromX + 1) * (toZ - fromZ + 1);
                PrismaticTerrainSampler cell = new PrismaticTerrainSampler(state, baseX, baseZ, noise, settings, fluids);
                cell.initializeForFirstCellX();
                cell.advanceCellX(0);
                // 每个垂直单元只计算一次完整缓存，向下两格继续确认真实地基厚度。
                for (int cellY = cellCountY - 1; cellY >= 0 && pending > 0; cellY--) {
                    cell.selectCellYZ(cellY, 0);
                    for (int localY = cellHeight - 1; localY >= 0 && pending > 0; localY--) {
                        int y = (minCellY + cellY) * cellHeight + localY;
                        cell.updateForY(y, (double) localY / cellHeight);
                        for (int x = fromX; x <= toX; x++) {
                            cell.updateForX(x, (double) (x - baseX) / cellWidth);
                            for (int z = fromZ; z <= toZ; z++) {
                                int index = (x - minX) + (z - minZ) * width;
                                if (checkedDepth[index] >= 3) continue;
                                cell.updateForZ(z, (double) (z - baseZ) / cellWidth);
                                BlockState block = cell.getInterpolatedState();
                                if (block == null) block = settings.defaultBlock();
                                if (checkedDepth[index] == 0) {
                                    if (!opaque.test(block)) continue;
                                    heights[index] = y;
                                    supported[index] = block.is(Blocks.END_STONE);
                                } else {
                                    supported[index] &= block.is(Blocks.END_STONE);
                                }
                                checkedDepth[index]++;
                                if (checkedDepth[index] == 3) pending--;
                            }
                        }
                    }
                }
                cell.stopInterpolation();
            }
        }
        // 位于噪声底边而不足三格的柱不能被当作合格承重地层。
        for (int index = 0; index < supported.length; index++) supported[index] &= checkedDepth[index] == 3;
        return new Terrain(minX, minZ, width, depth, heights, supported);
    }

    // 只在单次结构预检期间保存局部数组，不在世界之间或区块之间保留任何噪声缓存。
    public record Terrain(int minX, int minZ, int width, int depth, int[] heights, boolean[] supported) {
        public int height(int x, int z) { return heights[(x - minX) + (z - minZ) * width]; }
        public boolean supports(int x, int z) { return supported[(x - minX) + (z - minZ) * width]; }
    }

}
