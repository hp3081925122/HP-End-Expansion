package com.hp_end_expansion.registry;

import com.hp_end_expansion.HpEndExpansion;
import com.hp_end_expansion.world.entity.VoidWhale;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    // 实体延迟注册器。
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, HpEndExpansion.MODID);

    // 虚空鲸实体类型，包含体积、骑乘点、追踪范围和刷新频率。
    public static final DeferredHolder<EntityType<?>, EntityType<VoidWhale>> VOID_WHALE = ENTITIES.register("void_whale", () -> EntityType.Builder.of(VoidWhale::new, MobCategory.CREATURE)
            .fireImmune()
            .canSpawnFarFromPlayer()
            .sized(24.0F, 12.3F)
            .eyeHeight(8.4F)
            .passengerAttachments(9.0F)
            .clientTrackingRange(32)
            .updateInterval(2)
            .build("void_whale"));

    // 实体注册类只提供静态入口，不允许实例化。
    private ModEntities() {
    }

    // 注册实体延迟注册器。
    public static void register(IEventBus modEventBus) {
        ENTITIES.register(modEventBus);
    }

    // 注册虚空鲸属性。
    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(VOID_WHALE.get(), VoidWhale.createAttributes().build());
    }

    // 注册虚空鲸生成条件。
    public static void registerSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        event.register(VOID_WHALE.get(), SpawnPlacementTypes.NO_RESTRICTIONS, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, VoidWhale::canSpawn, RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }
}
