package org.hp.hp_end_expansion.item;

import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.hp.hp_end_expansion.Hp_end_expansion;
import org.hp.hp_end_expansion.registry.ModTidelight;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.slf4j.Logger;

@EventBusSubscriber(modid = Hp_end_expansion.MODID)
public final class TideCrossbowItem extends CrossbowItem {
    public static final UUID CAPACITY_MODIFIER_ID = UUID.nameUUIDFromBytes(
        (Hp_end_expansion.MODID + ":tide_crossbow_capacity").getBytes(java.nio.charset.StandardCharsets.UTF_8));
    private static final String RESERVE_KEY = Hp_end_expansion.MODID + ":crossbow_reserve";
    private static final AttributeModifier CAPACITY = new AttributeModifier(CAPACITY_MODIFIER_ID,
        "hp_end_expansion.tide_crossbow_capacity", 3.0D, AttributeModifier.Operation.ADDITION);
    private static final Logger LOGGER = LogUtils.getLogger();

    public TideCrossbowItem(Properties properties) { super(properties); }
    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        return slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND
            ? ImmutableMultimap.of(ModTidelight.CROSSBOW_CAPACITY.get(), CAPACITY) : super.getAttributeModifiers(slot, stack);
    }
    @SubscribeEvent
    public static void onArrowDamage(LivingAttackEvent event) {
        LivingEntity holder = event.getEntity();
        if (holder.level().isClientSide || !event.getSource().is(DamageTypeTags.IS_PROJECTILE)) return;
        if (!(event.getSource().getDirectEntity() instanceof AbstractArrow arrow) || arrow instanceof ThrownTrident) return;
        if (!(holder.getMainHandItem().getItem() instanceof TideCrossbowItem)
            && !(holder.getOffhandItem().getItem() instanceof TideCrossbowItem)) return;
        event.setCanceled(true);
        LOGGER.debug("Tide crossbow arrow immunity: holder={}, arrow={}, damage={}", holder.getId(), arrow.getId(), event.getAmount());
    }
    public static int loadedRounds(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return (isCharged(stack) ? 1 : 0) + (tag == null ? 0 : tag.getList(RESERVE_KEY, Tag.TAG_COMPOUND).size());
    }
    public static int capacity(LivingEntity entity) {
        return entity.getAttribute(ModTidelight.CROSSBOW_CAPACITY.get()) == null ? 0
            : (int) Math.floor(entity.getAttributeValue(ModTidelight.CROSSBOW_CAPACITY.get()));
    }
    public static void restoreCapacityModifier(LivingEntity entity) {
        if (entity.level().isClientSide) return;
        AttributeInstance capacity = entity.getAttribute(ModTidelight.CROSSBOW_CAPACITY.get());
        if (capacity == null || capacity.getModifier(CAPACITY_MODIFIER_ID) != null) return;
        if (entity.getMainHandItem().getItem() instanceof TideCrossbowItem || entity.getOffhandItem().getItem() instanceof TideCrossbowItem) {
            capacity.addTransientModifier(CAPACITY);
            LOGGER.debug("Tide crossbow capacity modifier restored: entity={}, amount=3", entity.getId());
        }
    }
    @Nullable
    public static InteractionHand heldHand(Player player) {
        if (player.getMainHandItem().getItem() instanceof TideCrossbowItem) return InteractionHand.MAIN_HAND;
        if (player.getOffhandItem().getItem() instanceof TideCrossbowItem) return InteractionHand.OFF_HAND;
        return null;
    }
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (loadedRounds(stack) >= capacity(player)) return InteractionResultHolder.consume(stack);
        if (player.getProjectile(stack).isEmpty() && !player.getAbilities().instabuild) return InteractionResultHolder.fail(stack);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }
    @Override public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {}
    @Override public boolean useOnRelease(ItemStack stack) { return false; }
    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int timeLeft) {
        boolean creative = entity instanceof Player player && player.getAbilities().instabuild;
        if (loadedRounds(stack) >= capacity(entity) || entity.getProjectile(stack).isEmpty() && !creative) {
            entity.stopUsingItem();
            return;
        }
        super.onUseTick(level, entity, stack, timeLeft);
        if (getUseDuration(stack) - timeLeft < getChargeDuration(stack)) return;
        InteractionHand hand = entity.getUsedItemHand();
        if (!level.isClientSide) {
            boolean loaded = loadRound(stack, entity);
            LOGGER.debug("Tide crossbow continuous reload: entity={}, loaded={}, rounds={}, capacity={}",
                entity.getId(), loaded, loadedRounds(stack), capacity(entity));
            if (!loaded || loadedRounds(stack) >= capacity(entity) || entity.getProjectile(stack).isEmpty() && !creative) {
                entity.stopUsingItem();
                return;
            }
        }
        entity.stopUsingItem();
        entity.startUsingItem(hand);
    }
    public boolean loadRound(ItemStack stack, LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel level) || !entity.isAlive() || stack.getItem() != this
            || loadedRounds(stack) >= capacity(entity)) return false;
        boolean creative = entity instanceof Player player && player.getAbilities().instabuild;
        ItemStack ammo = entity.getProjectile(stack);
        if (ammo.isEmpty()) {
            if (!creative) return false;
            ammo = new ItemStack(Items.ARROW);
        }
        ItemStack loaded = ammo.copyWithCount(1);
        int count = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.MULTISHOT, stack) > 0 ? 3 : 1;
        if (TideRemnantClawItem.isEquipped(entity)) count += capacity(entity) / 2;
        ListTag projectiles = new ListTag();
        for (int i = 0; i < count; i++) projectiles.add(loaded.save(new CompoundTag()));
        CompoundTag tag = stack.getOrCreateTag();
        if (isCharged(stack)) {
            ListTag reserve = tag.getList(RESERVE_KEY, Tag.TAG_COMPOUND);
            CompoundTag round = new CompoundTag();
            round.put("ChargedProjectiles", projectiles);
            reserve.add(round);
            tag.put(RESERVE_KEY, reserve);
        } else {
            tag.put("ChargedProjectiles", projectiles);
            setCharged(stack, true);
        }
        if (!creative) {
            ammo.shrink(1);
            if (ammo.isEmpty() && entity instanceof Player player) player.getInventory().removeItem(ammo);
        }
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.CROSSBOW_LOADING_END,
            entity instanceof Player ? SoundSource.PLAYERS : SoundSource.HOSTILE, 1.0F,
            1.0F / (level.random.nextFloat() * 0.5F + 1.0F) + 0.2F);
        if (entity instanceof Player player) showAmmo(player, stack);
        return true;
    }
    public void shootLoaded(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player.level() instanceof ServerLevel level) || !player.isAlive() || player.isSpectator()
            || player.isUsingItem() || stack.getItem() != this || player.getCooldowns().isOnCooldown(this) || !isCharged(stack)) return;
        if (ForgeEventFactory.onArrowLoose(stack, level, player, 1, true) < 0) return;
        ListTag projectiles = stack.getOrCreateTag().getList("ChargedProjectiles", Tag.TAG_COMPOUND).copy();
        int bonus = TidebreakerHammerCoreItem.isEquipped(player) ? Math.min(loadedRounds(stack), capacity(player)) : 0;
        float separation = projectiles.size() <= 1 ? 0.0F : 20.0F / (projectiles.size() - 1);
        float offset = ((projectiles.size() - 1) % 2) * separation / 2.0F;
        for (int i = 0; i < projectiles.size() && !stack.isEmpty(); i++) {
            ItemStack ammo = ItemStack.of(projectiles.getCompound(i));
            if (ammo.isEmpty()) continue;
            float angle = offset + (i % 2 == 0 ? 1.0F : -1.0F) * ((i + 1) / 2) * separation;
            shootProjectile(level, player, hand, stack, ammo, i > 0, angle, bonus);
        }
        CriteriaTriggers.SHOT_CROSSBOW.trigger((ServerPlayer) player, stack);
        player.awardStat(Stats.ITEM_USED.get(this));
        if (!stack.isEmpty()) {
            CompoundTag tag = stack.getOrCreateTag();
            ListTag reserve = tag.getList(RESERVE_KEY, Tag.TAG_COMPOUND);
            if (reserve.isEmpty()) {
                tag.remove("ChargedProjectiles");
                setCharged(stack, false);
            } else {
                tag.put("ChargedProjectiles", reserve.getCompound(0).getList("ChargedProjectiles", Tag.TAG_COMPOUND).copy());
                reserve.remove(0);
                if (reserve.isEmpty()) tag.remove(RESERVE_KEY);
                else tag.put(RESERVE_KEY, reserve);
                setCharged(stack, true);
            }
        }
        player.getCooldowns().addCooldown(this, 4);
        showAmmo(player, stack);
    }
    private void shootProjectile(ServerLevel level, Player player, InteractionHand hand, ItemStack weapon,
        ItemStack ammo, boolean extra, float angle, int bonus) {
        boolean firework = ammo.is(Items.FIREWORK_ROCKET);
        Projectile projectile;
        if (firework) {
            projectile = new FireworkRocketEntity(level, ammo, player, player.getX(), player.getEyeY() - 0.15D, player.getZ(), true);
        } else {
            ArrowItem arrowItem = (ArrowItem) (ammo.getItem() instanceof ArrowItem ? ammo.getItem() : Items.ARROW);
            AbstractArrow arrow = arrowItem.createArrow(level, ammo, player);
            arrow.setCritArrow(true);
            arrow.setSoundEvent(SoundEvents.CROSSBOW_HIT);
            arrow.setShotFromCrossbow(true);
            int pierce = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.PIERCING, weapon);
            if (pierce > 0) arrow.setPierceLevel((byte) pierce);
            if (extra || player.getAbilities().instabuild) arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
            arrow.getPersistentData().putBoolean(AbyssCrystalSpineItem.TIDE_ARROW_KEY, true);
            if (bonus > 0) arrow.getPersistentData().putInt(TidebreakerHammerCoreItem.ARROW_DAMAGE_KEY, bonus);
            projectile = arrow;
        }
        Vec3 up = player.getUpVector(1.0F);
        Vector3f direction = player.getViewVector(1.0F).toVector3f().rotate(
            new Quaternionf().setAngleAxis(Math.toRadians(angle), up.x, up.y, up.z));
        projectile.shoot(direction.x, direction.y, direction.z, firework ? 1.6F : 3.15F, 1.0F);
        weapon.hurtAndBreak(firework ? 3 : 1, player, e -> e.broadcastBreakEvent(hand));
        level.addFreshEntity(projectile);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CROSSBOW_SHOOT,
            SoundSource.PLAYERS, 1.0F, extra ? 1.0F / (level.random.nextFloat() * 0.5F + 1.8F) + 0.53F : 1.0F);
    }
    private static void showAmmo(Player player, ItemStack stack) {
        player.displayClientMessage(Component.translatable("item.hp_end_expansion.tide_crossbow.ammo", loadedRounds(stack), capacity(player)), true);
    }
    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, level, lines, flag);
        lines.add(Component.translatable("item.hp_end_expansion.tide_crossbow.loaded", loadedRounds(stack)).withStyle(ChatFormatting.AQUA));
        lines.add(Component.translatable("item.hp_end_expansion.tide_crossbow.controls").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("item.hp_end_expansion.tide_crossbow.arrow_immunity").withStyle(ChatFormatting.AQUA));
    }
    @Override public Component getName(ItemStack stack) {
        return Component.translatable(getDescriptionId(stack)).withStyle(ChatFormatting.AQUA);
    }
}
