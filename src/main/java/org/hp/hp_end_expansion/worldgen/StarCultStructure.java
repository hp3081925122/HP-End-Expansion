package org.hp.hp_end_expansion.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.block.Rotation;

public final class StarCultStructure extends Structure {
    public static final MapCodec<StarCultStructure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        settingsCodec(i), Codec.STRING.fieldOf("variant").forGetter(s -> s.variant)
    ).apply(i, StarCultStructure::new));
    private final String variant;

    public StarCultStructure(StructureSettings settings, String variant) {
        super(settings);
        this.variant = variant;
    }

    @Override
    public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        if (!StarwreckConfig.enabled()) return Optional.empty();
        int x = context.chunkPos().getMiddleBlockX();
        int z = context.chunkPos().getMiddleBlockZ();
        int y = context.chunkGenerator().getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
        if (y < 55 || !context.validBiome().test(context.biomeSource().getNoiseBiome(x >> 2, y >> 2, z >> 2, context.randomState().sampler()))) return Optional.empty();
        int radius = StarCultPiece.radius(variant);
        for (int dx = -radius; dx <= radius; dx += Math.max(3, radius / 2)) {
            for (int dz = -radius; dz <= radius; dz += Math.max(3, radius / 2)) {
                int sample = context.chunkGenerator().getFirstOccupiedHeight(x + dx, z + dz, Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
                if (sample < y - 2 || sample > y + 3) return Optional.empty();
            }
        }
        BlockPos center = new BlockPos(x, y, z);
        Rotation rotation = Rotation.getRandom(context.random());
        return Optional.of(new GenerationStub(center, pieces -> pieces.addPiece(new StarCultPiece(context.structureTemplateManager(), variant, center, rotation))));
    }

    @Override
    public StructureType<?> type() {
        return StarwreckWorldgen.STAR_CULT_STRUCTURE.get();
    }
}
