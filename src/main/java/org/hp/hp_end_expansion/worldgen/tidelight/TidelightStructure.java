package org.hp.hp_end_expansion.worldgen.tidelight;

import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.*;
import org.hp.hp_end_expansion.worldgen.StarwreckConfig;

public final class TidelightStructure extends Structure {
    public static final MapCodec<TidelightStructure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(settingsCodec(i), Codec.BOOL.fieldOf("whale").forGetter(s -> s.whale)).apply(i, TidelightStructure::new));
    private final boolean whale;
    public TidelightStructure(StructureSettings settings, boolean whale) { super(settings); this.whale = whale; }
    @Override public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        if (!StarwreckConfig.tidelightEnabled()) return Optional.empty();
        int x = context.chunkPos().getMiddleBlockX(), z = context.chunkPos().getMiddleBlockZ();
        int y = height(context, x, z);
        if (y < 40 || !context.validBiome().test(context.biomeSource().getNoiseBiome(x >> 2, y >> 2, z >> 2, context.randomState().sampler()))) return Optional.empty();
        for (int dx = -4; dx <= 4; dx += 4) for (int dz = -4; dz <= 4; dz += 4) {
            int h = height(context, x + dx, z + dz);
            if (h < y - 2 || h > y + 2 || !context.chunkGenerator().getBaseColumn(x + dx, z + dz, context.heightAccessor(), context.randomState()).getBlock(y - 3).is(Blocks.END_STONE)) return Optional.empty();
        }
        Rotation rotation = Rotation.getRandom(context.random());
        if (whale) {
            int lowest = Integer.MAX_VALUE;
            for (Rotation candidate : Rotation.values()) {
                BlockPos offset = new BlockPos(0, 0, 32).rotate(candidate);
                int h = height(context, x + offset.getX(), z + offset.getZ());
                if (h < lowest) { lowest = h; rotation = candidate; }
            }
            if (lowest >= y - 6) return Optional.empty();
        }
        Rotation chosen = rotation;
        int repeats = 1 + context.random().nextInt(2);
        BlockPos origin = new BlockPos(x, y - 1, z);
        return Optional.of(new GenerationStub(new BlockPos(x, y, z), pieces -> {
            if (!whale) pieces.addPiece(new TidelightPiece(context.structureTemplateManager(), "tide_shrine", origin.offset(new BlockPos(-4, 0, -4).rotate(chosen)), chosen, false, false));
            else {
                BlockPos start = origin.offset(new BlockPos(-6, 0, -6).rotate(chosen));
                pieces.addPiece(new TidelightPiece(context.structureTemplateManager(), "whale_head", start, chosen, false, false));
                for (int n = 0; n < repeats; n++) pieces.addPiece(new TidelightPiece(context.structureTemplateManager(), "whale_chest", start.offset(new BlockPos(0, 0, 12 + 16 * n).rotate(chosen)), chosen, n == 0, repeats == 2));
                pieces.addPiece(new TidelightPiece(context.structureTemplateManager(), "whale_tail", start.offset(new BlockPos(0, 0, 12 + 16 * repeats).rotate(chosen)), chosen, false, false));
            }
        }));
    }
    private static int height(GenerationContext context, int x, int z) { return context.chunkGenerator().getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState()); }
    @Override public StructureType<?> type() { return TidelightWorldgen.STRUCTURE.get(); }
}
