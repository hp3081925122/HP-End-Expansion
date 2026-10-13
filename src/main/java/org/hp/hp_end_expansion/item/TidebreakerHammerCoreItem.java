package org.hp.hp_end_expansion.item;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.hp.hp_end_expansion.Hp_end_expansion;
import org.hp.hp_end_expansion.registry.ModItems;
import org.hp.hp_end_expansion.registry.ModTidelight;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
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
    public Multimap<Holder<Attribute>, AttributeModifier> getAttributeModifiers(SlotContext slotContext, ResourceLocation id, ItemStack stack) {
        return ImmutableMultimap.of(ModTidelight.CROSSBOW_CAPACITY, new AttributeModifier(id, 1.0D, AttributeModifier.Operation.ADD_VALUE));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.hp_end_expansion.tidebreaker_hammer_core.damage").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.hp_end_expansion.tidebreaker_hammer_core.limit").withStyle(ChatFormatting.GRAY));
    }
}
