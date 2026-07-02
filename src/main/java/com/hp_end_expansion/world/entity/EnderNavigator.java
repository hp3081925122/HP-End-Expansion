package com.hp_end_expansion.world.entity;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

public class EnderNavigator extends PathfinderMob implements GeoEntity {
    // 领航者会把当前攻击动作同步给客户端，方便后面接动画。
    private static final EntityDataAccessor<Integer> DATA_ATTACK_STATE = SynchedEntityData.defineId(EnderNavigator.class, EntityDataSerializers.INT);
    // 存档字段记录领航者是否已经召唤过鱼群援军。
    private static final String SUMMONED_REINFORCEMENTS_TAG = "SummonedReinforcements";
    // 存档字段记录领航者撞地后的剩余硬直时间。
    private static final String DIVE_STUN_TICKS_TAG = "DiveStunTicks";
    // 领航者 GeckoLib 的常驻悬浮动画和各类技能动画资源名。
    private static final RawAnimation IDLE_ANIMATION = RawAnimation.begin().thenLoop("animation.ender_navigator.idle");
    private static final RawAnimation MOVE_ANIMATION = RawAnimation.begin().thenLoop("animation.ender_navigator.move");
    private static final RawAnimation BEAM_ANIMATION = RawAnimation.begin().thenPlay("animation.ender_navigator.beam");
    private static final RawAnimation ANCHOR_ANIMATION = RawAnimation.begin().thenPlay("animation.ender_navigator.anchor");
    private static final RawAnimation PHASE_SLASH_ANIMATION = RawAnimation.begin().thenPlay("animation.ender_navigator.phase_slash");
    private static final RawAnimation VOID_PULSE_ANIMATION = RawAnimation.begin().thenPlay("animation.ender_navigator.void_pulse");
    private static final RawAnimation SUMMON_ANIMATION = RawAnimation.begin().thenPlay("animation.ender_navigator.summon");
    private static final RawAnimation DIVE_ANIMATION = RawAnimation.begin().thenPlay("animation.ender_navigator.dive");
    // 预留给后续动画和模型使用的攻击状态编号。
    private static final int ATTACK_STATE_NONE = 0;
    private static final int ATTACK_STATE_BEAM = 1;
    private static final int ATTACK_STATE_ANCHOR = 2;
    private static final int ATTACK_STATE_PHASE_SLASH = 3;
    private static final int ATTACK_STATE_VOID_PULSE = 4;
    private static final int ATTACK_STATE_SUMMON = 5;
    private static final int ATTACK_STATE_DIVE = 6;
    // 普通盘旋、近战突袭和远程施法的大致距离参数。
    private static final double COMBAT_ORBIT_RADIUS = 6.0D;
    private static final double IDLE_HOVER_HEIGHT = 5.0D;
    private static final double COMBAT_HOVER_HEIGHT = 4.2D;
    private static final double PHASE_SLASH_RANGE = 3.2D;
    private static final double VOID_PULSE_RANGE = 3.6D;
    private static final double BEAM_RANGE = 18.0D;
    private static final double ANCHOR_RANGE = 14.0D;
    private static final double DIVE_RANGE = 17.0D;
    private static final double DIVE_DAMAGE = 12.0D;
    private static final int DIVE_MAX_TICKS = 30;
    private static final int DIVE_GROUND_STUN_TICKS = 60;
    private static final int DIVE_PLAYER_STUN_TICKS = 20;
    private static final double DIVE_HITBOX_INFLATE = 1.15D;
    // GeckoLib 使用实体级动画缓存记录当前动画状态。
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    // 半血后只召唤一次鱼群，避免战斗无限滚雪球。
    private boolean summonedReinforcements;
    // 通用攻击冷却控制领航者在技能之间切换节奏。
    private int attackCooldown;
    // 相位突袭单独冷却，防止连着疯狂贴脸瞬移。
    private int phaseSlashCooldown;
    // 光束持续时间。
    private int beamTicks;
    // 锚点场持续时间。
    private int anchorTicks;
    // 俯冲剩余时间，期间领航者会高速向下冲撞。
    private int diveTicks;
    // 撞地硬直时间，期间领航者不能释放技能。
    private int diveStunTicks;
    // 短暂视觉攻击状态计时，用来维持客户端攻击姿态。
    private int attackStateTicks;
    // 盘旋角度让领航者围绕目标横向位移，而不是傻站原地。
    private float orbitAngle;
    // 锚点技能锁定的中心位置。
    @Nullable
    private Vec3 anchorCenter;
    // 无目标时的漂浮巡游目标。
    @Nullable
    private Vec3 wanderTarget;
    // 俯冲开始时锁定的落点，目标丢失时也能继续向下砸落。
    @Nullable
    private Vec3 diveTarget;

    // 创建末影领航者，并关闭重力与视锥裁剪。
    public EnderNavigator(EntityType<? extends EnderNavigator> entityType, Level level) {
        super(entityType, level);
        this.setNoGravity(true);
        this.noCulling = true;
    }

    // 创建末影领航者的基础属性。
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 64.0D)
                .add(Attributes.ATTACK_DAMAGE, 8.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.22D)
                .add(Attributes.FLYING_SPEED, 0.42D)
                .add(Attributes.FOLLOW_RANGE, 48.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.55D);
    }

    // 末影领航者默认只在末地高空环境中允许生成。
    public static boolean canSpawn(EntityType<EnderNavigator> entityType, ServerLevelAccessor level, MobSpawnType spawnType, BlockPos pos, RandomSource random) {
        return level.getLevel().dimension() == Level.END && pos.getY() > level.getLevel().getMinBuildHeight() + 16 && level.getBlockState(pos).isAir();
    }

    // 定义同步给客户端的攻击状态。
    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ATTACK_STATE, ATTACK_STATE_NONE);
    }

    // 注册最小索敌和观察目标，移动与施法逻辑主要放在 tick 里手写控制。
    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 24.0F));
        this.goalSelector.addGoal(2, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    // 注册常驻悬浮控制器，并把多种攻击动作都接到 GeckoLib 触发动画上。
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 3, state -> {
            // 领航者处于战斗盘旋或明显位移时播放移动姿态，否则维持待机悬浮。
            state.setAnimation(this.getTarget() != null || this.getDeltaMovement().lengthSqr() > 0.006D ? MOVE_ANIMATION : IDLE_ANIMATION);
            return PlayState.CONTINUE;
        }).triggerableAnim("beam", BEAM_ANIMATION)
                .triggerableAnim("anchor", ANCHOR_ANIMATION)
                .triggerableAnim("phase_slash", PHASE_SLASH_ANIMATION)
                .triggerableAnim("void_pulse", VOID_PULSE_ANIMATION)
                .triggerableAnim("summon", SUMMON_ANIMATION)
                .triggerableAnim("dive", DIVE_ANIMATION));
    }

    // 返回 GeckoLib 动画缓存。
    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    // 生成时给一个随机朝向，让多只领航者不会整齐排队。
    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType, @Nullable SpawnGroupData spawnGroupData) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData);
        this.setYRot(level.getRandom().nextFloat() * 360.0F);
        this.yBodyRot = this.getYRot();
        this.setYHeadRot(this.getYRot());
        return result;
    }

    // 每刻维护无重力、攻击冷却、客户端粒子和服务端战斗逻辑。
    @Override
    public void tick() {
        super.tick();
        this.setNoGravity(true);
        this.fallDistance = 0.0F;

        if (this.attackCooldown > 0) {
            this.attackCooldown--;
        }
        if (this.phaseSlashCooldown > 0) {
            this.phaseSlashCooldown--;
        }
        if (this.attackStateTicks > 0) {
            this.attackStateTicks--;
        } else if (this.beamTicks <= 0 && this.anchorTicks <= 0 && this.diveTicks <= 0 && this.diveStunTicks <= 0) {
            this.entityData.set(DATA_ATTACK_STATE, ATTACK_STATE_NONE);
        }

        if (this.diveStunTicks > 0) {
            this.diveStunTicks--;
            this.setDeltaMovement(this.getDeltaMovement().scale(0.25D));
        }

        if (this.level().isClientSide) {
            this.spawnClientParticles();
        } else if (this.isEffectiveAi()) {
            this.updateCombatLoop();
        }
    }

    // 领航者使用自定义飞行移动，不走地面寻路。
    @Override
    public void travel(Vec3 travelVector) {
        this.move(MoverType.SELF, this.getDeltaMovement());
        this.setDeltaMovement(this.getDeltaMovement().scale(0.91D));
        this.calculateEntityAnimation(true);
    }

    // 领航者免疫摔落伤害。
    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        return false;
    }

    // 空实现避免飞行施法怪走原版落地摔伤逻辑。
    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
    }

    // 领航者基础经验奖励略高于普通末地生物。
    @Override
    protected int getBaseExperienceReward() {
        return 16;
    }

    // 受击后立即把伤害来源设为目标，避免只依赖原版目标 AI 导致不还手。
    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);

        // 服务端确认伤害生效后，主动锁定攻击者并缩短当前技能冷却。
        if (hurt && !this.level().isClientSide && source.getEntity() instanceof LivingEntity attacker && attacker != this && attacker.isAlive()) {
            this.setTarget(attacker);
            this.wanderTarget = null;
            this.attackCooldown = Math.min(this.attackCooldown, 10);
        }

        return hurt;
    }

    // 保存是否已经召唤过援军。
    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putBoolean(SUMMONED_REINFORCEMENTS_TAG, this.summonedReinforcements);
        compound.putInt(DIVE_STUN_TICKS_TAG, this.diveStunTicks);
    }

    // 读取是否已经召唤过援军，并恢复无重力状态。
    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        this.summonedReinforcements = compound.getBoolean(SUMMONED_REINFORCEMENTS_TAG);
        this.diveStunTicks = compound.getInt(DIVE_STUN_TICKS_TAG);
        this.setNoGravity(true);
    }

    // 暴露给后续临时渲染器读取当前攻击状态。
    public int getAttackState() {
        return this.entityData.get(DATA_ATTACK_STATE);
    }

    // 统一驱动战斗、巡游和技能释放。
    private void updateCombatLoop() {
        LivingEntity target = this.getTarget();

        if (this.diveStunTicks > 0) {
            this.anchorCenter = null;
            this.beamTicks = 0;
            this.anchorTicks = 0;
            return;
        }

        if (target == null || !target.isAlive()) {
            this.anchorCenter = null;
            this.beamTicks = 0;
            this.anchorTicks = 0;
            if (this.diveTicks > 0) {
                this.handleDive(null);
                return;
            }
            this.updateIdleFloat();
            return;
        }

        if (this.diveTicks > 0) {
            this.handleDive(target);
            return;
        }

        if (this.beamTicks > 0) {
            this.handleBeam(target);
            return;
        }

        if (this.anchorTicks > 0) {
            this.handleAnchorField();
            return;
        }

        this.updateCombatMovement(target);

        if (this.attackCooldown > 0) {
            return;
        }

        if (!this.summonedReinforcements && this.getHealth() <= this.getMaxHealth() * 0.5F) {
            this.castSummonFish(target);
            return;
        }

        double distanceToTargetSqr = this.distanceToSqr(target);

        if (distanceToTargetSqr <= DIVE_RANGE * DIVE_RANGE && this.getY() > target.getY() + 2.5D && this.random.nextInt(3) == 0) {
            this.startDive(target);
            return;
        }

        if (distanceToTargetSqr <= VOID_PULSE_RANGE * VOID_PULSE_RANGE && this.random.nextInt(4) == 0) {
            this.performVoidPulse();
            return;
        }

        if (distanceToTargetSqr <= ANCHOR_RANGE * ANCHOR_RANGE && this.phaseSlashCooldown <= 0 && this.random.nextBoolean()) {
            this.performPhaseSlash(target);
            return;
        }

        if (distanceToTargetSqr <= BEAM_RANGE * BEAM_RANGE && this.hasLineOfSight(target) && this.random.nextBoolean()) {
            this.startBeam();
            return;
        }

        this.startAnchorField(target);
    }

    // 开始向目标下方俯冲冲撞。
    private void startDive(LivingEntity target) {
        this.diveTicks = DIVE_MAX_TICKS;
        this.diveTarget = target.position().add(0.0D, target.getBbHeight() * 0.35D, 0.0D);
        this.attackCooldown = 95;
        this.anchorCenter = null;
        this.beamTicks = 0;
        this.anchorTicks = 0;
        this.setAttackState(ATTACK_STATE_DIVE, DIVE_MAX_TICKS);
        // 俯冲开始时触发收翼下压动画。
        this.triggerAnim("main", "dive");
    }

    // 维护俯冲位移、玩家命中和撞地硬直。
    private void handleDive(@Nullable LivingEntity target) {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            this.diveTicks--;
            return;
        }

        Vec3 aim = target != null && target.isAlive() ? target.position().add(0.0D, target.getBbHeight() * 0.35D, 0.0D) : this.diveTarget;
        if (aim == null) {
            aim = this.position().add(0.0D, -6.0D, 0.0D);
        }

        Vec3 direction = aim.subtract(this.position());
        if (direction.lengthSqr() < 1.0E-4D) {
            direction = new Vec3(0.0D, -1.0D, 0.0D);
        }

        Vec3 diveDirection = new Vec3(direction.x, Math.min(direction.y, -2.6D), direction.z).normalize();
        Vec3 nextMovement = this.getDeltaMovement().scale(0.18D).add(diveDirection.scale(1.05D));
        AABB sweepBox = this.getBoundingBox().expandTowards(nextMovement).inflate(DIVE_HITBOX_INFLATE);

        Player hitPlayer = null;
        for (Player player : serverLevel.getEntitiesOfClass(Player.class, sweepBox, player -> player.isAlive() && !player.isSpectator())) {
            hitPlayer = player;
            break;
        }

        if (hitPlayer != null) {
            this.finishDivePlayerHit(serverLevel, hitPlayer);
            return;
        }

        if (this.verticalCollisionBelow || this.onGround()) {
            this.finishDiveGroundCrash(serverLevel);
            return;
        }

        this.diveTicks--;
        this.setDeltaMovement(nextMovement);
        this.updateRotationFromMovement(diveDirection);
        serverLevel.sendParticles(ParticleTypes.REVERSE_PORTAL, this.getX(), this.getY() + this.getBbHeight() * 0.45D, this.getZ(), 8, 0.45D, 0.35D, 0.45D, 0.08D);

        if (this.diveTicks <= 0) {
            this.finishDiveGroundCrash(serverLevel);
        }
    }

    // 俯冲撞到玩家时造成伤害并给玩家一秒眩晕。
    private void finishDivePlayerHit(ServerLevel serverLevel, Player player) {
        this.diveTicks = 0;
        this.diveTarget = null;
        this.attackCooldown = 55;
        this.setAttackState(ATTACK_STATE_DIVE, 12);
        player.hurt(this.damageSources().mobAttack(this), (float)DIVE_DAMAGE);
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, DIVE_PLAYER_STUN_TICKS, 9, false, true), this);
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, DIVE_PLAYER_STUN_TICKS, 1, false, true), this);
        Vec3 knockback = player.position().subtract(this.position());
        if (knockback.lengthSqr() > 1.0E-4D) {
            Vec3 normalized = knockback.normalize().scale(0.75D);
            player.push(normalized.x, 0.22D, normalized.z);
        }
        this.setDeltaMovement(0.0D, 0.28D, 0.0D);
        serverLevel.sendParticles(ParticleTypes.DRAGON_BREATH, player.getX(), player.getY() + player.getBbHeight() * 0.5D, player.getZ(), 26, 0.45D, 0.35D, 0.45D, 0.03D);
        serverLevel.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + player.getBbHeight() * 0.5D, player.getZ(), 24, 0.35D, 0.35D, 0.35D, 0.08D);
    }

    // 俯冲砸到地面时让领航者自己硬直三秒。
    private void finishDiveGroundCrash(ServerLevel serverLevel) {
        this.diveTicks = 0;
        this.diveTarget = null;
        this.diveStunTicks = DIVE_GROUND_STUN_TICKS;
        this.attackCooldown = DIVE_GROUND_STUN_TICKS + 25;
        this.setAttackState(ATTACK_STATE_DIVE, DIVE_GROUND_STUN_TICKS);
        this.setDeltaMovement(Vec3.ZERO);
        this.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, DIVE_GROUND_STUN_TICKS, 9, false, false), this);
        this.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, DIVE_GROUND_STUN_TICKS, 1, false, false), this);
        serverLevel.sendParticles(ParticleTypes.DRAGON_BREATH, this.getX(), this.getY() + 0.35D, this.getZ(), 36, 0.85D, 0.18D, 0.85D, 0.03D);
        serverLevel.sendParticles(ParticleTypes.REVERSE_PORTAL, this.getX(), this.getY() + 0.35D, this.getZ(), 42, 0.95D, 0.2D, 0.95D, 0.08D);
    }

    // 无目标时保持缓慢悬浮巡游，避免实体完全站桩。
    private void updateIdleFloat() {
        if (this.wanderTarget == null || this.distanceToSqr(this.wanderTarget) < 4.0D || this.random.nextInt(120) == 0) {
            int groundY = this.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(this.getX()), Mth.floor(this.getZ()));
            double preferredY = Math.max(this.getY(), groundY + IDLE_HOVER_HEIGHT);
            this.wanderTarget = this.position().add(
                    Mth.nextDouble(this.random, -10.0D, 10.0D),
                    Mth.nextDouble(this.random, -1.5D, 2.5D),
                    Mth.nextDouble(this.random, -10.0D, 10.0D)
            );
            this.wanderTarget = new Vec3(this.wanderTarget.x, preferredY + Mth.nextDouble(this.random, -1.5D, 2.5D), this.wanderTarget.z);
        }

        double targetY = Mth.clamp(this.wanderTarget.y, this.level().getMinBuildHeight() + 8.0D, this.level().getMaxBuildHeight() - 8.0D);
        Vec3 target = new Vec3(this.wanderTarget.x, targetY, this.wanderTarget.z);
        Vec3 direction = target.subtract(this.position());
        if (direction.lengthSqr() > 0.01D) {
            Vec3 motion = direction.normalize().scale(0.04D);
            this.setDeltaMovement(this.getDeltaMovement().scale(0.88D).add(motion));
            this.updateRotationFromMovement(direction);
        }
    }

    // 战斗时围绕目标盘旋，不让精英怪只会直线追脸。
    private void updateCombatMovement(LivingEntity target) {
        this.orbitAngle += 0.11F;
        Vec3 orbitOffset = new Vec3(
                Math.cos(this.orbitAngle) * COMBAT_ORBIT_RADIUS,
                target.getBbHeight() + COMBAT_HOVER_HEIGHT + Math.sin(this.orbitAngle * 0.7F) * 1.1D,
                Math.sin(this.orbitAngle) * COMBAT_ORBIT_RADIUS
        );
        Vec3 orbitTarget = target.position().add(orbitOffset);
        Vec3 direction = orbitTarget.subtract(this.position());
        if (direction.lengthSqr() > 0.01D) {
            Vec3 motion = direction.normalize().scale(0.07D);
            this.setDeltaMovement(this.getDeltaMovement().scale(0.82D).add(motion));
            this.updateRotationFromMovement(direction);
        }
        this.getLookControl().setLookAt(target, 20.0F, 20.0F);
    }

    // 开始持续光束攻击。
    private void startBeam() {
        this.beamTicks = 30;
        this.attackCooldown = 70;
        this.setAttackState(ATTACK_STATE_BEAM, 30);
        // 服务端在技能真正开始时触发一次光束动画，避免客户端每刻重播。
        this.triggerAnim("main", "beam");
    }

    // 每刻维护光束，并定时结算伤害。
    private void handleBeam(LivingEntity target) {
        this.beamTicks--;
        this.getLookControl().setLookAt(target, 30.0F, 30.0F);

        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        Vec3 start = this.position().add(0.0D, this.getBbHeight() * 0.65D, 0.0D);
        Vec3 end = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
        this.sendLineParticles(serverLevel, start, end, ParticleTypes.END_ROD, 16, 0.01D);
        this.sendLineParticles(serverLevel, start, end, ParticleTypes.PORTAL, 10, 0.0D);

        if (this.beamTicks % 8 == 0 && this.distanceToSqr(target) <= BEAM_RANGE * BEAM_RANGE && this.hasLineOfSight(target)) {
            target.hurt(this.damageSources().magic(), 3.5F);
            Vec3 push = target.position().subtract(this.position());
            if (push.lengthSqr() > 1.0E-4) {
                Vec3 normalized = push.normalize().scale(0.22D);
                target.push(normalized.x, 0.05D, normalized.z);
            }
        }
    }

    // 开始锚点场控制技能。
    private void startAnchorField(LivingEntity target) {
        this.anchorCenter = target.position();
        this.anchorTicks = 34;
        this.attackCooldown = 80;
        this.setAttackState(ATTACK_STATE_ANCHOR, 34);
        // 在锚点场开始时触发展开姿态动画。
        this.triggerAnim("main", "anchor");
    }

    // 锚点场会持续拉扯中心附近目标，并在结束时爆发。
    private void handleAnchorField() {
        this.anchorTicks--;

        if (this.anchorCenter == null || !(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        for (int i = 0; i < 3; i++) {
            double angle = this.anchorTicks * 0.21D + i * (Math.PI * 2.0D / 3.0D);
            double x = this.anchorCenter.x + Math.cos(angle) * 2.8D;
            double z = this.anchorCenter.z + Math.sin(angle) * 2.8D;
            serverLevel.sendParticles(ParticleTypes.REVERSE_PORTAL, x, this.anchorCenter.y + 0.35D, z, 4, 0.1D, 0.15D, 0.1D, 0.01D);
            serverLevel.sendParticles(ParticleTypes.END_ROD, x, this.anchorCenter.y + 0.35D, z, 1, 0.02D, 0.02D, 0.02D, 0.0D);
        }
        serverLevel.sendParticles(ParticleTypes.PORTAL, this.anchorCenter.x, this.anchorCenter.y + 0.35D, this.anchorCenter.z, 8, 0.4D, 0.2D, 0.4D, 0.01D);

        for (LivingEntity livingEntity : serverLevel.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(18.0D), entity -> entity.isAlive() && entity != this && entity.distanceToSqr(this.anchorCenter) <= 30.25D)) {
            Vec3 pull = this.anchorCenter.subtract(livingEntity.position());
            if (pull.lengthSqr() > 1.0E-4) {
                Vec3 normalized = pull.normalize().scale(0.12D);
                livingEntity.push(normalized.x, 0.03D, normalized.z);
            }
        }

        if (this.anchorTicks <= 0) {
            for (LivingEntity livingEntity : serverLevel.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(18.0D), entity -> entity.isAlive() && entity != this && entity.distanceToSqr(this.anchorCenter) <= 9.0D)) {
                livingEntity.hurt(this.damageSources().magic(), 5.0F);
                Vec3 burst = livingEntity.position().subtract(this.anchorCenter);
                if (burst.lengthSqr() > 1.0E-4) {
                    Vec3 normalized = burst.normalize().scale(0.35D);
                    livingEntity.push(normalized.x, 0.12D, normalized.z);
                }
            }
            serverLevel.sendParticles(ParticleTypes.DRAGON_BREATH, this.anchorCenter.x, this.anchorCenter.y + 0.35D, this.anchorCenter.z, 24, 0.35D, 0.15D, 0.35D, 0.02D);
            this.anchorCenter = null;
        }
    }

    // 相位突袭会闪到目标附近并打出一次重击。
    private void performPhaseSlash(LivingEntity target) {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        this.phaseSlashCooldown = 90;
        this.attackCooldown = 48;
        this.setAttackState(ATTACK_STATE_PHASE_SLASH, 10);
        // 相位突袭是一段短促爆发动作，只在发动时触发一次。
        this.triggerAnim("main", "phase_slash");

        Vec3 side = new Vec3(target.getLookAngle().z, 0.0D, -target.getLookAngle().x);
        if (side.lengthSqr() < 1.0E-4) {
            side = new Vec3(this.random.nextDouble() - 0.5D, 0.0D, this.random.nextDouble() - 0.5D);
        }
        Vec3 offset = side.normalize().scale(this.random.nextBoolean() ? PHASE_SLASH_RANGE : -PHASE_SLASH_RANGE);
        Vec3 destination = target.position().add(offset).add(0.0D, target.getBbHeight() + 1.2D, 0.0D);
        double clampedY = Mth.clamp(destination.y, this.level().getMinBuildHeight() + 4.0D, this.level().getMaxBuildHeight() - 4.0D);

        serverLevel.sendParticles(ParticleTypes.PORTAL, this.getX(), this.getY() + this.getBbHeight() * 0.5D, this.getZ(), 18, 0.35D, 0.25D, 0.35D, 0.06D);
        this.teleportTo(destination.x, clampedY, destination.z);
        serverLevel.sendParticles(ParticleTypes.REVERSE_PORTAL, this.getX(), this.getY() + this.getBbHeight() * 0.5D, this.getZ(), 18, 0.35D, 0.25D, 0.35D, 0.06D);

        this.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (this.distanceToSqr(target) <= 10.24D) {
            target.hurt(this.damageSources().mobAttack(this), (float)this.getAttributeValue(Attributes.ATTACK_DAMAGE));
            Vec3 knockback = target.position().subtract(this.position());
            if (knockback.lengthSqr() > 1.0E-4) {
                Vec3 normalized = knockback.normalize().scale(0.45D);
                target.push(normalized.x, 0.08D, normalized.z);
            }
        }
    }

    // 虚空脉冲会把贴脸目标轰开，防止玩家长时间贴身输出。
    private void performVoidPulse() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        this.attackCooldown = 42;
        this.setAttackState(ATTACK_STATE_VOID_PULSE, 12);
        // 虚空脉冲开始时触发一次收束再爆开的动画。
        this.triggerAnim("main", "void_pulse");

        serverLevel.sendParticles(ParticleTypes.DRAGON_BREATH, this.getX(), this.getY() + this.getBbHeight() * 0.45D, this.getZ(), 20, 0.45D, 0.25D, 0.45D, 0.02D);
        serverLevel.sendParticles(ParticleTypes.REVERSE_PORTAL, this.getX(), this.getY() + this.getBbHeight() * 0.45D, this.getZ(), 24, 0.55D, 0.35D, 0.55D, 0.04D);

        for (LivingEntity livingEntity : serverLevel.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(VOID_PULSE_RANGE), entity -> entity.isAlive() && entity != this)) {
            livingEntity.hurt(this.damageSources().magic(), 4.0F);
            Vec3 push = livingEntity.position().subtract(this.position());
            if (push.lengthSqr() > 1.0E-4) {
                Vec3 normalized = push.normalize().scale(0.42D);
                livingEntity.push(normalized.x, 0.12D, normalized.z);
            }
        }
    }

    // 半血时召唤一小群被标记过的末影鱼援军。
    private void castSummonFish(LivingEntity target) {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        this.summonedReinforcements = true;
        this.attackCooldown = 110;
        this.setAttackState(ATTACK_STATE_SUMMON, 24);
        // 召唤鱼群时触发抬手施法动画。
        this.triggerAnim("main", "summon");

        for (int i = 0; i < 3; i++) {
            EnderFish fish = new EnderFish(com.hp_end_expansion.registry.ModEntities.ENDER_FISH.get(), serverLevel);
            double angle = i * (Math.PI * 2.0D / 3.0D) + this.random.nextDouble() * 0.35D;
            double x = target.getX() + Math.cos(angle) * 3.2D;
            double z = target.getZ() + Math.sin(angle) * 3.2D;
            double y = target.getY() + 0.4D + this.random.nextDouble() * 1.2D;
            fish.moveTo(x, y, z, this.random.nextFloat() * 360.0F, 0.0F);
            fish.setSuppressDeathRift(true);
            fish.setTarget(target);
            serverLevel.addFreshEntity(fish);
            serverLevel.sendParticles(ParticleTypes.PORTAL, x, y + 0.2D, z, 16, 0.22D, 0.18D, 0.22D, 0.05D);
        }
    }

    // 根据移动向量更新领航者的水平朝向和俯仰角。
    private void updateRotationFromMovement(Vec3 movement) {
        double horizontal = movement.horizontalDistance();
        // 领航者模型的面具在负 Z 方向，背翼在正 Z 方向，所以这里把原版移动朝向翻转一百八十度，让头部朝向实际飞行方向。
        float targetYaw = Mth.wrapDegrees((float)(Mth.atan2(movement.z, movement.x) * Mth.RAD_TO_DEG) + 90.0F);
        float targetPitch = Mth.clamp(Mth.wrapDegrees((float)(-(Mth.atan2(movement.y, horizontal) * Mth.RAD_TO_DEG))), -55.0F, 55.0F);
        float yaw = Mth.rotLerp(0.32F, this.getYRot(), targetYaw);
        float pitch = Mth.rotLerp(0.32F, this.getXRot(), targetPitch);
        this.setYRot(yaw);
        this.setYBodyRot(yaw);
        this.setYHeadRot(yaw);
        this.setXRot(pitch);
    }

    // 用统一方法维护攻击状态和显示时长，方便后续对接动画控制器。
    private void setAttackState(int attackState, int ticks) {
        this.entityData.set(DATA_ATTACK_STATE, attackState);
        this.attackStateTicks = ticks;
    }

    // 服务端沿着起点到终点铺一条粒子线，用来表现光束或能量轨迹。
    private void sendLineParticles(ServerLevel level, Vec3 start, Vec3 end, net.minecraft.core.particles.ParticleOptions particle, int steps, double speed) {
        Vec3 delta = end.subtract(start);
        for (int i = 0; i <= steps; i++) {
            double progress = (double)i / (double)steps;
            Vec3 point = start.add(delta.scale(progress));
            level.sendParticles(particle, point.x, point.y, point.z, 1, 0.0D, 0.0D, 0.0D, speed);
        }
    }

    // 客户端持续给领航者补少量环境粒子，方便占位渲染阶段也有精英感。
    private void spawnClientParticles() {
        if (this.random.nextInt(3) == 0) {
            this.level().addParticle(ParticleTypes.PORTAL, this.getRandomX(0.65D), this.getY() + 0.9D + this.random.nextDouble() * 1.4D, this.getRandomZ(0.65D), 0.0D, 0.015D, 0.0D);
        }
        if (this.random.nextInt(8) == 0) {
            this.level().addParticle(ParticleTypes.END_ROD, this.getRandomX(0.45D), this.getY() + 1.1D + this.random.nextDouble() * 1.2D, this.getRandomZ(0.45D), 0.0D, 0.01D, 0.0D);
        }
        if (this.getAttackState() == ATTACK_STATE_BEAM && this.random.nextInt(2) == 0) {
            this.level().addParticle(ParticleTypes.REVERSE_PORTAL, this.getRandomX(0.35D), this.getY() + 1.25D, this.getRandomZ(0.35D), 0.0D, 0.01D, 0.0D);
        }
    }
}
