package org.hp.hp_end_expansion.entity.tidelight;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.*;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;

import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.LevelReader;
import org.hp.hp_end_expansion.block.tidelight.ReefstoneBlock;

public final class ReefEelEntity extends Monster implements GeoEntity {
    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(ReefEelEntity.class, EntityDataSerializers.INT);
    private BlockPos perch;
    private int phaseTicks;
    private Vec3 retreat;
    public ReefEelEntity(EntityType<? extends ReefEelEntity> type, Level level) {
        super(type, level); setNoGravity(true); xpReward = 5;
        moveControl = new FlyingMoveControl(this, 30, true); navigation = new FlyingPathNavigation(this, level);
    }
    public static AttributeSupplier.Builder createAttributes() {
        return createMonsterAttributes().add(Attributes.MAX_HEALTH, 24).add(Attributes.ATTACK_DAMAGE, 5).add(Attributes.MOVEMENT_SPEED, 0.3).add(Attributes.FLYING_SPEED, 0.65).add(Attributes.FOLLOW_RANGE, 16);
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { super.defineSynchedData(builder); builder.define(PHASE, 0); }
    public int attackPhase() { return entityData.get(PHASE); }
    public boolean isWarning() { return attackPhase() == 1; }
    @Override public float getWalkTargetValue(BlockPos pos, LevelReader level) { return 0; }
    @Override public boolean causeFallDamage(float distance, float multiplier, DamageSource source) { return false; }
    @Override protected void customServerAiStep() {
        super.customServerAiStep(); setNoGravity(true);
        if (perch == null) {
            perch = blockPosition();
            double best = Double.MAX_VALUE;
            for (BlockPos at : BlockPos.betweenClosed(blockPosition().offset(-5, -4, -5), blockPosition().offset(5, 10, 5))) {
                if (!level().hasChunkAt(at) || !ReefstoneBlock.isReef(level().getBlockState(at)) || !level().isEmptyBlock(at.above())) continue;
                double distance = at.distSqr(blockPosition());
                if (distance < best) { best = distance; perch = at.above().immutable(); }
            }
        }
        Vec3 home = Vec3.atBottomCenterOf(perch);
        LivingEntity target = getTarget();
        if (target != null && (!target.isAlive() || target.position().distanceToSqr(home) > 256 || position().distanceToSqr(home) > 256 || target instanceof Player player && (player.isCreative() || player.isSpectator()))) {
            setTarget(null); target = null; entityData.set(PHASE, 0); phaseTicks = 0;
        }
        if (target == null && tickCount % 10 == 0) {
            Player player = level().getNearestPlayer(getX(), getY(), getZ(), 6, p -> p instanceof Player candidate && !candidate.isCreative() && !candidate.isSpectator() && candidate.isAlive());
            if (player != null && player.position().distanceToSqr(home) <= 256 && getSensing().hasLineOfSight(player)) { setTarget(player); target = player; }
        }
        if (target == null) {
            if (position().distanceToSqr(home) < 0.04D) {
                moveControl.setWantedPosition(getX(), getY(), getZ(), 0.0D);
                setDeltaMovement(Vec3.ZERO);
            } else {
                moveControl.setWantedPosition(home.x, home.y, home.z, 0.6);
            }
            return;
        }
        getLookControl().setLookAt(target, 45, 45);
        if (phaseTicks > 0) phaseTicks--;
        int phase = attackPhase();
        if (phase == 0) { entityData.set(PHASE, 1); phaseTicks = 12; return; }
        if (phase == 1) {
            moveControl.setWantedPosition(getX(), getY(), getZ(), 0);
            setDeltaMovement(getDeltaMovement().scale(0.5));
            if (phaseTicks == 0) { entityData.set(PHASE, 2); phaseTicks = 24; }
            return;
        }
        double speed = Math.max(0.35, 1 - Math.sqrt(position().distanceToSqr(home)) / 24);
        if (phase == 2) {
            Vec3 towards = target.position().add(0, target.getBbHeight() * 0.55, 0);
            Vec3 delta = towards.subtract(position());
            Vec3 curve = new Vec3(-delta.z, 0.35, delta.x).normalize().scale(Math.sin(phaseTicks / 24.0 * Math.PI) * 1.8);
            Vec3 at = towards.add(curve);
            moveControl.setWantedPosition(at.x, at.y, at.z, 1.5 * speed);
            boolean bite = distanceToSqr(target) < 3 && getSensing().hasLineOfSight(target);
            if (bite && doHurtTarget(target)) triggerAnim("action", "bite");
            if (bite || phaseTicks == 0) {
                Vec3 away = position().subtract(target.position()).normalize();
                if (away.lengthSqr() < 0.01) away = new Vec3(1, 0, 0);
                retreat = position().add(away.scale(3.5)).add(0, 1, 0);
                if (retreat.distanceToSqr(home) > 225) retreat = home;
                entityData.set(PHASE, 3); phaseTicks = 30;
            }
        } else {
            if (retreat == null) retreat = home;
            moveControl.setWantedPosition(retreat.x, retreat.y, retreat.z, speed);
            if (phaseTicks == 0) entityData.set(PHASE, 0);
        }
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); if (perch != null) tag.putLong("Perch", perch.asLong()); }
    @Override public void readAdditionalSaveData(CompoundTag tag) { super.readAdditionalSaveData(tag); if (tag.contains("Perch")) perch = BlockPos.of(tag.getLong("Perch")); entityData.set(PHASE, 0); phaseTicks = 0; }

    private final AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);
    private static final RawAnimation SWIM = RawAnimation.begin().thenLoop("animation.reef_eel.swim");
    private static final RawAnimation LUNGE = RawAnimation.begin().thenLoop("animation.reef_eel.lunge");
    private static final RawAnimation COIL = RawAnimation.begin().thenLoop("animation.reef_eel.coil");
    private static final RawAnimation WARN = RawAnimation.begin().thenPlayAndHold("animation.reef_eel.warn");
    private static final RawAnimation BITE = RawAnimation.begin().thenPlay("animation.reef_eel.bite");
    // 动作阶段是同步数据：0 栖息／归巢，1 预警，2 接近撕咬，3 后撤；咬中时由服务端触发 bite
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "base", 4, state -> {
            int phase = attackPhase();
            if (phase == 1) return state.setAndContinue(WARN);
            if (phase == 2) return state.setAndContinue(LUNGE);
            return state.setAndContinue(phase == 3 || state.isMoving() ? SWIM : COIL);
        }));
        controllers.add(new AnimationController<>(this, "action", 0, state -> PlayState.STOP).triggerableAnim("bite", BITE));
    }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }

    // 客户端记录最近的身体朝向，模型按它给后面几节加延迟偏航，转身时身体一节节跟上
    private final float[] yawTrail = new float[12];
    private boolean trailReady;
    @Override public void tick() {
        super.tick();
        if (!level().isClientSide) return;
        if (!trailReady) { java.util.Arrays.fill(yawTrail, yBodyRot); trailReady = true; }
        System.arraycopy(yawTrail, 0, yawTrail, 1, yawTrail.length - 1);
        yawTrail[0] = yBodyRot;
    }
    public float trailYaw(int ticksAgo, float partialTick) {
        int i = Math.min(ticksAgo, yawTrail.length - 2);
        return net.minecraft.util.Mth.rotLerp(partialTick, yawTrail[i + 1], yawTrail[i]);
    }
    // 身体拖在碰撞箱后面近两格，放大剔除范围，避免只看得到尾巴时整条消失
    @Override public net.minecraft.world.phys.AABB getBoundingBoxForCulling() { return getBoundingBox().inflate(2.5, 1.0, 2.5); }

}
