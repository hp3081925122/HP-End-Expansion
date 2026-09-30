package org.hp.hp_end_expansion.entity;

import com.mojang.logging.LogUtils;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
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
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.hp.hp_end_expansion.registry.ModParticles;
import org.slf4j.Logger;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

public final class RiftMantisEntity extends Monster implements GeoEntity {
    private static final Logger LOGGER = LogUtils.getLogger();

    // 动画定义
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.rift_mantis.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.rift_mantis.walk");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold("animation.rift_mantis.death");
    private static final RawAnimation SLASH = RawAnimation.begin().thenPlay("animation.rift_mantis.slash");
    private static final RawAnimation BLINK = RawAnimation.begin().thenPlay("animation.rift_mantis.blink");
    private static final RawAnimation BLADE = RawAnimation.begin().thenPlay("animation.rift_mantis.blade");
    private static final RawAnimation TEAR = RawAnimation.begin().thenPlay("animation.rift_mantis.tear");
    private static final RawAnimation HURT = RawAnimation.begin().thenPlay("animation.rift_mantis.hurt");

    // 技能编号：0 为空闲
    public static final int SKILL_NONE = 0;
    public static final int SKILL_SLASH = 1;
    public static final int SKILL_BLINK = 2;
    public static final int SKILL_BLADE = 3;
    public static final int SKILL_TEAR = 4;

    // 同步字段：当前技能与技能已进行 tick
    private static final EntityDataAccessor<Integer> SKILL = SynchedEntityData.defineId(RiftMantisEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SKILL_TICK = SynchedEntityData.defineId(RiftMantisEntity.class, EntityDataSerializers.INT);

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);

    // 各技能冷却
    private int slashCooldown;
    private int blinkCooldown = 40;
    private int bladeCooldown = 60;
    private int tearCooldown = 120;
    // 闪现目的地
    private Vec3 blinkTarget;
    // 螳后召唤物：无掉落、无经验
    private boolean minion;

    public RiftMantisEntity(EntityType<? extends RiftMantisEntity> entityType, Level level) {
        super(entityType, level);
        this.xpReward = 40;
    }

    // 精英怪属性
    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 160.0D)
            .add(Attributes.MOVEMENT_SPEED, 0.3D)
            .add(Attributes.ATTACK_DAMAGE, 12.0D)
            .add(Attributes.ARMOR, 10.0D)
            .add(Attributes.FOLLOW_RANGE, 40.0D)
            .add(Attributes.KNOCKBACK_RESISTANCE, 0.8D)
            .add(Attributes.STEP_HEIGHT, 1.25D);
    }

    // 转为螳后召唤物，设置生命上限
    public void makeMinion(float health) {
        this.minion = true;
        this.xpReward = 0;
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
        this.setHealth(health);
        this.setPersistenceRequired();
    }

    public boolean isMinion() {
        return this.minion;
    }

    @Override
    protected boolean shouldDropLoot() {
        return !this.minion && super.shouldDropLoot();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SKILL, SKILL_NONE);
        builder.define(SKILL_TICK, 0);
    }

    @Override
    protected void registerGoals() {
        // 行为目标：技能优先，其次游荡与观察
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new RiftCombatGoal(this));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 10.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        // 受击后反击
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this, RiftMantisEntity.class, RiftMatriarchEntity.class));
    }

    public int getSkill() {
        return this.entityData.get(SKILL);
    }

    public int getSkillTick() {
        return this.entityData.get(SKILL_TICK);
    }

    public boolean isCasting() {
        return this.getSkill() != SKILL_NONE;
    }

    // 启动技能：设置同步状态并触发动画
    private void startSkill(int skill, String anim) {
        this.entityData.set(SKILL, skill);
        this.entityData.set(SKILL_TICK, 0);
        this.getNavigation().stop();
        this.triggerAnim("skill", anim);
        LOGGER.debug("Rift Mantis {} starts skill {} ({})", this.getId(), skill, anim);
    }

    private void endSkill() {
        this.entityData.set(SKILL, SKILL_NONE);
        this.entityData.set(SKILL_TICK, 0);
    }

    @Override
    public void tick() {
        super.tick();
        // 冷却计时
        if (!this.level().isClientSide()) {
            if (this.slashCooldown > 0) {
                this.slashCooldown--;
            }
            if (this.blinkCooldown > 0) {
                this.blinkCooldown--;
            }
            if (this.bladeCooldown > 0) {
                this.bladeCooldown--;
            }
            if (this.tearCooldown > 0) {
                this.tearCooldown--;
            }
        }
        // 客户端：技能蓄力时镰刃附近溢出火花
        if (this.level().isClientSide() && this.isCasting()) {
            this.spawnChargeSparks();
        }
    }

    // 蓄力火花：只在各技能的前摇窗口内少量产生
    private void spawnChargeSparks() {
        int skill = this.getSkill();
        int t = this.getSkillTick();
        boolean charging = false;
        if (skill == SKILL_SLASH && t < 7) {
            charging = true;
        }
        if (skill == SKILL_BLADE && t < 8) {
            charging = true;
        }
        if (skill == SKILL_TEAR && t < 11) {
            charging = true;
        }
        if (!charging || this.random.nextInt(2) != 0) {
            return;
        }
        float yaw = this.yBodyRot * Mth.DEG_TO_RAD;
        Vec3 forward = new Vec3(-Mth.sin(yaw), 0.0D, Mth.cos(yaw));
        Vec3 side = new Vec3(Mth.cos(yaw), 0.0D, Mth.sin(yaw));
        double sign = 1.0D;
        if (this.random.nextBoolean()) {
            sign = -1.0D;
        }
        Vec3 p = this.position().add(forward.scale(1.05D)).add(side.scale(0.825D * sign)).add(0.0D, 2.4D + this.random.nextDouble() * 0.75D, 0.0D);
        this.level().addParticle(ModParticles.RIFT_SPARK.get(), p.x, p.y, p.z, 0.0D, 0.02D, 0.0D);
    }

    // 技能逐 tick 推进，由战斗目标调用
    void tickSkill(LivingEntity target) {
        int skill = this.getSkill();
        int t = this.getSkillTick() + 1;
        this.entityData.set(SKILL_TICK, t);
        // 持续朝向目标：身体、头部同步
        this.faceTarget(target);
        if (skill == SKILL_SLASH) {
            this.tickSlash(target, t);
        } else if (skill == SKILL_BLINK) {
            this.tickBlink(target, t);
        } else if (skill == SKILL_BLADE) {
            this.tickBlade(target, t);
        } else if (skill == SKILL_TEAR) {
            this.tickTear(target, t);
        }
    }

    // 朝向目标，覆盖身体惯性转向
    void faceTarget(LivingEntity target) {
        double dx = target.getX() - this.getX();
        double dz = target.getZ() - this.getZ();
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        float turned = Mth.approachDegrees(this.getYRot(), yaw, 30.0F);
        this.setYRot(turned);
        this.yBodyRot = turned;
        this.yHeadRot = turned;
        this.getLookControl().setLookAt(target, 30.0F, 30.0F);
    }

    private Vec3 forwardVec() {
        float yaw = this.getYRot() * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(yaw), 0.0D, Mth.cos(yaw));
    }

    // 双镰斩：0.45s 命中帧 = 第 9 tick
    private void tickSlash(LivingEntity target, int t) {
        if (t == 6) {
            this.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 0.6F);
        }
        if (t == 9) {
            Vec3 forward = this.forwardVec();
            Vec3 center = this.position().add(forward.scale(2.4D)).add(0.0D, 1.95D, 0.0D);
            // 弧光特效：左右两道交叉
            RiftVfxEntity.spawn(this.level(), RiftVfxEntity.KIND_SLASH, center.x, center.y, center.z, this.getYRot(), 18.0F, 1.5F, 8);
            RiftVfxEntity.spawn(this.level(), RiftVfxEntity.KIND_SLASH, center.x, center.y - 0.225D, center.z, this.getYRot(), -18.0F, 1.35F, 8);
            this.hitCone(forward, 5.1D, 0.35D, (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE), 0.6D);
        }
        if (t >= 18) {
            this.slashCooldown = 20;
            this.endSkill();
        }
    }

    // 裂隙闪现：第 7 tick 收缩后瞬移到目标侧后方，第 12 tick 出现并斩击
    private void tickBlink(LivingEntity target, int t) {
        if (t == 2) {
            this.blinkTarget = this.findBlinkSpot(target);
            Vec3 here = this.position();
            RiftVfxEntity.spawn(this.level(), RiftVfxEntity.KIND_PORTAL, here.x, here.y, here.z, this.getYRot(), 0.0F, 1.5F, 14);
            this.playSound(SoundEvents.EVOKER_PREPARE_ATTACK, 1.0F, 1.6F);
        }
        if (t == 8) {
            if (this.blinkTarget != null) {
                Vec3 dest = this.blinkTarget;
                RiftVfxEntity.spawn(this.level(), RiftVfxEntity.KIND_PORTAL, dest.x, dest.y, dest.z, this.getYRot(), 0.0F, 1.5F, 12);
                this.teleportTo(dest.x, dest.y, dest.z);
                this.level().playSound(null, dest.x, dest.y, dest.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0F, 1.2F);
                LOGGER.debug("Rift Mantis {} blinked to {}", this.getId(), dest);
            }
            this.faceTarget(target);
        }
        if (t == 13) {
            Vec3 forward = this.forwardVec();
            Vec3 center = this.position().add(forward.scale(2.25D)).add(0.0D, 1.8D, 0.0D);
            RiftVfxEntity.spawn(this.level(), RiftVfxEntity.KIND_SLASH, center.x, center.y, center.z, this.getYRot(), 25.0F, 1.65F, 8);
            this.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 0.8F);
            this.hitCone(forward, 4.8D, 0.3D, (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE) * 1.2F, 0.4D);
        }
        if (t >= 28) {
            this.blinkCooldown = 100;
            this.slashCooldown = 10;
            this.endSkill();
        }
    }

    // 寻找目标背后可站立的位置，失败则退回目标侧面
    private Vec3 findBlinkSpot(LivingEntity target) {
        Vec3 look = target.getLookAngle();
        Vec3 back = new Vec3(-look.x, 0.0D, -look.z);
        if (back.lengthSqr() < 1.0E-4D) {
            back = this.position().subtract(target.position()).multiply(1.0D, 0.0D, 1.0D);
        }
        back = back.normalize();
        Vec3 side = new Vec3(-back.z, 0.0D, back.x);
        Vec3[] candidates = new Vec3[] {back, back.add(side).normalize(), back.subtract(side).normalize(), side, side.scale(-1.0D)};
        for (Vec3 dir : candidates) {
            for (int dy = 1; dy >= -2; dy--) {
                Vec3 spot = target.position().add(dir.scale(3.4D)).add(0.0D, dy, 0.0D);
                BlockPos pos = BlockPos.containing(spot);
                BlockState below = this.level().getBlockState(pos.below());
                if (!below.isFaceSturdy(this.level(), pos.below(), net.minecraft.core.Direction.UP)) {
                    continue;
                }
                Vec3 feet = new Vec3(spot.x, pos.getY(), spot.z);
                AABB box = this.getDimensions(this.getPose()).makeBoundingBox(feet);
                if (this.level().noCollision(this, box) && !this.level().containsAnyLiquid(box)) {
                    return feet;
                }
            }
        }
        return null;
    }

    // 裂隙飞刃：第 10 tick 挥出，连发三道扇形
    private void tickBlade(LivingEntity target, int t) {
        if (t == 7) {
            this.playSound(SoundEvents.EVOKER_CAST_SPELL, 1.0F, 1.5F);
        }
        if (t == 10) {
            Vec3 origin = this.position().add(this.forwardVec().scale(1.8D)).add(0.0D, 1.95D, 0.0D);
            Vec3 aim = target.getEyePosition().subtract(0.0D, 0.4D, 0.0D).subtract(origin).normalize();
            for (int i = -1; i <= 1; i++) {
                Vec3 dir = aim.yRot(i * 12.0F * Mth.DEG_TO_RAD);
                RiftBladeEntity.launch(this, origin, dir, 1.1F);
            }
            this.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 1.4F);
        }
        if (t >= 20) {
            this.bladeCooldown = 80;
            this.endSkill();
        }
    }

    // 裂地突刺：0.7s 砸地 = 第 14 tick 生成地裂
    private void tickTear(LivingEntity target, int t) {
        if (t == 4) {
            this.playSound(SoundEvents.WARDEN_SONIC_CHARGE, 0.8F, 1.6F);
        }
        if (t == 14) {
            Vec3 forward = this.forwardVec();
            Vec3 start = this.position().add(forward.scale(2.1D));
            RiftFissureEntity.spawn(this, start, this.getYRot(), 12.0F, 1.5F);
            if (this.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ModParticles.RIFT_SHARD.get(), start.x, start.y + 0.2D, start.z, 13, 0.75D, 0.15D, 0.75D, 0.22D);
            }
            this.playSound(SoundEvents.GENERIC_EXPLODE.value(), 1.0F, 1.1F);
        }
        if (t >= 30) {
            this.tearCooldown = 160;
            this.endSkill();
        }
    }

    // 前方扇形判定
    private void hitCone(Vec3 forward, double range, double minDot, float damage, double knock) {
        AABB area = this.getBoundingBox().inflate(range, 1.5D, range);
        for (LivingEntity victim : this.level().getEntitiesOfClass(LivingEntity.class, area)) {
            if (victim == this || victim instanceof RiftMantisEntity || victim instanceof RiftMatriarchEntity || !victim.isAlive()) {
                continue;
            }
            Vec3 rel = victim.position().subtract(this.position()).multiply(1.0D, 0.0D, 1.0D);
            double dist = rel.length();
            if (dist > range + victim.getBbWidth() * 0.5D) {
                continue;
            }
            if (dist > 0.3D && rel.normalize().dot(forward) < minDot) {
                continue;
            }
            if (victim.hurt(this.damageSources().mobAttack(this), damage)) {
                victim.knockback(knock, -forward.x, -forward.z);
                victim.addEffect(new MobEffectInstance(MobEffects.WITHER, 30, 0), this);
            }
        }
    }

    // 选择技能：由战斗目标在空闲时调用
    boolean tryStartSkill(LivingEntity target) {
        double dist = this.distanceTo(target);
        boolean sight = this.hasLineOfSight(target);
        if (this.tearCooldown <= 0 && sight && dist > 4.5D && dist < 12.0D && this.random.nextInt(3) == 0) {
            this.startSkill(SKILL_TEAR, "tear");
            return true;
        }
        if (this.blinkCooldown <= 0 && dist > 7.0D && dist < 20.0D) {
            this.startSkill(SKILL_BLINK, "blink");
            return true;
        }
        if (this.bladeCooldown <= 0 && sight && dist > 6.0D && dist < 19.0D) {
            this.startSkill(SKILL_BLADE, "blade");
            return true;
        }
        if (this.slashCooldown <= 0 && dist < 4.6D) {
            this.startSkill(SKILL_SLASH, "slash");
            return true;
        }
        return false;
    }

    // 目标失效时中止技能
    void abortSkill() {
        if (this.isCasting()) {
            LOGGER.debug("Rift Mantis {} aborts skill {}", this.getId(), this.getSkill());
            this.endSkill();
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean result = super.hurt(source, amount);
        if (result && !this.level().isClientSide() && source.getEntity() instanceof Player player) {
            LOGGER.debug("Rift Mantis {} was damaged by player {}; retaliation target is available", this.getId(), player.getGameProfile().getName());
        }
        // 空闲状态受击播放短动画
        if (result && !this.level().isClientSide() && !this.isCasting() && this.isAlive()) {
            this.triggerAnim("skill", "hurt");
        }
        return result;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        // 死亡时裂隙碎裂
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ModParticles.RIFT_SHARD.get(), this.getX(), this.getY() + 1.5D, this.getZ(), 26, 0.9D, 0.75D, 0.9D, 0.18D);
        }
    }

    @Override
    protected void tickDeath() {
        this.deathTime++;
        // 死亡动画 1 秒后化为碎片消失
        if (this.deathTime >= 24 && !this.level().isClientSide() && !this.isRemoved()) {
            if (this.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ModParticles.RIFT_SPARK.get(), this.getX(), this.getY() + 0.9D, this.getZ(), 22, 1.2D, 0.45D, 1.2D, 0.07D);
            }
            this.level().broadcastEntityEvent(this, (byte) 60);
            this.remove(RemovalReason.KILLED);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ENDERMITE_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.ENDERMITE_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENDERMITE_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(SoundEvents.ENDERMITE_STEP, 0.5F, 0.65F);
    }

    @Override
    public float getVoicePitch() {
        return 0.5F + this.random.nextFloat() * 0.1F;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("BlinkCooldown", this.blinkCooldown);
        tag.putInt("BladeCooldown", this.bladeCooldown);
        tag.putInt("TearCooldown", this.tearCooldown);
        tag.putBoolean("Minion", this.minion);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.blinkCooldown = tag.getInt("BlinkCooldown");
        this.bladeCooldown = tag.getInt("BladeCooldown");
        this.tearCooldown = tag.getInt("TearCooldown");
        this.minion = tag.getBoolean("Minion");
        if (this.minion) {
            this.xpReward = 0;
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // 基础控制器：死亡、移动、待机
        controllers.add(new AnimationController<>(this, "base", 5, state -> {
            if (this.isDeadOrDying()) {
                return state.setAndContinue(DEATH);
            }
            if (state.isMoving()) {
                return state.setAndContinue(WALK);
            }
            return state.setAndContinue(IDLE);
        }));
        // 技能控制器：只播放服务端触发的动画
        controllers.add(new AnimationController<>(this, "skill", 2, state -> PlayState.STOP)
            .triggerableAnim("slash", SLASH)
            .triggerableAnim("blink", BLINK)
            .triggerableAnim("blade", BLADE)
            .triggerableAnim("tear", TEAR)
            .triggerableAnim("hurt", HURT));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.animationCache;
    }

    // 战斗目标：追击、持续朝向、选择并推进技能
    static final class RiftCombatGoal extends Goal {
        private final RiftMantisEntity mantis;
        private int repathDelay;

        RiftCombatGoal(RiftMantisEntity mantis) {
            this.mantis = mantis;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = this.mantis.getTarget();
            return target != null && target.isAlive();
        }

        @Override
        public boolean canContinueToUse() {
            return this.canUse();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void start() {
            this.mantis.setAggressive(true);
            this.repathDelay = 0;
        }

        @Override
        public void stop() {
            this.mantis.setAggressive(false);
            this.mantis.abortSkill();
            this.mantis.getNavigation().stop();
        }

        @Override
        public void tick() {
            LivingEntity target = this.mantis.getTarget();
            if (target == null) {
                return;
            }
            // 施法中：锁定移动，推进技能
            if (this.mantis.isCasting()) {
                this.mantis.getNavigation().stop();
                this.mantis.tickSkill(target);
                return;
            }
            // 空闲：持续朝向目标并尝试技能
            this.mantis.getLookControl().setLookAt(target, 30.0F, 30.0F);
            if (this.mantis.tryStartSkill(target)) {
                return;
            }
            // 追击：距离过近时停步等待镰斩冷却
            double dist = this.mantis.distanceTo(target);
            if (dist < 3.0D) {
                this.mantis.getNavigation().stop();
                this.mantis.faceTarget(target);
                return;
            }
            if (--this.repathDelay <= 0) {
                this.repathDelay = 6 + this.mantis.getRandom().nextInt(5);
                this.mantis.getNavigation().moveTo(target, 1.15D);
            }
        }
    }
}
