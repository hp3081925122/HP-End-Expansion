package com.hp_end_expansion.registry;

import com.hp_end_expansion.HpEndExpansion;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(BuiltInRegistries.CREATIVE_MODE_TAB, HpEndExpansion.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> HP_END_EXPANSION = CREATIVE_TABS.register("hp_end_expansion", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.hp_end_expansion"))
            .icon(() -> new ItemStack(ModItems.VOID_WHALE_SPAWN_EGG.get()))
            .displayItems((parameters, 输出物品) -> {
                输出物品.accept(ModItems.VOID_WHALE_SPAWN_EGG.get());
                输出物品.accept(ModItems.ENDER_BOX_SPAWN_EGG.get());
                输出物品.accept(ModItems.ENDER_SNAIL_SPAWN_EGG.get());
                输出物品.accept(ModItems.ENDER_BOX.get());
                输出物品.accept(ModItems.PET_BAG.get());
                输出物品.accept(ModItems.ENDER_SNAIL_SHELL.get());
            })
            .build());

    private ModCreativeTabs() {
    }

    public static void register(IEventBus 模组事件总线) {
        CREATIVE_TABS.register(模组事件总线);
    }
}
