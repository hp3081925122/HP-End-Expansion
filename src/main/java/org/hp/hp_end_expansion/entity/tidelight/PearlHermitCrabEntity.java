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

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.LevelReader;
import org.hp.hp_end_expansion.registry.*;

public final class PearlHermitCrabEntity extends Animal implements GeoEntity {
    private static final EntityDataAccessor<Boolean> SHELLED = SynchedEntityData.defineId(PearlHermitCrabEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> PEARL_READY = SynchedEntityData.defineId(PearlHermitCrabEntity.class, EntityDataSerializers.BOOLEAN);
    private int brushCooldown;
    public PearlHermitCrabEntity(EntityType<? extends PearlHermitCrabEntity> type, Level level) { super(type, level); }
    public static AttributeSupplier.Builder createAttributes() { return createMobAttributes().add(Attributes.MAX_HEALTH, 10).add(Attributes.MOVEMENT_SPEED, 0.18); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { super.defineSynchedData(builder); builder.define(SHELLED, false); builder.define(PEARL_READY, true); }
    public boolean isShelled() { return entityData.get(SHELLED); }
    public boolean hasPearl() { return entityData.get(PEARL_READY); }
    @Override public float getWalkTargetValue(BlockPos pos, LevelReader level) {
        return level.getBlockState(pos.below()).is(ModTidelight.PEARL_SAND.get()) ? 10 : 0;
    }
    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new BreedGoal(this, 1));
        goalSelector.addGoal(2, new TemptGoal(this, 1.15, this::isFood, false));
        goalSelector.addGoal(3, new FollowParentGoal(this, 1));
        goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(5, new RandomLookAroundGoal(this));
    }
    @Override public boolean isFood(ItemStack stack) { return stack.is(ModTidelight.GLOWKELP_POD.get()); }
    @Override public PearlHermitCrabEntity getBreedOffspring(ServerLevel level, AgeableMob parent) { return TidelightEntities.PEARL_HERMIT_CRAB.get().create(level); }
    @Override protected void customServerAiStep() {
        super.customServerAiStep();
        if (brushCooldown > 0 && --brushCooldown == 0) entityData.set(PEARL_READY, true);
        if (tickCount % 5 == 0) {
            Player near = level().getNearestPlayer(this, 3);
            boolean friendly = near != null && (isFood(near.getMainHandItem()) || isFood(near.getOffhandItem()));
            entityData.set(SHELLED, near != null && !friendly && !isInLove());
        }
        if (isShelled()) { navigation.stop(); moveControl.setWantedPosition(getX(), getY(), getZ(), 0); setSpeed(0); setDeltaMovement(0, getDeltaMovement().y, 0); }
    }
    @Override public boolean hurt(DamageSource source, float amount) { return super.hurt(source, isShelled() ? amount * 0.2F : amount); }
    @Override public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.is(Items.BRUSH) && !isBaby()) {
            if (!level().isClientSide && brushCooldown == 0) {
                spawnAtLocation(ModTidelight.TIDE_PEARL.get()); brushCooldown = 12000; entityData.set(PEARL_READY, false);
                stack.hurtAndBreak(1, player, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        return super.mobInteract(player, hand);
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); tag.putInt("PearlCooldown", brushCooldown); }
    @Override public void readAdditionalSaveData(CompoundTag tag) { super.readAdditionalSaveData(tag); brushCooldown = Math.max(0, tag.getInt("PearlCooldown")); entityData.set(PEARL_READY, brushCooldown == 0); }

    private final AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.pearl_hermit_crab.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.pearl_hermit_crab.walk");
    private static final RawAnimation RETRACT = RawAnimation.begin().thenPlayAndHold("animation.pearl_hermit_crab.retract");
    // 缩壳状态已同步到客户端；离开缩壳后由控制器过渡回待机或横走。
    // 横走步频按成体闲逛移速定；幼体渲染成一半大、步幅减半，所以动画放快一倍才不滑步
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "base", 4, state -> {
            if (isShelled()) return state.setAndContinue(RETRACT);
            return state.setAndContinue(state.isMoving() ? WALK : IDLE);
        }).setAnimationSpeedHandler(crab -> crab.isBaby() ? 2.0 : 1.0));
    }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }

}
