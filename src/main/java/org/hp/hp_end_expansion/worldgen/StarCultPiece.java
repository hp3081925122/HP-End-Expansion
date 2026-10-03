package org.hp.hp_end_expansion.worldgen;

import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

public final class StarCultPiece extends TemplateStructurePiece {
    private final String variant;

    public StarCultPiece(StructureTemplateManager manager, String variant, BlockPos center, Rotation rotation) {
        super(StarwreckWorldgen.STAR_CULT_PIECE.get(), 0, manager, id(normalize(variant)), id(normalize(variant)).toString(), settings(rotation, normalize(variant)), origin(center, normalize(variant)));
        this.variant = normalize(variant);
    }

    public StarCultPiece(StructureTemplateManager manager, CompoundTag tag) {
        super(StarwreckWorldgen.STAR_CULT_PIECE.get(), tag, manager, ignored -> settings(Rotation.valueOf(tag.getString("Rotation")), normalize(tag.getString("Variant"))));
        this.variant = normalize(tag.getString("Variant"));
    }

    public static int radius(String variant) {
        return switch (normalize(variant)) {
            case "canticle_hall" -> 10;
            case "sky_altar" -> 12;
            default -> 7;
        };
    }

    private static String normalize(String variant) {
        return variant == null || variant.isBlank() ? "observatory" : variant.toLowerCase(Locale.ROOT);
    }

    private static ResourceLocation id(String variant) {
        return ResourceLocation.fromNamespaceAndPath("hp_end_expansion", "star_cult/" + normalize(variant));
    }

    private static BlockPos origin(BlockPos center, String variant) {
        int radius = radius(variant);
        return center.offset(-radius, 0, -radius);
    }

    private static StructurePlaceSettings settings(Rotation rotation, String variant) {
        int radius = radius(variant);
        return new StructurePlaceSettings()
            .setRotation(rotation)
            .setRotationPivot(new BlockPos(radius, 0, radius))
            .addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        super.addAdditionalSaveData(context, tag);
        tag.putString("Rotation", placeSettings.getRotation().name());
        tag.putString("Variant", variant);
    }

    @Override
    protected void handleDataMarker(String marker, BlockPos pos, ServerLevelAccessor level, RandomSource random, BoundingBox box) {
        if (!box.isInside(pos) || !marker.equals("star_cult_chest")) return;
        level.setBlock(pos, Blocks.CHEST.defaultBlockState().rotate(placeSettings.getRotation()), 2);
        if (level.getBlockEntity(pos) instanceof RandomizableContainerBlockEntity container) {
            ResourceKey<net.minecraft.world.level.storage.loot.LootTable> loot = ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.fromNamespaceAndPath("hp_end_expansion", "chests/star_cult_" + variant));
            container.setLootTable(loot, random.nextLong());
        }
    }
}
