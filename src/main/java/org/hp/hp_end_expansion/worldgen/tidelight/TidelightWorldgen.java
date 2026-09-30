package org.hp.hp_end_expansion.worldgen.tidelight;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;
import org.hp.hp_end_expansion.Hp_end_expansion;

public final class TidelightWorldgen {
    public static final ResourceKey<Biome> BIOME = ResourceKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath(Hp_end_expansion.MODID, "tidelight_reef"));
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, Hp_end_expansion.MODID);
    public static final DeferredRegister<StructureType<?>> STRUCTURES = DeferredRegister.create(Registries.STRUCTURE_TYPE, Hp_end_expansion.MODID);
    public static final DeferredRegister<StructurePieceType> PIECES = DeferredRegister.create(Registries.STRUCTURE_PIECE, Hp_end_expansion.MODID);
    public static final DeferredRegister<StructureProcessorType<?>> PROCESSORS = DeferredRegister.create(Registries.STRUCTURE_PROCESSOR, Hp_end_expansion.MODID);
    public static final DeferredHolder<StructureProcessorType<?>, StructureProcessorType<TidelightRibProcessor>> RIB_PROCESSOR = PROCESSORS.register("tidelight_ribs", () -> () -> TidelightRibProcessor.CODEC);
    public static final DeferredHolder<Feature<?>, TidelightFeature> FEATURE = FEATURES.register("tidelight", TidelightFeature::new);
    public static final DeferredHolder<StructureType<?>, StructureType<TidelightStructure>> STRUCTURE = STRUCTURES.register("tidelight", () -> () -> TidelightStructure.CODEC);
    public static final DeferredHolder<StructurePieceType, StructurePieceType> PIECE = PIECES.register("tidelight", () -> (context, tag) -> new TidelightPiece(context.structureTemplateManager(), tag));
    public static void register(IEventBus bus) { FEATURES.register(bus); STRUCTURES.register(bus); PIECES.register(bus); PROCESSORS.register(bus); }
}
