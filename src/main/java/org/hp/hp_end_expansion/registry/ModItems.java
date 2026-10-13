package org.hp.hp_end_expansion.registry;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.ToolMaterial;
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
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.hp.hp_end_expansion.Hp_end_expansion;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Hp_end_expansion.MODID);
    public static final DeferredItem<Item> MOB_DUEL_STICK = ITEMS.register(
        "mob_duel_stick", id -> new MobDuelStickItem(new Item.Properties().setId(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ITEM,id)).stacksTo(1))
    );
    public static final DeferredItem<SpawnEggItem> RIFT_MANTIS_SPAWN_EGG = ITEMS.register(
        "rift_mantis_spawn_egg",
        id -> new SpawnEggItem(new Item.Properties().setId(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ITEM,id)).spawnEgg(ModEntities.RIFT_MANTIS.get()))
    );
    public static final DeferredItem<SpawnEggItem> RIFT_MATRIARCH_SPAWN_EGG = ITEMS.register(
        "rift_matriarch_spawn_egg",
        id -> new SpawnEggItem(new Item.Properties().setId(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ITEM,id)).spawnEgg(ModEntities.RIFT_MATRIARCH.get()))
    );
    public static final DeferredItem<SpawnEggItem> VOID_RAY_SPAWN_EGG = ITEMS.register(
        "void_ray_spawn_egg",
        id -> new SpawnEggItem(new Item.Properties().setId(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ITEM,id)).spawnEgg(ModEntities.VOID_RAY.get()))
    );
    public static final DeferredItem<SpawnEggItem> STAR_DEVOURER_SPAWN_EGG = ITEMS.register(
        "star_devourer_spawn_egg",
        id -> new SpawnEggItem(new Item.Properties().setId(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ITEM,id)).spawnEgg(ModEntities.STAR_DEVOURER.get()))
    );

    public static final DeferredItem<SpawnEggItem> END_MOTE_SPAWN_EGG = ITEMS.register(
        "end_mote_spawn_egg",
        id -> new SpawnEggItem(new Item.Properties().setId(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ITEM,id)).spawnEgg(ModEntities.END_MOTE.get()))
    );
    // 潮灯须鲛掉落：深渊晶刺（遗物）
    public static final DeferredItem<AbyssCrystalSpineItem> ABYSS_CRYSTAL_SPINE = ITEMS.register(
        "abyss_crystal_spine", id -> new AbyssCrystalSpineItem(new Item.Properties().setId(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ITEM,id)).stacksTo(1).rarity(Rarity.RARE))
    );
    public static final DeferredItem<SpawnEggItem> TIDELAMP_WYRM_SPAWN_EGG = ITEMS.register(
        "tidelamp_wyrm_spawn_egg",
        id -> new SpawnEggItem(new Item.Properties().setId(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ITEM,id)).spawnEgg(ModEntities.TIDELAMP_WYRM.get()))
    );
    public static final DeferredItem<SpawnEggItem> TIDECROWN_ANEMONE_SPAWN_EGG = ITEMS.register(
        "tidecrown_anemone_spawn_egg",
        id -> new SpawnEggItem(new Item.Properties().setId(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ITEM,id)).spawnEgg(ModEntities.TIDECROWN_ANEMONE.get()))
    );
    public static final DeferredItem<TidecrownTentacleItem> TIDECROWN_TENTACLE = ITEMS.register(
        "tidecrown_tentacle",
        id -> new TidecrownTentacleItem(new Item.Properties().setId(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ITEM,id)).stacksTo(1).rarity(Rarity.UNCOMMON))
    );
    public static final DeferredItem<SpawnEggItem> TIDEBREAKER_SHRIMP_SPAWN_EGG = ITEMS.register(
        "tidebreaker_shrimp_spawn_egg",
        id -> new SpawnEggItem(new Item.Properties().setId(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ITEM,id)).spawnEgg(ModEntities.TIDEBREAKER_SHRIMP.get()))
    );
    public static final DeferredItem<SpawnEggItem> TIDEBREAKER_WARLORD_SPAWN_EGG = ITEMS.register(
        "tidebreaker_warlord_spawn_egg",
        id -> new SpawnEggItem(new Item.Properties().setId(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ITEM,id)).spawnEgg(ModEntities.TIDEBREAKER_WARLORD.get()))
    );
    public static final DeferredItem<Item> TIDEBREAKER_GAUNTLET = ITEMS.register(
        "tidebreaker_gauntlet",
        id -> new Item(new Item.Properties().setId(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ITEM,id)).rarity(Rarity.UNCOMMON))
    );
    // 碎潮锤将专属掉落
    public static final DeferredItem<TidebreakerHammerCoreItem> TIDEBREAKER_HAMMER_CORE = ITEMS.register(
        "tidebreaker_hammer_core",
        id -> new TidebreakerHammerCoreItem(new Item.Properties().setId(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ITEM,id)).stacksTo(1).rarity(Rarity.RARE))
    );
    public static final DeferredItem<SpawnEggItem> LANTERN_JELLYFISH_SPAWN_EGG = ITEMS.register(
        "lantern_jellyfish_spawn_egg",
        id -> new SpawnEggItem(new Item.Properties().setId(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ITEM,id)).spawnEgg(ModEntities.LANTERN_JELLYFISH.get()))
    );
    public static final DeferredItem<SpawnEggItem> GLIMMERFIN_SPAWN_EGG = ITEMS.register(
        "glimmerfin_spawn_egg",
        id -> new SpawnEggItem(new Item.Properties().setId(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ITEM,id)).spawnEgg(ModEntities.GLIMMERFIN.get()))
    );
    public static final DeferredItem<SpawnEggItem> TIDE_REMNANT_HERMIT_CRAB_SPAWN_EGG = ITEMS.register(
        "tide_remnant_hermit_crab_spawn_egg",
        id -> new SpawnEggItem(new Item.Properties().setId(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ITEM,id)).spawnEgg(ModEntities.TIDE_REMNANT_HERMIT_CRAB.get()))
    );
    // 潮骸寄居蟹掉落：潮骸蟹钳
    public static final DeferredItem<TideRemnantClawItem> TIDE_REMNANT_CLAW = ITEMS.register(
        "tide_remnant_claw",
        id -> new TideRemnantClawItem(new Item.Properties().setId(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ITEM,id)).stacksTo(1))
    );
    // 末晶萤掉落：末晶尘
    public static final DeferredItem<Item> END_DUST = ITEMS.register(
        "end_dust",
        id -> new Item(new Item.Properties().setId(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ITEM,id)).rarity(Rarity.COMMON))
    );

    // 裂空鳐掉落：虚空核心
    public static final DeferredItem<VoidCoreItem> VOID_CORE = ITEMS.register(
        "void_core",
        id -> new VoidCoreItem(new Item.Properties().setId(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ITEM,id)).stacksTo(1).rarity(Rarity.RARE).fireResistant())
    );
    // 裂空鳐掉落：鳐翼膜
    public static final DeferredItem<Item> RAY_MEMBRANE = ITEMS.register(
        "ray_membrane",
        id -> new Item(new Item.Properties().setId(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ITEM,id)).rarity(Rarity.UNCOMMON))
    );

    // 裂隙螳螂掉落：裂隙甲壳
    public static final DeferredItem<Item> RIFT_CARAPACE = ITEMS.register(
        "rift_carapace",
        id -> new Item(new Item.Properties().setId(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ITEM,id)).rarity(Rarity.UNCOMMON))
    );
    // 裂隙螳螂掉落：裂隙镰刃
    public static final DeferredItem<Item> RIFT_SICKLE = ITEMS.register(
        "rift_sickle",
        id -> new Item(new Item.Properties().setId(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ITEM,id)).rarity(Rarity.RARE).fireResistant())
    );

    // 螳后召唤物：裂隙之卵
    public static final DeferredItem<Item> RIFT_EGG = ITEMS.register(
        "rift_egg",
        id -> new RiftEggItem(new Item.Properties().setId(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ITEM,id)).rarity(Rarity.EPIC).stacksTo(1).fireResistant())
    );
    // 螳后掉落：螳后之镰
    public static final DeferredItem<Item> MATRIARCH_SCYTHE = ITEMS.register(
        "matriarch_scythe",
        id -> new MatriarchScytheItem(ToolMaterial.NETHERITE, new Item.Properties().setId(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ITEM,id)).rarity(Rarity.EPIC).fireResistant()
            .sword(ToolMaterial.NETHERITE, 5, -2.6F))
    );

    // 鳐王召唤物：星引信标
    public static final DeferredItem<Item> STAR_BEACON = ITEMS.register(
        "star_beacon",
        id -> new StarBeaconItem(new Item.Properties().setId(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ITEM,id)).rarity(Rarity.EPIC).stacksTo(1).fireResistant())
    );
    // 鳐王掉落：鳐王之翼
    public static final DeferredItem<Item> DEVOURER_WINGS = ITEMS.register(
        "devourer_wings",
        id -> new DevourerWingsItem(new Item.Properties().setId(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ITEM,id)).rarity(Rarity.EPIC).fireResistant())
    );

    private ModItems() {
    }
}
