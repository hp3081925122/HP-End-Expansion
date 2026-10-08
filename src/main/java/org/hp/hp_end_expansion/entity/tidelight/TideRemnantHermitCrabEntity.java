package org.hp.hp_end_expansion.entity.tidelight;

import com.mojang.logging.LogUtils;
import java.util.EnumSet;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.instance.SingletonAnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import org.slf4j.Logger;

/**
 * 潮骸寄居蟹：中立精英。被打后记仇 30 秒并叫上 12 格内的同类，仇恨结束缩壳回血。
 * 技能：巨钳下砸、钳握重摔（两个独立技能）、潮压水箭三连发、大招骸潮炮。
 * 所有命中时刻和位置都取自动画采样（Blockbench 每 tick 读骨骼位置），服务端按同一时间轴结算。
 */
public final class TideRemnantHermitCrabEntity extends PathfinderMob implements GeoEntity {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final byte NONE = 0, CLAW = 1, GRAB = 2, GRAB_MISS = 3, STAGGER = 4, BOLT = 5, CANNON = 6, STUN = 7;
    private static final int[] DURATION = {0, 28, 52, 16, 12, 40, 120, 30};
    private static final String[] ANIM = {"", "claw_slam", "grab_slam", "grab_miss", "stagger", "water_bolt", "tide_cannon", "stun"};

    // 巨钳下砸
    public static final int CLAW_HIT = 16;
    private static final double CLAW_RADIUS = 1.6;
    private static final float CLAW_DAMAGE = 9;
    // 钳握重摔
    public static final int GRAB_TICK = 8, GRAB_SLAM = 34;
    private static final float GRAB_DAMAGE = 8, GRAB_SPLASH = 4, BREAK_FREE = 6;
    // 潮压水箭
    private static final int[] BOLT_SHOTS = {14, 20, 26};
    // 骸潮炮：A 归壳 0-20，B 吸潮 20-40，C 扫射 40-90，D 破壳 90-120，双钳第 104 tick 砸地
    public static final int CANNON_CHARGE = 20, CANNON_SWEEP = 40, CANNON_SWEEP_END = 90, CANNON_BURST = 104;
    public static final double BEAM_LENGTH = 14;
    private static final float BEAM_DAMAGE = 3, CHARGE_INTERRUPT = 10;

    private static final int CLAW_CD = 80, GRAB_CD = 160, BOLT_CD = 120, CANNON_CD = 600, GLOBAL_CD = 30;
    private static final int ANGER_TICKS = 600, REST_TICKS = 100;
    private static final int SKULL_RECOVERY_TICKS = 100;
    private static final double BASE_ARMOR = 12.0D;
    private static final double BASE_MOVEMENT_SPEED = 0.2D;
    private static final double TRACK_RANGE = 24;

    /**
     * 钳握重摔里 vfx_grip（左钳钳口）第 8-34 tick 的位置，Blockbench 像素坐标 {x, y, z}：
     * x 正向是蟹的右侧，z 负向是前方。被抓目标每 tick 跟着这个点走。
     */
    private static final float[][] GRIP = {
        {-8.64F, 8.59F, -31.96F}, {-8.87F, 9.99F, -32.01F}, {-9.22F, 12.35F, -31.91F}, {-9.54F, 14.7F, -31.59F},
        {-9.82F, 17.04F, -31.03F}, {-10.07F, 19.32F, -30.25F}, {-10.28F, 21.52F, -29.24F}, {-10.44F, 23.61F, -28.02F},
        {-10.56F, 25.56F, -26.58F}, {-10.66F, 26.82F, -25.16F}, {-10.65F, 27.95F, -23.64F}, {-10.52F, 28.92F, -22.03F},
        {-10.27F, 29.75F, -20.37F}, {-9.92F, 30.4F, -18.66F}, {-9.46F, 30.88F, -16.94F}, {-8.9F, 31.18F, -15.23F},
        {-8.25F, 31.3F, -13.54F}, {-9.93F, 31.66F, -13.79F}, {-6.68F, 30.82F, -13.09F}, {-9.36F, 31.55F, -13.73F},
        {-8.24F, 31.59F, -12.35F}, {-7.11F, 31.45F, -11.01F}, {-5.99F, 31.16F, -9.72F}, {-10.61F, 30F, -18.37F},
        {-13.38F, 23.74F, -26.33F}, {-13.3F, 14.06F, -31.04F}, {-10.38F, 3.81F, -31.26F}
    };

    private static final EntityDataAccessor<Byte> SKILL = SynchedEntityData.defineId(TideRemnantHermitCrabEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> RESTING = SynchedEntityData.defineId(TideRemnantHermitCrabEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> SKULL_BROKEN = SynchedEntityData.defineId(TideRemnantHermitCrabEntity.class, EntityDataSerializers.BOOLEAN);

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.tide_remnant_hermit_crab.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.tide_remnant_hermit_crab.walk");
    private static final RawAnimation REST = RawAnimation.begin().thenLoop("animation.tide_remnant_hermit_crab.rest");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold("animation.tide_remnant_hermit_crab.death");
    private static final RawAnimation[] SKILL_ANIMS = new RawAnimation[ANIM.length];
    static {
        for (int i = 1; i < ANIM.length; i++) SKILL_ANIMS[i] = RawAnimation.begin().thenPlay("animation.tide_remnant_hermit_crab." + ANIM[i]);
    }

    private final AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);
    private int skillTicks, globalCd, clawCd, grabCd, boltCd, cannonCd;
    private int angerTicks, restTicks;
    private int skullRecoveryTicks;
    /** 这一次缩壳里、减伤前受到的伤害合计。离开缩壳或头骨碎掉后清零。 */
    private float shellDamage;
    private double skullRestoreArmor = BASE_ARMOR;
    private double skullRestoreSpeed = BASE_MOVEMENT_SPEED;
    @Nullable private LivingEntity fleeingFrom;
    private Vec3 fleeDirection = new Vec3(0, 0, 1);
    private float skillYaw;
    private boolean ultUnlocked;
    @Nullable private LivingEntity grabbed;
    private float grabDamage;
    // 客户端：当前技能开始的 tickCount，用于对齐动画、光柱和震屏
    private int clientSkillStart;
    private int animResetSeen = -1;

    public TideRemnantHermitCrabEntity(EntityType<? extends TideRemnantHermitCrabEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, 40.0)
            .add(Attributes.ARMOR, 12.0)
            .add(Attributes.KNOCKBACK_RESISTANCE, 0.6)
            .add(Attributes.MOVEMENT_SPEED, 0.2)
            .add(Attributes.FOLLOW_RANGE, TRACK_RANGE)
            .add(Attributes.ATTACK_DAMAGE, CLAW_DAMAGE)
            .add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SKILL, NONE);
        builder.define(RESTING, false);
        builder.define(SKULL_BROKEN, false);
    }

    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (SKILL.equals(key)) clientSkillStart = tickCount;
    }

    public byte getSkill() { return entityData.get(SKILL); }
    public boolean isResting() { return entityData.get(RESTING); }
    public boolean isSkullBroken() { return entityData.get(SKULL_BROKEN); }
    /** 客户端：当前技能已经进行的 tick（含插值）。 */
    public float getSkillAge(float partialTick) { return tickCount - clientSkillStart + partialTick; }

    /** 骸潮炮扫射角：第 40 tick 在左 60°，匀速扫到第 90 tick 的右 60°。正值是蟹的右侧。 */
    public static float sweepAngle(float age) {
        return -60 + 120 * Mth.clamp((age - CANNON_SWEEP) / (CANNON_SWEEP_END - CANNON_SWEEP), 0, 1);
    }

    /** 以 yaw 为朝向时，sweep 角方向的水平单位向量。 */
    public static Vec3 beamDirection(float bodyYaw, float sweep) {
        double y = Math.toRadians(bodyYaw);
        double a = Math.toRadians(sweep);
        double fx = -Math.sin(y), fz = Math.cos(y), rx = -Math.cos(y), rz = -Math.sin(y);
        return new Vec3(fx * Math.cos(a) + rx * Math.sin(a), 0, fz * Math.cos(a) + rz * Math.sin(a));
    }

    /** 技能伤害只打其他生物：同类、创造和旁观模式的玩家除外。 */
    public static boolean canHit(Entity e) {
        return e instanceof LivingEntity living && living.isAlive() && !(e instanceof TideRemnantHermitCrabEntity)
            && !(e instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new FleeWhileSkullBrokenGoal());
        goalSelector.addGoal(2, new HoldStillGoal());
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8) {
            @Override public boolean canUse() { return getTarget() == null && !isResting() && !isSkullBroken() && super.canUse(); }
            @Override public boolean canContinueToUse() { return getTarget() == null && !isResting() && !isSkullBroken() && super.canContinueToUse(); }
        });
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8) {
            @Override public boolean canUse() { return !isSkullBroken() && super.canUse(); }
            @Override public boolean canContinueToUse() { return !isSkullBroken() && super.canContinueToUse(); }
        });
        goalSelector.addGoal(7, new RandomLookAroundGoal(this) {
            @Override public boolean canUse() { return !isSkullBroken() && super.canUse(); }
            @Override public boolean canContinueToUse() { return !isSkullBroken() && super.canContinueToUse(); }
        });
    }

    // ---------------- 中立与受伤 ----------------

    @Override public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide) return super.hurt(source, amount);
        Entity attacker = source.getEntity();
        if (attacker instanceof TideRemnantHermitCrabEntity) return false;
        float raw = amount;
        boolean shelled = isShellClosed();
        if (!shelled) shellDamage = 0;
        if (shelled) amount *= 0.5F;
        byte skill = getSkill();
        boolean hit = super.hurt(source, amount);
        if (hit && shelled) shellDamage += raw;
        boolean shatter = shelled && shellDamage > 20.0F;
        if (!hit || !isAlive()) return hit;
        if (shatter) breakSkull(attacker instanceof LivingEntity living ? living : null, raw);
        else if (attacker instanceof LivingEntity living && canHit(living) && !isSkullBroken()) anger(living, true);
        if (shatter) return hit;
        if (skill == GRAB && grabbed != null && skillTicks < GRAB_SLAM) {
            grabDamage += amount;
            if (grabDamage >= BREAK_FREE) {
                releaseGrab();
                startSkill(STAGGER);
            }
        } else if (skill == CANNON && skillTicks >= CANNON_CHARGE && skillTicks < CANNON_SWEEP && raw >= CHARGE_INTERRUPT) {
            playSound(SoundEvents.SHIELD_BREAK, 1.2F, 0.6F);
            startSkill(STUN);
        }
        return hit;
    }

    /** 缩壳回血，以及骸潮炮破壳前的归壳姿态。头骨已碎时不再算缩壳。 */
    private boolean isShellClosed() {
        if (isSkullBroken()) return false;
        if (isResting()) return true;
        return getSkill() == CANNON && skillTicks < CANNON_SWEEP_END;
    }

    private void breakSkull(@Nullable LivingEntity attacker, float damage) {
        var armor = getAttribute(Attributes.ARMOR);
        var speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (armor != null) {
            skullRestoreArmor = armor.getBaseValue();
            armor.setBaseValue(0.0D);
        }
        if (speed != null) {
            skullRestoreSpeed = speed.getBaseValue();
            speed.setBaseValue(Math.max(skullRestoreSpeed * 2.5D, 0.5D));
        }
        skullRecoveryTicks = SKULL_RECOVERY_TICKS;
        shellDamage = 0;
        entityData.set(SKULL_BROKEN, true);
        fleeingFrom = attacker;
        Vec3 away = attacker == null ? Vec3.ZERO : position().subtract(attacker.position()).multiply(1, 0, 1);
        if (away.lengthSqr() > 1.0E-4D) fleeDirection = away.normalize();
        else fleeDirection = new Vec3(Math.cos(random.nextDouble() * Math.PI * 2), 0, Math.sin(random.nextDouble() * Math.PI * 2));
        setTarget(null);
        angerTicks = 0;
        restTicks = 0;
        entityData.set(RESTING, false);
        releaseGrab();
        entityData.set(SKILL, NONE);
        skillTicks = 0;
        globalCd = 0;
        getNavigation().stop();
        playSound(SoundEvents.BONE_BLOCK_BREAK, 1.2F, 0.8F);
        if (level() instanceof ServerLevel server) {
            Vec3 skull = position().add(0, 1.35, 0);
            server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.defaultBlockState()), skull.x, skull.y, skull.z, 20, 0.28, 0.2, 0.28, 0.08);
            server.sendParticles(ParticleTypes.CRIT, skull.x, skull.y, skull.z, 10, 0.22, 0.18, 0.22, 0.08);
        }
        LOGGER.debug("Tide Remnant Hermit Crab {} skull shattered by damage {}", getId(), damage);
    }

    private void restoreSkull() {
        entityData.set(SKULL_BROKEN, false);
        skullRecoveryTicks = 0;
        var armor = getAttribute(Attributes.ARMOR);
        var speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (armor != null) armor.setBaseValue(skullRestoreArmor);
        if (speed != null) speed.setBaseValue(skullRestoreSpeed);
        fleeingFrom = null;
        getNavigation().stop();
        LOGGER.debug("Tide Remnant Hermit Crab {} skull and armor restored", getId());
    }

    private void anger(LivingEntity attacker, boolean alertOthers) {
        setTarget(attacker);
        angerTicks = ANGER_TICKS;
        if (!alertOthers) return;
        for (TideRemnantHermitCrabEntity kin : level().getEntitiesOfClass(TideRemnantHermitCrabEntity.class, getBoundingBox().inflate(12), e -> e != this && e.isAlive()))
            kin.anger(attacker, false);
    }

    private void calmDown() {
        boolean wasAngry = getTarget() != null;
        setTarget(null);
        angerTicks = 0;
        if (wasAngry) rest(REST_TICKS);
    }

    private void rest(int ticks) {
        entityData.set(RESTING, true);
        restTicks = ticks;
        shellDamage = 0;
        getNavigation().stop();
    }

    // ---------------- AI 主循环 ----------------

    @Override protected void customServerAiStep() {
        super.customServerAiStep();
        if (isSkullBroken()) {
            if (skullRecoveryTicks > 0 && --skullRecoveryTicks == 0) restoreSkull();
            if (isSkullBroken()) {
                setTarget(null);
                angerTicks = 0;
                return;
            }
        }
        if (clawCd > 0) clawCd--;
        if (grabCd > 0) grabCd--;
        if (boltCd > 0) boltCd--;
        if (cannonCd > 0) cannonCd--;
        if (!ultUnlocked && getHealth() < getMaxHealth() * 0.5F) { ultUnlocked = true; cannonCd = 0; }

        LivingEntity target = getTarget();
        if (getSkill() != NONE) { tickSkill(target); return; }
        if (globalCd > 0) globalCd--;

        if (target != null) {
            if (angerTicks > 0) angerTicks--;
            if (!isResting() && (!canHit(target) || angerTicks <= 0 || distanceToSqr(target) > TRACK_RANGE * TRACK_RANGE)) { calmDown(); target = null; }
        }

        if (isResting()) {
            if (--restTicks <= 0) { entityData.set(RESTING, false); shellDamage = 0; }
            else if (restTicks % 20 == 0 && getHealth() < getMaxHealth()) heal(1);
            return;
        }
        if (target == null) {
            if (random.nextInt(1600) == 0) rest(REST_TICKS);
            return;
        }

        getLookControl().setLookAt(target, 30, 30);
        if (globalCd <= 0 && chooseSkill(target)) return;
        boolean peaceful = level().getDifficulty() == Difficulty.PEACEFUL;
        if (!peaceful && distanceToSqr(target) > 2.4 * 2.4) {
            if (tickCount % 10 == 0 || getNavigation().isDone()) getNavigation().moveTo(target, 1.4);
        } else {
            getNavigation().stop();
        }
    }

    private boolean chooseSkill(LivingEntity target) {
        double dist = distanceTo(target);
        if (ultUnlocked && cannonCd <= 0 && dist <= 16 && hasLineOfSight(target)) { startSkill(CANNON); return true; }
        if (dist <= 3.2) {
            boolean claw = clawCd <= 0, grab = grabCd <= 0 && grabbable(target);
            if (claw && grab) { startSkill(random.nextBoolean() ? CLAW : GRAB); return true; }
            if (claw) { startSkill(CLAW); return true; }
            if (grab) { startSkill(GRAB); return true; }
        } else if (dist <= 12 && boltCd <= 0 && hasLineOfSight(target)) {
            startSkill(BOLT);
            return true;
        }
        return false;
    }

    // 只抓不超过玩家体型 1.5 倍、没在骑乘的目标
    private static boolean grabbable(LivingEntity e) {
        return !e.isPassenger() && !e.isVehicle() && e.getBbWidth() * e.getBbHeight() <= 0.6F * 1.8F * 1.5F;
    }

    private void startSkill(byte skill) {
        entityData.set(SKILL, skill);
        skillTicks = 0;
        getNavigation().stop();
        LivingEntity target = getTarget();
        if (target != null && skill != STAGGER && skill != STUN) skillYaw = yawTo(target);
        else skillYaw = getYRot();
        switch (skill) {
            case CLAW -> { clawCd = CLAW_CD; playSound(SoundEvents.RAVAGER_ATTACK, 0.8F, 1.4F); }
            case GRAB -> { grabCd = GRAB_CD; grabDamage = 0; }
            case BOLT -> boltCd = BOLT_CD;
            case CANNON -> { cannonCd = CANNON_CD; shellDamage = 0; playSound(SoundEvents.SHULKER_CLOSE, 1.5F, 0.6F); }
            default -> {}
        }
    }

    private void endSkill() {
        releaseGrab();
        entityData.set(SKILL, NONE);
        skillTicks = 0;
        globalCd = GLOBAL_CD;
    }

    private void tickSkill(@Nullable LivingEntity target) {
        skillTicks++;
        byte skill = getSkill();
        if (skill == CANNON && skillTicks >= CANNON_SWEEP_END) shellDamage = 0;
        // 起手阶段转向目标，出招前锁死朝向，给目标闪避的机会
        int trackUntil = switch (skill) { case CLAW -> 12; case GRAB -> 6; case BOLT -> 30; case CANNON -> 24; default -> 0; };
        if (target != null && target.isAlive() && skillTicks <= trackUntil) skillYaw = Mth.approachDegrees(skillYaw, yawTo(target), skill == BOLT ? 12 : 20);
        setYRot(skillYaw);
        yBodyRot = skillYaw;
        yHeadRot = skillYaw;

        switch (skill) {
            case CLAW -> tickClaw();
            case GRAB -> tickGrab(target);
            case BOLT -> tickBolt(target);
            case CANNON -> tickCannon();
            default -> {}
        }
        if (getSkill() == skill && skillTicks >= DURATION[skill]) {
            if (skill == GRAB_MISS) grabCd = GRAB_CD / 2;
            endSkill();
        }
    }

    // ---------------- ① 巨钳下砸 ----------------

    private void tickClaw() {
        if (level() instanceof ServerLevel server && skillTicks >= 6 && skillTicks < CLAW_HIT) {
            // 举钳蓄力：钳口（第 10-14 tick 实测在右 -0.6、高 1.9、前 1.07 格）往下滴水，第 15 tick 下砸时甩出一串水
            Vec3 claw = local(-0.6, 1.9, 1.07);
            server.sendParticles(ParticleTypes.FALLING_WATER, claw.x, claw.y, claw.z, 2, 0.2, 0.1, 0.2, 0);
            if (skillTicks == CLAW_HIT - 1) {
                Vec3 mid = local(-0.9, 1.25, 1.8);
                server.sendParticles(ParticleTypes.SPLASH, mid.x, mid.y, mid.z, 14, 0.3, 0.4, 0.3, 0.2);
            }
        }
        if (skillTicks != CLAW_HIT) return;
        // 第 16 tick 钳口落在前方 1.95 格、偏左 0.64 格
        Vec3 at = local(-0.64, 0, 1.95);
        for (LivingEntity victim : victims(at, CLAW_RADIUS, 1, 2)) {
            boolean blocking = victim instanceof Player p && p.isBlocking();
            if (victim.hurt(damageSources().mobAttack(this), CLAW_DAMAGE)) {
                Vec3 away = horizontalAway(victim.position(), at);
                victim.knockback(0.5, -away.x, -away.z);
                victim.setDeltaMovement(victim.getDeltaMovement().add(0, -0.3, 0));
                victim.hurtMarked = true;
            }
            if (blocking && victim instanceof Player p) disableShield(p);
        }
        impactFx(at, 1.4F, 20);
        if (level() instanceof ServerLevel server) TideVfxEntity.spawnSplash(server, at, 1.0F);
        level().playSound(null, at.x, at.y, at.z, SoundEvents.MACE_SMASH_GROUND, SoundSource.HOSTILE, 1.3F, 0.8F);
        level().playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_SPLASH, SoundSource.HOSTILE, 0.8F, 1.0F);
    }

    // 和斧头一样：挡住的盾牌进入 3 秒冷却
    private void disableShield(Player player) {
        player.getCooldowns().addCooldown(player.getUseItem().getItem(), 60);
        player.stopUsingItem();
        level().broadcastEntityEvent(player, (byte) 30);
    }

    // ---------------- ② 钳握重摔 ----------------

    private void tickGrab(@Nullable LivingEntity target) {
        if (skillTicks == GRAB_TICK) {
            Vec3 grip = gripAt(GRAB_TICK);
            LivingEntity caught = null;
            double best = Double.MAX_VALUE;
            for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, new AABB(grip, grip).inflate(1.2), TideRemnantHermitCrabEntity::canHit)) {
                if (!grabbable(e) || e == this) continue;
                double d = e.getBoundingBox().getCenter().distanceToSqr(grip) - (e == target ? 1 : 0);
                if (d < best) { best = d; caught = e; }
            }
            if (caught == null) {
                startSkill(GRAB_MISS);
                return;
            }
            grabbed = caught;
            playSound(SoundEvents.ARMOR_EQUIP_TURTLE.value(), 1.2F, 0.6F);
        }
        if (grabbed == null) return;
        if (!grabbed.isAlive() || grabbed.isPassenger() || grabbed.isRemoved()) { releaseGrab(); return; }
        if (skillTicks > GRAB_TICK && skillTicks <= GRAB_SLAM) {
            Vec3 grip = gripAt(skillTicks);
            // 目标挂在钳口上：身体中心对准钳口
            grabbed.teleportTo(grip.x, grip.y - grabbed.getBbHeight() * 0.5, grip.z);
            grabbed.setDeltaMovement(Vec3.ZERO);
            grabbed.fallDistance = 0;
            grabbed.hurtMarked = true;
        }
        if (skillTicks == 24) playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 0.6F);
        if (level() instanceof ServerLevel server && skillTicks >= 24 && skillTicks < GRAB_SLAM) {
            // 甩开和砸下时钳口拖出一道水痕
            Vec3 grip = gripAt(skillTicks);
            server.sendParticles(ParticleTypes.SPLASH, grip.x, grip.y, grip.z, 6, 0.15, 0.15, 0.15, 0.05);
            server.sendParticles(ParticleTypes.FALLING_WATER, grip.x, grip.y, grip.z, 2, 0.2, 0.2, 0.2, 0);
        }
        if (skillTicks == GRAB_SLAM) {
            LivingEntity victim = grabbed;
            releaseGrab();
            Vec3 at = gripAt(GRAB_SLAM);
            Vec3 ground = new Vec3(at.x, getY(), at.z);
            if (victim.hurt(damageSources().mobAttack(this), GRAB_DAMAGE)) {
                Vec3 away = horizontalAway(victim.position(), position());
                victim.setDeltaMovement(away.x * 0.3, 0.55, away.z * 0.3);
                victim.hurtMarked = true;
            }
            for (LivingEntity other : victims(ground, 1.5, 1, 2)) {
                if (other == victim) continue;
                if (other.hurt(damageSources().mobAttack(this), GRAB_SPLASH)) {
                    Vec3 away = horizontalAway(other.position(), ground);
                    other.knockback(0.6, -away.x, -away.z);
                }
            }
            impactFx(ground, 1.6F, 26);
            if (level() instanceof ServerLevel server) TideVfxEntity.spawnSplash(server, ground, 1.35F);
            level().playSound(null, ground.x, ground.y, ground.z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.HOSTILE, 1.4F, 0.8F);
            level().playSound(null, ground.x, ground.y, ground.z, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.HOSTILE, 0.9F, 0.9F);
        }
    }

    private Vec3 gripAt(int tick) {
        float[] p = GRIP[Mth.clamp(tick - GRAB_TICK, 0, GRIP.length - 1)];
        return local(p[0] / 16, p[1] / 16, -p[2] / 16);
    }

    private void releaseGrab() {
        grabbed = null;
        grabDamage = 0;
    }

    // ---------------- ③ 潮压水箭 ----------------

    private void tickBolt(@Nullable LivingEntity target) {
        if (skillTicks == 4) playSound(SoundEvents.BUCKET_FILL, 1.0F, 0.7F);
        if (level() instanceof ServerLevel server && skillTicks >= 4 && skillTicks < BOLT_SHOTS[0]) {
            // 蓄水：水珠向口器汇聚
            Vec3 mouth = local(0, 0.5, 0.97);
            for (int i = 0; i < 2; i++) {
                Vec3 from = mouth.add((random.nextDouble() - 0.5) * 1.6, random.nextDouble() * 0.8, (random.nextDouble() - 0.5) * 1.6);
                Vec3 v = mouth.subtract(from).scale(0.15);
                server.sendParticles(ParticleTypes.SPLASH, from.x, from.y, from.z, 0, v.x, v.y, v.z, 1);
            }
        }
        for (int shot : BOLT_SHOTS) {
            if (skillTicks != shot || target == null || !target.isAlive()) continue;
            Vec3 mouth = local(0, 0.48, 0.97);
            Vec3 aim = target.position().add(0, target.getBbHeight() * 0.5, 0);
            double flight = aim.distanceTo(mouth) / TideWaterBoltEntity.SPEED;
            Vec3 tv = target.getDeltaMovement();
            aim = aim.add(tv.x * flight * 0.5, 0, tv.z * flight * 0.5);
            TideWaterBoltEntity.shoot(level(), this, mouth, aim);
            playSound(SoundEvents.DROWNED_SHOOT, 1.0F, 0.8F + random.nextFloat() * 0.2F);
        }
    }

    // ---------------- ④ 骸潮炮 ----------------

    private void tickCannon() {
        if (!(level() instanceof ServerLevel server)) return;
        int t = skillTicks;
        if (t == CANNON_CHARGE) playSound(SoundEvents.CONDUIT_ACTIVATE, 2.0F, 0.7F);
        if (t >= CANNON_CHARGE && t < CANNON_SWEEP) {
            // 吸潮：5 格内的生物被轻微拉近；地面水花画出 120° 扇形预警
            for (LivingEntity e : victims(position(), 5, 2, 3)) {
                Vec3 pull = horizontalAway(position(), e.position()).scale(0.05);
                e.setDeltaMovement(e.getDeltaMovement().add(pull));
                e.hurtMarked = true;
            }
            for (int i = 0; i < 3; i++) {
                Vec3 from = position().add((random.nextDouble() - 0.5) * 12, 0.3 + random.nextDouble() * 2, (random.nextDouble() - 0.5) * 12);
                Vec3 v = local(0, 1.2, 1.6).subtract(from).scale(0.08);
                server.sendParticles(ParticleTypes.SPLASH, from.x, from.y, from.z, 0, v.x, v.y, v.z, 1);
            }
            if (t % 4 == 0) {
                float base = skillYaw;
                for (int a = -60; a <= 60; a += 10) {
                    Vec3 dir = beamDirection(base, a);
                    for (double r = 3; r <= 12; r += 3) {
                        Vec3 p = position().add(dir.scale(r));
                        server.sendParticles(ParticleTypes.FALLING_WATER, p.x, getY() + 0.15, p.z, 1, 0.15, 0, 0.15, 0);
                    }
                }
            }
        }
        if (t == CANNON_SWEEP) {
            playSound(SoundEvents.GUARDIAN_ATTACK, 2.0F, 0.6F);
            playSound(SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 2.0F, 0.6F);
        }
        if (t >= CANNON_SWEEP && t <= CANNON_SWEEP_END) {
            float sweep = sweepAngle(t);
            Vec3 dir = beamDirection(skillYaw, sweep);
            Vec3 origin = beamOrigin(skillYaw, sweep);
            double length = beamLength(level(), origin, dir);
            // 两侧眼窝喷出短水雾
            for (int side = -1; side <= 1; side += 2) {
                Vec3 eye = local(side * 0.625, 1.25, 0.375);
                Vec3 v = dir.scale(0.35).add(0, 0.05, 0);
                server.sendParticles(ParticleTypes.SPLASH, eye.x, eye.y, eye.z, 0, v.x, v.y, v.z, 1);
                server.sendParticles(ParticleTypes.CLOUD, eye.x, eye.y, eye.z, 0, v.x * 0.5, 0.02, v.z * 0.5, 1);
            }
            if (t % 5 == 0) {
                Vec3 end = origin.add(dir.scale(length));
                for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, new AABB(origin, end).inflate(1.2), TideRemnantHermitCrabEntity::canHit)) {
                    Vec3 c = e.getBoundingBox().getCenter();
                    if (distanceToSegment(c, origin, end) > 0.6 + e.getBbWidth() * 0.5) continue;
                    e.invulnerableTime = 0;
                    if (e.hurt(damageSources().mobAttack(this), BEAM_DAMAGE)) {
                        e.setDeltaMovement(e.getDeltaMovement().add(dir.x * 0.35, 0.05, dir.z * 0.35));
                        e.hurtMarked = true;
                    }
                }
                // 扫过的地面 0.5 秒后喷出间歇泉，沿光柱的近、中、远三个距离轮流出现
                double d = Math.min(length, 4 + (t / 5 % 3) * 3.5);
                Vec3 at = origin.add(dir.scale(d));
                BlockPos ground = findGround(level(), BlockPos.containing(at.x, getY() + 1, at.z));
                if (ground != null) TideVfxEntity.spawnGeyser(server, this, Vec3.atBottomCenterOf(ground.above()));
            }
        }
        if (t == CANNON_BURST) {
            TideVfxEntity.spawnWave(server, this, position());
            impactFx(position(), 2.5F, 40);
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.0F, 1.3F);
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.HOSTILE, 2.0F, 0.7F);
        }
    }

    /** 吻端 vfx_snout 的位置：扫射时离身体中心约 1.7 格、高 0.95 格。 */
    public Vec3 beamOrigin(float bodyYaw, float sweep) {
        return position().add(0, 0.95, 0).add(beamDirection(bodyYaw, sweep).scale(1.65));
    }

    public static double beamLength(Level level, Vec3 origin, Vec3 dir) {
        Vec3 end = origin.add(dir.scale(BEAM_LENGTH));
        HitResult hit = level.clip(new ClipContext(origin, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        return hit.getType() == HitResult.Type.MISS ? BEAM_LENGTH : hit.getLocation().distanceTo(origin);
    }

    @Nullable public static BlockPos findGround(Level level, BlockPos start) {
        BlockPos.MutableBlockPos pos = start.mutable();
        for (int i = 0; i < 6; i++, pos.move(0, -1, 0)) {
            BlockState state = level.getBlockState(pos);
            if (!state.getCollisionShape(level, pos).isEmpty() && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty())
                return pos.immutable();
        }
        return null;
    }

    // ---------------- 工具 ----------------

    /** 以技能朝向为基准的本地坐标（格）：right 向右、up 向上、fwd 向前。 */
    private Vec3 local(double right, double up, double fwd) {
        double y = Math.toRadians(skillYaw);
        double sin = Math.sin(y), cos = Math.cos(y);
        return position().add(-sin * fwd - cos * right, up, cos * fwd - sin * right);
    }

    private float yawTo(Entity e) {
        return (float) (Mth.atan2(e.getZ() - getZ(), e.getX() - getX()) * Mth.RAD_TO_DEG) - 90;
    }

    private List<LivingEntity> victims(Vec3 at, double radius, double below, double above) {
        AABB box = new AABB(at.x - radius, at.y - below, at.z - radius, at.x + radius, at.y + above, at.z + radius);
        return level().getEntitiesOfClass(LivingEntity.class, box, e -> e != this && canHit(e)
            && e.position().subtract(at).horizontalDistanceSqr() <= (radius + e.getBbWidth() * 0.5) * (radius + e.getBbWidth() * 0.5));
    }

    private static Vec3 horizontalAway(Vec3 from, Vec3 center) {
        Vec3 d = from.subtract(center).multiply(1, 0, 1);
        return d.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : d.normalize();
    }

    private static double distanceToSegment(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double t = Mth.clamp(p.subtract(a).dot(ab) / Math.max(ab.lengthSqr(), 1.0E-6), 0, 1);
        return p.distanceTo(a.add(ab.scale(t)));
    }

    // 落点碎石 + 一圈水花
    private void impactFx(Vec3 at, float ring, int count) {
        if (!(level() instanceof ServerLevel server)) return;
        BlockState ground = level().getBlockState(BlockPos.containing(at.x, at.y - 0.2, at.z));
        if (!ground.isAir()) server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), at.x, at.y + 0.1, at.z, count, 0.4, 0.1, 0.4, 0.15);
        for (int i = 0; i < count; i++) {
            double a = i * Math.PI * 2 / count;
            server.sendParticles(ParticleTypes.SPLASH, at.x + Math.cos(a) * ring, at.y + 0.1, at.z + Math.sin(a) * ring, 2, 0.1, 0.05, 0.1, 0.1);
        }
        server.sendParticles(ParticleTypes.BUBBLE_POP, at.x, at.y + 0.2, at.z, count / 2, ring * 0.5, 0.1, ring * 0.5, 0.05);
    }

    @Override public void die(DamageSource source) {
        releaseGrab();
        super.die(source);
    }

    @Override public boolean removeWhenFarAway(double distance) { return false; }
    @Override protected SoundEvent getAmbientSound() { return isResting() ? null : SoundEvents.TURTLE_AMBIENT_LAND; }
    @Override protected SoundEvent getHurtSound(DamageSource source) {
        return isShellClosed() ? SoundEvents.SHULKER_HURT_CLOSED : SoundEvents.TURTLE_HURT;
    }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.TURTLE_DEATH; }
    @Override protected void playStepSound(BlockPos pos, BlockState state) { playSound(SoundEvents.TURTLE_SHAMBLE, 0.3F, 0.9F); }
    @Override public int getAmbientSoundInterval() { return 400; }

    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("UltUnlocked", ultUnlocked);
        tag.putBoolean("SkullBroken", isSkullBroken());
        tag.putInt("SkullRecoveryTicks", skullRecoveryTicks);
        tag.putDouble("SkullRestoreArmor", skullRestoreArmor);
        tag.putDouble("SkullRestoreSpeed", skullRestoreSpeed);
        tag.putFloat("ShellDamage", shellDamage);
    }

    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        ultUnlocked = tag.getBoolean("UltUnlocked");
        skullRestoreArmor = tag.contains("SkullRestoreArmor") ? tag.getDouble("SkullRestoreArmor") : BASE_ARMOR;
        skullRestoreSpeed = tag.contains("SkullRestoreSpeed") ? tag.getDouble("SkullRestoreSpeed") : BASE_MOVEMENT_SPEED;
        skullRecoveryTicks = Math.max(0, tag.getInt("SkullRecoveryTicks"));
        shellDamage = tag.getFloat("ShellDamage");
        boolean skullBroken = tag.getBoolean("SkullBroken") && skullRecoveryTicks > 0;
        entityData.set(SKULL_BROKEN, skullBroken);
        if (skullBroken) {
            var armor = getAttribute(Attributes.ARMOR);
            var speed = getAttribute(Attributes.MOVEMENT_SPEED);
            if (armor != null) armor.setBaseValue(0.0D);
            if (speed != null) speed.setBaseValue(Math.max(skullRestoreSpeed * 2.5D, 0.5D));
        }
    }

    // ---------------- 动画 ----------------

    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "base", 4, state -> {
            if (isDeadOrDying()) return state.setAndContinue(DEATH);
            if (getSkill() != NONE) return PlayState.STOP;
            if (isResting()) return state.setAndContinue(REST);
            return state.setAndContinue(state.isMoving() ? WALK : IDLE);
        }));
        // 技能动画按同步的技能状态播放，不做过渡，命中帧和服务端结算对齐
        controllers.add(new AnimationController<>(this, "skill", 0, state -> {
            byte skill = getSkill();
            if (isDeadOrDying() || skill == NONE) return PlayState.STOP;
            if (animResetSeen != clientSkillStart) {
                animResetSeen = clientSkillStart;
                state.getController().forceAnimationReset();
            }
            return state.setAndContinue(SKILL_ANIMS[skill]);
        }));
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }

    // 技能和缩壳期间原地不动
    private final class HoldStillGoal extends Goal {
        HoldStillGoal() { setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP, Flag.LOOK)); }
        @Override public boolean canUse() { return !isSkullBroken() && (getSkill() != NONE || isResting()); }
        @Override public void start() { getNavigation().stop(); }
        @Override public void tick() {
            getNavigation().stop();
            setDeltaMovement(getDeltaMovement().multiply(0, 1, 0));
        }
        @Override public boolean requiresUpdateEveryTick() { return true; }
    }

    private final class FleeWhileSkullBrokenGoal extends Goal {
        FleeWhileSkullBrokenGoal() { setFlags(EnumSet.of(Flag.MOVE)); }
        @Override public boolean canUse() { return isSkullBroken(); }
        @Override public boolean canContinueToUse() { return isSkullBroken(); }
        @Override public void start() { moveAway(); }
        @Override public void tick() {
            if (tickCount % 8 == 0 || getNavigation().isDone()) moveAway();
        }
        @Override public boolean requiresUpdateEveryTick() { return true; }

        private void moveAway() {
            if (fleeingFrom != null && fleeingFrom.isAlive()) {
                Vec3 away = position().subtract(fleeingFrom.position()).multiply(1, 0, 1);
                if (away.lengthSqr() > 1.0E-4D) fleeDirection = away.normalize();
            }
            Vec3 destination = position().add(fleeDirection.scale(8.0D));
            getNavigation().moveTo(destination.x, getY(), destination.z, 1.8D);
        }
    }
}
