package org.hp.hp_end_expansion.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.hp.hp_end_expansion.Hp_end_expansion;
import org.hp.hp_end_expansion.entity.RiftBladeEntity;
import org.hp.hp_end_expansion.entity.RiftFissureEntity;
import org.hp.hp_end_expansion.entity.RiftMantisEntity;
import org.hp.hp_end_expansion.entity.RiftVfxEntity;

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
    }

    // 地面生成规则：沿用怪物的黑暗生成检测
    private static void registerSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        event.register(RIFT_MANTIS.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            Monster::checkMonsterSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }
}
