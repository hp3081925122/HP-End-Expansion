package com.hp_end_expansion.content.prismatic;

import com.hp_end_expansion.HpEndExpansion;
import com.hp_end_expansion.content.prismatic.entity.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

// 折光实体与刷怪蛋独立注册，外部结构只需通过短标识查询类型。
public final class PrismaticEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, HpEndExpansion.MODID);
    public static final DeferredRegister.Items EGGS = DeferredRegister.createItems(HpEndExpansion.MODID);
    private static final Map<String, Supplier<? extends EntityType<?>>> TYPES = new LinkedHashMap<>();

    // 四种生态动物按实际模型尺寸设置碰撞体。
    public static final DeferredHolder<EntityType<?>, EntityType<Shardling>> SHARDLING = creature("shardling", Shardling::new, MobCategory.CREATURE, 0.65F, 0.35F, 0x607D91, 0xEDAC50);
    public static final DeferredHolder<EntityType<?>, EntityType<PrismHare>> PRISM_HARE = creature("prism_hare", PrismHare::new, MobCategory.CREATURE, 0.6F, 0.8F, 0xD8CEB0, 0xAC90CD);
    public static final DeferredHolder<EntityType<?>, EntityType<FacetRam>> FACET_RAM = creature("facet_ram", FacetRam::new, MobCategory.CREATURE, 1.0F, 1.2F, 0xCABCA0, 0x607D91);
    public static final DeferredHolder<EntityType<?>, EntityType<DuskMoth>> DUSK_MOTH = creature("dusk_moth", DuskMoth::new, MobCategory.CREATURE, 0.65F, 0.55F, 0x302739, 0xBD97BD);

    // 四种普通怪物和两个精英分别有专用行为类。
    public static final DeferredHolder<EntityType<?>, EntityType<GlassStalker>> GLASS_STALKER = creature("glass_stalker", GlassStalker::new, MobCategory.MONSTER, 1.15F, 0.85F, 0x52646E, 0xD56CB0);
    public static final DeferredHolder<EntityType<?>, EntityType<NeedleSpitter>> NEEDLE_SPITTER = creature("needle_spitter", NeedleSpitter::new, MobCategory.MONSTER, 0.9F, 1.6F, 0x6B7166, 0xD5BFCF);
    public static final DeferredHolder<EntityType<?>, EntityType<Shardback>> SHARDBACK = creature("shardback", Shardback::new, MobCategory.MONSTER, 1.25F, 1.0F, 0xD8CEB0, 0x756475);
    public static final DeferredHolder<EntityType<?>, EntityType<GlareWisp>> GLARE_WISP = creature("glare_wisp", GlareWisp::new, MobCategory.MONSTER, 0.8F, 0.85F, 0x302739, 0xEDAC50);
    public static final DeferredHolder<EntityType<?>, EntityType<FaultWarden>> FAULT_WARDEN = creature("fault_warden", FaultWarden::new, MobCategory.MONSTER, 2.4F, 2.0F, 0x726C65, 0xE8BE90);
    public static final DeferredHolder<EntityType<?>, EntityType<MirrorHuntress>> MIRROR_HUNTRESS = creature("mirror_huntress", MirrorHuntress::new, MobCategory.MONSTER, 1.35F, 2.6F, 0x302739, 0xD56CB0);
    public static final DeferredHolder<EntityType<?>, EntityType<ParallaxRegent>> PARALLAX_REGENT = creature("parallax_regent", ParallaxRegent::new, MobCategory.MONSTER, 3.0F, 3.1F, 0xD8CEB0, 0xD56CB0);

    // 技能晶针属于辅助投射物，不计入十一种生物，不生成刷怪蛋。
    public static final DeferredHolder<EntityType<?>, EntityType<CrystalNeedle>> CRYSTAL_NEEDLE = ENTITIES.register("prismatic_crystal_needle",
            () -> EntityType.Builder.<CrystalNeedle>of(CrystalNeedle::new, MobCategory.MISC).sized(0.2F, 0.2F)
                    .clientTrackingRange(8).updateInterval(1).noSave().build(HpEndExpansion.MODID + ":prismatic_crystal_needle"));

    private PrismaticEntities() { }

    // 同一帮助方法实际用于全部生物，统一添加注册项、类型查询和刷怪蛋。
    private static <T extends Mob> DeferredHolder<EntityType<?>, EntityType<T>> creature(String id, EntityType.EntityFactory<T> factory,
            MobCategory category, float width, float height, int primaryColor, int secondaryColor) {
        DeferredHolder<EntityType<?>, EntityType<T>> holder = ENTITIES.register("prismatic_" + id,
                () -> EntityType.Builder.of(factory, category).sized(width, height).clientTrackingRange(12)
                        .updateInterval(2).build(HpEndExpansion.MODID + ":prismatic_" + id));
        TYPES.put(id, holder);
        EGGS.register("prismatic_" + id + "_spawn_egg", () -> new DeferredSpawnEggItem(holder, primaryColor, secondaryColor, new Item.Properties()));
        return holder;
    }

    public static EntityType<?> type(String shortId) {
        Supplier<? extends EntityType<?>> holder = TYPES.get(shortId);
        if (holder == null) { throw new IllegalArgumentException("Unknown prismatic entity: " + shortId); }
        return holder.get();
    }

    // 一个公共入口完成实体、物品、属性与生成条件的监听器接入。
    public static void register(IEventBus bus) {
        ENTITIES.register(bus);
        EGGS.register(bus);
        bus.addListener(PrismaticEntities::attributes);
        bus.addListener(PrismaticEntities::spawnPlacements);
        bus.addListener(PrismaticEntities::creativeItems);
    }

    private static void creativeItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) { EGGS.getEntries().forEach(egg -> event.accept(egg.get())); }
    }

    // 属性直接对应设计数值，精英和首领增加合理的抗击退能力。
    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(SHARDLING.get(), PrismaticAnimal.attributes(10, 0.18, 0).build());
        event.put(PRISM_HARE.get(), PrismaticAnimal.attributes(12, 0.30, 0).build());
        event.put(FACET_RAM.get(), PrismaticAnimal.attributes(28, 0.23, 4).build());
        event.put(DUSK_MOTH.get(), PrismaticAnimal.attributes(8, 0.18, 0).build());
        event.put(GLASS_STALKER.get(), PrismaticMonster.attributes(26, 0.28, 5, 0, 0.1).build());
        event.put(NEEDLE_SPITTER.get(), PrismaticMonster.attributes(22, 0.21, 4, 1, 0.1).build());
        event.put(SHARDBACK.get(), PrismaticMonster.attributes(36, 0.20, 6, 4, 0.3).build());
        event.put(GLARE_WISP.get(), PrismaticMonster.attributes(18, 0.22, 4, 0, 0.1).build());
        event.put(FAULT_WARDEN.get(), PrismaticMonster.attributes(140, 0.24, 10, 6, 0.8).build());
        event.put(MIRROR_HUNTRESS.get(), PrismaticMonster.attributes(120, 0.28, 8, 3, 0.65).build());
        event.put(PARALLAX_REGENT.get(), PrismaticMonster.attributes(480, 0.23, 10, 7, 1.0).build());
    }

    // 八种普通物种只在折光荒原自然刷新，精英和首领保留结构或祭台生成。
    private static void spawnPlacements(RegisterSpawnPlacementsEvent event) {
        event.register(SHARDLING.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, PrismaticEntities::naturalSpawn, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(PRISM_HARE.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, PrismaticEntities::naturalSpawn, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(FACET_RAM.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, PrismaticEntities::naturalSpawn, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(DUSK_MOTH.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, PrismaticEntities::naturalSpawn, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(GLASS_STALKER.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, PrismaticEntities::naturalSpawn, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(NEEDLE_SPITTER.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, PrismaticEntities::naturalSpawn, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(SHARDBACK.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, PrismaticEntities::naturalSpawn, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(GLARE_WISP.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, PrismaticEntities::naturalSpawn, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(FAULT_WARDEN.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, PrismaticEntities::placedSpawn, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(MIRROR_HUNTRESS.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, PrismaticEntities::placedSpawn, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(PARALLAX_REGENT.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, PrismaticEntities::placedSpawn, RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }

    private static <T extends Mob> boolean naturalSpawn(EntityType<T> type, ServerLevelAccessor level, MobSpawnType reason, BlockPos pos, RandomSource random) {
        if (reason != MobSpawnType.NATURAL && reason != MobSpawnType.CHUNK_GENERATION) { return true; }
        return level.getLevel().dimension() == Level.END
                && level.getBiome(pos).is(ResourceKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath(HpEndExpansion.MODID, "prismatic_wastes")))
                && (type.getCategory() != MobCategory.MONSTER || level.getDifficulty() != Difficulty.PEACEFUL)
                && !level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty()
                && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    private static <T extends Mob> boolean placedSpawn(EntityType<T> type, ServerLevelAccessor level, MobSpawnType reason, BlockPos pos, RandomSource random) {
        return reason != MobSpawnType.NATURAL && reason != MobSpawnType.CHUNK_GENERATION;
    }
}
