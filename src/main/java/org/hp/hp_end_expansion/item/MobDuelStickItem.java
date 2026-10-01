package org.hp.hp_end_expansion.item;

import com.mojang.logging.LogUtils;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.hp.hp_end_expansion.Hp_end_expansion;
import org.hp.hp_end_expansion.entity.starwreck.MeteorTortoiseEntity;
import org.slf4j.Logger;

@EventBusSubscriber(modid = Hp_end_expansion.MODID)
public final class MobDuelStickItem extends Item {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String SELECTED = "hp_duel_selected";
    private static final String DIMENSION = "hp_duel_dimension";
    private static final Map<Mob, Duel> DUELS = new HashMap<>();

    public MobDuelStickItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.hp_end_expansion.mob_duel_stick").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.hp_end_expansion.mob_duel_stick.cancel").withStyle(ChatFormatting.GRAY));
    }

    @SubscribeEvent
    public static void select(AttackEntityEvent event) {
        ItemStack stack = event.getEntity().getMainHandItem();
        if (!(stack.getItem() instanceof MobDuelStickItem)) return;
        event.setCanceled(true);
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        if (!(event.getTarget() instanceof Mob mob) || !mob.isAlive() || mob.isNoAi()) {
            event.getEntity().displayClientMessage(Component.translatable("message.hp_end_expansion.mob_duel_stick.invalid"), true);
            return;
        }
        if (event.getEntity().isShiftKeyDown()) {
            release(mob);
            clearSelection(stack);
            event.getEntity().displayClientMessage(Component.translatable("message.hp_end_expansion.mob_duel_stick.canceled"), true);
            return;
        }
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (data.hasUUID(SELECTED) && data.getString(DIMENSION).equals(level.dimension().location().toString())
            && level.getEntity(data.getUUID(SELECTED)) instanceof Mob first && first.isAlive() && !first.isNoAi()) {
            clearSelection(stack);
            if (first == mob) {
                event.getEntity().displayClientMessage(Component.translatable("message.hp_end_expansion.mob_duel_stick.selection_canceled"), true);
                return;
            }
            release(first);
            release(mob);
            DUELS.put(first, new Duel(first, mob));
            DUELS.put(mob, new Duel(mob, first));
            if (!sheltered(first)) first.setTarget(mob);
            if (!sheltered(mob)) mob.setTarget(first);
            LOGGER.debug("Mob duel started with native AI: first={}, firstType={}, second={}, secondType={}",
                first.getId(), first.getType(), mob.getId(), mob.getType());
            event.getEntity().displayClientMessage(Component.translatable("message.hp_end_expansion.mob_duel_stick.started",
                first.getDisplayName(), mob.getDisplayName()), true);
            return;
        }
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putUUID(SELECTED, mob.getUUID());
            tag.putString(DIMENSION, level.dimension().location().toString());
        });
        event.getEntity().displayClientMessage(Component.translatable("message.hp_end_expansion.mob_duel_stick.selected", mob.getDisplayName()), true);
    }

    private static void clearSelection(ItemStack stack) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.remove(SELECTED);
            tag.remove(DIMENSION);
        });
    }

    @SubscribeEvent
    public static void maintain(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof Mob mob) || mob.level().isClientSide()) return;
        Duel duel = DUELS.get(mob);
        if (duel == null) return;
        if (!duel.valid()) {
            release(mob);
        } else if (!sheltered(mob) && mob.getTarget() != duel.opponent) {
            mob.setTarget(duel.opponent);
        }
    }

    @SubscribeEvent
    public static void lockTarget(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || mob.level().isClientSide()) return;
        Duel duel = DUELS.get(mob);
        if (duel != null && duel.valid() && !sheltered(mob)) {
            event.setNewAboutToBeSetTarget(duel.opponent);
        }
    }

    @SubscribeEvent
    public static void leave(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof Mob mob) release(mob);
    }

    @SubscribeEvent
    public static void stop(ServerStoppedEvent event) {
        for (Mob mob : List.copyOf(DUELS.keySet())) release(mob);
    }

    private static void release(Mob mob) {
        Duel duel = DUELS.remove(mob);
        if (duel == null) return;
        Duel reverse = DUELS.get(duel.opponent);
        if (reverse != null && reverse.opponent == mob) DUELS.remove(duel.opponent);
        clearTarget(duel);
        if (reverse != null && reverse.opponent == mob) clearTarget(reverse);
        LOGGER.debug("Mob duel ended: first={}, second={}", mob.getId(), duel.opponent.getId());
    }

    private static void clearTarget(Duel duel) {
        if (duel.mob.getTarget() == duel.opponent) duel.mob.setTarget(null);
    }

    private static boolean sheltered(Mob mob) {
        return mob instanceof MeteorTortoiseEntity tortoise && tortoise.isShelled();
    }

    private record Duel(Mob mob, Mob opponent) {
        private boolean valid() {
            return mob.isAlive() && opponent.isAlive() && !mob.isRemoved() && !opponent.isRemoved()
                && !mob.isNoAi() && !opponent.isNoAi() && mob.level() == opponent.level();
        }
    }
}
