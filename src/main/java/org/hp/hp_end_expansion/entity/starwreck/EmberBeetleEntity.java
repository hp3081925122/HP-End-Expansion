package org.hp.hp_end_expansion.entity.starwreck;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.hp.hp_end_expansion.registry.ModParticles;
import org.hp.hp_end_expansion.registry.StarwreckEntities;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.instance.SingletonAnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;

/**
 * 余烬甲虫。中立：平时不主动攻击，被打后反击，并让附近同类一起仇恨。
 * 死亡时鞘翅张开、爆出一圈火星，原地留下 2 秒的余烬地面（{@link EmberGroundEntity}）。
 */
public final class EmberBeetleEntity extends Monster implements GeoEntity {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.ember_beetle.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.ember_beetle.walk");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold("animation.ember_beetle.death");
    private static final RawAnimation ATTACK = RawAnimation.begin().thenPlay("animation.ember_beetle.attack");
    private final AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);

    public EmberBeetleEntity(EntityType<? extends EmberBeetleEntity> type, Level level) { super(type, level); }

    public static AttributeSupplier.Builder createAttributes() {
        return createMonsterAttributes().add(Attributes.MAX_HEALTH, 10).add(Attributes.ATTACK_DAMAGE, 3)
            .add(Attributes.MOVEMENT_SPEED, 0.26).add(Attributes.FOLLOW_RANGE, 16);
    }

    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.15, false));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        // 被打时反击，并通知附近同类一起仇恨
        targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
    }

    // 近战在命中的同一 tick 触发攻击动画；动画 0.18 s 处是颚合拢的命中帧，与伤害只差控制器过渡的一两帧
    @Override public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit) triggerAnim("action", "attack");
        return hit;
    }

    @Override public void die(DamageSource source) {
        boolean first = !dead && !isRemoved();
        super.die(source);
        if (first && level() instanceof ServerLevel server) {
            // 一圈向外爆开的火星，外加少量火焰
            for (int i = 0; i < 20; i++) {
                double angle = i * Math.PI * 2 / 20 + random.nextDouble() * 0.2;
                double speed = 0.08 + random.nextDouble() * 0.06;
                server.sendParticles(ModParticles.STAR_EMBER.get(), getX(), getY() + 0.3, getZ(), 0,
                    Math.cos(angle) * speed, 0.04 + random.nextDouble() * 0.05, Math.sin(angle) * speed, 1);
            }
            server.sendParticles(ParticleTypes.FLAME, getX(), getY() + 0.3, getZ(), 6, 0.2, 0.1, 0.2, 0.02);
            playSound(SoundEvents.FIRECHARGE_USE, 0.6F, 1.4F);
            EmberGroundEntity.spawn(server, this, StarwreckEntities.EMBER_GROUND.get());
        }
    }

    @Override protected SoundEvent getAmbientSound() { return SoundEvents.SILVERFISH_AMBIENT; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.SILVERFISH_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.SILVERFISH_DEATH; }
    @Override protected void playStepSound(BlockPos pos, BlockState state) { playSound(SoundEvents.SILVERFISH_STEP, 0.15F, 0.8F); }
    @Override public float getVoicePitch() { return super.getVoicePitch() * 0.7F; }

    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "base", 3, state -> {
            if (isDeadOrDying()) return state.setAndContinue(DEATH);
            return state.setAndContinue(state.isMoving() ? WALK : IDLE);
        }));
        // 攻击层放在后面，播放时覆盖身体、头和颚
        controllers.add(new AnimationController<>(this, "action", 1, state -> PlayState.STOP).triggerableAnim("attack", ATTACK));
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
