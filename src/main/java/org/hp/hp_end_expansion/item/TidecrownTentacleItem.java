package org.hp.hp_end_expansion.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
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
    public top.theillusivec4.curios.api.CurioAttributeModifiers getDefaultCurioAttributeModifiers(ItemStack stack) {
        return top.theillusivec4.curios.api.CurioAttributeModifiers.EMPTY.withModifierAdded(ModTidelight.CROSSBOW_CAPACITY,
            new AttributeModifier(Identifier.fromNamespaceAndPath(org.hp.hp_end_expansion.Hp_end_expansion.MODID, "tidecrown_tentacle_capacity"), 1.0D, AttributeModifier.Operation.ADD_VALUE), "relic");
    }

    @Override
    public void curioTick(SlotContext slotContext, ItemStack stack) {
        if (!(slotContext.entity() instanceof Player player) || player.level().isClientSide()
            || !player.isAlive() || player.isSpectator() || player.getCooldowns().isOnCooldown(new ItemStack(this))) return;
        player.getCooldowns().addCooldown(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(this), RELOAD_COOLDOWN);
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
    public void appendHoverText(ItemStack stack, TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display, java.util.function.Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.hp_end_expansion.tidecrown_tentacle.reload").withStyle(ChatFormatting.GOLD));
    }
}
