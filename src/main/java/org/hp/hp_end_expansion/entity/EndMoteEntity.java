package org.hp.hp_end_expansion.entity;

import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.instance.SingletonAnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;

public final class EndMoteEntity extends PathfinderMob implements GeoEntity {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final double FLYING_SPEED = 0.4D;
    private static final RawAnimation ANIM_IDLE = RawAnimation.begin().thenLoop("animation.end_mote.idle");
    private final AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);

    public EndMoteEntity(EntityType<? extends EndMoteEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.moveControl = new FlyingMoveControl(this, 20, true);
        this.navigation = new FlyingPathNavigation(this, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        LOGGER.debug("End Mote flying speed attribute registered: {}", FLYING_SPEED);
        return PathfinderMob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, 8.0)
            .add(Attributes.MOVEMENT_SPEED, 0.18)
            .add(Attributes.FLYING_SPEED, FLYING_SPEED);
    }

    @Override
    public void tick() {
        this.setNoGravity(true);
        super.tick();
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        LOGGER.debug("End Mote {} suppressed fall damage at distance {}", this.getId(), fallDistance);
        return false;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new PanicGoal(this, 1.4));
        goalSelector.addGoal(2, new EndMoteFlyingGoal(this));
        goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 6.0F));
        goalSelector.addGoal(4, new RandomLookAroundGoal(this));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "base", 5, state ->
            state.setAndContinue(ANIM_IDLE)
        ));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    private static final class EndMoteFlyingGoal extends WaterAvoidingRandomFlyingGoal {
        private final EndMoteEntity mote;

        private EndMoteFlyingGoal(EndMoteEntity mote) {
            super(mote, 1.0);
            this.mote = mote;
        }

        @Override
        public void start() {
            super.start();
            LOGGER.debug("End Mote {} started flying", this.mote.getId());
        }
    }
}
