package org.hp.hp_end_expansion.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

public final class HolyStarItem extends Item implements ICurioItem {
    public HolyStarItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.hp_end_expansion.holy_star.1").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.hp_end_expansion.holy_star.2").withStyle(ChatFormatting.GOLD));
    }
}
