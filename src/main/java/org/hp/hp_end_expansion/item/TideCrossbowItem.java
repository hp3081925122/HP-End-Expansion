package org.hp.hp_end_expansion.item;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import org.hp.hp_end_expansion.registry.ModTidelight;
import org.hp.hp_end_expansion.Hp_end_expansion;
import org.slf4j.Logger;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

@EventBusSubscriber(modid = Hp_end_expansion.MODID)
public final class TideCrossbowItem extends CrossbowItem {
    public static final Identifier CAPACITY_MODIFIER_ID = Identifier.fromNamespaceAndPath(Hp_end_expansion.MODID, "tide_crossbow_capacity");
    private static final Logger LOGGER = LogUtils.getLogger();

    public TideCrossbowItem(Properties properties) {
        super(properties);
    }

    @SubscribeEvent
    public static void onArrowDamage(LivingIncomingDamageEvent event) {
        LivingEntity holder = event.getEntity();
        if (holder.level().isClientSide() || !event.getSource().is(DamageTypeTags.IS_PROJECTILE)) return;
        if (!(event.getSource().getDirectEntity() instanceof AbstractArrow arrow) || arrow instanceof ThrownTrident) return;
        if (!(holder.getMainHandItem().getItem() instanceof TideCrossbowItem)
            && !(holder.getOffhandItem().getItem() instanceof TideCrossbowItem)) return;
        event.setCanceled(true);
        LOGGER.debug("Tide crossbow arrow immunity: holder={}, arrow={}, damage={}", holder.getId(), arrow.getId(), event.getAmount());
    }

    public static int loadedRounds(ItemStack stack) {
        return (isCharged(stack) ? 1 : 0) + stack.getOrDefault(ModTidelight.CROSSBOW_RESERVE.get(), List.of()).size();
    }

    public static int capacity(LivingEntity entity) {
        return entity.getAttribute(ModTidelight.CROSSBOW_CAPACITY) == null ? 0
            : (int) Math.floor(entity.getAttributeValue(ModTidelight.CROSSBOW_CAPACITY));
    }

    public static void restoreCapacityModifier(LivingEntity entity) {
        if (entity.level().isClientSide()) return;
        AttributeInstance capacity = entity.getAttribute(ModTidelight.CROSSBOW_CAPACITY);
        if (capacity == null || capacity.getModifier(CAPACITY_MODIFIER_ID) != null) return;
        for (EquipmentSlot slot : List.of(EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND)) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (!(stack.getItem() instanceof TideCrossbowItem)) continue;
            stack.forEachModifier(slot, (attribute, modifier) -> {
                if (attribute.value() == ModTidelight.CROSSBOW_CAPACITY.value() && modifier.id().equals(CAPACITY_MODIFIER_ID)
                    && capacity.getModifier(CAPACITY_MODIFIER_ID) == null) {
                    capacity.addTransientModifier(modifier);
                    LOGGER.debug("Tide crossbow capacity modifier restored: entity={}, slot={}, amount={}", entity.getId(), slot, modifier.amount());
                }
            });
            if (capacity.getModifier(CAPACITY_MODIFIER_ID) != null) return;
        }
    }

    @Nullable
    public static InteractionHand heldHand(Player player) {
        if (player.getMainHandItem().getItem() instanceof TideCrossbowItem) return InteractionHand.MAIN_HAND;
        if (player.getOffhandItem().getItem() instanceof TideCrossbowItem) return InteractionHand.OFF_HAND;
        return null;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (loadedRounds(stack) >= capacity(player)) return InteractionResult.CONSUME;
        if (player.getProjectile(stack).isEmpty()) return InteractionResult.FAIL;
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        return false;
    }

    @Override
    public boolean useOnRelease(ItemStack stack) {
        return false;
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int timeLeft) {
        if (loadedRounds(stack) >= capacity(entity) || entity.getProjectile(stack).isEmpty()) {
            entity.stopUsingItem();
            return;
        }
        if (getUseDuration(stack, entity) - timeLeft < getChargeDuration(stack, entity)) {
            super.onUseTick(level, entity, stack, timeLeft);
            return;
        }
        InteractionHand hand = entity.getUsedItemHand();
        if (!level.isClientSide()) {
            boolean loaded = loadRound(stack, entity);
            LOGGER.debug("Tide crossbow continuous reload: entity={}, loaded={}, rounds={}, capacity={}",
                entity.getId(), loaded, loadedRounds(stack), capacity(entity));
            if (!loaded || loadedRounds(stack) >= capacity(entity) || entity.getProjectile(stack).isEmpty()) {
                entity.stopUsingItem();
                return;
            }
        }
        entity.stopUsingItem();
        entity.startUsingItem(hand);
    }

    public boolean loadRound(ItemStack stack, LivingEntity entity) {
        Level level = entity.level();
        if (!(level instanceof ServerLevel) || !entity.isAlive() || stack.getItem() != this
            || loadedRounds(stack) >= capacity(entity)) return false;
        int previousRounds = loadedRounds(stack);
        ChargedProjectiles current = stack.getOrDefault(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY);
        stack.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY);
        try {
            List<ItemStack> drawn = draw(stack, entity.getProjectile(stack), entity);
            if (!drawn.isEmpty()) stack.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.ofNonEmpty(drawn));
            ChargedProjectiles loaded = stack.getOrDefault(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY);
            if (!loaded.isEmpty() && !current.isEmpty()) {
                List<ChargedProjectiles> reserve = new ArrayList<>(stack.getOrDefault(ModTidelight.CROSSBOW_RESERVE.get(), List.of()));
                reserve.add(loaded);
                stack.set(ModTidelight.CROSSBOW_RESERVE.get(), List.copyOf(reserve));
            }
        } finally {
            if (!current.isEmpty()) stack.set(DataComponents.CHARGED_PROJECTILES, current);
        }
        if (loadedRounds(stack) == previousRounds) return false;
        EnchantmentHelper.pickHighestLevel(stack, EnchantmentEffectComponents.CROSSBOW_CHARGING_SOUNDS)
            .map(ChargingSounds::end).orElse(java.util.Optional.of(SoundEvents.CROSSBOW_LOADING_END))
            .ifPresent(sound -> level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), sound.value(),
                entity.getSoundSource(), 1.0F, 1.0F / (level.getRandom().nextFloat() * 0.5F + 1.0F) + 0.2F));
        if (entity instanceof Player player) {
            player.sendOverlayMessage(Component.translatable("item.hp_end_expansion.tide_crossbow.ammo", loadedRounds(stack), capacity(entity)));
        }
        return true;
    }

    public void shootLoaded(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player.level() instanceof ServerLevel) || !player.isAlive() || player.isSpectator()
            || player.isUsingItem() || stack.getItem() != this || player.getCooldowns().isOnCooldown(new ItemStack(this)) || !isCharged(stack)) return;
        ChargedProjectiles current = stack.getOrDefault(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY);
        performShooting(player.level(), player, hand, stack, current.contains(Items.FIREWORK_ROCKET) ? 1.6F : 3.15F, 1.0F, null);
        player.sendOverlayMessage(Component.translatable("item.hp_end_expansion.tide_crossbow.ammo", loadedRounds(stack), capacity(player)));
    }

    @Override
    protected Projectile createProjectile(Level level, LivingEntity shooter, ItemStack weapon, ItemStack ammo, boolean critical) {
        Projectile projectile = super.createProjectile(level, shooter, weapon, ammo, critical);
        // 标记潮光之弩射出的箭矢，供深渊晶刺识别
        if (projectile instanceof AbstractArrow tideArrow) tideArrow.getPersistentData().putBoolean(AbyssCrystalSpineItem.TIDE_ARROW_KEY, true);
        if (projectile instanceof AbstractArrow arrow && TidebreakerHammerCoreItem.isEquipped(shooter)) {
            int roundsBeforeShot = loadedRounds(weapon) + 1;
            int bonus = Math.min(roundsBeforeShot, capacity(shooter));
            arrow.getPersistentData().putInt(TidebreakerHammerCoreItem.ARROW_DAMAGE_KEY, bonus);
            LOGGER.debug("Tide crossbow hammer core shot: shooter={}, arrow={}, loadedRounds={}, bonus={}",
                shooter.getId(), arrow.getId(), roundsBeforeShot, bonus);
        }
        return projectile;
    }

    @Override
    public void performShooting(Level level, LivingEntity shooter, InteractionHand hand, ItemStack stack, float velocity, float inaccuracy, @Nullable LivingEntity target) {
        if (!(level instanceof ServerLevel) || !isCharged(stack)) return;
        super.performShooting(level, shooter, hand, stack, velocity, inaccuracy, target);
        if (isCharged(stack)) return;
        if (!stack.isEmpty()) {
            List<ChargedProjectiles> reserve = stack.getOrDefault(ModTidelight.CROSSBOW_RESERVE.get(), List.of());
            if (!reserve.isEmpty()) {
                stack.set(DataComponents.CHARGED_PROJECTILES, reserve.getFirst());
                if (reserve.size() == 1) stack.remove(ModTidelight.CROSSBOW_RESERVE.get());
                else stack.set(ModTidelight.CROSSBOW_RESERVE.get(), List.copyOf(reserve.subList(1, reserve.size())));
            }
        }
        if (shooter instanceof Player player) player.getCooldowns().addCooldown(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(this), 4);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display, java.util.function.Consumer<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack,context,display,lines,flag);
        lines.accept(Component.translatable("item.hp_end_expansion.tide_crossbow.loaded", loadedRounds(stack)).withStyle(ChatFormatting.AQUA));
        lines.accept(Component.translatable("item.hp_end_expansion.tide_crossbow.controls").withStyle(ChatFormatting.GRAY));
        lines.accept(Component.translatable("item.hp_end_expansion.tide_crossbow.arrow_immunity").withStyle(ChatFormatting.AQUA));
    }

    @Override public Component getName(ItemStack stack) {
        return Component.translatable(getDescriptionId()).withStyle(ChatFormatting.AQUA);
    }
}
