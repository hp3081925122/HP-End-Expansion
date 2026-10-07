package org.hp.hp_end_expansion.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.hp.hp_end_expansion.registry.ModStarwreck;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

public final class EmberCrownItem extends Item implements ICurioItem {
    private static final int REPAIR_AMOUNT = 3;

    public EmberCrownItem(Properties properties) {
        super(properties);
    }

    @Override
    public void curioTick(SlotContext slotContext, ItemStack stack) {
        if (!(slotContext.entity() instanceof Player player) || player.level().isClientSide) return;
        if (player.tickCount % 20 != 0) return;
        ItemStack bow = findDamagedBow(player);
        if (bow.isEmpty() || countStarCrystalShards(player) <= 1 || !consumeStarCrystalShard(player)) return;
        bow.setDamageValue(Math.max(0, bow.getDamageValue() - REPAIR_AMOUNT));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.hp_end_expansion.ember_crown.1").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.hp_end_expansion.ember_crown.2").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.hp_end_expansion.ember_crown.3").withStyle(ChatFormatting.GOLD));
    }

    private static ItemStack findDamagedBow(Player player) {
        for (ItemStack item : player.getInventory().items) {
            if (item.is(ModStarwreck.STAR_BOW.get()) && item.getDamageValue() > 0) return item;
        }
        return ItemStack.EMPTY;
    }

    private static int countStarCrystalShards(Player player) {
        int count = 0;
        for (ItemStack item : player.getInventory().items) {
            if (item.is(ModStarwreck.STAR_CRYSTAL_SHARD.get())) count += item.getCount();
        }
        return count;
    }

    private static boolean consumeStarCrystalShard(Player player) {
        for (ItemStack item : player.getInventory().items) {
            if (item.is(ModStarwreck.STAR_CRYSTAL_SHARD.get())) {
                item.shrink(1);
                return true;
            }
        }
        return false;
    }
}
