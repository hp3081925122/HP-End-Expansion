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
import software.bernie.geckolib.animation.RawAnimation;

import net.minecraft.world.level.levelgen.Heightmap;

public final class LanternJellyfishEntity extends PathfinderMob implements GeoEntity {
    private static final EntityDataAccessor<Boolean> FLEEING = SynchedEntityData.defineId(LanternJellyfishEntity.class, EntityDataSerializers.BOOLEAN);
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.lantern_jellyfish.idle");
    private static final RawAnimation FLEE = RawAnimation.begin().thenLoop("animation.lantern_jellyfish.flee");
    private int fleeTicks;
    private Vec3 destination;
    public LanternJellyfishEntity(EntityType<? extends LanternJellyfishEntity> type, Level level) {
        super(type, level); setNoGravity(true);
        moveControl = new FlyingMoveControl(this, 12, true);
        navigation = new FlyingPathNavigation(this, level);
    }
    public static AttributeSupplier.Builder createAttributes() {
        return createMobAttributes().add(Attributes.MAX_HEALTH, 8).add(Attributes.MOVEMENT_SPEED, 0.12).add(Attributes.FLYING_SPEED, 0.22);
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { super.defineSynchedData(builder); builder.define(FLEEING, false); }
    // 受击后的逃离期同步给客户端，用来切换快速脉动的逃离动画
    public boolean isFleeing() { return entityData.get(FLEEING); }
    @Override public boolean causeFallDamage(float distance, float multiplier, DamageSource source) { return false; }
    @Override public boolean hurt(DamageSource source, float amount) {
        boolean hit = super.hurt(source, amount);
        if (hit && !level().isClientSide) { fleeTicks = 60; destination = position().add(0, 8, 0); }
        return hit;
    }
    @Override protected void customServerAiStep() {
        super.customServerAiStep(); setNoGravity(true);
        if (fleeTicks > 0) fleeTicks--;
        entityData.set(FLEEING, fleeTicks > 0);
        if (tickCount % 10 != 0) return;
        if (destination == null || tickCount % 80 == 0 || destination.distanceToSqr(position()) < 1) {
            BlockPos candidate = blockPosition().offset(random.nextInt(13) - 6, 0, random.nextInt(13) - 6);
            if (!level().hasChunkAt(candidate)) return;
            int surface = level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, candidate.getX(), candidate.getZ());
            destination = surface > 20 ? new Vec3(candidate.getX() + 0.5, surface + 3 + random.nextInt(10), candidate.getZ() + 0.5) : position().add(0, 1, 0);
        }
        Vec3 push = Vec3.ZERO;
        for (LanternJellyfishEntity other : level().getEntitiesOfClass(LanternJellyfishEntity.class, getBoundingBox().inflate(3))) {
            if (other == this) continue;
            Vec3 delta = position().subtract(other.position());
            if (delta.lengthSqr() > 0.01) push = push.add(delta.normalize().scale(0.5));
        }
        Vec3 at = destination.add(push);
        moveControl.setWantedPosition(at.x, at.y, at.z, fleeTicks > 0 ? 2.5 : 1);
    }

    private final AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "base", 6, state -> state.setAndContinue(isFleeing() ? FLEE : IDLE)));
    }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }

}
