package org.hp.hp_end_expansion.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

public final class SkyEyeItem extends Item implements ICurioItem {
    public SkyEyeItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display, java.util.function.Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.hp_end_expansion.sky_eye.1").withStyle(ChatFormatting.GOLD));
        tooltip.accept(Component.translatable("tooltip.hp_end_expansion.sky_eye.2").withStyle(ChatFormatting.GOLD));
        tooltip.accept(Component.translatable("tooltip.hp_end_expansion.sky_eye.3").withStyle(ChatFormatting.GOLD));
        tooltip.accept(Component.translatable("tooltip.hp_end_expansion.sky_eye.4").withStyle(ChatFormatting.GOLD));
    }
}
