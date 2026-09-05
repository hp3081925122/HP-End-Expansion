package com.hp_end_expansion.content.prismatic.entity;

import com.hp_end_expansion.HpEndExpansion;
import com.hp_end_expansion.content.prismatic.PrismaticEntities;
import java.util.HashSet;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.util.GeckoLibUtil;

// 服务端统一处理锁向、前摇、命中和收招，客户端读取同一状态绘制稳定提示。
public abstract class PrismaticMonster extends Monster implements PrismaticAnimated {
    public enum Attack { NONE, STRIKE, CHARGE, NEEDLES, PULSE, BEAM }
    private static final EntityDataAccessor<Integer> ATTACK = SynchedEntityData.defineId(PrismaticMonster.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> SPECIAL = SynchedEntityData.defineId(PrismaticMonster.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Long> START = SynchedEntityData.defineId(PrismaticMonster.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Float> YAW = SynchedEntityData.defineId(PrismaticMonster.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> PITCH = SynchedEntityData.defineId(PrismaticMonster.class, EntityDataSerializers.FLOAT);
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Set<UUID> attackVictims = new HashSet<>();
    protected int attackCooldown = 35;
    protected int attackSequence;
    private boolean chargeStopped;

    protected PrismaticMonster(EntityType<? extends Monster> type, Level level) { super(type, level); }

    // 各物种决定具体技能组合，所有组合沿用同一命中时间契约。
    protected abstract Attack chooseAttack(LivingEntity target);
    protected boolean usesSpecial(Attack attack) { return attack == Attack.CHARGE || attack == Attack.BEAM; }
    protected double preferredRange() { return 2.5; }
    protected int idleCooldown() { return 24; }
    public double attackRadius() { return 3.0; }
    public double beamRange() { return 22.0; }
    protected int needleCount() { return 1; }
    protected double chargeSpeed() { return 0.85; }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ATTACK, 0);
        builder.define(SPECIAL, false);
        builder.define(START, 0L);
        builder.define(YAW, 0.0F);
        builder.define(PITCH, 0.0F);
    }

    // 移动与攻击由状态机控制，原版目标负责识别玩家和受击来源。
    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new Goal() {
            { setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }
            @Override public boolean canUse() { return getTarget() != null || currentAttack() != Attack.NONE; }
        });
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    public static AttributeSupplier.Builder attributes(double health, double speed, double damage, double armor, double resistance) {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, health)
                .add(Attributes.MOVEMENT_SPEED, speed).add(Attributes.ATTACK_DAMAGE, damage)
                .add(Attributes.ARMOR, armor).add(Attributes.KNOCKBACK_RESISTANCE, resistance)
                .add(Attributes.FOLLOW_RANGE, 32.0).add(Attributes.FLYING_SPEED, 0.24);
    }

    // 开始动作时一次性锁定目标方向并广播动画，不在前摇期间偷偷追踪。
    protected void beginAttack(Attack attack, LivingEntity target) {
        Vec3 direction = target.getBoundingBox().getCenter().subtract(attackOrigin()).normalize();
        float yaw = (float) (Math.toDegrees(Math.atan2(direction.z, direction.x)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(direction.y, direction.horizontalDistance()));
        entityData.set(YAW, yaw);
        entityData.set(PITCH, pitch);
        entityData.set(START, level().getGameTime());
        entityData.set(SPECIAL, usesSpecial(attack));
        entityData.set(ATTACK, attack.ordinal());
        attackVictims.clear();
        chargeStopped = false;
        attackSequence++;
        getNavigation().stop();
        setDeltaMovement(0, getDeltaMovement().y, 0);
        alignAttackDirection();
        triggerAnim("main", isSpecialAttack() ? "special" : "attack");
        playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.8F, 0.65F);
        HpEndExpansion.LOGGER.debug("Prismatic attack started: entity={}, id={}, attack={}, windup={}", speciesId(), getId(), attack, windupTicks());
    }

    // 读写同步字段时不发送逐刻计时，客户端以世界时间计算动作进度。
    public Attack currentAttack() { return Attack.values()[entityData.get(ATTACK)]; }
    public boolean isSpecialAttack() { return entityData.get(SPECIAL); }
    public int attackAge() { return (int) Math.max(0, level().getGameTime() - entityData.get(START)); }
    public int windupTicks() { return isSpecialAttack() ? 24 : 20; }
    public int attackDuration() { return isSpecialAttack() ? 48 : 40; }
    public float attackYaw() { return entityData.get(YAW); }
    public float attackPitch() { return entityData.get(PITCH); }
    public Vec3 attackOrigin() { return position().add(0, getBbHeight() * 0.65, 0); }
    public Vec3 attackDirection() { return Vec3.directionFromRotation(attackPitch(), attackYaw()); }

    // 技能几何超出本体时扩大视锥盒，玩家只看见光束末端仍能看到提示。
    @Override
    public AABB getBoundingBoxForCulling() {
        return currentAttack() == Attack.NONE ? super.getBoundingBoxForCulling() : getBoundingBox().inflate(beamRange());
    }

    // 光束末端同时用于服务端伤害和客户端几何，以实体方块截断。
    public Vec3 beamEnd() {
        Vec3 origin = attackOrigin();
        Vec3 far = origin.add(attackDirection().scale(beamRange()));
        return level().clip(new ClipContext(origin, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getLocation();
    }

    protected void cancelAttack() {
        entityData.set(ATTACK, 0);
        attackVictims.clear();
        getNavigation().stop();
        setDeltaMovement(0, getDeltaMovement().y, 0);
    }

    // 朝向在整个攻击窗口内与锁线提示一致。
    private void alignAttackDirection() {
        setYRot(attackYaw());
        setYHeadRot(attackYaw());
        setYBodyRot(attackYaw());
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || !isAlive() || isNoAi()) { return; }
        if (currentAttack() != Attack.NONE) { tickAttack(); return; }
        if (attackCooldown > 0) { attackCooldown--; }
        LivingEntity target = getTarget();
        if (!isCombatTarget(target)) {
            setTarget(null);
            return;
        }
        getLookControl().setLookAt(target, 30.0F, 30.0F);
        double distance = distanceTo(target);
        if (tickCount % 10 == 0) {
            if (distance > preferredRange() || !hasLineOfSight(target)) {
                getNavigation().moveTo(target, 1.05);
            } else if (preferredRange() >= 6.0 && distance < 4.0) {
                Vec3 retreat = position().subtract(target.position()).normalize().scale(3.0).add(position());
                getNavigation().moveTo(retreat.x, retreat.y, retreat.z, 1.0);
            } else {
                getNavigation().stop();
            }
        }
        if (attackCooldown == 0 && distance <= preferredRange() + 1.0 && hasLineOfSight(target)) {
            beginAttack(chooseAttack(target), target);
        }
    }

    // 同一动作命中集合覆盖冲锋多刻检查，普通攻击只在单个确定帧结算。
    private void tickAttack() {
        int age = attackAge();
        getNavigation().stop();
        alignAttackDirection();
        if (currentAttack() == Attack.CHARGE && age >= windupTicks() && age < windupTicks() + 8 && !chargeStopped) {
            Vec3 direction = Vec3.directionFromRotation(0, attackYaw());
            BlockPos ahead = BlockPos.containing(position().add(direction.scale(1.2))).below();
            if (horizontalCollision || level().getBlockState(ahead).isAir() && level().getBlockState(ahead.below()).isAir()) {
                chargeStopped = true;
                setDeltaMovement(0, getDeltaMovement().y, 0);
            } else {
                setDeltaMovement(direction.x * chargeSpeed(), getDeltaMovement().y, direction.z * chargeSpeed());
                for (LivingEntity victim : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(0.45), this::isCombatTarget)) {
                    dealDamage(victim, 1.0F);
                }
            }
        } else {
            setDeltaMovement(0, getDeltaMovement().y, 0);
        }
        if (age == windupTicks()) {
            switch (currentAttack()) {
                case STRIKE -> hitArea(attackRadius(), true);
                case PULSE -> hitArea(attackRadius(), false);
                case NEEDLES -> fireNeedles();
                case BEAM -> hitBeam();
                default -> { }
            }
            playSound(SoundEvents.AMETHYST_BLOCK_BREAK, 1.0F, 0.75F);
            if (level() instanceof ServerLevel server) {
                server.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 0.7, getZ(), 12, 0.45, 0.35, 0.45, 0.02);
            }
        }
        if (age >= attackDuration()) {
            cancelAttack();
            attackCooldown = idleCooldown();
        }
    }

    // 范围命中要求实体眼部视线，同时按锁定方向约束前方横扫。
    private void hitArea(double radius, boolean frontal) {
        Vec3 forward = Vec3.directionFromRotation(0, attackYaw());
        AABB area = getBoundingBox().inflate(radius, 1.0, radius);
        for (LivingEntity victim : level().getEntitiesOfClass(LivingEntity.class, area, this::isCombatTarget)) {
            Vec3 delta = victim.position().subtract(position());
            double reach = radius + victim.getBbWidth() * 0.5;
            if (delta.horizontalDistanceSqr() <= reach * reach && Math.abs(delta.y) < 3.0
                    && (!frontal || delta.normalize().dot(forward) > 0.1)) {
                dealDamage(victim, 1.0F);
            }
        }
    }

    // 光束用实际碰撞盒线段检测并截断于首个完整掩体。
    private void hitBeam() {
        Vec3 start = attackOrigin();
        Vec3 end = beamEnd();
        AABB search = new AABB(start, end).inflate(0.65);
        for (LivingEntity victim : level().getEntitiesOfClass(LivingEntity.class, search, this::isCombatTarget)) {
            AABB bounds = victim.getBoundingBox().inflate(0.35);
            if (bounds.contains(start) || bounds.clip(start, end).isPresent()) { dealDamage(victim, 1.0F); }
        }
    }

    // 晶针由真实投射物负责飞行和方块碰撞，扇形始终只有三条固定方向。
    private void fireNeedles() {
        int count = needleCount();
        for (int i = 0; i < count; i++) {
            CrystalNeedle needle = PrismaticEntities.CRYSTAL_NEEDLE.get().create(level());
            if (needle == null) { continue; }
            Vec3 origin = attackOrigin();
            Vec3 direction = Vec3.directionFromRotation(attackPitch(), attackYaw() + (i - (count - 1) * 0.5F) * 16.0F);
            needle.setOwner(this);
            needle.setDamage((float) getAttributeValue(Attributes.ATTACK_DAMAGE));
            needle.setPos(origin.x, origin.y, origin.z);
            needle.shoot(direction.x, direction.y, direction.z, 1.1F, 0.0F);
            level().addFreshEntity(needle);
        }
    }

    // 所有伤害仅发生在服务端，遮挡和同阵营检查统一执行。
    private void dealDamage(LivingEntity victim, float multiplier) {
        if (attackVictims.contains(victim.getUUID()) || !hasLineOfSight(victim)) { return; }
        attackVictims.add(victim.getUUID());
        if (victim.hurt(damageSources().mobAttack(this), (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * multiplier)) {
            victim.knockback(currentAttack() == Attack.CHARGE ? 0.7 : 0.35, getX() - victim.getX(), getZ() - victim.getZ());
        }
    }

    protected boolean isCombatTarget(LivingEntity entity) {
        return entity != null && entity != this && entity.isAlive() && !entity.isSpectator()
                && !(entity instanceof PrismaticMonster) && !(entity instanceof PrismaticAnimal)
                && !(entity instanceof Player player && player.isCreative()) && !isAlliedTo(entity);
    }

    // 受击不覆盖正在播放的前摇，保证视觉提示和服务端命中时间对齐。
    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean damaged = super.hurt(source, amount);
        if (damaged && !level().isClientSide && isAlive() && currentAttack() == Attack.NONE) { triggerAnim("main", "hurt"); }
        return damaged;
    }

    @Override
    public void die(DamageSource source) {
        if (!level().isClientSide) {
            cancelAttack();
            triggerAnim("main", "death");
        }
        super.die(source);
    }

    // 保存技能冷却和轮换，重新加载取消半段攻击并保留至少一秒安全间隔。
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("PrismaticAttackCooldown", Math.max(attackCooldown, currentAttack() == Attack.NONE ? 0 : 40));
        tag.putInt("PrismaticAttackSequence", attackSequence);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        attackCooldown = Math.max(20, tag.getInt("PrismaticAttackCooldown"));
        attackSequence = Math.max(0, tag.getInt("PrismaticAttackSequence"));
        entityData.set(ATTACK, 0);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
