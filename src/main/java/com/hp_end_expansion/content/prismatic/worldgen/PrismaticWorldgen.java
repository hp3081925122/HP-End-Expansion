package com.hp_end_expansion.content.prismatic.worldgen;

import com.hp_end_expansion.HpEndExpansion;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class PrismaticWorldgen {
    // 静态注册类型，配置、群系和结构实例则交给数据包动态注册表加载。
    private static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, HpEndExpansion.MODID);
    private static final DeferredRegister<StructureType<?>> STRUCTURES = DeferredRegister.create(Registries.STRUCTURE_TYPE, HpEndExpansion.MODID);
    private static final DeferredRegister<StructurePieceType> PIECES = DeferredRegister.create(Registries.STRUCTURE_PIECE, HpEndExpansion.MODID);
    public static final DeferredHolder<Feature<?>, PrismaticSurfaceFeature> SURFACE = FEATURES.register("prismatic_surface", PrismaticSurfaceFeature::new);
    public static final DeferredHolder<Feature<?>, PrismaticVegetationFeature> VEGETATION = FEATURES.register("prismatic_vegetation", PrismaticVegetationFeature::new);
    public static final DeferredHolder<Feature<?>, PrismaticTreeFeature> TREE = FEATURES.register("prismatic_tree", PrismaticTreeFeature::new);
    public static final DeferredHolder<StructureType<?>, StructureType<PrismaticStructure>> STRUCTURE = STRUCTURES.register("prismatic_ruin", () -> () -> PrismaticStructure.CODEC);
    public static final DeferredHolder<StructurePieceType, StructurePieceType> RUIN_PIECE = PIECES.register("prismatic_ruin", () -> PrismaticStructurePiece::new);

    // 模组入口只需调用一次，延迟注册不会提前读取方块和实体实例。
    public static void register(IEventBus bus) {
        FEATURES.register(bus);
        STRUCTURES.register(bus);
        PIECES.register(bus);
    }

    private PrismaticWorldgen() { }
}
