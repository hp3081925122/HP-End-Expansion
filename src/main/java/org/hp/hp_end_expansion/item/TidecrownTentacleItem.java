package org.hp.hp_end_expansion.item;

import net.minecraft.world.level.Level;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.hp.hp_end_expansion.registry.ModTidelight;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

public final class TidecrownTentacleItem extends Item implements ICurioItem {
    private static final int RELOAD_COOLDOWN = 60;

    public TidecrownTentacleItem(Properties properties) {
        super(properties);
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(SlotContext slotContext, java.util.UUID id, ItemStack stack) {
        return ImmutableMultimap.of(ModTidelight.CROSSBOW_CAPACITY.get(), new AttributeModifier(id, "hp_end_expansion.capacity", 1.0D, AttributeModifier.Operation.ADDITION));
    }

    @Override
    public void curioTick(SlotContext slotContext, ItemStack stack) {
        if (!(slotContext.entity() instanceof Player player) || player.level().isClientSide
            || !player.isAlive() || player.isSpectator() || player.getCooldowns().isOnCooldown(this)) return;
        player.getCooldowns().addCooldown(this, RELOAD_COOLDOWN);
        ItemStack selected = mostLoadedCrossbow(player);
        if (selected.isEmpty() || player.isUsingItem() && player.getUseItem() == selected) return;
        if (selected.getItem() instanceof TideCrossbowItem crossbow) crossbow.loadRound(selected, player);
    }

    private static ItemStack mostLoadedCrossbow(Player player) {
        ItemStack selected = ItemStack.EMPTY;
        int maximum = -1;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack candidate = player.getInventory().getItem(slot);
            if (!(candidate.getItem() instanceof TideCrossbowItem)) continue;
            int loaded = TideCrossbowItem.loadedRounds(candidate);
            if (loaded > maximum) {
                maximum = loaded;
                selected = candidate;
            }
        }
        return selected;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.hp_end_expansion.tidecrown_tentacle.reload").withStyle(ChatFormatting.GOLD));
    }
}
