package com.hp_end_expansion.content.prismatic.item;

import com.hp_end_expansion.HpEndExpansion;
import com.hp_end_expansion.content.prismatic.block.PrismaticPlantBlock;
import com.hp_end_expansion.content.prismatic.block.PrismaticSaplingBlock;
import com.hp_end_expansion.content.prismatic.block.RegentAltarBlock;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

@EventBusSubscriber(modid = HpEndExpansion.MODID)
public final class PrismaticHints {
    // 可放置植株与祭台使用同一提示入口，说明实际可执行的交互。
    @SubscribeEvent
    public static void tooltip(ItemTooltipEvent event) {
        if (!(event.getItemStack().getItem() instanceof BlockItem item)) return;
        if (item.getBlock() instanceof PrismaticPlantBlock) {
            event.getToolTip().add(Component.translatable("tooltip.hp_end_expansion.prismatic_plant"));
        } else if (item.getBlock() instanceof PrismaticSaplingBlock) {
            event.getToolTip().add(Component.translatable("tooltip.hp_end_expansion.prismatic_tree"));
        } else if (item.getBlock() instanceof RegentAltarBlock) {
            event.getToolTip().add(Component.translatable("message.hp_end_expansion.prismatic_altar_hint"));
        }
    }

    // 事件类只使用静态监听器。
    private PrismaticHints() {}
}
