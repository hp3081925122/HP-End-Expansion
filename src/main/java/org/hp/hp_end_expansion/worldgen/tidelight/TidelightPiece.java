package org.hp.hp_end_expansion.worldgen.tidelight;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import org.hp.hp_end_expansion.registry.ModTidelight;

public final class TidelightPiece extends TemplateStructurePiece {
    private final boolean chest;
    private final boolean sparse;
    public TidelightPiece(StructureTemplateManager manager, String name, BlockPos pos, Rotation rotation, boolean chest, boolean sparse) {
        super(TidelightWorldgen.PIECE.get(), 0, manager, id(name), id(name).toString(), settings(rotation, sparse), pos);
        this.chest = chest; this.sparse = sparse;
    }
    public TidelightPiece(StructureTemplateManager manager, CompoundTag tag) {
        super(TidelightWorldgen.PIECE.get(), tag, manager, id -> settings(Rotation.valueOf(tag.getString("Rotation")), tag.getBoolean("Sparse")));
        this.chest = tag.getBoolean("Chest"); this.sparse = tag.getBoolean("Sparse");
    }
    private static ResourceLocation id(String name) { return ResourceLocation.fromNamespaceAndPath("hp_end_expansion", "tidelight/" + name); }
    private static StructurePlaceSettings settings(Rotation rotation, boolean sparse) {
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rotation).addProcessor(BlockIgnoreProcessor.STRUCTURE_AND_AIR);
        if (sparse) settings.addProcessor(TidelightRibProcessor.INSTANCE);
        return settings
            .addProcessor(new RuleProcessor(List.of(
                new ProcessorRule(new RandomBlockMatchTest(ModTidelight.REEF_BONE_BLOCK.get(), 0.15F), AlwaysTrueTest.INSTANCE, ModTidelight.LUMEN_CORAL_BLOCK.get().defaultBlockState()),
                new ProcessorRule(new RandomBlockMatchTest(ModTidelight.REEF_BONE_BLOCK.get(), 0.17647F), AlwaysTrueTest.INSTANCE, ModTidelight.GLOWKELP_REEFSTONE.get().defaultBlockState())
            )));
    }
    @Override protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        super.addAdditionalSaveData(context, tag); tag.putString("Rotation", placeSettings.getRotation().name()); tag.putBoolean("Chest", chest); tag.putBoolean("Sparse", sparse);
    }
    @Override protected void handleDataMarker(String marker, BlockPos pos, ServerLevelAccessor level, RandomSource random, BoundingBox box) {
        if (!box.isInside(pos)) return;
        if (marker.equals("middle_chest") && !chest) { level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2); return; }
        if (marker.equals("middle_chest") || marker.equals("head_chest") || marker.equals("shrine_chest")) {
            level.setBlock(pos, Blocks.CHEST.defaultBlockState().rotate(placeSettings.getRotation()), 2);
            String loot = marker.equals("shrine_chest") ? "tide_shrine" : "whalefall_reef";
            if (level.getBlockEntity(pos) instanceof RandomizableContainerBlockEntity container) container.setLootTable(ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.fromNamespaceAndPath("hp_end_expansion", "chests/" + loot)), random.nextLong());
        }
    }
}
