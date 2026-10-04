package org.hp.hp_end_expansion.item;

import java.util.List;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.EventHooks;
import org.hp.hp_end_expansion.entity.starwreck.StarBoltEntity;
import org.hp.hp_end_expansion.registry.ModStarwreck;
import org.hp.hp_end_expansion.registry.StarwreckEntities;
import top.theillusivec4.curios.api.CuriosApi;

public final class StarBowItem extends BowItem {
    private static final int FULL_CHARGE_TICKS = 20;
    private static final int MAX_HORN_CHARGE_TICKS = FULL_CHARGE_TICKS * 3;

    public StarBowItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(getDescriptionId(stack)).withStyle(ChatFormatting.GOLD);
    }

    @Override
    public Predicate<ItemStack> getAllSupportedProjectiles() {
        return stack -> stack.is(ModStarwreck.STAR_CRYSTAL_SHARD.get());
    }

    @Override
    public ItemStack getDefaultCreativeAmmo(@Nullable Player player, ItemStack projectileWeaponItem) {
        return ModStarwreck.STAR_CRYSTAL_SHARD.get().getDefaultInstance();
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseDuration) {
        super.onUseTick(level, entity, stack, remainingUseDuration);
        if (level.isClientSide) org.hp.hp_end_expansion.client.StarBowChargeFx.tick(level, entity);
        if (entity instanceof Player player && hasSkyEye(player) && getChargeTicks(entity) >= FULL_CHARGE_TICKS) {
            player.releaseUsingItem();
        }
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!(entity instanceof Player player)) {
            super.releaseUsing(stack, level, entity, timeLeft);
            return;
        }
        boolean holyStar = hasHolyStar(player);
        boolean infinity = hasInfinity(player, stack);
        boolean starcallPendant = hasStarcallPendant(player);
        boolean skyEye = hasSkyEye(player);
        boolean skyrenderHorn = hasSkyrenderHorn(player);
        if (!holyStar && !infinity && !starcallPendant && !skyEye && !skyrenderHorn) {
            super.releaseUsing(stack, level, entity, timeLeft);
            return;
        }
        ItemStack ammo = player.getProjectile(stack);
        if (ammo.isEmpty()) return;
        int charge = getUseDuration(stack, entity) - timeLeft;
        charge = EventHooks.onArrowLoose(stack, level, player, charge, true);
        if (charge < 0) return;
        float effectiveCharge = getEffectiveChargeTicks(player, charge);
        float power = getPowerForTime((int) Math.min(effectiveCharge, FULL_CHARGE_TICKS));
        if (power < 0.1F) return;
        if (level instanceof ServerLevel server) {
            if (starcallPendant) {
                shootPendantArrows(server, player, player.getUsedItemHand(), stack, ammo, power * 3.0F, power == 1.0F,
                    holyStar || infinity, getDamageMultiplier(player, effectiveCharge));
            } else {
                ItemStack ammoInput = holyStar || infinity ? ammo.copyWithCount(ammo.getCount() + 1) : ammo;
                List<ItemStack> projectiles = draw(stack, ammoInput, player);
                if (!projectiles.isEmpty()) {
                    shoot(server, player, player.getUsedItemHand(), stack, projectiles, power * 3.0F, 1.0F, power == 1.0F, null);
                }
            }
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS,
            1.0F, 1.0F / (level.getRandom().nextFloat() * 0.4F + 1.2F) + power * 0.5F);
        player.awardStat(Stats.ITEM_USED.get(this));
    }

    private static boolean hasHolyStar(Player player) {
        return CuriosApi.getCuriosInventory(player)
            .map(handler -> handler.isEquipped(StarwreckEntities.HOLY_STAR.get()))
            .orElse(false);
    }

    public static boolean hasSkyEye(Player player) {
        return CuriosApi.getCuriosInventory(player)
            .map(handler -> handler.isEquipped(StarwreckEntities.SKY_EYE.get()))
            .orElse(false);
    }

    public static boolean hasSkyrenderHorn(Player player) {
        return CuriosApi.getCuriosInventory(player)
            .map(handler -> handler.isEquipped(StarwreckEntities.SKYRENDER_HORN.get()))
            .orElse(false);
    }

    public static float getChargeTicks(LivingEntity entity) {
        return getChargeTicks(entity, entity.getTicksUsingItem());
    }

    /** 按给定的实际拉弓拍数算有效蓄力（特效用它取上一拍的蓄力，封顶后不能靠比例倒推）。 */
    public static float getChargeTicks(LivingEntity entity, int ticks) {
        return entity instanceof Player player ? getEffectiveChargeTicks(player, ticks) : ticks;
    }

    public static float getPullProgress(ItemStack stack, LivingEntity entity) {
        if (entity == null || entity.getUseItem() != stack) return 0.0F;
        // 拉弓贴图：满一次之后一直保持拉满，多段蓄力时不回到初始拉弓画面（分段进度只给蓄力特效用）
        return Math.min(1.0F, getChargeTicks(entity) / FULL_CHARGE_TICKS);
    }

    public static float getChargeProgress(LivingEntity entity) {
        float ticks = getChargeTicks(entity);
        if (entity instanceof Player player && hasSkyrenderHorn(player) && ticks > FULL_CHARGE_TICKS) {
            float cycle = ticks % FULL_CHARGE_TICKS;
            return cycle == 0.0F ? 1.0F : cycle / FULL_CHARGE_TICKS;
        }
        return Math.min(1.0F, ticks / FULL_CHARGE_TICKS);
    }

    private static boolean hasStarcallPendant(Player player) {
        return CuriosApi.getCuriosInventory(player)
            .map(handler -> handler.isEquipped(StarwreckEntities.STARCALL_PENDANT.get()))
            .orElse(false);
    }

    private static float getEffectiveChargeTicks(Player player, int ticks) {
        float speed = 1.0F;
        if (hasSkyEye(player)) speed *= 2.0F;
        if (hasSkyrenderHorn(player)) speed *= 0.5F;
        float effectiveCharge = ticks * speed;
        return hasSkyrenderHorn(player) ? Math.min(MAX_HORN_CHARGE_TICKS, effectiveCharge) : effectiveCharge;
    }

    private static float getDamageMultiplier(Player player, float effectiveCharge) {
        if (!hasSkyrenderHorn(player)) return 1.0F;
        float charge = effectiveCharge / FULL_CHARGE_TICKS;
        return charge * charge;
    }

    private static void shootPendantArrows(ServerLevel level, Player player, InteractionHand hand, ItemStack weapon,
        ItemStack ammo, float velocity, boolean crit, boolean freeAmmo, float damageMultiplier) {
        float reducedVelocity = velocity * 0.75F;
        shootPendantArrow(level, player, hand, weapon, ammo, reducedVelocity, crit, 0, false, damageMultiplier);
        shootPendantArrow(level, player, hand, weapon, ammo, reducedVelocity, crit, -8, true, damageMultiplier);
        shootPendantArrow(level, player, hand, weapon, ammo, reducedVelocity, crit, 8, true, damageMultiplier);
        if (!freeAmmo && !player.hasInfiniteMaterials()) ammo.shrink(1);
    }

    private static void shootPendantArrow(ServerLevel level, Player player, InteractionHand hand, ItemStack weapon,
        ItemStack ammo, float velocity, boolean crit, float yawOffset, boolean extra, float damageMultiplier) {
        StarBoltEntity bolt = new StarBoltEntity(level, player, ammo.copyWithCount(1), weapon);
        if (crit) bolt.setCritArrow(true);
        if (damageMultiplier != 1.0F) bolt.setBaseDamage(bolt.getBaseDamage() * damageMultiplier);
        if (extra) {
            bolt.pickup = AbstractArrow.Pickup.DISALLOWED;
            bolt.setHoming(true);
            bolt.setBaseDamage(bolt.getBaseDamage() * 0.5D);
        }
        bolt.shootFromRotation(player, player.getXRot(), player.getYRot() + yawOffset, 0, velocity, 1.0F);
        level.addFreshEntity(bolt);
        weapon.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
    }

    private static boolean hasInfinity(Player player, ItemStack stack) {
        var infinity = player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.INFINITY);
        return EnchantmentHelper.getItemEnchantmentLevel(infinity, stack) > 0;
    }

    // 星晶碎片不是箭，原版会退回成普通箭；这里射出自己的星晶矢
    @Override
    protected Projectile createProjectile(Level level, LivingEntity shooter, ItemStack weapon, ItemStack ammo, boolean isCrit) {
        StarBoltEntity bolt = new StarBoltEntity(level, shooter, ammo.copyWithCount(1), weapon);
        if (isCrit) bolt.setCritArrow(true);
        return customArrow(bolt, ammo, weapon);
    }

    @Override
    protected void shootProjectile(LivingEntity shooter, Projectile projectile, int index, float velocity, float inaccuracy,
        float angle, LivingEntity target) {
        super.shootProjectile(shooter, projectile, index, velocity, inaccuracy, angle, target);
        if (projectile instanceof StarBoltEntity bolt && shooter instanceof Player player && hasSkyrenderHorn(player)) {
            bolt.setBaseDamage(bolt.getBaseDamage() * getDamageMultiplier(player, getChargeTicks(player)));
        }
    }
}
