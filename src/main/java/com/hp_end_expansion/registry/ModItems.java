package com.hp_end_expansion.registry;

import com.hp_end_expansion.HpEndExpansion;
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

    // 物品注册类只提供静态入口，不允许实例化。
    private ModItems() {
    }

    // 注册物品和创造模式物品栏事件。
    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        modEventBus.addListener(ModItems::addCreativeItems);
    }

    // 把虚空鲸刷怪蛋加入原版刷怪蛋标签页。
    private static void addCreativeItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(VOID_WHALE_SPAWN_EGG.get());
        }
    }
}
