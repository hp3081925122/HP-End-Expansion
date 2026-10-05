package org.hp.hp_end_expansion.entity.tidelight;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.*;
import software.bernie.geckolib.animation.*;

import java.util.EnumSet;

/**
 * 礁晶兽：潮光礁海的重甲四足兽。
 * 技能「潮吸吐息」：张口 → 周围六股水流螺旋卷进口中、口里水球越涨越大、附近生物被吸向它 → 一道高压水柱喷向目标，
 * 沿途推开并伤害，打到方块为止 → 收口。四段用同步的 PHASE 驱动，服务端判定和客户端动画/特效看同一个阶段。
 */
public final class ReefCrystalBeastEntity extends Monster implements GeoEntity {
    public static final int NONE = 0, OPEN = 1, CHARGE = 2, FIRE = 3, RECOVER = 4;
    public static final int OPEN_TICKS = 10, CHARGE_TICKS = 36, FIRE_TICKS = 24, RECOVER_TICKS = 12;
    public static final double JET_RANGE = 16, SUCTION_RANGE = 8;
    public static final int STREAMS = 6;
    private static final float JET_DAMAGE = 4;

    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(ReefCrystalBeastEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> AIM_YAW = SynchedEntityData.defineId(ReefCrystalBeastEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> AIM_PITCH = SynchedEntityData.defineId(ReefCrystalBeastEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> JET_LENGTH = SynchedEntityData.defineId(ReefCrystalBeastEntity.class, EntityDataSerializers.FLOAT);

    private int phaseTicks, gulpCooldown = 60;
    /** 客户端：当前阶段开始的 tickCount，以及上一 tick 的瞄准角，用来插值。 */
    private int phaseStart;
    public float aimYawO, aimPitchO;

    public ReefCrystalBeastEntity(EntityType<? extends ReefCrystalBeastEntity> type, Level level) {
        super(type, level); xpReward = 15;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return createMonsterAttributes().add(Attributes.MAX_HEALTH, 60).add(Attributes.ATTACK_DAMAGE, 7).add(Attributes.ARMOR, 6)
            .add(Attributes.MOVEMENT_SPEED, 0.22).add(Attributes.FOLLOW_RANGE, 24).add(Attributes.KNOCKBACK_RESISTANCE, 0.6)
            .add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(PHASE, NONE); builder.define(AIM_YAW, 0F); builder.define(AIM_PITCH, 0F); builder.define(JET_LENGTH, 0F);
    }

    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (PHASE.equals(key)) phaseStart = tickCount;
    }

    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new GulpGoal());
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.7));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    public int phase() { return entityData.get(PHASE); }
    public float aimYaw() { return entityData.get(AIM_YAW); }
    public float aimPitch() { return entityData.get(AIM_PITCH); }
    public float jetLength() { return entityData.get(JET_LENGTH); }
    /** 客户端：当前阶段已经过去的 tick 数（含插值）。 */
    public float phaseAge(float partialTick) { return tickCount - phaseStart + partialTick; }

    private void setPhase(int phase, int ticks) { entityData.set(PHASE, phase); phaseTicks = ticks; }

    /** 嘴在世界里的位置。张口仰头时嘴高 0.9 格、前伸 2.2 格；吐射时头压回来，嘴降到 0.72 格。数值取自 Blockbench 姿态实测。 */
    public static Vec3 mouthOffset(float yaw, int phase) {
        Vec3 f = Vec3.directionFromRotation(0, yaw);
        boolean low = phase == FIRE || phase == RECOVER;
        return f.scale(low ? 2.12 : 2.2).add(0, low ? 0.72 : 0.9, 0);
    }

    public Vec3 mouth() { return position().add(mouthOffset(yBodyRot, phase())); }

    /**
     * 第 i 股水流在参数 s（0 = 外侧源头，1 = 嘴）处相对实体脚底的位置；mouthRel 是嘴相对实体的偏移。
     * 源头贴着地面绕嘴一圈；水先从地面被直着拔起来（前段几乎只升不进），在半空拱过一道弧，再边旋边扎进嘴里。服务端不用，客户端渲染和水花粒子共用。
     */
    public static Vec3 streamPoint(int i, double s, float age, float yaw, Vec3 mouthRel) {
        double base = Math.toRadians(-yaw) + i * (Math.PI * 2 / STREAMS) + 0.35 * Math.sin(i * 1.7);
        double r0 = 5.0 + 1.0 * Math.sin(i * 2.3 + 1);
        double r = r0 * (1 - Math.pow(s, 1.5));
        double theta = base + 1.9 * s + age * 0.01;
        double y = 0.1 + (mouthRel.y - 0.1) * s * s + Math.sin(Math.PI * Math.pow(s, 0.75)) * (1.1 + 0.3 * Math.sin(i * 3.1));
        return new Vec3(mouthRel.x + Math.cos(theta) * r, y, mouthRel.z + Math.sin(theta) * r);
    }

    public Vec3 aimDirection() { return Vec3.directionFromRotation(aimPitch(), aimYaw()); }

    @Override public void tick() {
        aimYawO = aimYaw(); aimPitchO = aimPitch();
        super.tick();
        if (level().isClientSide) clientEffects();
    }

    @Override public void aiStep() {
        super.aiStep();
        if (!level().isClientSide && gulpCooldown > 0 && phase() == NONE) gulpCooldown--;
    }

    @Override public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit) triggerAnim("action", "bite");
        return hit;
    }

    // ---------------- 技能 ----------------
    private final class GulpGoal extends Goal {
        GulpGoal() { setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP)); }

        @Override public boolean canUse() {
            LivingEntity t = getTarget();
            if (t == null || !t.isAlive() || gulpCooldown > 0 || !onGround()) return false;
            double d = distanceToSqr(t);
            return d >= 16 && d <= 196 && getSensing().hasLineOfSight(t);
        }
        @Override public boolean canContinueToUse() { return phase() != NONE; }
        @Override public boolean isInterruptable() { return false; }
        @Override public boolean requiresUpdateEveryTick() { return true; }

        @Override public void start() {
            getNavigation().stop();
            entityData.set(AIM_YAW, yBodyRot); entityData.set(AIM_PITCH, 0F); entityData.set(JET_LENGTH, 0F);
            setPhase(OPEN, OPEN_TICKS);
            playSound(SoundEvents.DROWNED_AMBIENT_WATER, 1.6F, 0.55F);
        }

        @Override public void stop() {
            setPhase(NONE, 0); entityData.set(JET_LENGTH, 0F);
            gulpCooldown = 140 + random.nextInt(60);
        }

        @Override public void tick() {
            getNavigation().stop();
            setDeltaMovement(getDeltaMovement().multiply(0.4, 1, 0.4));
            LivingEntity t = getTarget();
            boolean valid = t != null && t.isAlive() && !(t instanceof Player p && (p.isCreative() || p.isSpectator()));
            int phase = phase();
            int elapsed = switch (phase) { case OPEN -> OPEN_TICKS; case CHARGE -> CHARGE_TICKS; case FIRE -> FIRE_TICKS; default -> RECOVER_TICKS; } - phaseTicks;
            // 目标在蓄力完成前丢失就收招；已经开始喷就沿当前方向喷完
            if (!valid && (phase == OPEN || phase == CHARGE)) { setPhase(RECOVER, RECOVER_TICKS); return; }
            switch (phase) {
                case OPEN -> turnTo(t, 8, 6);
                case CHARGE -> {
                    turnTo(t, 6, 4);
                    if (elapsed >= 8) suction();
                    if (elapsed % 8 == 0) playSound(SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_INSIDE, 1.4F, 0.7F + 0.6F * elapsed / CHARGE_TICKS);
                }
                case FIRE -> {
                    // 喷射时缓慢跟随，横向跑动可以躲开
                    if (valid) turnTo(t, 1.6F, 1.2F);
                    if (elapsed == 0) playSound(SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 2.2F, 0.6F);
                    if (elapsed % 4 == 0) playSound(SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, 1.6F, 0.8F);
                    jet(elapsed);
                }
                default -> {}
            }
            if (--phaseTicks > 0) return;
            switch (phase) {
                case OPEN -> setPhase(CHARGE, CHARGE_TICKS);
                case CHARGE -> setPhase(FIRE, FIRE_TICKS);
                case FIRE -> { setPhase(RECOVER, RECOVER_TICKS); entityData.set(JET_LENGTH, 0F); }
                default -> setPhase(NONE, 0);
            }
        }
    }

    /** 嘴对准目标胸口，偏航和俯仰各按每 tick 上限转过去；身体、头和实体朝向跟着瞄准偏航走。 */
    private void turnTo(LivingEntity target, float maxYaw, float maxPitch) {
        if (target == null) return;
        Vec3 m = position().add(mouthOffset(aimYaw(), phase()));
        Vec3 to = target.position().add(0, target.getBbHeight() * 0.55, 0).subtract(m);
        float yaw = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90F;
        float pitch = (float) -(Mth.atan2(to.y, to.horizontalDistance()) * Mth.RAD_TO_DEG);
        float newYaw = Mth.approachDegrees(aimYaw(), yaw, maxYaw);
        entityData.set(AIM_YAW, newYaw);
        entityData.set(AIM_PITCH, Mth.approach(aimPitch(), Mth.clamp(pitch, -30F, 30F), maxPitch));
        setYRot(newYaw); yBodyRot = newYaw; yHeadRot = newYaw;
    }

    private boolean affects(LivingEntity e) {
        return e != this && e.isAlive() && !(e instanceof Player p && (p.isCreative() || p.isSpectator())) && !(e instanceof ReefCrystalBeastEntity);
    }

    /** 蓄力：8 格内的生物被拉向嘴，离得越近拉得越猛；2.2 格内不再拉，避免卡进身体。 */
    private void suction() {
        Vec3 m = mouth();
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(SUCTION_RANGE), this::affects)) {
            Vec3 to = m.subtract(e.position().add(0, e.getBbHeight() * 0.5, 0));
            double d = to.length();
            if (d < 2.2 || d > SUCTION_RANGE) continue;
            double k = (0.03 + 0.05 * (1 - d / SUCTION_RANGE)) * (1 - e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) * 0.6);
            Vec3 v = to.normalize().scale(k);
            e.push(v.x, Math.max(0, v.y) * 0.3, v.z);
            e.hurtMarked = true;
        }
    }

    /** 吐射：每 tick 从嘴沿瞄准方向打一条射线，停在第一个方块；线上的生物被持续推开、灭火，每 10 tick 受一次伤。 */
    private void jet(int elapsed) {
        Vec3 from = mouth(), dir = aimDirection();
        BlockHitResult hit = level().clip(new ClipContext(from, from.add(dir.scale(JET_RANGE)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        double len = hit.getType() == HitResult.Type.MISS ? JET_RANGE : hit.getLocation().distanceTo(from);
        // 水柱头部每 tick 前进 5 格，打出去要一点时间，判定和画面一起往前走
        len = Math.min(len, (elapsed + 1) * 5.0);
        entityData.set(JET_LENGTH, (float) len);
        Vec3 end = from.add(dir.scale(len));
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, new AABB(from, end).inflate(1.0), this::affects)) {
            AABB box = e.getBoundingBox().inflate(0.35);
            if (!box.contains(from) && box.clip(from, end).isEmpty()) continue;
            double resist = 1 - e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) * 0.7;
            e.push(dir.x * 0.13 * resist, (dir.y * 0.13 + 0.02) * resist, dir.z * 0.13 * resist);
            e.hurtMarked = true;
            e.clearFire();
            if (elapsed % 10 == 0) e.hurt(damageSources().mobAttack(this), JET_DAMAGE);
        }
    }

    // ---------------- 客户端粒子：只做零碎水花，主体形状由渲染器画 ----------------
    private void clientEffects() {
        int phase = phase();
        float age = phaseAge(0);
        if (phase == CHARGE) {
            Vec3 m = mouth();
            int i = random.nextInt(STREAMS);
            if (age < CHARGE_TICKS * 0.85F) {
                Vec3 src = position().add(streamPoint(i, 0.02, tickCount, yBodyRot, mouthOffset(yBodyRot, CHARGE)));
                level().addParticle(ParticleTypes.SPLASH, src.x + random.nextGaussian() * 0.2, src.y, src.z + random.nextGaussian() * 0.2, 0, 0.1, 0);
            }
            if (random.nextInt(3) == 0) level().addParticle(ParticleTypes.FALLING_WATER, m.x + random.nextGaussian() * 0.15, m.y - 0.2, m.z + random.nextGaussian() * 0.15, 0, 0, 0);
        } else if (phase == FIRE && jetLength() > 0) {
            Vec3 end = mouth().add(aimDirection().scale(jetLength()));
            for (int k = 0; k < 3; k++)
                level().addParticle(ParticleTypes.SPLASH, end.x + random.nextGaussian() * 0.3, end.y + random.nextGaussian() * 0.3, end.z + random.nextGaussian() * 0.3,
                    random.nextGaussian() * 0.1, 0.2, random.nextGaussian() * 0.1);
        } else if (phase == RECOVER && random.nextInt(2) == 0) {
            Vec3 m = mouth();
            level().addParticle(ParticleTypes.DRIPPING_WATER, m.x + random.nextGaussian() * 0.2, m.y - 0.15, m.z + random.nextGaussian() * 0.2, 0, 0, 0);
        }
    }

    /** 水流半径 6 格、水柱 16 格，都远在碰撞箱外，放技能时放大剔除范围。 */
    @Override public AABB getBoundingBoxForCulling() {
        return phase() == NONE ? super.getBoundingBoxForCulling() : getBoundingBox().inflate(JET_RANGE);
    }

    @Override public void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); tag.putInt("GulpCooldown", gulpCooldown); }
    @Override public void readAdditionalSaveData(CompoundTag tag) { super.readAdditionalSaveData(tag); gulpCooldown = tag.getInt("GulpCooldown"); }

    // ---------------- 动画 ----------------
    private final AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);
    private static final String A = "animation.reef_crystal_beast.";
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop(A + "idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop(A + "walk");
    private static final RawAnimation BITE = RawAnimation.begin().thenPlay(A + "bite");
    private static final RawAnimation GULP_OPEN = RawAnimation.begin().thenPlayAndHold(A + "gulp_open");
    private static final RawAnimation GULP_CHARGE = RawAnimation.begin().thenLoop(A + "gulp_charge");
    private static final RawAnimation GULP_FIRE = RawAnimation.begin().thenLoop(A + "gulp_fire");
    private static final RawAnimation GULP_RECOVER = RawAnimation.begin().thenPlayAndHold(A + "gulp_recover");

    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // 放技能时基础控制器停下，由技能控制器接管脖子、头、下颌和尾巴，免得两边同时写同一根骨骼
        controllers.add(new AnimationController<>(this, "base", 4, state -> {
            if (phase() != NONE) return PlayState.STOP;
            return state.setAndContinue(state.isMoving() ? WALK : IDLE);
        }));
        controllers.add(new AnimationController<>(this, "skill", 3, state -> switch (phase()) {
            case OPEN -> state.setAndContinue(GULP_OPEN);
            case CHARGE -> state.setAndContinue(GULP_CHARGE);
            case FIRE -> state.setAndContinue(GULP_FIRE);
            case RECOVER -> state.setAndContinue(GULP_RECOVER);
            default -> PlayState.STOP;
        }));
        controllers.add(new AnimationController<>(this, "action", 0, state -> PlayState.STOP).triggerableAnim("bite", BITE));
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
