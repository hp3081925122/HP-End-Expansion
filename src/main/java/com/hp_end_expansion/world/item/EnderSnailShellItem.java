package com.hp_end_expansion.world.item;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public class EnderSnailShellItem extends Item {
    // 创建末影蜗牛壳物品。
    public EnderSnailShellItem(Properties properties) {
        super(properties);
    }

    // 显示末影蜗牛壳的盔甲合成用途。
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.hp_end_expansion.ender_snail_shell.tooltip"));
    }
}
