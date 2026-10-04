package org.hp.hp_end_expansion.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

public final class SkyrenderHornItem extends Item implements ICurioItem {
    public SkyrenderHornItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        for (int i = 1; i <= 5; i++) {
            tooltip.add(Component.translatable("tooltip.hp_end_expansion.skyrender_horn." + i).withStyle(ChatFormatting.GOLD));
        }
    }
}
