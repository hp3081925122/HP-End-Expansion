package org.hp.hp_end_expansion.item;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.hp.hp_end_expansion.Hp_end_expansion;
import org.hp.hp_end_expansion.registry.ModItems;
import org.hp.hp_end_expansion.registry.ModTidelight;
import org.slf4j.Logger;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

@EventBusSubscriber(modid = Hp_end_expansion.MODID)
public final class VoidCoreItem extends Item implements ICurioItem {
    private static final Identifier CAPACITY_ID = Identifier.fromNamespaceAndPath(Hp_end_expansion.MODID, "void_core_capacity");
    private static final AttributeModifier CAPACITY = new AttributeModifier(CAPACITY_ID, 3.0D, AttributeModifier.Operation.ADD_VALUE);
    private static final Logger LOGGER = LogUtils.getLogger();

    public VoidCoreItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display,
        java.util.function.Consumer<net.minecraft.network.chat.Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
        tooltip.accept(net.minecraft.network.chat.Component.translatable("attribute.modifier.plus.0", 3,
            net.minecraft.network.chat.Component.translatable(ModTidelight.CROSSBOW_CAPACITY.value().getDescriptionId())).withStyle(net.minecraft.ChatFormatting.BLUE));
    }

    @SubscribeEvent
    public static void maintainCapacity(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;
        AttributeInstance capacity = player.getAttribute(ModTidelight.CROSSBOW_CAPACITY);
        if (capacity == null) return;
        boolean equipped = CuriosApi.getCuriosInventory(player).map(handler -> handler.isEquipped(ModItems.VOID_CORE.get())).orElse(false);
        AttributeModifier current = capacity.getModifier(CAPACITY_ID);
        if (equipped && current == null) {
            capacity.addTransientModifier(CAPACITY);
            LOGGER.debug("Void core capacity modifier restored: entity={}, amount=3", player.getId());
        } else if (!equipped && current != null) {
            capacity.removeModifier(CAPACITY_ID);
            LOGGER.debug("Void core capacity modifier removed: entity={}", player.getId());
        }
    }
}
