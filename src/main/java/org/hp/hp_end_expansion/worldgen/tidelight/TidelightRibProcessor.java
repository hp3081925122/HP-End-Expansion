package org.hp.hp_end_expansion.worldgen.tidelight;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;

public final class TidelightRibProcessor extends StructureProcessor {
    public static final TidelightRibProcessor INSTANCE = new TidelightRibProcessor();
    public static final MapCodec<TidelightRibProcessor> CODEC = MapCodec.unit(INSTANCE);
    @Override public StructureTemplate.StructureBlockInfo process(LevelReader level, BlockPos origin, BlockPos pivot, StructureTemplate.StructureBlockInfo raw, StructureTemplate.StructureBlockInfo placed, StructurePlaceSettings settings, StructureTemplate template) {
        int z = raw.pos().getZ(), y = raw.pos().getY();
        if (z < 15 && z % 4 == 3 && (y > 2 || y == 2 && raw.pos().getX() != 6)) return null;
        return placed;
    }
    @Override protected StructureProcessorType<?> getType() { return TidelightWorldgen.RIB_PROCESSOR.get(); }
}
