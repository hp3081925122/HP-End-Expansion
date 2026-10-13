package org.hp.hp_end_expansion.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.hp.hp_end_expansion.Hp_end_expansion;
import org.hp.hp_end_expansion.registry.ModItems;
import org.hp.hp_end_expansion.registry.ModTidelight;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

public final class TidebreakerHammerCoreItem extends Item implements ICurioItem {
    public static final String ARROW_DAMAGE_KEY = Hp_end_expansion.MODID + ":hammer_core_damage";

    public TidebreakerHammerCoreItem(Properties properties) {
        super(properties);
    }

    public static boolean isEquipped(LivingEntity entity) {
        return CuriosApi.getCuriosInventory(entity)
            .map(handler -> handler.isEquipped(ModItems.TIDEBREAKER_HAMMER_CORE.get()))
            .orElse(false);
    }

    @Override
    public top.theillusivec4.curios.api.CurioAttributeModifiers getDefaultCurioAttributeModifiers(ItemStack stack) {
        return top.theillusivec4.curios.api.CurioAttributeModifiers.EMPTY.withModifierAdded(ModTidelight.CROSSBOW_CAPACITY,
            new AttributeModifier(Identifier.fromNamespaceAndPath(org.hp.hp_end_expansion.Hp_end_expansion.MODID, "tidebreaker_hammer_core_capacity"), 1.0D, AttributeModifier.Operation.ADD_VALUE), "relic");
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display, java.util.function.Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.hp_end_expansion.tidebreaker_hammer_core.damage").withStyle(ChatFormatting.GOLD));
        tooltip.accept(Component.translatable("tooltip.hp_end_expansion.tidebreaker_hammer_core.limit").withStyle(ChatFormatting.GRAY));
    }
}
