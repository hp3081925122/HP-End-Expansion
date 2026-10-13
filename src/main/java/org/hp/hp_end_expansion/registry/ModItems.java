package org.hp.hp_end_expansion.registry;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import org.hp.hp_end_expansion.item.AbyssCrystalSpineItem;
import org.hp.hp_end_expansion.item.MatriarchScytheItem;
import org.hp.hp_end_expansion.item.MobDuelStickItem;
import org.hp.hp_end_expansion.item.RiftEggItem;
import org.hp.hp_end_expansion.item.DevourerWingsItem;
import org.hp.hp_end_expansion.item.StarBeaconItem;
import org.hp.hp_end_expansion.item.TidecrownTentacleItem;
import org.hp.hp_end_expansion.item.TideRemnantClawItem;
import org.hp.hp_end_expansion.item.TidebreakerHammerCoreItem;
import org.hp.hp_end_expansion.item.VoidCoreItem;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import org.hp.hp_end_expansion.Hp_end_expansion;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(net.minecraftforge.registries.ForgeRegistries.ITEMS, Hp_end_expansion.MODID);
    public static final RegistryObject<Item> MOB_DUEL_STICK = ITEMS.register(
        "mob_duel_stick", () -> new MobDuelStickItem(new Item.Properties().stacksTo(1))
    );
    public static final RegistryObject<SpawnEggItem> RIFT_MANTIS_SPAWN_EGG = ITEMS.register(
        "rift_mantis_spawn_egg",
        () -> new ForgeSpawnEggItem(ModEntities.RIFT_MANTIS, 0x30283E, 0xB26DE3, new Item.Properties())
    );
    public static final RegistryObject<SpawnEggItem> RIFT_MATRIARCH_SPAWN_EGG = ITEMS.register(
        "rift_matriarch_spawn_egg",
        () -> new ForgeSpawnEggItem(ModEntities.RIFT_MATRIARCH, 0x231D30, 0xB26DE3, new Item.Properties())
    );
    public static final RegistryObject<SpawnEggItem> VOID_RAY_SPAWN_EGG = ITEMS.register(
        "void_ray_spawn_egg",
        () -> new ForgeSpawnEggItem(ModEntities.VOID_RAY, 0x15242B, 0x4FDCCB, new Item.Properties())
    );
    public static final RegistryObject<SpawnEggItem> STAR_DEVOURER_SPAWN_EGG = ITEMS.register(
        "star_devourer_spawn_egg",
        () -> new ForgeSpawnEggItem(ModEntities.STAR_DEVOURER, 0x0D161C, 0xBDFFF0, new Item.Properties())
    );

    public static final RegistryObject<SpawnEggItem> END_MOTE_SPAWN_EGG = ITEMS.register(
        "end_mote_spawn_egg",
        () -> new ForgeSpawnEggItem(ModEntities.END_MOTE, 0x1A0A2E, 0x9FE8FF, new Item.Properties())
    );
    // 潮灯须鲛掉落：深渊晶刺（遗物）
    public static final RegistryObject<AbyssCrystalSpineItem> ABYSS_CRYSTAL_SPINE = ITEMS.register(
        "abyss_crystal_spine", () -> new AbyssCrystalSpineItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE))
    );
    public static final RegistryObject<SpawnEggItem> TIDELAMP_WYRM_SPAWN_EGG = ITEMS.register(
        "tidelamp_wyrm_spawn_egg",
        () -> new ForgeSpawnEggItem(ModEntities.TIDELAMP_WYRM, 0x243348, 0x66DAE7, new Item.Properties())
    );
    public static final RegistryObject<SpawnEggItem> TIDECROWN_ANEMONE_SPAWN_EGG = ITEMS.register(
        "tidecrown_anemone_spawn_egg",
        () -> new ForgeSpawnEggItem(ModEntities.TIDECROWN_ANEMONE, 0x314F56, 0x99DECB, new Item.Properties())
    );
    public static final RegistryObject<TidecrownTentacleItem> TIDECROWN_TENTACLE = ITEMS.register(
        "tidecrown_tentacle",
        () -> new TidecrownTentacleItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON))
    );
    public static final RegistryObject<SpawnEggItem> TIDEBREAKER_SHRIMP_SPAWN_EGG = ITEMS.register(
        "tidebreaker_shrimp_spawn_egg",
        () -> new ForgeSpawnEggItem(ModEntities.TIDEBREAKER_SHRIMP, 0x2C525C, 0xB0B897, new Item.Properties())
    );
    public static final RegistryObject<SpawnEggItem> TIDEBREAKER_WARLORD_SPAWN_EGG = ITEMS.register(
        "tidebreaker_warlord_spawn_egg",
        () -> new ForgeSpawnEggItem(ModEntities.TIDEBREAKER_WARLORD, 0x1F5866, 0x8FF0D8, new Item.Properties())
    );
    public static final RegistryObject<Item> TIDEBREAKER_GAUNTLET = ITEMS.register(
        "tidebreaker_gauntlet",
        () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON))
    );
    // 碎潮锤将专属掉落
    public static final RegistryObject<TidebreakerHammerCoreItem> TIDEBREAKER_HAMMER_CORE = ITEMS.register(
        "tidebreaker_hammer_core",
        () -> new TidebreakerHammerCoreItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE))
    );
    public static final RegistryObject<SpawnEggItem> LANTERN_JELLYFISH_SPAWN_EGG = ITEMS.register(
        "lantern_jellyfish_spawn_egg",
        () -> new ForgeSpawnEggItem(ModEntities.LANTERN_JELLYFISH, 0x17495A, 0xBDFFF0, new Item.Properties())
    );
    public static final RegistryObject<SpawnEggItem> GLIMMERFIN_SPAWN_EGG = ITEMS.register(
        "glimmerfin_spawn_egg",
        () -> new ForgeSpawnEggItem(ModEntities.GLIMMERFIN, 0x1F6E87, 0xB7FFF0, new Item.Properties())
    );
    public static final RegistryObject<SpawnEggItem> TIDE_REMNANT_HERMIT_CRAB_SPAWN_EGG = ITEMS.register(
        "tide_remnant_hermit_crab_spawn_egg",
        () -> new ForgeSpawnEggItem(ModEntities.TIDE_REMNANT_HERMIT_CRAB, 0xD7C89D, 0x247C83, new Item.Properties())
    );
    // 潮骸寄居蟹掉落：潮骸蟹钳
    public static final RegistryObject<TideRemnantClawItem> TIDE_REMNANT_CLAW = ITEMS.register(
        "tide_remnant_claw",
        () -> new TideRemnantClawItem(new Item.Properties().stacksTo(1))
    );
    // 末晶萤掉落：末晶尘
    public static final RegistryObject<Item> END_DUST = ITEMS.register(
        "end_dust",
        () -> new Item(new Item.Properties().rarity(Rarity.COMMON))
    );

    // 裂空鳐掉落：虚空核心
    public static final RegistryObject<VoidCoreItem> VOID_CORE = ITEMS.register(
        "void_core",
        () -> new VoidCoreItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE).fireResistant())
    );
    // 裂空鳐掉落：鳐翼膜
    public static final RegistryObject<Item> RAY_MEMBRANE = ITEMS.register(
        "ray_membrane",
        () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON))
    );

    // 裂隙螳螂掉落：裂隙甲壳
    public static final RegistryObject<Item> RIFT_CARAPACE = ITEMS.register(
        "rift_carapace",
        () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON))
    );
    // 裂隙螳螂掉落：裂隙镰刃
    public static final RegistryObject<Item> RIFT_SICKLE = ITEMS.register(
        "rift_sickle",
        () -> new Item(new Item.Properties().rarity(Rarity.RARE).fireResistant())
    );

    // 螳后召唤物：裂隙之卵
    public static final RegistryObject<Item> RIFT_EGG = ITEMS.register(
        "rift_egg",
        () -> new RiftEggItem(new Item.Properties().rarity(Rarity.EPIC).stacksTo(1).fireResistant())
    );
    // 螳后掉落：螳后之镰
    public static final RegistryObject<Item> MATRIARCH_SCYTHE = ITEMS.register(
        "matriarch_scythe",
        () -> new MatriarchScytheItem(Tiers.NETHERITE, new Item.Properties().rarity(Rarity.EPIC).fireResistant())
    );

    // 鳐王召唤物：星引信标
    public static final RegistryObject<Item> STAR_BEACON = ITEMS.register(
        "star_beacon",
        () -> new StarBeaconItem(new Item.Properties().rarity(Rarity.EPIC).stacksTo(1).fireResistant())
    );
    // 鳐王掉落：鳐王之翼
    public static final RegistryObject<Item> DEVOURER_WINGS = ITEMS.register(
        "devourer_wings",
        () -> new DevourerWingsItem(new Item.Properties().rarity(Rarity.EPIC).fireResistant())
    );

    private ModItems() {
    }
}
