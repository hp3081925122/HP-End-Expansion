package org.hp.hp_end_expansion.entity.starwreck;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.hp.hp_end_expansion.entity.FlightAvoidance;
import org.hp.hp_end_expansion.registry.ModParticles;
import org.hp.hp_end_expansion.registry.ModStarwreck;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.instance.SingletonAnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;

/**
 * 余烬蛾（设计文档 9.1）。
 * 平时在地表上方 1～6 格飞，偏好余烬花和余烬星骸岩；玩家手持发光方块时绕着光源打转；受击后急速上升逃离并拖出火星。
 * 移动全部由 customServerAiStep 选目标点再交给 FlyingMoveControl，不用原版随机飞行 Goal，避免两套目标互相覆盖。
 */
public final class EmberMothEntity extends PathfinderMob implements GeoEntity {
    private static final EntityDataAccessor<Boolean> FLEEING = SynchedEntityData.defineId(EmberMothEntity.class, EntityDataSerializers.BOOLEAN);
    private static final RawAnimation FLY = RawAnimation.begin().thenLoop("animation.ember_moth.fly");
    private static final RawAnimation FLEE = RawAnimation.begin().thenLoop("animation.ember_moth.flee");
    private static final int FLEE_TICKS = 60;
    private static final double LIGHT_RANGE = 10;
    private final AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);
    private int fleeTicks;
    private int retargetTicks;
    private float orbitAngle;
    private Vec3 destination;
    private final FlightAvoidance flightAvoidance = new FlightAvoidance(this);

    public EmberMothEntity(EntityType<? extends EmberMothEntity> type, Level level) {
        super(type, level);
        moveControl = new FlyingMoveControl(this, 20, true);
        setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return createMobAttributes().add(Attributes.MAX_HEALTH, 4).add(Attributes.MOVEMENT_SPEED, 0.2).add(Attributes.FLYING_SPEED, 0.45);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(FLEEING, false);
    }

    public boolean isFleeing() { return entityData.get(FLEEING); }

    @Override protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
        navigation.setCanOpenDoors(false);
        navigation.setCanFloat(true);
        return navigation;
    }

    @Override protected void registerGoals() { goalSelector.addGoal(0, new FloatGoal(this)); }

    @Override public boolean hurt(DamageSource source, float amount) {
        boolean hit = super.hurt(source, amount);
        if (hit && !level().isClientSide) {
            fleeTicks = FLEE_TICKS;
            double angle = random.nextDouble() * Math.PI * 2;
            destination = position().add(Math.cos(angle) * 4, 7 + random.nextInt(4), Math.sin(angle) * 4);
        }
        return hit;
    }

    @Override protected void customServerAiStep() {
        super.customServerAiStep();
        setNoGravity(true);
        if (fleeTicks > 0) fleeTicks--;
        entityData.set(FLEEING, fleeTicks > 0);
        if (fleeTicks > 0) {
            if (tickCount % 2 == 0 && level() instanceof ServerLevel server)
                server.sendParticles(ModParticles.STAR_EMBER.get(), getX(), getY() + 0.25, getZ(), 1, 0.1, 0.05, 0.1, 0);
            if (destination != null) setFlightTarget(destination, 2.6);
            return;
        }
        Player lightHolder = level().getNearestPlayer(getX(), getY(), getZ(), LIGHT_RANGE,
            entity -> entity instanceof Player player && !player.isSpectator() && holdsLight(player));
        if (lightHolder != null) {
            // 绕光源打转：半径 1.6～2.4 格、带一点上下起伏的圈
            orbitAngle += 0.16F;
            double radius = 2.0 + Math.sin(tickCount * 0.05) * 0.4;
            Vec3 center = lightHolder.getEyePosition().add(0, -0.2, 0);
            setFlightTarget(center.add(Math.cos(orbitAngle) * radius, Math.sin(orbitAngle * 1.7) * 0.4,
                Math.sin(orbitAngle) * radius), 1.3);
            destination = null;
            return;
        }
        if (--retargetTicks <= 0 || destination == null || destination.distanceToSqr(position()) < 0.8) {
            retargetTicks = 60 + random.nextInt(60);
            destination = pickWanderTarget();
        }
        if (destination != null) setFlightTarget(destination, 1.0);
    }

    private void setFlightTarget(Vec3 target, double speed) {
        Vec3 at = flightAvoidance.steer(target);
        moveControl.setWantedPosition(at.x, at.y, at.z, speed);
    }

    // 优先飞到附近的余烬花或余烬星骸岩上方；找不到就在周围地表上方 1～6 格随机取点
    private Vec3 pickWanderTarget() {
        BlockPos base = blockPosition();
        for (int attempt = 0; attempt < 12; attempt++) {
            BlockPos probe = base.offset(random.nextInt(17) - 8, random.nextInt(9) - 6, random.nextInt(17) - 8);
            if (!level().hasChunkAt(probe)) continue;
            BlockState state = level().getBlockState(probe);
            if (state.is(ModStarwreck.EMBERBLOOM.get()) || state.is(ModStarwreck.EMBER_STARWRECK_STONE.get()) && level().isEmptyBlock(probe.above()))
                return Vec3.atCenterOf(probe).add(random.nextDouble() - 0.5, 1.2 + random.nextDouble(), random.nextDouble() - 0.5);
        }
        BlockPos column = base.offset(random.nextInt(15) - 7, 0, random.nextInt(15) - 7);
        if (!level().hasChunkAt(column)) return null;
        int surface = level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
        // 下方是虚空（岛屿边缘外）时不往外飞，只在当前高度附近平移
        if (surface <= level().getMinBuildHeight()) return position().add(random.nextDouble() * 4 - 2, random.nextDouble() - 0.5, random.nextDouble() * 4 - 2);
        return new Vec3(column.getX() + 0.5, surface + 1 + random.nextInt(6), column.getZ() + 0.5);
    }

    private static boolean holdsLight(Player player) { return emitsLight(player.getMainHandItem()) || emitsLight(player.getOffhandItem()); }

    private static boolean emitsLight(ItemStack stack) {
        return stack.getItem() instanceof BlockItem item && item.getBlock().defaultBlockState().getLightEmission() > 0;
    }

    @Override public boolean causeFallDamage(float distance, float multiplier, DamageSource source) { return false; }
    @Override protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {}
    @Override public boolean isPushable() { return false; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.BAT_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.BAT_DEATH; }
    @Override public float getVoicePitch() { return super.getVoicePitch() * 1.4F; }
    @Override protected float getSoundVolume() { return 0.4F; }

    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "base", 3, state -> state.setAndContinue(isFleeing() ? FLEE : FLY)));
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }

    // 落到地面时轻推向上，避免停在地上不起飞
    @Override public void aiStep() {
        super.aiStep();
        if (!level().isClientSide && onGround()) setDeltaMovement(getDeltaMovement().add(0, 0.06, 0));
    }
}
