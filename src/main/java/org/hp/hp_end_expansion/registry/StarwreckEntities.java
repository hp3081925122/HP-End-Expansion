package org.hp.hp_end_expansion.registry;

import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;
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
import org.hp.hp_end_expansion.item.EmberCrownItem;
import org.hp.hp_end_expansion.item.StarCoreChestplateItem;
import org.hp.hp_end_expansion.item.StarfallOmenItem;
import org.hp.hp_end_expansion.item.HolyStarItem;
import org.hp.hp_end_expansion.item.StarcallPendantItem;
import org.hp.hp_end_expansion.item.SkyEyeItem;
import org.hp.hp_end_expansion.item.SkyrenderHornItem;
import org.hp.hp_end_expansion.item.UnstableRemnantStarItem;
import org.hp.hp_end_expansion.entity.starwreck.EmberBeetleEntity;
import org.hp.hp_end_expansion.entity.starwreck.EmberGroundEntity;
import org.hp.hp_end_expansion.entity.starwreck.EmberMothEntity;
import org.hp.hp_end_expansion.entity.starwreck.FallingStarEntity;
import org.hp.hp_end_expansion.entity.starwreck.MeteorTortoiseEntity;
import org.hp.hp_end_expansion.entity.starwreck.StarBoltEntity;
import org.hp.hp_end_expansion.entity.starwreck.SkyMeteorEntity;
import org.hp.hp_end_expansion.entity.starwreck.StarBearerEntity;
import org.hp.hp_end_expansion.entity.starwreck.BearerVfxEntity;
import org.hp.hp_end_expansion.entity.starwreck.StarShardEntity;
import org.hp.hp_end_expansion.entity.starwreck.StarCallerEntity;
import org.hp.hp_end_expansion.entity.starwreck.StarFlailEntity;
import org.hp.hp_end_expansion.entity.starwreck.StarMartyrEntity;
import org.hp.hp_end_expansion.entity.starwreck.StarChaserEntity;
import org.hp.hp_end_expansion.entity.starwreck.StarImpactEntity;
import org.hp.hp_end_expansion.entity.starwreck.StarMarkEntity;
import org.hp.hp_end_expansion.entity.starwreck.StarRain;
import org.hp.hp_end_expansion.entity.starwreck.StarRiftEntity;
import org.hp.hp_end_expansion.entity.starwreck.SkyrenderEntity;
import org.hp.hp_end_expansion.entity.starwreck.SkyShardEntity;
import org.hp.hp_end_expansion.item.SkyEyeOmenItem;
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
    public static final DeferredHolder<EntityType<?>, EntityType<StarChaserEntity>> STAR_CHASER = TYPES.register("star_chaser",
        () -> EntityType.Builder.of(StarChaserEntity::new, MobCategory.MONSTER).sized(1.8F, 2.1F).fireImmune()
            .clientTrackingRange(10).build("hp_end_expansion:star_chaser"));
    public static final DeferredHolder<EntityType<?>, EntityType<StarCallerEntity>> STAR_CALLER = TYPES.register("star_caller",
        () -> EntityType.Builder.of(StarCallerEntity::new, MobCategory.MONSTER).sized(0.7F, 2.1F).clientTrackingRange(10).build("hp_end_expansion:star_caller"));
    public static final DeferredHolder<EntityType<?>, EntityType<StarMartyrEntity>> STAR_MARTYR = TYPES.register("star_martyr",
        () -> EntityType.Builder.of(StarMartyrEntity::new, MobCategory.MONSTER).sized(0.6F, 1.95F).clientTrackingRange(10).build("hp_end_expansion:star_martyr"));
    public static final DeferredHolder<EntityType<?>, EntityType<StarBearerEntity>> STAR_BEARER = TYPES.register("star_bearer",
        () -> EntityType.Builder.of(StarBearerEntity::new, MobCategory.MONSTER).sized(1.4F, 2.9F).eyeHeight(2.2F).clientTrackingRange(10).build("hp_end_expansion:star_bearer"));
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
    public static final DeferredHolder<EntityType<?>, EntityType<StarMarkEntity>> STAR_MARK = TYPES.register("star_mark",
        () -> EntityType.Builder.<StarMarkEntity>of(StarMarkEntity::new, MobCategory.MISC).sized(0.5F, 0.5F).noSummon().noSave().fireImmune()
            .clientTrackingRange(10).updateInterval(20).build("hp_end_expansion:star_mark"));
    public static final DeferredHolder<EntityType<?>, EntityType<BearerVfxEntity>> BEARER_VFX = TYPES.register("bearer_vfx",
        () -> EntityType.Builder.<BearerVfxEntity>of(BearerVfxEntity::new, MobCategory.MISC).sized(0.5F, 0.5F).noSummon().noSave().fireImmune()
            .clientTrackingRange(10).updateInterval(20).build("hp_end_expansion:bearer_vfx"));
    // 群星之弓的星晶矢：尺寸和同步频率照原版箭
    public static final DeferredHolder<EntityType<?>, EntityType<StarBoltEntity>> STAR_BOLT = TYPES.register("star_bolt",
        () -> EntityType.Builder.<StarBoltEntity>of(StarBoltEntity::new, MobCategory.MISC).sized(0.5F, 0.5F).eyeHeight(0.13F)
            .clientTrackingRange(4).updateInterval(20).build("hp_end_expansion:star_bolt"));
    public static final DeferredHolder<EntityType<?>, EntityType<StarShardEntity>> STAR_SHARD = TYPES.register("star_shard",
        () -> EntityType.Builder.<StarShardEntity>of(StarShardEntity::new, MobCategory.MISC).sized(0.4F, 0.4F).noSummon().noSave().fireImmune()
            .clientTrackingRange(10).updateInterval(1).build("hp_end_expansion:star_shard"));
    public static final DeferredHolder<EntityType<?>, EntityType<StarFlailEntity>> STAR_FLAIL = TYPES.register("star_flail",
        () -> EntityType.Builder.<StarFlailEntity>of(StarFlailEntity::new, MobCategory.MISC).sized(0.5F, 0.5F).noSummon()
            .clientTrackingRange(10).updateInterval(1).build("hp_end_expansion:star_flail"));
    // 裂天之主：四足星骸巨兽，碰撞箱只包身躯，头另有部件判定；天幕碎片不存档
    public static final DeferredHolder<EntityType<?>, EntityType<SkyrenderEntity>> SKYRENDER = TYPES.register("skyrender",
        () -> EntityType.Builder.of(SkyrenderEntity::new, MobCategory.MONSTER).sized(3.4F, 3.8F).eyeHeight(2.4F).fireImmune()
            .clientTrackingRange(10).updateInterval(2).build("hp_end_expansion:skyrender"));
    public static final DeferredHolder<EntityType<?>, EntityType<SkyShardEntity>> SKY_SHARD = TYPES.register("sky_shard",
        () -> EntityType.Builder.<SkyShardEntity>of(SkyShardEntity::new, MobCategory.MISC).sized(1.5F, 3.0F).noSummon().noSave().fireImmune()
            .clientTrackingRange(12).updateInterval(20).build("hp_end_expansion:sky_shard"));
    public static final DeferredHolder<EntityType<?>, EntityType<SkyMeteorEntity>> SKY_METEOR = TYPES.register("sky_meteor",
        () -> EntityType.Builder.<SkyMeteorEntity>of(SkyMeteorEntity::new, MobCategory.MISC).sized(1F, 1F).noSummon().noSave().fireImmune()
            .clientTrackingRange(16).updateInterval(20).build("hp_end_expansion:sky_meteor"));

    public static final DeferredItem<Item> EMBER_SCALE_DUST = ModItems.ITEMS.register("ember_scale_dust", () -> new Item(new Item.Properties()));
    // 燃料时长见 data/neoforge/data_maps/item/furnace_fuels.json
    public static final DeferredItem<Item> EMBER = ModItems.ITEMS.register("ember", () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> STARFALL_OMEN = ModItems.ITEMS.register("starfall_omen",
        () -> new StarfallOmenItem(new Item.Properties().rarity(Rarity.UNCOMMON).stacksTo(16)));
    // 逐星兽必掉，胸核冷却后的残块
    public static final DeferredItem<Item> STAR_CORE_EMBER = ModItems.ITEMS.register("star_core_ember",
        () -> new Item(new Item.Properties().rarity(Rarity.RARE).fireResistant()));
    public static final DeferredItem<StarcallPendantItem> STARCALL_PENDANT = ModItems.ITEMS.register("starcall_pendant",
        () -> new StarcallPendantItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<EmberCrownItem> EMBER_CROWN = ModItems.ITEMS.register("ember_crown",
        () -> new EmberCrownItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<UnstableRemnantStarItem> UNSTABLE_REMNANT_STAR = ModItems.ITEMS.register("unstable_remnant_star",
        () -> new UnstableRemnantStarItem(new Item.Properties().rarity(Rarity.RARE).stacksTo(1).fireResistant()));
    public static final DeferredItem<HolyStarItem> HOLY_STAR = ModItems.ITEMS.register("holy_star",
        () -> new HolyStarItem(new Item.Properties().rarity(Rarity.EPIC).stacksTo(1).fireResistant()));
    public static final DeferredRegister<ArmorMaterial> ARMOR_MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, Hp_end_expansion.MODID);
    // 数值对齐下界合金胸甲；贴图层只在 GeckoLib 渲染失效时才会用到
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> STAR_CORE = ARMOR_MATERIALS.register("star_core", () -> new ArmorMaterial(
        Map.of(ArmorItem.Type.HELMET, 3, ArmorItem.Type.CHESTPLATE, 8, ArmorItem.Type.LEGGINGS, 6, ArmorItem.Type.BOOTS, 3, ArmorItem.Type.BODY, 11),
        15, SoundEvents.ARMOR_EQUIP_NETHERITE, () -> Ingredient.of(ModStarwreck.STAR_CRYSTAL_SHARD.get()),
        List.of(new ArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath(Hp_end_expansion.MODID, "star_core"))), 3.0F, 0.1F));
    public static final DeferredItem<StarCoreChestplateItem> STAR_CORE_CHESTPLATE = ModItems.ITEMS.register("star_core_chestplate",
        () -> new StarCoreChestplateItem(STAR_CORE, new Item.Properties().rarity(Rarity.EPIC).fireResistant()
            .durability(ArmorItem.Type.CHESTPLATE.getDurability(37))));
    public static final DeferredItem<SpawnEggItem> MOTH_EGG = ModItems.ITEMS.register("ember_moth_spawn_egg", () -> new SpawnEggItem(EMBER_MOTH.get(), 0x3D2B26, 0xE08C2A, new Item.Properties()));
    public static final DeferredItem<SpawnEggItem> BEETLE_EGG = ModItems.ITEMS.register("ember_beetle_spawn_egg", () -> new SpawnEggItem(EMBER_BEETLE.get(), 0x2B1E1E, 0xB0581C, new Item.Properties()));
    public static final DeferredItem<SpawnEggItem> TORTOISE_EGG = ModItems.ITEMS.register("meteor_tortoise_spawn_egg", () -> new SpawnEggItem(METEOR_TORTOISE.get(), 0x52392D, 0xE8A23A, new Item.Properties()));
    public static final DeferredItem<SpawnEggItem> CHASER_EGG = ModItems.ITEMS.register("star_chaser_spawn_egg", () -> new SpawnEggItem(STAR_CHASER.get(), 0x2B1E1E, 0xFFCB5E, new Item.Properties()));
    public static final DeferredItem<SpawnEggItem> CALLER_EGG = ModItems.ITEMS.register("star_caller_spawn_egg", () -> new SpawnEggItem(STAR_CALLER.get(), 0x4C403B, 0xF7CE62, new Item.Properties()));
    public static final DeferredItem<SpawnEggItem> MARTYR_EGG = ModItems.ITEMS.register("star_martyr_spawn_egg", () -> new SpawnEggItem(STAR_MARTYR.get(), 0x615A70, 0xF7CE62, new Item.Properties()));
    public static final DeferredItem<SpawnEggItem> BEARER_EGG = ModItems.ITEMS.register("star_bearer_spawn_egg", () -> new SpawnEggItem(STAR_BEARER.get(), 0x3E3A48, 0xE8A23A, new Item.Properties()));
    public static final DeferredItem<Item> SKY_EYE_OMEN = ModItems.ITEMS.register("sky_eye_omen",
        () -> new SkyEyeOmenItem(new Item.Properties().rarity(Rarity.EPIC).stacksTo(1).fireResistant()));
    public static final DeferredItem<Item> SKY_FRAGMENT = ModItems.ITEMS.register("sky_fragment",
        () -> new Item(new Item.Properties().rarity(Rarity.EPIC).fireResistant()));
    public static final DeferredItem<SkyEyeItem> SKY_EYE = ModItems.ITEMS.register("sky_eye",
        () -> new SkyEyeItem(new Item.Properties().rarity(Rarity.EPIC).stacksTo(1).fireResistant()));
    public static final DeferredItem<SkyrenderHornItem> SKYRENDER_HORN = ModItems.ITEMS.register("skyrender_horn",
        () -> new SkyrenderHornItem(new Item.Properties().rarity(Rarity.EPIC).stacksTo(1).fireResistant()));
    public static final DeferredItem<SpawnEggItem> SKYRENDER_EGG = ModItems.ITEMS.register("skyrender_spawn_egg", () -> new SpawnEggItem(SKYRENDER.get(), 0x0B1024, 0xFFB46A, new Item.Properties()));
    // 创造物品栏顺序：掉落物在前，刷怪蛋在后
    public static final List<DeferredItem<? extends Item>> ITEMS = List.of(EMBER_SCALE_DUST, EMBER, STARFALL_OMEN, STAR_CORE_EMBER, STARCALL_PENDANT, EMBER_CROWN, UNSTABLE_REMNANT_STAR, HOLY_STAR, STAR_CORE_CHESTPLATE, SKY_EYE_OMEN, SKY_FRAGMENT, SKY_EYE, SKYRENDER_HORN, MOTH_EGG, BEETLE_EGG, TORTOISE_EGG, CHASER_EGG, CALLER_EGG, MARTYR_EGG, BEARER_EGG, SKYRENDER_EGG);

    public static void register(IEventBus bus) {
        TYPES.register(bus);
        ARMOR_MATERIALS.register(bus);
        bus.addListener(StarwreckEntities::attributes);
        bus.addListener(StarwreckEntities::placements);
        NeoForge.EVENT_BUS.addListener(StarRain::tick);
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(EMBER_MOTH.get(), EmberMothEntity.createAttributes().build());
        event.put(EMBER_BEETLE.get(), EmberBeetleEntity.createAttributes().build());
        event.put(METEOR_TORTOISE.get(), MeteorTortoiseEntity.createAttributes().build());
        event.put(STAR_CHASER.get(), StarChaserEntity.createAttributes().build());
        event.put(STAR_CALLER.get(), StarCallerEntity.createAttributes().build());
        event.put(STAR_MARTYR.get(), StarMartyrEntity.createAttributes().build());
        event.put(STAR_BEARER.get(), StarBearerEntity.createAttributes().build());
        event.put(SKYRENDER.get(), SkyrenderEntity.createAttributes().build());
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
        event.register(STAR_CALLER.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            (type, level, reason, pos, random) -> Monster.checkMonsterSpawnRules(type, level, reason, pos, random) && naturalOn(level, reason, pos, false),
            RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(STAR_MARTYR.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            (type, level, reason, pos, random) -> Monster.checkMonsterSpawnRules(type, level, reason, pos, random) && naturalOn(level, reason, pos, false),
            RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(STAR_BEARER.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            StarwreckEntities::bearerSpot, RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }

    // 64 格内已有负星者或存活的逐星兽时不再生成；刷怪蛋和指令不走这条。
    // 区块生成时直接不刷：那是在世界生成线程上跑的，查 ServerLevel 的实体不安全，精英怪也不该随地形预先摆好
    private static boolean bearerSpot(EntityType<StarBearerEntity> type, ServerLevelAccessor level, MobSpawnType reason, BlockPos pos, net.minecraft.util.RandomSource random) {
        if (reason == MobSpawnType.CHUNK_GENERATION) return false;
        if (!Monster.checkMonsterSpawnRules(type, level, reason, pos, random) || !naturalOn(level, reason, pos, false)) return false;
        if (reason != MobSpawnType.NATURAL) return true;
        AABB area = new AABB(pos).inflate(64);
        return level.getLevel().getEntitiesOfClass(StarBearerEntity.class, area, LivingEntity::isAlive).isEmpty()
            && level.getLevel().getEntitiesOfClass(StarChaserEntity.class, area, LivingEntity::isAlive).isEmpty();
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
