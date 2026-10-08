package org.hp.hp_end_expansion.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;

/** 潮光礁海的神器弩。射击和原版弩一样，名称用潮青色。 */
public final class TideCrossbowItem extends CrossbowItem {
    public TideCrossbowItem(Properties properties) {
        super(properties);
    }

    @Override public Component getName(ItemStack stack) {
        return Component.translatable(getDescriptionId(stack)).withStyle(ChatFormatting.AQUA);
    }
}
