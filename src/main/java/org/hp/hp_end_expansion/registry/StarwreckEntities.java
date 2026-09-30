package org.hp.hp_end_expansion.registry;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.hp.hp_end_expansion.Hp_end_expansion;
import org.hp.hp_end_expansion.item.StarfallOmenItem;
import org.hp.hp_end_expansion.entity.starwreck.EmberBeetleEntity;
import org.hp.hp_end_expansion.entity.starwreck.EmberGroundEntity;
import org.hp.hp_end_expansion.entity.starwreck.EmberMothEntity;
import org.hp.hp_end_expansion.entity.starwreck.FallingStarEntity;
import org.hp.hp_end_expansion.entity.starwreck.MeteorTortoiseEntity;
import org.hp.hp_end_expansion.entity.starwreck.StarImpactEntity;
import org.hp.hp_end_expansion.entity.starwreck.StarRain;
import org.hp.hp_end_expansion.entity.starwreck.StarRiftEntity;
import org.hp.hp_end_expansion.worldgen.StarwreckWorldgen;

// 星骸荒原生物：实体类型、属性、刷怪蛋、掉落物、生成位置规则（设计文档第 9 节）。生成权重写在群系 JSON 里
public final class StarwreckEntities {
    public static final DeferredRegister<EntityType<?>> TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, Hp_end_expansion.MODID);
    public static final DeferredHolder<EntityType<?>, EntityType<EmberMothEntity>> EMBER_MOTH = TYPES.register("ember_moth",
        () -> EntityType.Builder.of(EmberMothEntity::new, MobCategory.AMBIENT).sized(0.9F, 0.5F).clientTrackingRange(8).build("hp_end_expansion:ember_moth"));
    public static final DeferredHolder<EntityType<?>, EntityType<EmberBeetleEntity>> EMBER_BEETLE = TYPES.register("ember_beetle",
        () -> EntityType.Builder.of(EmberBeetleEntity::new, MobCategory.MONSTER).sized(0.7F, 0.45F).clientTrackingRange(8).build("hp_end_expansion:ember_beetle"));
    public static final DeferredHolder<EntityType<?>, EntityType<MeteorTortoiseEntity>> METEOR_TORTOISE = TYPES.register("meteor_tortoise",
        () -> EntityType.Builder.of(MeteorTortoiseEntity::new, MobCategory.CREATURE).sized(1.2F, 0.9F).clientTrackingRange(10).build("hp_end_expansion:meteor_tortoise"));
    public static final DeferredHolder<EntityType<?>, EntityType<StarRiftEntity>> STAR_RIFT = TYPES.register("star_rift",
        () -> EntityType.Builder.<StarRiftEntity>of(StarRiftEntity::new, MobCategory.MISC).sized(0.5F, 0.5F).noSummon().fireImmune()
            .clientTrackingRange(16).updateInterval(1).build("hp_end_expansion:star_rift"));
    public static final DeferredHolder<EntityType<?>, EntityType<FallingStarEntity>> FALLING_STAR = TYPES.register("falling_star",
        () -> EntityType.Builder.<FallingStarEntity>of(FallingStarEntity::new, MobCategory.MISC).sized(1.4F, 1.4F).noSummon().fireImmune()
            .clientTrackingRange(16).updateInterval(1).build("hp_end_expansion:falling_star"));
    public static final DeferredHolder<EntityType<?>, EntityType<StarImpactEntity>> STAR_IMPACT = TYPES.register("star_impact",
        () -> EntityType.Builder.<StarImpactEntity>of(StarImpactEntity::new, MobCategory.MISC).sized(0.5F, 0.5F).noSummon().noSave().fireImmune()
            .clientTrackingRange(16).updateInterval(20).build("hp_end_expansion:star_impact"));
    public static final DeferredHolder<EntityType<?>, EntityType<EmberGroundEntity>> EMBER_GROUND = TYPES.register("ember_ground",
        () -> EntityType.Builder.<EmberGroundEntity>of(EmberGroundEntity::new, MobCategory.MISC).sized(2.0F, 0.25F).noSummon().fireImmune()
            .clientTrackingRange(8).updateInterval(20).build("hp_end_expansion:ember_ground"));

    public static final DeferredItem<Item> EMBER_SCALE_DUST = ModItems.ITEMS.register("ember_scale_dust", () -> new Item(new Item.Properties()));
    // 燃料时长见 data/neoforge/data_maps/item/furnace_fuels.json
    public static final DeferredItem<Item> EMBER = ModItems.ITEMS.register("ember", () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> STARFALL_OMEN = ModItems.ITEMS.register("starfall_omen",
        () -> new StarfallOmenItem(new Item.Properties().rarity(Rarity.UNCOMMON).stacksTo(16)));
    public static final DeferredItem<SpawnEggItem> MOTH_EGG = ModItems.ITEMS.register("ember_moth_spawn_egg", () -> new SpawnEggItem(EMBER_MOTH.get(), 0x3D2B26, 0xE08C2A, new Item.Properties()));
    public static final DeferredItem<SpawnEggItem> BEETLE_EGG = ModItems.ITEMS.register("ember_beetle_spawn_egg", () -> new SpawnEggItem(EMBER_BEETLE.get(), 0x2B1E1E, 0xB0581C, new Item.Properties()));
    public static final DeferredItem<SpawnEggItem> TORTOISE_EGG = ModItems.ITEMS.register("meteor_tortoise_spawn_egg", () -> new SpawnEggItem(METEOR_TORTOISE.get(), 0x52392D, 0xE8A23A, new Item.Properties()));
    // 创造物品栏顺序：掉落物在前，刷怪蛋在后
    public static final List<DeferredItem<? extends Item>> ITEMS = List.of(EMBER_SCALE_DUST, EMBER, STARFALL_OMEN, MOTH_EGG, BEETLE_EGG, TORTOISE_EGG);

    public static void register(IEventBus bus) {
        TYPES.register(bus);
        bus.addListener(StarwreckEntities::attributes);
        bus.addListener(StarwreckEntities::placements);
        NeoForge.EVENT_BUS.addListener(StarRain::tick);
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(EMBER_MOTH.get(), EmberMothEntity.createAttributes().build());
        event.put(EMBER_BEETLE.get(), EmberBeetleEntity.createAttributes().build());
        event.put(METEOR_TORTOISE.get(), MeteorTortoiseEntity.createAttributes().build());
    }

    private static void placements(RegisterSpawnPlacementsEvent event) {
        event.register(EMBER_MOTH.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            (type, level, reason, pos, random) -> Mob.checkMobSpawnRules(type, level, reason, pos, random) && naturalOn(level, reason, pos, false),
            RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(EMBER_BEETLE.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            (type, level, reason, pos, random) -> Monster.checkMonsterSpawnRules(type, level, reason, pos, random) && naturalOn(level, reason, pos, true),
            RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(METEOR_TORTOISE.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            (type, level, reason, pos, random) -> Mob.checkMobSpawnRules(type, level, reason, pos, random) && naturalOn(level, reason, pos, false),
            RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }

    // 自然生成和区块生成时只在星骸荒原的星骸系方块上出现；刷怪蛋、刷怪笼和指令不受限制。甲虫只站在星苔或裸星骸岩上
    private static boolean naturalOn(ServerLevelAccessor level, MobSpawnType reason, BlockPos pos, boolean mossOnly) {
        if (reason != MobSpawnType.NATURAL && reason != MobSpawnType.CHUNK_GENERATION) return true;
        if (!level.getBiome(pos).is(StarwreckWorldgen.BIOME)) return false;
        BlockState below = level.getBlockState(pos.below());
        if (below.is(ModStarwreck.STAR_MOSS.get()) || below.is(ModStarwreck.STARWRECK_STONE.get())) return true;
        return !mossOnly && (below.is(ModStarwreck.METEOR_ASH.get()) || below.is(ModStarwreck.EMBER_STARWRECK_STONE.get()));
    }
}
