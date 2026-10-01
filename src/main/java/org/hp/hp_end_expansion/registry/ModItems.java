package org.hp.hp_end_expansion.registry;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import org.hp.hp_end_expansion.item.MatriarchScytheItem;
import org.hp.hp_end_expansion.item.MobDuelStickItem;
import org.hp.hp_end_expansion.item.RiftEggItem;
import org.hp.hp_end_expansion.item.DevourerWingsItem;
import org.hp.hp_end_expansion.item.StarBeaconItem;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.hp.hp_end_expansion.Hp_end_expansion;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Hp_end_expansion.MODID);
    public static final DeferredItem<Item> MOB_DUEL_STICK = ITEMS.register(
        "mob_duel_stick", () -> new MobDuelStickItem(new Item.Properties().stacksTo(1))
    );
    public static final DeferredItem<SpawnEggItem> RIFT_MANTIS_SPAWN_EGG = ITEMS.register(
        "rift_mantis_spawn_egg",
        () -> new DeferredSpawnEggItem(ModEntities.RIFT_MANTIS, 0x30283E, 0xB26DE3, new Item.Properties())
    );
    public static final DeferredItem<SpawnEggItem> RIFT_MATRIARCH_SPAWN_EGG = ITEMS.register(
        "rift_matriarch_spawn_egg",
        () -> new DeferredSpawnEggItem(ModEntities.RIFT_MATRIARCH, 0x231D30, 0xB26DE3, new Item.Properties())
    );
    public static final DeferredItem<SpawnEggItem> VOID_RAY_SPAWN_EGG = ITEMS.register(
        "void_ray_spawn_egg",
        () -> new DeferredSpawnEggItem(ModEntities.VOID_RAY, 0x231D30, 0xB26DE3, new Item.Properties())
    );
    public static final DeferredItem<SpawnEggItem> STAR_DEVOURER_SPAWN_EGG = ITEMS.register(
        "star_devourer_spawn_egg",
        () -> new DeferredSpawnEggItem(ModEntities.STAR_DEVOURER, 0x171622, 0xCB70D8, new Item.Properties())
    );

    public static final DeferredItem<SpawnEggItem> END_MOTE_SPAWN_EGG = ITEMS.register(
        "end_mote_spawn_egg",
        () -> new DeferredSpawnEggItem(ModEntities.END_MOTE, 0x1A0A2E, 0x9FE8FF, new Item.Properties())
    );
    // 末晶萤掉落：末晶尘
    public static final DeferredItem<Item> END_DUST = ITEMS.register(
        "end_dust",
        () -> new Item(new Item.Properties().rarity(Rarity.COMMON))
    );

    // 裂空鳐掉落：虚空核心
    public static final DeferredItem<Item> VOID_CORE = ITEMS.register(
        "void_core",
        () -> new Item(new Item.Properties().rarity(Rarity.RARE).fireResistant())
    );
    // 裂空鳐掉落：鳐翼膜
    public static final DeferredItem<Item> RAY_MEMBRANE = ITEMS.register(
        "ray_membrane",
        () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON))
    );

    // 裂隙螳螂掉落：裂隙甲壳
    public static final DeferredItem<Item> RIFT_CARAPACE = ITEMS.register(
        "rift_carapace",
        () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON))
    );
    // 裂隙螳螂掉落：裂隙镰刃
    public static final DeferredItem<Item> RIFT_SICKLE = ITEMS.register(
        "rift_sickle",
        () -> new Item(new Item.Properties().rarity(Rarity.RARE).fireResistant())
    );

    // 螳后召唤物：裂隙之卵
    public static final DeferredItem<Item> RIFT_EGG = ITEMS.register(
        "rift_egg",
        () -> new RiftEggItem(new Item.Properties().rarity(Rarity.EPIC).stacksTo(1).fireResistant())
    );
    // 螳后掉落：螳后之镰
    public static final DeferredItem<Item> MATRIARCH_SCYTHE = ITEMS.register(
        "matriarch_scythe",
        () -> new MatriarchScytheItem(Tiers.NETHERITE, new Item.Properties().rarity(Rarity.EPIC).fireResistant()
            .attributes(SwordItem.createAttributes(Tiers.NETHERITE, 5, -2.6F)))
    );

    // 鳐王召唤物：星引信标
    public static final DeferredItem<Item> STAR_BEACON = ITEMS.register(
        "star_beacon",
        () -> new StarBeaconItem(new Item.Properties().rarity(Rarity.EPIC).stacksTo(1).fireResistant())
    );
    // 鳐王掉落：鳐王之翼
    public static final DeferredItem<Item> DEVOURER_WINGS = ITEMS.register(
        "devourer_wings",
        () -> new DevourerWingsItem(new Item.Properties().rarity(Rarity.EPIC).durability(864).fireResistant())
    );

    private ModItems() {
    }
}
