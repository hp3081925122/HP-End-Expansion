package org.hp.hp_end_expansion.entity.starwreck;

import com.mojang.logging.LogUtils;
import javax.annotation.Nullable;
import java.util.Comparator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.hp.hp_end_expansion.item.StarCoreChestplateItem;
import org.hp.hp_end_expansion.registry.ModParticles;
import org.hp.hp_end_expansion.registry.ModStarwreck;
import org.hp.hp_end_expansion.registry.StarwreckEntities;
import org.hp.hp_end_expansion.item.StarBowItem;
import org.slf4j.Logger;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * 星晶矢：群星之弓射出的箭。伤害、穿透、拾取都和原版箭一样，只换了外观和特效：
 * 金色星晶矢身 + 发光层、矢尖四芒星、身后一条拖尾，飞行时掉星火，命中时迸一团星屑。
 * 拾取回来的是星晶碎片。
 */
public class StarBoltEntity extends AbstractArrow {
    /** 客户端记的最近几拍位置，给拖尾用。trail[0] 是最新的。 */
    public static final int TRAIL = 16;
    private static final double HOMING_RANGE = 24.0D;
    private static final double HOMING_TURN = 0.25D;
    private static final double HOMING_MIN_SPEED = 0.6D;
    private static final Logger LOGGER = LogUtils.getLogger();
    public final Vec3[] trail = new Vec3[TRAIL];
    public int trailCount;
    private boolean homing;
    private int homingTargetId = -1;
    private boolean unstableRemnantTriggered;
    private boolean skyEyeDamageReduced;
    private boolean homingMomentumRecovered;
    private int chestplateBounceCount;
    private final IntOpenHashSet chestplateHitEntityIds = new IntOpenHashSet();

    public StarBoltEntity(EntityType<? extends StarBoltEntity> type, Level level) {
        super(type, level);
    }

    public StarBoltEntity(Level level, LivingEntity owner, ItemStack ammo, @Nullable ItemStack weapon) {
        super(StarwreckEntities.STAR_BOLT.get(), owner, level, ammo, weapon);
    }

    @Override protected ItemStack getDefaultPickupItem() {
        return new ItemStack(ModStarwreck.STAR_CRYSTAL_SHARD.get());
    }

    @Override protected double getDefaultGravity() {
        if (getOwner() instanceof Player player && StarBowItem.hasSkyrenderHorn(player)) return 0.0D;
        return super.getDefaultGravity();
    }

    @Override protected SoundEvent getDefaultHitGroundSoundEvent() {
        return SoundEvents.AMETHYST_BLOCK_HIT;
    }

    public boolean stuck() { return inGround; }

    public void setHoming(boolean homing) {
        this.homing = homing;
    }

    @Override public void tick() {
        if (!level().isClientSide && homing && !inGround) updateHoming();
        // 原版箭在客户端 tick 里只要是暴击就每拍撒 4 个 CRIT 粒子，拉满弓必暴击，会盖住星晶矢自己的拖尾。
        // 客户端这一拍先把暴击标记临时清掉再放回，伤害在服务端算，不受影响；渲染读到的仍是真实标记。
        boolean crit = level().isClientSide && isCritArrow();
        if (crit) setCritArrow(false);
        super.tick();
        if (crit) setCritArrow(true);
        if (!level().isClientSide) return;
        if (inGround) {
            // 扎在地上：拖尾一拍一拍缩回去，偶尔闪一下
            if (trailCount > 0) trailCount--;
            if (tickCount % 12 == 0) level().addParticle(ModParticles.BEARER_GLINT.get(), getX(), getY(), getZ(), 0, 0.01, 0);
            return;
        }
        System.arraycopy(trail, 0, trail, 1, TRAIL - 1);
        trail[0] = position();
        trailCount = Math.min(TRAIL, trailCount + 1);
        Vec3 v = getDeltaMovement();
        if (random.nextFloat() < (isCritArrow() ? 0.9F : 0.55F)) {
            level().addParticle(ModParticles.BEARER_GLINT.get(), getX() - v.x * random.nextFloat(), getY() - v.y * random.nextFloat(),
                getZ() - v.z * random.nextFloat(), 0, 0, 0);
        }
        if (random.nextFloat() < 0.3F) {
            level().addParticle(ModParticles.STAR_EMBER.get(), getX(), getY(), getZ(),
                (random.nextFloat() - 0.5F) * 0.04, 0.02 + random.nextFloat() * 0.03, (random.nextFloat() - 0.5F) * 0.04);
        }
    }

    private void updateHoming() {
        Vec3 velocity = getDeltaMovement();
        double speed = velocity.length();
        if (speed < HOMING_MIN_SPEED) {
            Vec3 direction = speed > 1.0E-6D ? velocity.normalize() : getLookAngle();
            if (direction.lengthSqr() < 1.0E-6D) direction = new Vec3(0.0D, 0.0D, 1.0D);
            velocity = direction.scale(HOMING_MIN_SPEED);
            speed = HOMING_MIN_SPEED;
            if (!homingMomentumRecovered) {
                LOGGER.debug("Recovered stalled homing star bolt momentum: entity={}, speed={}", getId(), HOMING_MIN_SPEED);
                homingMomentumRecovered = true;
            }
        }
        LivingEntity target = homingTarget();
        if (target == null) {
            target = findHomingTarget();
            homingTargetId = target == null ? -1 : target.getId();
        }
        if (target == null) {
            setDeltaMovement(velocity);
            return;
        }
        Vec3 desired = target.getEyePosition().subtract(position()).normalize().scale(speed);
        setDeltaMovement(velocity.scale(1.0D - HOMING_TURN).add(desired.scale(HOMING_TURN)));
    }

    private LivingEntity homingTarget() {
        if (homingTargetId < 0) return null;
        Entity entity = level().getEntity(homingTargetId);
        if (entity instanceof LivingEntity living && validHomingTarget(living)
            && distanceToSqr(living) <= HOMING_RANGE * HOMING_RANGE * 4.0D && hasHomingLineOfSight(living)) {
            return living;
        }
        homingTargetId = -1;
        return null;
    }

    private LivingEntity findHomingTarget() {
        Entity owner = getOwner();
        return level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(HOMING_RANGE), this::validHomingTarget)
            .stream().filter(entity -> owner == null || !entity.isAlliedTo(owner))
            .filter(this::hasHomingLineOfSight)
            .min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
    }

    private boolean hasHomingLineOfSight(LivingEntity target) {
        Vec3 from = position();
        Vec3 to = target.getEyePosition();
        return level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
    }

    private boolean validHomingTarget(LivingEntity entity) {
        Entity owner = getOwner();
        return entity.isAlive() && !entity.isSpectator() && entity != owner && !entity.isInvulnerable()
            && !chestplateHitEntityIds.contains(entity.getId());
    }

    @Override protected void onHitEntity(EntityHitResult result) {
        if (hasSkyEye()) {
            clearInvulnerability(result.getEntity());
            if (!skyEyeDamageReduced) {
                setBaseDamage(getBaseDamage() * 0.25D);
                skyEyeDamageReduced = true;
            }
        }
        if (triggerUnstableRemnantStar()) {
            burst(result.getLocation());
            if (level() instanceof ServerLevel server) {
                Vec3 at = result.getLocation();
                FallingStarEntity.spawn(server, at.add(0, 20, 0), at, true);
            }
        }
        boolean bounce = !level().isClientSide && hasStarCoreChestplate() && chestplateBounceCount < 2;
        if (bounce) rememberChestplateHit(result.getEntity());
        double bounceSpeed = Math.max(getDeltaMovement().length(), 1.0D);
        super.onHitEntity(result);
        if (!bounce) return;
        LivingEntity target = findChestplateBounceTarget(result.getEntity());
        if (target == null) {
            return;
        }
        chestplateBounceCount++;
        spawnBounceProjectile(target, result.getLocation(), bounceSpeed);
    }

    @Override protected void onHitBlock(BlockHitResult result) {
        if (triggerUnstableRemnantStar()) {
            burst(result.getLocation());
        }
        super.onHitBlock(result);
    }

    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        unstableRemnantTriggered = tag.getBoolean("UnstableRemnantTriggered");
        chestplateBounceCount = tag.getInt("ChestplateBounceCount");
        chestplateHitEntityIds.clear();
        for (int id : tag.getIntArray("ChestplateHitEntityIds")) chestplateHitEntityIds.add(id);
    }

    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("UnstableRemnantTriggered", unstableRemnantTriggered);
        tag.putInt("ChestplateBounceCount", chestplateBounceCount);
        tag.putIntArray("ChestplateHitEntityIds", chestplateHitEntityIds.toIntArray());
    }

    private boolean hasStarCoreChestplate() {
        return getOwner() instanceof Player player
            && player.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof StarCoreChestplateItem;
    }

    private void rememberChestplateHit(Entity entity) {
        chestplateHitEntityIds.add(entity.getId());
        if (entity instanceof SkyrenderPart part) chestplateHitEntityIds.add(part.getParent().getId());
    }

    private LivingEntity findChestplateBounceTarget(Entity source) {
        Entity owner = getOwner();
        AABB area = source.getBoundingBox().inflate(16.0D);
        return level().getEntitiesOfClass(LivingEntity.class, area, candidate -> candidate.isAlive()
                && !candidate.isSpectator()
                && candidate != owner
                && !candidate.isInvulnerable()
                && !chestplateHitEntityIds.contains(candidate.getId())
                && candidate.distanceToSqr(source) <= 16.0D * 16.0D
                && (owner == null || !candidate.isAlliedTo(owner)))
            .stream().min(Comparator.comparingDouble(candidate -> candidate.distanceToSqr(source))).orElse(null);
    }

    private void spawnBounceProjectile(LivingEntity target, Vec3 from, double speed) {
        Vec3 direction = target.getEyePosition().subtract(from);
        if (direction.lengthSqr() < 1.0E-6D || !(level() instanceof ServerLevel server) || !(getOwner() instanceof LivingEntity owner)) return;
        direction = direction.normalize();
        StarBoltEntity bounced = new StarBoltEntity(server, owner, getPickupItemStackOrigin().copyWithCount(1), getWeaponItem());
        bounced.setBaseDamage(getBaseDamage());
        bounced.setCritArrow(isCritArrow());
        bounced.setHoming(homing);
        bounced.homingTargetId = target.getId();
        bounced.pickup = pickup;
        bounced.unstableRemnantTriggered = unstableRemnantTriggered;
        bounced.skyEyeDamageReduced = skyEyeDamageReduced;
        bounced.chestplateBounceCount = chestplateBounceCount;
        for (int id : chestplateHitEntityIds) bounced.chestplateHitEntityIds.add(id);
        bounced.setPos(from.add(direction.scale(0.15D)));
        bounced.shoot(direction.x, direction.y, direction.z, (float) speed, 0.0F);
        server.addFreshEntity(bounced);
    }

    private boolean hasUnstableRemnantStar() {
        if (!(getOwner() instanceof Player player)) return false;
        return CuriosApi.getCuriosInventory(player)
            .map(handler -> handler.isEquipped(StarwreckEntities.UNSTABLE_REMNANT_STAR.get()))
            .orElse(false);
    }

    private boolean hasSkyEye() {
        if (!(getOwner() instanceof Player player)) return false;
        return CuriosApi.getCuriosInventory(player)
            .map(handler -> handler.isEquipped(StarwreckEntities.SKY_EYE.get()))
            .orElse(false);
    }

    private static void clearInvulnerability(Entity entity) {
        if (entity instanceof LivingEntity living) living.invulnerableTime = 0;
        if (entity instanceof SkyrenderPart part) part.getParent().invulnerableTime = 0;
    }

    private boolean triggerUnstableRemnantStar() {
        if (unstableRemnantTriggered || !hasUnstableRemnantStar()) return false;
        unstableRemnantTriggered = true;
        return true;
    }

    private void burst(Vec3 at) {
        if (!(level() instanceof ServerLevel sl)) return;
        int n = isCritArrow() ? 18 : 10;
        sl.sendParticles(ModParticles.BEARER_GLINT.get(), at.x, at.y, at.z, n, 0.18, 0.18, 0.18, 0.06);
        sl.sendParticles(ModParticles.STAR_EMBER.get(), at.x, at.y, at.z, n / 2, 0.12, 0.12, 0.12, 0.03);
        sl.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 0.6F, 1.5F + random.nextFloat() * 0.3F);
    }
}
