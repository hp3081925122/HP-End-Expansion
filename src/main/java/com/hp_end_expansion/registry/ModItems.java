package com.hp_end_expansion.registry;

import com.hp_end_expansion.HpEndExpansion;
import com.hp_end_expansion.world.item.EnderBoxItem;
import com.hp_end_expansion.world.item.EnderSnailShellItem;
import com.hp_end_expansion.world.item.PetBagItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    // 物品延迟注册器。
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(HpEndExpansion.MODID);

    // 虚空鲸刷怪蛋。
    public static final DeferredItem<DeferredSpawnEggItem> VOID_WHALE_SPAWN_EGG = ITEMS.register("void_whale_spawn_egg", () -> new DeferredSpawnEggItem(ModEntities.VOID_WHALE, 0x1D0B2E, 0xC77DFF, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> ENDER_BOX_SPAWN_EGG = ITEMS.register("ender_box_spawn_egg", () -> new DeferredSpawnEggItem(ModEntities.ENDER_BOX, 0x2A103D, 0xB05CFF, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> ENDER_SNAIL_SPAWN_EGG = ITEMS.register("ender_snail_spawn_egg", () -> new DeferredSpawnEggItem(ModEntities.ENDER_SNAIL, 0x251038, 0xC786FF, new Item.Properties()));
    public static final DeferredItem<EnderBoxItem> ENDER_BOX = ITEMS.register("ender_box", () -> new EnderBoxItem(new Item.Properties()));
    public static final DeferredItem<PetBagItem> PET_BAG = ITEMS.register("pet_bag", () -> new PetBagItem(new Item.Properties()));
    public static final DeferredItem<EnderSnailShellItem> ENDER_SNAIL_SHELL = ITEMS.register("ender_snail_shell", () -> new EnderSnailShellItem(new Item.Properties()));

    // 物品注册类只提供静态入口，不允许实例化。
    private ModItems() {
    }

    // 注册物品和创造模式物品栏事件。
    public static void register(IEventBus 模组事件总线) {
        ITEMS.register(模组事件总线);
        模组事件总线.addListener(ModItems::addCreativeItems);
    }

    // 把虚空鲸刷怪蛋加入原版刷怪蛋标签页。
    private static void addCreativeItems(BuildCreativeModeTabContentsEvent 事件) {
        if (事件.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            事件.accept(VOID_WHALE_SPAWN_EGG.get());
            事件.accept(ENDER_BOX_SPAWN_EGG.get());
            事件.accept(ENDER_SNAIL_SPAWN_EGG.get());
        }
        if (事件.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            事件.accept(ENDER_BOX.get());
            事件.accept(PET_BAG.get());
            事件.accept(ENDER_SNAIL_SHELL.get());
        }
    }
}
