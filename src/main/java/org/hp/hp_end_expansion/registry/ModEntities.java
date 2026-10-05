package org.hp.hp_end_expansion.registry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.hp.hp_end_expansion.Hp_end_expansion;
import org.hp.hp_end_expansion.block.tidelight.ReefstoneBlock;
import org.hp.hp_end_expansion.entity.EndMoteEntity;
import org.hp.hp_end_expansion.entity.RiftBladeEntity;
import org.hp.hp_end_expansion.entity.RiftFissureEntity;
import org.hp.hp_end_expansion.entity.RiftMantisEntity;
import org.hp.hp_end_expansion.entity.RiftMatriarchEntity;
import org.hp.hp_end_expansion.entity.RiftVfxEntity;
import org.hp.hp_end_expansion.entity.StarCoreEntity;
import org.hp.hp_end_expansion.entity.StarDevourerEntity;
import org.hp.hp_end_expansion.entity.VoidRayEntity;
import org.hp.hp_end_expansion.entity.VoidRayVfxEntity;
import org.hp.hp_end_expansion.entity.tidelight.LanternJellyfishEntity;
import org.hp.hp_end_expansion.worldgen.tidelight.TidelightWorldgen;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, Hp_end_expansion.MODID);
    // 裂隙螳螂本体
    public static final DeferredHolder<EntityType<?>, EntityType<RiftMantisEntity>> RIFT_MANTIS = ENTITY_TYPES.register(
        "rift_mantis",
        () -> EntityType.Builder.of(RiftMantisEntity::new, MobCategory.MONSTER)
            .sized(2.7F, 3.6F)
            .clientTrackingRange(10)
            .build("hp_end_expansion:rift_mantis")
    );
    public static final DeferredHolder<EntityType<?>, EntityType<RiftMatriarchEntity>> RIFT_MATRIARCH = ENTITY_TYPES.register(
        "rift_matriarch",
        () -> EntityType.Builder.of(RiftMatriarchEntity::new, MobCategory.MONSTER)
            .sized(4.5F, 6.0F)
            .eyeHeight(4.8F)
            .clientTrackingRange(12)
            .build("hp_end_expansion:rift_matriarch")
    );
    public static final DeferredHolder<EntityType<?>, EntityType<VoidRayEntity>> VOID_RAY = ENTITY_TYPES.register(
        "void_ray",
        () -> EntityType.Builder.of(VoidRayEntity::new, MobCategory.MONSTER)
            .sized(3.0F, 1.2F)
            .eyeHeight(0.9F)
            .clientTrackingRange(10)
            .build("hp_end_expansion:void_ray")
    );
    public static final DeferredHolder<EntityType<?>, EntityType<StarDevourerEntity>> STAR_DEVOURER = ENTITY_TYPES.register(
        "star_devourer",
        () -> EntityType.Builder.of(StarDevourerEntity::new, MobCategory.MONSTER)
            .sized(7.5F, 3.0F)
            .eyeHeight(2.4F)
            .clientTrackingRange(12)
            .build("hp_end_expansion:star_devourer")
    );
    // 末晶萤：末地小型被动生物
    public static final DeferredHolder<EntityType<?>, EntityType<EndMoteEntity>> END_MOTE = ENTITY_TYPES.register(
        "end_mote",
        () -> EntityType.Builder.of(EndMoteEntity::new, MobCategory.CREATURE)
            .sized(0.5F, 0.5F)
            .clientTrackingRange(8)
            .build("hp_end_expansion:end_mote")
    );
    public static final DeferredHolder<EntityType<?>, EntityType<LanternJellyfishEntity>> LANTERN_JELLYFISH = ENTITY_TYPES.register(
        "lantern_jellyfish",
        () -> EntityType.Builder.of(LanternJellyfishEntity::new, MobCategory.CREATURE)
            .sized(0.8F, 1.2F)
            .clientTrackingRange(8)
            .build("hp_end_expansion:lantern_jellyfish")
    );
    // 吞星星核
    public static final DeferredHolder<EntityType<?>, EntityType<StarCoreEntity>> STAR_CORE = ENTITY_TYPES.register(
        "star_core",
        () -> EntityType.Builder.of(StarCoreEntity::new, MobCategory.MISC)
            .sized(1.2F, 1.2F)
            .noSave()
            .fireImmune()
            .clientTrackingRange(10)
            .build("hp_end_expansion:star_core")
    );
    // 裂空鳐技能特效：预警线、冲击环、漩涡、星陨
    public static final DeferredHolder<EntityType<?>, EntityType<VoidRayVfxEntity>> VOID_RAY_VFX = ENTITY_TYPES.register(
        "void_ray_vfx",
        () -> EntityType.Builder.<VoidRayVfxEntity>of(VoidRayVfxEntity::new, MobCategory.MISC)
            .sized(0.5F, 0.5F)
            .noSave()
            .noSummon()
            .fireImmune()
            .clientTrackingRange(8)
            .updateInterval(20)
            .build("hp_end_expansion:void_ray_vfx")
    );
    // 弧光与裂隙门等短命特效
    public static final DeferredHolder<EntityType<?>, EntityType<RiftVfxEntity>> RIFT_VFX = ENTITY_TYPES.register(
        "rift_vfx",
        () -> EntityType.Builder.<RiftVfxEntity>of(RiftVfxEntity::new, MobCategory.MISC)
            .sized(0.5F, 0.5F)
            .noSave()
            .noSummon()
            .fireImmune()
            .clientTrackingRange(8)
            .updateInterval(20)
            .build("hp_end_expansion:rift_vfx")
    );
    // 裂隙飞刃投射物
    public static final DeferredHolder<EntityType<?>, EntityType<RiftBladeEntity>> RIFT_BLADE = ENTITY_TYPES.register(
        "rift_blade",
        () -> EntityType.Builder.<RiftBladeEntity>of(RiftBladeEntity::new, MobCategory.MISC)
            .sized(1.15F, 0.4F)
            .noSave()
            .fireImmune()
            .clientTrackingRange(8)
            .updateInterval(1)
            .build("hp_end_expansion:rift_blade")
    );
    // 地裂突刺
    public static final DeferredHolder<EntityType<?>, EntityType<RiftFissureEntity>> RIFT_FISSURE = ENTITY_TYPES.register(
        "rift_fissure",
        () -> EntityType.Builder.<RiftFissureEntity>of(RiftFissureEntity::new, MobCategory.MISC)
            .sized(0.5F, 0.5F)
            .noSave()
            .noSummon()
            .fireImmune()
            .clientTrackingRange(8)
            .updateInterval(20)
            .build("hp_end_expansion:rift_fissure")
    );

    private ModEntities() {
    }

    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
        modEventBus.addListener(ModEntities::registerAttributes);
        modEventBus.addListener(ModEntities::registerSpawnPlacements);
    }

    private static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(RIFT_MANTIS.get(), RiftMantisEntity.createAttributes().build());
        event.put(RIFT_MATRIARCH.get(), RiftMatriarchEntity.createAttributes().build());
        event.put(VOID_RAY.get(), VoidRayEntity.createAttributes().build());
        event.put(STAR_DEVOURER.get(), StarDevourerEntity.createAttributes().build());
        event.put(STAR_CORE.get(), StarCoreEntity.createAttributes().build());
        event.put(END_MOTE.get(), EndMoteEntity.createAttributes().build());
        event.put(LANTERN_JELLYFISH.get(), LanternJellyfishEntity.createAttributes().build());
    }

    // 地面生成规则：沿用怪物的黑暗生成检测
    private static void registerSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        event.register(RIFT_MANTIS.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            Monster::checkMonsterSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        // 裂空鳐：空中生成，不要求黑暗
        event.register(VOID_RAY.get(), SpawnPlacementTypes.NO_RESTRICTIONS, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            Monster::checkAnyLightMonsterSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        // 末晶萤：地面生成，无亮度限制
        event.register(END_MOTE.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            PathfinderMob::checkMobSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(LANTERN_JELLYFISH.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            ModEntities::lanternJellyfishSpawn, RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }

    private static <T extends Mob> boolean lanternJellyfishSpawn(EntityType<T> type, ServerLevelAccessor level, MobSpawnType reason, BlockPos pos, RandomSource random) {
        if (!Mob.checkMobSpawnRules(type, level, reason, pos, random)) {
            return false;
        }
        return reason != MobSpawnType.NATURAL
            && reason != MobSpawnType.CHUNK_GENERATION
            || level.getBiome(pos).is(TidelightWorldgen.BIOME)
            && ReefstoneBlock.isReef(level.getBlockState(pos.below()));
    }
}
