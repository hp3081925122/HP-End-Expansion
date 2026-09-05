package com.hp_end_expansion.content.prismatic.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

public final class PrismaticStructure extends Structure {
    // 四个动态结构实例共享地基规则，通过明确的布局编号选择不同探索建筑。
    public static final MapCodec<PrismaticStructure> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            settingsCodec(instance), Codec.intRange(0, 3).fieldOf("layout").forGetter(structure -> structure.layout))
            .apply(instance, PrismaticStructure::new));
    private final int layout;

    // 布局编号在数据包中固定，修改不会影响已序列化结构片段的种类。
    public PrismaticStructure(StructureSettings settings, int layout) {
        super(settings);
        this.layout = layout;
    }

    // 大建筑在整片实地上生成；只读取生成器噪声，不读取或强制生成邻区块。
    @Override protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        if (!(context.chunkGenerator() instanceof NoiseBasedChunkGenerator noiseGenerator)) return Optional.empty();
        int width = PrismaticStructurePiece.width(layout);
        int depth = PrismaticStructurePiece.depth(layout);
        int centerX = context.chunkPos().getMiddleBlockX();
        int centerZ = context.chunkPos().getMiddleBlockZ();
        int minX = centerX - width / 2;
        int minZ = centerZ - depth / 2;
        int minHeight = Integer.MAX_VALUE;
        int maxHeight = Integer.MIN_VALUE;
        int maxSlope = layout == 0 ? 2 : 8;
        // 先以低成本群系网格淘汰错误区域，不对每个位置重新构造噪声单元。
        for (int x = minX - 2; x <= minX + width + 1; x += 4) {
            for (int z = minZ - 2; z <= minZ + depth + 1; z += 4) {
                if (!context.biomeSource().getNoiseBiome(QuartPos.fromBlock(x), 16,
                        QuartPos.fromBlock(z), context.randomState().sampler()).is(PrismaticBiomeRouting.WASTES)) return Optional.empty();
            }
        }
        PrismaticTerrainSampler.Terrain terrain = PrismaticTerrainSampler.sample(noiseGenerator, context.randomState(),
                context.heightAccessor(), minX - 2, minZ - 2, width + 4, depth + 4);
        // 精确检查每一柱及三格承重厚度，共用单元采样不降低落地检查的密度。
        for (int x = minX - 2; x <= minX + width + 1; x++) {
            for (int z = minZ - 2; z <= minZ + depth + 1; z++) {
                int y = terrain.height(x, z);
                if (y < 48 || !terrain.supports(x, z)) return Optional.empty();
                minHeight = Math.min(minHeight, y);
                maxHeight = Math.max(maxHeight, y);
                if (maxHeight - minHeight > maxSlope) return Optional.empty();
            }
        }
        int floorY = maxHeight;
        // 入口高差在噪声预检阶段冻结，分块放置不会读取远处或已被建筑改变的高度图。
        int northRise = Math.min(layout == 0 ? 2 : 8, floorY - terrain.height(centerX, minZ - 1));
        int southRise = Math.min(layout == 0 ? 2 : 8, floorY - terrain.height(centerX, minZ + depth));
        return Optional.of(new GenerationStub(new BlockPos(centerX, floorY, centerZ),
                builder -> builder.addPiece(new PrismaticStructurePiece(layout, minX, floorY, minZ, northRise, southRise))));
    }

    // 动态结构通过本模组已登记的类型编解码，可被原版定位指令发现。
    @Override public StructureType<?> type() { return PrismaticWorldgen.STRUCTURE.get(); }
}
