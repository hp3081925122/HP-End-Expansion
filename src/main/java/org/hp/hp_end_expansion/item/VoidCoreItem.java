package org.hp.hp_end_expansion.item;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.mojang.logging.LogUtils;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.TickEvent;
import org.hp.hp_end_expansion.Hp_end_expansion;
import org.hp.hp_end_expansion.registry.ModItems;
import org.hp.hp_end_expansion.registry.ModTidelight;
import org.slf4j.Logger;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

@EventBusSubscriber(modid = Hp_end_expansion.MODID)
public final class VoidCoreItem extends Item implements ICurioItem {
    private static final java.util.UUID CAPACITY_ID = java.util.UUID.nameUUIDFromBytes((Hp_end_expansion.MODID + ":void_core_capacity").getBytes(java.nio.charset.StandardCharsets.UTF_8));
    private static final AttributeModifier CAPACITY = new AttributeModifier(CAPACITY_ID, "hp_end_expansion.capacity_id", 3.0D, AttributeModifier.Operation.ADDITION);
    private static final Logger LOGGER = LogUtils.getLogger();

    public VoidCoreItem(Properties properties) {
        super(properties);
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(SlotContext slotContext, java.util.UUID id, ItemStack stack) {
        return ImmutableMultimap.of(ModTidelight.CROSSBOW_CAPACITY.get(), CAPACITY);
    }

    @SubscribeEvent
    public static void maintainCapacity(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        if (player.level().isClientSide) return;
        AttributeInstance capacity = player.getAttribute(ModTidelight.CROSSBOW_CAPACITY.get());
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
