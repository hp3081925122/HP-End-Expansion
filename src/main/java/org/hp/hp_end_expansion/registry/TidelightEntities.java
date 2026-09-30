package org.hp.hp_end_expansion.registry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.*;
import net.neoforged.neoforge.registries.*;
import org.hp.hp_end_expansion.Hp_end_expansion;
import org.hp.hp_end_expansion.block.tidelight.ReefstoneBlock;
import org.hp.hp_end_expansion.entity.tidelight.*;
import org.hp.hp_end_expansion.worldgen.tidelight.TidelightWorldgen;

public final class TidelightEntities {
    public static final DeferredRegister<EntityType<?>> TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, Hp_end_expansion.MODID);
    public static final DeferredHolder<EntityType<?>, EntityType<LanternJellyfishEntity>> LANTERN_JELLYFISH = TYPES.register("lantern_jellyfish", () -> EntityType.Builder.of(LanternJellyfishEntity::new, MobCategory.CREATURE).sized(0.8F, 1.2F).clientTrackingRange(8).build("hp_end_expansion:lantern_jellyfish"));
    public static final DeferredHolder<EntityType<?>, EntityType<PearlHermitCrabEntity>> PEARL_HERMIT_CRAB = TYPES.register("pearl_hermit_crab", () -> EntityType.Builder.of(PearlHermitCrabEntity::new, MobCategory.CREATURE).sized(0.6F, 0.5F).clientTrackingRange(8).build("hp_end_expansion:pearl_hermit_crab"));
    public static final DeferredHolder<EntityType<?>, EntityType<ReefEelEntity>> REEF_EEL = TYPES.register("reef_eel", () -> EntityType.Builder.of(ReefEelEntity::new, MobCategory.MONSTER).sized(0.8F, 0.6F).clientTrackingRange(8).build("hp_end_expansion:reef_eel"));
    public static final DeferredItem<SpawnEggItem> JELLYFISH_EGG = ModItems.ITEMS.register("lantern_jellyfish_spawn_egg", () -> new SpawnEggItem(LANTERN_JELLYFISH.get(), 0x17495A, 0xBDFFF0, new Item.Properties()));
    public static final DeferredItem<SpawnEggItem> CRAB_EGG = ModItems.ITEMS.register("pearl_hermit_crab_spawn_egg", () -> new SpawnEggItem(PEARL_HERMIT_CRAB.get(), 0x76878A, 0xC4B6E6, new Item.Properties()));
    public static final DeferredItem<SpawnEggItem> EEL_EGG = ModItems.ITEMS.register("reef_eel_spawn_egg", () -> new SpawnEggItem(REEF_EEL.get(), 0x15242B, 0x4FDCCB, new Item.Properties()));
    public static void register(IEventBus bus) {
        TYPES.register(bus); bus.addListener(TidelightEntities::attributes); bus.addListener(TidelightEntities::placements);
        ModTidelight.ITEMS.add(JELLYFISH_EGG); ModTidelight.ITEMS.add(CRAB_EGG); ModTidelight.ITEMS.add(EEL_EGG);
    }
    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(LANTERN_JELLYFISH.get(), LanternJellyfishEntity.createAttributes().build());
        event.put(PEARL_HERMIT_CRAB.get(), PearlHermitCrabEntity.createAttributes().build());
        event.put(REEF_EEL.get(), ReefEelEntity.createAttributes().build());
    }
    private static void placements(RegisterSpawnPlacementsEvent event) {
        event.register(LANTERN_JELLYFISH.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, TidelightEntities::spawn, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(PEARL_HERMIT_CRAB.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, TidelightEntities::crabSpawn, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(REEF_EEL.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, TidelightEntities::eelSpawn, RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }
    private static <T extends Mob> boolean spawn(EntityType<T> type, ServerLevelAccessor level, MobSpawnType reason, BlockPos pos, RandomSource random) {
        if (!Mob.checkMobSpawnRules(type, level, reason, pos, random)) return false;
        return reason != MobSpawnType.NATURAL && reason != MobSpawnType.CHUNK_GENERATION || level.getBiome(pos).is(TidelightWorldgen.BIOME) && ReefstoneBlock.isReef(level.getBlockState(pos.below()));
    }
    private static boolean crabSpawn(EntityType<PearlHermitCrabEntity> type, ServerLevelAccessor level, MobSpawnType reason, BlockPos pos, RandomSource random) {
        return Mob.checkMobSpawnRules(type, level, reason, pos, random) && (reason != MobSpawnType.NATURAL && reason != MobSpawnType.CHUNK_GENERATION || level.getBiome(pos).is(TidelightWorldgen.BIOME) && (level.getBlockState(pos.below()).is(ModTidelight.PEARL_SAND.get()) || level.getBlockState(pos.below()).is(ModTidelight.TIDEMARKED_REEFSTONE.get())));
    }
    private static boolean eelSpawn(EntityType<ReefEelEntity> type, ServerLevelAccessor level, MobSpawnType reason, BlockPos pos, RandomSource random) {
        if (!Monster.checkAnyLightMonsterSpawnRules(type, level, reason, pos, random)) return false;
        if (reason != MobSpawnType.NATURAL && reason != MobSpawnType.CHUNK_GENERATION) return true;
        if (!level.getBiome(pos).is(TidelightWorldgen.BIOME)) return false;
        for (BlockPos at : BlockPos.betweenClosed(pos.offset(-4, 2, -4), pos.offset(4, 8, 4))) if (level.getBlockState(at).is(ModTidelight.TIDEMARKED_REEFSTONE.get()) || level.getBlockState(at).is(ModTidelight.LUMEN_CORAL_BLOCK.get())) return true;
        return false;
    }
}
