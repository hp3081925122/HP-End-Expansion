package org.hp.hp_end_expansion.worldgen;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import org.hp.hp_end_expansion.registry.ModStarwreck;

public final class StarwreckMeteorPiece extends TemplateStructurePiece {
    private static final ResourceLocation TEMPLATE=ResourceLocation.fromNamespaceAndPath("hp_end_expansion","starwreck/giant_meteor");
    public StarwreckMeteorPiece(StructureTemplateManager manager,BlockPos pos,Rotation rotation) {
        super(StarwreckWorldgen.METEOR_PIECE.get(),0,manager,TEMPLATE,TEMPLATE.toString(),settings(rotation),pos);
    }
    public StarwreckMeteorPiece(StructureTemplateManager manager,CompoundTag tag) {
        super(StarwreckWorldgen.METEOR_PIECE.get(),tag,manager,id -> settings(Rotation.valueOf(tag.getString("Rotation"))));
    }
    private static StructurePlaceSettings settings(Rotation rotation) {
        return new StructurePlaceSettings().setRotation(rotation).setRotationPivot(new BlockPos(8,0,8))
            .addProcessor(BlockIgnoreProcessor.STRUCTURE_AND_AIR)
            .addProcessor(new RuleProcessor(List.of(new ProcessorRule(new RandomBlockMatchTest(ModStarwreck.STARWRECK_STONE.get(),0.12F),AlwaysTrueTest.INSTANCE,ModStarwreck.EMBER_STARWRECK_STONE.get().defaultBlockState()))));
    }
    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context,CompoundTag tag) {
        super.addAdditionalSaveData(context,tag);tag.putString("Rotation",placeSettings.getRotation().name());
    }
    @Override
    protected void handleDataMarker(String name,BlockPos pos,ServerLevelAccessor level,RandomSource random,BoundingBox box) { }
}
