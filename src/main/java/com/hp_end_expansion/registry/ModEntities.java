package com.hp_end_expansion.registry;

import com.hp_end_expansion.HpEndExpansion;
import com.hp_end_expansion.world.entity.EnderBox;
import com.hp_end_expansion.world.entity.EnderFish;
import com.hp_end_expansion.world.entity.EnderNavigator;
import com.hp_end_expansion.world.entity.EnderRift;
import com.hp_end_expansion.world.entity.EnderSnail;
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
            .sized(12.0F, 6.3F)
            .eyeHeight( 4.4F)
            .passengerAttachments(9.0F)
            .clientTrackingRange(32)
            .updateInterval(2)
            .build("void_whale"));

    // 末影盒实体类型，当前先使用临时方块渲染占位。
    public static final DeferredHolder<EntityType<?>, EntityType<EnderBox>> ENDER_BOX = ENTITIES.register("ender_box", () -> EntityType.Builder.of(EnderBox::new, MobCategory.CREATURE)
            .sized(1.0F, 1.0F)
            .clientTrackingRange(8)
            .updateInterval(3)
            .build("ender_box"));

    // 末影蜗牛实体类型，用于紫颂园丁生物的基础模型和动画。
    public static final DeferredHolder<EntityType<?>, EntityType<EnderSnail>> ENDER_SNAIL = ENTITIES.register("ender_snail", () -> EntityType.Builder.of(EnderSnail::new, MobCategory.CREATURE)
            .sized(1.2F, 0.85F)
            .clientTrackingRange(8)
            .updateInterval(3)
            .build("ender_snail"));

    // 末影鱼实体类型，小型虚空游动生物，移动时会随机短距离穿梭。
    public static final DeferredHolder<EntityType<?>, EntityType<EnderFish>> ENDER_FISH = ENTITIES.register("ender_fish", () -> EntityType.Builder.of(EnderFish::new, MobCategory.CREATURE)
            .fireImmune()
            .canSpawnFarFromPlayer()
            .sized(0.7F, 0.35F)
            .eyeHeight(0.22F)
            .clientTrackingRange(8)
            .updateInterval(2)
            .build("ender_fish"));

    // 末影领航者实体类型，作为会施法和召唤鱼群的末地精英怪。
    public static final DeferredHolder<EntityType<?>, EntityType<EnderNavigator>> ENDER_NAVIGATOR = ENTITIES.register("ender_navigator", () -> EntityType.Builder.of(EnderNavigator::new, MobCategory.MONSTER)
            .fireImmune()
            .canSpawnFarFromPlayer()
            .sized(1.9F, 4.5F)
            .eyeHeight(3.3F)
            .clientTrackingRange(10)
            .updateInterval(2)
            .build("ender_navigator"));

    // 末影裂隙实体类型，用于击杀末影鱼后临时生成的传送入口。
    public static final DeferredHolder<EntityType<?>, EntityType<EnderRift>> ENDER_RIFT = ENTITIES.register("ender_rift", () -> EntityType.Builder.of(EnderRift::new, MobCategory.MISC)
            .fireImmune()
            .noSave()
            .sized(1.0F, 1.4F)
            .clientTrackingRange(8)
            .updateInterval(2)
            .build("ender_rift"));

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
        event.put(ENDER_BOX.get(), EnderBox.createAttributes().build());
        event.put(ENDER_SNAIL.get(), EnderSnail.createAttributes().build());
        event.put(ENDER_FISH.get(), EnderFish.createAttributes().build());
        event.put(ENDER_NAVIGATOR.get(), EnderNavigator.createAttributes().build());
    }

    // 注册虚空鲸生成条件。
    public static void registerSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        event.register(VOID_WHALE.get(), SpawnPlacementTypes.NO_RESTRICTIONS, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, VoidWhale::canSpawn, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(ENDER_SNAIL.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, EnderSnail::canSpawn, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(ENDER_FISH.get(), SpawnPlacementTypes.NO_RESTRICTIONS, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, EnderFish::canSpawn, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(ENDER_NAVIGATOR.get(), SpawnPlacementTypes.NO_RESTRICTIONS, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, EnderNavigator::canSpawn, RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }
}
