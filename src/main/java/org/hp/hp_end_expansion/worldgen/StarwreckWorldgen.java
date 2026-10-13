package org.hp.hp_end_expansion.worldgen;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import org.hp.hp_end_expansion.Hp_end_expansion;

public final class StarwreckWorldgen {
    public static final ResourceKey<Biome> BIOME = ResourceKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath(Hp_end_expansion.MODID, "starwreck_wastes"));
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, Hp_end_expansion.MODID);
    public static final DeferredRegister<StructureType<?>> STRUCTURES = DeferredRegister.create(Registries.STRUCTURE_TYPE, Hp_end_expansion.MODID);
    public static final DeferredRegister<StructurePieceType> PIECES = DeferredRegister.create(Registries.STRUCTURE_PIECE, Hp_end_expansion.MODID);
    public static final RegistryObject<StarwreckFeature> TERRAIN = FEATURES.register("starwreck", StarwreckFeature::new);
    public static final RegistryObject<StructureType<StarwreckStructure>> STRUCTURE = STRUCTURES.register("starwreck", () -> () -> StarwreckStructure.CODEC.codec());
    public static final RegistryObject<StructurePieceType> TERRAIN_PIECE = PIECES.register("starwreck_terrain", () -> (context, tag) -> new StarwreckPiece(tag));
    public static final RegistryObject<StructurePieceType> METEOR_PIECE = PIECES.register("starwreck_meteor", () -> (context, tag) -> new StarwreckMeteorPiece(context.structureTemplateManager(), tag));
    public static void register(IEventBus bus) { FEATURES.register(bus); STRUCTURES.register(bus); PIECES.register(bus); }
}
