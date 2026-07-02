package com.hp_end_expansion.world.item;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public class EnderSnailShellItem extends Item {
    // 创建末影蜗牛壳物品。
    public EnderSnailShellItem(Properties 属性) {
        super(属性);
    }

    // 显示末影蜗牛壳的盔甲合成用途。
    @Override
    public void appendHoverText(ItemStack 物品栈, TooltipContext 上下文, List<Component> 提示列表, TooltipFlag 提示标志) {
        提示列表.add(Component.translatable("item.hp_end_expansion.ender_snail_shell.tooltip"));
    }
}
